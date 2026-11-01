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
    private static final String TOWN_PREFIX = "town:";

    private PlanTool() {}

    public static boolean allowed(ServerPlayer player) {
        return player.isCreative() || WorldEditService.canEdit(player);
    }

    /** « town:CHINOISE:BOURG » : une ville entière d'un style et d'une taille. */
    public static String townRef(NationStyle style, TownSize size) {
        return TOWN_PREFIX + style.name() + ":" + size.name();
    }

    /** Les villes entières qu'on peut poser : chaque style dont un pack est installé, de hameau en capitale. */
    public static List<PlanLibrary.Entry> towns() {
        List<PlanLibrary.Entry> out = new ArrayList<>();
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
                         int[] colors, int kind, String role) {}

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
                    town.get().walls().length > 0, grid[0][0], grid[0][1], grid[1], TOWN_KIND, ""));
        }
        Optional<Plan> plan = PlanRef.decode(ref).flatMap(r -> PlanLibrary.load(r, true));
        if (plan.isEmpty()) return Optional.empty();
        Plan p = plan.get();
        int[][] grid = planPreview(p);
        String path = p.ref().source() == PlanRef.Source.PACK ? p.ref().path() : "";
        return Optional.of(new Detail(ref, p.name(), p.sizeX(), p.sizeY(), p.sizeZ(), 1, p.huts(), false, grid[0][0], grid[0][1], grid[1],
                path.isEmpty() ? PlanKinds.AUTRE : PlanKinds.kind(path), path.isEmpty() ? "" : PlanKinds.role(path)));
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
            };
        }
        return 0xB5714A;
    }

    static int shade(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * f), g = (int) Math.min(255, ((rgb >> 8) & 0xFF) * f), b = (int) Math.min(255, (rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** Au plus, les blocs envoyés pour le fantôme : la surface d'un grand bâtiment tient dedans. */
    public static final int GHOST_MAX = 40_000;

    /**
     * Les blocs du fantôme d'un plan (P35) : ceux qu'on voit (un voisin au moins n'est pas dans le plan), sans les
     * fondations ni l'air, positions tassées en un entier et états par leur numéro.
     */
    static int[][] ghost(Plan plan) {
        it.unimi.dsi.fastutil.longs.LongOpenHashSet solid = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (var p : plan.placements()) if (!p.foundation() && !p.state().isAir()) solid.add(net.minecraft.core.BlockPos.asLong(p.x(), p.y(), p.z()));
        it.unimi.dsi.fastutil.ints.IntArrayList pos = new it.unimi.dsi.fastutil.ints.IntArrayList(), ids = new it.unimi.dsi.fastutil.ints.IntArrayList();
        for (var p : plan.placements()) {
            if (p.foundation() || p.state().isAir()) continue;
            boolean seen = false;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                if (!solid.contains(net.minecraft.core.BlockPos.asLong(p.x() + d.getStepX(), p.y() + d.getStepY(), p.z() + d.getStepZ()))) {
                    seen = true;
                    break;
                }
            }
            if (!seen) continue;
            if (pos.size() >= GHOST_MAX) break;
            pos.add(fr.crusadecraft.core.build.plan.PlanTool.pack(p.x(), p.y(), p.z()));
            ids.add(net.minecraft.world.level.block.Block.getId(p.state()));
        }
        return new int[][]{pos.toIntArray(), ids.toIntArray()};
    }

    public static int pack(int x, int y, int z) {
        return ((x + 512) & 0x3FF) << 20 | ((y + 512) & 0x3FF) << 10 | ((z + 512) & 0x3FF);
    }

    /** Envoie au joueur le fantôme d'un plan (rien pour une ville entière : trop de blocs, le cadre suffit). */
    public static void sendGhost(ServerPlayer player, String ref) {
        if (ref == null || ref.isEmpty() || ref.startsWith(TOWN_PREFIX)) return;
        PlanRef.decode(ref).flatMap(r -> PlanLibrary.load(r, true)).ifPresent(plan -> {
            int[][] g = ghost(plan);
            fr.crusadecraft.network.CrusadeNetwork.toPlayer(player, new fr.crusadecraft.network.packet.PlanPackets.Ghost(ref, g[0], g[1]));
        });
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
            if (PlanRef.decode(choice.ref()).flatMap(r -> PlanLibrary.load(r, true)).isEmpty()) {
                player.displayClientMessage(Component.translatable("crusadecraft.plan.unreadable"), true);
                return;
            }
            Journals.paste(player, ground, choice.rotation(), choice.ref(), null, choice.name());
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
