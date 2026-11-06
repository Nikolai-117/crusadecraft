package fr.crusadecraft.core.build.plan;

import net.minecraft.world.level.block.Rotation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Le générateur de l'atelier des villes (P60). Il dresse le plan sur une grille d'une case par bloc : l'eau, l'enceinte, la place, les grandes routes qui
 * partent du centre, les rues qui en naissent, puis les îlots (ce que les rues enferment), leur nature, et les bâtiments le long des rues. Autour, les faubourgs, les
 * champs, les forêts. Rien ici ne touche au monde : le même plan sert à l'aperçu, au fantôme et au chantier, et se teste sans Minecraft.
 *
 * <p>Le principe vient des générateurs de Watabou et de Fantasy Town Generator, l'écriture est la nôtre. La grande différence avec eux : un plan de
 * Minecraft ne se pose qu'à angle droit. Les rues serpentent donc, les bâtiments restent alignés et regardent la rue la plus proche.
 */
public final class UrbanGenerator {
    /** Ce que le générateur demande à la bibliothèque des plans. Un faux dans les essais : aucune dépendance au jeu. */
    public interface Plans {
        /** Un modèle de ce genre, au niveau le plus proche de celui voulu ; vide si le pack n'en a pas. */
        Optional<Pick> pick(TownSize.Role role, int level, int min, int max, Random random);

        /** L'enceinte faite des pièces du pack autour de ce rectangle ; vide si le pack n'en a pas. */
        default Optional<WallSets.Ring> wallRing(boolean big, int x0, int z0, int x1, int z1) {
            return Optional.empty();
        }
    }

    public record Pick(String ref, int w, int d) {}

    /** Un bâtiment posé : son numéro (pour le retoucher à la main), son plan, sa place, son genre, son îlot. */
    public record Placed(int slot, Pick pick, TownSize.Role role, int x, int z, Rotation rotation, TownLayout.Rect rect, int lot) {}

    /** Un îlot : ce que les rues enferment. */
    public static final class Lot {
        public final int id;
        public int area, x0, z0, x1, z1, houses;
        public double cx, cz;
        public boolean water, wall, main, plaza, citadel, suburb;
        public UrbanWard ward = UrbanWard.HABITATIONS;
        final List<int[]> cells = new ArrayList<>();

        Lot(int id) {
            this.id = id;
        }
    }

    // Ce qu'il y a sur une case. Les numéros servent aussi à l'aperçu : ne pas les changer sans mettre à jour sa palette.
    public static final byte LAND = 0, SEA = 1, RIVER = 2, MOAT = 3, ROAD_MAIN = 4, ROAD = 5, ALLEY = 6, BRIDGE = 7, PLAZA = 8, WALL = 9, TOWER = 10, GATE = 11, BUILDING = 12,
            DECOR = 13, FIELD = 14, TREE = 15, GARDEN = 16, YARD = 17, PATH = 18, PIER = 19, SPECIAL = 20;
    /** Dans l'aperçu : un îlot, ou un bâtiment, teinté selon sa nature. */
    public static final int VIEW_LOT = 32, VIEW_HOUSE = 64;

    private final UrbanParams p;
    private final Plans plans;
    private final Random rng;
    private final UrbanNoise noise;

    private int half, size;
    private byte[] cell;
    private byte[] zone;
    private short[] lotOf;
    private int seg, mainW, roadW, alleyW, gap, marge, minBlock, minGap;
    private double rIn, rOut, avgD = 12, avgW = 10;
    private boolean walled, packWalls;
    private double[][] polygon;
    private final List<int[][]> mains = new ArrayList<>();
    private final List<int[]> gates = new ArrayList<>();
    private final List<Lot> lots = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();
    private final List<TownPlan.Piece> extraPieces = new ArrayList<>();
    private final List<int[]> river = new ArrayList<>();
    private final Map<String, List<Pick>> pools = new HashMap<>();
    private int bridgesUsed, slotCounter, noRoom, noModel;
    private int citadelLot = -1;
    private final List<int[]> trees = new ArrayList<>();
    private final List<int[]> lamps = new ArrayList<>();
    private final Map<TownSize.Role, Integer> program = new EnumMap<>(TownSize.Role.class);
    private final Map<TownSize.Role, Integer> built = new EnumMap<>(TownSize.Role.class);
    private Pick centerPick;
    private TownSize.Role centerRole;

    private UrbanGenerator(UrbanParams p, Plans plans) {
        this.p = p;
        this.plans = plans;
        this.rng = new Random(p.seed());
        this.noise = new UrbanNoise(p.seed() * 31 + 7);
    }

    /** Dresse le plan. Vide si le pack de ce style n'a pas de quoi faire une ville. */
    public static Optional<UrbanPlan> generate(UrbanParams p, Plans plans) {
        return new UrbanGenerator(p, plans).run();
    }

    // ------------------------------------------------------------------ les grilles

    private int idx(int x, int z) {
        return x + half + (z + half) * size;
    }

    private boolean in(int x, int z) {
        return x >= -half && x <= half && z >= -half && z <= half;
    }

    private byte at(int x, int z) {
        return in(x, z) ? cell[idx(x, z)] : SEA;
    }

    private void put(int x, int z, byte kind) {
        if (in(x, z)) cell[idx(x, z)] = kind;
    }

    private static boolean roadLike(byte k) {
        return k == ROAD_MAIN || k == ROAD || k == ALLEY || k == BRIDGE || k == PLAZA || k == GATE || k == PATH;
    }

    private static boolean water(byte k) {
        return k == SEA || k == RIVER || k == MOAT;
    }

    private static boolean wallLike(byte k) {
        return k == WALL || k == TOWER;
    }

    // ------------------------------------------------------------------ l'ensemble

    private Optional<UrbanPlan> run() {
        if (!prepareProgram()) return Optional.empty();
        measure();
        cell = new byte[size * size];
        zone = new byte[size * size];
        lotOf = new short[size * size];
        java.util.Arrays.fill(lotOf, (short) -1);
        paintSea();
        paintRiver();
        shapeWall();
        paintZone();
        paintPlaza();
        paintWall();
        spokes();
        paintCitadel();
        insideRoad();
        network();
        extraBridges();
        finishGates();
        if (p.on(UrbanParams.DOUVES) && walled) paintMoat();
        findLots();
        splitLots();
        assignWards();
        fillCenter();
        placeSpecials();
        placeHouses();
        suburbs();
        placeDecor();
        fillParks();
        harbour();
        countryside();
        forests();
        roadTrees();
        return Optional.of(finish());
    }

    // ------------------------------------------------------------------ le programme : combien de chaque

    private int auto(TownSize.Role role, int value) {
        UrbanParams.Def d = UrbanParams.count(role);
        int v = d == null ? -1 : p.get(d);
        return v >= 0 ? v : Math.max(0, value);
    }

    private boolean prepareProgram() {
        int t = p.get(UrbanParams.HABITANTS);
        int perHouse = Math.max(1, p.get(UrbanParams.PAR_MAISON));
        boolean temple = p.on(UrbanParams.TEMPLE);
        program.put(TownSize.Role.HOUSE, auto(TownSize.Role.HOUSE, Math.max(1, t / perHouse)));
        program.put(TownSize.Role.TAVERN, auto(TownSize.Role.TAVERN, t < 40 ? 0 : 1 + t / 250));
        program.put(TownSize.Role.FARM, auto(TownSize.Role.FARM, p.get(UrbanParams.CHAMPS) == 0 ? 0 : Math.min(24, 1 + t / 70)));
        program.put(TownSize.Role.MARKET, auto(TownSize.Role.MARKET, t < 120 ? 0 : 1 + t / 900));
        program.put(TownSize.Role.WAREHOUSE, auto(TownSize.Role.WAREHOUSE, t < 300 ? 0 : 1 + t / 800));
        program.put(TownSize.Role.CHURCH, auto(TownSize.Role.CHURCH, !temple || t < 60 ? 0 : 1 + t / 1200));
        program.put(TownSize.Role.GRAVEYARD, auto(TownSize.Role.GRAVEYARD, !p.on(UrbanParams.CIMETIERE) || t < 100 ? 0 : 1 + t / 1500));
        program.put(TownSize.Role.BARRACKS, auto(TownSize.Role.BARRACKS, t < 300 ? 0 : 1 + t / 1000));
        program.put(TownSize.Role.TOWER, auto(TownSize.Role.TOWER, t < 150 ? 0 : 1 + t / 500));
        program.put(TownSize.Role.CRAFT, auto(TownSize.Role.CRAFT, Math.min(40, t / 90)));
        program.put(TownSize.Role.HOSPITAL, auto(TownSize.Role.HOSPITAL, t < 500 ? 0 : 1 + t / 2000));
        program.put(TownSize.Role.SCHOOL, auto(TownSize.Role.SCHOOL, t < 800 ? 0 : 1 + t / 2000));
        program.put(TownSize.Role.STALL, auto(TownSize.Role.STALL, t < 100 ? 0 : 2 + t / 150));
        program.put(TownSize.Role.STATUE, auto(TownSize.Role.STATUE, t < 200 ? 0 : 1 + t / 500));
        program.put(TownSize.Role.GARDEN, auto(TownSize.Role.GARDEN, !p.on(UrbanParams.ESPACES_VERTS) ? 0 : t / 150));
        program.put(TownSize.Role.DECOR, auto(TownSize.Role.DECOR, t / 80));
        program.put(TownSize.Role.LAMP, auto(TownSize.Role.LAMP, -1));

        // La place centrale, si un bâtiment doit s'y dresser.
        int center = p.get(UrbanParams.CENTRE);
        centerRole = switch (center) {
            case 1 -> TownSize.Role.TOWNHALL;
            case 2 -> TownSize.Role.CHURCH;
            case 3 -> TownSize.Role.BARRACKS;
            default -> null;
        };
        if (centerRole != null) centerPick = onePick(centerRole, 5, UrbanWard.ADMINISTRATION).orElse(null);
        // Les maisons d'échantillon donnent la mesure : la profondeur des pâtés dépend de ce que le pack contient.
        List<Pick> sample = pool(TownSize.Role.HOUSE, UrbanWard.HABITATIONS);
        if (sample.isEmpty()) sample = pool(TownSize.Role.CRAFT, UrbanWard.HABITATIONS);
        if (sample.isEmpty()) return false;
        double w = 0, d = 0;
        for (Pick s : sample) {
            w += s.w();
            d += s.d();
        }
        avgW = w / sample.size();
        avgD = d / sample.size();
        return true;
    }

    private int levelFor(UrbanWard ward) {
        int offset = p.get(UrbanParams.RICHESSE) - 2;
        int min = Math.max(p.get(UrbanParams.NIVEAU_MIN), Math.min(5, ward.minLevel() + Math.min(0, offset)));
        int max = Math.min(p.get(UrbanParams.NIVEAU_MAX), Math.max(1, ward.maxLevel() + Math.max(0, offset)));
        if (max < min) max = min;
        return Math.max(1, Math.min(5, min + rng.nextInt(max - min + 1)));
    }

    /** Quelques modèles par genre et par nature d'îlot : les maisons d'un même quartier se ressemblent sans se répéter. */
    private List<Pick> pool(TownSize.Role role, UrbanWard ward) {
        String key = role.name() + ":" + ward.name();
        List<Pick> known = pools.get(key);
        if (known != null) return known;
        List<Pick> out = new ArrayList<>();
        int want = role == TownSize.Role.HOUSE ? 6 : role.decor() ? 5 : 3;
        for (int i = 0; i < want; i++) {
            int level = levelFor(ward);
            Optional<Pick> pick = plans.pick(role, level, p.get(UrbanParams.NIVEAU_MIN), p.get(UrbanParams.NIVEAU_MAX), rng);
            if (pick.isEmpty()) break;
            if (!out.contains(pick.get())) out.add(pick.get());
        }
        pools.put(key, out);
        return out;
    }

    private Optional<Pick> onePick(TownSize.Role role, int level, UrbanWard ward) {
        return plans.pick(role, level, p.get(UrbanParams.NIVEAU_MIN), p.get(UrbanParams.NIVEAU_MAX), rng);
    }

    /** La surface qu'il faut : les emprises, les écarts entre elles, et ce que prennent les rues. */
    private void measure() {
        seg = p.get(UrbanParams.SEGMENT);
        mainW = p.get(UrbanParams.LARGEUR_GRANDE);
        roadW = p.get(UrbanParams.LARGEUR_RUE);
        alleyW = p.get(UrbanParams.LARGEUR_RUELLE);
        gap = switch (p.get(UrbanParams.PLACEMENT)) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 3;
            default -> 6;
        };
        marge = p.get(UrbanParams.MARGE_RUE);
        walled = p.on(UrbanParams.MURS);
        minGap = (int) Math.max(8, avgD * 1.3 + marge + gap + roadW / 2 + 2);
        minBlock = (int) Math.max(16, 2 * avgD + 2 * marge + roadW + gap + 2);
        double perHouse = (avgW + gap) * (avgD + gap + marge);
        double area = 0;
        for (Map.Entry<TownSize.Role, Integer> e : program.entrySet()) {
            if (e.getKey().decor() || e.getKey() == TownSize.Role.FARM) continue;
            area += e.getValue() * (e.getKey() == TownSize.Role.HOUSE ? perHouse : 3 * perHouse);
        }
        area += perHouse * 4;
        double coverage = p.get(UrbanParams.RESEAU) == 2 ? 0.5 : 0.42;
        double fill = p.get(UrbanParams.REMPLISSAGE) / 100.0;
        double total = area / (coverage * Math.max(0.3, fill));
        double outside = p.get(UrbanParams.FAUBOURGS) / 100.0;
        double r0 = Math.sqrt(total * (1 - outside) / Math.PI);
        // Une côte ou une large rivière mange une part de la ville : on la fait plus grande d'autant.
        if (p.get(UrbanParams.COTE) == 1) r0 /= Math.sqrt(Math.max(0.5, 1 - p.get(UrbanParams.PART_MER) / 100.0 * 0.7));
        if (p.on(UrbanParams.RIVIERE)) r0 += p.get(UrbanParams.LARGEUR_RIVIERE) * 0.6;
        rIn = Math.max(24, r0);
        rOut = rIn + Math.sqrt(total * outside / Math.PI) * 1.4 + 6;
        double margin = 24 + p.get(UrbanParams.SURFACE_CHAMPS) * 1.1 + p.get(UrbanParams.FORETS) * 0.5 + p.get(UrbanParams.ROUTES_CAMPAGNE) * 0.5;
        half = (int) Math.min(900, Math.max(64, rOut + margin));
        size = 2 * half + 1;
    }

    // ------------------------------------------------------------------ l'eau

    private void paintSea() {
        int coast = p.get(UrbanParams.COTE);
        if (coast == 0) return;
        int dir = p.get(UrbanParams.DIRECTION_MER);
        double a = dir == 0 ? rng.nextDouble() * Math.PI * 2 : (dir - 1) * Math.PI / 4 - Math.PI / 2;
        double nx = Math.cos(a), nz = Math.sin(a), part = p.get(UrbanParams.PART_MER) / 100.0;
        seaNormal = new double[]{nx, nz};
        if (coast == 1) {
            double offset = rIn * (1.25 - 1.6 * part);
            for (int z = -half; z <= half; z++) {
                for (int x = -half; x <= half; x++) {
                    double along = -x * nz + z * nx;
                    double s = x * nx + z * nz + (noise.at(along / 90.0, 3) - 0.5) * rIn * 0.7 + (noise.at(along / 22.0, 5) - 0.5) * 8;
                    if (s > offset) put(x, z, SEA);
                }
            }
        } else {
            double base = Math.min(half - 8, Math.max(rOut + 10, rIn * 1.35));
            for (int z = -half; z <= half; z++) {
                for (int x = -half; x <= half; x++) {
                    double theta = Math.atan2(z, x);
                    double wob = 1 + (noise.at((theta + Math.PI) * 2.0, 9) - 0.5) * 0.35 + (noise.at((theta + Math.PI) * 7.0, 11) - 0.5) * 0.1;
                    if (Math.hypot(x, z) > base * wob) put(x, z, SEA);
                }
            }
        }
    }

    private double[] seaNormal;

    private void paintRiver() {
        if (!p.on(UrbanParams.RIVIERE)) return;
        int width = p.get(UrbanParams.LARGEUR_RIVIERE);
        double wander = p.get(UrbanParams.MEANDRES) / 100.0;
        double phi = seaNormal != null ? Math.atan2(seaNormal[1], seaNormal[0]) + (rng.nextDouble() - 0.5) * 0.5 : rng.nextDouble() * Math.PI * 2;
        double dx = Math.cos(phi), dz = Math.sin(phi);
        double off = (rng.nextBoolean() ? 1 : -1) * rIn * (0.18 + rng.nextDouble() * 0.22);
        for (double t = -half * 1.5; t <= half * 1.5; t += 1) {
            double lateral = off + (noise.at(t / 70.0, 13) - 0.5) * rIn * 1.4 * wander + (noise.at(t / 21.0, 17) - 0.5) * 12 * wander;
            int x = (int) Math.round(dx * t - dz * lateral), z = (int) Math.round(dz * t + dx * lateral);
            if (!in(x, z)) continue;
            // Elle se jette dans la mer : au-delà, plus rien à tracer.
            if (at(x, z) == SEA && seaNormal != null && t > 0) break;
            river.add(new int[]{x, z});
            int w = (int) Math.round(width + (noise.at(t / 40.0, 19) - 0.5) * width * 0.3);
            brush(x, z, w, RIVER, k -> k == LAND);
        }
    }

    private interface Accept {
        boolean test(byte kind);
    }

    /** Un disque de cette largeur (une rivière n'a pas d'angles). */
    private void brush(int cx, int cz, int width, byte kind, Accept accept) {
        double r = width / 2.0;
        int rr = (int) Math.ceil(r);
        for (int z = -rr; z <= rr; z++) {
            for (int x = -rr; x <= rr; x++) {
                if (x * x + z * z > r * r) continue;
                int ax = cx + x, az = cz + z;
                if (in(ax, az) && accept.test(cell[idx(ax, az)])) cell[idx(ax, az)] = kind;
            }
        }
    }

    // ------------------------------------------------------------------ l'enceinte

    private void shapeWall() {
        int form = p.get(UrbanParams.FORME_MUR);
        packWalls = walled && p.get(UrbanParams.TYPE_MUR) == 0;
        // Les pièces des packs ne se posent qu'à angle droit : leur enceinte est toujours un rectangle.
        if (packWalls) form = 0;
        if (p.get(UrbanParams.RESEAU) == 2 && form != 0 && !walled) form = 0;
        boolean rect = form == 0;
        int n = rect ? 4 : form == 2 ? 28 : 7 + rng.nextInt(5);
        polygon = new double[n][2];
        double start = rect ? Math.PI / 4 : rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = start + i * Math.PI * 2 / n + (rect || form == 2 ? 0 : (rng.nextDouble() - 0.5) * 0.6 * Math.PI / n);
            double r = rIn * (rect ? Math.sqrt(2) * 0.9 : form == 2 ? 1 + (noise.at(a, 23) - 0.5) * 0.06 : 0.92 + rng.nextDouble() * 0.2);
            polygon[i][0] = Math.cos(a) * r;
            polygon[i][1] = Math.sin(a) * r;
        }
        if (rect) {
            // Un rectangle aligné, un peu plus long que large.
            double ratio = 0.9 + rng.nextDouble() * 0.2;
            double ax = Math.round(rIn * 0.9 * ratio), az = Math.round(rIn * 0.9 / ratio);
            polygon = new double[][]{{-ax, -az}, {ax, -az}, {ax, az}, {-ax, az}};
        }
    }

    private boolean insidePolygon(double x, double z) {
        boolean inside = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            double xi = polygon[i][0], zi = polygon[i][1], xj = polygon[j][0], zj = polygon[j][1];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) inside = !inside;
        }
        return inside;
    }

    /** Ce qui compte pour la ville elle-même : l'intérieur de l'enceinte ou, sans enceinte, ce qu'on peut couvrir en gardant une forme vivante. */
    private void paintZone() {
        boolean polygonal = walled || p.get(UrbanParams.RESEAU) == 2;
        for (int z = -half; z <= half; z++) {
            for (int x = -half; x <= half; x++) {
                boolean inside;
                if (polygonal) inside = insidePolygon(x, z);
                else {
                    double theta = Math.atan2(z, x);
                    inside = Math.hypot(x, z) < rIn * (1 + (noise.at((theta + Math.PI) * 1.7, 29) - 0.5) * 0.35);
                }
                if (inside) zone[idx(x, z)] = 1;
            }
        }
    }

    private void paintWall() {
        if (!walled) return;
        if (packWalls) {
            int x0 = (int) polygon[0][0], z0 = (int) polygon[0][1], x1 = (int) polygon[2][0], z1 = (int) polygon[2][1];
            Optional<WallSets.Ring> ring = plans.wallRing(p.get(UrbanParams.HABITANTS) >= 500, x0, z0, x1, z1);
            if (ring.isPresent()) {
                extraPieces.addAll(ring.get().pieces());
                for (TownLayout.Rect r : ring.get().rects()) {
                    for (int x = r.x0(); x <= r.x1(); x++) for (int z = r.z0(); z <= r.z1(); z++) put(x, z, WALL);
                }
                for (int[] g : ring.get().gates()) {
                    packGates.add(g);
                }
                for (TownLayout.Rect r : ring.get().gateRects()) {
                    for (int x = r.x0(); x <= r.x1(); x++) for (int z = r.z0(); z <= r.z1(); z++) put(x, z, GATE);
                }
                return;
            }
            // Le pack n'a pas de pièces de muraille : on retombe sur la pierre, plutôt que de ne rien bâtir.
            packWalls = false;
        }
        int thick = p.get(UrbanParams.EPAISSEUR_MUR);
        int every = p.get(UrbanParams.TOUR_TOUS);
        for (int i = 0; i < polygon.length; i++) {
            double[] a = polygon[i], b = polygon[(i + 1) % polygon.length];
            line((int) a[0], (int) a[1], (int) b[0], (int) b[1], thick, WALL, k -> k == LAND || k == WALL);
        }
        // Une tour à chaque angle, puis à intervalles.
        for (int i = 0; i < polygon.length; i++) {
            double[] a = polygon[i], b = polygon[(i + 1) % polygon.length];
            tower((int) a[0], (int) a[1], thick);
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            int count = (int) (len / every);
            for (int k = 1; k < count; k++) {
                tower((int) (a[0] + (b[0] - a[0]) * k / count), (int) (a[1] + (b[1] - a[1]) * k / count), thick);
            }
        }
    }

    private final List<int[]> packGates = new ArrayList<>();
    private final List<int[]> towers = new ArrayList<>();

    private void tower(int x, int z, int thick) {
        if (!in(x, z) || water(at(x, z))) return;
        int r = Math.max(1, thick / 2 + 1);
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (in(x + dx, z + dz) && !water(at(x + dx, z + dz)) && at(x + dx, z + dz) != PLAZA) put(x + dx, z + dz, TOWER);
            }
        }
        towers.add(new int[]{x, z});
    }

    /** Une ligne droite de cette largeur (Bresenham, avec un pinceau carré : pas de trous en diagonale). */
    private void line(int x0, int z0, int x1, int z1, int width, byte kind, Accept accept) {
        int dx = Math.abs(x1 - x0), dz = Math.abs(z1 - z0), sx = x0 < x1 ? 1 : -1, sz = z0 < z1 ? 1 : -1, err = dx - dz;
        int lo = -(width - 1) / 2, hi = width / 2;
        int x = x0, z = z0;
        while (true) {
            for (int a = lo; a <= hi; a++) {
                for (int b = lo; b <= hi; b++) {
                    int cx = x + a, cz = z + b;
                    if (in(cx, cz) && accept.test(cell[idx(cx, cz)])) cell[idx(cx, cz)] = kind;
                }
            }
            if (x == x1 && z == z1) return;
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
    }

    private void paintMoat() {
        int w = Math.max(3, p.get(UrbanParams.EPAISSEUR_MUR) + 2);
        for (int i = 0; i < polygon.length; i++) {
            double[] a = polygon[i], b = polygon[(i + 1) % polygon.length];
            double ax = a[0] * (1 + (w + 3) / Math.hypot(a[0], a[1])), az = a[1] * (1 + (w + 3) / Math.hypot(a[0], a[1]));
            double bx = b[0] * (1 + (w + 3) / Math.hypot(b[0], b[1])), bz = b[1] * (1 + (w + 3) / Math.hypot(b[0], b[1]));
            line((int) ax, (int) az, (int) bx, (int) bz, w, MOAT, k -> k == LAND);
        }
        // Là où une route la franchit, elle garde sa terre : c'est un passage plein, comme sur bien des fossés.
    }

    // ------------------------------------------------------------------ la citadelle

    private int[] citadelBox;

    private void paintCitadel() {
        if (!p.on(UrbanParams.CITADELLE)) return;
        int size = p.get(UrbanParams.TAILLE_CITADELLE);
        boolean urban = p.on(UrbanParams.CHATEAU_EN_VILLE) || !walled;
        double a = rng.nextDouble() * Math.PI * 2;
        // Ni sur l'eau, ni à travers une grande route : on cherche une place libre, d'abord là où on la voudrait, puis un peu plus loin ou plus près du centre.
        for (int ring = 0; ring < 5 && citadelBox == null; ring++) {
            double d = (urban ? rIn * 0.45 : rIn * 0.72) * (1 + (ring % 2 == 0 ? ring : -ring - 1) * 0.08);
            for (int tries = 0; tries < 24; tries++) {
                int cx = (int) (Math.cos(a) * d), cz = (int) (Math.sin(a) * d);
                if (free(cx - size / 2, cz - size / 2, cx + size / 2, cz + size / 2)) {
                    citadelBox = new int[]{cx - size / 2, cz - size / 2, cx + size / 2, cz + size / 2};
                    break;
                }
                a += Math.PI / 12;
            }
        }
        if (citadelBox == null) return;
        int thick = Math.max(2, p.get(UrbanParams.EPAISSEUR_MUR));
        int[] b = citadelBox;
        for (int x = b[0]; x <= b[2]; x++) {
            for (int z = b[1]; z <= b[3]; z++) {
                boolean edge = x < b[0] + thick || x > b[2] - thick || z < b[1] + thick || z > b[3] - thick;
                put(x, z, edge ? WALL : LAND);
                zone[idx(x, z)] = edge ? (byte) 0 : (byte) 2;
            }
        }
        tower(b[0], b[1], thick);
        tower(b[2], b[1], thick);
        tower(b[2], b[3], thick);
        tower(b[0], b[3], thick);
    }

    private boolean free(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x += 2) {
            for (int z = z0; z <= z1; z += 2) {
                if (!in(x, z) || cell[idx(x, z)] != LAND) return false;
            }
        }
        return true;
    }

    /** La porte de la citadelle : sur le côté qui regarde le centre, avec un bout de rue. */
    private void citadelGate() {
        if (citadelBox == null) return;
        int[] b = citadelBox;
        int cx = (b[0] + b[2]) / 2, cz = (b[1] + b[3]) / 2;
        boolean alongX = Math.abs(cx) > Math.abs(cz);
        int width = Math.max(3, roadW);
        if (alongX) {
            int side = cx > 0 ? b[0] : b[2];
            int out = cx > 0 ? -1 : 1;
            line(side - 2 * out, cz, side + 24 * out, cz, width, ROAD, k -> k == LAND || k == WALL || k == TOWER || k == ROAD);
            int start = side - 2 * out;
            line(start, cz, side, cz, width, GATE, k -> true);
            gates.add(new int[]{side, cz, width, 1});
        } else {
            int side = cz > 0 ? b[1] : b[3];
            int out = cz > 0 ? -1 : 1;
            line(cx, side - 2 * out, cx, side + 24 * out, width, ROAD, k -> k == LAND || k == WALL || k == TOWER || k == ROAD);
            int start = side - 2 * out;
            line(cx, start, cx, side, width, GATE, k -> true);
            gates.add(new int[]{cx, side, width, 0});
        }
    }

    // ------------------------------------------------------------------ la place

    private int plazaHalf;

    private void paintPlaza() {
        boolean wanted = p.on(UrbanParams.PLACE);
        plazaHalf = wanted ? p.get(UrbanParams.TAILLE_PLACE) / 2 : 0;
        if (centerPick != null) plazaHalf = Math.max(plazaHalf, Math.max(centerPick.w(), centerPick.d()) / 2 + 8);
        if (plazaHalf == 0) return;
        for (int z = -plazaHalf; z <= plazaHalf; z++) {
            for (int x = -plazaHalf; x <= plazaHalf; x++) {
                // Les angles sont cassés et le bord irrégulier : une place de ville, pas un carré dessiné à la règle.
                double wobble = noise.at((Math.atan2(z, x) + Math.PI) * 3.0, 31) * 3;
                boolean corner = Math.abs(x) + Math.abs(z) > plazaHalf * 1.65 + wobble;
                if (corner || !in(x, z) || cell[idx(x, z)] != LAND || zone[idx(x, z)] != 1) continue;
                cell[idx(x, z)] = PLAZA;
            }
        }
    }

    // ------------------------------------------------------------------ les grandes routes

    private int spokeCount() {
        int routes = p.get(UrbanParams.ROUTES);
        int n = switch (routes) {
            case 0 -> 2 + rng.nextInt(2) + (p.get(UrbanParams.HABITANTS) >= 500 ? 1 : 0) + (p.get(UrbanParams.HABITANTS) >= 1500 ? 1 : 0);
            case 1 -> 1;
            case 2 -> 2;
            default -> routes;
        };
        int want = p.get(UrbanParams.PORTES);
        return walled && want > n ? want : n;
    }

    private void spokes() {
        if (packWalls && !packGates.isEmpty()) {
            for (int[] g : packGates) walkSpoke(Math.atan2(g[1], g[0]), new int[]{g[0], g[1]});
            return;
        }
        int n = spokeCount();
        double base = rng.nextDouble() * Math.PI * 2;
        if (p.get(UrbanParams.RESEAU) == 2) base = 0;
        // La première route part vers la terre : à quoi bon une ville dont la grand-rue finit à l'eau tout de suite ?
        if (seaNormal != null) base = Math.atan2(seaNormal[1], seaNormal[0]) + Math.PI + (n % 2 == 0 ? Math.PI / n : 0);
        boolean grid = p.get(UrbanParams.RESEAU) == 2;
        for (int i = 0; i < n; i++) {
            double a = base + i * Math.PI * 2 / n;
            if (!grid) a += (rng.nextDouble() - 0.5) * 0.5 * Math.PI / n;
            walkSpoke(a, null);
        }
    }

    private void walkSpoke(double angle, int[] target) {
        boolean grid = p.get(UrbanParams.RESEAU) == 2;
        double maxAngle = Math.toRadians(p.get(UrbanParams.ANGLE));
        double reach = rOut + p.get(UrbanParams.ROUTES_CAMPAGNE);
        double x = Math.cos(angle) * Math.max(0, plazaHalf - 3), z = Math.sin(angle) * Math.max(0, plazaHalf - 3), h = angle;
        List<int[]> pts = new ArrayList<>();
        pts.add(new int[]{(int) x, (int) z});
        boolean inRiver = false, gated = false;
        for (int step = 0; step < 400; step++) {
            double desired = target != null && !gated ? Math.atan2(target[1] - z, target[0] - x) : angle;
            h += (rng.nextDouble() * 2 - 1) * maxAngle + wrap(desired - h) * 0.3;
            if (grid) h = angle;
            double nx = x + Math.cos(h) * seg, nz = z + Math.sin(h) * seg;
            boolean stop = false;
            int steps = (int) Math.ceil(seg);
            double px = x, pz = z;
            for (int s = 1; s <= steps; s++) {
                int cx = (int) Math.round(x + (nx - x) * s / steps), cz = (int) Math.round(z + (nz - z) * s / steps);
                byte k = at(cx, cz);
                if (!in(cx, cz) || k == SEA) {
                    stop = true;
                    if (in((int) px, (int) pz) && p.on(UrbanParams.PORT)) quays.add(new int[]{(int) px, (int) pz});
                    break;
                }
                if (k == RIVER && !inRiver) {
                    inRiver = true;
                    int limit = p.get(UrbanParams.PONTS);
                    if (limit >= 0 && bridgesUsed >= limit) {
                        stop = true;
                        break;
                    }
                    bridgesUsed++;
                } else if (k != RIVER) {
                    inRiver = false;
                }
                if (wallLike(k) && !gated) {
                    int limit = p.get(UrbanParams.PORTES);
                    if (limit >= 0 && cityGates >= limit) {
                        stop = true;
                        break;
                    }
                    gates.add(new int[]{cx, cz, mainW, Math.abs(Math.cos(h)) > Math.abs(Math.sin(h)) ? 1 : 0});
                    cityGates++;
                    gated = true;
                }
                px = cx;
                pz = cz;
            }
            if (!stop) {
                x = nx;
                z = nz;
            } else {
                x = px;
                z = pz;
            }
            pts.add(new int[]{(int) Math.round(x), (int) Math.round(z)});
            if (stop || Math.hypot(x, z) > reach) break;
        }
        int[][] arr = pts.toArray(new int[0][]);
        mains.add(arr);
        for (int i = 0; i + 1 < arr.length; i++) stampRoad(arr[i], arr[i + 1], mainW, ROAD_MAIN);
    }

    private final List<int[]> quays = new ArrayList<>();
    private int cityGates;

    private static double wrap(double a) {
        while (a > Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    /** Une route sur la grille : un pont sur l'eau, une porte dans le mur, la plus grande des deux si deux routes se croisent. */
    private void stampRoad(int[] a, int[] b, int width, byte kind) {
        int dx = Math.abs(b[0] - a[0]), dz = Math.abs(b[1] - a[1]), sx = a[0] < b[0] ? 1 : -1, sz = a[1] < b[1] ? 1 : -1, err = dx - dz;
        int lo = -(width - 1) / 2, hi = width / 2;
        int x = a[0], z = a[1];
        while (true) {
            for (int u = lo; u <= hi; u++) {
                for (int v = lo; v <= hi; v++) {
                    int cx = x + u, cz = z + v;
                    if (!in(cx, cz)) continue;
                    byte k = cell[idx(cx, cz)];
                    if (k == LAND || (k == ROAD && kind == ROAD_MAIN) || (k == ALLEY && kind != ALLEY)) cell[idx(cx, cz)] = kind;
                    else if (k == RIVER || k == MOAT) cell[idx(cx, cz)] = BRIDGE;
                    else if (wallLike(k) && kind == ROAD_MAIN) cell[idx(cx, cz)] = GATE;
                }
            }
            if (x == b[0] && z == b[1]) return;
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
    }

    /** Une rue qui longe les remparts par l'intérieur : elle ferme les îlots du bord, et les soldats y passent. */
    private void insideRoad() {
        citadelGate();
        if (!walled || !p.on(UrbanParams.RUE_REMPARTS) || p.get(UrbanParams.RESEAU) == 2) return;
        double inset = p.get(UrbanParams.EPAISSEUR_MUR) + Math.max(2, marge) + roadW / 2.0 + 1;
        int n = polygon.length;
        for (int i = 0; i < n; i++) {
            double[] a = polygon[i], b = polygon[(i + 1) % n];
            double la = Math.hypot(a[0], a[1]), lb = Math.hypot(b[0], b[1]);
            int ax = (int) (a[0] * (la - inset) / la), az = (int) (a[1] * (la - inset) / la);
            int bx = (int) (b[0] * (lb - inset) / lb), bz = (int) (b[1] * (lb - inset) / lb);
            stampRoadWithin(ax, az, bx, bz, roadW);
        }
    }

    private void stampRoadWithin(int x0, int z0, int x1, int z1, int width) {
        int dx = Math.abs(x1 - x0), dz = Math.abs(z1 - z0), sx = x0 < x1 ? 1 : -1, sz = z0 < z1 ? 1 : -1, err = dx - dz;
        int lo = -(width - 1) / 2, hi = width / 2;
        int x = x0, z = z0;
        while (true) {
            for (int u = lo; u <= hi; u++) {
                for (int v = lo; v <= hi; v++) {
                    int cx = x + u, cz = z + v;
                    if (in(cx, cz) && cell[idx(cx, cz)] == LAND && zone[idx(cx, cz)] == 1) cell[idx(cx, cz)] = ROAD;
                }
            }
            if (x == x1 && z == z1) return;
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
    }

    // ------------------------------------------------------------------ les rues

    private record Proposal(double x, double z, double h, int depth, double priority) {}

    private int spacingSegs() {
        double factor = switch (p.get(UrbanParams.QUARTIERS_NB)) {
            case 0 -> 1.5;
            case 2 -> 0.7;
            default -> 1.0;
        };
        return (int) Math.max(1, Math.round(p.get(UrbanParams.BRANCHE) * factor));
    }

    private double spacing() {
        return Math.max(spacingSegs() * seg, minBlock);
    }

    private void network() {
        switch (p.get(UrbanParams.RESEAU)) {
            case 1 -> rings();
            case 2 -> grid();
            default -> branches();
        }
    }

    private void branches() {
        Deque<Proposal> queue = new ArrayDeque<>();
        for (int[][] main : mains) propose(queue, main, 0);
        int budget = 900;
        while (!queue.isEmpty() && budget-- > 0) {
            Proposal pr = queue.pollFirst();
            grow(pr, queue);
        }
    }

    /** Le long d'une route, tous les tant de blocs, une rue à gauche et une à droite. */
    private void propose(Deque<Proposal> queue, int[][] road, int depth) {
        double every = spacing(), run = every * (0.4 + rng.nextDouble() * 0.3);
        for (int i = 0; i + 1 < road.length; i++) {
            int[] a = road[i], b = road[i + 1];
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            double h = Math.atan2(b[1] - a[1], b[0] - a[0]);
            double t = 0;
            while (run <= len - t) {
                t += run;
                double x = a[0] + Math.cos(h) * t, z = a[1] + Math.sin(h) * t;
                run = every * (0.85 + rng.nextDouble() * 0.3);
                if (!in((int) x, (int) z) || zone[idx((int) x, (int) z)] != 1) continue;
                boolean bigger = rng.nextInt(100) < p.get(UrbanParams.NOUVELLE_ROUTE) * 0.3;
                double jitter = Math.toRadians(p.get(UrbanParams.ANGLE)) * (rng.nextDouble() - 0.5);
                queue.addLast(new Proposal(x, z, h + Math.PI / 2 + jitter, bigger ? Math.max(0, depth - 1) : depth + 1, 0));
                queue.addLast(new Proposal(x, z, h - Math.PI / 2 + jitter, bigger ? Math.max(0, depth - 1) : depth + 1, 0));
            }
            run -= len - t;
        }
    }

    private void grow(Proposal pr, Deque<Proposal> queue) {
        double maxAngle = Math.toRadians(p.get(UrbanParams.ANGLE));
        double x = pr.x(), z = pr.z(), h = pr.h();
        List<int[]> pts = new ArrayList<>();
        pts.add(new int[]{(int) Math.round(x), (int) Math.round(z)});
        boolean joined = false;
        int start = Math.max(roadW, mainW) / 2 + 2;
        int maxSegs = (int) (rIn * 2 / seg) + 2;
        for (int i = 0; i < maxSegs; i++) {
            h += (rng.nextDouble() * 2 - 1) * maxAngle * 0.6;
            double nx = x + Math.cos(h) * seg, nz = z + Math.sin(h) * seg;
            int steps = (int) Math.ceil(seg);
            boolean blocked = false;
            double px = x, pz = z;
            for (int s = 1; s <= steps; s++) {
                int cx = (int) Math.round(x + (nx - x) * s / steps), cz = (int) Math.round(z + (nz - z) * s / steps);
                if (i == 0 && s < start) continue;
                if (!in(cx, cz)) {
                    blocked = true;
                    break;
                }
                byte k = cell[idx(cx, cz)];
                if (roadLike(k)) {
                    pts.add(new int[]{cx, cz});
                    joined = true;
                    break;
                }
                if (k != LAND || zone[idx(cx, cz)] != 1) {
                    blocked = true;
                    break;
                }
                px = cx;
                pz = cz;
            }
            if (joined) break;
            if (blocked) {
                if (Math.hypot(px - x, pz - z) > 2) pts.add(new int[]{(int) Math.round(px), (int) Math.round(pz)});
                break;
            }
            // Une rue ne longe pas une autre de trop près : il faut la place d'une rangée de maisons entre les deux.
            if (roadBeside(nx, nz, h, minGap, i == 0)) {
                if (i == 0) return;
                break;
            }
            pts.add(new int[]{(int) Math.round(nx), (int) Math.round(nz)});
            x = nx;
            z = nz;
        }
        double length = 0;
        for (int i = 0; i + 1 < pts.size(); i++) length += Math.hypot(pts.get(i + 1)[0] - pts.get(i)[0], pts.get(i + 1)[1] - pts.get(i)[1]);
        // Une rue qui finit en impasse doit au moins desservir quelque chose.
        if (length < (joined ? 4 : minGap)) return;
        int[][] arr = pts.toArray(new int[0][]);
        for (int i = 0; i + 1 < arr.length; i++) stampRoad(arr[i], arr[i + 1], roadW, ROAD);
        if (pr.depth() < 5) propose(queue, arr, pr.depth() + 1);
    }

    private boolean roadBeside(double x, double z, double h, int distance, boolean first) {
        double sx = -Math.sin(h), sz = Math.cos(h);
        for (int side = -1; side <= 1; side += 2) {
            for (int d = 2; d < distance; d++) {
                int cx = (int) Math.round(x + sx * d * side), cz = (int) Math.round(z + sz * d * side);
                if (in(cx, cz) && roadLike(cell[idx(cx, cz)]) && !first) return true;
            }
        }
        return false;
    }

    /** Le damier : tout droit, à intervalles réguliers. */
    private void grid() {
        int s = (int) spacing();
        for (int k = s; k <= half; k += s) {
            for (int sign = -1; sign <= 1; sign += 2) {
                gridLine(k * sign, true);
                gridLine(k * sign, false);
            }
        }
    }

    private void gridLine(int at, boolean alongX) {
        for (int t = -half; t <= half; t++) {
            int x = alongX ? t : at, z = alongX ? at : t;
            int lo = -(roadW - 1) / 2, hi = roadW / 2;
            for (int u = lo; u <= hi; u++) {
                int cx = alongX ? x : x + u, cz = alongX ? z + u : z;
                if (in(cx, cz) && cell[idx(cx, cz)] == LAND && zone[idx(cx, cz)] == 1) cell[idx(cx, cz)] = ROAD;
            }
        }
    }

    /** Les anneaux : des rues qui tournent autour du centre, reliées par des rues qui en partent. */
    private void rings() {
        double s = spacing();
        double maxAngle = p.get(UrbanParams.ANGLE) / 45.0;
        List<int[][]> ringsDrawn = new ArrayList<>();
        for (double r = plazaHalf + s * 0.8; r < rIn * 1.05; r += s) {
            int steps = Math.max(10, (int) (r * Math.PI * 2 / seg));
            List<int[]> pts = new ArrayList<>();
            for (int i = 0; i <= steps; i++) {
                double a = i * Math.PI * 2 / steps;
                double wob = 1 + (noise.at(a * 2 + r / 10.0, 37) - 0.5) * 0.25 * maxAngle;
                pts.add(new int[]{(int) Math.round(Math.cos(a) * r * wob), (int) Math.round(Math.sin(a) * r * wob)});
            }
            int[][] arr = pts.toArray(new int[0][]);
            for (int i = 0; i + 1 < arr.length; i++) stampRoadWithin(arr[i][0], arr[i][1], arr[i + 1][0], arr[i + 1][1], roadW);
            ringsDrawn.add(arr);
        }
        // Entre deux anneaux, une rue de temps à autre, en quinconce.
        Deque<Proposal> queue = new ArrayDeque<>();
        for (int[][] ring : ringsDrawn) {
            double every = Math.max(minBlock * 1.5, spacing());
            double run = rng.nextDouble() * every;
            for (int i = 0; i + 1 < ring.length; i++) {
                double len = Math.hypot(ring[i + 1][0] - ring[i][0], ring[i + 1][1] - ring[i][1]);
                run -= len;
                if (run > 0) continue;
                run = every * (0.8 + rng.nextDouble() * 0.4);
                double h = Math.atan2(ring[i][1], ring[i][0]);
                if (in(ring[i][0], ring[i][1]) && zone[idx(ring[i][0], ring[i][1])] == 1) queue.addLast(new Proposal(ring[i][0], ring[i][1], h, 9, 0));
            }
        }
        while (!queue.isEmpty()) grow(queue.pollFirst(), queue);
    }

    /** Les ponts demandés en plus de ceux que les grandes routes ont déjà jetés. */
    private void extraBridges() {
        int want = p.get(UrbanParams.PONTS);
        if (want <= 0 || river.isEmpty() || bridgesUsed >= want) return;
        List<int[]> candidates = new ArrayList<>();
        for (int i = 8; i + 8 < river.size(); i += 12) {
            int[] r = river.get(i);
            if (in(r[0], r[1]) && zone[idx(r[0], r[1])] == 1) candidates.add(new int[]{i, r[0], r[1]});
        }
        if (candidates.isEmpty()) return;
        int stepBy = Math.max(1, candidates.size() / (want - bridgesUsed + 1));
        for (int c = stepBy; c < candidates.size() && bridgesUsed < want; c += stepBy) {
            int i = candidates.get(c)[0];
            int[] a = river.get(i - 4), b = river.get(i + 4);
            double h = Math.atan2(b[1] - a[1], b[0] - a[0]) + Math.PI / 2;
            int cx = candidates.get(c)[1], cz = candidates.get(c)[2];
            int[] one = reachRoad(cx, cz, h), two = reachRoad(cx, cz, h + Math.PI);
            if (one == null || two == null) continue;
            stampRoad(one, two, roadW, ROAD);
            bridgesUsed++;
        }
    }

    private int[] reachRoad(int x, int z, double h) {
        for (int d = 1; d < minBlock * 3; d++) {
            int cx = (int) Math.round(x + Math.cos(h) * d), cz = (int) Math.round(z + Math.sin(h) * d);
            if (!in(cx, cz)) return null;
            byte k = cell[idx(cx, cz)];
            if (roadLike(k)) return new int[]{cx, cz};
            if (k != LAND && k != RIVER) return null;
        }
        return null;
    }

    /** Les portes de l'enceinte : deux tours de part et d'autre de chaque route qui passe. */
    private void finishGates() {
        if (!walled || packWalls) return;
        int thick = p.get(UrbanParams.EPAISSEUR_MUR);
        for (int[] g : gates) {
            if (citadelBox != null && g[0] >= citadelBox[0] - 3 && g[0] <= citadelBox[2] + 3 && g[1] >= citadelBox[1] - 3 && g[1] <= citadelBox[3] + 3) continue;
            int off = g[2] / 2 + 3;
            int ax = g[3] == 1 ? g[0] : g[0] + off, az = g[3] == 1 ? g[1] + off : g[1];
            int bx = g[3] == 1 ? g[0] : g[0] - off, bz = g[3] == 1 ? g[1] - off : g[1];
            if (wallLike(at(ax, az))) tower(ax, az, thick);
            if (wallLike(at(bx, bz))) tower(bx, bz, thick);
        }
    }

    // ------------------------------------------------------------------ les îlots

    private void findLots() {
        for (int z = -half; z <= half; z++) {
            for (int x = -half; x <= half; x++) {
                int i = idx(x, z);
                if (cell[i] == LAND && zone[i] != 0 && lotOf[i] < 0) flood(x, z, zone[i] == 2);
            }
        }
    }

    private Lot flood(int sx, int sz, boolean citadel) {
        Lot lot = new Lot(lots.size());
        lot.citadel = citadel;
        byte want = zone[idx(sx, sz)];
        lot.x0 = lot.x1 = sx;
        lot.z0 = lot.z1 = sz;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{sx, sz});
        lotOf[idx(sx, sz)] = (short) lot.id;
        double sumX = 0, sumZ = 0;
        while (!stack.isEmpty()) {
            int[] c = stack.pop();
            lot.cells.add(c);
            lot.area++;
            sumX += c[0];
            sumZ += c[1];
            lot.x0 = Math.min(lot.x0, c[0]);
            lot.x1 = Math.max(lot.x1, c[0]);
            lot.z0 = Math.min(lot.z0, c[1]);
            lot.z1 = Math.max(lot.z1, c[1]);
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + (d == 0 ? 1 : d == 1 ? -1 : 0), nz = c[1] + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (!in(nx, nz)) continue;
                int n = idx(nx, nz);
                byte k = cell[n];
                if (k == LAND && zone[n] == want && lotOf[n] < 0) {
                    lotOf[n] = (short) lot.id;
                    stack.push(new int[]{nx, nz});
                } else if (water(k)) lot.water = true;
                else if (wallLike(k)) lot.wall = true;
                else if (k == ROAD_MAIN) lot.main = true;
                else if (k == PLAZA) lot.plaza = true;
            }
        }
        lot.cx = sumX / lot.area;
        lot.cz = sumZ / lot.area;
        lots.add(lot);
        // Une tour d'angle peut isoler un recoin : l'îlot de la citadelle, c'est le plus grand.
        if (citadel && (citadelLot < 0 || lot.area > lots.get(citadelLot).area)) citadelLot = lot.id;
        return lot;
    }

    /** Un îlot trop grand se coupe en deux par une ruelle, jusqu'à ce qu'on puisse atteindre chaque maison. */
    private void splitLots() {
        int max = p.get(UrbanParams.ILOT_MAX);
        for (int round = 0; round < 7; round++) {
            boolean changed = false;
            List<Lot> current = new ArrayList<>(lots);
            for (Lot lot : current) {
                if (lot.citadel || lot.area <= max) continue;
                int w = lot.x1 - lot.x0, d = lot.z1 - lot.z0;
                if (Math.min(w, d) < 2 * minGap + alleyW) continue;
                boolean vertical = w >= d;
                int at = vertical ? (int) Math.round(lot.cx + (rng.nextDouble() - 0.5) * w * 0.2) : (int) Math.round(lot.cz + (rng.nextDouble() - 0.5) * d * 0.2);
                int lo = -(alleyW - 1) / 2, hi = alleyW / 2;
                for (int[] c : lot.cells) {
                    int v = vertical ? c[0] - at : c[1] - at;
                    if (v >= lo && v <= hi) {
                        cell[idx(c[0], c[1])] = ALLEY;
                    }
                }
                changed = true;
            }
            if (!changed) break;
            // On recompte les îlots : ils ont changé. Les numéros restent stables à réglages égaux, c'est ce qui compte pour les retouches.
            lots.clear();
            java.util.Arrays.fill(lotOf, (short) -1);
            citadelLot = -1;
            findLots();
        }
    }

    private void assignWards() {
        double[] weights = new double[UrbanWard.values().length];
        double total = 0;
        boolean anyWater = false;
        int richness = p.get(UrbanParams.RICHESSE);
        for (Lot l : lots) anyWater |= l.water;
        for (UrbanWard w : UrbanWard.values()) {
            double v = p.get(w.weight());
            if (w == UrbanWard.PORT && (!anyWater || !p.on(UrbanParams.PORT))) v = 0;
            if (w == UrbanWard.PARC && !p.on(UrbanParams.ESPACES_VERTS)) v = 0;
            if (w == UrbanWard.NOBLES) v *= 0.4 + richness * 0.35;
            if (w == UrbanWard.PAUVRES) v *= 1.6 - richness * 0.3;
            weights[w.ordinal()] = Math.max(0, v);
            total += weights[w.ordinal()];
        }
        double area = 0;
        for (Lot l : lots) if (!l.citadel) area += l.area;
        boolean[] taken = new boolean[lots.size()];
        for (Lot l : lots) {
            if (l.citadel) {
                l.ward = UrbanWard.MILITAIRE;
                taken[l.id] = true;
                continue;
            }
            Integer forced = p.lotWards().get(l.id);
            if (forced != null) {
                l.ward = UrbanWard.values()[Math.floorMod(forced, UrbanWard.values().length)];
                taken[l.id] = true;
                area -= l.area;
            }
        }
        UrbanWard[] order = {UrbanWard.ADMINISTRATION, UrbanWard.RELIGIEUX, UrbanWard.MARCHE, UrbanWard.NOBLES, UrbanWard.MILITAIRE, UrbanWard.PORT, UrbanWard.MARCHANDS, UrbanWard.ARTISANS,
                UrbanWard.PARC, UrbanWard.PAUVRES};
        for (UrbanWard w : order) {
            double quota = total <= 0 ? 0 : area * weights[w.ordinal()] / total;
            double got = 0;
            boolean first = true;
            while (got < quota && weights[w.ordinal()] > 0) {
                Lot best = null;
                double score = -1e9;
                for (Lot l : lots) {
                    if (taken[l.id] || l.area < p.get(UrbanParams.ILOT_MIN)) continue;
                    if (w == UrbanWard.PORT && !l.water) continue;
                    double s = wardScore(w, l) + rng.nextDouble() * 0.15;
                    if (s > score) {
                        score = s;
                        best = l;
                    }
                }
                if (best == null) break;
                if (!first && got + best.area * 0.5 > quota) break;
                best.ward = w;
                taken[best.id] = true;
                got += best.area;
                first = false;
            }
        }
        for (Lot l : lots) {
            if (!taken[l.id]) l.ward = l.area < p.get(UrbanParams.ILOT_MIN) ? UrbanWard.PARC : UrbanWard.HABITATIONS;
        }
    }

    private double wardScore(UrbanWard w, Lot l) {
        double d = Math.hypot(l.cx, l.cz) / rIn;
        return switch (w) {
            case MARCHE -> (l.plaza ? 2 : 0) + (l.main ? 0.5 : 0) + 1 - d;
            case NOBLES -> 1 - Math.abs(d - 0.35) - (l.wall ? 0.5 : 0);
            case RELIGIEUX -> (l.plaza ? 0.5 : 0) + 1 - Math.abs(d - 0.3);
            case ADMINISTRATION -> (l.plaza ? 1.5 : 0) + 1 - d;
            case MILITAIRE -> (l.wall ? 1 : 0) + d;
            case PORT -> 2 - d;
            case ARTISANS -> d + (l.water ? 0.4 : 0) + (l.main ? 0.2 : 0);
            case MARCHANDS -> (l.main ? 1 : 0) + 1 - Math.abs(d - 0.5);
            case PAUVRES -> d + (l.wall ? 0.5 : 0);
            default -> rng.nextDouble();
        };
    }

    // ------------------------------------------------------------------ les bâtiments

    /** Sur la grille : les cases d'un îlot d'où l'on peut aborder une rue. */
    private List<int[]> frontage(Lot lot) {
        List<int[]> out = new ArrayList<>();
        for (int[] c : lot.cells) {
            if (cell[idx(c[0], c[1])] != LAND && cell[idx(c[0], c[1])] != YARD) continue;
            for (int f = 0; f < 4; f++) {
                int rx = c[0] + DX[f], rz = c[1] + DZ[f];
                if (in(rx, rz) && roadLike(cell[idx(rx, rz)])) out.add(new int[]{c[0], c[1], f});
            }
        }
        // Le long de la rue, dans l'ordre : les maisons se suivent au lieu de se poser au hasard.
        out.sort(Comparator.<int[]>comparingInt(a -> a[2]).thenComparingInt(a -> a[2] % 2 == 0 ? a[1] : a[0]).thenComparingInt(a -> a[2] % 2 == 0 ? a[0] : a[1]));
        return out;
    }

    // Le côté de la porte : sud, ouest, nord, est (l'ordre des quarts de tour d'un plan dont la porte regarde le sud).
    private static final int[] DX = {0, -1, 0, 1}, DZ = {1, 0, -1, 0};
    private static final Rotation[] TURN = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};

    /** Essaie de poser ce plan devant cette case. La façade regarde la rue, à la marge voulue. */
    private boolean tryAt(Pick pick, TownSize.Role role, Lot lot, int[] c, int gapHere, int margin) {
        int f = c[2];
        TownLayout.Box box = new TownLayout.Box(pick.w(), pick.d());
        // La rue est peut-être en biais et le bâtiment, lui, droit : la façade recule jusqu'à ne plus mordre dessus.
        int setback = Math.max(pick.w(), pick.d()) / 2 + 2;
        int back = -1;
        TownLayout.Spot spot = null;
        TownLayout.Rect r = null;
        for (int e = 0; e <= setback; e++) {
            // La case juste devant la porte : sur la rue si la marge est nulle, sinon un peu en retrait.
            int doorX = c[0] + DX[f] - DX[f] * (margin + e), doorZ = c[1] + DZ[f] - DZ[f] * (margin + e);
            TownLayout.Spot candidate = TownLayout.byDoor(box, TURN[f], doorX, doorZ);
            TownLayout.Rect rect = TownLayout.rect(box, candidate);
            if (fits(rect, lot.id, gapHere)) {
                spot = candidate;
                r = rect;
                back = e;
                break;
            }
        }
        if (r == null) return false;
        stamp(r, role, lot);
        // Le chemin de la porte à la rue.
        int doorX = c[0] + DX[f] - DX[f] * (margin + back), doorZ = c[1] + DZ[f] - DZ[f] * (margin + back);
        for (int m = 0; m < margin + back; m++) {
            int px = doorX + DX[f] * m, pz = doorZ + DZ[f] * m;
            if (in(px, pz) && (cell[idx(px, pz)] == LAND || cell[idx(px, pz)] == YARD)) cell[idx(px, pz)] = PATH;
        }
        int slot = slotCounter++;
        if (p.removed().contains(slot)) {
            unstamp(r, lot);
            return true;
        }
        placed.add(new Placed(slot, pick, role, spot.x(), spot.z(), spot.rotation(), r, lot.id));
        built.merge(role, 1, Integer::sum);
        return true;
    }

    /** Le rectangle est libre, et à bonne distance de ce qui est bâti. */
    private boolean fits(TownLayout.Rect r, int lotId, int gapHere) {
        for (int x = r.x0(); x <= r.x1(); x++) {
            for (int z = r.z0(); z <= r.z1(); z++) {
                if (!in(x, z)) return false;
                int i = idx(x, z);
                if (cell[i] != LAND || (lotId >= 0 && lotOf[i] != lotId)) return false;
            }
        }
        if (gapHere <= 0) return true;
        for (int x = r.x0() - gapHere; x <= r.x1() + gapHere; x++) {
            for (int z = r.z0() - gapHere; z <= r.z1() + gapHere; z++) {
                if (x >= r.x0() && x <= r.x1() && z >= r.z0() && z <= r.z1()) continue;
                if (in(x, z) && (cell[idx(x, z)] == BUILDING || cell[idx(x, z)] == SPECIAL)) return false;
            }
        }
        return true;
    }

    private void stamp(TownLayout.Rect r, TownSize.Role role, Lot lot) {
        byte kind = role.decor() ? DECOR : role == TownSize.Role.HOUSE ? BUILDING : SPECIAL;
        for (int x = r.x0(); x <= r.x1(); x++) for (int z = r.z0(); z <= r.z1(); z++) cell[idx(x, z)] = kind;
    }

    private void unstamp(TownLayout.Rect r, Lot lot) {
        for (int x = r.x0(); x <= r.x1(); x++) for (int z = r.z0(); z <= r.z1(); z++) cell[idx(x, z)] = LAND;
    }

    /** Le plan de ce numéro, en tenant compte de ce qu'on a retiré au dé à la main. */
    private Pick choose(List<Pick> pool, int index) {
        Integer again = p.rerolls().get(slotCounter);
        return pool.get(Math.floorMod(index + (again == null ? 0 : again), pool.size()));
    }

    /** Au milieu de la place, si on l'a voulu : l'hôtel de ville, le temple, le château. */
    private void fillCenter() {
        if (centerPick == null) return;
        TownLayout.Box box = new TownLayout.Box(centerPick.w(), centerPick.d());
        TownLayout.Spot spot = new TownLayout.Spot(0, 0, Rotation.NONE);
        TownLayout.Rect r = TownLayout.rect(box, spot);
        for (int x = r.x0(); x <= r.x1(); x++) for (int z = r.z0(); z <= r.z1(); z++) put(x, z, SPECIAL);
        placed.add(new Placed(slotCounter++, centerPick, centerRole, 0, 0, Rotation.NONE, r, -1));
        built.merge(centerRole, 1, Integer::sum);
    }

    /** Le donjon au milieu de la citadelle, la caserne et les tours autour : ils n'ont pas de rue devant eux, ils regardent la cour. */
    private int citadelBarracks;

    private void fillCitadel() {
        if (citadelBox == null || citadelLot < 0) return;
        Lot lot = lots.get(citadelLot);
        int cx = (citadelBox[0] + citadelBox[2]) / 2, cz = (citadelBox[1] + citadelBox[3]) / 2;
        List<Pick> keep = pool(TownSize.Role.TOWNHALL, UrbanWard.MILITAIRE);
        if (keep.isEmpty()) keep = pool(TownSize.Role.BARRACKS, UrbanWard.MILITAIRE);
        if (!keep.isEmpty() && inCitadel(keep.get(0), TownSize.Role.TOWNHALL, cx, cz, Rotation.NONE, lot)) citadelKeep = true;
        List<Pick> barracks = pool(TownSize.Role.BARRACKS, UrbanWard.MILITAIRE);
        int[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        int inset = Math.max(2, p.get(UrbanParams.EPAISSEUR_MUR)) + 2;
        int k = 0;
        for (int[] corner : corners) {
            List<Pick> pool2 = k == 0 && !barracks.isEmpty() ? barracks : pool(TownSize.Role.TOWER, UrbanWard.MILITAIRE);
            if (pool2.isEmpty()) continue;
            Pick pick = pool2.get(0);
            int x = corner[0] < 0 ? citadelBox[0] + inset + pick.w() / 2 : citadelBox[2] - inset - pick.w() / 2;
            int z = corner[1] < 0 ? citadelBox[1] + inset + pick.d() / 2 : citadelBox[3] - inset - pick.d() / 2;
            TownSize.Role role = pool2 == barracks ? TownSize.Role.BARRACKS : TownSize.Role.TOWER;
            if (inCitadel(pick, role, x, z, corner[1] < 0 ? Rotation.NONE : Rotation.CLOCKWISE_180, lot) && role == TownSize.Role.BARRACKS) citadelBarracks++;
            k++;
        }
        // Le reste est la cour : pavée, comme une place, recoins compris.
        for (Lot l : lots) {
            if (!l.citadel) continue;
            for (int[] c : l.cells) if (cell[idx(c[0], c[1])] == LAND) cell[idx(c[0], c[1])] = PLAZA;
        }
    }

    private boolean citadelKeep;

    private boolean inCitadel(Pick pick, TownSize.Role role, int x, int z, Rotation rotation, Lot lot) {
        TownLayout.Box box = new TownLayout.Box(pick.w(), pick.d());
        TownLayout.Spot spot = new TownLayout.Spot(x, z, rotation);
        TownLayout.Rect r = TownLayout.rect(box, spot);
        if (!fits(r, lot.id, 0)) return false;
        stamp(r, role, lot);
        int slot = slotCounter++;
        if (p.removed().contains(slot)) {
            unstamp(r, lot);
            return true;
        }
        placed.add(new Placed(slot, pick, role, x, z, rotation, r, lot.id));
        built.merge(role, 1, Integer::sum);
        return true;
    }

    private void placeSpecials() {
        fillCitadel();
        List<TownSize.Role> order = new ArrayList<>();
        if (centerRole != TownSize.Role.TOWNHALL && !citadelKeep) order.add(TownSize.Role.TOWNHALL);
        addN(order, TownSize.Role.CHURCH, centerRole == TownSize.Role.CHURCH ? -1 : 0);
        addN(order, TownSize.Role.MARKET, 0);
        addN(order, TownSize.Role.BARRACKS, (centerRole == TownSize.Role.BARRACKS ? -1 : 0) - citadelBarracks);
        addN(order, TownSize.Role.WAREHOUSE, 0);
        addN(order, TownSize.Role.HOSPITAL, 0);
        addN(order, TownSize.Role.SCHOOL, 0);
        addN(order, TownSize.Role.TAVERN, 0);
        addN(order, TownSize.Role.GRAVEYARD, 0);
        addN(order, TownSize.Role.CRAFT, 0);
        addN(order, TownSize.Role.TOWER, 0);
        int[] turn = new int[TownSize.Role.values().length];
        for (TownSize.Role role : order) {
            UrbanWard home = homeOf(role);
            List<Pick> pool = pool(role, home);
            if (pool.isEmpty()) {
                noModel++;
                continue;
            }
            Pick pick = choose(pool, turn[role.ordinal()]++);
            // Le coût se calcule une fois pour toutes : il contient un peu de hasard, et un tri veut des comparaisons qui ne changent pas.
            Map<Integer, Double> costs = new HashMap<>();
            for (Lot l : lots) costs.put(l.id, specialCost(role, l));
            List<Lot> sorted = new ArrayList<>(lots);
            sorted.sort(Comparator.comparingDouble(l -> costs.get(l.id)));
            boolean done = false;
            for (Lot l : sorted) {
                if (l.citadel) continue;
                if (l.area < pick.w() * pick.d()) continue;
                for (int[] c : frontage(l)) {
                    if (tryAt(pick, role, l, c, Math.max(1, gap), marge)) {
                        done = true;
                        break;
                    }
                }
                if (done) break;
            }
            if (!done) noRoom++;
        }
    }

    private void addN(List<TownSize.Role> out, TownSize.Role role, int delta) {
        int n = program.getOrDefault(role, 0) + delta;
        for (int i = 0; i < n; i++) out.add(role);
    }

    private double specialCost(TownSize.Role role, Lot l) {
        double d = Math.hypot(l.cx, l.cz) / rIn;
        boolean ward = l.ward.roles().contains(role);
        double base = ward ? 0 : 3;
        return switch (role) {
            case TOWNHALL, MARKET -> base + d - (l.plaza ? 2 : 0);
            case CHURCH -> base + d - (l.plaza ? 1 : 0);
            case BARRACKS, TOWER -> base - d - (l.wall ? 1 : 0);
            case GRAVEYARD -> base + Math.abs(d - 0.8);
            default -> base + d * 0.5 + rng.nextDouble() * 0.2;
        };
    }

    private UrbanWard homeOf(TownSize.Role role) {
        for (UrbanWard w : UrbanWard.values()) if (w.roles().contains(role)) return w;
        return UrbanWard.HABITATIONS;
    }

    private void placeHouses() {
        int total = program.getOrDefault(TownSize.Role.HOUSE, 0);
        double outside = p.get(UrbanParams.FAUBOURGS) / 100.0;
        int inside = (int) Math.round(total * (1 - outside));
        int fill = p.get(UrbanParams.REMPLISSAGE);
        List<Lot> sorted = new ArrayList<>(lots);
        sorted.sort(Comparator.comparingDouble(l -> Math.hypot(l.cx, l.cz)));
        int count = 0;
        int[] turn = new int[UrbanWard.values().length];
        // Deux passes : d'abord la façade sur les rues, puis ce qui reste, en retrait, dans les îlots profonds.
        for (Lot l : sorted) {
            if (count >= inside) break;
            if (!l.ward.houses() || l.citadel || l.area < p.get(UrbanParams.ILOT_MIN)) continue;
            List<Pick> pool = pool(TownSize.Role.HOUSE, l.ward);
            if (pool.isEmpty()) pool = pool(TownSize.Role.HOUSE, UrbanWard.HABITATIONS);
            if (pool.isEmpty()) continue;
            int wardGap = l.ward == UrbanWard.PAUVRES ? Math.min(1, gap) : l.ward == UrbanWard.NOBLES ? gap + 2 : gap;
            for (int[] c : frontage(l)) {
                if (count >= inside) break;
                byte k = cell[idx(c[0], c[1])];
                if (k != LAND) continue;
                if (rng.nextInt(100) >= fill) continue;
                Pick pick = choose(pool, turn[l.ward.ordinal()]);
                if (tryAt(pick, TownSize.Role.HOUSE, l, c, wardGap, marge)) {
                    turn[l.ward.ordinal()]++;
                    count++;
                    l.houses++;
                }
            }
        }
        housesLeft = total - count;
    }

    private int housesLeft;

    // ------------------------------------------------------------------ les faubourgs

    private void suburbs() {
        if (housesLeft <= 0) return;
        int density = p.get(UrbanParams.DENSITE_FAUBOURGS);
        int depth = (int) (avgD * 1.8) + marge + 4;
        // Une bande le long des routes qui sortent : là où l'on bâtit dehors. Un nouvel îlot par côté de route, comme en ville.
        boolean shanty = p.on(UrbanParams.BIDONVILLE) && walled;
        for (int[][] road : mains) {
            int index = 0;
            for (int i = 0; i + 1 < road.length; i++) {
                int[] a = road[i], b = road[i + 1];
                int steps = (int) Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
                for (int s = 0; s <= steps && steps > 0; s++) {
                    int x = a[0] + (b[0] - a[0]) * s / steps, z = a[1] + (b[1] - a[1]) * s / steps;
                    if (!in(x, z) || zone[idx(x, z)] != 0) continue;
                    index++;
                    if (index % Math.max(1, (int) avgW) == 0 && rng.nextInt(100) < p.get(UrbanParams.NOUVELLE_ROUTE)) offshoot(x, z, Math.atan2(b[1] - a[1], b[0] - a[0]) + (rng.nextBoolean() ? 1 : -1) * Math.PI / 2);
                }
            }
        }
        Lot suburb = new Lot(lots.size());
        suburb.ward = shanty ? UrbanWard.PAUVRES : UrbanWard.HABITATIONS;
        int id = suburb.id;
        for (int z = -half; z <= half; z++) {
            for (int x = -half; x <= half; x++) {
                int i = idx(x, z);
                if (cell[i] != LAND || zone[i] != 0 || lotOf[i] >= 0) continue;
                if (distanceToRoad(x, z, depth)) {
                    lotOf[i] = (short) id;
                    suburb.cells.add(new int[]{x, z});
                    suburb.area++;
                }
            }
        }
        if (suburb.area == 0) return;
        lots.add(suburb);
        List<Pick> pool = pool(TownSize.Role.HOUSE, suburb.ward);
        if (pool.isEmpty()) pool = pool(TownSize.Role.HOUSE, UrbanWard.HABITATIONS);
        if (pool.isEmpty()) return;
        // On part des portes vers le dehors : les faubourgs s'étirent, ils ne se dispersent pas.
        List<int[]> front = frontage(suburb);
        front.sort(Comparator.comparingDouble(c -> Math.hypot(c[0], c[1])));
        int turn = 0, wardGap = Math.max(gap, shanty ? 1 : 2);
        for (int[] c : front) {
            if (housesLeft <= 0) break;
            if (cell[idx(c[0], c[1])] != LAND) continue;
            if (rng.nextInt(100) >= density) continue;
            Pick pick = choose(pool, turn);
            if (tryAt(pick, TownSize.Role.HOUSE, suburb, c, wardGap, marge)) {
                turn++;
                housesLeft--;
                suburb.houses++;
            }
        }
        noRoom += housesLeft;
        // Ce qui n'a pas été bâti retourne à la campagne : des champs peuvent s'y poser, et le plan n'y met pas d'aplat de couleur.
        for (int[] c : suburb.cells) {
            if (cell[idx(c[0], c[1])] == LAND) lotOf[idx(c[0], c[1])] = -1;
        }
        suburb.suburb = true;
    }

    private void offshoot(int x, int z, double h) {
        int segs = 1 + rng.nextInt(3);
        double maxAngle = Math.toRadians(p.get(UrbanParams.ANGLE));
        double cx = x, cz = z;
        for (int i = 0; i < segs; i++) {
            h += (rng.nextDouble() * 2 - 1) * maxAngle;
            double nx = cx + Math.cos(h) * seg, nz = cz + Math.sin(h) * seg;
            if (!in((int) nx, (int) nz) || cell[idx((int) nx, (int) nz)] != LAND || zone[idx((int) nx, (int) nz)] != 0) return;
            stampRoad(new int[]{(int) cx, (int) cz}, new int[]{(int) nx, (int) nz}, Math.max(1, roadW - 1), ROAD);
            cx = nx;
            cz = nz;
        }
    }

    private boolean distanceToRoad(int x, int z, int depth) {
        for (int d = 1; d <= depth; d += 2) {
            if (roadLike(at(x + d, z)) || roadLike(at(x - d, z)) || roadLike(at(x, z + d)) || roadLike(at(x, z - d))) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ places et décors

    private void placeDecor() {
        // Sur la place : le puits ou la fontaine au milieu, les étals et les statues autour.
        if (plazaHalf > 0 && centerPick == null) {
            List<Pick> wells = pool(TownSize.Role.PLAZA, UrbanWard.MARCHE);
            if (!wells.isEmpty()) putOnPlaza(wells.get(0), TownSize.Role.PLAZA, 0, 0);
        }
        int stalls = program.getOrDefault(TownSize.Role.STALL, 0);
        List<Pick> stallPool = pool(TownSize.Role.STALL, UrbanWard.MARCHE);
        int ringR = Math.max(4, plazaHalf - 5);
        for (int i = 0; i < stalls && plazaHalf > 0 && !stallPool.isEmpty(); i++) {
            double a = i * Math.PI * 2 / Math.max(1, stalls) + 0.3;
            putOnPlaza(stallPool.get(i % stallPool.size()), TownSize.Role.STALL, (int) (Math.cos(a) * ringR), (int) (Math.sin(a) * ringR));
        }
        int statues = program.getOrDefault(TownSize.Role.STATUE, 0);
        List<Pick> statuePool = pool(TownSize.Role.STATUE, UrbanWard.NOBLES);
        for (int i = 0; i < statues && plazaHalf > 0 && !statuePool.isEmpty(); i++) {
            double a = i * Math.PI * 2 / Math.max(1, statues) + 1.1;
            if (!putOnPlaza(statuePool.get(i % statuePool.size()), TownSize.Role.STATUE, (int) (Math.cos(a) * ringR * 0.55), (int) (Math.sin(a) * ringR * 0.55))) noRoom++;
        }
        // Les décors de rue : aux coins de rues, sur ce qui reste libre.
        int decors = program.getOrDefault(TownSize.Role.DECOR, 0);
        List<Pick> decorPool = pool(TownSize.Role.DECOR, UrbanWard.HABITATIONS);
        if (!decorPool.isEmpty()) {
            List<Lot> sorted = new ArrayList<>(lots);
            sorted.sort(Comparator.comparingDouble(l -> Math.hypot(l.cx, l.cz)));
            int placedDecor = 0;
            for (Lot l : sorted) {
                if (placedDecor >= decors) break;
                for (int[] c : frontage(l)) {
                    if (placedDecor >= decors) break;
                    if (cell[idx(c[0], c[1])] != LAND || rng.nextInt(6) != 0) continue;
                    Pick pick = decorPool.get(placedDecor % decorPool.size());
                    if (tryAt(pick, TownSize.Role.DECOR, l, c, 0, 0)) {
                        placedDecor++;
                        break;
                    }
                }
            }
        }
        placeLamps();
    }

    private boolean putOnPlaza(Pick pick, TownSize.Role role, int x, int z) {
        TownLayout.Box box = new TownLayout.Box(pick.w(), pick.d());
        // Tourné vers le centre, quand ça compte.
        Rotation r = Math.abs(x) > Math.abs(z) ? (x > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90) : (z > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE);
        TownLayout.Spot spot = new TownLayout.Spot(x, z, r);
        TownLayout.Rect rect = TownLayout.rect(box, spot);
        for (int cx = rect.x0(); cx <= rect.x1(); cx++) {
            for (int cz = rect.z0(); cz <= rect.z1(); cz++) {
                if (!in(cx, cz) || cell[idx(cx, cz)] != PLAZA) return false;
            }
        }
        for (int cx = rect.x0(); cx <= rect.x1(); cx++) for (int cz = rect.z0(); cz <= rect.z1(); cz++) cell[idx(cx, cz)] = DECOR;
        int slot = slotCounter++;
        if (p.removed().contains(slot)) {
            for (int cx = rect.x0(); cx <= rect.x1(); cx++) for (int cz = rect.z0(); cz <= rect.z1(); cz++) cell[idx(cx, cz)] = PLAZA;
            return true;
        }
        placed.add(new Placed(slot, pick, role, x, z, r, rect, -1));
        built.merge(role, 1, Integer::sum);
        return true;
    }

    /** Des lanternes le long des rues : sur le bord, jamais au milieu du passage. */
    private void placeLamps() {
        int chance = p.get(UrbanParams.LANTERNES);
        UrbanParams.Def d = UrbanParams.count(TownSize.Role.LAMP);
        int limit = d != null && p.get(d) >= 0 ? p.get(d) : Integer.MAX_VALUE;
        if (chance == 0 || limit == 0) return;
        int every = 8 + (100 - chance) / 4;
        int k = 0, count = 0;
        for (int z = -half + 1; z < half; z++) {
            for (int x = -half + 1; x < half; x++) {
                byte c = cell[idx(x, z)];
                if (c != LAND || zone[idx(x, z)] != 1) continue;
                boolean edge = roadLike(at(x + 1, z)) || roadLike(at(x - 1, z)) || roadLike(at(x, z + 1)) || roadLike(at(x, z - 1));
                if (!edge || ++k % every != 0 || count >= limit) continue;
                lamps.add(new int[]{x, z});
                cell[idx(x, z)] = DECOR;
                count++;
            }
        }
        built.merge(TownSize.Role.LAMP, count, Integer::sum);
    }

    /** Les jardins : dans les parcs et les beaux quartiers, sur le reste. */
    private void fillParks() {
        int budget = program.getOrDefault(TownSize.Role.GARDEN, 0);
        List<Pick> pool = pool(TownSize.Role.GARDEN, UrbanWard.PARC);
        int done = 0;
        for (Lot l : lots) {
            if (l.ward != UrbanWard.PARC && l.ward != UrbanWard.NOBLES && l.ward != UrbanWard.RELIGIEUX) continue;
            if (!pool.isEmpty()) {
                for (int[] c : frontage(l)) {
                    if (done >= budget) break;
                    if (cell[idx(c[0], c[1])] != LAND || rng.nextInt(4) != 0) continue;
                    if (tryAt(pool.get(done % pool.size()), TownSize.Role.GARDEN, l, c, 1, 1)) done++;
                }
            }
            if (l.ward == UrbanWard.PARC) {
                for (int[] c : l.cells) {
                    if (cell[idx(c[0], c[1])] == LAND) cell[idx(c[0], c[1])] = GARDEN;
                    if (Math.floorMod(c[0] * 7 + c[1] * 13, 37) == 0 && cell[idx(c[0], c[1])] == GARDEN) plantTree(c[0], c[1]);
                }
            }
        }
    }

    private void plantTree(int x, int z) {
        if (trees.size() >= 4000) return;
        for (int[] t : trees) if (Math.abs(t[0] - x) < 3 && Math.abs(t[1] - z) < 3) return;
        trees.add(new int[]{x, z});
        cell[idx(x, z)] = TREE;
    }

    // ------------------------------------------------------------------ le port

    /** Des pontons en bois sur l'eau, là où un îlot du port la touche. */
    private void harbour() {
        if (!p.on(UrbanParams.PORT)) return;
        int every = 18, k = 0;
        for (Lot l : lots) {
            if (l.ward != UrbanWard.PORT) continue;
            for (int[] c : l.cells) {
                for (int f = 0; f < 4; f++) {
                    int nx = c[0] + DX[f], nz = c[1] + DZ[f];
                    if (!in(nx, nz) || !water(cell[idx(nx, nz)]) || ++k % every != 0) continue;
                    pier(nx, nz, f);
                }
            }
        }
        for (int[] q : quays) {
            for (int f = 0; f < 4; f++) {
                int nx = q[0] + DX[f], nz = q[1] + DZ[f];
                if (in(nx, nz) && cell[idx(nx, nz)] == SEA) {
                    pier(nx, nz, f);
                    break;
                }
            }
        }
    }

    private void pier(int x, int z, int f) {
        int len = 8 + rng.nextInt(9);
        for (int i = 0; i < len; i++) {
            int cx = x + DX[f] * i, cz = z + DZ[f] * i;
            if (!in(cx, cz) || !water(cell[idx(cx, cz)])) return;
            cell[idx(cx, cz)] = PIER;
        }
    }

    // ------------------------------------------------------------------ la campagne

    private void countryside() {
        int layout = p.get(UrbanParams.CHAMPS);
        if (layout == 0) return;
        int farms = program.getOrDefault(TownSize.Role.FARM, 0);
        List<Pick> pool = pool(TownSize.Role.FARM, UrbanWard.HABITATIONS);
        double budget = p.get(UrbanParams.HABITANTS) * 9.0 * p.get(UrbanParams.SURFACE_CHAMPS) / 30.0;
        int farmIndex = 0;
        if (layout == 2 && !river.isEmpty()) {
            for (int i = 0; i < river.size() && budget > 0; i += 28) {
                int[] r = river.get(i);
                if (in(r[0], r[1]) && zone[idx(r[0], r[1])] == 0) budget -= fieldAt(r[0], r[1], rng.nextInt(4));
            }
        } else if (layout == 3) {
            for (int tries = 0; tries < 300 && farmIndex < farms; tries++) {
                double a = rng.nextDouble() * Math.PI * 2, d = rOut + rng.nextDouble() * Math.max(10, half - rOut - 16);
                int x = (int) (Math.cos(a) * d), z = (int) (Math.sin(a) * d);
                if (!pool.isEmpty() && farm(pool.get(farmIndex % pool.size()), x, z, rng.nextInt(4))) {
                    farmIndex++;
                    budget -= fieldAt(x + DX[1] * 12, z + DZ[1] * 12, 0);
                    budget -= fieldAt(x + DX[3] * 12, z + DZ[3] * 12, 2);
                }
            }
        } else {
            for (int[][] road : mains) {
                double run = 0;
                for (int i = 0; i + 1 < road.length && budget > 0; i++) {
                    int[] a = road[i], b = road[i + 1];
                    if (!in(a[0], a[1]) || zone[idx(a[0], a[1])] != 0) continue;
                    run += Math.hypot(b[0] - a[0], b[1] - a[1]);
                    if (run < 18) continue;
                    run = 0;
                    double h = Math.atan2(b[1] - a[1], b[0] - a[0]);
                    for (int side = -1; side <= 1; side += 2) {
                        int fx = (int) Math.round(a[0] - Math.sin(h) * side * (mainW + 12)), fz = (int) Math.round(a[1] + Math.cos(h) * side * (mainW + 12));
                        double got = fieldAt(fx, fz, side > 0 ? 0 : 2);
                        budget -= got;
                        if (got > 0 && farmIndex < farms && !pool.isEmpty()) {
                            if (farm(pool.get(farmIndex % pool.size()), (int) (fx - Math.cos(h) * 16), (int) (fz - Math.sin(h) * 16), Math.abs(Math.cos(h)) > Math.abs(Math.sin(h)) ? (side > 0 ? 2 : 0) : (side > 0 ? 3 : 1))) farmIndex++;
                        }
                    }
                }
            }
        }
        // Ce qui reste se pose à l'écart des routes, autour de la ville : un damier de parcelles, comme autour d'une vraie ville.
        if (layout != 2) {
            for (int tries = 0; tries < 400 && budget > 0; tries++) {
                double a = rng.nextDouble() * Math.PI * 2, d = rIn * 1.12 + rng.nextDouble() * Math.max(10, half - rIn * 1.12 - 10);
                budget -= fieldAt((int) (Math.cos(a) * d), (int) (Math.sin(a) * d), rng.nextInt(4));
            }
        }
        for (; farmIndex < farms; farmIndex++) {
            if (pool.isEmpty()) noModel++;
            else if (!looseFarm(pool.get(farmIndex % pool.size()))) noRoom++;
        }
    }

    /** Une ferme qui n'a pas trouvé sa place le long d'une route : au bord d'un champ, ou à défaut dans la campagne. */
    private boolean looseFarm(Pick pick) {
        for (int tries = 0; tries < 200; tries++) {
            double a = rng.nextDouble() * Math.PI * 2, d = rIn * 1.15 + rng.nextDouble() * Math.max(8, half - rIn * 1.15 - 12);
            if (farm(pick, (int) (Math.cos(a) * d), (int) (Math.sin(a) * d), rng.nextInt(4))) return true;
        }
        return false;
    }

    private boolean farm(Pick pick, int x, int z, int f) {
        TownLayout.Box box = new TownLayout.Box(pick.w(), pick.d());
        TownLayout.Spot spot = new TownLayout.Spot(x, z, TURN[Math.floorMod(f, 4)]);
        TownLayout.Rect r = TownLayout.rect(box, spot);
        if (!fits(r, -1, 2)) return false;
        for (int cx = r.x0(); cx <= r.x1(); cx++) for (int cz = r.z0(); cz <= r.z1(); cz++) cell[idx(cx, cz)] = SPECIAL;
        int slot = slotCounter++;
        if (p.removed().contains(slot)) {
            for (int cx = r.x0(); cx <= r.x1(); cx++) for (int cz = r.z0(); cz <= r.z1(); cz++) cell[idx(cx, cz)] = LAND;
            return true;
        }
        placed.add(new Placed(slot, pick, TownSize.Role.FARM, x, z, spot.rotation(), r, -1));
        built.merge(TownSize.Role.FARM, 1, Integer::sum);
        return true;
    }

    /** Un champ, là s'il y a la place. Renvoie sa surface (0 s'il ne tient pas). */
    private int fieldAt(int x, int z, int orient) {
        int w = 12 + rng.nextInt(10), d = 16 + rng.nextInt(14);
        if (orient % 2 == 1) {
            int t = w;
            w = d;
            d = t;
        }
        int x0 = x - w / 2, z0 = z - d / 2, x1 = x0 + w, z1 = z0 + d;
        for (int cx = x0 - 1; cx <= x1 + 1; cx++) {
            for (int cz = z0 - 1; cz <= z1 + 1; cz++) {
                if (!in(cx, cz) || cell[idx(cx, cz)] != LAND || zone[idx(cx, cz)] != 0 || lotOf[idx(cx, cz)] >= 0) return 0;
            }
        }
        for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++) cell[idx(cx, cz)] = FIELD;
        return (w + 1) * (d + 1);
    }

    private void forests() {
        int f = p.get(UrbanParams.FORETS);
        if (f == 0) return;
        double threshold = 0.82 - f / 100.0 * 0.6;
        int step = 5;
        for (int z = -half; z <= half; z += step) {
            for (int x = -half; x <= half; x += step) {
                int cx = x + rng.nextInt(3) - 1, cz = z + rng.nextInt(3) - 1;
                if (!in(cx, cz) || cell[idx(cx, cz)] != LAND || zone[idx(cx, cz)] != 0 || lotOf[idx(cx, cz)] >= 0) continue;
                if (Math.hypot(cx, cz) < rOut * 0.9) continue;
                if (noise.at2(cx / 46.0, cz / 46.0, 41) < threshold) continue;
                if (distanceToRoad(cx, cz, 3)) continue;
                if (trees.size() >= 4000) return;
                trees.add(new int[]{cx, cz});
                cell[idx(cx, cz)] = TREE;
            }
        }
    }

    private void roadTrees() {
        int chance = p.get(UrbanParams.ARBRES_RUES);
        if (chance == 0) return;
        for (Lot l : lots) {
            if (l.ward != UrbanWard.NOBLES && l.ward != UrbanWard.PARC && l.ward != UrbanWard.HABITATIONS && l.ward != UrbanWard.RELIGIEUX) continue;
            int k = 0;
            for (int[] c : frontage(l)) {
                if (cell[idx(c[0], c[1])] != LAND || ++k % 11 != 0 || rng.nextInt(100) >= chance) continue;
                if (at(c[0] + 2, c[1]) == BUILDING || at(c[0] - 2, c[1]) == BUILDING || at(c[0], c[1] + 2) == BUILDING || at(c[0], c[1] - 2) == BUILDING) continue;
                plantTree(c[0], c[1]);
            }
        }
    }

    // ------------------------------------------------------------------ le résultat

    private long[] collect(Accept accept) {
        int n = 0;
        for (byte k : cell) if (accept.test(k)) n++;
        long[] out = new long[n];
        int i = 0;
        for (int z = -half; z <= half; z++) {
            for (int x = -half; x <= half; x++) {
                if (accept.test(cell[idx(x, z)])) out[i++] = ((long) x << 32) | (z & 0xFFFFFFFFL);
            }
        }
        return out;
    }

    private static long[] pack(List<int[]> cells) {
        long[] out = new long[cells.size()];
        for (int i = 0; i < out.length; i++) out[i] = ((long) cells.get(i)[0] << 32) | (cells.get(i)[1] & 0xFFFFFFFFL);
        return out;
    }

    private UrbanPlan finish() {
        List<TownPlan.Piece> pieces = new ArrayList<>();
        for (Placed b : placed) pieces.add(new TownPlan.Piece(b.pick().ref(), b.x(), b.z(), b.rotation().ordinal(), 0));
        pieces.addAll(extraPieces);
        long[] streets = collect(k -> k == ROAD_MAIN || k == ROAD || k == ALLEY || k == PLAZA || k == GATE || k == PATH);
        int t = p.get(UrbanParams.HABITANTS);
        TownSize size = t <= 60 ? TownSize.HAMEAU : t <= 180 ? TownSize.VILLAGE : t <= 500 ? TownSize.BOURG : t <= 1200 ? TownSize.VILLE : TownSize.CAPITALE;
        // L'étendue de la ville à proprement parler : sa terre, pas ses forêts.
        int extent = (int) Math.min(size() * 1.0, 2 * (rOut + 16));
        TownPlan town = new TownPlan(p.style().name(), size, pieces, streets, extent);
        UrbanExtras extras = new UrbanExtras();
        extras.families = Math.max(1, t / Math.max(1, p.get(UrbanParams.PAR_MAISON)));
        extras.streetMaterial = p.get(UrbanParams.MATIERE_RUES);
        extras.wallType = packWalls ? 0 : p.get(UrbanParams.TYPE_MUR);
        extras.wallHeight = p.get(UrbanParams.HAUTEUR_MUR);
        extras.water = p.on(UrbanParams.CREUSER) ? collect(UrbanGenerator::water) : new long[0];
        extras.bridges = collect(k -> k == BRIDGE || k == PIER);
        extras.fields = collect(k -> k == FIELD);
        extras.crop = p.get(UrbanParams.CULTURE);
        extras.gardens = collect(k -> k == GARDEN);
        extras.trees = pack(trees);
        extras.treeKind = p.get(UrbanParams.CLIMAT);
        extras.lamps = pack(lamps);
        extras.name = p.name();
        town.setWalls(packWalls ? citadelWalls() : collect(UrbanGenerator::wallLike));
        extras.towers = collect(k -> k == TOWER);
        extras.lintels = packWalls ? new long[0] : collect(k -> k == GATE);
        town.setExtras(extras);

        UrbanPlan.Stats stats = new UrbanPlan.Stats(t, built.getOrDefault(TownSize.Role.HOUSE, 0), program.getOrDefault(TownSize.Role.HOUSE, 0), placed.size(), lots.size(), packWalls ? packGates.size() : cityGates,
                towers.size(), bridgesUsed, noRoom + Math.max(0, housesLeft > 0 && p.get(UrbanParams.FAUBOURGS) == 0 ? housesLeft : 0), noModel, size(), new EnumMap<>(built));
        return new UrbanPlan(town, half, view(), lotOf, new ArrayList<>(placed), lots, stats);
    }

    /** Avec une enceinte en pièces de pack, les seuls murs en blocs sont ceux de la citadelle : sous les pièces, ils se doubleraient. */
    private long[] citadelWalls() {
        if (citadelBox == null) return new long[0];
        List<int[]> out = new ArrayList<>();
        int[] b = citadelBox;
        for (int x = b[0]; x <= b[2]; x++) {
            for (int z = b[1]; z <= b[3]; z++) {
                if (in(x, z) && wallLike(cell[idx(x, z)])) out.add(new int[]{x, z});
            }
        }
        return pack(out);
    }

    private int size() {
        return size;
    }

    /** Ce que l'écran dessine : une valeur par case. */
    private byte[] view() {
        byte[] out = new byte[cell.length];
        for (int i = 0; i < cell.length; i++) {
            byte k = cell[i];
            int lot = lotOf[i];
            UrbanWard ward = lot >= 0 && lot < lots.size() ? lots.get(lot).ward : null;
            if (ward != null && (k == LAND || k == YARD) && !lots.get(lot).suburb) out[i] = (byte) (VIEW_LOT + ward.ordinal());
            else if (ward != null && k == BUILDING) out[i] = (byte) (VIEW_HOUSE + ward.ordinal());
            else out[i] = k;
        }
        return out;
    }
}
