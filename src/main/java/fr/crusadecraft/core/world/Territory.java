package fr.crusadecraft.core.world;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * À qui appartient un endroit du monde (P14) : la seule réponse, partagée par le bâton de bornage, les règles de
 * survie, la guerre, l'annonce d'entrée et la carte papier.
 *
 * <p>La règle est celle de la carte du mod : une case peinte décide seule (terre libre comprise) ; sinon, le siège le
 * plus proche dont le cercle couvre l'endroit — province d'un royaume IA (qui la tient, conquêtes comprises),
 * colonie d'un joueur, construction bâtie, poste de garde MineColonies.
 *
 * <p>Pour une série de questions (un tracé de centaines de cases), on prend un {@link Snapshot} une fois : il
 * rassemble les cercles et répond ensuite sans rien recalculer.
 */
public final class Territory {
    /** Les postes de garde changent rarement : on les relit au plus toutes les trente secondes. */
    private static final long GUARD_CACHE_MS = 30_000;
    private static final Map<ColonyRef, List<MineColoniesBridge.GuardPost>> GUARDS = new HashMap<>();
    private static final Map<ColonyRef, Long> GUARDS_READ = new HashMap<>();

    private Territory() {}

    /** Un cercle de territoire : son centre, son rayon au carré, à qui il revient. */
    public record Circle(double x, double z, double r2, UUID owner, Kind kind) {}

    public enum Kind { PROVINCE, COLONY, SITE, GUARD_POST }

    public static void onServerStopped() {
        GUARDS.clear();
        GUARDS_READ.clear();
    }

    /** Le rayon d'un poste de garde MineColonies : 48 blocs, et 16 de plus par niveau. */
    public static int guardRadius(int level) {
        return 48 + 16 * Math.max(1, level);
    }

    /** Les postes de garde d'une colonie, relus au plus toutes les trente secondes. */
    public static List<MineColoniesBridge.GuardPost> guardPosts(ColonyRef colony) {
        long now = System.currentTimeMillis();
        Long read = GUARDS_READ.get(colony);
        if (read == null || now - read > GUARD_CACHE_MS) {
            GUARDS.put(colony, MineColoniesBridge.isReady() ? MineColoniesBridge.guardPosts(colony) : List.of());
            GUARDS_READ.put(colony, now);
        }
        return GUARDS.getOrDefault(colony, List.of());
    }

    /** Tous les cercles de territoire d'une dimension. */
    public static List<Circle> circles(MinecraftServer server, ResourceLocation dim) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        List<Circle> out = new ArrayList<>();
        double provinceR2 = sq(CrusadeConfig.SERVER.territoryRadius.get());
        for (AiRealm r : data.world().realms()) {
            for (AiProvince p : r.provinces()) {
                if (!p.dimension().equals(dim)) continue;
                out.add(new Circle(p.seat().getX() + 0.5, p.seat().getZ() + 0.5, provinceR2, p.holder() == null ? r.id() : p.holder(), Kind.PROVINCE));
            }
        }
        double colonyR2 = sq(CrusadeConfig.SERVER.colonyRadius.get());
        for (Realm r : data.realms()) {
            for (ColonyRef c : r.allColonies()) {
                if (!c.dimension().equals(dim)) continue;
                Optional<BlockPos> center = MineColoniesBridge.isReady() ? MineColoniesBridge.center(c) : Optional.empty();
                center.ifPresent(b -> out.add(new Circle(b.getX() + 0.5, b.getZ() + 0.5, colonyR2, r.id(), Kind.COLONY)));
                for (MineColoniesBridge.GuardPost g : guardPosts(c)) {
                    out.add(new Circle(g.pos().getX() + 0.5, g.pos().getZ() + 0.5, sq(guardRadius(g.level())), r.id(), Kind.GUARD_POST));
                }
            }
        }
        for (Site s : data.world().sites()) {
            if (!s.dimension().equals(dim) || s.radius() <= 0) continue;
            out.add(new Circle(s.anchor().getX() + 0.5, s.anchor().getZ() + 0.5, sq(s.radius()), s.owner(), Kind.SITE));
        }
        return out;
    }

    private static double sq(double v) {
        return v * v;
    }

    public static Snapshot snapshot(MinecraftServer server, ResourceLocation dim) {
        return new Snapshot(CrusadeSavedData.get(server).world().borders(), dim, circles(server, dim));
    }

    /** À qui appartient cet endroit (vide : à personne). */
    public static Optional<UUID> ownerAt(MinecraftServer server, ResourceLocation dim, double x, double z) {
        return snapshot(server, dim).ownerAt(x, z);
    }

    /** Les cercles et les cases peintes d'une dimension, figés le temps d'une série de questions. */
    public static final class Snapshot {
        private final TerritoryMap borders;
        private final ResourceLocation dim;
        private final List<Circle> circles;

        public Snapshot(TerritoryMap borders, ResourceLocation dim, List<Circle> circles) {
            this.borders = borders;
            this.dim = dim;
            this.circles = circles;
        }

        public List<Circle> circles() {
            return circles;
        }

        /** Ce qui est peint à cet endroit, s'il l'est (y compris {@link TerritoryMap#NEUTRAL}). */
        public Optional<UUID> painted(double x, double z) {
            return borders.at(dim, x, z);
        }

        /** Le propriétaire par les seuls cercles, sans tenir compte de la peinture. */
        public Optional<UUID> circleOwner(double x, double z) {
            Circle best = null;
            double bestD = Double.MAX_VALUE;
            for (Circle c : circles) {
                double d = sq(x - c.x()) + sq(z - c.z());
                if (d <= c.r2() && d < bestD) {
                    best = c;
                    bestD = d;
                }
            }
            return best == null ? Optional.empty() : Optional.of(best.owner());
        }

        public Optional<UUID> ownerAt(double x, double z) {
            Optional<UUID> painted = painted(x, z);
            if (painted.isPresent()) return TerritoryMap.NEUTRAL.equals(painted.get()) ? Optional.empty() : painted;
            return circleOwner(x, z);
        }

        /** Le propriétaire d'une case (son centre). */
        public Optional<UUID> ownerOfCell(long key) {
            return ownerAt(TerritoryMap.cellX(key) * TerritoryMap.CELL + 8, TerritoryMap.cellZ(key) * TerritoryMap.CELL + 8);
        }
    }
}
