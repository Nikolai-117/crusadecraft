package fr.crusadecraft.core.build.plan;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;

/**
 * Les villes de l'ordinateur prennent corps (P34) : quand un joueur approche d'une ville de la carte qui n'existe
 * encore que sur la carte, son chantier s'ouvre, aux bâtiments du style de son royaume. Coupé par défaut.
 *
 * <p>Par prudence, jamais là où des joueurs ont longtemps vécu (le temps passé dans les chunks voisins, que Minecraft
 * compte), ni près d'une colonie, ni dans une ville tenue par un royaume de joueur.
 */
public final class AiTowns {
    /** Vingt minutes de présence dans un chunk : on suppose qu'on y a bâti. */
    static final long LIVED_TICKS = 20L * 60 * 20;
    /** Une colonie à moins de tant de blocs : on ne bâtit pas. */
    static final int COLONY_RANGE = 160;

    private AiTowns() {}

    /** Toutes les dix secondes : ouvre les chantiers des villes qu'un joueur approche. Renvoie le nombre ouvert. */
    public static int tick(MinecraftServer server) {
        if (!CrusadeConfig.SERVER.aiTowns.get()) return 0;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        int building = 0;
        for (Site s : data.world().sites()) if (s.town() != null && !s.built() && s.auto()) building++;
        int max = CrusadeConfig.SERVER.aiTownsAtOnce.get(), range = CrusadeConfig.SERVER.aiTownRange.get();
        int opened = 0;
        for (AiRealm r : new ArrayList<>(data.world().realms())) {
            for (AiProvince p : new ArrayList<>(r.provinces())) {
                if (building + opened >= max) return opened;
                if (p.holder() != null && data.realm(p.holder()).isPresent()) continue;
                if (SiteService.townOf(server, p.id()).isPresent() || hasVillage(data, p.id())) continue;
                ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, p.dimension()));
                if (level == null || !level.hasChunkAt(p.seat()) || !playerNear(level, p.seat(), range)) continue;
                if (lived(level, p.seat()) || colonyNear(level, p.seat())) continue;
                if (SiteService.startTown(server, p.id(), false, true).isPresent()) opened++;
            }
        }
        return opened;
    }

    /** Un village de l'ordinateur (P28) a déjà fondé cette ville. */
    private static boolean hasVillage(CrusadeSavedData data, int province) {
        for (Site s : data.world().sites()) if (s.kind() == SiteKind.VILLAGE && s.province() == province) return true;
        return false;
    }

    public static boolean playerNear(ServerLevel level, BlockPos seat, int range) {
        for (ServerPlayer pl : level.players()) {
            double dx = pl.getX() - seat.getX(), dz = pl.getZ() - seat.getZ();
            if (dx * dx + dz * dz <= (double) range * range) return true;
        }
        return false;
    }

    /** Des joueurs ont-ils longtemps vécu autour (les neuf chunks chargés autour du siège) ? */
    public static boolean lived(ServerLevel level, BlockPos seat) {
        int cx = seat.getX() >> 4, cz = seat.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.hasChunk(cx + dx, cz + dz)) continue;
                if (level.getChunk(cx + dx, cz + dz).getInhabitedTime() > LIVED_TICKS) return true;
            }
        }
        return false;
    }

    public static boolean colonyNear(ServerLevel level, BlockPos seat) {
        for (int[] c : MineColoniesBridge.colonyCenters(level)) {
            double dx = c[0] - seat.getX(), dz = c[1] - seat.getZ();
            if (dx * dx + dz * dz < (double) COLONY_RANGE * COLONY_RANGE) return true;
        }
        return false;
    }
}
