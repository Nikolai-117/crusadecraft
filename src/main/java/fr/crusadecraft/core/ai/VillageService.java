package fr.crusadecraft.core.ai;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.Territory;
import fr.crusadecraft.core.world.WorldNews;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Les villages de l'ordinateur (P28). Un royaume qui a un camp bâti depuis quelques jours fonde un village tout près :
 * un hameau (quatre maisons et un puits), une ville de plus sur la carte, et deux familles au registre des lignées
 * (ils apparaissent avec Comes Alive). Ensuite le village grandit par quartiers de deux maisons : de hameau en bourg,
 * de bourg en ville. Fonder près des terres d'un autre royaume fait une dispute : l'opinion entre eux baisse, et la
 * nouvelle court.
 *
 * <p>Comme le reste des civilisations autonomes, c'est coupé par défaut, noté bloc par bloc, et
 * {@code /crusade civ annuler} défait tout — villes, quartiers et habitants compris.
 */
public final class VillageService {
    /** Quartiers au plus autour d'un village : trois, de hameau en ville. */
    public static final int MAX_QUARTERS = 3;
    /** Familles au registre à la fondation, et par quartier achevé. */
    public static final int FOUNDING_FAMILIES = 2, QUARTER_FAMILIES = 1;
    /** Population ajoutée par quartier achevé. */
    public static final int QUARTER_POPULATION = 12;
    /** Les terres d'un autre royaume à moins de tant de blocs d'un village fondé : dispute. */
    public static final int DISPUTE_RANGE = 200;

    private VillageService() {}

    /** Le rang d'un village selon ses quartiers achevés : hameau, bourg, ville. Logique pure. */
    public static String sizeKey(int quarters) {
        return quarters >= 3 ? "crusadecraft.village.size.ville" : quarters >= 1 ? "crusadecraft.village.size.bourg" : "crusadecraft.village.size.hameau";
    }

    /** Les places d'un quartier autour d'un village : huit, à {@code ring} blocs, dans un ordre qui alterne les côtés. */
    public static List<int[]> quarterSpots(int ring) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        List<int[]> out = new ArrayList<>();
        for (int[] d : dirs) out.add(new int[]{d[0] * ring, d[1] * ring});
        return out;
    }

    /** L'écart entre le village et ses quartiers : la moitié des deux tailles, et la rue. */
    static int ring() {
        return (SiteKind.VILLAGE.size() + SiteKind.QUARTIER.size()) / 2 + 4;
    }

    static int autoVillages(CrusadeSavedData data, UUID realm) {
        int n = 0;
        for (Site s : data.world().sites()) if (s.auto() && s.kind() == SiteKind.VILLAGE && realm.equals(s.owner())) n++;
        return n;
    }

    static int quarters(CrusadeSavedData data, Site village) {
        int n = 0;
        for (Site s : data.world().sites()) {
            if (s.kind() == SiteKind.QUARTIER && village.province() >= 0 && s.province() == village.province()) n++;
        }
        return n;
    }

    /**
     * La part des villages dans la journée d'un royaume : faire grandir un village achevé s'il en a un qui peut
     * l'être, sinon en fonder un près d'un camp bâti depuis assez longtemps. Renvoie le chantier ouvert, s'il y en a un.
     */
    public static Optional<Site> onDay(MinecraftServer server, AiRealm ai, long day, Random random) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        int every = CrusadeConfig.SERVER.civQuarterDays.get();
        for (Site v : new ArrayList<>(data.world().sites())) {
            if (!v.auto() || v.kind() != SiteKind.VILLAGE || !ai.id().equals(v.owner()) || !v.built()) continue;
            if (day - v.createdDay() < (long) every * (quarters(data, v) + 1) || quarters(data, v) >= MAX_QUARTERS) continue;
            if (pending(data, v)) continue;
            Optional<Site> q = growAt(server, ai, v, random);
            if (q.isPresent()) return q;
        }
        if (autoVillages(data, ai.id()) >= CrusadeConfig.SERVER.civMaxVillages.get()) return Optional.empty();
        for (Site camp : new ArrayList<>(data.world().sites())) {
            if (!camp.auto() || camp.kind() != SiteKind.CAMP || !camp.built() || !ai.id().equals(camp.owner())) continue;
            if (day - camp.createdDay() < CrusadeConfig.SERVER.civVillageDays.get()) continue;
            Optional<Site> v = foundNear(server, ai, camp, random);
            if (v.isPresent()) return v;
        }
        return Optional.empty();
    }

    /** Un quartier de ce village est-il encore en chantier ? Il grandit un quartier à la fois. */
    static boolean pending(CrusadeSavedData data, Site village) {
        for (Site s : data.world().sites()) if (s.kind() == SiteKind.QUARTIER && s.province() == village.province() && !s.built()) return true;
        return false;
    }

    /** Fonde un village à 40–80 blocs du camp, sur une parcelle qui convient. */
    static Optional<Site> foundNear(MinecraftServer server, AiRealm ai, Site camp, Random random) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, camp.dimension()));
        if (level == null) return Optional.empty();
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2, dist = 40 + random.nextInt(40);
            int x = camp.anchor().getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = camp.anchor().getZ() + (int) Math.round(Math.sin(angle) * dist);
            Optional<Site> v = foundAt(server, ai, level, x, z, CrusadeConfig.SERVER.civRadius.get());
            if (v.isPresent()) return v;
        }
        return Optional.empty();
    }

    /** Fonde un village à cet endroit précis s'il convient (aussi pour les bancs d'essai, {@code radius} 0 : partout). */
    public static Optional<Site> foundAt(MinecraftServer server, AiRealm ai, ServerLevel level, int x, int z, int radius) {
        Optional<BlockPos> ground = CivService.suitable(server, level, ai, SiteKind.VILLAGE, x, z, radius);
        if (ground.isEmpty()) return Optional.empty();
        Optional<Site> site = SiteService.foundVillageForAi(server, level, ai, ground.get(), Direction.SOUTH);
        site.ifPresent(s -> dispute(server, ai, level, s));
        return site;
    }

    /** Un quartier de plus contre le village, à la première place qui convient. */
    public static Optional<Site> growAt(MinecraftServer server, AiRealm ai, Site village, Random random) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, village.dimension()));
        if (level == null) return Optional.empty();
        List<int[]> spots = quarterSpots(ring());
        int start = random.nextInt(spots.size());
        for (int i = 0; i < spots.size(); i++) {
            int[] o = spots.get((start + i) % spots.size());
            int x = village.anchor().getX() + o[0], z = village.anchor().getZ() + o[1];
            Optional<BlockPos> ground = CivService.suitable(server, level, ai, SiteKind.QUARTIER, x, z, 0);
            if (ground.isEmpty() || Math.abs(ground.get().getY() - village.anchor().getY()) > 4) continue;
            Direction front = Direction.getNearest(-o[0], 0, -o[1]);
            if (!front.getAxis().isHorizontal()) front = Direction.SOUTH;
            Optional<Site> q = SiteService.quarterForAi(server, level, ai, village, ground.get(), front);
            if (q.isPresent()) return q;
        }
        return Optional.empty();
    }

    /** Une ligne par village de l'ordinateur : son nom, son royaume, son rang et ses quartiers. */
    public static List<Component> report(MinecraftServer server) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        List<Component> out = new ArrayList<>();
        for (Site v : data.world().sites()) {
            if (!v.auto() || v.kind() != SiteKind.VILLAGE) continue;
            int built = 0;
            for (Site q : data.world().sites()) if (q.kind() == SiteKind.QUARTIER && q.province() == v.province() && q.built()) built++;
            out.add(Component.translatable("crusadecraft.civ.info.village", v.name() == null ? "?" : v.name(), DiplomacyService.name(server, v.owner()),
                    Component.translatable(v.built() ? sizeKey(built) : "crusadecraft.village.size.chantier"), built, v.anchor().getX(), v.anchor().getZ()));
        }
        return out;
    }

    /**
     * Un quartier achevé : la ville gagne des habitants, le registre une famille de plus dans la communauté du
     * village (elle apparaît avec Comes Alive), et le village change de rang.
     */
    public static void onQuarterBuilt(MinecraftServer server, ServerLevel level, Site quarter) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        for (AiRealm r : data.world().realms()) {
            for (AiProvince p : r.provinces()) {
                if (p.id() != quarter.province()) continue;
                p.setPopulation(p.population() + QUARTER_POPULATION);
                p.setDevelopment(p.development() + 5);
            }
        }
        if (quarter.community() != null) {
            var state = data.lineage();
            state.community(quarter.community()).ifPresent(c -> {
                var born = new fr.crusadecraft.core.lineage.LineageGenerator(quarter.anchor().asLong() ^ quarter.createdDay()).populate(state, c, QUARTER_FAMILIES);
                fr.crusadecraft.core.lineage.LineageService.spawnPeople(level, state, born, quarter.anchor().above());
            });
        }
        data.setDirty();
    }

    /**
     * Fonder près des terres d'un autre royaume : une dispute. L'opinion baisse des deux côtés, et le monde l'apprend.
     * On regarde huit points à {@link #DISPUTE_RANGE} blocs du village.
     */
    static void dispute(MinecraftServer server, AiRealm ai, ServerLevel level, Site village) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        java.util.Set<UUID> neighbours = new java.util.LinkedHashSet<>();
        for (int[] o : quarterSpots(DISPUTE_RANGE)) {
            Territory.ownerAt(server, level.dimension().location(), village.anchor().getX() + o[0] + 0.5, village.anchor().getZ() + o[1] + 0.5)
                    .filter(owner -> !owner.equals(ai.id())).ifPresent(neighbours::add);
        }
        long today = RealmService.currentDay(server);
        for (UUID other : neighbours) {
            var rel = data.diplomacy().between(ai.id(), other);
            rel.addOpinion(ai.id(), OpinionModifier.of(OpinionReason.DISPUTE, today, 1));
            rel.addOpinion(other, OpinionModifier.of(OpinionReason.DISPUTE, today, 1));
            AiBrain.publish(server, WorldNews.of(today, "crusadecraft.news.dispute", List.of(ai.id(), other), AiNames.realm(ai.rank(), ai.place()),
                    Component.literal(village.name() == null ? "?" : village.name()), DiplomacyService.name(server, other)), true);
            CrusadeCraft.LOGGER.info("CrusadeCraft : dispute — {} fonde {} près des terres de {}", ai.place(), village.name(), other);
        }
    }
}
