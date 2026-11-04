package fr.crusadecraft.core.city;

import fr.crusadecraft.compat.mca.McaBridge;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.lineage.Community;
import fr.crusadecraft.core.lineage.LineageGenerator;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Repliques;
import fr.crusadecraft.core.nation.NationBook;
import fr.crusadecraft.core.nation.NationDef;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Qui sont les gens d'une ville (P42) : ils portent les noms de la culture de leur nation, ont des traits et une vie
 * composée de textes écrits d'avance, et certains sont des personnages écrits à la main dans le fichier de leur nation.
 */
public final class CityPeople {
    private CityPeople() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** La nation de cette ville, si elle en a une. */
    public static Optional<NationDef> nation(MinecraftServer server, Site site) {
        return data(server).world().realm(site.owner()).map(AiRealm::nation).flatMap(NationBook::byKey);
    }

    /** Un générateur de gens qui portent les noms de cette ville. */
    static LineageGenerator generator(MinecraftServer server, Site site, long seed) {
        LineageGenerator gen = new LineageGenerator(seed);
        nation(server, site).map(NationDef::culture).ifPresent(c -> gen.withNames(c.men(), c.women(), c.families()));
        return gen;
    }

    /** Ce qui fait de lui quelqu'un : son métier, sa ville, son caractère, sa vie. Un habitant déjà habillé ne change pas. */
    static void dress(MinecraftServer server, Site site, Person p, BuildingFunction f, Random random) {
        p.setSite(site.id());
        if (f != null && !f.dwelling() && p.age() >= 16) p.setJob(f.name());
        if (p.traits().isEmpty()) p.setTraits(traits(random, p.age()), Person.MAX_TRAITS);
        if (p.bio().isEmpty()) p.setBio(life(server, site, p, random));
    }

    /** Deux ou trois traits de caractère, jamais contraires ; un enfant n'en a qu'un. */
    static List<Trait> traits(Random random, int age) {
        List<Trait> pool = new ArrayList<>();
        for (Trait t : Trait.values()) if (t.kind() == Trait.Kind.PERSONALITY) pool.add(t);
        Collections.shuffle(pool, random);
        int n = age < 16 ? 1 : 2 + random.nextInt(2);
        List<Trait> out = new ArrayList<>();
        for (Trait t : pool) {
            if (out.size() >= n) break;
            if (out.stream().noneMatch(k -> k.conflictsWith(t))) out.add(t);
        }
        return out;
    }

    /** Sa vie : d'où il vient, ce qu'il fait, un trait qui le distingue, pris dans les textes écrits d'avance. */
    static String life(MinecraftServer server, Site site, Person p, Random random) {
        StringBuilder out = new StringBuilder();
        for (Repliques.Moment m : new Repliques.Moment[]{Repliques.Moment.VIE_ORIGINE, Repliques.Moment.VIE_METIER, Repliques.Moment.VIE_TIC}) {
            Optional<String> line = CityVoice.text(server, site, p, m, null, random);
            if (line.isEmpty()) continue;
            if (out.length() + line.get().length() + 1 > Person.BIO_MAX) break;
            if (out.length() > 0) out.append(' ');
            out.append(line.get());
        }
        return out.toString();
    }

    /** La clé d'un personnage écrit d'avance : elle ne change pas quand on corrige sa vie dans le fichier. */
    static String key(NationDef nation, String city, NationDef.Notable n) {
        return nation.key() + "/" + city.toLowerCase(Locale.ROOT) + "/" + n.function().name().toLowerCase(Locale.ROOT) + "/" + n.first().toLowerCase(Locale.ROOT);
    }

    /** Ce personnage écrit d'avance, s'il en est un. */
    static Optional<NationDef.Notable> notable(MinecraftServer server, Site site, Person p) {
        if (p.notable().isEmpty()) return Optional.empty();
        return nation(server, site).map(d -> {
            NationDef.CityLore lore = d.city(site.name());
            if (lore == null) return null;
            for (NationDef.Notable n : lore.notables()) if (key(d, site.name(), n).equals(p.notable())) return n;
            return null;
        });
    }

    /**
     * Les personnages écrits dans le fichier de la nation prennent leur place dans la ville : le premier poste libre de
     * leur fonction. Un personnage déjà là reçoit ce que le fichier dit de lui, pour qu'une correction se voie tout de suite.
     */
    public static int applyNotables(MinecraftServer server, Site site) {
        Optional<NationDef> nation = nation(server, site);
        if (nation.isEmpty()) return 0;
        NationDef.CityLore lore = nation.get().city(site.name());
        if (lore == null) return 0;
        LineageState state = data(server).lineage();
        int placed = 0;
        for (NationDef.Notable n : lore.notables()) {
            String key = key(nation.get(), site.name(), n);
            Person holder = null;
            List<Person> candidates = new ArrayList<>();
            for (CityBuilding b : site.buildings()) {
                for (UUID id : b.occupants()) {
                    Person p = state.person(id).orElse(null);
                    if (p == null || !p.alive()) continue;
                    if (key.equals(p.notable())) holder = p;
                    else if (b.function() == n.function() && p.notable().isEmpty() && p.age() >= 16) candidates.add(p);
                }
            }
            if (holder == null) {
                if (candidates.isEmpty()) continue;
                // Le premier adulte du bon sexe, à défaut le premier adulte.
                holder = candidates.stream().filter(p -> p.female() == n.female()).findFirst().orElse(candidates.get(0));
                holder.setNotable(key);
                // À son arrivée, il a l'âge que dit le fichier ; ensuite, il vieillit comme tout le monde.
                holder.setAge(n.age());
                placed++;
            }
            incarnate(server, state, site, holder, n);
        }
        if (placed > 0) data(server).setDirty();
        return placed;
    }

    /** Il devient ce que le fichier dit de lui ; un villageois déjà dans le monde change de nom. */
    private static void incarnate(MinecraftServer server, LineageState state, Site site, Person p, NationDef.Notable n) {
        boolean renamed = !p.name().equals(n.first()) || !p.surname().equals(n.last());
        p.setName(n.first());
        p.setSurname(n.last());
        p.setFemale(n.female());
        // L'âge ne redescend pas : celui qui a vieilli dans le monde garde ses années.
        if (p.age() < n.age()) p.setAge(n.age());
        if (!n.traits().isEmpty()) p.setTraits(n.traits(), Person.MAX_TRAITS);
        if (!n.bio().isEmpty()) p.setBio(n.bio());
        if (n.function().dwelling()) p.setJob("");
        p.setSite(site.id());
        if (!renamed || !p.inWorld()) return;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, site.dimension()));
        Entity e = level == null ? null : level.getEntity(p.id());
        if (e != null) {
            String full = state.fullName(p);
            McaBridge.rename(e, full);
            if (!McaBridge.isReady()) e.setCustomName(net.minecraft.network.chat.Component.literal(full));
        }
    }

    /** Une ville vit depuis une version où ses gens n'avaient pas de fiche : ils en reçoivent une, sans rien perdre. */
    public static void backfill(MinecraftServer server, Site site) {
        LineageState state = data(server).lineage();
        Random random = new Random(site.id().getLeastSignificantBits());
        for (CityBuilding b : site.buildings()) {
            if (b.function() == null) continue;
            for (UUID id : b.occupants()) state.person(id).ifPresent(p -> {
                if (p.site() == null) dress(server, site, p, b.function(), random);
            });
        }
        applyNotables(server, site);
    }

    /** La communauté de la ville, créée s'il le faut. */
    static Community community(MinecraftServer server, Site site) {
        return CityService.community(server, site);
    }
}
