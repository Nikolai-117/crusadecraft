package fr.crusadecraft.core.world;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.ToastPacket;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les zones dessinées d'avance (P33). Dans l'éditeur de la carte, on crée un royaume et on peint sa zone avant que
 * personne n'y vive ; ensuite :
 * <ul>
 *   <li>poser son Trône dans la zone d'un royaume posé à la main en fait <b>son</b> royaume : ses terres peintes et ses
 *   villes passent au nouveau royaume du joueur ;</li>
 *   <li>un royaume peut en <b>absorber</b> un autre (fusion) : villes, terres peintes et trésor ;</li>
 *   <li>une nouvelle colonie que le souverain fonde dans la zone de son royaume s'y <b>rattache d'elle-même</b>.</li>
 * </ul>
 */
public final class ZoneService {
    private ZoneService() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /**
     * À la fondation : si la capitale du nouveau royaume tombe dans la zone peinte d'un royaume posé à la main, le joueur
     * le reprend. Renvoie le royaume repris, s'il y en a un.
     */
    public static Optional<AiRealm> adoptAtFounding(MinecraftServer server, Realm realm) {
        Optional<BlockPos> center = MineColoniesBridge.center(realm.capital());
        if (center.isEmpty()) return Optional.empty();
        Optional<UUID> painted = data(server).world().borders().at(realm.capital().dimension(), center.get().getX() + 0.5, center.get().getZ() + 0.5);
        Optional<AiRealm> drawn = painted.flatMap(id -> data(server).world().realm(id)).filter(AiRealm::handMade);
        if (drawn.isEmpty()) return Optional.empty();
        absorb(server, drawn.get(), realm.id());
        fr.crusadecraft.core.intrigue.Chronicle.record(server, realm, "crusadecraft.chronicle.adopted",
                AiNames.realm(drawn.get().rank(), drawn.get().place()));
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} reprend le royaume dessiné « {} »", realm.name(), drawn.get().place());
        return drawn;
    }

    /**
     * {@code from} passe sous {@code into} : ses villes, ses terres peintes et son trésor. Vers un royaume de
     * l'ordinateur, les villes déménagent et {@code from} disparaît ; vers un joueur, elles lui sont tenues (comme
     * des villes prises) et {@code from} reste sans terre, à l'écart.
     */
    static void absorb(MinecraftServer server, AiRealm from, UUID into) {
        CrusadeSavedData data = data(server);
        Optional<AiRealm> aiTarget = data.world().realm(into);
        int moved = 0;
        for (AiProvince p : new ArrayList<>(from.provinces())) {
            boolean own = p.holder() == null || p.holder().equals(from.id());
            if (!own) continue;
            if (aiTarget.isPresent()) {
                from.provinces().remove(p);
                AiProvince copy = new AiProvince(p.id(), p.name(), p.dimension(), p.seat(), p.anchor(), false, p.dejureKingdom(),
                        p.population(), p.development());
                aiTarget.get().provinces().add(copy);
            } else {
                p.setHolder(into);
            }
            moved++;
        }
        repaint(data.world().borders(), from.id(), into);
        if (aiTarget.isPresent()) {
            aiTarget.get().setGold(aiTarget.get().gold() + from.gold());
            from.setGold(0);
        } else {
            data.realm(into).ifPresent(r -> fr.crusadecraft.core.treasury.TreasuryService.credit(server, r,
                    fr.crusadecraft.core.treasury.LedgerCategory.CONQUETES, Math.max(0, from.gold())));
            from.setGold(0);
        }
        data.setDirty();
        if (aiTarget.isPresent()) WorldEditService.removeRealm(server, from.id());
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} absorbé ({} ville(s))", from.place(), moved);
    }

    /** Les cases peintes d'un royaume passent à un autre. */
    static int repaint(TerritoryMap borders, UUID from, UUID into) {
        int n = 0;
        for (ResourceLocation dim : new ArrayList<>(borders.dimensions())) {
            LongArrayList keys = new LongArrayList();
            for (var e : borders.cells(dim).long2ObjectEntrySet()) if (from.equals(e.getValue())) keys.add(e.getLongKey());
            if (!keys.isEmpty()) n += borders.paint(dim, keys.toLongArray(), into);
        }
        return n;
    }

    /** La fusion, depuis l'éditeur de la carte : un royaume de l'ordinateur passe sous un autre royaume (IA ou joueur). */
    public static Outcome merge(ServerPlayer player, UUID from, UUID into) {
        if (!WorldEditService.canEdit(player)) return Outcome.no("crusadecraft.mapedit.denied");
        if (from.equals(into)) return Outcome.no("crusadecraft.zone.merge.same");
        CrusadeSavedData data = data(player.server);
        Optional<AiRealm> source = data.world().realm(from);
        if (source.isEmpty()) return Outcome.no("crusadecraft.mapedit.realm.not_ai");
        if (data.world().realm(into).isEmpty() && data.realm(into).isEmpty()) return Outcome.no("crusadecraft.mapedit.realm.unknown");
        Component was = AiNames.realm(source.get().rank(), source.get().place());
        Component target = DiplomacyService.name(player.server, into);
        absorb(player.server, source.get(), into);
        fr.crusadecraft.core.ai.AiBrain.publish(player.server, WorldNews.of(RealmService.currentDay(player.server), "crusadecraft.news.merge",
                List.of(into), was, target), true);
        return Outcome.ok(Component.translatable("crusadecraft.zone.merge.done", was, target));
    }

    /**
     * Toutes les vingt secondes : chaque colonie qu'un souverain possède dans la zone de son royaume (terres peintes ou
     * revendiquées), et qui n'est encore à personne, s'y rattache. Renvoie le nombre de colonies rattachées.
     */
    public static int autoAttach(MinecraftServer server) {
        if (!CrusadeConfig.SERVER.coloniesJoinZone.get()) return 0;
        CrusadeSavedData data = data(server);
        int attached = 0;
        for (Realm realm : data.realms()) {
            if (!realm.state().isActive()) continue;
            for (ServerLevel level : server.getAllLevels()) {
                for (Map.Entry<ColonyRef, BlockPos> c : MineColoniesBridge.coloniesOf(level, realm.ruler()).entrySet()) {
                    ColonyRef colony = c.getKey();
                    if (realm.holds(colony) || data.realmHolding(colony).isPresent()) continue;
                    BlockPos center = c.getValue();
                    boolean inZone = Territory.ownerAt(server, colony.dimension(), center.getX() + 0.5, center.getZ() + 0.5)
                            .map(realm.id()::equals).orElse(false);
                    if (!inZone) continue;
                    realm.addProvince(colony);
                    attached++;
                    String name = MineColoniesBridge.info(colony).name();
                    fr.crusadecraft.core.intrigue.Chronicle.record(server, realm, "crusadecraft.chronicle.annexed", Component.literal(name));
                    ServerPlayer ruler = server.getPlayerList().getPlayer(realm.ruler());
                    if (ruler != null) {
                        CrusadeNetwork.toPlayer(ruler, ToastPacket.of(ToastPacket.Kind.REALM, Component.translatable("crusadecraft.toast.annexed.title"),
                                Component.translatable("crusadecraft.toast.annexed.body", name, realm.name())));
                    }
                    CrusadeCraft.LOGGER.info("CrusadeCraft : la colonie {} rejoint d'elle-même le royaume {}", name, realm.name());
                }
            }
        }
        if (attached > 0) data.setDirty();
        return attached;
    }
}
