package fr.crusadecraft.core.place;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.Territory;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Les lieux (P15) : leur mise à jour d'après le monde, leur hiérarchie, leurs fiches, et ce que chacun peut en faire.
 *
 * <p>Le monde fait foi. Chaque royaume, province, colonie de joueur et construction a son lieu, créé à la première
 * occasion ; il disparaît avec la chose, sauf s'il porte une fiche : il reste alors, « disparu du monde », et c'est au
 * joueur de le supprimer. Le parent d'un lieu est celui que le monde donne (le suzerain d'un royaume, le royaume qui
 * tient une ville, la ville d'une construction), sauf si l'éditeur en a choisi un autre.
 */
public final class PlaceService {
    /** Au plus ce nombre de lieux posés à la main. */
    public static final int MAX_MANUAL = 500;
    /** Une place à moins de cette distance donne le « près de… » de la position. */
    static final int NEAR = 96;
    private static final int NEUTRAL_COLOR = 0x9A8A70;

    private PlaceService() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    // ------------------------------------------------------------------ mise à jour d'après le monde

    /** Crée les lieux qui manquent, rafraîchit les copies, retire (ou garde, s'ils ont une fiche) ceux qui ont disparu. */
    public static void sync(MinecraftServer server) {
        CrusadeSavedData data = data(server);
        PlaceState places = data.places();
        boolean[] changed = {false};
        Set<Lieu.Ref> alive = new HashSet<>();
        Set<Integer> villages = new HashSet<>();
        for (Site s : data.world().sites()) if (s.kind() == SiteKind.VILLAGE && s.province() >= 0) villages.add(s.province());
        for (AiRealm r : data.world().realms()) {
            Lieu l = ensure(places, Lieu.Ref.aiRealm(r.id()), LieuKind.of(r.rank()), r.place(), true, r.foundedDay(), changed);
            AiProvince capital = r.capital();
            if (capital != null) place(l, capital.dimension(), capital.seat(), changed);
            alive.add(l.ref());
            for (AiProvince p : r.provinces()) {
                Lieu lp = ensure(places, Lieu.Ref.aiProvince(p.id()), villages.contains(p.id()) ? LieuKind.VILLAGE : LieuKind.VILLE,
                        p.name(), false, r.foundedDay(), changed);
                place(lp, p.dimension(), p.seat(), changed);
                alive.add(lp.ref());
            }
        }
        boolean colonies = MineColoniesBridge.isReady();
        for (Realm realm : data.realms()) {
            Lieu l = ensure(places, Lieu.Ref.playerRealm(realm.id()), LieuKind.of(realm.rank()), realm.name(), false, realm.foundedDay(), changed);
            alive.add(l.ref());
            for (ColonyRef ref : realm.allColonies()) {
                String name = null;
                Optional<BlockPos> center = Optional.empty();
                if (colonies) {
                    ColonyInfo info = MineColoniesBridge.info(ref);
                    if (info != null && !info.name().isBlank()) name = info.name();
                    center = MineColoniesBridge.center(ref);
                }
                Lieu lc = ensure(places, Lieu.Ref.colony(ref.dimension(), ref.colonyId()), LieuKind.VILLE, name, false, realm.foundedDay(), changed);
                if (center.isPresent()) {
                    place(lc, ref.dimension(), center.get(), changed);
                    if (ref.equals(realm.capital())) place(l, ref.dimension(), center.get(), changed);
                }
                alive.add(lc.ref());
            }
        }
        for (Site s : data.world().sites()) {
            LieuKind k = LieuKind.of(s.kind());
            if (k == null) continue;
            Lieu l = ensure(places, Lieu.Ref.site(s.id()), k, s.name(), true, s.createdDay(), changed);
            place(l, s.dimension(), s.anchor(), changed);
            alive.add(l.ref());
        }
        for (Lieu l : new ArrayList<>(places.all())) {
            if (l.ref() == null || alive.contains(l.ref())) continue;
            drop(places, l);
            changed[0] = true;
        }
        if (changed[0]) data.setDirty();
    }

    /**
     * La chose à laquelle renvoyait ce lieu disparaît : à appeler <b>avant</b> qu'un numéro de province puisse être
     * réutilisé, sans quoi la fiche d'une ville effacée irait à la suivante.
     */
    public static void forget(MinecraftServer server, Lieu.Ref ref) {
        PlaceState places = data(server).places();
        places.byRef(ref).ifPresent(l -> {
            drop(places, l);
            data(server).setDirty();
        });
    }

    private static void drop(PlaceState places, Lieu l) {
        if (l.text().isBlank()) places.remove(l.id());
        else places.detach(l);
    }

    private static Lieu ensure(PlaceState places, Lieu.Ref ref, LieuKind kind, String name, boolean composed, long founded, boolean[] changed) {
        Optional<Lieu> found = places.byRef(ref);
        if (found.isPresent()) {
            Lieu l = found.get();
            if (l.kind() != kind) {
                l.setKind(kind);
                changed[0] = true;
            }
            if (name != null && !name.isBlank() && !name.equals(l.name())) {
                l.setName(name);
                changed[0] = true;
            }
            if (l.composed() != composed) {
                l.setComposed(composed);
                changed[0] = true;
            }
            return l;
        }
        Lieu l = new Lieu(UUID.randomUUID(), kind, ref, name == null ? "" : name);
        l.setComposed(composed);
        l.setFoundedDay(founded);
        places.add(l);
        changed[0] = true;
        return l;
    }

    private static void place(Lieu l, ResourceLocation dim, BlockPos pos, boolean[] changed) {
        if (pos == null || dim == null) return;
        if (dim.equals(l.dimension()) && pos.equals(l.anchor())) return;
        l.setPlace(dim, pos);
        changed[0] = true;
    }

    // ------------------------------------------------------------------ lecture

    /** Ce que le monde dit d'un lieu, lu une fois pour une série de questions. */
    public static final class Resolver {
        private final MinecraftServer server;
        private final CrusadeSavedData data;
        private final Map<UUID, AiRealm> aiRealms = new HashMap<>();
        private final Map<Integer, AiRealm> provinceRealm = new HashMap<>();
        private final Map<Integer, AiProvince> provinces = new HashMap<>();
        private final Map<ColonyRef, Realm> colonyRealm = new HashMap<>();
        private final Map<UUID, UUID> parentCache = new HashMap<>();

        public Resolver(MinecraftServer server) {
            this.server = server;
            this.data = data(server);
            for (AiRealm r : data.world().realms()) {
                aiRealms.put(r.id(), r);
                for (AiProvince p : r.provinces()) {
                    provinces.put(p.id(), p);
                    provinceRealm.put(p.id(), r);
                }
            }
            for (Realm realm : data.realms()) for (ColonyRef c : realm.allColonies()) colonyRealm.put(c, realm);
        }

        public PlaceState places() {
            return data.places();
        }

        /** Le lieu d'un royaume, IA ou joueur. */
        public Optional<Lieu> realmLieu(UUID realm) {
            if (realm == null) return Optional.empty();
            Optional<Lieu> ai = data.places().byRef(Lieu.Ref.aiRealm(realm));
            return ai.isPresent() ? ai : data.places().byRef(Lieu.Ref.playerRealm(realm));
        }

        /** Le parent effectif : choisi à la main s'il existe encore, sinon celui que le monde donne. */
        public UUID parentOf(UUID id) {
            if (parentCache.containsKey(id)) return parentCache.get(id);
            UUID p = data.places().get(id).map(this::computeParent).orElse(null);
            parentCache.put(id, p);
            return p;
        }

        private UUID computeParent(Lieu l) {
            if (l.parent() != null && data.places().get(l.parent()).isPresent()) return l.parent();
            return defaultParent(l).map(Lieu::id).orElse(null);
        }

        /** Le parent que le monde donne. */
        public Optional<Lieu> defaultParent(Lieu l) {
            Lieu.Ref ref = l.ref();
            // Un lieu saint, une ruine, une région relèvent du royaume qui les tient. (Pas un titre : il regroupe,
            // et son tenancier peut très bien être rangé sous lui.)
            if (ref == null) return l.kind().placed() ? realmLieu(l.holder()) : Optional.empty();
            return switch (ref.type()) {
                case AI_REALM, PLAYER_REALM -> realmLieu(data.diplomacy().suzerainOf(ref.uuid()).orElse(null));
                case AI_PROVINCE -> {
                    AiProvince p = provinces.get(ref.index());
                    AiRealm r = provinceRealm.get(ref.index());
                    yield p == null ? Optional.empty() : realmLieu(p.holder() != null ? p.holder() : r.id());
                }
                case COLONY -> {
                    Realm realm = colonyRealm.get(new ColonyRef(ref.dim(), ref.index()));
                    yield realm == null ? Optional.empty() : realmLieu(realm.id());
                }
                case SITE -> {
                    Optional<Site> s = data.world().site(ref.uuid());
                    if (s.isEmpty()) yield Optional.empty();
                    Optional<Lieu> city = s.get().province() >= 0 ? data.places().byRef(Lieu.Ref.aiProvince(s.get().province())) : Optional.empty();
                    yield city.isPresent() ? city : realmLieu(s.get().owner());
                }
            };
        }

        /** Le royaume qui tient ce lieu ({@code null} : personne). */
        public UUID holderRealm(Lieu l) {
            Lieu.Ref ref = l.ref();
            if (ref == null) return l.holder();
            return switch (ref.type()) {
                case AI_REALM, PLAYER_REALM -> ref.uuid();
                case AI_PROVINCE -> {
                    AiProvince p = provinces.get(ref.index());
                    AiRealm r = provinceRealm.get(ref.index());
                    yield p == null ? null : p.holder() != null ? p.holder() : r.id();
                }
                case COLONY -> {
                    Realm realm = colonyRealm.get(new ColonyRef(ref.dim(), ref.index()));
                    yield realm == null ? null : realm.id();
                }
                case SITE -> data.world().site(ref.uuid()).map(Site::owner).orElse(null);
            };
        }

        /** Le nom à afficher. */
        public Component name(Lieu l) {
            Lieu.Ref ref = l.ref();
            if (ref != null) {
                switch (ref.type()) {
                    case AI_REALM -> {
                        AiRealm r = aiRealms.get(ref.uuid());
                        if (r != null) return AiNames.realm(r.rank(), r.place());
                    }
                    case SITE -> {
                        Optional<Site> s = data.world().site(ref.uuid());
                        if (s.isPresent()) return SiteService.displayName(s.get());
                    }
                    default -> { }
                }
            }
            return composedName(l);
        }

        /** Le nom d'un lieu posé à la main ou disparu : « Duché de Normandie », « Fort de Valbrume », ou tel quel. */
        public static Component composedName(Lieu l) {
            if (!l.composed()) return Component.literal(l.name());
            if (l.kind().title()) return AiNames.realm(l.kind().rank(), l.name());
            return Component.translatable(AiNames.elidesPublic(l.name()) ? "crusadecraft.site.named.elided" : "crusadecraft.site.named",
                    Component.translatable(l.kind().key()), l.name());
        }

        /** Qui le tient : le souverain d'un royaume, sinon le royaume qui tient le lieu. */
        public Component holderName(Lieu l) {
            Lieu.Ref ref = l.ref();
            if (ref != null && ref.type() == Lieu.RefType.AI_REALM) {
                AiRealm r = aiRealms.get(ref.uuid());
                if (r != null) return Component.translatable("crusadecraft.lieu.ruler", r.ruler().name(),
                        Component.translatable(r.rank().holderKey(r.ruler().female())));
            }
            if (ref != null && ref.type() == Lieu.RefType.PLAYER_REALM) {
                Optional<Realm> r = data.realm(ref.uuid());
                if (r.isPresent()) return Component.translatable("crusadecraft.lieu.ruler", r.get().rulerName(),
                        Component.translatable(r.get().rank().holderKey(r.get().feminineTitle())));
            }
            UUID holder = holderRealm(l);
            if (holder == null) return Component.translatable("crusadecraft.lieu.nobody");
            return realmLieu(holder).map(this::name).orElse(Component.translatable("crusadecraft.lieu.nobody"));
        }

        /** D'où vient le lieu. */
        public Component origin(Lieu l) {
            if (l.ref() == null) return Component.translatable(l.detached() ? "crusadecraft.lieu.origin.detached" : "crusadecraft.lieu.origin.manual");
            return Component.translatable("crusadecraft.lieu.origin." + l.ref().type().name().toLowerCase(Locale.ROOT));
        }

        /** Sa propre population (sans ses enfants). */
        public int ownPopulation(Lieu l) {
            Lieu.Ref ref = l.ref();
            if (ref == null) return 0;
            if (ref.type() == Lieu.RefType.AI_PROVINCE) {
                AiProvince p = provinces.get(ref.index());
                return p == null ? 0 : p.population();
            }
            if (ref.type() == Lieu.RefType.COLONY && MineColoniesBridge.isReady()) {
                ColonyInfo info = MineColoniesBridge.info(new ColonyRef(ref.dim(), ref.index()));
                return info == null ? 0 : info.citizens();
            }
            return 0;
        }

        /** Sa couleur : celle du royaume qui le tient. */
        public int color(Lieu l) {
            if (l.ref() != null && l.ref().type() == Lieu.RefType.SITE) {
                Optional<Site> s = data.world().site(l.ref().uuid());
                if (s.isPresent()) return s.get().color();
            }
            UUID holder = holderRealm(l);
            if (holder != null) {
                AiRealm r = aiRealms.get(holder);
                if (r != null) return r.color();
                Optional<Realm> realm = data.realm(holder);
                if (realm.isPresent()) return realm.get().color();
            }
            return NEUTRAL_COLOR;
        }

        /** Ce joueur connaît-il ce lieu ? (Brouillard : les royaumes IA qu'il n'a pas découverts restent cachés.) */
        public boolean visible(Lieu l, ServerPlayer viewer, UUID viewerRealm) {
            Lieu.Ref ref = l.ref();
            if (ref == null) return true;
            return switch (ref.type()) {
                case AI_REALM -> {
                    AiRealm r = aiRealms.get(ref.uuid());
                    yield r == null || r.discoveredBy(viewer.getUUID());
                }
                case AI_PROVINCE -> {
                    AiProvince p = provinces.get(ref.index());
                    AiRealm r = provinceRealm.get(ref.index());
                    yield r == null || r.discoveredBy(viewer.getUUID()) || p != null && viewerRealm != null && viewerRealm.equals(p.holder());
                }
                case SITE -> {
                    UUID owner = holderRealm(l);
                    AiRealm r = owner == null ? null : aiRealms.get(owner);
                    yield r == null || r.discoveredBy(viewer.getUUID());
                }
                default -> true;
            };
        }
    }

    // ------------------------------------------------------------------ la position

    /**
     * Où l'on est : la place la plus proche (à moins de {@value #NEAR} blocs), la région qui contient le point, puis le
     * royaume qui tient la terre et ceux dont il relève, du plus proche au plus vaste.
     */
    public record Chain(Lieu near, int nearDistance, Lieu region, List<Lieu> realms) {
        public boolean empty() {
            return near == null && region == null && realms.isEmpty();
        }
    }

    public static Chain chain(MinecraftServer server, ResourceLocation dim, double x, double z) {
        sync(server);
        Resolver res = new Resolver(server);
        Lieu region = null, near = null;
        double regionD = Double.MAX_VALUE, nearD = (double) NEAR * NEAR;
        for (Lieu l : res.places().all()) {
            if (l.anchor() == null || !dim.equals(l.dimension())) continue;
            double dx = l.anchor().getX() + 0.5 - x, dz = l.anchor().getZ() + 0.5 - z, d = dx * dx + dz * dz;
            if (l.kind() == LieuKind.REGION) {
                if (d <= (double) l.radius() * l.radius() && d < regionD) {
                    regionD = d;
                    region = l;
                }
            } else if (!l.kind().title() && d < nearD) {
                nearD = d;
                near = l;
            }
        }
        List<Lieu> realms = new ArrayList<>();
        Optional<Lieu> realm = Territory.ownerAt(server, dim, x, z).flatMap(res::realmLieu);
        Set<UUID> seen = new HashSet<>();
        for (UUID at = realm.map(Lieu::id).orElse(null); at != null && seen.add(at) && seen.size() <= 8; at = res.parentOf(at)) {
            res.places().get(at).ifPresent(realms::add);
        }
        return new Chain(near, near == null ? 0 : (int) Math.round(Math.sqrt(nearD)), region, realms);
    }

    /** « près de Hautemarche › Forêt de Brocéliande › Comté de Valcourt › Royaume d'Ambrevent ». */
    public static Component chainAt(MinecraftServer server, ResourceLocation dim, double x, double z) {
        Chain c = chain(server, dim, x, z);
        if (c.empty()) return Component.translatable("crusadecraft.lieu.here.wild");
        Resolver res = new Resolver(server);
        List<Component> parts = new ArrayList<>();
        // « Abbaye de Saint-Aubin, à 12 blocs » : sans article à deviner devant un nom que le joueur a choisi.
        if (c.near() != null) parts.add(Component.translatable("crusadecraft.lieu.near", res.name(c.near()), c.nearDistance()));
        if (c.region() != null) parts.add(res.name(c.region()));
        for (Lieu l : c.realms()) parts.add(res.name(l));
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(Component.literal(" › "));
            out.append(parts.get(i));
        }
        return out;
    }

    // ------------------------------------------------------------------ la carte

    /** Les lieux posés à la main (ou disparus) qui ont une position dans cette dimension, pour la carte du mod. */
    public static List<fr.crusadecraft.core.world.MapView.Place> mapPlaces(MinecraftServer server, ResourceLocation dim) {
        Resolver res = new Resolver(server);
        List<fr.crusadecraft.core.world.MapView.Place> out = new ArrayList<>();
        for (Lieu l : res.places().all()) {
            if (l.ref() != null || l.anchor() == null || l.kind().title() || !dim.equals(l.dimension())) continue;
            out.add(new fr.crusadecraft.core.world.MapView.Place(l.id(), l.kind().ordinal(), res.name(l), l.anchor().getX(), l.anchor().getZ(),
                    l.radius(), res.color(l)));
        }
        return out;
    }

    // ------------------------------------------------------------------ la vue d'un joueur

    public static LieuView view(ServerPlayer player, UUID select, Component status, boolean ok) {
        MinecraftServer server = player.server;
        sync(server);
        Resolver res = new Resolver(server);
        boolean editor = WorldEditService.canEdit(player);
        UUID mine = RealmService.realmOf(server, player.getUUID()).map(Realm::id).orElse(null);
        List<UUID> ids = new ArrayList<>();
        for (Lieu l : res.places().all()) if (editor || res.visible(l, player, mine)) ids.add(l.id());
        Set<UUID> shown = new HashSet<>(ids);
        Comparator<UUID> order = Comparator.<UUID>comparingInt(id -> res.places().get(id).map(l -> l.kind().ordinal()).orElse(99))
                .thenComparing(id -> res.places().get(id).map(l -> l.name().toLowerCase(Locale.ROOT)).orElse(""));
        List<PlaceState.Row> rows = PlaceState.tree(ids, res::parentOf, order);
        // Population : la sienne, plus celle de tout ce qui en relève.
        Map<UUID, Integer> population = new HashMap<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            UUID id = rows.get(i).id();
            Lieu l = res.places().get(id).orElseThrow();
            int total = population.getOrDefault(id, 0) + res.ownPopulation(l);
            population.put(id, total);
            UUID parent = res.parentOf(id);
            if (parent != null && shown.contains(parent)) population.merge(parent, total, Integer::sum);
        }
        List<LieuView.Entry> entries = new ArrayList<>(rows.size());
        for (PlaceState.Row row : rows) {
            Lieu l = res.places().get(row.id()).orElseThrow();
            entries.add(new LieuView.Entry(l.id(), l.kind().ordinal(), res.name(l), row.depth(), res.color(l), l.linked(),
                    population.getOrDefault(l.id(), 0), !l.text().isBlank()));
        }
        LieuView.Selection selection = null;
        Optional<Lieu> chosen = select == null ? Optional.empty() : res.places().get(select).filter(l -> shown.contains(l.id()));
        if (chosen.isPresent()) {
            Lieu l = chosen.get();
            UUID parent = res.parentOf(l.id());
            boolean write = canWrite(player, l, res, editor, mine);
            boolean site = l.ref() != null && l.ref().type() == Lieu.RefType.SITE;
            selection = new LieuView.Selection(l.id(), l.kind().ordinal(), res.name(l), res.origin(l), res.holderName(l), parent,
                    parent == null ? Component.translatable("crusadecraft.lieu.none") : res.places().get(parent).map(res::name).orElse(Component.empty()),
                    population.getOrDefault(l.id(), 0), l.anchor() != null, l.anchor() == null ? 0 : l.anchor().getX(),
                    l.anchor() == null ? 0 : l.anchor().getZ(), l.radius(), l.foundedDay(), l.text(),
                    site ? data(server).world().site(l.ref().uuid()).map(Site::name).orElse(l.name()) : l.name(),
                    write, write && (l.ref() == null || site), write && l.ref() == null, editor);
        }
        List<LieuView.Choice> realms = new ArrayList<>();
        if (editor) {
            for (AiRealm r : data(server).world().realms()) realms.add(new LieuView.Choice(r.id(), AiNames.realm(r.rank(), r.place())));
            for (Realm r : data(server).realms()) realms.add(new LieuView.Choice(r.id(), Component.literal(r.name())));
        }
        Component here = chainAt(server, player.level().dimension().location(), player.getX(), player.getZ());
        return new LieuView(editor, entries, selection, here, realms, status, ok);
    }

    private static boolean canWrite(ServerPlayer player, Lieu l, Resolver res, boolean editor, UUID mine) {
        if (editor) return true;
        if (mine == null) return false;
        return mine.equals(res.holderRealm(l));
    }

    // ------------------------------------------------------------------ actions

    /** Le résultat d'une action, avec le lieu à montrer ensuite. */
    public record Result(Outcome outcome, UUID select) {
        static Result no(String key, Object... args) {
            return new Result(Outcome.no(key, args), null);
        }
    }

    /**
     * Pose un lieu à la main. Dans l'éditeur : n'importe quel genre posable, n'importe où. En survie : un lieu saint,
     * une ruine ou une région, sur ses propres terres, tenu par son royaume.
     */
    public static Result create(ServerPlayer player, LieuKind kind, String rawName, UUID parent, BlockPos pos, int radius) {
        MinecraftServer server = player.server;
        String name = rawName == null ? "" : rawName.trim();
        if (!kind.manual()) return Result.no("crusadecraft.lieu.kind_refused");
        if (name.isEmpty() || name.length() > Lieu.MAX_NAME) return Result.no("crusadecraft.lieu.bad_name", Lieu.MAX_NAME);
        boolean editor = WorldEditService.canEdit(player);
        UUID mine = RealmService.realmOf(server, player.getUUID()).map(Realm::id).orElse(null);
        ResourceLocation dim = player.level().dimension().location();
        if (!editor) {
            if (!kind.placed()) return Result.no("crusadecraft.mapedit.denied");
            if (mine == null) return Result.no("crusadecraft.lieu.no_realm");
            if (pos == null || !Territory.ownerAt(server, dim, pos.getX() + 0.5, pos.getZ() + 0.5).map(mine::equals).orElse(false)) {
                return Result.no("crusadecraft.lieu.not_your_land");
            }
        }
        PlaceState places = data(server).places();
        if (places.all().stream().filter(l -> l.ref() == null).count() >= MAX_MANUAL) return Result.no("crusadecraft.lieu.too_many", MAX_MANUAL);
        Lieu l = new Lieu(UUID.randomUUID(), kind, null, name);
        l.setComposed(kind.title());
        l.setFoundedDay(RealmService.currentDay(server));
        if (kind.placed() && pos != null) l.setPlace(dim, pos);
        if (kind == LieuKind.REGION) l.setRadius(radius <= 0 ? 128 : radius);
        // Tenu par le royaume du joueur en survie ; dans l'éditeur, par celui qui tient la terre où on le pose.
        if (!editor) l.setHolder(mine);
        else if (kind.placed() && pos != null) l.setHolder(Territory.ownerAt(server, dim, pos.getX() + 0.5, pos.getZ() + 0.5).orElse(null));
        if (editor && parent != null && places.get(parent).isPresent()) l.setParent(parent);
        places.add(l);
        data(server).setDirty();
        return new Result(Outcome.ok(Component.translatable("crusadecraft.lieu.created", Resolver.composedName(l))), l.id());
    }

    public static Result rename(ServerPlayer player, UUID id, String rawName) {
        Resolver res = new Resolver(player.server);
        Optional<Lieu> found = res.places().get(id);
        if (found.isEmpty()) return Result.no("crusadecraft.lieu.unknown");
        Lieu l = found.get();
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > Lieu.MAX_NAME) return Result.no("crusadecraft.lieu.bad_name", Lieu.MAX_NAME);
        if (!canWrite(player, l, res, WorldEditService.canEdit(player), RealmService.realmOf(player.server, player.getUUID()).map(Realm::id).orElse(null))) {
            return Result.no("crusadecraft.mapedit.denied");
        }
        if (l.ref() != null) {
            // P32 : un royaume, une ville ou une colonie se renomment à leur source ; le lieu suit.
            if (l.ref().type() != Lieu.RefType.SITE) {
                Outcome out = fr.crusadecraft.core.realm.NameService.renameSource(player, l.ref(), name);
                if (!out.success()) return new Result(out, null);
                l.setName(name);
                data(player.server).setDirty();
                return new Result(out, id);
            }
            Optional<Site> site = data(player.server).world().site(l.ref().uuid());
            if (site.isEmpty()) return Result.no("crusadecraft.lieu.unknown");
            site.get().setName(name);
        }
        l.setName(name);
        data(player.server).setDirty();
        return new Result(Outcome.ok(Component.translatable("crusadecraft.lieu.renamed", res.name(l))), id);
    }

    public static Result setText(ServerPlayer player, UUID id, String text) {
        Resolver res = new Resolver(player.server);
        Optional<Lieu> found = res.places().get(id);
        if (found.isEmpty()) return Result.no("crusadecraft.lieu.unknown");
        Lieu l = found.get();
        if (!canWrite(player, l, res, WorldEditService.canEdit(player), RealmService.realmOf(player.server, player.getUUID()).map(Realm::id).orElse(null))) {
            return Result.no("crusadecraft.mapedit.denied");
        }
        l.setText(text == null ? "" : text.strip());
        data(player.server).setDirty();
        return new Result(Outcome.ok(Component.translatable("crusadecraft.lieu.text_saved")), id);
    }

    /** Range un lieu sous un autre ({@code null} : celui que le monde donne). Éditeur seulement ; jamais en boucle. */
    public static Result setParent(ServerPlayer player, UUID id, UUID parent) {
        if (!WorldEditService.canEdit(player)) return Result.no("crusadecraft.mapedit.denied");
        Resolver res = new Resolver(player.server);
        Optional<Lieu> found = res.places().get(id);
        if (found.isEmpty()) return Result.no("crusadecraft.lieu.unknown");
        if (parent != null) {
            if (res.places().get(parent).isEmpty()) return Result.no("crusadecraft.lieu.unknown");
            if (PlaceState.wouldCycle(id, parent, res::parentOf)) return Result.no("crusadecraft.lieu.cycle");
        }
        found.get().setParent(parent);
        data(player.server).setDirty();
        Resolver after = new Resolver(player.server);
        UUID effective = after.parentOf(id);
        return new Result(Outcome.ok(Component.translatable("crusadecraft.lieu.moved", after.name(found.get()),
                effective == null ? Component.translatable("crusadecraft.lieu.none") : after.places().get(effective).map(after::name).orElse(Component.empty()))), id);
    }

    /** Le royaume qui tient un lieu posé à la main. Éditeur seulement. */
    public static Result setHolder(ServerPlayer player, UUID id, UUID realm) {
        if (!WorldEditService.canEdit(player)) return Result.no("crusadecraft.mapedit.denied");
        Resolver res = new Resolver(player.server);
        Optional<Lieu> found = res.places().get(id);
        if (found.isEmpty()) return Result.no("crusadecraft.lieu.unknown");
        if (found.get().ref() != null) return Result.no("crusadecraft.lieu.holder_elsewhere");
        if (realm != null && res.realmLieu(realm).isEmpty()) return Result.no("crusadecraft.lieu.unknown");
        found.get().setHolder(realm);
        data(player.server).setDirty();
        return new Result(Outcome.ok(Component.translatable("crusadecraft.lieu.holder_set", res.holderName(found.get()))), id);
    }

    /** Supprime un lieu posé à la main (ou disparu du monde). Ce qui en relevait retombe sur le parent du monde. */
    public static Result delete(ServerPlayer player, UUID id) {
        Resolver res = new Resolver(player.server);
        Optional<Lieu> found = res.places().get(id);
        if (found.isEmpty()) return Result.no("crusadecraft.lieu.unknown");
        Lieu l = found.get();
        if (l.ref() != null) return Result.no("crusadecraft.lieu.delete_elsewhere");
        if (!canWrite(player, l, res, WorldEditService.canEdit(player), RealmService.realmOf(player.server, player.getUUID()).map(Realm::id).orElse(null))) {
            return Result.no("crusadecraft.mapedit.denied");
        }
        UUID parent = res.parentOf(id);
        Component name = res.name(l);
        res.places().remove(id);
        data(player.server).setDirty();
        return new Result(Outcome.ok(Component.translatable("crusadecraft.lieu.deleted", name)), parent);
    }
}
