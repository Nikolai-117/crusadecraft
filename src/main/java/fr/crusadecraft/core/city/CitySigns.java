package fr.crusadecraft.core.city;

import fr.crusadecraft.block.BuildingSignBlock;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.plan.PlanTool;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.BuildingPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Les enseignes (P44) : un bloc par fonction, à poser dans un bâtiment. Il dit ce qu'est ce bâtiment, sans bâton d'intendant
 * ni commande : une enseigne de boulangerie, et le boulanger arrive. Elle se trouve aussi sur les plans : une ville bâtie
 * avec ses enseignes, à la main ou dans une schématique, se reconnaît toute seule en se posant. La retirer rend le bâtiment
 * à ce qu'il était, et renvoie ses gens.
 */
public final class CitySigns {
    /** Autour d'une enseigne posée hors de tout intérieur (un étal, un champ), le bâtiment fait tant de blocs. */
    static final int OPEN_AIR = 4;

    private CitySigns() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** Une enseigne vient d'être posée par ce joueur. */
    public static Outcome onPlaced(ServerPlayer player, BlockPos pos, BuildingFunction f) {
        MinecraftServer server = player.server;
        ServerLevel level = player.serverLevel();
        Optional<Site> city = CityService.cityAt(server, level.dimension().location(), pos);
        if (city.isEmpty()) {
            // Pas de ville ici : on propose d'en fonder une ; l'enseigne sera reconnue avec les autres.
            if (PlanTool.allowed(player)) {
                CrusadeNetwork.toPlayer(player, new BuildingPackets.OpenFound(CityService.FOUND_DEFAULT));
                return Outcome.ok(Component.translatable("crusadecraft.city.sign.found_first"));
            }
            return Outcome.no("crusadecraft.city.sign.no_city");
        }
        if (!CityService.allowed(player, city.get())) return Outcome.no("crusadecraft.city.denied");
        CityBuilding b = apply(level, city.get(), pos, f);
        int people = b.occupants().size();
        data(server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.city.sign.done", Component.translatable(f.key()), people));
    }

    /** Le bâtiment de cette enseigne prend sa fonction : celui où elle se trouve, ou un nouveau, trouvé autour d'elle. */
    public static CityBuilding apply(ServerLevel level, Site site, BlockPos pos, BuildingFunction f) {
        MinecraftServer server = level.getServer();
        CityBuilding b = CityService.buildingAt(site, pos).orElse(null);
        if (b == null) {
            BlockState st = level.getBlockState(pos);
            BlockPos inFront = st.getBlock() instanceof BuildingSignBlock ? pos.relative(st.getValue(BuildingSignBlock.FACING)) : pos;
            BuildingScan.Result r = BuildingScan.fromInside(level, inFront).or(() -> BuildingScan.fromInside(level, pos.above()))
                    .or(() -> BuildingScan.fromBox(level, pos.offset(-OPEN_AIR, -1, -OPEN_AIR), pos.offset(OPEN_AIR, 3, OPEN_AIR))).orElse(null);
            if (r == null) r = new BuildingScan.Result(pos.offset(-OPEN_AIR, -1, -OPEN_AIR), pos.offset(OPEN_AIR, 3, OPEN_AIR), inFront, 40, 1, java.util.Map.of());
            b = CityService.draft(r);
            site.buildings().add(b);
        }
        CityService.assign(server, site, b, f, level.random.nextLong());
        return b;
    }

    /** Une enseigne retirée : si le bâtiment portait cette fonction, ses gens s'en vont, et il redevient à décider. */
    public static void onRemoved(ServerLevel level, BlockPos pos, BuildingFunction f) {
        MinecraftServer server = level.getServer();
        CityService.cityAt(server, level.dimension().location(), pos).ifPresent(site -> CityService.buildingAt(site, pos).ifPresent(b -> {
            if (b.function() != f) return;
            CityService.assign(server, site, b, null, 0L);
            data(server).setDirty();
        }));
    }
}
