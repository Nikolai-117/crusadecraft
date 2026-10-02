package fr.crusadecraft.block;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Optional;

/**
 * Règles communes aux meubles de la cour : où l'on peut les poser, et leur inscription dans les
 * données du royaume (la position de la table, celle du bureau de chaque maire).
 */
public final class CourtBlocks {

    private CourtBlocks() {}

    /** Vérifie la pose de la Table du Conseil ; renvoie le motif de refus, ou vide si elle est permise. */
    public static Optional<Component> checkTable(ServerPlayer player, Level level, BlockPos pos) {
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) return Optional.of(Component.translatable("crusadecraft.realm.none"));
        Optional<ColonyRef> colony = MineColoniesBridge.colonyAt(level, pos);
        if (colony.isEmpty() || !realm.get().holds(colony.get())) {
            return Optional.of(Component.translatable("crusadecraft.court.outside_realm"));
        }
        int need = CrusadeConfig.SERVER.councilMinTownHallLevel.get();
        if (MineColoniesBridge.townHallLevel(colony.get()) < need) {
            return Optional.of(Component.translatable("crusadecraft.court.town_hall_level", need));
        }
        var court = realm.get().court();
        if (court.hasTable() && !(court.tablePos().equals(pos) && court.tableDimension().equals(level.dimension().location()))) {
            return Optional.of(Component.translatable("crusadecraft.court.table_exists", court.tablePos().toShortString()));
        }
        return Optional.empty();
    }

    public static Optional<Component> checkDesk(ServerPlayer player, Level level, BlockPos pos) {
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) return Optional.of(Component.translatable("crusadecraft.realm.none"));
        Optional<ColonyRef> colony = MineColoniesBridge.colonyAt(level, pos);
        if (colony.isEmpty() || !realm.get().holds(colony.get())) {
            return Optional.of(Component.translatable("crusadecraft.court.outside_realm"));
        }
        BlockPos existing = realm.get().court().mayorDesks().get(colony.get());
        if (existing != null && !existing.equals(pos)) {
            return Optional.of(Component.translatable("crusadecraft.court.desk_exists", existing.toShortString()));
        }
        return Optional.empty();
    }

    /** Le Coffre du Trésor se pose dans une colonie de son royaume. */
    public static Optional<Component> checkTreasury(ServerPlayer player, Level level, BlockPos pos) {
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) return Optional.of(Component.translatable("crusadecraft.realm.none"));
        Optional<ColonyRef> colony = MineColoniesBridge.colonyAt(level, pos);
        if (colony.isEmpty() || !realm.get().holds(colony.get())) {
            return Optional.of(Component.translatable("crusadecraft.court.outside_realm"));
        }
        return Optional.empty();
    }

    public static void registerTable(ServerPlayer player, Level level, BlockPos pos) {
        RealmService.realmOf(player.server, player.getUUID()).ifPresent(realm -> {
            realm.court().setTable(level.dimension().location(), pos);
            CrusadeSavedData.get(player.server).setDirty();
        });
    }

    public static void registerDesk(ServerPlayer player, Level level, BlockPos pos) {
        Optional<ColonyRef> colony = MineColoniesBridge.colonyAt(level, pos);
        if (colony.isEmpty()) return;
        RealmService.realmOf(player.server, player.getUUID()).ifPresent(realm -> {
            if (!realm.holds(colony.get())) return;
            realm.court().mayorDesks().put(colony.get(), pos.immutable());
            CrusadeSavedData.get(player.server).setDirty();
        });
    }

    /** La table ou le bureau a été cassé : on l'efface des données de son royaume. */
    public static void unregister(ServerLevel level, BlockPos pos) {
        CrusadeSavedData data = CrusadeSavedData.get(level.getServer());
        for (Realm realm : data.realms()) {
            var court = realm.court();
            if (court.hasTable() && court.tablePos().equals(pos) && court.tableDimension().equals(level.dimension().location())) {
                court.clearTable();
                data.setDirty();
            }
            for (Map.Entry<ColonyRef, BlockPos> e : Map.copyOf(court.mayorDesks()).entrySet()) {
                if (e.getValue().equals(pos) && e.getKey().dimension().equals(level.dimension().location())) {
                    court.mayorDesks().remove(e.getKey());
                    data.setDirty();
                }
            }
        }
    }
}
