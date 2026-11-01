package fr.crusadecraft.core.build.plan;

import fr.crusadecraft.core.build.Blueprints;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Les enceintes des villes, faites seulement des pièces de muraille des packs (P36, demandé par Romain) : les grandes
 * murailles de chaque thème pour les villes et les capitales, une palissade pour les bourgs. Les quatre angles aux
 * coins, des segments enchaînés le long de chaque côté, une porte au milieu (là où passe la grande rue), une tour tous
 * les quatre segments. Chaque pièce est tournée d'après ses raccords, mesurés sur le plan lui-même, sa face extérieure
 * (la plus pleine) vers le dehors.
 */
public final class WallSets {

    /** Les pièces voulues pour un style, par chemin dans son pack (sans le niveau ni l'extension), par ordre de préférence. */
    record Spec(List<String> segments, List<String> fillers, List<String> corners, List<String> gates, List<String> towers) {}

    /** Une pièce chargée : sa référence, sa taille (non tournée), ses raccords, sa face pleine (null : inconnus). */
    public record Part(String ref, int sx, int sz, Set<Direction> attach, Direction outer) {}

    /** Le jeu de pièces d'une enceinte. Les angles, les bouche-trous, les portes et les tours peuvent manquer. */
    public record Kit(Part segment, Part filler, Part corner, Part gate, Part tower) {
        int thickness() {
            return Math.min(segment.sx(), segment.sz());
        }
    }

    /** L'enceinte posée : ses pièces, et le milieu de chaque porte avec la direction du dehors. */
    record Ring(List<TownPlan.Piece> pieces, List<int[]> gates, List<TownLayout.Rect> gateRects, List<TownLayout.Rect> rects) {}

    private WallSets() {}

    static Spec spec(NationStyle style, TownSize.Walls kind) {
        boolean big = kind == TownSize.Walls.BIG;
        return switch (style) {
            case EUROPEENNE -> new Spec(List.of("walls/segment/segment"), List.of(), List.of("walls/corner/corner"), List.of("walls/gate/gate"),
                    List.of("walls/tower/tower"));
            case CHINOISE -> big
                    ? new Spec(List.of("walls/big_walls/long"), List.of("walls/big_walls/short"), List.of("walls/big_walls/corner_tower", "walls/big_walls/corner"),
                    List.of("walls/big_walls/gatehouse"), List.of("walls/big_walls/straight_tower"))
                    : new Spec(List.of("walls/fences/fence_a", "walls/fences/fancy_a"), List.of("walls/fences/fence_short"),
                    List.of("walls/fences/fence_plug_b", "walls/fences/fence_plug_a"), List.of("walls/fences/fence_gate"), List.of());
            case JAPONAISE -> big
                    ? new Spec(List.of("walls/city_flat/straight/straight"), List.of("walls/city_flat/straight/straightshort"),
                    List.of("walls/city_flat/corner/corneroutround", "walls/city_flat/corner/cornerinround"),
                    List.of("walls/city_gate/straightavearchgateguards", "walls/city_gate/straightavewoodgateguards"),
                    List.of("walls/city_walltower/biginwalltower", "walls/city_walltower/inwallallroundtower"))
                    : new Spec(List.of("walls/palisades/long"), List.of("walls/palisades/short"), List.of("walls/palisades/outsideturn"), List.of(),
                    List.of("walls/palisades/tower"));
            case EGYPTIENNE -> big
                    ? new Spec(List.of("walls/flat/straight"), List.of("walls/flat/straightshort"), List.of("walls/flat/cornerinrev", "walls/flat/cornerout"),
                    List.of("walls/flat/gatebig", "walls/flat/gatesmall"), List.of("walls/tower/midwalltall", "walls/tower/midwall"))
                    : new Spec(List.of("walls/flat/straightshort"), List.of("walls/flat/shim"), List.of("walls/flat/cornerinrev"),
                    List.of("walls/flat/gatesmall"), List.of());
            case ROMAINE -> big
                    ? new Spec(List.of("walls/wall_main/wallmain"), List.of(), List.of("decorations/dummytower/big/corner"), List.of("walls/wall_main/gate"),
                    List.of("decorations/dummytower/big/towerbig"))
                    : new Spec(List.of("walls/palisades/long"), List.of("walls/palisades/short"), List.of("walls/palisades/outsideturn"), List.of(),
                    List.of("walls/palisades/tower"));
            case NORDIQUE -> big
                    ? new Spec(List.of("walls/fancy/fancy_segment_large"), List.of("walls/fancy/fancy_segment_small"),
                    List.of("walls/fancy/fancy_square_corner", "walls/fancy/fancy_round_corner"), List.of("walls/fancy/fancy_gate"),
                    List.of("walls/fancy/fancy_round_tower"))
                    : new Spec(List.of("walls/palisade/palisade_wall"), List.of(), List.of("walls/palisade/palisade_corner"),
                    List.of("walls/palisade/palisade_gate"), List.of("walls/palisade/palisade_platform"));
        };
    }

    // ------------------------------------------------------------------ charger les pièces

    /** Le plan d'un chemin sans niveau : le niveau le plus haut permis (grande muraille) ou le plus bas (palissade). */
    static Optional<String> resolve(List<String> entries, String base, boolean highest, int maxLevel) {
        String best = null;
        int bestLevel = highest ? -1 : Integer.MAX_VALUE;
        for (String e : entries) {
            if (!e.endsWith(".blueprint")) continue;
            String stem = e.substring(0, e.length() - ".blueprint".length());
            if (!stem.startsWith(base)) continue;
            String rest = stem.substring(base.length());
            if (!rest.isEmpty() && !rest.chars().allMatch(Character::isDigit)) continue;
            int level = rest.isEmpty() ? 0 : Integer.parseInt(rest);
            if (level > maxLevel) continue;
            if (highest ? level > bestLevel : level < bestLevel) {
                best = e;
                bestLevel = level;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Le jeu de pièces d'un style dans son pack ; vide s'il n'a pas au moins un segment. */
    public static Optional<Kit> kit(NationStyle style, TownSize.Walls kind, String pack, List<String> entries) {
        if (kind == TownSize.Walls.NONE) return Optional.empty();
        Spec spec = spec(style, kind);
        boolean big = kind == TownSize.Walls.BIG;
        // P37 : une pièce de muraille marquée d'une étoile, dans le pack du style, passe avant celles de la fiche.
        Part segment = favorite(pack, big, "segment", true);
        if (segment == null) segment = first(spec.segments(), pack, entries, big, true);
        if (segment == null) return Optional.empty();
        Part corner = favorite(pack, big, "angle", false), gate = favorite(pack, big, "porte", true), tower = favorite(pack, big, "tour", false);
        return Optional.of(new Kit(segment, first(spec.fillers(), pack, entries, big, true),
                corner != null ? corner : first(spec.corners(), pack, entries, big, false),
                gate != null ? gate : first(spec.gates(), pack, entries, big, true),
                tower != null ? tower : first(spec.towers(), pack, entries, big, false)));
    }

    /** La pièce favorite de ce rôle (segment, angle, porte, tour) dans ce pack : palissade pour un bourg, mur sinon. */
    private static Part favorite(String pack, boolean big, String role, boolean linear) {
        for (String r : PlanFavorites.current()) {
            Optional<PlanRef> ref = PlanRef.decode(r);
            if (ref.isEmpty() || ref.get().source() != PlanRef.Source.PACK || !ref.get().pack().equals(pack)) continue;
            String path = ref.get().path();
            if (PlanKinds.kind(path) != PlanKinds.MURAILLE && !path.contains("dummytower")) continue;
            boolean palisade = path.toLowerCase(java.util.Locale.ROOT).contains("palis") || path.contains("fence");
            if (palisade == big) continue;
            String pr = PlanKinds.role(path);
            boolean match = switch (role) {
                case "segment" -> pr.equals("segment") || pr.equals("palissade") || pr.equals("cloture");
                case "angle" -> pr.equals("angle") || pr.equals("angle_rentrant");
                default -> pr.equals(role);
            };
            if (!match) continue;
            Optional<Plan> plan = PlanLibrary.load(ref.get(), false);
            if (plan.isPresent() && !plan.get().placements().isEmpty()) return part(r, plan.get(), linear);
        }
        return null;
    }

    private static Part first(List<String> bases, String pack, List<String> entries, boolean highest, boolean linear) {
        for (String base : bases) {
            Optional<String> path = resolve(entries, base, highest, 5);
            if (path.isEmpty()) continue;
            PlanRef ref = new PlanRef(PlanRef.Source.PACK, pack, path.get());
            Optional<Plan> plan = PlanLibrary.load(ref, false);
            if (plan.isEmpty() || plan.get().placements().isEmpty()) continue;
            return part(ref.encode(), plan.get(), linear);
        }
        return null;
    }

    /** Les raccords d'une pièce et sa face pleine, mesurés sur ses bords au-dessus du sol. */
    static Part part(String ref, Plan plan, boolean linear) {
        int sx = plan.sizeX(), sz = plan.sizeZ();
        int minX = -(sx / 2), maxX = sx - 1 - sx / 2, minZ = -(sz / 2), maxZ = sz - 1 - sz / 2;
        Set<Long> solid = new HashSet<>();
        int top = 1;
        for (Blueprints.Placement p : plan.placements()) {
            if (p.foundation() || p.state().isAir() || p.y() < 1) continue;
            solid.add(key(p.x(), p.y(), p.z()));
            top = Math.max(top, p.y());
        }
        double[] f = new double[4];
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (int i = 0; i < 4; i++) {
            int full = 0, all = 0;
            for (int y = 1; y <= top; y++) {
                if (i < 2) {
                    int z = i == 0 ? minZ : maxZ;
                    for (int x = minX; x <= maxX; x++, all++) if (solid.contains(key(x, y, z))) full++;
                } else {
                    int x = i == 2 ? minX : maxX;
                    for (int z = minZ; z <= maxZ; z++, all++) if (solid.contains(key(x, y, z))) full++;
                }
            }
            f[i] = all == 0 ? 0 : full / (double) all;
        }
        Set<Direction> attach = EnumSet.noneOf(Direction.class);
        Direction outer = null;
        if (linear && sx != sz) {
            // Un segment se raccorde par ses deux petits côtés ; sa face pleine est le plus plein des deux grands.
            if (sx > sz) {
                attach.add(Direction.WEST);
                attach.add(Direction.EAST);
                outer = f[0] >= f[1] ? Direction.NORTH : Direction.SOUTH;
            } else {
                attach.add(Direction.NORTH);
                attach.add(Direction.SOUTH);
                outer = f[2] >= f[3] ? Direction.WEST : Direction.EAST;
            }
        } else {
            for (int i = 0; i < 4; i++) if (f[i] >= 0.18) attach.add(dirs[i]);
            if (attach.size() == 4 || attach.isEmpty()) attach = null;
        }
        return new Part(ref, sx, sz, attach, outer);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 20) | (z + 4096);
    }

    // ------------------------------------------------------------------ assembler

    static Set<Direction> rotate(Set<Direction> set, int r) {
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        for (Direction d : set) {
            Direction x = d;
            for (int i = 0; i < r; i++) x = x.getClockWise();
            out.add(x);
        }
        return out;
    }

    /** Le quart de tour qui donne à la pièce ces raccords et cette face pleine (le mieux possible). */
    static int rotationFor(Part p, Set<Direction> want, Direction outer) {
        int fallback = -1;
        for (int r = 0; r < 4; r++) {
            if (p.attach() != null && !rotate(p.attach(), r).equals(want)) continue;
            if (fallback < 0) fallback = r;
            if (outer == null || p.outer() == null) return r;
            Direction o = p.outer();
            for (int i = 0; i < r; i++) o = o.getClockWise();
            if (o == outer) return r;
        }
        return Math.max(0, fallback);
    }

    private static TownLayout.Box box(Part p) {
        return new TownLayout.Box(p.sx(), p.sz());
    }

    /**
     * L'enceinte autour du rectangle (x0, z0)–(x1, z1), ligne des murs. Les portes s'ouvrent au milieu de chaque côté, sur
     * x = 0 (nord, sud) et z = 0 (ouest, est) quand l'axe le traverse.
     */
    static Ring assemble(Kit kit, int x0, int z0, int x1, int z1) {
        List<TownPlan.Piece> out = new ArrayList<>();
        List<int[]> gates = new ArrayList<>();
        List<TownLayout.Rect> gateRects = new ArrayList<>(), rects = new ArrayList<>();
        // Les angles d'abord : nord-ouest, nord-est, sud-est, sud-ouest.
        int[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        List<Set<Direction>> want = List.of(EnumSet.of(Direction.SOUTH, Direction.EAST), EnumSet.of(Direction.SOUTH, Direction.WEST),
                EnumSet.of(Direction.NORTH, Direction.WEST), EnumSet.of(Direction.NORTH, Direction.EAST));
        TownLayout.Rect[] cornerRects = new TownLayout.Rect[4];
        Part corner = kit.corner() != null ? kit.corner() : kit.tower();
        for (int i = 0; i < 4; i++) {
            if (corner == null) {
                cornerRects[i] = new TownLayout.Rect(corners[i][0], corners[i][1], corners[i][0], corners[i][1]);
                continue;
            }
            int r = corner.attach() == null ? 0 : rotationFor(corner, want.get(i), null);
            TownLayout.Spot s = new TownLayout.Spot(corners[i][0], corners[i][1], Rotation.values()[r]);
            cornerRects[i] = TownLayout.rect(box(corner), s);
            add(out, rects, corner, s);
        }
        // Les quatre côtés : (côté, ligne, début, fin, sens de la course, dehors).
        side(kit, out, rects, gates, gateRects, true, z0, cornerRects[0].x1() + 1, cornerRects[1].x0() - 1, Direction.NORTH);
        side(kit, out, rects, gates, gateRects, true, z1, cornerRects[3].x1() + 1, cornerRects[2].x0() - 1, Direction.SOUTH);
        side(kit, out, rects, gates, gateRects, false, x0, cornerRects[0].z1() + 1, cornerRects[3].z0() - 1, Direction.WEST);
        side(kit, out, rects, gates, gateRects, false, x1, cornerRects[1].z1() + 1, cornerRects[2].z0() - 1, Direction.EAST);
        return new Ring(out, gates, gateRects, rects);
    }

    private static void add(List<TownPlan.Piece> out, List<TownLayout.Rect> rects, Part p, TownLayout.Spot s) {
        out.add(new TownPlan.Piece(p.ref(), s.x(), s.z(), s.rotation().ordinal(), 0));
        rects.add(TownLayout.rect(box(p), s));
    }

    /** La place d'une pièce dont le bord commence en {@code start} sur la course, centrée sur la ligne des murs. */
    private static TownLayout.Spot spotAt(Part p, int r, boolean alongX, int line, int start) {
        TownLayout.Rect rr = TownLayout.rect(box(p), new TownLayout.Spot(0, 0, Rotation.values()[r]));
        if (alongX) return new TownLayout.Spot(start - rr.x0(), line - (rr.z0() + rr.z1()) / 2, Rotation.values()[r]);
        return new TownLayout.Spot(line - (rr.x0() + rr.x1()) / 2, start - rr.z0(), Rotation.values()[r]);
    }

    private static int length(Part p, int r, boolean alongX) {
        boolean turned = r % 2 == 1;
        return alongX ? (turned ? p.sz() : p.sx()) : (turned ? p.sx() : p.sz());
    }

    private static void side(Kit kit, List<TownPlan.Piece> out, List<TownLayout.Rect> rects, List<int[]> gates, List<TownLayout.Rect> gateRects,
                             boolean alongX, int line, int a, int b, Direction outside) {
        if (b < a) return;
        Set<Direction> run = alongX ? EnumSet.of(Direction.EAST, Direction.WEST) : EnumSet.of(Direction.NORTH, Direction.SOUTH);
        int sr = rotationFor(kit.segment(), run, outside);
        int segLen = length(kit.segment(), sr, alongX);
        if (kit.gate() != null) {
            int gr = rotationFor(kit.gate(), run, outside);
            int gLen = length(kit.gate(), gr, alongX);
            int center = a < 0 && 0 < b ? 0 : (a + b) / 2;
            int ga = Math.max(a, Math.min(b - gLen + 1, center - gLen / 2));
            if (b - a + 1 >= gLen + 2) {
                TownLayout.Spot s = spotAt(kit.gate(), gr, alongX, line, ga);
                add(out, rects, kit.gate(), s);
                gateRects.add(rects.get(rects.size() - 1));
                int mid = ga + gLen / 2;
                gates.add(alongX ? new int[]{mid, line, outside.ordinal()} : new int[]{line, mid, outside.ordinal()});
                run(kit, out, rects, alongX, line, a, ga - 1, sr, segLen, outside);
                run(kit, out, rects, alongX, line, ga + gLen, b, sr, segLen, outside);
                return;
            }
        }
        run(kit, out, rects, alongX, line, a, b, sr, segLen, outside);
    }

    /** Des segments bout à bout de {@code a} à {@code b} ; une tour tous les quatre ; le reste comblé ou recouvert. */
    private static void run(Kit kit, List<TownPlan.Piece> out, List<TownLayout.Rect> rects, boolean alongX, int line, int a, int b, int sr, int segLen,
                            Direction outside) {
        if (b < a) return;
        int pos = a, count = 0;
        while (b - pos + 1 >= segLen) {
            add(out, rects, kit.segment(), spotAt(kit.segment(), sr, alongX, line, pos));
            pos += segLen;
            count++;
            if (kit.tower() != null && count % 4 == 0 && b - pos + 1 >= segLen) {
                Part t = kit.tower();
                int tr = t.attach() == null ? 0 : rotationFor(t, alongX ? EnumSet.of(Direction.EAST, Direction.WEST)
                        : EnumSet.of(Direction.NORTH, Direction.SOUTH), outside);
                int tLen = length(t, tr, alongX);
                add(out, rects, t, spotAt(t, tr, alongX, line, pos - tLen / 2));
            }
        }
        if (pos > b) return;
        Part filler = kit.filler();
        if (filler != null) {
            Set<Direction> runDirs = alongX ? EnumSet.of(Direction.EAST, Direction.WEST) : EnumSet.of(Direction.NORTH, Direction.SOUTH);
            int fr = rotationFor(filler, runDirs, outside);
            int fLen = length(filler, fr, alongX);
            while (fLen > 0 && b - pos + 1 >= fLen) {
                add(out, rects, filler, spotAt(filler, fr, alongX, line, pos));
                pos += fLen;
            }
        }
        // Le bout qui reste : un dernier segment recouvre le précédent jusqu'au coin.
        if (pos <= b) add(out, rects, kit.segment(), spotAt(kit.segment(), sr, alongX, line, Math.max(a, b - segLen + 1)));
    }
}
