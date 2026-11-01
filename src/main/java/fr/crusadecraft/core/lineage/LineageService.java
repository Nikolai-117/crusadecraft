package fr.crusadecraft.core.lineage;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.mca.McaBridge;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.logic.Heraldry;
import fr.crusadecraft.core.logic.TextRules;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.world.AiCharacter;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le registre des lignées (P13) : créer et générer des maisons, des communautés et leurs gens, les modifier, et les
 * faire apparaître en villageois de Minecraft Comes Alive, arbre généalogique compris.
 *
 * <p>Chaque royaume IA reçoit sa cour la première fois qu'on ouvre le registre : son souverain tel que le monde le
 * connaît, sa famille, son héritier, ses conseillers. Le reste se compose à la main ou par le générateur. Modifier
 * le registre demande les mêmes droits que l'éditeur de la carte ; le consulter est ouvert à tous.
 */
public final class LineageService {
    public static final int NAME_MAX = 24;
    /** Personnes qu'on fait apparaître au plus d'un coup : une grande maison arrive en plusieurs fois. */
    public static final int SPAWN_MAX = 16;
    public static final int ADOPT_RADIUS = 16;
    public static final int COMMUNITY_MAX = 20;

    private LineageService() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    private static LineageState state(MinecraftServer server) {
        return data(server).lineage();
    }

    private static Outcome denied() {
        return Outcome.no("crusadecraft.mapedit.denied");
    }

    // ------------------------------------------------------------------ cours des royaumes IA

    /** Donne une cour aux royaumes IA qui n'en ont pas encore. Renvoie le nombre de maisons créées. */
    public static int ensureCourts(MinecraftServer server, Collection<AiRealm> realms) {
        LineageState state = state(server);
        int made = 0;
        for (AiRealm ai : realms) {
            if (state.houseOfRealm(ai.id()).isPresent()) continue;
            List<LineageGenerator.Counsellor> council = new ArrayList<>();
            for (AiCharacter c : ai.council().values()) council.add(new LineageGenerator.Counsellor(c.name(), c.female(), c.age()));
            AiCharacter ruler = ai.ruler();
            new LineageGenerator(ai.id().getMostSignificantBits() ^ 0x434F5552L)
                    .court(state, ai.house(), ai.color(), ai.id(), ruler.name(), ruler.female(), ruler.age(), council);
            made++;
        }
        if (made > 0) data(server).setDirty();
        return made;
    }

    // ------------------------------------------------------------------ vue

    private static boolean visible(ServerPlayer player, UUID realm) {
        if (realm == null || WorldEditService.canEdit(player)) return true;
        Optional<AiRealm> ai = data(player.server).world().realm(realm);
        return ai.isEmpty() || ai.get().discoveredBy(player.getUUID());
    }

    private static Component realmName(MinecraftServer server, UUID realm) {
        return realm == null ? Component.translatable("crusadecraft.lineage.no_realm") : DiplomacyService.name(server, realm);
    }

    public static LineageView view(ServerPlayer player, UUID selected) {
        MinecraftServer server = player.server;
        CrusadeSavedData data = data(server);
        boolean editor = WorldEditService.canEdit(player);
        List<AiRealm> known = new ArrayList<>();
        for (AiRealm ai : data.world().realms()) if (editor || ai.discoveredBy(player.getUUID())) known.add(ai);
        ensureCourts(server, known);
        LineageState state = data.lineage();

        List<LineageView.HouseLine> houses = new ArrayList<>();
        for (House h : state.houses()) {
            if (!visible(player, h.realm())) continue;
            List<Person> members = state.members(h.id());
            int living = (int) members.stream().filter(Person::alive).count();
            houses.add(new LineageView.HouseLine(h.id(), h.name(), h.color(), realmName(server, h.realm()), living, members.size()));
        }
        List<LineageView.CommunityLine> communities = new ArrayList<>();
        for (Community c : state.communities()) {
            if (!visible(player, c.realm())) continue;
            communities.add(new LineageView.CommunityLine(c.id(), c.name(), c.kind(), realmName(server, c.realm()), state.residents(c.id()).size()));
        }
        List<LineageView.RealmChoice> realms = new ArrayList<>();
        for (Realm r : data.realms()) realms.add(new LineageView.RealmChoice(r.id(), Component.literal(r.name())));
        for (AiRealm ai : known) realms.add(new LineageView.RealmChoice(ai.id(), DiplomacyService.name(server, ai.id())));

        LineageView.Selection selection = null;
        Optional<House> house = state.house(selected);
        Optional<Community> community = state.community(selected);
        if (house.isPresent() && visible(player, house.get().realm())) selection = houseSelection(server, state, house.get());
        else if (community.isPresent() && visible(player, community.get().realm())) selection = communitySelection(server, state, community.get());
        return new LineageView(editor, McaBridge.isReady(), houses, communities, realms, selection);
    }

    private static LineageView.Selection houseSelection(MinecraftServer server, LineageState state, House h) {
        List<LineageView.Row> rows = new ArrayList<>();
        for (Map.Entry<Integer, List<Person>> e : state.generations(h.id()).entrySet()) {
            for (Person p : e.getValue()) rows.add(row(state, p, e.getKey(), h.id().equals(p.house()), h.id()));
        }
        return new LineageView.Selection(true, h.id(), h.name(), realmName(server, h.realm()), null, rows, citizens(server, h));
    }

    /**
     * Les citoyens MineColonies entrés dans la maison du souverain (la maison de P4), quand cette maison du registre est
     * celle d'un royaume de joueur : les deux généalogies se montrent l'une l'autre, sans se fondre.
     */
    private static List<String> citizens(MinecraftServer server, House h) {
        if (h.realm() == null) return List.of();
        Optional<Realm> realm = data(server).realm(h.realm());
        if (realm.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (fr.crusadecraft.core.character.CitizenRef ref : realm.get().dynasty().members()) {
            out.add(fr.crusadecraft.core.character.CharacterService.nameOf(server, ref));
        }
        return out;
    }

    private static LineageView.Selection communitySelection(MinecraftServer server, LineageState state, Community c) {
        // Les foyers : les gens reliés par un mariage ou une filiation, dans la communauté.
        List<Person> residents = state.residents(c.id());
        Map<UUID, UUID> root = new HashMap<>();
        for (Person p : residents) root.put(p.id(), p.id());
        for (Person p : residents) {
            for (UUID other : new UUID[]{p.spouse(), p.father(), p.mother()}) {
                if (other != null && root.containsKey(other)) root.put(find(root, p.id()), find(root, other));
            }
        }
        Map<UUID, Integer> households = new HashMap<>();
        List<Person> sorted = new ArrayList<>(residents);
        sorted.sort((a, b) -> {
            int byHousehold = find(root, a.id()).compareTo(find(root, b.id()));
            return byHousehold != 0 ? byHousehold : Integer.compare(b.age(), a.age());
        });
        List<LineageView.Row> rows = new ArrayList<>();
        for (Person p : sorted) {
            int group = households.computeIfAbsent(find(root, p.id()), k -> households.size());
            rows.add(row(state, p, group, true, null));
        }
        return new LineageView.Selection(false, c.id(), c.name(), realmName(server, c.realm()), c.kind(), rows);
    }

    private static UUID find(Map<UUID, UUID> root, UUID id) {
        UUID r = id;
        for (int guard = 0; guard < 1000 && !root.get(r).equals(r); guard++) r = root.get(r);
        return r;
    }

    private static LineageView.Row row(LineageState state, Person p, int group, boolean member, UUID house) {
        String spouse = state.person(p.spouse()).map(Person::name).orElse("");
        return new LineageView.Row(p.id(), state.fullName(p), p.female(), p.age(), p.alive(), p.role(), group, spouse, p.inWorld(), member,
                p.traitMask(), p.bio(), known(state, p.father()), known(state, p.mother()), known(state, p.spouse()));
    }

    private static UUID known(LineageState state, UUID id) {
        return id != null && state.person(id).isPresent() ? id : null;
    }

    // ------------------------------------------------------------------ créer

    private static Optional<String> name(String raw) {
        String name = TextRules.clean(raw, NAME_MAX);
        return TextRules.isValidName(name) ? Optional.of(name) : Optional.empty();
    }

    private static boolean realmExists(MinecraftServer server, UUID realm) {
        return realm == null || data(server).world().realm(realm).isPresent() || data(server).realm(realm).isPresent();
    }

    public static Outcome createHouse(ServerPlayer player, String rawName, int colorIndex, UUID realm, int generations) {
        if (!WorldEditService.canEdit(player)) return denied();
        Optional<String> name = name(rawName);
        if (name.isEmpty()) return Outcome.no("crusadecraft.mapedit.name_invalid");
        if (!realmExists(player.server, realm)) return Outcome.no("crusadecraft.mapedit.realm.unknown");
        LineageState state = state(player.server);
        if (realm != null && state.houseOfRealm(realm).isPresent()) return Outcome.no("crusadecraft.lineage.realm_has_house");
        House h = new LineageGenerator(player.getRandom().nextLong()).house(state, name.get(), Heraldry.byOrdinal(colorIndex).rgb(), realm,
                generations);
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.lineage.house.created", h.name(), state.members(h.id()).size()));
    }

    public static Outcome createCommunity(ServerPlayer player, String rawName, CommunityKind kind, UUID realm, int size) {
        if (!WorldEditService.canEdit(player)) return denied();
        Optional<String> name = name(rawName);
        if (name.isEmpty()) return Outcome.no("crusadecraft.mapedit.name_invalid");
        if (!realmExists(player.server, realm)) return Outcome.no("crusadecraft.mapedit.realm.unknown");
        LineageState state = state(player.server);
        Community c = new LineageGenerator(player.getRandom().nextLong()).community(state, name.get(), kind, realm,
                Math.max(1, Math.min(COMMUNITY_MAX, size)));
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.lineage.community.created", c.name(), state.residents(c.id()).size()));
    }

    // ------------------------------------------------------------------ retoucher

    private static Optional<Person> editable(ServerPlayer player, UUID id) {
        return state(player.server).person(id);
    }

    public static Outcome addChild(ServerPlayer player, UUID parentId) {
        if (!WorldEditService.canEdit(player)) return denied();
        Optional<Person> parent = editable(player, parentId);
        if (parent.isEmpty()) return Outcome.no("crusadecraft.lineage.unknown");
        LineageState state = state(player.server);
        Optional<Person> child = new LineageGenerator(player.getRandom().nextLong()).child(state, parent.get());
        if (child.isEmpty()) return Outcome.no("crusadecraft.lineage.too_young", parent.get().name());
        state.house(child.get().house()).ifPresent(h -> LineageGenerator.assignRoles(state, h));
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.lineage.child.born", state.fullName(child.get()), parent.get().name()));
    }

    public static Outcome marry(ServerPlayer player, UUID personId) {
        if (!WorldEditService.canEdit(player)) return denied();
        Optional<Person> p = editable(player, personId);
        if (p.isEmpty()) return Outcome.no("crusadecraft.lineage.unknown");
        if (p.get().spouse() != null) return Outcome.no("crusadecraft.lineage.already_married", p.get().name());
        if (p.get().age() < 16) return Outcome.no("crusadecraft.lineage.too_young_marry", p.get().name());
        LineageState state = state(player.server);
        Person spouse = new LineageGenerator(player.getRandom().nextLong()).spouse(state, p.get());
        state.house(p.get().house()).ifPresent(h -> LineageGenerator.assignRoles(state, h));
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.lineage.married", p.get().name(), state.fullName(spouse)));
    }

    /** Renomme une personne, une maison ou une communauté (et, pour une personne déjà apparue, son villageois). */
    public static Outcome rename(ServerPlayer player, UUID target, String rawName) {
        if (!WorldEditService.canEdit(player)) return denied();
        Optional<String> name = name(rawName);
        if (name.isEmpty()) return Outcome.no("crusadecraft.mapedit.name_invalid");
        LineageState state = state(player.server);
        Optional<Person> p = state.person(target);
        if (p.isPresent()) {
            p.get().setName(name.get());
            Entity e = player.serverLevel().getEntity(target);
            if (e != null) McaBridge.rename(e, state.fullName(p.get()));
        } else if (state.house(target).isPresent()) {
            state.house(target).get().setName(name.get());
        } else if (state.community(target).isPresent()) {
            state.community(target).get().setName(name.get());
        } else {
            return Outcome.no("crusadecraft.lineage.unknown");
        }
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.lineage.renamed", name.get()));
    }

    /**
     * La fiche d'une personne (P20) : son prénom, son âge, ses traits et ce qu'on écrit d'elle. {@code text} porte le
     * prénom puis, après un saut de ligne, la biographie ; {@code traitMask} les traits en bits.
     */
    public static Outcome editPerson(ServerPlayer player, UUID personId, String text, int age, int traitMask) {
        if (!WorldEditService.canEdit(player)) return denied();
        Optional<Person> p = editable(player, personId);
        if (p.isEmpty()) return Outcome.no("crusadecraft.lineage.unknown");
        String raw = text == null ? "" : text;
        int cut = raw.indexOf('\n');
        Optional<String> name = name(cut < 0 ? raw : raw.substring(0, cut));
        if (name.isEmpty()) return Outcome.no("crusadecraft.mapedit.name_invalid");
        String bio = cut < 0 ? "" : cleanBio(raw.substring(cut + 1));
        List<fr.crusadecraft.core.character.Trait> wanted = new ArrayList<>();
        for (fr.crusadecraft.core.character.Trait t : fr.crusadecraft.core.character.Trait.values()) {
            if ((traitMask & (1 << t.ordinal())) != 0) wanted.add(t);
        }
        Person person = p.get();
        person.setName(name.get());
        person.setAge(age);
        person.setBio(bio);
        person.setTraits(wanted, Person.MAX_TRAITS);
        LineageState state = state(player.server);
        Entity e = player.serverLevel().getEntity(personId);
        if (e != null) McaBridge.rename(e, state.fullName(person));
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.lineage.edited", state.fullName(person)));
    }

    /** Une biographie sans codes de mise en forme, sauts de ligne gardés, bornée. */
    static String cleanBio(String raw) {
        String s = raw.replaceAll("§.?", "").replace('\r', ' ').replaceAll("[\\p{Cntrl}&&[^\n]]", " ").strip();
        return s.length() > Person.BIO_MAX ? s.substring(0, Person.BIO_MAX) : s;
    }

    public static Outcome setRole(ServerPlayer player, UUID personId, Role role) {
        if (!WorldEditService.canEdit(player)) return denied();
        Optional<Person> p = editable(player, personId);
        if (p.isEmpty()) return Outcome.no("crusadecraft.lineage.unknown");
        p.get().setRole(role);
        // Un nouveau souverain devient le chef de sa maison.
        if (role == Role.SOUVERAIN) state(player.server).house(p.get().house()).ifPresent(h -> h.setHead(p.get().id()));
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.lineage.role_set", p.get().name(), Component.translatable(role.key())));
    }

    public static Outcome toggleAlive(ServerPlayer player, UUID personId) {
        if (!WorldEditService.canEdit(player)) return denied();
        Optional<Person> p = editable(player, personId);
        if (p.isEmpty()) return Outcome.no("crusadecraft.lineage.unknown");
        p.get().setAlive(!p.get().alive());
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable(p.get().alive() ? "crusadecraft.lineage.revived" : "crusadecraft.lineage.died", p.get().name()));
    }

    /** Supprime une personne, une maison (avec ses gens) ou une communauté (avec ses habitants). */
    public static Outcome remove(ServerPlayer player, UUID target) {
        if (!WorldEditService.canEdit(player)) return denied();
        LineageState state = state(player.server);
        String name;
        if (state.person(target).isPresent()) {
            name = state.fullName(state.person(target).get());
            UUID house = state.person(target).get().house();
            state.remove(target);
            state.house(house).ifPresent(h -> new LineageGenerator(target.getLeastSignificantBits()).settle(state, h));
        } else if (state.house(target).isPresent()) {
            name = state.house(target).get().name();
            state.removeHouse(target);
        } else if (state.community(target).isPresent()) {
            name = state.community(target).get().name();
            state.removeCommunity(target);
        } else {
            return Outcome.no("crusadecraft.lineage.unknown");
        }
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.lineage.removed", name));
    }

    /**
     * Agrandit : une génération de plus pour une maison (les couples de la dernière génération ont des enfants), trois
     * foyers de plus pour une communauté.
     */
    public static Outcome grow(ServerPlayer player, UUID target) {
        if (!WorldEditService.canEdit(player)) return denied();
        LineageState state = state(player.server);
        LineageGenerator gen = new LineageGenerator(player.getRandom().nextLong());
        int added = 0;
        if (state.house(target).isPresent()) {
            House h = state.house(target).get();
            var gens = state.generations(h.id());
            if (gens.isEmpty()) return Outcome.no("crusadecraft.lineage.unknown");
            List<Person> last = new ArrayList<>(gens.lastEntry().getValue());
            for (Person p : last) {
                if (!h.id().equals(p.house()) || !p.alive()) continue;
                if (p.spouse() == null && p.age() >= LineageGenerator.PARENT_GAP + 2) gen.spouse(state, p);
                int children = 1 + player.getRandom().nextInt(3);
                for (int i = 0; i < children; i++) if (gen.child(state, p).isPresent()) added++;
            }
            gen.settle(state, h);
        } else if (state.community(target).isPresent()) {
            added = gen.populate(state, state.community(target).get(), 3).size();
        } else {
            return Outcome.no("crusadecraft.lineage.unknown");
        }
        if (added == 0) return Outcome.no("crusadecraft.lineage.grow_none");
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.lineage.grown", added));
    }

    // ------------------------------------------------------------------ Comes Alive

    /** Les personnes visées : une personne, toute une maison (conjoints compris), toute une communauté. */
    static List<Person> people(LineageState state, UUID target) {
        List<Person> out = new ArrayList<>();
        Optional<Person> p = state.person(target);
        if (p.isPresent()) {
            out.add(p.get());
        } else if (state.house(target).isPresent()) {
            for (Person m : state.members(target)) {
                out.add(m);
                state.person(m.spouse()).filter(s -> !target.equals(s.house())).ifPresent(out::add);
            }
        } else if (state.community(target).isPresent()) {
            out.addAll(state.residents(target));
        }
        return out;
    }

    /**
     * Fait apparaître près du joueur, en villageois de Comes Alive, les vivants visés qui ne sont pas encore dans le
     * monde ; ceux qui y sont déjà et à portée viennent à lui. L'arbre généalogique de Comes Alive est mis à jour.
     */
    public static Outcome spawn(ServerPlayer player, UUID target) {
        if (!WorldEditService.canEdit(player)) return denied();
        if (!McaBridge.isReady()) return Outcome.no("crusadecraft.lineage.no_mca");
        LineageState state = state(player.server);
        List<Person> people = people(state, target);
        if (people.isEmpty()) return Outcome.no("crusadecraft.lineage.unknown");
        int[] counts = spawnAt(player.serverLevel(), state, people, player.blockPosition());
        int spawned = counts[0], came = counts[1], elsewhere = counts[2];
        data(player.server).setDirty();
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} personne(s) du registre apparues, {} venues, {} ailleurs", spawned, came, elsewhere);
        if (spawned + came == 0) return Outcome.no(elsewhere > 0 ? "crusadecraft.lineage.spawn.elsewhere" : "crusadecraft.lineage.spawn.none");
        return Outcome.ok(Component.translatable("crusadecraft.lineage.spawn.done", spawned, came));
    }

    /**
     * Fait apparaître ces personnes autour d'un point (un village qui vient d'être bâti), sans demander de droits :
     * c'est le jeu qui le fait. Renvoie le nombre d'apparus. Sans Comes Alive, rien.
     */
    public static int spawnPeople(ServerLevel level, LineageState state, List<Person> people, BlockPos center) {
        if (!McaBridge.isReady() || people.isEmpty()) return 0;
        int[] counts = spawnAt(level, state, people, center);
        CrusadeSavedData.get(level.getServer()).setDirty();
        return counts[0];
    }

    /** Apparus, venus, ailleurs : les vivants visés qui ne sont pas encore dans le monde apparaissent autour du centre. */
    private static int[] spawnAt(ServerLevel level, LineageState state, List<Person> people, BlockPos center) {
        McaBridge.syncTree(level, state, people);
        int spawned = 0, came = 0, elsewhere = 0, index = 0;
        for (Person p : people) {
            if (!p.alive()) continue;
            if (spawned + came >= SPAWN_MAX) break;
            BlockPos spot = spot(level, center, index++);
            Entity existing = level.getEntity(p.id());
            if (existing != null) {
                existing.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                p.setInWorld(level.dimension().location(), spot);
                came++;
                continue;
            }
            if (p.inWorld()) {
                elsewhere++;
                continue;
            }
            if (McaBridge.spawn(level, p, state.fullName(p), spot).isPresent()) {
                p.setInWorld(level.dimension().location(), spot);
                spawned++;
            }
        }
        return new int[]{spawned, came, elsewhere};
    }

    /** Inscrit les personnes visées dans l'arbre généalogique de Comes Alive, sans rien faire apparaître. */
    public static Outcome syncTree(ServerPlayer player, UUID target) {
        if (!WorldEditService.canEdit(player)) return denied();
        if (!McaBridge.isReady()) return Outcome.no("crusadecraft.lineage.no_mca");
        LineageState state = state(player.server);
        List<Person> people = people(state, target);
        if (people.isEmpty()) return Outcome.no("crusadecraft.lineage.unknown");
        if (!McaBridge.syncTree(player.serverLevel(), state, people)) return Outcome.no("crusadecraft.lineage.no_mca");
        return Outcome.ok(Component.translatable("crusadecraft.lineage.tree.done", people.size()));
    }

    /** Assez près pour que le villageois soit chargé chez le joueur et que l'éditeur de Comes Alive le trouve. */
    public static final int APPEARANCE_RANGE = 48;

    /** L'éditeur d'apparence de Comes Alive (P26), sur le villageois qui incarne cette personne, s'il est tout près. */
    public static Outcome appearance(ServerPlayer player, UUID target) {
        if (!WorldEditService.canEdit(player)) return denied();
        if (!McaBridge.isReady()) return Outcome.no("crusadecraft.lineage.no_mca");
        LineageState state = state(player.server);
        Optional<Person> person = state.person(target);
        if (person.isEmpty()) return Outcome.no("crusadecraft.lineage.unknown");
        String name = state.fullName(person.get());
        Entity e = player.serverLevel().getEntity(target);
        if (e == null || !e.isAlive() || e.distanceToSqr(player) > (double) APPEARANCE_RANGE * APPEARANCE_RANGE) {
            return Outcome.no("crusadecraft.person.appearance.far", name);
        }
        if (!McaBridge.openAppearance(player, e)) return Outcome.no("crusadecraft.lineage.no_mca");
        return Outcome.ok(Component.translatable("crusadecraft.person.appearance.opened", name));
    }

    /**
     * L'arbre de Comes Alive (P26), ouvert sur cette personne : sa maison y est d'abord inscrite (avec les conjoints
     * venus d'ailleurs), pour que l'arbre la montre entière même si personne n'est apparu dans le monde.
     */
    public static Outcome mcaTree(ServerPlayer player, UUID target) {
        if (!McaBridge.isReady()) return Outcome.no("crusadecraft.lineage.no_mca");
        LineageState state = state(player.server);
        Optional<Person> person = state.person(target);
        if (person.isEmpty()) return Outcome.no("crusadecraft.lineage.unknown");
        UUID realm = state.house(person.get().house()).map(House::realm).orElse(person.get().realm());
        if (!visible(player, realm)) return Outcome.no("crusadecraft.lineage.unknown");
        List<Person> people = person.get().house() != null ? people(state, person.get().house()) : List.of(person.get());
        if (!McaBridge.syncTree(player.serverLevel(), state, people)) return Outcome.no("crusadecraft.lineage.no_mca");
        fr.crusadecraft.network.CrusadeNetwork.toPlayer(player, new fr.crusadecraft.network.packet.LineagePackets.OpenMcaTree(target));
        return Outcome.ok(Component.translatable("crusadecraft.lineage.tree.done", people.size()));
    }

    /**
     * Inscrit au registre les villageois de Comes Alive autour du joueur qui n'y sont pas encore : dans la maison ou
     * la communauté visée, avec leurs liens de famille quand leurs parents ou leur conjoint y sont aussi.
     */
    public static Outcome adoptNearby(ServerPlayer player, UUID target) {
        if (!WorldEditService.canEdit(player)) return denied();
        if (!McaBridge.isReady()) return Outcome.no("crusadecraft.lineage.no_mca");
        LineageState state = state(player.server);
        Optional<House> house = state.house(target);
        Optional<Community> community = state.community(target);
        if (house.isEmpty() && community.isEmpty()) return Outcome.no("crusadecraft.lineage.unknown");
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(ADOPT_RADIUS);
        List<McaBridge.Villager> found = new ArrayList<>();
        for (Entity e : level.getEntities(player, box)) {
            if (state.person(e.getUUID()).isPresent()) continue;
            McaBridge.read(e).ifPresent(found::add);
        }
        if (found.isEmpty()) return Outcome.no("crusadecraft.lineage.adopt.none");
        for (McaBridge.Villager v : found) {
            Person p = new Person(v.id(), firstName(v.name()), v.female(), v.age());
            p.setSurname(surname(v.name()));
            house.ifPresent(h -> {
                p.setHouse(h.id());
                p.setSurname("");
                p.setRealm(h.realm());
                p.setRole(Role.FAMILLE);
            });
            community.ifPresent(c -> {
                p.setCommunity(c.id());
                p.setRealm(c.realm());
            });
            Entity e = level.getEntity(v.id());
            if (e != null) p.setInWorld(level.dimension().location(), e.blockPosition());
            state.add(p);
        }
        // Les liens de famille, une fois tout le monde inscrit.
        for (McaBridge.Villager v : found) {
            Person p = state.person(v.id()).orElseThrow();
            if (v.father() != null && state.person(v.father()).isPresent()) p.setFather(v.father());
            if (v.mother() != null && state.person(v.mother()).isPresent()) p.setMother(v.mother());
            if (v.partner() != null && state.person(v.partner()).isPresent()) {
                p.setSpouse(v.partner());
                state.person(v.partner()).get().setSpouse(p.id());
            }
        }
        house.ifPresent(h -> new LineageGenerator(player.getRandom().nextLong()).settle(state, h));
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.lineage.adopt.done", found.size()));
    }

    private static String firstName(String full) {
        int space = full.indexOf(' ');
        return space > 0 ? full.substring(0, space) : full;
    }

    private static String surname(String full) {
        int space = full.indexOf(' ');
        return space > 0 ? full.substring(space + 1) : "";
    }

    /** Un villageois du registre est mort dans le monde : la personne l'est aussi. */
    public static void onDeath(MinecraftServer server, Entity entity) {
        Optional<Person> p = state(server).person(entity.getUUID());
        if (p.isEmpty()) return;
        p.get().setAlive(false);
        p.get().setInWorld(null, null);
        data(server).setDirty();
    }

    /** Un royaume a disparu de l'éditeur : sa maison reste, mais ne règne plus. */
    public static void onRealmRemoved(MinecraftServer server, UUID realm) {
        state(server).forgetRealm(realm);
        data(server).setDirty();
    }

    /** Une place libre autour du joueur, en cercle, sur un sol où tenir debout. */
    private static BlockPos spot(ServerLevel level, BlockPos center, int index) {
        double angle = index * 2.399963;
        double radius = 2 + Math.sqrt(index) * 0.9;
        BlockPos base = center.offset((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius));
        for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
            BlockPos p = base.above(dy);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                    && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
                return p;
            }
        }
        return center;
    }
}
