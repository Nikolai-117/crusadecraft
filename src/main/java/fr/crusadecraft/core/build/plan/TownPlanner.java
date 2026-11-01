package fr.crusadecraft.core.build.plan;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.Blueprints;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Dresse le plan d'une ville d'un style (P34) : on prend dans le pack du style un hôtel de ville, une place, des
 * maisons, une taverne… reconnus au nom de leur plan, au niveau qui convient à la taille de la ville, puis
 * {@link TownLayout} les range. Les cabanes deviennent des tonneaux : une ville de l'ordinateur n'a pas de colonie.
 */
public final class TownPlanner {

    private TownPlanner() {}

    // ------------------------------------------------------------------ reconnaître les bâtiments

    private static final Map<TownSize.Role, Set<String>> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put(TownSize.Role.TOWNHALL, Set.of("townhall", "alttownhall"));
        NAMES.put(TownSize.Role.PLAZA, Set.of("citysquare", "towncenter", "bigwell", "smallwell", "small_well", "plaza_a", "plaza_b",
                "obeliskseats", "tholos", "shrine", "sjinto_wayshrine", "fountain"));
        NAMES.put(TownSize.Role.HOUSE, Set.of("residence", "altresidence", "citizen", "cottage", "grachtenpand"));
        NAMES.put(TownSize.Role.TAVERN, Set.of("tavern", "senethouse"));
        NAMES.put(TownSize.Role.FARM, Set.of("farmer", "altfarmer", "farm"));
        NAMES.put(TownSize.Role.MARKET, Set.of("plazamarket", "bazaar", "market", "marketcart", "postoffice"));
        NAMES.put(TownSize.Role.WAREHOUSE, Set.of("warehouse", "altwarehouse", "landwhouse", "whousecart"));
        NAMES.put(TownSize.Role.GRAVEYARD, Set.of("graveyard", "altgraveyard", "koyagraveyard", "isis_grave", "red_graves"));
        NAMES.put(TownSize.Role.HOSPITAL, Set.of("hospital"));
        NAMES.put(TownSize.Role.SCHOOL, Set.of("school", "library", "altlibrary", "university", "universitylibrary", "isis_school"));
        NAMES.put(TownSize.Role.STALL, Set.of());
        NAMES.put(TownSize.Role.LAMP, Set.of());
        NAMES.put(TownSize.Role.STATUE, Set.of());
        NAMES.put(TownSize.Role.GARDEN, Set.of());
        NAMES.put(TownSize.Role.DECOR, Set.of());
        NAMES.put(TownSize.Role.CHURCH, Set.of("mysticalsite", "church", "cathedral", "temple_isis", "mountkoya", "townmastaba"));
        NAMES.put(TownSize.Role.BARRACKS, Set.of("barracks", "altbarracks", "castle", "fort"));
        NAMES.put(TownSize.Role.TOWER, Set.of("guardtower", "altguardtower", "tower", "fieldtower", "firelookout", "towersmall", "barrackstower"));
        NAMES.put(TownSize.Role.CRAFT, Set.of("baker", "altbaker", "blacksmith", "altblacksmith", "sawmill", "altsawmill", "cook", "altcook",
                "restaurant", "stonemason", "fletcher", "cookery"));
    }

    /** Le plus grand côté permis par genre : une cathédrale de quatre-vingts blocs n'entre pas dans un bourg. */
    static int maxFootprint(TownSize.Role role) {
        return switch (role) {
            case TOWNHALL -> 80;
            case BARRACKS, CHURCH, SCHOOL -> 64;
            case TAVERN, FARM, MARKET, WAREHOUSE, GRAVEYARD, HOSPITAL -> 52;
            case HOUSE, CRAFT -> 40;
            case PLAZA, TOWER -> 28;
            case STATUE, GARDEN -> 16;
            case DECOR -> 14;
            case STALL -> 12;
            case LAMP -> 7;
        };
    }

    /** Le plan (chemin dans le pack) est-il de ce genre ? Logique pure, sur son nom et son dossier. */
    public static boolean matches(TownSize.Role role, String path) {
        String p = path.toLowerCase(Locale.ROOT);
        String base = PlanRules.base(PlanRef.label(p));
        // Les décors se reconnaissent à leur rôle (dossier « decorations ») : étals, lanternes, statues, puits et fontaines.
        if (role.decor() && PlanKinds.kind(p) == PlanKinds.DECORATION) {
            String r = PlanKinds.role(p);
            switch (role) {
                case STALL -> { if (r.equals("etal")) return true; }
                case LAMP -> { if (r.equals("lanterne")) return true; }
                case STATUE -> { if (r.equals("statue")) return true; }
                case GARDEN -> { if (r.equals("jardin") || r.equals("fontaine")) return true; }
                case DECOR -> {
                    if (!r.equals("place") && !r.equals("aqueduc")) return true;
                }
                case PLAZA -> { if (r.equals("puits") || r.equals("fontaine")) return true; }
                default -> { }
            }
        }
        if (role == TownSize.Role.STALL || role == TownSize.Role.LAMP || role == TownSize.Role.STATUE || role == TownSize.Role.GARDEN
                || role == TownSize.Role.DECOR) return false;
        if (role == TownSize.Role.HOUSE && p.contains("/house/") && !base.contains("guard") && !base.contains("dock") && !base.startsWith("dorm")) {
            return true;
        }
        return NAMES.get(role).contains(base);
    }

    // ------------------------------------------------------------------ dresser le plan

    /**
     * Le plan d'une ville de ce style et de cette taille, ou rien si aucun pack du style n'est installé (ou s'il n'a
     * pas d'hôtel de ville qui convienne).
     */
    public static Optional<TownPlan> plan(NationStyle style, TownSize size, long seed) {
        Optional<String> pack = PlanLibrary.packFor(style);
        if (pack.isEmpty()) return Optional.empty();
        List<String> entries = PlanLibrary.packEntries(pack.get());
        Random random = new Random(seed);
        Set<String> used = new HashSet<>();
        List<TownLayout.Box> boxes = new ArrayList<>();
        List<String> refs = new ArrayList<>();
        List<TownSize.Role> program = new ArrayList<>(size.program());
        List<TownSize.Role> roles = new ArrayList<>();
        if (size.hasCenter()) {
            Optional<Plan> hall = pick(pack.get(), entries, TownSize.Role.TOWNHALL, size.level(), size.minLevel(), size.maxLevel(), random, used);
            if (hall.isEmpty()) return Optional.empty();
            boxes.add(box(hall.get()));
            refs.add(hall.get().ref().encode());
            roles.add(TownSize.Role.TOWNHALL);
        }
        for (TownSize.Role role : program) {
            int level = role == TownSize.Role.HOUSE && size.maxLevel() > size.minLevel()
                    ? size.minLevel() + random.nextInt(size.maxLevel() - size.minLevel() + 1) : size.level();
            Optional<Plan> p = pick(pack.get(), entries, role, level, size.minLevel(), size.maxLevel(), random, used);
            if (p.isEmpty()) continue;
            boxes.add(box(p.get()));
            refs.add(p.get().ref().encode());
            roles.add(role);
        }
        if (boxes.isEmpty()) return Optional.empty();
        int core = boxes.size();
        // P36 : le remplissage, après le programme — quelques modèles par genre, répétés, posés s'ils tiennent.
        Map<TownSize.Role, List<Plan>> variants = new java.util.EnumMap<>(TownSize.Role.class);
        Set<String> fillUsed = new HashSet<>(used);
        for (TownSize.Role role : size.fill()) {
            List<Plan> list = variants.computeIfAbsent(role, r -> new ArrayList<>());
            if (list.size() >= (role == TownSize.Role.HOUSE ? 7 : role == TownSize.Role.DECOR || role == TownSize.Role.GARDEN ? 6 : 3)) continue;
            int level = role == TownSize.Role.HOUSE && size.maxLevel() > size.minLevel()
                    ? size.minLevel() + random.nextInt(size.maxLevel() - size.minLevel() + 1) : size.level();
            pick(pack.get(), entries, role, level, size.minLevel(), size.maxLevel(), random, fillUsed).ifPresent(list::add);
        }
        int[] turn = new int[TownSize.Role.values().length];
        for (TownSize.Role role : size.fill()) {
            List<Plan> list = variants.get(role);
            if (list == null || list.isEmpty()) continue;
            Plan p = list.get(turn[role.ordinal()]++ % list.size());
            boxes.add(box(p));
            refs.add(p.ref().encode());
            roles.add(role);
        }
        TownLayout.Pattern pattern = size.hasCenter() ? style.pattern() : TownLayout.Pattern.RUE;
        TownLayout.Layout layout = TownLayout.plan(pattern, boxes, roles, false, seed, core);
        List<TownPlan.Piece> pieces = new ArrayList<>();
        List<TownLayout.Rect> rects = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            TownLayout.Spot s = layout.spots().get(i);
            if (s == null) continue;
            pieces.add(new TownPlan.Piece(refs.get(i), s.x(), s.z(), s.rotation().ordinal(), 0));
            rects.add(TownLayout.rect(boxes.get(i), s));
        }
        List<int[]> streets = new ArrayList<>(layout.streets());
        int extent = layout.extent();
        // P36 : l'enceinte en pièces de muraille du pack (palissade d'un bourg, grande muraille d'une ville).
        Optional<WallSets.Kit> kit = WallSets.kit(style, size.walls(), pack.get(), entries);
        if (kit.isPresent()) {
            int x0 = 0, z0 = 0, x1 = 0, z1 = 0;
            for (TownLayout.Rect r : rects) {
                x0 = Math.min(x0, r.x0());
                z0 = Math.min(z0, r.z0());
                x1 = Math.max(x1, r.x1());
                z1 = Math.max(z1, r.z1());
            }
            int margin = kit.get().thickness() / 2 + 4;
            final int ix0 = x0 - margin + 1, iz0 = z0 - margin + 1, ix1 = x1 + margin - 1, iz1 = z1 + margin - 1;
            streets.removeIf(c -> c[0] < ix0 || c[0] > ix1 || c[1] < iz0 || c[1] > iz1);
            WallSets.Ring ring = WallSets.assemble(kit.get(), x0 - margin, z0 - margin, x1 + margin, z1 + margin);
            pieces.addAll(ring.pieces());
            // Pas de rue sous les murs, sauf sous les portes ; de chaque porte, une rue jusqu'à la ville.
            streets.removeIf(c -> under(ring.rects(), c[0], c[1]) && !under(ring.gateRects(), c[0], c[1]));
            for (int[] g : ring.gates()) streets.addAll(gateStreet(g, streets, rects, ring.rects()));
            extent = 2 * Math.max(Math.max(Math.abs(x0 - margin), Math.abs(x1 + margin)), Math.max(Math.abs(z0 - margin), Math.abs(z1 + margin))) + 20;
        }
        TownPlan town = new TownPlan(style.name(), size, pieces, pack(streets), extent);
        CrusadeCraft.LOGGER.info("CrusadeCraft : ville {} ({}, plan {}) dressée avec {} bâtiments sur {} du pack {}", size, style, pattern,
                pieces.size(), boxes.size(), pack.get());
        return Optional.of(town);
    }

    private static boolean under(List<TownLayout.Rect> rects, int x, int z) {
        for (TownLayout.Rect r : rects) if (r.contains(x, z)) return true;
        return false;
    }

    /** De la porte vers l'intérieur, une rue de trois de large jusqu'à la première rue de la ville (ou un bâtiment). */
    static List<int[]> gateStreet(int[] gate, List<int[]> streets, List<TownLayout.Rect> buildings, List<TownLayout.Rect> walls) {
        java.util.Set<Long> known = new java.util.HashSet<>();
        for (int[] c : streets) known.add(((long) c[0] << 32) | (c[1] & 0xFFFFFFFFL));
        net.minecraft.core.Direction out = net.minecraft.core.Direction.values()[gate[2]];
        int dx = -out.getStepX(), dz = -out.getStepZ();
        List<int[]> cells = new ArrayList<>();
        int x = gate[0], z = gate[1];
        for (int step = 0; step < 80; step++) {
            boolean reached = false;
            for (int w = -1; w <= 1; w++) {
                int cx = x + (dx == 0 ? w : 0), cz = z + (dz == 0 ? w : 0);
                if (under(buildings, cx, cz)) return cells;
                if (known.contains(((long) cx << 32) | (cz & 0xFFFFFFFFL))) reached = true;
                cells.add(new int[]{cx, cz});
            }
            if (reached && step > 2) return cells;
            x += dx;
            z += dz;
        }
        return cells;
    }

    private static long[] pack(List<int[]> cells) {
        long[] out = new long[cells.size()];
        for (int i = 0; i < out.length; i++) out[i] = ((long) cells.get(i)[0] << 32) | (cells.get(i)[1] & 0xFFFFFFFFL);
        return out;
    }

    /** De l'eau (ou un autre fluide) sur le bord du plan : posé seul, il se répandrait. */
    static boolean leaks(Plan p) {
        int minX = -(p.sizeX() / 2), maxX = p.sizeX() - 1 - p.sizeX() / 2, minZ = -(p.sizeZ() / 2), maxZ = p.sizeZ() - 1 - p.sizeZ() / 2;
        for (Blueprints.Placement pl : p.placements()) {
            if (pl.state().getFluidState().isEmpty()) continue;
            if (pl.x() == minX || pl.x() == maxX || pl.z() == minZ || pl.z() == maxZ) return true;
        }
        return false;
    }

    static TownLayout.Box box(Plan p) {
        return new TownLayout.Box(p.sizeX(), p.sizeZ());
    }

    /**
     * Un plan de ce genre : un modèle tiré au sort (pour les maisons, un autre que ceux déjà pris), au niveau le plus
     * proche du niveau voulu dont l'emprise convient.
     */
    static Optional<Plan> pick(String pack, List<String> entries, TownSize.Role role, int level, Random random, Set<String> used) {
        return pick(pack, entries, role, level, 1, 5, random, used);
    }

    /**
     * Un plan de ce genre : un modèle tiré au sort (un autre que ceux déjà pris, tant qu'il en reste), au niveau le plus
     * proche du niveau voulu sans sortir de la fourchette de la taille ({@code min}–{@code max}), dont l'emprise convient.
     * Un plan sans niveau (une seule version) convient toujours.
     */
    static Optional<Plan> pick(String pack, List<String> entries, TownSize.Role role, int level, int min, int max, Random random, Set<String> used) {
        Map<String, List<String>> byBase = new LinkedHashMap<>();
        for (String e : entries) {
            if (!matches(role, e)) continue;
            int lv = PlanRules.level(PlanRef.label(e));
            if (lv != 0 && (lv < min || lv > max)) continue;
            String label = PlanRef.label(e);
            String base = e.substring(0, Math.max(0, e.lastIndexOf('/') + 1)) + PlanRules.base(label);
            byBase.computeIfAbsent(base, k -> new ArrayList<>()).add(e);
        }
        if (byBase.isEmpty()) return Optional.empty();
        List<String> bases = new ArrayList<>(byBase.keySet());
        Collections.shuffle(bases, random);
        // P37 : les modèles marqués d'une étoile d'abord, puis ceux pas encore pris.
        Set<String> fav = PlanFavorites.current();
        Set<String> favBases = new HashSet<>();
        for (Map.Entry<String, List<String>> e : byBase.entrySet()) {
            for (String path : e.getValue()) if (fav.contains(new PlanRef(PlanRef.Source.PACK, pack, path).encode())) favBases.add(e.getKey());
        }
        bases.sort((a, b) -> {
            int c = Boolean.compare(!favBases.contains(a), !favBases.contains(b));
            return c != 0 ? c : Boolean.compare(used.contains(a), used.contains(b));
        });
        int attempts = 0;
        for (String base : bases) {
            List<String> levels = new ArrayList<>(byBase.get(base));
            int want = Math.max(1, level);
            levels.sort((a, b) -> {
                // Le niveau marqué d'une étoile d'abord, s'il est dans la fourchette.
                boolean fa = fav.contains(new PlanRef(PlanRef.Source.PACK, pack, a).encode()), fb = fav.contains(new PlanRef(PlanRef.Source.PACK, pack, b).encode());
                if (fa != fb) return fa ? -1 : 1;
                int la = PlanRules.level(PlanRef.label(a)), lb = PlanRules.level(PlanRef.label(b));
                int c = Integer.compare(Math.abs(la - want), Math.abs(lb - want));
                return c != 0 ? c : Integer.compare(lb, la);
            });
            for (String path : levels) {
                if (attempts++ > 16) return Optional.empty();
                Optional<Plan> p = PlanLibrary.load(new PlanRef(PlanRef.Source.PACK, pack, path), false);
                if (p.isPresent() && role.decor() && leaks(p.get())) continue;
                if (p.isPresent() && p.get().footprint() <= maxFootprint(role) && !p.get().placements().isEmpty()) {
                    if (!role.decor()) used.add(base);
                    return p;
                }
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ relever le terrain, poser

    /** La hauteur du sol (le bloc solide le plus haut, feuillages exclus). */
    static int surface(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /**
     * Relève le terrain : chaque bâtiment se pose à la hauteur médiane du sol sous son emprise, chaque case de rue sur
     * le sol. Une seule fois, au premier coup de pioche.
     */
    public static void survey(ServerLevel level, BlockPos anchor, TownPlan town) {
        if (town.surveyed()) return;
        for (TownPlan.Piece piece : town.pieces()) {
            Optional<Plan> plan = PlanRef.decode(piece.ref()).flatMap(r -> PlanLibrary.load(r, false));
            if (plan.isEmpty()) continue;
            TownLayout.Rect r = TownLayout.rect(box(plan.get()), new TownLayout.Spot(piece.dx(), piece.dz(), Rotation.values()[piece.rotation()]));
            List<Integer> heights = new ArrayList<>();
            for (int x = r.x0(); x <= r.x1(); x += 2) {
                for (int z = r.z0(); z <= r.z1(); z += 2) heights.add(surface(level, anchor.getX() + x, anchor.getZ() + z));
            }
            if (heights.isEmpty()) continue;
            Collections.sort(heights);
            piece.setDy(heights.get(heights.size() / 2) - anchor.getY());
        }
        long[] streets = town.streets();
        int[] ys = new int[streets.length];
        for (int i = 0; i < streets.length; i++) {
            int x = (int) (streets[i] >> 32), z = (int) streets[i];
            ys[i] = surface(level, anchor.getX() + x, anchor.getZ() + z) - anchor.getY();
        }
        long[] walls = town.walls();
        int[] wy = new int[walls.length];
        for (int i = 0; i < walls.length; i++) {
            int x = (int) (walls[i] >> 32), z = (int) walls[i];
            wy[i] = surface(level, anchor.getX() + x, anchor.getZ() + z) - anchor.getY();
        }
        town.setSurvey(ys, wy);
    }

    /**
     * Les étapes du chantier, bâtiment après bâtiment, puis les rues. Un plan qui ne se lit plus (pack retiré) est
     * sauté : la ville a un trou, rien ne plante.
     */
    public static List<Blueprints.Placement> placements(TownPlan town) {
        List<Blueprints.Placement> out = new ArrayList<>();
        for (TownPlan.Piece piece : town.pieces()) {
            Optional<Plan> plan = PlanRef.decode(piece.ref()).flatMap(r -> PlanLibrary.load(r, false));
            if (plan.isEmpty()) {
                CrusadeCraft.LOGGER.warn("CrusadeCraft : le plan {} ne se lit plus, ce bâtiment est sauté", piece.ref());
                continue;
            }
            Rotation rot = Rotation.values()[piece.rotation()];
            for (Blueprints.Placement p : plan.get().placements()) {
                int[] xz = PlanBuilder.turn(p.x(), p.z(), rot);
                out.add(new Blueprints.Placement(piece.dx() + xz[0], p.y() + piece.dy(), piece.dz() + xz[1], p.state().rotate(rot), p.foundation(), p.tag()));
            }
        }
        NationStyle style = NationStyle.byName(town.style(), NationStyle.EUROPEENNE);
        walls(town, style, out);
        BlockState street = style.street();
        long[] streets = town.streets();
        int[] ys = town.streetY();
        for (int i = 0; i < streets.length && i < ys.length; i++) {
            int x = (int) (streets[i] >> 32), z = (int) streets[i];
            out.add(new Blueprints.Placement(x, ys[i], z, style.street(x, z), false));
            out.add(new Blueprints.Placement(x, ys[i] + 1, z, Blocks.AIR.defaultBlockState(), false));
            out.add(new Blueprints.Placement(x, ys[i] + 2, z, Blocks.AIR.defaultBlockState(), false));
        }
        return out;
    }

    /**
     * L'enceinte : sur chaque case, une colonne de la matière du style posée sur le sol (le terrain comblé dessous),
     * un créneau sur une case sur deux pour les murs de pierre, une levée enherbée pour le rempart viking.
     */
    static void walls(TownPlan town, NationStyle style, List<Blueprints.Placement> out) {
        long[] walls = town.walls();
        int[] ys = town.wallY();
        BlockState wall = style.wall();
        int h = style.wallHeight();
        boolean earth = style == NationStyle.NORDIQUE;
        for (int i = 0; i < walls.length && i < ys.length; i++) {
            int x = (int) (walls[i] >> 32), z = (int) walls[i];
            out.add(new Blueprints.Placement(x, ys[i], z, wall, true));
            for (int y = 1; y <= h; y++) {
                BlockState st = earth && y == h ? Blocks.GRASS_BLOCK.defaultBlockState() : wall;
                out.add(new Blueprints.Placement(x, ys[i] + y, z, st, false));
            }
            if (!earth && Math.floorMod(x + z, 2) == 0) out.add(new Blueprints.Placement(x, ys[i] + h + 1, z, wall, false));
        }
    }

    /** Pour les journaux : les plans d'une ville, en clair. */
    public static String describe(TownPlan town) {
        return Arrays.toString(town.pieces().stream().map(TownPlan.Piece::ref).toArray());
    }
}
