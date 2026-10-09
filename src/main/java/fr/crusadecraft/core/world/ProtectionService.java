package fr.crusadecraft.core.world;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La protection des terres (P16), chunk par chunk, selon le lien entre le joueur et le royaume qui les tient, comme
 * dans Factions.
 *
 * <ul>
 *   <li>Les siens (le souverain, les officiers de ses colonies) font tout.</li>
 *   <li>Suzerain et vassaux aussi (réglable).</li>
 *   <li>Un allié ouvre portes et coffres, sans casser ni poser (réglable).</li>
 *   <li>En guerre, l'ennemi pose ses échelles et frappe ; casser reste l'affaire des règles de guerre (explosifs).</li>
 *   <li>Les autres ne font rien.</li>
 * </ul>
 *
 * <p>Ne visent que les vrais joueurs : ni les automates (Create, faux joueurs), ni les citoyens MineColonies. Dans une
 * colonie, ce sont les permissions de MineColonies qui comptent. Les terres des royaumes IA sont libres par défaut.
 */
public final class ProtectionService {
    public enum Action { BREAK, PLACE, INTERACT, ATTACK }

    /** Le refus n'est rappelé qu'une fois par seconde : casser à la chaîne ne doit pas inonder l'écran. */
    private static final Map<UUID, Long> LAST_WARNING = new HashMap<>();

    private ProtectionService() {}

    public static void onLogout(UUID player) {
        LAST_WARNING.remove(player);
    }

    /** Le refus, s'il y en a un : le message à montrer. Vide : permis. */
    public static Optional<Component> deny(ServerPlayer player, BlockPos pos, Action action) {
        if (player instanceof FakePlayer) return Optional.empty();
        if (CrusadeConfig.SERVER.operatorsBypass.get() && player.hasPermissions(2)) return Optional.empty();
        return decide(player.server, player.level(), player.getUUID(), pos, action);
    }

    /** La décision pour ce joueur (désigné par son identifiant), sans les exceptions des automates et des opérateurs. */
    public static Optional<Component> decide(MinecraftServer server, net.minecraft.world.level.Level level, UUID player, BlockPos pos, Action action) {
        if (!CrusadeConfig.SERVER.protection.get()) return Optional.empty();
        Optional<UUID> owner = Territory.ownerAt(server, level.dimension().location(), pos.getX() + 0.5, pos.getZ() + 0.5);
        if (owner.isEmpty()) return Optional.empty();
        // Dans une colonie, MineColonies a ses propres permissions : on ne s'en mêle pas.
        if (MineColoniesBridge.isReady() && MineColoniesBridge.colonyAt(level, pos).isPresent()) return Optional.empty();
        CrusadeSavedData data = CrusadeSavedData.get(server);
        UUID realm = owner.get();
        Optional<AiRealm> ai = data.world().realm(realm);
        if (ai.isPresent() && !CrusadeConfig.SERVER.protectAiLand.get()) return Optional.empty();
        Optional<Realm> human = data.realm(realm);
        if (human.isPresent() && isMember(human.get(), player)) return Optional.empty();
        UUID mine = RealmService.realmOf(server, player).map(Realm::id).orElse(null);
        if (allowed(data, mine, realm, action, RealmService.currentDay(server))) return Optional.empty();
        Component name = ai.map(r -> AiNames.realm(r.rank(), r.place()))
                .orElseGet(() -> Component.literal(human.map(Realm::name).orElse("?")));
        return Optional.of(Component.translatable("crusadecraft.protection.denied", name));
    }

    /** Le refus montré au joueur, au plus une fois par seconde. Renvoie vrai si l'action est refusée. */
    public static boolean refuse(ServerPlayer player, BlockPos pos, Action action) {
        Optional<Component> denial = deny(player, pos, action);
        if (denial.isEmpty()) return false;
        long now = System.currentTimeMillis();
        Long last = LAST_WARNING.get(player.getUUID());
        if (last == null || now - last > 1000) {
            LAST_WARNING.put(player.getUUID(), now);
            player.displayClientMessage(denial.get(), true);
        }
        return true;
    }

    /** Le souverain, ou un officier (ou le propriétaire) de l'une de ses colonies au sens de MineColonies. */
    static boolean isMember(Realm realm, UUID player) {
        if (realm.ruler().equals(player)) return true;
        if (!MineColoniesBridge.isReady()) return false;
        for (ColonyRef c : realm.allColonies()) if (MineColoniesBridge.isManager(c, player)) return true;
        return false;
    }

    /** Ce que le lien entre deux royaumes permet ({@code mine} : celui du joueur, ou {@code null}). */
    static boolean allowed(CrusadeSavedData data, UUID mine, UUID owner, Action action, long today) {
        if (mine == null) return false;
        if (mine.equals(owner)) return true;
        // En guerre : l'assaillant pose ses échelles, ouvre les portes, frappe ; casser relève des règles de guerre.
        if (data.wars().between(mine, owner).isPresent()) return true;
        var dip = data.diplomacy();
        if (CrusadeConfig.SERVER.protectVassals.get()
                && (dip.suzerainOf(mine).map(owner::equals).orElse(false) || dip.suzerainOf(owner).map(mine::equals).orElse(false))) {
            return true;
        }
        if (action == Action.INTERACT && CrusadeConfig.SERVER.alliesInteract.get()) {
            Optional<Relation> rel = dip.find(mine, owner);
            if (rel.isPresent() && (rel.get().has(Treaty.ALLIANCE, today) || rel.get().has(Treaty.ALLIANCE_DEFENSIVE, today))) return true;
        }
        return false;
    }
}
