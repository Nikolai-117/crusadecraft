package fr.crusadecraft.core.world;

import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.StatFormula;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.character.TraitGenerator;
import fr.crusadecraft.core.logic.Heraldry;
import fr.crusadecraft.core.realm.TitleRank;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Le plan des royaumes IA autour d'un point : où sont leurs capitales et leurs provinces, qui les gouverne,
 * à quels royaumes de jure ils appartiennent. Aucun accès au monde : les villages connus et un test de terre
 * ferme arrivent en paramètres, ce qui rend le plan reproductible et testable.
 */
public final class WorldPlanner {

    /** Un village repéré, candidat pour un siège. */
    public record Candidate(int x, int y, int z, AiProvince.Anchor anchor) {}

    /** Répond à « peut-on poser un siège ici ? » ; renvoie la hauteur du sol, ou {@code null} (eau…). */
    @FunctionalInterface
    public interface LandProbe {
        Integer surface(int x, int z);
    }

    public record Settings(int realmCount, int minRing, int maxRing, int capitalSpacing, int provinceRadius,
                           int seatSpacing, int avoidRadius, int baronies) {
        public static Settings defaults() {
            return new Settings(6, 600, 2500, 700, 450, 180, 320, 4);
        }

        /** Les seigneuries indépendantes se tiennent plus près de la capitale que les grands royaumes. */
        int baronyMin() { return Math.max(avoidRadius + 20, minRing / 2); }
        int baronyMax() { return Math.max(baronyMin() + 300, minRing + 500); }
    }

    public record Plan(List<AiRealm> realms, List<WorldState.DejureKingdom> dejure) {}

    private final Settings settings;
    private final Random random;
    private final NameGenerator names;
    private final LandProbe land;
    private final ResourceLocation dimension;
    /** Premier identifiant de province : après ceux des royaumes posés à la main, qui restent. */
    private int firstProvinceId;

    public WorldPlanner(Settings settings, long seed, LandProbe land, ResourceLocation dimension) {
        this.settings = settings;
        this.random = new Random(seed);
        this.names = new NameGenerator(seed ^ 0x5DEECE66DL);
        this.land = land;
        this.dimension = dimension;
    }

    public record Point(int x, int y, int z, AiProvince.Anchor anchor) {
        double dist(double ox, double oz) {
            return Math.hypot(x - ox, z - oz);
        }
    }

    public Plan plan(int cx, int cz, List<Candidate> villages, List<int[]> avoid, List<Integer> takenColors, long today) {
        List<Point> pool = new ArrayList<>();
        for (Candidate c : villages) pool.add(new Point(c.x(), c.y(), c.z(), c.anchor()));
        Collections.shuffle(pool, random);
        // Les villages déjà générés passent avant les villages seulement prévus.
        pool.sort(Comparator.comparingInt(p -> p.anchor() == AiProvince.Anchor.VILLAGE ? 0 : 1));

        List<Point> seats = new ArrayList<>();
        List<TitleRank> ranks = ranks(settings.realmCount());
        List<Point> capitals = new ArrayList<>();
        for (int i = 0; i < ranks.size(); i++) {
            Point capital = pickCapital(pool, cx, cz, avoid, capitals, seats, i, ranks.size());
            if (capital == null) break;
            capitals.add(capital);
            seats.add(capital);
        }

        // Royaumes de jure : un par secteur autour du centre, un pour trois royaumes environ.
        int dejureCount = Math.max(1, Math.round(capitals.size() / 3f));
        List<Integer> colors = colors(takenColors);
        List<WorldState.DejureKingdom> dejure = new ArrayList<>();
        double sectorOffset = random.nextDouble() * Math.PI * 2;
        int[][] sums = new int[dejureCount][3];
        int[] sectorOf = new int[capitals.size()];
        for (int i = 0; i < capitals.size(); i++) {
            Point c = capitals.get(i);
            double angle = Math.floorMod((long) Math.round(Math.toDegrees(Math.atan2(c.z() - cz, c.x() - cx) - sectorOffset)), 360L);
            int sector = (int) Math.min(dejureCount - 1, angle * dejureCount / 360);
            sectorOf[i] = sector;
            sums[sector][0] += c.x();
            sums[sector][1] += c.z();
            sums[sector][2]++;
        }
        for (int k = 0; k < dejureCount; k++) {
            int n = Math.max(1, sums[k][2]);
            int color = blend(colors.get(colors.size() - 1 - k % colors.size()), 0xE8D9B0);
            dejure.add(new WorldState.DejureKingdom(names.place(), color, sums[k][0] / n, sums[k][1] / n));
        }

        List<AiRealm> realms = new ArrayList<>();
        int provinceId = firstProvinceId;
        for (int i = 0; i < capitals.size(); i++) {
            TitleRank rank = ranks.get(i);
            Point capital = capitals.get(i);
            int wanted = provinceCount(rank);
            List<Point> mine = new ArrayList<>();
            mine.add(capital);
            // D'abord les villages proches, puis des points de terre ferme autour de la capitale.
            pool.sort(Comparator.comparingDouble(p -> p.dist(capital.x(), capital.z())));
            for (Point p : new ArrayList<>(pool)) {
                if (mine.size() >= wanted) break;
                if (p.dist(capital.x(), capital.z()) > settings.provinceRadius()) break;
                if (!farFrom(p, seats, settings.seatSpacing()) || !farFromColonies(p, avoid)) continue;
                mine.add(p);
                seats.add(p);
                pool.remove(p);
            }
            for (int attempt = 0; attempt < 40 && mine.size() < wanted; attempt++) {
                double a = random.nextDouble() * Math.PI * 2;
                double d = settings.seatSpacing() + random.nextDouble() * (settings.provinceRadius() - settings.seatSpacing());
                Point p = probe((int) (capital.x() + Math.cos(a) * d), (int) (capital.z() + Math.sin(a) * d));
                if (p == null || !farFrom(p, seats, settings.seatSpacing()) || !farFromColonies(p, avoid)) continue;
                mine.add(p);
                seats.add(p);
            }

            String place = names.place();
            AiPersonality personality = AiPersonality.values()[random.nextInt(AiPersonality.values().length)];
            AiCharacter ruler = character(30 + random.nextInt(31), true);
            AiRealm realm = new AiRealm(new UUID(random.nextLong(), random.nextLong()), place, rank,
                    colors.get(i % colors.size()), names.place(), personality, ruler, today);
            for (Post post : Post.values()) {
                if (post.isCouncil()) realm.council().put(post, character(22 + random.nextInt(40), false));
            }
            for (int j = 0; j < mine.size(); j++) {
                Point p = mine.get(j);
                boolean isCapital = j == 0;
                realm.provinces().add(new AiProvince(provinceId++, isCapital ? place : names.place(), dimension,
                        new BlockPos(p.x(), p.y(), p.z()), p.anchor(), isCapital, sectorOf[i],
                        isCapital ? 25 + random.nextInt(40) : 8 + random.nextInt(25), 10 + random.nextInt(30)));
            }
            realm.setGold(100L * (rank.ordinal() + 1) + random.nextInt(200));
            realm.setPrestige(50 * (rank.ordinal() + 1) + random.nextInt(100));
            realm.setPiety(personality == AiPersonality.FANATIQUE ? 150 + random.nextInt(100) : random.nextInt(100));
            realms.add(realm);
        }
        List<int[]> taken = new ArrayList<>();
        for (Point p : seats) taken.add(new int[]{p.x(), p.z()});
        realms.addAll(baronies(cx, cz, pool, avoid, taken, takenColors, provinceId, settings.baronies(), dejure, today));
        return new Plan(realms, dejure);
    }

    /**
     * Seigneuries indépendantes : une seule province, un village de préférence, entre la colonie du joueur et
     * les grands royaumes. Ce sont les premières qu'un baron peut s'attacher.
     */
    public List<AiRealm> baronies(int cx, int cz, List<Point> pool, List<int[]> avoid, List<int[]> takenSeats,
                                  List<Integer> takenColors, int firstProvinceId, int count,
                                  List<WorldState.DejureKingdom> dejure, long today) {
        List<AiRealm> out = new ArrayList<>();
        List<Point> seats = new ArrayList<>();
        for (int[] s : takenSeats) seats.add(new Point(s[0], 0, s[1], AiProvince.Anchor.LAND));
        List<Integer> colors = colors(takenColors);
        int provinceId = firstProvinceId;
        for (int i = 0; i < count; i++) {
            Point seat = null;
            for (Point p : new ArrayList<>(pool)) {
                double d = p.dist(cx, cz);
                if (d < settings.baronyMin() || d > settings.baronyMax()) continue;
                if (!farFrom(p, seats, settings.seatSpacing()) || !farFromColonies(p, avoid)) continue;
                seat = p;
                pool.remove(p);
                break;
            }
            for (int attempt = 0; seat == null && attempt < 60; attempt++) {
                double a = random.nextDouble() * Math.PI * 2;
                double d = settings.baronyMin() + random.nextDouble() * (settings.baronyMax() - settings.baronyMin());
                Point p = probe((int) (cx + Math.cos(a) * d), (int) (cz + Math.sin(a) * d));
                if (p != null && farFrom(p, seats, settings.seatSpacing()) && farFromColonies(p, avoid)) seat = p;
            }
            if (seat == null) break;
            seats.add(seat);
            String place = names.place();
            AiPersonality personality = AiPersonality.values()[random.nextInt(AiPersonality.values().length)];
            AiRealm realm = new AiRealm(new UUID(random.nextLong(), random.nextLong()), place, TitleRank.BARONNIE,
                    colors.get((i + 3) % colors.size()), names.place(), personality, character(28 + random.nextInt(35), true), today);
            for (Post post : Post.values()) {
                if (post.isCouncil()) realm.council().put(post, character(22 + random.nextInt(40), false));
            }
            realm.provinces().add(new AiProvince(provinceId++, place, dimension, new BlockPos(seat.x(), seat.y(), seat.z()),
                    seat.anchor(), true, nearestDejure(dejure, seat.x(), seat.z()), 10 + random.nextInt(20), 5 + random.nextInt(20)));
            realm.setGold(40 + random.nextInt(80));
            realm.setPrestige(10 + random.nextInt(40));
            realm.setPiety(random.nextInt(60));
            out.add(realm);
        }
        return out;
    }

    private static int nearestDejure(List<WorldState.DejureKingdom> dejure, int x, int z) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < dejure.size(); i++) {
            double d = Math.hypot(dejure.get(i).centerX() - x, dejure.get(i).centerZ() - z);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Les villages candidats, sous la forme qu'attend {@link #baronies}. */
    public static List<Point> points(List<Candidate> villages) {
        List<Point> out = new ArrayList<>();
        for (Candidate c : villages) out.add(new Point(c.x(), c.y(), c.z(), c.anchor()));
        return out;
    }

    /** Un royaume, deux ou trois duchés, et des comtés pour le reste. */
    static List<TitleRank> ranks(int count) {
        List<TitleRank> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i == 0 && count >= 4) out.add(TitleRank.ROYAUME);
            else if (out.stream().filter(r -> r == TitleRank.DUCHE).count() < Math.max(1, count / 3)) out.add(TitleRank.DUCHE);
            else out.add(TitleRank.COMTE);
        }
        return out;
    }

    private int provinceCount(TitleRank rank) {
        return switch (rank) {
            case ROYAUME, EMPIRE -> 3 + random.nextInt(3);
            case DUCHE -> 2 + random.nextInt(2);
            default -> 1 + random.nextInt(2);
        };
    }

    private Point pickCapital(List<Point> pool, int cx, int cz, List<int[]> avoid, List<Point> capitals, List<Point> seats,
                              int index, int total) {
        for (Point p : new ArrayList<>(pool)) {
            double d = p.dist(cx, cz);
            if (d < settings.minRing() || d > settings.maxRing()) continue;
            if (!farFrom(p, capitals, settings.capitalSpacing()) || !farFromColonies(p, avoid)) continue;
            pool.remove(p);
            return p;
        }
        // Pas de village qui convienne : un point de terre ferme, réparti autour du centre.
        double base = Math.PI * 2 * index / Math.max(1, total);
        for (int attempt = 0; attempt < 60; attempt++) {
            double a = base + (random.nextDouble() - 0.5) * Math.PI * 2 / Math.max(1, total);
            double d = settings.minRing() + random.nextDouble() * (settings.maxRing() - settings.minRing());
            Point p = probe((int) (cx + Math.cos(a) * d), (int) (cz + Math.sin(a) * d));
            if (p != null && farFrom(p, capitals, settings.capitalSpacing()) && farFromColonies(p, avoid)) return p;
        }
        return null;
    }

    private Point probe(int x, int z) {
        Integer y = land.surface(x, z);
        return y == null ? null : new Point(x, y, z, AiProvince.Anchor.LAND);
    }

    private static boolean farFrom(Point p, List<Point> others, int spacing) {
        for (Point o : others) if (o.dist(p.x(), p.z()) < spacing) return false;
        return true;
    }

    private boolean farFromColonies(Point p, List<int[]> avoid) {
        for (int[] c : avoid) if (p.dist(c[0], c[1]) < settings.avoidRadius()) return false;
        return true;
    }

    /** Émaux disponibles, sans ceux des royaumes des joueurs ni le sable (illisible sur la carte). */
    private List<Integer> colors(List<Integer> taken) {
        List<Integer> out = new ArrayList<>();
        for (Heraldry h : Heraldry.values()) {
            if (h == Heraldry.SABLE || h == Heraldry.ARGENT || taken.contains(h.rgb())) continue;
            out.add(h.rgb());
        }
        Collections.shuffle(out, random);
        if (out.isEmpty()) out.add(Heraldry.ACIER.rgb());
        return out;
    }

    public WorldPlanner startingAt(int firstProvinceId) {
        this.firstProvinceId = firstProvinceId;
        return this;
    }

    /**
     * Un royaume posé à la main dans l'éditeur de la carte : nom, rang, couleur, foi et caractère choisis par le
     * joueur ; souverain (nommé s'il le souhaite), conseil, trésor et capitale tirés comme pour les autres.
     */
    public AiRealm handMade(String place, TitleRank rank, int color, AiFaith faith, AiPersonality personality, String rulerName,
                            int provinceId, BlockPos seat, int dejure, int population, long today) {
        AiCharacter ruler = character(30 + random.nextInt(31), true);
        if (rulerName != null && !rulerName.isBlank()) {
            ruler = new AiCharacter(rulerName, ruler.female(), ruler.age(), ruler.stats(), ruler.traits());
        }
        AiRealm realm = new AiRealm(new UUID(random.nextLong(), random.nextLong()), place, rank, color, names.place(),
                personality, ruler, today);
        realm.setFaith(faith);
        realm.setHandMade(true);
        for (Post post : Post.values()) {
            if (post.isCouncil()) realm.council().put(post, character(22 + random.nextInt(40), false));
        }
        realm.provinces().add(new AiProvince(provinceId, place, dimension, seat, AiProvince.Anchor.LAND, true, dejure,
                population, 10 + random.nextInt(30)));
        realm.setGold(100L * (rank.ordinal() + 1) + random.nextInt(200));
        realm.setPrestige(50 * (rank.ordinal() + 1) + random.nextInt(100));
        realm.setPiety(personality == AiPersonality.FANATIQUE ? 150 + random.nextInt(100) : random.nextInt(100));
        return realm;
    }

    /** Mélange à moitié avec le parchemin : les royaumes de jure ont des teintes passées. */
    static int blend(int rgb, int with) {
        int r = (((rgb >> 16) & 0xFF) + ((with >> 16) & 0xFF)) / 2;
        int g = (((rgb >> 8) & 0xFF) + ((with >> 8) & 0xFF)) / 2;
        int b = ((rgb & 0xFF) + (with & 0xFF)) / 2;
        return (r << 16) | (g << 8) | b;
    }

    /** Un personnage : compétences tirées au hasard, aptitudes par la formule par défaut, trois traits. */
    private AiCharacter character(int age, boolean ruler) {
        boolean female = random.nextInt(4) == 0;
        int[] skills = new int[11];
        for (int i = 0; i < skills.length; i++) skills[i] = 4 + random.nextInt(ruler ? 34 : 28);
        int[] stats = StatFormula.defaults().compute(skills);
        List<Trait> traits = TraitGenerator.roll(random.nextLong(), skills, 3);
        for (Trait t : traits) {
            for (int s = 0; s < stats.length; s++) stats[s] = Math.max(0, stats[s] + t.statModifier(fr.crusadecraft.core.character.Stat.values()[s]));
        }
        return new AiCharacter(names.personName(female), female, age, stats, traits);
    }
}
