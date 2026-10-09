package fr.crusadecraft.core.world;

import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * L'annonce d'entrée sur des terres (P16), comme dans Factions : on passe la frontière d'un chunk à l'autre, le nom du
 * royaume s'affiche en grand, avec son souverain ; quand on en sort, « Terres sans maître » au-dessus de la barre.
 * Un royaume IA pas encore découvert reste « Terres inconnues ».
 */
public final class EntryTitles {
    public enum Mode { TITRE, BARRE, AUCUN }

    /** Ce que chaque joueur avait sous les pieds à la dernière vérification ({@link #NOBODY} : terre libre). */
    private static final Map<UUID, UUID> LAST = new HashMap<>();
    private static final UUID NOBODY = new UUID(0, 2);

    private EntryTitles() {}

    public static void onLogout(UUID player) {
        LAST.remove(player);
    }

    public static void onServerStopped() {
        LAST.clear();
    }

    /** Une fois par seconde : qui a changé de terres. */
    public static void tick(MinecraftServer server) {
        Mode mode = CrusadeConfig.SERVER.entryMessages.get();
        if (mode == Mode.AUCUN) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            UUID owner = Territory.ownerAt(server, player.level().dimension().location(), player.getX(), player.getZ()).orElse(NOBODY);
            UUID before = LAST.put(player.getUUID(), owner);
            // À la connexion, on ne rappelle pas où l'on est : seulement quand on passe une frontière.
            if (before == null || before.equals(owner)) continue;
            announce(player, owner, mode);
        }
    }

    private static void announce(ServerPlayer player, UUID owner, Mode mode) {
        MinecraftServer server = player.server;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        if (NOBODY.equals(owner)) {
            player.displayClientMessage(Component.translatable("crusadecraft.entry.wild"), true);
            return;
        }
        Component title;
        Component subtitle;
        int color;
        Optional<AiRealm> ai = data.world().realm(owner);
        Optional<Realm> human = data.realm(owner);
        if (ai.isPresent()) {
            AiRealm r = ai.get();
            if (!r.discoveredBy(player.getUUID())) {
                title = Component.translatable("crusadecraft.entry.unknown");
                subtitle = Component.empty();
                color = 0xBFB7A8;
            } else {
                title = AiNames.realm(r.rank(), r.place());
                subtitle = Component.translatable("crusadecraft.entry.ruler", r.ruler().name(),
                        Component.translatable(r.rank().holderKey(r.ruler().female())));
                color = r.color();
            }
        } else if (human.isPresent()) {
            Realm r = human.get();
            title = Component.literal(r.name());
            boolean mine = RealmService.realmOf(server, player.getUUID()).map(m -> m.id().equals(r.id())).orElse(false);
            subtitle = mine ? Component.translatable("crusadecraft.entry.yours")
                    : Component.translatable("crusadecraft.entry.ruler", r.rulerName(), Component.translatable(r.rank().holderKey(r.feminineTitle())));
            color = r.color();
        } else {
            return;
        }
        MutableComponent colored = title.copy().withStyle(s -> s.withColor(TextColor.fromRgb(readable(color))));
        if (mode == Mode.BARRE) {
            player.displayClientMessage(Component.translatable("crusadecraft.entry.bar", colored, subtitle), true);
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 14));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(colored));
    }

    /** Une couleur de royaume trop sombre pour se lire sur le monde est éclaircie. */
    static int readable(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        if (lum >= 90) return rgb & 0xFFFFFF;
        double k = 90.0 / Math.max(1, lum);
        return (Math.min(255, (int) (r * k + 40)) << 16) | (Math.min(255, (int) (g * k + 40)) << 8) | Math.min(255, (int) (b * k + 40));
    }
}
