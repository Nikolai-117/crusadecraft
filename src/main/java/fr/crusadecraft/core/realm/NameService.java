package fr.crusadecraft.core.realm;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.logic.TextRules;
import fr.crusadecraft.core.place.Lieu;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * Les noms du monde (P32) : renommer son royaume et ses villes depuis le jeu (menu royal, écran des villes, écran des
 * lieux, ou commande), et, pour qui a l'éditeur de la carte, les royaumes et les villes de l'ordinateur. Un nom changé
 * l'est partout : carte du mod, Dynmap, registre, fiches — tout le lit à sa source.
 */
public final class NameService {
    private NameService() {}

    private static CrusadeSavedData data(ServerPlayer player) {
        return CrusadeSavedData.get(player.server);
    }

    /** Un nom propre : nettoyé, pas vide, sans codes de mise en forme. {@code null} s'il ne va pas. */
    static String clean(String raw) {
        String name = TextRules.clean(raw, TextRules.NAME_MAX);
        return TextRules.isValidName(name) ? name : null;
    }

    /** Le souverain renomme son royaume. */
    public static Outcome renameRealm(ServerPlayer player, String raw) {
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        return renameRealm(player, realm.get(), raw);
    }

    static Outcome renameRealm(ServerPlayer player, Realm realm, String raw) {
        if (!realm.ruler().equals(player.getUUID()) && !WorldEditService.canEdit(player)) return Outcome.no("crusadecraft.mapedit.denied");
        String name = clean(raw);
        if (name == null) return Outcome.no("crusadecraft.name.invalid", TextRules.NAME_MAX);
        String before = realm.name();
        realm.setName(name);
        data(player).setDirty();
        CrusadeCraft.LOGGER.info("CrusadeCraft : le royaume « {} » s'appelle désormais « {} »", before, name);
        return Outcome.ok(Component.translatable("crusadecraft.name.realm.done", name));
    }

    /** Le souverain renomme une de ses villes : la colonie MineColonies elle-même change de nom. */
    public static Outcome renameColony(ServerPlayer player, ColonyRef colony, String raw) {
        Optional<Realm> holder = data(player).realmHolding(colony);
        boolean editor = WorldEditService.canEdit(player);
        boolean mine = holder.map(r -> r.ruler().equals(player.getUUID())).orElse(false) || MineColoniesBridge.isOwner(colony, player.getUUID());
        if (!mine && !editor) return Outcome.no("crusadecraft.mapedit.denied");
        String name = clean(raw);
        if (name == null) return Outcome.no("crusadecraft.name.invalid", TextRules.NAME_MAX);
        if (!MineColoniesBridge.rename(colony, name)) return Outcome.no("crusadecraft.name.colony.missing");
        data(player).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.name.city.done", name));
    }

    /** La colonie où l'on se tient, renommée (commande). */
    public static Outcome renameColonyHere(ServerPlayer player, String raw) {
        Optional<ColonyRef> here = MineColoniesBridge.colonyAt(player.level(), player.blockPosition());
        if (here.isEmpty()) return Outcome.no("crusadecraft.name.colony.none");
        return renameColony(player, here.get(), raw);
    }

    /** Un royaume de l'ordinateur (éditeur de la carte) : sa capitale suit si elle portait son nom. */
    public static Outcome renameAiRealm(ServerPlayer player, UUID id, String raw) {
        if (!WorldEditService.canEdit(player)) return Outcome.no("crusadecraft.mapedit.denied");
        Optional<AiRealm> ai = data(player).world().realm(id);
        if (ai.isEmpty()) return Outcome.no("crusadecraft.mapedit.realm.unknown");
        String name = clean(raw);
        if (name == null) return Outcome.no("crusadecraft.name.invalid", TextRules.NAME_MAX);
        AiProvince capital = ai.get().capital();
        if (capital != null && capital.name().equals(ai.get().place())) capital.setName(name);
        ai.get().setPlace(name);
        data(player).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.name.realm.done", name));
    }

    /** Une ville de l'ordinateur (éditeur de la carte). */
    public static Outcome renameAiCity(ServerPlayer player, int province, String raw) {
        if (!WorldEditService.canEdit(player)) return Outcome.no("crusadecraft.mapedit.denied");
        String name = clean(raw);
        if (name == null) return Outcome.no("crusadecraft.name.invalid", TextRules.NAME_MAX);
        for (AiRealm r : data(player).world().realms()) {
            for (AiProvince p : r.provinces()) {
                if (p.id() != province) continue;
                if (p.capital() && p.name().equals(r.place())) r.setPlace(name);
                p.setName(name);
                data(player).setDirty();
                return Outcome.ok(Component.translatable("crusadecraft.name.city.done", name));
            }
        }
        return Outcome.no("crusadecraft.mapedit.city.unknown");
    }

    /** Le nom d'un lieu qui renvoie à autre chose (royaume, ville, colonie) se change à sa source. */
    public static Outcome renameSource(ServerPlayer player, Lieu.Ref ref, String raw) {
        return switch (ref.type()) {
            case PLAYER_REALM -> data(player).realm(ref.uuid()).map(r -> renameRealm(player, r, raw))
                    .orElse(Outcome.no("crusadecraft.lieu.unknown"));
            case AI_REALM -> renameAiRealm(player, ref.uuid(), raw);
            case AI_PROVINCE -> renameAiCity(player, ref.index(), raw);
            case COLONY -> renameColony(player, new ColonyRef(ref.dim(), ref.index()), raw);
            case SITE -> Outcome.no("crusadecraft.lieu.unknown");
        };
    }
}
