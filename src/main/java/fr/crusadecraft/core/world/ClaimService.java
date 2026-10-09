package fr.crusadecraft.core.world;

import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.place.PlaceService;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.item.BornageStaffItem;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le territoire en commandes (P16), comme dans Factions : revendiquer le chunk où l'on se tient (ou un carré autour),
 * le rendre, voir les chunks voisins en couleurs dans le chat, savoir à qui est celui-ci. Les règles sont celles du
 * bâton de bornage (survie : contigu, proche, payé, plafonné) ; l'annulation Ctrl+Z vaut aussi pour les commandes.
 */
public final class ClaimService {
    /** La carte du chat : 25 chunks de large, 7 de haut (le chat n'affiche qu'une dizaine de lignes). */
    static final int MAP_HALF_W = 12, MAP_HALF_H = 3;
    private static final int WILD = 0x5A5A5A, UNKNOWN = 0x8A7F70, SELF = 0xFFFFFF;

    private ClaimService() {}

    /** Les chunks d'un carré de rayon {@code radius} autour de celui-ci (0 : lui seul). */
    static long[] square(int cx, int cz, int radius) {
        int side = 2 * radius + 1;
        long[] out = new long[side * side];
        int i = 0;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) out[i++] = TerritoryMap.key(cx + dx, cz + dz);
        return out;
    }

    public static Outcome claim(ServerPlayer player, int radius) {
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        int max = CrusadeConfig.SERVER.claimCommandRadius.get();
        if (radius > max) return Outcome.no("crusadecraft.claim.radius", max);
        long[] cells = square(TerritoryMap.cellOf(player.getX()), TerritoryMap.cellOf(player.getZ()), radius);
        return TerritoryService.apply(player, realm.get().id(), "", cells, false);
    }

    /** Rendre des chunks : ils deviennent terre libre (en survie, seulement les siens). */
    public static Outcome unclaim(ServerPlayer player, int radius) {
        int max = CrusadeConfig.SERVER.claimCommandRadius.get();
        if (radius > max) return Outcome.no("crusadecraft.claim.radius", max);
        long[] cells = square(TerritoryMap.cellOf(player.getX()), TerritoryMap.cellOf(player.getZ()), radius);
        return TerritoryService.apply(player, null, BornageStaffItem.NEUTRAL, cells, false);
    }

    /** La carte des chunks voisins, en couleurs, avec sa légende : le joueur au centre, le nord en haut. */
    public static List<Component> map(ServerPlayer player) {
        MinecraftServer server = player.server;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        ResourceLocation dim = player.level().dimension().location();
        Territory.Snapshot snap = Territory.snapshot(server, dim);
        int pcx = TerritoryMap.cellOf(player.getX()), pcz = TerritoryMap.cellOf(player.getZ());
        UUID mine = RealmService.realmOf(server, player.getUUID()).map(Realm::id).orElse(null);
        Map<UUID, Integer> legend = new LinkedHashMap<>();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("crusadecraft.claim.map.title", pcx, pcz));
        for (int dz = -MAP_HALF_H; dz <= MAP_HALF_H; dz++) {
            MutableComponent row = Component.empty();
            for (int dx = -MAP_HALF_W; dx <= MAP_HALF_W; dx++) {
                Optional<UUID> owner = snap.ownerOfCell(TerritoryMap.key(pcx + dx, pcz + dz));
                int color = WILD;
                if (owner.isPresent()) {
                    color = colorFor(data, owner.get(), player);
                    if (color != UNKNOWN) legend.putIfAbsent(owner.get(), color);
                    else legend.putIfAbsent(TerritoryMap.NEUTRAL, UNKNOWN);
                }
                boolean here = dx == 0 && dz == 0;
                int c = here ? SELF : color;
                row.append(Component.literal(here ? "✚" : "■").withStyle(s -> s.withColor(TextColor.fromRgb(c))));
            }
            lines.add(row);
        }
        MutableComponent key = Component.translatable("crusadecraft.claim.map.you").withStyle(s -> s.withColor(TextColor.fromRgb(SELF)));
        key.append(Component.literal("  ■ ").withStyle(s -> s.withColor(TextColor.fromRgb(WILD))))
                .append(Component.translatable("crusadecraft.claim.map.wild").withStyle(s -> s.withColor(TextColor.fromRgb(0xAAAAAA))));
        for (Map.Entry<UUID, Integer> e : legend.entrySet()) {
            int c = e.getValue();
            Component name = TerritoryMap.NEUTRAL.equals(e.getKey()) ? Component.translatable("crusadecraft.claim.map.unknown")
                    : realmName(data, e.getKey());
            key.append(Component.literal("  ■ ").withStyle(s -> s.withColor(TextColor.fromRgb(c))));
            key.append(e.getKey().equals(mine) ? Component.translatable("crusadecraft.claim.map.mine", name) : name.copy());
        }
        lines.add(key);
        return lines;
    }

    /** À qui est ce chunk, comment il l'est, et ce que pèse ce royaume en terres. */
    public static List<Component> info(ServerPlayer player) {
        MinecraftServer server = player.server;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        ResourceLocation dim = player.level().dimension().location();
        Territory.Snapshot snap = Territory.snapshot(server, dim);
        int cx = TerritoryMap.cellOf(player.getX()), cz = TerritoryMap.cellOf(player.getZ());
        long key = TerritoryMap.key(cx, cz);
        Optional<UUID> owner = snap.ownerOfCell(key);
        List<Component> lines = new ArrayList<>();
        if (owner.isEmpty()) {
            lines.add(Component.translatable("crusadecraft.claim.info.wild", cx, cz));
        } else {
            boolean known = colorFor(data, owner.get(), player) != UNKNOWN;
            Component name = known ? realmName(data, owner.get()) : Component.translatable("crusadecraft.claim.map.unknown");
            boolean painted = owner.get().equals(data.world().borders().cells(dim).get(key));
            lines.add(Component.translatable(painted ? "crusadecraft.claim.info.claimed" : "crusadecraft.claim.info.auto", cx, cz, name));
            if (known) {
                LongSet chunks = snap.chunksByOwner().get(owner.get());
                int total = chunks == null ? 0 : chunks.size();
                Optional<Realm> mine = RealmService.realmOf(server, player.getUUID()).filter(r -> r.id().equals(owner.get()));
                if (mine.isPresent()) {
                    lines.add(Component.translatable("crusadecraft.claim.info.yours", total, data.world().borders().countOf(owner.get()),
                            TerritoryService.claimCap(mine.get())));
                } else {
                    lines.add(Component.translatable("crusadecraft.claim.info.realm", total));
                }
            }
        }
        lines.add(Component.translatable("crusadecraft.lieu.here", PlaceService.chainAt(server, dim, player.getX(), player.getZ())));
        return lines;
    }

    private static int colorFor(CrusadeSavedData data, UUID owner, ServerPlayer viewer) {
        Optional<AiRealm> ai = data.world().realm(owner);
        if (ai.isPresent()) return ai.get().discoveredBy(viewer.getUUID()) ? ai.get().color() & 0xFFFFFF : UNKNOWN;
        return data.realm(owner).map(r -> r.color() & 0xFFFFFF).orElse(UNKNOWN);
    }

    private static Component realmName(CrusadeSavedData data, UUID id) {
        Optional<AiRealm> ai = data.world().realm(id);
        if (ai.isPresent()) return AiNames.realm(ai.get().rank(), ai.get().place());
        return Component.literal(data.realm(id).map(Realm::name).orElse("?"));
    }
}
