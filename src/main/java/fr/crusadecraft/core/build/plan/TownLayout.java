package fr.crusadecraft.core.build.plan;

import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Le plan d'une ville (P34), en logique pure, d'après l'urbanisme attesté de chaque civilisation :
 * <ul>
 *   <li>{@link Pattern#MARCHE} — le bourg médiéval : place du marché au carrefour des routes, hôtel de ville et église
 *       sur la place, parcelles en lanière le long de la grand-rue ;</li>
 *   <li>{@link Pattern#CASTRUM} — la ville romaine : cardo nord-sud et decumanus est-ouest qui se croisent au forum,
 *       basilique et temple sur le forum, îlots (insulae) en damier, enceinte carrée à quatre portes ;</li>
 *   <li>{@link Pattern#KAOGONG} — la capitale chinoise du Kaogong ji : palais au centre tourné au sud, cour devant,
 *       marché derrière, temple des ancêtres à l'est, avenue axiale, ruelles est-ouest où chaque maison regarde le sud,
 *       muraille carrée ;</li>
 *   <li>{@link Pattern#JOBO} — Heian-kyō : palais au nord, avenue Suzaku jusqu'à la porte du sud, grille jōbō, temple
 *       de l'Est près de la porte, marché de l'Ouest ; pas de muraille ;</li>
 *   <li>{@link Pattern#KAHUN} — la ville égyptienne planifiée : enceinte de briques crues alignée sur les points
 *       cardinaux, axe processionnel est-ouest du palais au temple, rangées de maisons dos à dos ;</li>
 *   <li>{@link Pattern#TRELLEBORG} — la forteresse circulaire viking : rempart rond, quatre portes, deux rues en croix,
 *       dans chaque quartier quatre maisons en carré autour d'une cour, le surplus dans une basse-cour au sud-est ;</li>
 *   <li>{@link Pattern#RUE} — un quartier ajouté : une rue, des maisons de part et d'autre.</li>
 * </ul>
 * Le nord est −z, comme dans Minecraft. Une entrée « au sud » regarde +z (rotation nulle).
 */
public final class TownLayout {
    /** Une emprise au sol, entrée au sud (+z), comme les plans. */
    public record Box(int w, int d) {}

    /** Où va un bâtiment : son milieu relatif au centre de la ville, et sa rotation. */
    public record Spot(int x, int z, Rotation rotation) {}

    /** Le plan dressé : la place de chaque bâtiment ({@code null} s'il n'a pas trouvé place), les rues, les murs. */
    public record Layout(List<Spot> spots, List<int[]> streets, List<int[]> walls, int extent) {}

    public enum Pattern { MARCHE, CASTRUM, KAOGONG, JOBO, KAHUN, TRELLEBORG, RUE }

    /** Un rectangle de blocs, bornes comprises. */
    record Rect(int x0, int z0, int x1, int z1) {
        boolean overlaps(Rect o, int gap) {
            return x0 - gap <= o.x1 && o.x0 <= x1 + gap && z0 - gap <= o.z1 && o.z0 <= z1 + gap;
        }

        boolean contains(int x, int z) {
            return x0 <= x && x <= x1 && z0 <= z && z <= z1;
        }

        Rect grow(int n) {
            return new Rect(x0 - n, z0 - n, x1 + n, z1 + n);
        }
    }

    /** L'écart minimal entre deux bâtiments (une venelle). */
    public static final int STREET = 2;
    /** Demi-largeurs : grande rue (cinq blocs), avenue d'apparat (sept), ruelle (trois). */
    static final int MAIN = 2, AVENUE = 3, LANE = 1;

    private TownLayout() {}

    /** Le rectangle occupé par une emprise posée là, tournée ainsi (bornes exactes des plans). */
    static Rect rect(Box b, Spot s) {
        int minX = -(b.w() / 2), maxX = b.w() - 1 - b.w() / 2, minZ = -(b.d() / 2), maxZ = b.d() - 1 - b.d() / 2;
        int[] a = PlanBuilder.turn(minX, minZ, s.rotation()), c = PlanBuilder.turn(maxX, maxZ, s.rotation());
        return new Rect(s.x() + Math.min(a[0], c[0]), s.z() + Math.min(a[1], c[1]), s.x() + Math.max(a[0], c[0]), s.z() + Math.max(a[1], c[1]));
    }

    /** La case juste devant l'entrée, relative au milieu du bâtiment. */
    static int[] door(Box b, Rotation r) {
        return PlanBuilder.turn(0, b.d() - 1 - b.d() / 2 + 1, r);
    }

    /** Le bâtiment posé de sorte que la case devant sa porte soit (x, z). */
    static Spot byDoor(Box b, Rotation r, int x, int z) {
        int[] d = door(b, r);
        return new Spot(x - d[0], z - d[1], r);
    }

    /** L'entrée tournée vers le centre, vue depuis un bâtiment posé en (x, z). */
    static Rotation facingCenter(int x, int z) {
        if (Math.abs(x) > Math.abs(z)) return x > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
        return z > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE;
    }

    // ------------------------------------------------------------------ le placeur

    private static final class Placer {
        final List<Box> boxes;
        final List<TownSize.Role> roles;
        final Spot[] spots;
        final List<Rect> taken = new ArrayList<>();
        final List<Rect> corridors = new ArrayList<>();
        final List<Integer> pending = new ArrayList<>();

        Placer(List<Box> boxes, List<TownSize.Role> roles) {
            this(boxes, roles, boxes.size());
        }

        /** Seuls les {@code core} premiers attendent d'abord leur place ; les autres viennent au remplissage. */
        Placer(List<Box> boxes, List<TownSize.Role> roles, int core) {
            this.boxes = boxes;
            this.roles = roles;
            this.spots = new Spot[boxes.size()];
            for (int i = 0; i < core; i++) pending.add(i);
        }

        boolean fits(Rect r) {
            for (Rect t : taken) if (r.overlaps(t, 1)) return false;
            for (Rect c : corridors) if (r.overlaps(c, 0)) return false;
            return true;
        }

        /** P36 : pendant le remplissage, rien ne sort de la ville déjà posée. */
        Rect bounds;

        boolean put(int i, Spot s) {
            if (spots[i] != null) return false;
            Rect r = rect(boxes.get(i), s);
            if (bounds != null && (r.x0() < bounds.x0() || r.x1() > bounds.x1() || r.z0() < bounds.z0() || r.z1() > bounds.z1())) return false;
            if (!fits(r)) return false;
            spots[i] = s;
            taken.add(r);
            pending.remove((Integer) i);
            return true;
        }

        /** Une rue, gardée libre de tout bâtiment. */
        void street(int x0, int z0, int x1, int z1) {
            corridors.add(new Rect(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)));
        }

        /** Une rue horizontale (est-ouest) de demi-largeur hw. */
        void streetX(int z, int hw, int x0, int x1) {
            street(x0, z - hw, x1, z + hw);
        }

        /** Une rue verticale (nord-sud) de demi-largeur hw. */
        void streetZ(int x, int hw, int z0, int z1) {
            street(x - hw, z0, x + hw, z1);
        }

        int first(TownSize.Role role) {
            for (int i : pending) if (roles.get(i) == role) return i;
            return -1;
        }

        Rect rectOf(int i) {
            return spots[i] == null ? null : rect(boxes.get(i), spots[i]);
        }

        int maxDepth() {
            int m = 5;
            for (int i = 1; i < boxes.size(); i++) if (roles.get(i) != TownSize.Role.PLAZA) m = Math.max(m, Math.max(boxes.get(i).w(), boxes.get(i).d()));
            return m;
        }
    }

    /**
     * Une rive de rue : les bâtiments s'y alignent l'un après l'autre, la porte sur la rue. {@code side} −1 : du côté
     * nord (rue est-ouest) ou ouest (rue nord-sud) ; +1 : sud ou est. On remplit de {@code from} vers {@code to}.
     */
    private static final class Frontage {
        final boolean alongX;
        final int line, hw, side, from, to;
        int cursor;
        /** P36 : au remplissage, un retrait et un écart tirés au sort (une rue vivante n'est pas une règle). */
        java.util.Random jitter;

        Frontage(boolean alongX, int line, int hw, int side, int from, int to) {
            this.alongX = alongX;
            this.line = line;
            this.hw = hw;
            this.side = side;
            this.from = from;
            this.to = to;
            this.cursor = from;
        }

        Rotation rotation() {
            if (alongX) return side < 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
            return side < 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
        }

        boolean place(Placer p, int i, int gap) {
            Box b = p.boxes.get(i);
            Rotation r = rotation();
            int setback = jitter == null ? 0 : jitter.nextInt(3);
            gap = jitter == null ? gap : 1 + jitter.nextInt(3);
            int doorPerp = line + side * (hw + setback);
            Spot t = alongX ? byDoor(b, r, 0, doorPerp) : byDoor(b, r, doorPerp, 0);
            Rect rr = rect(b, t);
            int lo = alongX ? rr.x0() : rr.z0(), hi = alongX ? rr.x1() : rr.z1();
            int dir = to >= from ? 1 : -1;
            for (int c = cursor; dir > 0 ? c <= to : c >= to; c += dir) {
                int doorAlong = dir > 0 ? c - lo : c - hi;
                if (dir > 0 ? doorAlong + hi > to : doorAlong + lo < to) return false;
                Spot s = alongX ? byDoor(b, r, doorAlong, doorPerp) : byDoor(b, r, doorPerp, doorAlong);
                if (p.put(i, s)) {
                    cursor = dir > 0 ? doorAlong + hi + 1 + gap : doorAlong + lo - 1 - gap;
                    return true;
                }
            }
            return false;
        }
    }

    /** Range les bâtiments en attente qui conviennent, tour à tour sur chaque rive du groupe (d'où la symétrie). */
    private static void fill(Placer p, List<Frontage> group, Predicate<TownSize.Role> accepts) {
        int k = 0;
        for (Integer i : new ArrayList<>(p.pending)) {
            if (!accepts.test(p.roles.get(i))) continue;
            for (int t = 0; t < group.size(); t++) {
                Frontage f = group.get((k + t) % group.size());
                if (f.place(p, i, 1)) {
                    k = (k + t + 1) % group.size();
                    break;
                }
            }
        }
    }

    private static Predicate<TownSize.Role> only(TownSize.Role... roles) {
        return r -> {
            for (TownSize.Role x : roles) if (x == r) return true;
            return false;
        };
    }

    private static final Predicate<TownSize.Role> ANY = r -> true;

    // ------------------------------------------------------------------ dresser

    /**
     * Dresse le plan. {@code boxes.get(0)} est le centre (hôtel de ville, palais, halle) ; {@code roles} dit ce qu'est
     * chaque bâtiment ; {@code walled} : une enceinte, là où la civilisation en bâtissait. Les rues trop longues sont
     * reprises plus longues jusqu'à ce que tout tienne (cinq essais).
     */
    public static Layout plan(Pattern pattern, List<Box> boxes, List<TownSize.Role> roles, boolean walled, long seed) {
        return plan(pattern, boxes, roles, walled, seed, boxes.size());
    }

    /**
     * P36 : les {@code core} premiers forment le programme de la ville ; les suivants la remplissent (maisons, lanternes,
     * étals, puits, statues le long des rues, jardins dans les îlots), posés seulement là où ils tiennent sans agrandir
     * la ville.
     */
    public static Layout plan(Pattern pattern, List<Box> boxes, List<TownSize.Role> roles, boolean walled, long seed, int core) {
        double area = 0;
        for (int i = 0; i < core; i++) area += (boxes.get(i).w() + 3.0) * (boxes.get(i).d() + 3.0);
        int length = (int) Math.max(24, Math.sqrt(area) * 0.75);
        Placer best = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            Placer p = new Placer(boxes, roles, core);
            switch (pattern) {
                case MARCHE -> marche(p, length);
                case CASTRUM -> castrum(p, length);
                case KAOGONG -> kaogong(p, length);
                case JOBO -> jobo(p, length);
                case KAHUN -> kahun(p, length);
                case TRELLEBORG -> trelleborg(p);
                case RUE -> rue(p, length);
            }
            if (best == null || p.pending.size() < best.pending.size()) best = p;
            if (p.pending.isEmpty()) break;
            length = (int) (length * 1.35);
        }
        densify(best, core, seed);
        return finish(best, pattern, walled);
    }

    /**
     * Le remplissage : chaque côté de chaque rue, dans les limites de la ville déjà posée, reçoit ce qui y tient ; puis
     * les jardins se glissent dans les cœurs d'îlots et les coins libres.
     */
    private static void densify(Placer p, int core, long seed) {
        if (core >= p.boxes.size()) return;
        Rect box = null;
        for (int i = 0; i < core; i++) {
            Rect r = p.rectOf(i);
            if (r == null) continue;
            box = box == null ? r : new Rect(Math.min(box.x0(), r.x0()), Math.min(box.z0(), r.z0()), Math.max(box.x1(), r.x1()), Math.max(box.z1(), r.z1()));
        }
        if (box == null) return;
        p.bounds = box.grow(2);
        for (int i = core; i < p.boxes.size(); i++) if (p.roles.get(i) != TownSize.Role.GARDEN) p.pending.add(i);
        // Le long des rues : les deux côtés de chaque tronçon, du centre vers le bord.
        List<Frontage> sides = new ArrayList<>();
        for (Rect c : new ArrayList<>(p.corridors)) {
            boolean alongX = c.x1() - c.x0() >= c.z1() - c.z0();
            int hw = alongX ? (c.z1() - c.z0()) / 2 : (c.x1() - c.x0()) / 2;
            int line = alongX ? (c.z0() + c.z1()) / 2 : (c.x0() + c.x1()) / 2;
            int a = alongX ? Math.max(c.x0(), p.bounds.x0()) : Math.max(c.z0(), p.bounds.z0());
            int b = alongX ? Math.min(c.x1(), p.bounds.x1()) : Math.min(c.z1(), p.bounds.z1());
            if (b - a < 4) continue;
            sides.add(new Frontage(alongX, line, hw, -1, a, b));
            sides.add(new Frontage(alongX, line, hw, 1, a, b));
        }
        java.util.Random rnd = new java.util.Random(seed);
        for (Frontage f : sides) f.jitter = rnd;
        // Les carrefours d'abord : une placette à chaque croisement (puits, statue, lanterne, étal) aux quatre coins.
        corners(p, rnd);
        placeAlong(p, sides);
        // Puis des ruelles parallèles aux grandes rues, là où la place est libre (les rangs de parcelles de derrière),
        // bordées à leur tour : les îlots se remplissent au lieu de rester en herbe.
        int depth = 9;
        int houses = 0;
        for (int i = 0; i < p.boxes.size(); i++) {
            if (p.roles.get(i) != TownSize.Role.HOUSE) continue;
            depth += Math.max(p.boxes.get(i).w(), p.boxes.get(i).d());
            houses++;
        }
        depth = houses == 0 ? 11 : depth / (houses + 1);
        int pitch = 2 * depth + 2 * LANE + 3;
        List<Frontage> lanes = new ArrayList<>();
        for (Rect c : new ArrayList<>(p.corridors)) {
            boolean alongX = c.x1() - c.x0() >= c.z1() - c.z0();
            if ((alongX ? c.x1() - c.x0() : c.z1() - c.z0()) < 24) continue;
            int line = alongX ? (c.z0() + c.z1()) / 2 : (c.x0() + c.x1()) / 2;
            int a = alongX ? Math.max(c.x0(), p.bounds.x0()) : Math.max(c.z0(), p.bounds.z0());
            int b = alongX ? Math.min(c.x1(), p.bounds.x1()) : Math.min(c.z1(), p.bounds.z1());
            for (int k = 1; k <= 6; k++) {
                for (int sgn : new int[]{-1, 1}) {
                    int l = line + sgn * k * pitch;
                    if (alongX ? (l < p.bounds.z0() + depth || l > p.bounds.z1() - depth) : (l < p.bounds.x0() + depth || l > p.bounds.x1() - depth)) continue;
                    lanes.addAll(freeLane(p, alongX, l, a, b));
                }
            }
        }
        for (Frontage f : lanes) f.jitter = rnd;
        placeAlong(p, lanes);
        // Les jardins, puis le reste du mobilier, semés au hasard dans ce qui reste (arrière-cours, coins, bords).
        List<Integer> scatter = new ArrayList<>();
        for (int i = core; i < p.boxes.size(); i++) if (p.roles.get(i) == TownSize.Role.GARDEN) scatter.add(i);
        for (Integer i : p.pending) if (p.roles.get(i).decor()) scatter.add(i);
        p.pending.clear();
        java.util.Random random = new java.util.Random(seed ^ 0x5DEECE66DL);
        int w = p.bounds.x1() - p.bounds.x0() + 1, d = p.bounds.z1() - p.bounds.z0() + 1;
        for (Integer i : scatter) {
            for (int attempt = 0; attempt < 60; attempt++) {
                if (p.put(i, new Spot(p.bounds.x0() + random.nextInt(w), p.bounds.z0() + random.nextInt(d), Rotation.values()[random.nextInt(4)]))) break;
            }
        }
        p.bounds = null;
    }

    /** Le bourg médiéval : la place du marché au carrefour, la grand-rue est-ouest, la rue du sud. */
    private static void marche(Placer p, int length) {
        int plaza = p.first(TownSize.Role.PLAZA);
        int q = plaza >= 0 ? Math.max(p.boxes.get(plaza).w(), p.boxes.get(plaza).d()) / 2 + 3 : 7;
        if (plaza >= 0) p.put(plaza, new Spot(0, 0, Rotation.NONE));
        p.taken.add(new Rect(-q, -q, q, q));
        // L'hôtel de ville sur le côté nord de la place, porte sur la place (une venelle entre les deux).
        p.put(0, byDoor(p.boxes.get(0), Rotation.NONE, 0, -q - 1));
        Rect hall = p.rectOf(0);
        p.streetX(0, MAIN, -length, -q - 1);
        p.streetX(0, MAIN, q + 1, length);
        p.streetZ(0, MAIN, q + 1, length / 2 + q);
        // L'église et le marché couvert sur la place, à côté de l'hôtel de ville ; la taverne en face.
        fill(p, List.of(new Frontage(true, -q - 1, 0, -1, hall == null ? MAIN + 1 : hall.x1() + 2, q + length / 2),
                new Frontage(true, -q - 1, 0, -1, hall == null ? -MAIN - 1 : hall.x0() - 2, -q - length / 2)),
                only(TownSize.Role.CHURCH, TownSize.Role.MARKET, TownSize.Role.STATUE));
        fill(p, List.of(new Frontage(true, q + 1, 0, 1, -MAIN - 1, -q - length / 2), new Frontage(true, q + 1, 0, 1, MAIN + 1, q + length / 2)),
                only(TownSize.Role.TAVERN, TownSize.Role.CRAFT, TownSize.Role.STALL));
        // Les parcelles le long de la grand-rue, de la place vers les portes ; puis la rue du sud.
        List<Frontage> high = List.of(new Frontage(true, 0, MAIN, -1, q + 1, length), new Frontage(true, 0, MAIN, 1, -q - 1, -length),
                new Frontage(true, 0, MAIN, 1, q + 1, length), new Frontage(true, 0, MAIN, -1, -q - 1, -length));
        List<Frontage> south = List.of(new Frontage(false, 0, MAIN, 1, q + 1, length / 2 + q), new Frontage(false, 0, MAIN, -1, q + 1, length / 2 + q));
        fill(p, high, only(TownSize.Role.HOUSE, TownSize.Role.CRAFT, TownSize.Role.TAVERN));
        fill(p, south, ANY.and(r -> r != TownSize.Role.FARM));
        // Les fermes et la garde au bout des rues, vers la campagne.
        List<Frontage> ends = List.of(new Frontage(true, 0, MAIN, -1, length, q + 1), new Frontage(true, 0, MAIN, 1, -length, -q - 1),
                new Frontage(false, 0, MAIN, 1, length / 2 + q, q + 1));
        fill(p, ends, ANY);
        fill(p, high, ANY);
        fill(p, south, ANY);
    }

    /** La ville romaine : forum au croisement du cardo et du decumanus, insulae autour. */
    private static void castrum(Placer p, int length) {
        int plaza = p.first(TownSize.Role.PLAZA);
        int q = plaza >= 0 ? Math.max(p.boxes.get(plaza).w(), p.boxes.get(plaza).d()) / 2 + 3 : 8;
        if (plaza >= 0) p.put(plaza, new Spot(0, 0, Rotation.NONE));
        p.taken.add(new Rect(-q, -q, q, q));
        // La basilique au nord du forum, le temple en face, au sud (une venelle entre eux et le forum).
        p.put(0, byDoor(p.boxes.get(0), Rotation.NONE, 0, -q - 1));
        int temple = p.first(TownSize.Role.CHURCH);
        if (temple >= 0) p.put(temple, byDoor(p.boxes.get(temple), Rotation.CLOCKWISE_180, 0, q + 1));
        Rect north = p.rectOf(0), south = temple >= 0 ? p.rectOf(temple) : null;
        int depth = p.maxDepth(), pitch = 2 * depth + 2 * LANE + 4;
        // Decumanus maximus (est-ouest) de part et d'autre du forum, cardo maximus (nord-sud) au-delà de la basilique et du temple.
        p.streetX(0, MAIN, -length, -q - 1);
        p.streetX(0, MAIN, q + 1, length);
        int cn = north == null ? -q - 1 : north.z0() - 2, cs = south == null ? q + 1 : south.z1() + 2;
        p.streetZ(0, MAIN, -length, cn);
        p.streetZ(0, MAIN, cs, length);
        // Les rues secondaires, en damier : elles découpent les insulae.
        List<Frontage> secondary = new ArrayList<>();
        for (int k = 1; k * pitch <= length; k++) {
            for (int s : new int[]{-1, 1}) {
                int z = s * k * pitch;
                p.streetX(z, LANE, -length, length);
                secondary.add(new Frontage(true, z, LANE, -1, 0, s * length));
                secondary.add(new Frontage(true, z, LANE, 1, 0, -s * length));
                int x = s * k * pitch;
                p.streetZ(x, LANE, -length, length);
                secondary.add(new Frontage(false, x, LANE, -1, 0, s * length));
                secondary.add(new Frontage(false, x, LANE, 1, 0, -s * length));
            }
        }
        List<Frontage> decumanus = List.of(new Frontage(true, 0, MAIN, -1, q + 1, length), new Frontage(true, 0, MAIN, -1, -q - 1, -length),
                new Frontage(true, 0, MAIN, 1, q + 1, length), new Frontage(true, 0, MAIN, 1, -q - 1, -length));
        List<Frontage> cardo = List.of(new Frontage(false, 0, MAIN, -1, cs, length), new Frontage(false, 0, MAIN, 1, cs, length),
                new Frontage(false, 0, MAIN, -1, cn, -length), new Frontage(false, 0, MAIN, 1, cn, -length));
        // Les marchés et les tavernes sur les grandes rues près du forum ; la garde au bout, vers les portes.
        fill(p, decumanus, only(TownSize.Role.MARKET, TownSize.Role.STALL, TownSize.Role.WAREHOUSE, TownSize.Role.TAVERN, TownSize.Role.CRAFT));
        List<Frontage> gates = List.of(new Frontage(true, 0, MAIN, -1, length, q + 1), new Frontage(true, 0, MAIN, 1, -length, -q - 1),
                new Frontage(false, 0, MAIN, 1, length, cs), new Frontage(false, 0, MAIN, -1, -length, cn));
        fill(p, gates, only(TownSize.Role.BARRACKS, TownSize.Role.TOWER, TownSize.Role.GRAVEYARD));
        fill(p, decumanus, ANY);
        fill(p, cardo, ANY);
        fill(p, secondary, ANY);
    }

    /** La capitale du Kaogong ji : palais au centre, cour devant, marché derrière, ruelles où tout regarde le sud. */
    private static void kaogong(Placer p, int length) {
        p.put(0, new Spot(0, 0, Rotation.NONE));
        Rect palace = p.rectOf(0);
        int plaza = p.first(TownSize.Role.PLAZA);
        int courtSouth = palace.z1() + 1;
        if (plaza >= 0) {
            Box b = p.boxes.get(plaza);
            p.put(plaza, new Spot(0, palace.z1() + 3 + b.d() / 2, Rotation.NONE));
            Rect court = p.rectOf(plaza);
            if (court != null) courtSouth = court.z1() + 1;
        }
        int core = Math.max(palace.x1(), plaza >= 0 && p.rectOf(plaza) != null ? p.rectOf(plaza).x1() : 0) + 2;
        // L'avenue axiale, de la cour à la porte du sud.
        p.streetZ(0, AVENUE, courtSouth + 1, length);
        // Le marché derrière le palais (前朝后市).
        int lane = palace.z0() - 2;
        p.streetX(lane, LANE, -core - length / 2, core + length / 2);
        fill(p, List.of(new Frontage(true, lane, LANE, -1, 0, core + length / 2), new Frontage(true, lane, LANE, -1, -1, -core - length / 2)),
                only(TownSize.Role.MARKET));
        // Le temple des ancêtres à l'est de l'axe, devant le palais (左祖右社).
        fill(p, List.of(new Frontage(false, 0, AVENUE, 1, courtSouth + 1, length)), only(TownSize.Role.CHURCH));
        // Les ruelles est-ouest : chaque maison au nord de sa ruelle, porte au sud (坐北朝南).
        int pitch = p.maxDepth() + 2 * LANE + 2;
        List<Frontage> hutongs = new ArrayList<>();
        for (int z = lane - pitch; z >= -length; z -= pitch) hutongs.addAll(hutong(p, z, core, length));
        for (int z = lane + pitch; z <= length; z += pitch) hutongs.addAll(hutong(p, z, core, length));
        fill(p, hutongs, ANY);
    }

    private static List<Frontage> hutong(Placer p, int z, int core, int length) {
        p.streetX(z, LANE, -length, -AVENUE - 1);
        p.streetX(z, LANE, AVENUE + 1, length);
        int start = Math.max(AVENUE + 2, core);
        return List.of(new Frontage(true, z, LANE, -1, start, length), new Frontage(true, z, LANE, -1, -start, -length));
    }

    /** Heian-kyō : palais au nord, avenue Suzaku vers la porte du sud, grille de rues, temple de l'Est, marché de l'Ouest. */
    private static void jobo(Placer p, int length) {
        Box hallBox = p.boxes.get(0);
        int north = -length / 3;
        p.put(0, byDoor(hallBox, Rotation.NONE, 0, north));
        int south = north + length + length / 2;
        p.streetZ(0, AVENUE, north, south);
        int plaza = p.first(TownSize.Role.PLAZA);
        if (plaza >= 0) p.put(plaza, byDoor(p.boxes.get(plaza), Rotation.CLOCKWISE_180, 0, south + 1));
        int pitch = 2 * p.maxDepth() + 2 * LANE + 4, half = length;
        List<Frontage> streets = new ArrayList<>();
        for (int z = north + pitch / 2; z <= south; z += pitch) {
            p.streetX(z, LANE, -half, -AVENUE - 1);
            p.streetX(z, LANE, AVENUE + 1, half);
            streets.add(new Frontage(true, z, LANE, -1, AVENUE + 2, half));
            streets.add(new Frontage(true, z, LANE, -1, -AVENUE - 2, -half));
            streets.add(new Frontage(true, z, LANE, 1, AVENUE + 2, half));
            streets.add(new Frontage(true, z, LANE, 1, -AVENUE - 2, -half));
        }
        for (int x = pitch; x <= half; x += pitch) {
            p.streetZ(x, LANE, north, south);
            p.streetZ(-x, LANE, north, south);
        }
        // Le temple de l'Est (Tō-ji) et le marché de l'Ouest, dans la moitié sud, au bord de Suzaku.
        fill(p, List.of(new Frontage(false, 0, AVENUE, 1, south, north)), only(TownSize.Role.CHURCH));
        fill(p, List.of(new Frontage(false, 0, AVENUE, -1, south, north)), only(TownSize.Role.MARKET));
        fill(p, List.of(new Frontage(false, 0, AVENUE, 1, north + hallBox.d() + 2, south), new Frontage(false, 0, AVENUE, -1, north + hallBox.d() + 2, south)),
                only(TownSize.Role.TAVERN, TownSize.Role.BARRACKS));
        fill(p, streets, ANY);
    }

    /** La ville égyptienne : palais à l'ouest, axe processionnel jusqu'au temple à l'est, rangées de maisons dos à dos. */
    private static void kahun(Placer p, int length) {
        Box hall = p.boxes.get(0);
        int west = -length / 2;
        p.put(0, byDoor(hall, Rotation.COUNTERCLOCKWISE_90, west, 0));
        int start = west + 1;
        int plaza = p.first(TownSize.Role.PLAZA);
        if (plaza >= 0 && p.put(plaza, byDoor(p.boxes.get(plaza), Rotation.CLOCKWISE_90, west + 1, 0))) {
            start = p.rectOf(plaza).x1() + 2;
        }
        int east = west + length + length / 2;
        p.streetX(0, AVENUE, start, east);
        int temple = p.first(TownSize.Role.CHURCH);
        if (temple >= 0) p.put(temple, byDoor(p.boxes.get(temple), Rotation.CLOCKWISE_90, east + 1, 0));
        // Les grands bâtiments le long de l'axe ; les maisons en rangées, de part et d'autre de rues parallèles.
        List<Frontage> axis = List.of(new Frontage(true, 0, AVENUE, -1, start, east), new Frontage(true, 0, AVENUE, 1, start, east));
        fill(p, axis, only(TownSize.Role.MARKET, TownSize.Role.BARRACKS, TownSize.Role.TAVERN, TownSize.Role.TOWER));
        int pitch = 2 * p.maxDepth() + 2 * LANE + 4;
        List<Frontage> rows = new ArrayList<>();
        int rowWest = Math.min(west, p.rectOf(0) == null ? west : p.rectOf(0).x0()) , rowEast = east;
        for (int k = 1; k * pitch <= length; k++) {
            for (int s : new int[]{-1, 1}) {
                int z = s * (AVENUE + k * pitch - pitch / 2);
                p.streetX(z, LANE, rowWest, rowEast);
                rows.add(new Frontage(true, z, LANE, -1, rowWest + 1, rowEast));
                rows.add(new Frontage(true, z, LANE, 1, rowWest + 1, rowEast));
            }
        }
        p.streetZ(rowWest - 2, LANE, -length, length);
        fill(p, rows, ANY);
        fill(p, axis, ANY);
    }

    /** La forteresse circulaire : deux rues en croix, des cours carrées de quatre maisons, la halle dans un quartier. */
    private static void trelleborg(Placer p) {
        int depth = 5, width = 5;
        for (int i = 1; i < p.boxes.size(); i++) {
            if (p.roles.get(i) == TownSize.Role.PLAZA) continue;
            depth = Math.max(depth, p.boxes.get(i).d());
            width = Math.max(width, p.boxes.get(i).w());
        }
        int c0 = width / 2 + 2;
        Box hall = p.boxes.get(0);
        int hallHalf = Math.max(hall.w(), hall.d()) / 2 + 1;
        int quarter = Math.max(hallHalf, c0 + depth) + MAIN + 3;
        int radius = quarter + Math.max(hallHalf, c0 + depth) + 4;
        p.streetX(0, MAIN, -radius, radius);
        p.streetZ(0, MAIN, -radius, radius);
        int plaza = p.first(TownSize.Role.PLAZA);
        if (plaza >= 0) p.put(plaza, new Spot(0, 0, Rotation.NONE));
        // La halle seule dans le quartier nord-est, porte vers le carrefour.
        p.put(0, new Spot(quarter, -quarter, facingCenter(quarter, -quarter)));
        int[][] quarters = {{-quarter, -quarter}, {-quarter, quarter}, {quarter, quarter}};
        for (int[] q : quarters) {
            int qx = q[0], qz = q[1];
            Rotation[] rot = {Rotation.NONE, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90, Rotation.CLOCKWISE_90};
            int[][] doors = {{qx, qz - c0}, {qx, qz + c0}, {qx - c0, qz}, {qx + c0, qz}};
            p.taken.add(new Rect(qx - c0 + 1, qz - c0 + 1, qx + c0 - 1, qz + c0 - 1));
            for (int s = 0; s < 4; s++) {
                for (Integer i : new ArrayList<>(p.pending)) {
                    if (p.roles.get(i) == TownSize.Role.PLAZA) continue;
                    if (p.put(i, byDoor(p.boxes.get(i), rot[s], doors[s][0], doors[s][1]))) break;
                }
            }
        }
        // Le surplus dans la basse-cour au sud-est, hors du rempart, en rayons tournés vers la forteresse.
        int r = radius + 6 + depth / 2;
        for (int step = 0; step < 400 && !p.pending.isEmpty(); step++) {
            double a = Math.toRadians(15 + (step * 7) % 60);
            int ring = r + (step * 7 / 60) * (depth + 3);
            int x = (int) Math.round(Math.cos(a) * ring), z = (int) Math.round(Math.sin(a) * ring);
            for (Integer i : new ArrayList<>(p.pending)) if (p.put(i, new Spot(x, z, facingCenter(x, z)))) break;
        }
    }

    /** Un quartier : une rue est-ouest, les maisons de part et d'autre, du milieu vers les bouts. */
    private static void rue(Placer p, int length) {
        p.streetX(0, MAIN, -length, length);
        fill(p, List.of(new Frontage(true, 0, MAIN, -1, 0, length), new Frontage(true, 0, MAIN, 1, -1, -length),
                new Frontage(true, 0, MAIN, 1, 0, length), new Frontage(true, 0, MAIN, -1, -1, -length)), ANY);
    }

    /** Une placette à chaque croisement de rues : les décors en attente se posent aux coins, tournés vers le carrefour. */
    private static void corners(Placer p, java.util.Random rnd) {
        List<Rect> cs = p.corridors;
        for (int a = 0; a < cs.size(); a++) {
            for (int b = a + 1; b < cs.size(); b++) {
                Rect x = cs.get(a), y = cs.get(b);
                if (!x.overlaps(y, 0)) continue;
                int cx = (Math.max(x.x0(), y.x0()) + Math.min(x.x1(), y.x1())) / 2, cz = (Math.max(x.z0(), y.z0()) + Math.min(x.z1(), y.z1())) / 2;
                int reach = Math.max(x.x1() - x.x0(), x.z1() - x.z0()) < Math.max(y.x1() - y.x0(), y.z1() - y.z0())
                        ? Math.min(x.x1() - x.x0(), x.z1() - x.z0()) : Math.min(y.x1() - y.x0(), y.z1() - y.z0());
                for (int[] q : new int[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
                    for (Integer i : new ArrayList<>(p.pending)) {
                        TownSize.Role r = p.roles.get(i);
                        if (r != TownSize.Role.PLAZA && r != TownSize.Role.STATUE && r != TownSize.Role.LAMP && r != TownSize.Role.STALL
                                && r != TownSize.Role.DECOR) continue;
                        Box bx = p.boxes.get(i);
                        int off = reach / 2 + 2 + Math.max(bx.w(), bx.d()) / 2;
                        if (p.put(i, new Spot(cx + q[0] * off, cz + q[1] * off, facingCenter(q[0], q[1])))) break;
                    }
                }
            }
        }
    }

    /** Chaque élément en attente, sur la première rive de rue où il tient (en repartant du début de chaque rive). */
    private static void placeAlong(Placer p, List<Frontage> sides) {
        if (sides.isEmpty()) return;
        // Les rives sont prises dans un ordre mêlé : la ville ne se remplit pas rue après rue, d'un seul côté.
        java.util.Collections.shuffle(sides, new java.util.Random(sides.size() * 31L + p.taken.size()));
        // Chaque rive se remplit de proche en proche ; quand plus rien ne tient nulle part, on s'arrête.
        int misses = 0;
        for (Integer i : new ArrayList<>(p.pending)) {
            boolean placed = false;
            for (Frontage f : sides) {
                if (f.place(p, i, 1)) {
                    placed = true;
                    break;
                }
            }
            misses = placed ? 0 : misses + 1;
            if (misses > 24) return;
        }
    }

    /**
     * Une ruelle sur cette ligne, découpée en tronçons libres (là où aucun bâtiment ne la barre) d'au moins douze
     * cases ; chaque tronçon devient une rue et ses deux rives.
     */
    private static List<Frontage> freeLane(Placer p, boolean alongX, int line, int a, int b) {
        List<Frontage> out = new ArrayList<>();
        int start = -1;
        for (int t = a; t <= b + 1; t++) {
            boolean free = t <= b;
            if (free) {
                Rect cell = alongX ? new Rect(t, line - LANE, t, line + LANE) : new Rect(line - LANE, t, line + LANE, t);
                for (Rect r : p.taken) if (r.overlaps(cell, 0)) {
                    free = false;
                    break;
                }
            }
            if (free && start < 0) start = t;
            if (!free && start >= 0) {
                if (t - 1 - start >= 12) {
                    if (alongX) p.streetX(line, LANE, start, t - 1);
                    else p.streetZ(line, LANE, start, t - 1);
                    out.add(new Frontage(alongX, line, LANE, -1, start, t - 1));
                    out.add(new Frontage(alongX, line, LANE, 1, start, t - 1));
                }
                start = -1;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ rues, murs, emprise

    private static Layout finish(Placer p, Pattern pattern, boolean walled) {
        List<Spot> spots = new ArrayList<>();
        List<Rect> rects = new ArrayList<>();
        Rect box = null;
        for (int i = 0; i < p.spots.length; i++) {
            spots.add(p.spots[i]);
            Rect r = p.rectOf(i);
            if (r == null) continue;
            rects.add(r);
            box = box == null ? r : new Rect(Math.min(box.x0(), r.x0()), Math.min(box.z0(), r.z0()), Math.max(box.x1(), r.x1()), Math.max(box.z1(), r.z1()));
        }
        if (box == null) box = new Rect(-4, -4, 4, 4);
        Set<Long> streetCells = new LinkedHashSet<>(), wallCells = new LinkedHashSet<>();
        if (pattern == Pattern.TRELLEBORG) {
            int radius = 0;
            for (Rect c : p.corridors) radius = Math.max(radius, Math.max(Math.abs(c.x0()), Math.abs(c.x1())));
            for (Rect c : p.corridors) cells(streetCells, c, new Rect(-radius, -radius, radius, radius));
            if (walled) ring(wallCells, radius + 2, 3, MAIN + 2);
        } else {
            Rect inside = box.grow(3);
            for (Rect c : p.corridors) cells(streetCells, c, inside);
            if (walled) {
                Rect w = box.grow(5);
                square(wallCells, w, 2, MAIN + 2);
                // Les grandes rues vont jusqu'aux portes.
                for (int z = w.z0(); z <= w.z1(); z++) for (int x = -MAIN; x <= MAIN; x++) if (onAxisStreet(p, x, z)) add(streetCells, x, z);
                for (int x = w.x0(); x <= w.x1(); x++) for (int z = -MAIN; z <= MAIN; z++) if (onAxisStreet(p, x, z)) add(streetCells, x, z);
            }
        }
        List<int[]> streets = new ArrayList<>(), walls = new ArrayList<>();
        int m = 0;
        for (Rect r : rects) m = Math.max(m, Math.max(Math.max(Math.abs(r.x0()), Math.abs(r.x1())), Math.max(Math.abs(r.z0()), Math.abs(r.z1()))));
        for (long key : streetCells) {
            int x = (int) (key >> 32), z = (int) key;
            if (under(rects, x, z) || wallCells.contains(key)) continue;
            streets.add(new int[]{x, z});
            m = Math.max(m, Math.max(Math.abs(x), Math.abs(z)));
        }
        for (long key : wallCells) {
            int x = (int) (key >> 32), z = (int) key;
            if (under(rects, x, z)) continue;
            walls.add(new int[]{x, z});
            m = Math.max(m, Math.max(Math.abs(x), Math.abs(z)));
        }
        return new Layout(spots, streets, walls, 2 * (m + 2) + 1);
    }

    /** La case appartient-elle à une rue qui passe par l'axe (x = 0 ou z = 0) ? Ce sont elles qui mènent aux portes. */
    private static boolean onAxisStreet(Placer p, int x, int z) {
        for (Rect c : p.corridors) {
            boolean vertical = c.x0() <= 0 && 0 <= c.x1() && c.x1() - c.x0() <= 2 * AVENUE;
            boolean horizontal = c.z0() <= 0 && 0 <= c.z1() && c.z1() - c.z0() <= 2 * AVENUE;
            if (vertical && x >= c.x0() && x <= c.x1()) return true;
            if (horizontal && z >= c.z0() && z <= c.z1()) return true;
        }
        return false;
    }

    private static boolean under(List<Rect> rects, int x, int z) {
        for (Rect r : rects) if (r.contains(x, z)) return true;
        return false;
    }

    private static void cells(Set<Long> out, Rect c, Rect clip) {
        for (int x = Math.max(c.x0(), clip.x0()); x <= Math.min(c.x1(), clip.x1()); x++) {
            for (int z = Math.max(c.z0(), clip.z0()); z <= Math.min(c.z1(), clip.z1()); z++) add(out, x, z);
        }
    }

    /** Une enceinte carrée de {@code thick} blocs, percée d'une porte au milieu de chaque côté. */
    private static void square(Set<Long> out, Rect w, int thick, int gate) {
        for (int t = 0; t < thick; t++) {
            for (int x = w.x0() + t; x <= w.x1() - t; x++) {
                if (Math.abs(x) > gate) {
                    add(out, x, w.z0() + t);
                    add(out, x, w.z1() - t);
                }
            }
            for (int z = w.z0() + t; z <= w.z1() - t; z++) {
                if (Math.abs(z) > gate) {
                    add(out, w.x0() + t, z);
                    add(out, w.x1() - t, z);
                }
            }
        }
    }

    /** Un rempart circulaire de {@code thick} blocs, percé de quatre portes aux points cardinaux. */
    private static void ring(Set<Long> out, int radius, int thick, int gate) {
        for (int x = -radius - thick; x <= radius + thick; x++) {
            for (int z = -radius - thick; z <= radius + thick; z++) {
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d < radius || d >= radius + thick) continue;
                if (Math.abs(x) <= gate || Math.abs(z) <= gate) continue;
                add(out, x, z);
            }
        }
    }

    private static void add(Set<Long> cells, int x, int z) {
        cells.add(((long) x << 32) | (z & 0xFFFFFFFFL));
    }
}
