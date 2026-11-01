package fr.crusadecraft.core.diplomacy;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Le commerce (P25) : chaque traité de commerce en cours trace une route entre les deux capitales. Elle se voit sur la
 * carte du mod et sur Dynmap, et plus elle est longue, plus elle rapporte : les marchands font le trajet pour ce
 * qu'on ne trouve pas chez soi.
 */
public final class TradeService {
    /** Une pièce de plus par jour pour tant de blocs de route. */
    public static final int BLOCKS_PER_COIN = 400;
    /** Au plus tant de pièces de plus, quelle que soit la distance. */
    public static final int MAX_BONUS = 10;
    /** P29 : des marchandises différentes de part et d'autre rapportent tant de pièces de plus par jour. */
    public static final int GOODS_BONUS = 3;

    private TradeService() {}

    /** Une route de commerce : ses deux royaumes, ses deux capitales, sa longueur et ce qu'elle rapporte par jour à chacun. */
    public record Route(UUID a, UUID b, ResourceLocation dim, int x0, int z0, int x1, int z1, int distance, long income, Good goodA, Good goodB,
                        boolean raided) {}

    /** Ce que la distance ajoute au revenu d'une route, en pièces par jour. */
    public static long distanceBonus(int distance) {
        return Math.max(0, Math.min(MAX_BONUS, distance / BLOCKS_PER_COIN));
    }

    /** Ce qu'un traité de commerce rapporte par jour : la part de base, plus la distance. */
    public static long income(long base, int distance) {
        return base + distanceBonus(distance);
    }

    /** Avec les marchandises (P29) : on gagne à échanger ce qu'on n'a pas. */
    public static long income(long base, int distance, Good a, Good b) {
        return income(base, distance) + (a != null && b != null && a != b ? GOODS_BONUS : 0);
    }

    /** La marchandise de chaque royaume, d'après le biome de sa capitale (gardée tant que la capitale ne bouge pas). */
    private static final java.util.Map<UUID, java.util.Map.Entry<BlockPos, Good>> GOODS = new java.util.HashMap<>();

    public static void onServerStopped() {
        GOODS.clear();
    }

    /** La marchandise d'un royaume, ou {@code null} s'il n'a pas de capitale. */
    public static Good good(MinecraftServer server, UUID realm) {
        Optional<Seat> s = seat(CrusadeSavedData.get(server), realm);
        if (s.isEmpty()) return null;
        var cached = GOODS.get(realm);
        if (cached != null && cached.getKey().equals(s.get().pos())) return cached.getValue();
        net.minecraft.server.level.ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, s.get().dim()));
        if (level == null) return null;
        Good g = Good.at(level, s.get().pos());
        if (g == null) return Good.BLE;
        GOODS.put(realm, java.util.Map.entry(s.get().pos(), g));
        return g;
    }

    /** Ce que le commerce avec ce royaume rapporte aujourd'hui : rien si la caravane de la route a été pillée. */
    public static long dailyIncome(MinecraftServer server, UUID me, UUID other, long base) {
        if (CaravanService.raided(me, other, RealmService.currentDay(server))) return 0;
        return income(base, distance(server, me, other).orElse(0), good(server, me), good(server, other));
    }

    /** Où se tient la cour d'un royaume : le centre de la capitale d'un joueur, le siège de la capitale d'un royaume IA. */
    static Optional<Seat> seat(CrusadeSavedData data, UUID realm) {
        Optional<Realm> human = data.realm(realm);
        if (human.isPresent()) {
            return MineColoniesBridge.center(human.get().capital()).map(p -> new Seat(human.get().capital().dimension(), p));
        }
        Optional<AiRealm> ai = data.world().realm(realm);
        if (ai.isPresent() && ai.get().capital() != null) return Optional.of(new Seat(ai.get().capital().dimension(), ai.get().capital().seat()));
        return Optional.empty();
    }

    record Seat(ResourceLocation dim, BlockPos pos) {}

    /** La part de base d'un traité vue de ce royaume : celle du jour pour un joueur (2, plus le développement de l'autre). */
    static long base(CrusadeSavedData data, UUID other) {
        return 2 + data.world().realm(other).map(a -> a.capital() == null ? 0L : (long) a.capital().development() / 20).orElse(0L);
    }

    /** La longueur de la route entre deux royaumes, s'ils ont tous deux une capitale dans le même monde. */
    public static Optional<Integer> distance(MinecraftServer server, UUID a, UUID b) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        Optional<Seat> sa = seat(data, a), sb = seat(data, b);
        if (sa.isEmpty() || sb.isEmpty() || !sa.get().dim().equals(sb.get().dim())) return Optional.empty();
        return Optional.of((int) Math.round(Math.sqrt(sa.get().pos().distSqr(sb.get().pos()))));
    }

    /** Toutes les routes en cours : une par traité de commerce dont les deux capitales sont dans le même monde. */
    public static List<Route> routes(MinecraftServer server) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        long today = RealmService.currentDay(server);
        List<Route> out = new ArrayList<>();
        for (Relation r : data.diplomacy().all()) {
            if (!r.has(Treaty.COMMERCE, today)) continue;
            Optional<Seat> sa = seat(data, r.a()), sb = seat(data, r.b());
            if (sa.isEmpty() || sb.isEmpty() || !sa.get().dim().equals(sb.get().dim())) continue;
            BlockPos pa = sa.get().pos(), pb = sb.get().pos();
            int distance = (int) Math.round(Math.sqrt(pa.distSqr(pb)));
            Good ga = good(server, r.a()), gb = good(server, r.b());
            boolean raided = CaravanService.raided(r.a(), r.b(), today);
            long income = raided ? 0 : income(Math.max(base(data, r.a()), base(data, r.b())), distance, ga, gb);
            out.add(new Route(r.a(), r.b(), sa.get().dim(), pa.getX(), pa.getZ(), pb.getX(), pb.getZ(), distance, income, ga, gb, raided));
        }
        return out;
    }

    /** Le nom d'un royaume pour une route. */
    public static Component name(CrusadeSavedData data, UUID id) {
        Optional<AiRealm> ai = data.world().realm(id);
        if (ai.isPresent()) return AiNames.realm(ai.get().rank(), ai.get().place());
        return Component.literal(data.realm(id).map(Realm::name).orElse("?"));
    }
}
