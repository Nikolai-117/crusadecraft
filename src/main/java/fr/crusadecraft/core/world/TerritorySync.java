package fr.crusadecraft.core.world;

import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.TerritorySyncPacket;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les terres envoyées aux joueurs (P17), pour les cartes de leur client (Xaero). Toutes les cinq secondes, chaque
 * joueur reçoit les terres de sa dimension si elles ont changé pour lui : celles des royaumes de joueurs et des
 * royaumes de l'ordinateur qu'il a découverts (le brouillard vaut aussi ici). Rien ne part si rien n'a bougé.
 */
public final class TerritorySync {
    private static final Map<UUID, Integer> SENT = new HashMap<>();

    private TerritorySync() {}

    public static void onLogout(UUID player) {
        SENT.remove(player);
    }

    public static void onServerStopped() {
        SENT.clear();
    }

    public static void tick(MinecraftServer server) {
        if (!CrusadeConfig.SERVER.clientTerritory.get()) return;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        Map<ResourceLocation, Map<UUID, List<WebMapGeometry.Rect>>> byDim = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceLocation dim = player.level().dimension().location();
            Map<UUID, List<WebMapGeometry.Rect>> lands = byDim.computeIfAbsent(dim, d -> {
                Map<UUID, List<WebMapGeometry.Rect>> out = new HashMap<>();
                for (Map.Entry<UUID, LongSet> e : Territory.snapshot(server, d).chunksByOwner().entrySet()) {
                    out.put(e.getKey(), WebMapGeometry.rectangles(e.getValue()));
                }
                return out;
            });
            List<TerritorySyncPacket.Owner> owners = new ArrayList<>();
            IntArrayList rects = new IntArrayList();
            List<UUID> ids = new ArrayList<>(lands.keySet());
            ids.sort(UUID::compareTo);
            int hash = dim.hashCode();
            for (UUID id : ids) {
                Optional<AiRealm> ai = data.world().realm(id);
                Optional<Realm> human = data.realm(id);
                if (ai.isEmpty() && human.isEmpty()) continue;
                if (ai.isPresent() && !ai.get().discoveredBy(player.getUUID())) continue;
                int color = ai.map(AiRealm::color).orElseGet(() -> human.get().color()) & 0xFFFFFF;
                Component name = ai.<Component>map(r -> AiNames.realm(r.rank(), r.place())).orElseGet(() -> Component.literal(human.get().name()));
                int index = owners.size();
                owners.add(new TerritorySyncPacket.Owner(id, color, name));
                hash = hash * 31 + id.hashCode() + color * 17 + name.getString().hashCode();
                for (WebMapGeometry.Rect r : lands.get(id)) {
                    rects.add(index);
                    rects.add(r.x0());
                    rects.add(r.z0());
                    rects.add(r.x1());
                    rects.add(r.z1());
                    hash = hash * 31 + r.hashCode();
                }
            }
            Integer last = SENT.get(player.getUUID());
            if (last != null && last == hash) continue;
            SENT.put(player.getUUID(), hash);
            CrusadeNetwork.toPlayer(player, new TerritorySyncPacket(dim, owners, rects.toIntArray()));
        }
    }
}
