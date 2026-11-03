package fr.crusadecraft.core.build.plan;

import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.item.PlanItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Le plan d'architecte côté serveur (P34) : le catalogue, le choix d'un plan, la rotation, la pose. Tout est
 * revérifié ici : seul un joueur en créatif ou qui a l'éditeur de la carte pose quoi que ce soit.
 */
public final class PlanTool {
    /** L'étagère des villes entières, en tête du catalogue ; celle des favoris, juste après. */
    public static final String TOWNS = "@villes", FAVORITES = "@favoris";
    /** La sorte d'une ville entière, à part des sortes de plans. */
    public static final int TOWN_KIND = 5;
    /** P38 : une ville entière venue d'un fichier. */
    public static final int CITY_KIND = 6;
    private static final String TOWN_PREFIX = "town:";

    private PlanTool() {}

    public static boolean allowed(ServerPlayer player) {
        return player.isCreative() || WorldEditService.canEdit(player);
    }

    /** « town:CHINOISE:BOURG » : une ville entière d'un style et d'une taille. */
    public static String townRef(NationStyle style, TownSize size) {
        return TOWN_PREFIX + style.name() + ":" + size.name();
    }

    /**
     * Les villes entières qu'on peut poser : d'abord celles des fichiers (Dolhimluith sous Levenzia, Grunbach sous
     * Varenhold…, rangées par leur dossier), puis chaque style dont un pack est installé, de hameau en capitale.
     */
    public static List<PlanLibrary.Entry> towns() {
        List<PlanLibrary.Entry> out = new ArrayList<>(PlanLibrary.cityFiles());
        for (NationStyle style : NationStyle.values()) {
            Optional<String> pack = PlanLibrary.packFor(style);
            if (pack.isEmpty()) continue;
            for (TownSize size : TownSize.values()) {
                if (size == TownSize.QUARTIER) continue;
                out.add(new PlanLibrary.Entry(townRef(style, size), pack.get(), style.name(), TOWN_KIND));
            }
        }
        return out;
    }

    private static int favoritesCount;

    /** Les plans favoris du monde, en entrées du catalogue (rangées par pack). */
    public static List<PlanLibrary.Entry> favorites(net.minecraft.server.MinecraftServer server) {
        List<PlanLibrary.Entry> out = new ArrayList<>();
        for (String ref : PlanFavorites.get(server).all()) {
            if (ref.startsWith(TOWN_PREFIX)) {
                String[] parts = ref.substring(TOWN_PREFIX.length()).split(":");
                out.add(new PlanLibrary.Entry(ref, "", parts.length > 0 ? parts[0] : "", TOWN_KIND));
                continue;
            }
            PlanRef.decode(ref).ifPresent(r -> out.add(new PlanLibrary.Entry(ref, r.label(), r.pack() + (r.category().isEmpty() ? "" : " · " + r.category()),
                    r.source() == PlanRef.Source.PACK ? PlanKinds.kind(r.path()) : PlanKinds.AUTRE)));
        }
        out.sort(java.util.Comparator.comparing(PlanLibrary.Entry::category));
        return out;
    }

    public static List<PlanLibrary.Source> sources(net.minecraft.server.MinecraftServer server) {
        favoritesCount = PlanFavorites.get(server).all().size();
        return sources();
    }

    public static List<PlanLibrary.Source> sources() {
        List<PlanLibrary.Source> out = new ArrayList<>();
        out.add(new PlanLibrary.Source(TOWNS, "", towns().size()));
        out.add(new PlanLibrary.Source(FAVORITES, "", favoritesCount));
        out.addAll(PlanLibrary.sources());
        return out;
    }

    public static List<PlanLibrary.Entry> entries(net.minecraft.server.MinecraftServer server, String source) {
        if (FAVORITES.equals(source)) return favorites(server);
        return TOWNS.equals(source) ? towns() : PlanLibrary.entries(source);
    }

    /** Marque ou démarque un plan favori (la ville entière sans son tirage). */
    public static Outcome toggleFavorite(ServerPlayer player, String ref) {
        if (!allowed(player)) return Outcome.no("crusadecraft.plan.denied");
        String key = ref;
        if (ref.startsWith(TOWN_PREFIX)) {
            String[] parts = ref.substring(TOWN_PREFIX.length()).split(":");
            if (parts.length >= 2) key = TOWN_PREFIX + parts[0] + ":" + parts[1];
        } else if (PlanRef.decode(ref).isEmpty()) {
            return Outcome.no("crusadecraft.plan.unreadable");
        }
        boolean now = PlanFavorites.get(player.server).toggle(key);
        return Outcome.ok(Component.translatable(now ? "crusadecraft.plan.favorite.added" : "crusadecraft.plan.favorite.removed"));
    }

    /** Choisit ce que l'outil tenu posera. */
    public static Outcome choose(ServerPlayer player, String ref) {
        if (!allowed(player)) return Outcome.no("crusadecraft.plan.denied");
        Optional<ItemStack> held = PlanItem.held(player);
        if (held.isEmpty()) return Outcome.no("crusadecraft.plan.not_held");
        int rotation = PlanItem.choice(held.get()).map(PlanItem.Choice::rotation).orElse(0);
        if (ref.startsWith(TOWN_PREFIX)) {
            String[] parts = ref.substring(TOWN_PREFIX.length()).split(":");
            if (parts.length != 2 && parts.length != 3) return Outcome.no("crusadecraft.plan.unreadable");
            NationStyle style = NationStyle.byName(parts[0], null);
            TownSize size;
            try {
                size = TownSize.valueOf(parts[1]);
            } catch (IllegalArgumentException e) {
                size = null;
            }
            if (style == null || size == null) return Outcome.no("crusadecraft.plan.unreadable");
            long seed = parts.length == 3 ? parseSeed(parts[2], player.getRandom().nextLong()) : player.getRandom().nextLong();
            Optional<TownPlan> town = TownPlanner.plan(style, size, seed);
            if (town.isEmpty()) return Outcome.no("crusadecraft.town.no_pack");
            // P39 : son fantôme partira de ce plan-ci, sans le refaire.
            synchronized (TOWNS_FOR_GHOST) {
                TOWNS_FOR_GHOST.put(TOWN_PREFIX + style.name() + ":" + size.name() + ":" + seed, town.get());
            }
            Component name = Component.translatable("crusadecraft.plan.town_name", Component.translatable(style.key()), Component.translatable(size.key()));
            PlanItem.setChoice(held.get(), new PlanItem.Choice("", name.getString(), town.get().extent(), 32, town.get().extent(), 0, rotation, true,
                    style.ordinal(), size.ordinal(), seed));
            return Outcome.ok(Component.translatable("crusadecraft.plan.chosen.town", name, town.get().pieces().size()));
        }
        Optional<PlanRef> r = PlanRef.decode(ref);
        if (r.isEmpty()) return Outcome.no("crusadecraft.plan.unreadable");
        Optional<Plan> plan = PlanLibrary.load(r.get(), true);
        if (plan.isEmpty()) return Outcome.no("crusadecraft.plan.unreadable");
        Plan p = plan.get();
        PlanItem.setChoice(held.get(), new PlanItem.Choice(ref, p.name(), p.sizeX(), p.sizeY(), p.sizeZ(), p.groundY(), rotation, false, 0, 0, 0));
        sendGhost(player, ref);
        // P38 : ce qu'on a dû remplacer (un autre mod, une autre version), et ce qui manque.
        if (p.replaced() > 0) player.sendSystemMessage(Component.translatable("crusadecraft.plan.replaced", p.replaced()));
        if (!p.unknown().isEmpty()) player.sendSystemMessage(Component.translatable("crusadecraft.plan.unknown", p.unknown().size(),
                SchematicReader.join(p.unknown(), 6)));
        if (p.city()) player.sendSystemMessage(Component.translatable("crusadecraft.plan.chosen.city", p.name()));
        return Outcome.ok(Component.translatable("crusadecraft.plan.chosen", p.name(), p.sizeX(), p.sizeY(), p.sizeZ()));
    }

    static long parseSeed(String s, long fallback) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Ce qu'on sait d'un plan avant de le choisir (P34.1), pour le panneau du catalogue : son nom, sa taille, et un
     * aperçu vu du ciel en cases de couleur ({@code 0} : vide). Pour une ville entière, l'aperçu dessine le plan de la
     * ville : bâtiments selon leur rôle, rues, enceinte.
     */
    public record Detail(String ref, String name, int sizeX, int sizeY, int sizeZ, int pieces, int huts, boolean walled, int gridW, int gridH,
                         int[] colors, int kind, String role, int replaced, String unknown) {}

    /** Le plus grand côté de l'aperçu, en cases. */
    public static final int PREVIEW = 72;

    public static Optional<Detail> detail(ServerPlayer player, String ref) {
        if (!allowed(player)) return Optional.empty();
        if (ref.startsWith(TOWN_PREFIX)) {
            String[] parts = ref.substring(TOWN_PREFIX.length()).split(":");
            if (parts.length < 2) return Optional.empty();
            NationStyle style = NationStyle.byName(parts[0], null);
            TownSize size;
            try {
                size = TownSize.valueOf(parts[1]);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            if (style == null) return Optional.empty();
            long seed = parts.length == 3 ? parseSeed(parts[2], 1) : 1;
            Optional<TownPlan> town = TownPlanner.plan(style, size, seed);
            if (town.isEmpty()) return Optional.empty();
            int[][] grid = townPreview(town.get(), style);
            Component name = Component.translatable("crusadecraft.plan.town_name", Component.translatable(style.key()), Component.translatable(size.key()));
            return Optional.of(new Detail(ref, name.getString(), town.get().extent(), 0, town.get().extent(), town.get().pieces().size(), 0,
                    town.get().walls().length > 0, grid[0][0], grid[0][1], grid[1], TOWN_KIND, "", 0, ""));
        }
        Optional<Plan> plan = PlanRef.decode(ref).flatMap(r -> PlanLibrary.load(r, true));
        if (plan.isEmpty()) return Optional.empty();
        Plan p = plan.get();
        int[][] grid = planPreview(p);
        String path = p.ref().source() == PlanRef.Source.PACK ? p.ref().path() : "";
        int kind = p.city() ? CITY_KIND : path.isEmpty() ? PlanKinds.AUTRE : PlanKinds.kind(path);
        return Optional.of(new Detail(ref, p.name(), p.sizeX(), p.sizeY(), p.sizeZ(), 1, p.huts(), false, grid[0][0], grid[0][1], grid[1],
                kind, path.isEmpty() ? "" : PlanKinds.role(path), p.replaced(), SchematicReader.join(p.unknown(), 4)));
    }

    /** Le dessus d'un plan, vu du ciel : la couleur de carte du bloc le plus haut de chaque colonne, plus claire en hauteur. */
    static int[][] planPreview(Plan p) {
        int cell = Math.max(1, (int) Math.ceil(Math.max(p.sizeX(), p.sizeZ()) / (double) PREVIEW));
        int minX = -(p.sizeX() / 2), minZ = -(p.sizeZ() / 2);
        int gw = (p.sizeX() + cell - 1) / cell, gh = (p.sizeZ() + cell - 1) / cell;
        int[] top = new int[gw * gh];
        java.util.Arrays.fill(top, Integer.MIN_VALUE);
        int[] colors = new int[gw * gh];
        for (var pl : p.placements()) {
            if (pl.foundation() || pl.state().isAir()) continue;
            int gx = (pl.x() - minX) / cell, gz = (pl.z() - minZ) / cell;
            if (gx < 0 || gz < 0 || gx >= gw || gz >= gh) continue;
            int i = gz * gw + gx;
            if (pl.y() < top[i]) continue;
            int rgb = pl.state().getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).col;
            if (rgb == 0) continue;
            top[i] = pl.y();
            colors[i] = rgb;
        }
        int maxY = Math.max(1, p.sizeY() - p.groundY());
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == 0) continue;
            colors[i] = shade(colors[i], 0.72 + 0.4 * Math.min(1.0, Math.max(0, top[i]) / (double) maxY)) | 0xFF000000;
        }
        return new int[][]{{gw, gh}, colors};
    }

    /** Le plan d'une ville : chaque bâtiment selon son rôle, les rues, l'enceinte. */
    static int[][] townPreview(TownPlan town, NationStyle style) {
        int extent = Math.max(1, town.extent());
        int cell = Math.max(1, (int) Math.ceil(extent / (double) PREVIEW));
        int half = extent / 2, g = (extent + cell - 1) / cell;
        int[] colors = new int[g * g];
        int street = style.street().getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).col;
        int wall = style.wall().getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).col;
        java.util.function.BiConsumer<long[], Integer> paint = (cells, rgb) -> {
            for (long key : cells) {
                int x = (int) (key >> 32) + half, z = (int) key + half;
                if (x < 0 || z < 0 || x >= extent || z >= extent) continue;
                colors[(z / cell) * g + x / cell] = rgb | 0xFF000000;
            }
        };
        paint.accept(town.streets(), shade(street, 1.05));
        paint.accept(town.walls(), shade(wall, 0.9));
        for (TownPlan.Piece piece : town.pieces()) {
            Optional<PlanRef> ref = PlanRef.decode(piece.ref());
            Optional<Plan> plan = ref.flatMap(r -> PlanLibrary.load(r, false));
            if (plan.isEmpty()) continue;
            int rgb = roleColor(ref.get().path(), piece == town.pieces().get(0) && town.size().hasCenter());
            TownLayout.Rect r = TownLayout.rect(TownPlanner.box(plan.get()),
                    new TownLayout.Spot(piece.dx(), piece.dz(), net.minecraft.world.level.block.Rotation.values()[piece.rotation()]));
            for (int x = r.x0(); x <= r.x1(); x++) {
                for (int z = r.z0(); z <= r.z1(); z++) {
                    int px = x + half, pz = z + half;
                    if (px < 0 || pz < 0 || px >= extent || pz >= extent) continue;
                    boolean edge = x == r.x0() || x == r.x1() || z == r.z0() || z == r.z1();
                    colors[(pz / cell) * g + px / cell] = (edge ? shade(rgb, 0.7) : rgb) | 0xFF000000;
                }
            }
        }
        return new int[][]{{g, g}, colors};
    }

    /** La couleur d'un bâtiment sur le plan de la ville, selon ce qu'il est. */
    static int roleColor(String path, boolean center) {
        if (center) return 0xE7BD55;
        for (TownSize.Role role : TownSize.Role.values()) {
            if (!TownPlanner.matches(role, path)) continue;
            return switch (role) {
                case TOWNHALL -> 0xE7BD55;
                case PLAZA -> 0xD8CFB8;
                case HOUSE -> 0xB5714A;
                case TAVERN -> 0xE0913A;
                case FARM -> 0x7FAE4E;
                case MARKET -> 0xD9C24A;
                case CHURCH -> 0xC9D6E8;
                case BARRACKS -> 0xB0413A;
                case TOWER -> 0x8E8E8E;
                case CRAFT -> 0xA88B5E;
                case WAREHOUSE -> 0xC9A86A;
                case GRAVEYARD -> 0x7E8A78;
                case HOSPITAL -> 0xE8E2D2;
                case SCHOOL -> 0x8FA8C9;
                case STALL -> 0xE0C060;
                case LAMP -> 0xF2D27A;
                case STATUE -> 0xBDBDBD;
                case GARDEN -> 0x5E9E4A;
                case DECOR -> 0xC8B48A;
            };
        }
        return 0xB5714A;
    }

    static int shade(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * f), g = (int) Math.min(255, ((rgb >> 8) & 0xFF) * f), b = (int) Math.min(255, (rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** Au plus, les blocs d'un bâtiment seul envoyés pour son fantôme (gardé pour les anciens appels). */
    public static final int GHOST_MAX = 40_000;
    /**
     * P39 : au plus, les blocs d'un fantôme, une ville entière comprise (ses toits, ses façades, puis son terrain) ; au
     * delà, le dessin pèserait trop lourd pour la carte graphique. Ils partent par lots de {@link #GHOST_PART}.
     */
    public static final int GHOST_LIMIT = 250_000, GHOST_PART = 60_000;
    /** Les derniers fantômes calculés, gardés pour qu'un second choix soit immédiat. */
    private static final java.util.Map<String, int[][]> GHOSTS = new java.util.LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, int[][]> eldest) {
            return size() > 4;
        }
    };

    /** Les villes entières choisies dernièrement, pour leur fantôme. */
    private static final java.util.Map<String, TownPlan> TOWNS_FOR_GHOST = new java.util.LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, TownPlan> eldest) {
            return size() > 4;
        }
    };

    public static void onServerStopped() {
        synchronized (GHOSTS) {
            GHOSTS.clear();
        }
        synchronized (TOWNS_FOR_GHOST) {
            TOWNS_FOR_GHOST.clear();
        }
    }

    /**
     * Les blocs du fantôme (P35, P39) : ce qu'on voit, positions tassées en un entier et états par leur numéro. D'abord
     * le haut de chaque colonne (toits, rues, jardins) et tout ce qui est bâti et visible (un voisin au moins n'est pas
     * dans le plan) ; puis, s'il reste de la place, le terrain visible (pentes, feuillages), du centre vers les bords.
     * Sans les fondations ni l'air. Calculé hors du fil du serveur : les poses ne changent plus une fois lues.
     */
    public static int[][] ghost(java.util.List<fr.crusadecraft.core.build.Blueprints.Placement> placements) {
        it.unimi.dsi.fastutil.longs.LongOpenHashSet solid = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap top = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        top.defaultReturnValue(Integer.MIN_VALUE);
        for (var p : placements) {
            if (p.foundation() || p.state().isAir()) continue;
            solid.add(net.minecraft.core.BlockPos.asLong(p.x(), p.y(), p.z()));
            long col = net.minecraft.core.BlockPos.asLong(p.x(), 0, p.z());
            if (p.y() > top.get(col)) top.put(col, p.y());
        }
        it.unimi.dsi.fastutil.ints.IntArrayList pos = new it.unimi.dsi.fastutil.ints.IntArrayList(), ids = new it.unimi.dsi.fastutil.ints.IntArrayList();
        it.unimi.dsi.fastutil.ints.IntArrayList landPos = new it.unimi.dsi.fastutil.ints.IntArrayList(), landIds = new it.unimi.dsi.fastutil.ints.IntArrayList();
        for (var p : placements) {
            if (p.foundation() || p.state().isAir()) continue;
            boolean isTop = top.get(net.minecraft.core.BlockPos.asLong(p.x(), 0, p.z())) == p.y();
            boolean seen = isTop;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                if (seen) break;
                if (!solid.contains(net.minecraft.core.BlockPos.asLong(p.x() + d.getStepX(), p.y() + d.getStepY(), p.z() + d.getStepZ()))) seen = true;
            }
            if (!seen) continue;
            boolean land = !isTop && natural(p.state());
            (land ? landPos : pos).add(pack(p.x(), p.y(), p.z()));
            (land ? landIds : ids).add(net.minecraft.world.level.block.Block.getId(p.state()));
        }
        if (pos.size() > GHOST_LIMIT) {
            pos.size(GHOST_LIMIT);
            ids.size(GHOST_LIMIT);
        } else if (pos.size() + landPos.size() > GHOST_LIMIT) {
            // Le terrain le plus proche du centre d'abord.
            Integer[] order = new Integer[landPos.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            java.util.Arrays.sort(order, java.util.Comparator.comparingInt(i -> {
                int q = landPos.getInt(i);
                int x = ((q >> 20) & 0x3FF) - 512, z = (q & 0x3FF) - 512;
                return x * x + z * z;
            }));
            for (int k = 0; k < order.length && pos.size() < GHOST_LIMIT; k++) {
                pos.add(landPos.getInt(order[k]));
                ids.add(landIds.getInt(order[k]));
            }
        } else {
            pos.addAll(landPos);
            ids.addAll(landIds);
        }
        return new int[][]{pos.toIntArray(), ids.toIntArray()};
    }

    /** Du terrain naturel (terre, pierre, sable, feuillage, herbes) : il cède la place aux bâtiments dans le fantôme. */
    static boolean natural(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(net.minecraft.tags.BlockTags.DIRT) || s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || s.is(net.minecraft.tags.BlockTags.SAND) || s.is(net.minecraft.world.level.block.Blocks.GRAVEL) || s.is(net.minecraft.world.level.block.Blocks.CLAY)
                || !s.getFluidState().isEmpty() || s.canBeReplaced();
    }

    public static int pack(int x, int y, int z) {
        return ((x + 512) & 0x3FF) << 20 | ((y + 512) & 0x3FF) << 10 | ((z + 512) & 0x3FF);
    }

    /**
     * Envoie au joueur le fantôme d'un plan, ou d'une ville entière (P39) : les poses sont prises sur le fil du
     * serveur, les blocs visibles comptés à côté, puis envoyés par lots.
     */
    public static void sendGhost(ServerPlayer player, String ref) {
        if (ref == null || ref.isEmpty()) return;
        int[][] cached;
        synchronized (GHOSTS) {
            cached = GHOSTS.get(ref);
        }
        if (cached != null) {
            sendParts(player, ref, cached);
            return;
        }
        java.util.List<fr.crusadecraft.core.build.Blueprints.Placement> placements = ghostPlacements(ref);
        if (placements == null) return;
        net.minecraft.server.MinecraftServer server = player.server;
        UUID who = player.getUUID();
        java.util.concurrent.CompletableFuture.supplyAsync(() -> ghost(placements), net.minecraft.Util.backgroundExecutor())
                .thenAccept(g -> server.execute(() -> {
                    synchronized (GHOSTS) {
                        GHOSTS.put(ref, g);
                    }
                    ServerPlayer p = server.getPlayerList().getPlayer(who);
                    if (p != null) sendParts(p, ref, g);
                }));
    }

    /** Les poses d'un plan, ou d'une ville entière (« town:STYLE:TAILLE:tirage »). */
    public static java.util.List<fr.crusadecraft.core.build.Blueprints.Placement> ghostPlacements(String ref) {
        if (ref.startsWith(TOWN_PREFIX)) {
            String[] parts = ref.substring(TOWN_PREFIX.length()).split(":");
            if (parts.length != 3) return null;
            NationStyle style = NationStyle.byName(parts[0], null);
            TownSize size;
            try {
                size = TownSize.valueOf(parts[1]);
            } catch (IllegalArgumentException e) {
                return null;
            }
            if (style == null) return null;
            TownPlan known;
            synchronized (TOWNS_FOR_GHOST) {
                known = TOWNS_FOR_GHOST.get(ref);
            }
            if (known != null) return TownPlanner.placements(known);
            return TownPlanner.plan(style, size, parseSeed(parts[2], 1)).map(TownPlanner::placements).orElse(null);
        }
        return PlanRef.decode(ref).flatMap(r -> PlanLibrary.load(r, true)).map(Plan::placements).orElse(null);
    }

    private static void sendParts(ServerPlayer player, String ref, int[][] g) {
        int total = Math.max(1, (g[0].length + GHOST_PART - 1) / GHOST_PART);
        for (int part = 0; part < total; part++) {
            int from = part * GHOST_PART, to = Math.min(g[0].length, from + GHOST_PART);
            fr.crusadecraft.network.CrusadeNetwork.toPlayer(player, new fr.crusadecraft.network.packet.PlanPackets.Ghost(ref, part, total,
                    java.util.Arrays.copyOfRange(g[0], from, to), java.util.Arrays.copyOfRange(g[1], from, to)));
        }
    }

    /** La référence du fantôme d'un choix : le plan, ou la ville entière avec son tirage. */
    public static String ghostRef(PlanItem.Choice c) {
        if (!c.town()) return c.ref();
        return TOWN_PREFIX + NationStyle.byOrdinal(c.style()).name() + ":" + TownSize.byOrdinal(c.size()).name() + ":" + c.seed();
    }

    /** Pose là où le fantôme est, envoyé par le client : revérifié (l'outil en main, les droits, la distance). */
    public static void pasteAt(ServerPlayer player, net.minecraft.core.BlockPos ground) {
        Optional<ItemStack> held = PlanItem.held(player);
        if (held.isEmpty()) return;
        if (player.distanceToSqr(ground.getCenter()) > 160.0 * 160.0 || !player.serverLevel().isLoaded(ground)) {
            player.displayClientMessage(Component.translatable("crusadecraft.plan.too_far"), true);
            return;
        }
        paste(player, held.get(), ground);
    }

    public static void rotate(ServerPlayer player, ItemStack stack) {
        PlanItem.rotate(stack);
        int rot = PlanItem.choice(stack).map(PlanItem.Choice::rotation).orElse(0);
        player.displayClientMessage(Component.translatable("crusadecraft.plan.rotated", rot * 90), true);
    }

    /** Pose le plan choisi, son sol au niveau du bloc visé. */
    public static void paste(ServerPlayer player, ItemStack stack, BlockPos ground) {
        if (!allowed(player)) {
            player.displayClientMessage(Component.translatable("crusadecraft.plan.denied"), true);
            return;
        }
        Optional<PlanItem.Choice> c = PlanItem.choice(stack);
        if (c.isEmpty()) {
            player.displayClientMessage(Component.translatable("crusadecraft.plan.tooltip.none"), true);
            return;
        }
        PlanItem.Choice choice = c.get();
        if (choice.town()) {
            Optional<TownPlan> town = TownPlanner.plan(NationStyle.byOrdinal(choice.style()), TownSize.byOrdinal(choice.size()), choice.seed());
            if (town.isEmpty()) {
                player.displayClientMessage(Component.translatable("crusadecraft.town.no_pack"), true);
                return;
            }
            UUID id = Journals.paste(player, ground, choice.rotation(), null, town.get(), choice.name());
            player.sendSystemMessage(Component.translatable("crusadecraft.plan.pasting", choice.name()));
            // La ville posée est une ville du monde : sur la carte, à un royaume, avec ses habitants.
            player.sendSystemMessage(fr.crusadecraft.core.build.SiteService.registerToolTown(player, id, ground,
                    NationStyle.byOrdinal(choice.style()), TownSize.byOrdinal(choice.size()), town.get(), choice.rotation()));
            // P35 : la faire sienne, d'un clic dans le message.
            player.sendSystemMessage(TownColony.offer(id));
            return;
        } else {
            Optional<Plan> plan = PlanRef.decode(choice.ref()).flatMap(r -> PlanLibrary.load(r, true));
            if (plan.isEmpty()) {
                player.displayClientMessage(Component.translatable("crusadecraft.plan.unreadable"), true);
                return;
            }
            UUID id = Journals.paste(player, ground, choice.rotation(), choice.ref(), null, choice.name());
            // P38 : une ville entière venue d'un fichier devient une ville du royaume, à son nom.
            if (plan.get().city()) {
                player.sendSystemMessage(Component.translatable("crusadecraft.plan.pasting", choice.name()));
                player.sendSystemMessage(fr.crusadecraft.core.build.SiteService.registerImportedTown(player, id, ground, plan.get(), choice.ref()));
                return;
            }
        }
        player.sendSystemMessage(Component.translatable("crusadecraft.plan.pasting", choice.name()));
    }

    /** Défait la dernière pose du joueur. */
    public static Outcome undoLast(ServerPlayer player) {
        if (!allowed(player)) return Outcome.no("crusadecraft.plan.denied");
        return Journals.undoLast(player).isPresent() ? Outcome.ok(Component.translatable("crusadecraft.plan.undone"))
                : Outcome.no("crusadecraft.plan.nothing_to_undo");
    }
}
