package fr.crusadecraft.core.ai;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.Territory;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Les civilisations autonomes (P22) : les royaumes de l'ordinateur bâtissent d'eux-mêmes, de loin en loin, une tour,
 * un camp, un avant-poste puis un fort sur leurs propres terres, près d'une de leurs villes. C'est la seule partie du
 * mod qui change le monde sans geste du joueur : coupée par défaut, limitée à une zone, seulement dans les chunks déjà
 * chargés, jamais dans une colonie, jamais sur autre chose qu'un sol naturel et plat ; chaque bloc remplacé est noté,
 * et {@code /crusade civ annuler} défait tout.
 */
public final class CivService {
    private CivService() {}

    /** La construction suivante d'un royaume, selon ce qu'il a déjà bâti de lui-même et son rang. */
    public static SiteKind nextKind(int alreadyBuilt, TitleRank rank) {
        return switch (alreadyBuilt) {
            case 0 -> SiteKind.TOUR;
            case 1 -> SiteKind.CAMP;
            default -> rank.ordinal() >= TitleRank.DUCHE.ordinal() && alreadyBuilt >= 2 ? SiteKind.FORT : SiteKind.AVANT_POSTE;
        };
    }

    /** Dans la zone permise : à moins de {@code radius} blocs de l'origine du monde (0 : partout). */
    public static boolean inZone(int x, int z, int radius) {
        return radius <= 0 || (long) x * x + (long) z * z <= (long) radius * radius;
    }

    /** Un sol qu'on peut bâtir : naturel (terre, herbe, sable, pierre, neige, gravier), sec. */
    static boolean naturalGround(BlockState s) {
        return s.getFluidState().isEmpty() && (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(BlockTags.SNOW) || s.is(net.minecraft.world.level.block.Blocks.GRAVEL));
    }

    /** Appelé chaque jour du monde. Renvoie le nombre de chantiers ouverts. */
    public static int onNewDay(MinecraftServer server, long day) {
        if (!CrusadeConfig.SERVER.civActive.get()) return 0;
        int every = CrusadeConfig.SERVER.civDays.get();
        if (day <= 0 || day % every != 0) return 0;
        Random random = new Random(day * 92821L + server.getWorldData().worldGenOptions().seed());
        CrusadeSavedData data = CrusadeSavedData.get(server);
        int opened = 0;
        for (AiRealm ai : new ArrayList<>(data.world().realms())) {
            if (ai.landless() || data.diplomacy().suzerainOf(ai.id()).isPresent()) continue;
            if (random.nextInt(100) >= CrusadeConfig.SERVER.civChance.get()) continue;
            // P28 : faire grandir ou fonder un village passe avant une place forte de plus.
            if (VillageService.onDay(server, ai, day, random).isPresent() || tryBuild(server, ai, random).isPresent()) opened++;
        }
        return opened;
    }

    /** Ce royaume cherche un endroit pour sa prochaine construction, et l'ouvre s'il en trouve un. */
    /** Les places fortes que ce royaume a posées de lui-même (ses villages et leurs quartiers à part). */
    static int military(CrusadeSavedData data, AiRealm ai) {
        int built = 0;
        for (Site s : data.world().sites()) {
            if (s.auto() && ai.id().equals(s.owner()) && s.kind() != SiteKind.VILLAGE && s.kind() != SiteKind.QUARTIER) built++;
        }
        return built;
    }

    public static Optional<Site> tryBuild(MinecraftServer server, AiRealm ai, Random random) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        int built = military(data, ai);
        if (built >= CrusadeConfig.SERVER.civMaxPerRealm.get()) return Optional.empty();
        SiteKind kind = nextKind(built, ai.rank());
        List<AiProvince> provinces = ai.ownProvinces();
        if (provinces.isEmpty()) return Optional.empty();
        int radius = CrusadeConfig.SERVER.civRadius.get();
        for (int attempt = 0; attempt < 12; attempt++) {
            AiProvince home = provinces.get(random.nextInt(provinces.size()));
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, home.dimension()));
            if (level == null) continue;
            double angle = random.nextDouble() * Math.PI * 2, dist = 40 + random.nextInt(60);
            int x = home.seat().getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = home.seat().getZ() + (int) Math.round(Math.sin(angle) * dist);
            Optional<BlockPos> ground = suitable(server, level, ai, kind, x, z, radius);
            if (ground.isEmpty()) continue;
            Direction front = Direction.getNearest(home.seat().getX() - x, 0, home.seat().getZ() - z);
            if (!front.getAxis().isHorizontal()) front = Direction.SOUTH;
            Optional<Site> site = SiteService.placeForAi(server, level, kind, ai, ground.get(), front);
            if (site.isPresent()) return site;
        }
        return Optional.empty();
    }

    /** Bâtir à cet endroit précis, s'il convient (bancs d'essai : un terrain préparé plutôt qu'un hasard du relief). */
    public static Optional<Site> tryBuildAt(MinecraftServer server, AiRealm ai, ServerLevel level, int x, int z) {
        int built = military(CrusadeSavedData.get(server), ai);
        SiteKind kind = nextKind(built, ai.rank());
        Optional<BlockPos> ground = suitable(server, level, ai, kind, x, z, 0);
        if (ground.isEmpty()) return Optional.empty();
        return SiteService.placeForAi(server, level, kind, ai, ground.get(), Direction.SOUTH);
    }

    /**
     * Un endroit où bâtir : chunks chargés, dans la zone, sur les terres de ce royaume, hors de toute colonie, sur un
     * sol naturel et à peu près plat (trois blocs d'écart au plus), sans rien de bâti par-dessus.
     */
    static Optional<BlockPos> suitable(MinecraftServer server, ServerLevel level, AiRealm ai, SiteKind kind, int x, int z, int radius) {
        if (refusal(server, level, ai, kind, x, z, radius) != null) return Optional.empty();
        return Optional.of(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below());
    }

    /** Pourquoi cet endroit ne convient pas, en clair ({@code null} s'il convient) : pour {@link #suitable} et les bancs d'essai. */
    public static String refusal(MinecraftServer server, ServerLevel level, AiRealm ai, SiteKind kind, int x, int z, int radius) {
        if (!inZone(x, z, radius)) return "hors de la zone permise";
        int half = Math.max(2, kind.size() / 2);
        int[][] probes = {{0, 0}, {-half, -half}, {half, -half}, {-half, half}, {half, half}, {0, -half}, {0, half}, {-half, 0}, {half, 0}};
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int[] d : probes) {
            BlockPos column = new BlockPos(x + d[0], 0, z + d[1]);
            String at = " en " + column.getX() + "," + column.getZ();
            if (!level.hasChunkAt(column)) return "chunk non chargé" + at;
            if (MineColoniesBridge.colonyAt(level, column).isPresent()) return "dans une colonie" + at;
            Optional<java.util.UUID> owner = Territory.ownerAt(server, level.dimension().location(), column.getX() + 0.5, column.getZ() + 0.5);
            if (!owner.map(ai.id()::equals).orElse(false)) return "terre de " + owner.map(Object::toString).orElse("personne") + at;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            BlockState ground = level.getBlockState(top.below());
            if (!naturalGround(ground)) return "sol " + ground.getBlock().getDescriptionId() + at + " (y " + top.getY() + ")";
            min = Math.min(min, top.getY());
            max = Math.max(max, top.getY());
        }
        if (max - min > 3) return "pas assez plat (" + min + " à " + max + ")";
        return null;
    }
}
