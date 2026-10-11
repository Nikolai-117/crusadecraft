package fr.crusadecraft.core.world;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.logic.Heraldry;
import fr.crusadecraft.core.logic.TextRules;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.war.War;
import fr.crusadecraft.core.war.WarService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * L'éditeur de la carte (P12) : poser ses propres royaumes et villes, alliés ou ennemis, peindre les frontières à
 * la main, et repartir d'un monde vierge.
 *
 * <p>Tout passe par ici et tout est revérifié : le paquet du client n'est qu'une demande. Les royaumes posés à la
 * main sont des royaumes IA comme les autres (souverain, conseil, trésor, diplomatie, guerre), marqués pour
 * survivre à une régénération du monde. Une ville « à vous » est une province IA que vous tenez, comme une
 * conquête : elle dépend d'un royaume voisin, qui pourra la revendiquer.
 */
public final class WorldEditService {

    /** Qui peut ouvrir l'éditeur. */
    public enum Access {
        /** Tout le monde. */
        TOUS,
        /** L'hôte d'une partie solo (ou ouverte en LAN) et les opérateurs. */
        HOTE_ET_OPERATEURS,
        /** Personne : l'éditeur est fermé. */
        PERSONNE
    }

    /** La taille d'une ville posée à la main, qui fixe sa population de départ. */
    public enum CitySize {
        HAMEAU(12), BOURG(28), VILLE(50);

        private final int population;

        CitySize(int population) {
            this.population = population;
        }

        public int population() {
            return population;
        }

        public String key() {
            return "crusadecraft.mapedit.size." + name().toLowerCase(Locale.ROOT);
        }

        public static CitySize byOrdinal(int i) {
            CitySize[] all = values();
            return all[Math.floorMod(i, all.length)];
        }
    }

    public static final int NAME_MAX = 24;
    /** Cases peintes au plus par demande : un grand coup de pinceau arrive en plusieurs paquets. */
    public static final int MAX_CELLS = 4096;
    /** Deux sièges ne se posent pas plus près l'un de l'autre. */
    public static final int MIN_SPACING = 24;
    /** Population de départ de la capitale d'un royaume posé à la main. */
    private static final int CAPITAL_POPULATION = 40;

    private WorldEditService() {}

    /**
     * Joueurs fictifs du banc d'essai autorisés à éditer (le serveur de test donne le niveau de permission 0 même aux
     * opérateurs). Vide en jeu.
     */
    private static final java.util.Set<UUID> TEST_GRANTS = new java.util.HashSet<>();

    public static void grantForTests(UUID player, boolean granted) {
        if (granted) TEST_GRANTS.add(player);
        else TEST_GRANTS.remove(player);
    }

    public static boolean canEdit(ServerPlayer player) {
        if (TEST_GRANTS.contains(player.getUUID())) return true;
        return switch (CrusadeConfig.SERVER.mapEditor.get()) {
            case TOUS -> true;
            case PERSONNE -> false;
            case HOTE_ET_OPERATEURS -> player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
        };
    }

    private static Outcome denied() {
        return Outcome.no("crusadecraft.mapedit.denied");
    }

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    // ------------------------------------------------------------------ royaumes

    public static Outcome createRealm(ServerPlayer player, String rawName, String rawRuler, TitleRank rank, int colorIndex,
                                      AiFaith faith, AiPersonality personality, int x, int z) {
        if (!canEdit(player)) return denied();
        String name = TextRules.clean(rawName, NAME_MAX);
        if (!TextRules.isValidName(name)) return Outcome.no("crusadecraft.mapedit.name_invalid");
        String ruler = TextRules.clean(rawRuler, NAME_MAX);
        if (!ruler.isEmpty() && !TextRules.isValidName(ruler)) return Outcome.no("crusadecraft.mapedit.name_invalid");
        ServerLevel level = player.serverLevel();
        Optional<String> crowded = crowded(player.server, level.dimension().location(), x, z, -1);
        if (crowded.isPresent()) return Outcome.no("crusadecraft.mapedit.too_close", crowded.get());
        AiRealm realm = addRealm(player.server, level, name, ruler, rank, Heraldry.byOrdinal(colorIndex).rgb(), faith, personality, x, z);
        realm.discover(player.getUUID(), RealmService.currentDay(player.server));
        // Créé depuis le bâton de bornage : on trace aussitôt pour le nouveau royaume.
        fr.crusadecraft.item.BornageStaffItem.held(player).ifPresent(st -> fr.crusadecraft.item.BornageStaffItem.setOwner(st, realm.id(),
                realm.color(), AiNames.realm(realm.rank(), realm.place()).getString()));
        return Outcome.ok(Component.translatable("crusadecraft.mapedit.realm.created", AiNames.realm(realm.rank(), realm.place())));
    }

    /** Pose un royaume IA fait main, sa capitale en (x, z). Sans contrôle de droits : pour l'éditeur et les tests. */
    public static AiRealm addRealm(MinecraftServer server, ServerLevel level, String name, String ruler, TitleRank rank, int color,
                                   AiFaith faith, AiPersonality personality, int x, int z) {
        WorldState world = data(server).world();
        long seed = level.getSeed() ^ System.nanoTime() ^ ((long) x * 341873128712L) ^ ((long) z * 132897987541L);
        AiRealm realm = new WorldPlanner(WorldPlanner.Settings.defaults(), seed, null, level.dimension().location())
                .handMade(name, rank, color, faith, personality, ruler, world.nextProvinceId(), seatAt(level, x, z),
                        WorldService.nearestDejure(world, x, z), CAPITAL_POPULATION, RealmService.currentDay(server));
        world.add(realm);
        data(server).setDirty();
        CrusadeCraft.LOGGER.info("CrusadeCraft : royaume « {} » posé à la main en {}, {}", name, x, z);
        return realm;
    }

    public static Outcome editRealm(ServerPlayer player, UUID id, String rawName, TitleRank rank, int colorIndex, AiFaith faith,
                                    AiPersonality personality) {
        if (!canEdit(player)) return denied();
        Optional<AiRealm> found = data(player.server).world().realm(id);
        if (found.isEmpty()) {
            return data(player.server).realm(id).isPresent() ? Outcome.no("crusadecraft.mapedit.realm.not_ai")
                    : Outcome.no("crusadecraft.mapedit.realm.unknown");
        }
        String name = TextRules.clean(rawName, NAME_MAX);
        if (!TextRules.isValidName(name)) return Outcome.no("crusadecraft.mapedit.name_invalid");
        AiRealm realm = found.get();
        // La capitale porte le nom du royaume : elle suit le changement si elle le portait.
        AiProvince capital = realm.capital();
        if (capital != null && capital.name().equals(realm.place())) capital.setName(name);
        realm.setPlace(name);
        realm.setRank(rank);
        realm.setColor(Heraldry.byOrdinal(colorIndex).rgb());
        realm.setFaith(faith);
        realm.setPersonality(personality);
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.mapedit.realm.edited", AiNames.realm(rank, name)));
    }

    public static Outcome deleteRealm(ServerPlayer player, UUID id) {
        if (!canEdit(player)) return denied();
        Optional<AiRealm> found = data(player.server).world().realm(id);
        if (found.isEmpty()) {
            return data(player.server).realm(id).isPresent() ? Outcome.no("crusadecraft.mapedit.realm.not_ai")
                    : Outcome.no("crusadecraft.mapedit.realm.unknown");
        }
        Component name = AiNames.realm(found.get().rank(), found.get().place());
        removeRealm(player.server, id);
        return Outcome.ok(Component.translatable("crusadecraft.mapedit.realm.deleted", name));
    }

    /**
     * Efface un royaume IA et tout ce qui le désigne : ses guerres finissent en paix blanche, ses alliances, ses
     * coalitions, ses lettres en attente et ses frontières disparaissent ; ce qu'il avait conquis revient à ses
     * propriétaires, et ce que d'autres lui avaient pris reste à ceux qui le tiennent.
     */
    public static void removeRealm(MinecraftServer server, UUID id) {
        CrusadeSavedData data = data(server);
        WorldState world = data.world();
        Optional<AiRealm> found = world.realm(id);
        if (found.isEmpty()) return;
        AiRealm realm = found.get();
        for (War w : new ArrayList<>(data.wars().all())) {
            if (w.attacker().equals(id) || w.defender().equals(id)) {
                WarService.endWar(server, w, 0);
            } else {
                w.attackerAllies().remove(id);
                w.defenderAllies().remove(id);
            }
        }
        for (fr.crusadecraft.core.ai.Coalition c : world.coalitions()) c.members().remove(id);
        world.coalitions().removeIf(c -> c.members().isEmpty());
        data.diplomacy().forget(id);
        data.warMap().forget(id);
        for (Realm r : data.realms()) r.mail().removeIf(l -> id.equals(l.from()));
        for (AiRealm other : world.realms()) {
            for (AiProvince p : other.provinces()) if (id.equals(p.holder())) p.setHolder(null);
        }
        List<AiProvince> keptByOthers = new ArrayList<>();
        List<Integer> gone = new ArrayList<>();
        for (AiProvince p : realm.provinces()) {
            if (p.holder() != null && !p.holder().equals(id)) keptByOthers.add(p);
            else gone.add(p.id());
        }
        world.remove(id);
        // Une province qu'un autre tient change seulement de royaume d'origine : le plus proche qui reste.
        for (AiProvince p : keptByOthers) {
            Optional<AiRealm> host = nearestAi(world, p.dimension(), p.seat().getX(), p.seat().getZ(), null);
            if (host.isEmpty()) {
                gone.add(p.id());
                continue;
            }
            AiProvince moved = new AiProvince(p.id(), p.name(), p.dimension(), p.seat(), p.anchor(), false, p.dejureKingdom(),
                    p.population(), p.development());
            moved.setHolder(p.holder().equals(host.get().id()) ? null : p.holder());
            host.get().provinces().add(moved);
        }
        for (Realm r : data.realms()) r.claims().removeAll(gone);
        // Avant qu'un numéro de province ne resserve : la fiche d'une ville effacée n'ira pas à la suivante.
        for (int g : gone) fr.crusadecraft.core.place.PlaceService.forget(server, fr.crusadecraft.core.place.Lieu.Ref.aiProvince(g));
        fr.crusadecraft.core.place.PlaceService.forget(server, fr.crusadecraft.core.place.Lieu.Ref.aiRealm(id));
        world.borders().forget(id);
        fr.crusadecraft.core.lineage.LineageService.onRealmRemoved(server, id);
        fr.crusadecraft.core.build.SiteService.onRealmRemoved(server, id);
        data.setDirty();
        CrusadeCraft.LOGGER.info("CrusadeCraft : royaume IA {} effacé dans l'éditeur de la carte", realm.place());
    }

    /** Monde vierge : tous les royaumes générés disparaissent, ceux posés à la main restent, et rien ne sera regénéré. */
    public static Outcome wipe(ServerPlayer player) {
        if (!canEdit(player)) return denied();
        int n = wipeGenerated(player.server);
        return Outcome.ok(Component.translatable("crusadecraft.mapedit.wipe.done", n));
    }

    public static int wipeGenerated(MinecraftServer server) {
        WorldState world = data(server).world();
        List<UUID> generated = new ArrayList<>();
        for (AiRealm r : world.realms()) if (!r.handMade()) generated.add(r.id());
        for (UUID id : generated) removeRealm(server, id);
        world.markGenerated(RealmService.currentDay(server));
        data(server).setDirty();
        return generated.size();
    }

    // ------------------------------------------------------------------ villes

    public static Outcome createCity(ServerPlayer player, UUID owner, String rawName, CitySize size, int x, int z) {
        if (!canEdit(player)) return denied();
        String name = TextRules.clean(rawName, NAME_MAX);
        if (!TextRules.isValidName(name)) return Outcome.no("crusadecraft.mapedit.name_invalid");
        ServerLevel level = player.serverLevel();
        ResourceLocation dim = level.dimension().location();
        Optional<String> crowded = crowded(player.server, dim, x, z, -1);
        if (crowded.isPresent()) return Outcome.no("crusadecraft.mapedit.too_close", crowded.get());
        Optional<AiProvince> city = addCity(player.server, level, owner, name, size, x, z);
        if (city.isEmpty()) {
            return data(player.server).realm(owner).isPresent() ? Outcome.no("crusadecraft.mapedit.city.no_host")
                    : Outcome.no("crusadecraft.mapedit.realm.unknown");
        }
        return Outcome.ok(Component.translatable("crusadecraft.mapedit.city.created", name,
                fr.crusadecraft.core.diplomacy.DiplomacyService.name(player.server, owner)));
    }

    /**
     * Pose une ville pour ce royaume. Pour un royaume IA, elle s'ajoute à ses provinces. Pour un royaume de joueur,
     * elle naît dans le royaume IA dont on a peint les terres à cet endroit (sinon le plus proche), et le joueur la
     * tient. Vide si le royaume n'existe pas, ou s'il n'y a aucun royaume IA pour accueillir la ville d'un joueur.
     */
    public static Optional<AiProvince> addCity(MinecraftServer server, ServerLevel level, UUID owner, String name, CitySize size, int x, int z) {
        WorldState world = data(server).world();
        ResourceLocation dim = level.dimension().location();
        Optional<AiRealm> host = world.realm(owner);
        UUID holder = null;
        if (host.isEmpty()) {
            if (data(server).realm(owner).isEmpty()) return Optional.empty();
            holder = owner;
            Optional<UUID> painted = world.borders().at(dim, x, z);
            host = painted.flatMap(world::realm);
            if (host.isEmpty()) host = nearestAi(world, dim, x, z, null);
            if (host.isEmpty()) return Optional.empty();
        }
        AiProvince city = new AiProvince(world.nextProvinceId(), name, dim, seatAt(level, x, z), AiProvince.Anchor.LAND, false,
                WorldService.nearestDejure(world, x, z), size.population(), 15);
        city.setHolder(holder);
        host.get().provinces().add(city);
        data(server).setDirty();
        return Optional.of(city);
    }

    public static Outcome renameCity(ServerPlayer player, int provinceId, String rawName) {
        if (!canEdit(player)) return denied();
        Optional<Map.Entry<AiRealm, AiProvince>> found = province(player.server, provinceId);
        if (found.isEmpty()) return Outcome.no("crusadecraft.mapedit.city.unknown");
        String name = TextRules.clean(rawName, NAME_MAX);
        if (!TextRules.isValidName(name)) return Outcome.no("crusadecraft.mapedit.name_invalid");
        found.get().getValue().setName(name);
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.mapedit.city.renamed", name));
    }

    public static Outcome moveCity(ServerPlayer player, int provinceId, int x, int z) {
        if (!canEdit(player)) return denied();
        Optional<Map.Entry<AiRealm, AiProvince>> found = province(player.server, provinceId);
        if (found.isEmpty()) return Outcome.no("crusadecraft.mapedit.city.unknown");
        AiProvince city = found.get().getValue();
        ServerLevel level = player.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                city.dimension()));
        if (level == null) return Outcome.no("crusadecraft.mapedit.city.unknown");
        Optional<String> crowded = crowded(player.server, city.dimension(), x, z, provinceId);
        if (crowded.isPresent()) return Outcome.no("crusadecraft.mapedit.too_close", crowded.get());
        city.setSeat(seatAt(level, x, z));
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.mapedit.city.moved", city.name()));
    }

    public static Outcome giveCity(ServerPlayer player, int provinceId, UUID newHolder) {
        if (!canEdit(player)) return denied();
        Optional<Map.Entry<AiRealm, AiProvince>> found = province(player.server, provinceId);
        if (found.isEmpty()) return Outcome.no("crusadecraft.mapedit.city.unknown");
        if (data(player.server).world().realm(newHolder).isEmpty() && data(player.server).realm(newHolder).isEmpty()) {
            return Outcome.no("crusadecraft.mapedit.realm.unknown");
        }
        AiRealm host = found.get().getKey();
        AiProvince city = found.get().getValue();
        city.setHolder(newHolder.equals(host.id()) ? null : newHolder);
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.mapedit.city.given", city.name(),
                fr.crusadecraft.core.diplomacy.DiplomacyService.name(player.server, newHolder)));
    }

    public static Outcome deleteCity(ServerPlayer player, int provinceId) {
        if (!canEdit(player)) return denied();
        Optional<Map.Entry<AiRealm, AiProvince>> found = province(player.server, provinceId);
        if (found.isEmpty()) return Outcome.no("crusadecraft.mapedit.city.unknown");
        AiRealm host = found.get().getKey();
        AiProvince city = found.get().getValue();
        if (city.capital()) return Outcome.no("crusadecraft.mapedit.city.capital", AiNames.realm(host.rank(), host.place()));
        host.provinces().remove(city);
        for (Realm r : data(player.server).realms()) r.claims().remove(city.id());
        fr.crusadecraft.core.place.PlaceService.forget(player.server, fr.crusadecraft.core.place.Lieu.Ref.aiProvince(city.id()));
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.mapedit.city.deleted", city.name()));
    }

    // ------------------------------------------------------------------ frontières

    /**
     * Peint des cases. {@code owner} : le royaume à qui elles reviennent ; {@code neutral} : terre libre ; ni l'un ni
     * l'autre : la gomme, qui rend le territoire automatique.
     */
    public static Outcome paint(ServerPlayer player, UUID owner, boolean neutral, long[] cells) {
        if (!canEdit(player)) return denied();
        if (cells.length > MAX_CELLS) return Outcome.no("crusadecraft.mapedit.paint.too_many");
        CrusadeSavedData data = data(player.server);
        UUID value = neutral ? TerritoryMap.NEUTRAL : owner;
        if (value != null && !neutral && data.world().realm(value).isEmpty() && data.realm(value).isEmpty()) {
            return Outcome.no("crusadecraft.mapedit.realm.unknown");
        }
        int changed = data.world().borders().paint(player.level().dimension().location(), cells, value);
        if (changed > 0) data.setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.mapedit.paint.done", changed));
    }

    // ------------------------------------------------------------------ outils

    /** La province IA de cet identifiant, avec le royaume qui la porte dans ses listes. */
    static Optional<Map.Entry<AiRealm, AiProvince>> province(MinecraftServer server, int id) {
        for (AiRealm r : data(server).world().realms()) {
            for (AiProvince p : r.provinces()) if (p.id() == id) return Optional.of(Map.entry(r, p));
        }
        return Optional.empty();
    }

    /** Le siège trop proche de (x, z), s'il y en a un (on ignore la province {@code except} qu'on déplace). */
    static Optional<String> crowded(MinecraftServer server, ResourceLocation dim, int x, int z, int except) {
        double min = (double) MIN_SPACING * MIN_SPACING;
        for (AiRealm r : data(server).world().realms()) {
            for (AiProvince p : r.provinces()) {
                if (p.id() != except && p.dimension().equals(dim) && p.distanceSqr(x + 0.5, z + 0.5) < min) return Optional.of(p.name());
            }
        }
        return Optional.empty();
    }

    /** Le royaume IA dont la capitale est la plus proche, en excluant {@code except}. */
    static Optional<AiRealm> nearestAi(WorldState world, ResourceLocation dim, double x, double z, UUID except) {
        AiRealm best = null;
        double bestD = Double.MAX_VALUE;
        for (AiRealm r : world.realms()) {
            if (r.id().equals(except)) continue;
            AiProvince capital = r.capital();
            if (capital == null || !capital.dimension().equals(dim)) continue;
            double d = capital.distanceSqr(x, z);
            if (d < bestD) {
                bestD = d;
                best = r;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Le siège posé au sol, sans générer de chunk ; au niveau de la mer sur l'eau. */
    private static BlockPos seatAt(ServerLevel level, int x, int z) {
        Integer y = VillageScanner.landProbe(level).surface(x, z);
        return new BlockPos(x, y == null ? level.getSeaLevel() : y, z);
    }
}
