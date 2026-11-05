package fr.crusadecraft.client.dev;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.city.CityPanel;
import fr.crusadecraft.core.city.WorldEconomy;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.WorldPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * -Pshots_open=dossier (P59) : ouvre une COPIE d'un vrai monde avec la version en cours, la fait vivre trois jours et note ce qu'il contient avant et après.
 * C'est ce qui s'approche le plus d'une partie du joueur sans toucher à ses mondes : la copie est dans run-worldshots/saves, et jetable.
 */
final class WorldShotsCopy {
    private static int index, wait;

    private WorldShotsCopy() {}

    private static void log(String text, Object... args) {
        CrusadeCraft.LOGGER.info("WorldShots copie : " + text, args);
    }

    /** Les questions que pose le jeu à l'ouverture d'un monde venu d'ailleurs : c'est une copie, on répond oui. */
    static void answer(Minecraft mc) {
        if (mc.screen == null) return;
        List<AbstractButton> buttons = new ArrayList<>();
        for (GuiEventListener c : mc.screen.children()) if (c instanceof AbstractButton b) buttons.add(b);
        int pick = mc.screen instanceof BackupConfirmScreen ? 1 : mc.screen instanceof ConfirmScreen ? 0 : -1;
        if (pick >= 0 && pick < buttons.size()) {
            log("à {} : on presse « {} »", mc.screen.getClass().getSimpleName(), buttons.get(pick).getMessage().getString());
            buttons.get(pick).onPress();
        }
    }

    /** Ce qu'il y a dans le monde : si le joueur a perdu quelque chose, ça se voit ici. */
    private static void census(MinecraftServer server, String when) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        var persons = data.lineage().persons();
        int alive = 0;
        for (var p : persons) if (p.alive()) alive++;
        log("{} : {} royaumes de joueurs, {} royaumes de l'ordinateur, {} constructions, {} marchés, {} lignes, {} au registre dont {} vivants, jour {}", when, data.realms().size(), data.world().realms().size(), data.world().sites().size(), data.world().markets().size(),
                data.world().lines().size(), persons.size(), alive, fr.crusadecraft.core.realm.RealmService.currentDay(server));
        for (Realm r : data.realms()) log("  royaume {} : souverain {}, {} colonies, {} pièces", r.name(), r.ruler(), r.allColonies().size(), r.gold());
        for (Site s : data.world().sites()) log("  {} : {} bâtiments, bâtie {}", s.name(), s.buildings().size(), s.built());
    }

    static boolean tick(Minecraft mc, BiConsumer<Minecraft, String> grab) {
        if (wait > 0) {
            wait--;
            return false;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        ServerPlayer player = server == null || server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
        if (player == null) return true;
        switch (index++) {
            case 0 -> {
                server.execute(() -> census(server, "à l'ouverture"));
                grab.accept(mc, "copie_arrivee");
                wait = 40;
            }
            case 1 -> {
                server.execute(() -> WorldEconomy.send(player, false));
                wait = 60;
            }
            case 2 -> {
                grab.accept(mc, "copie_economie");
                mc.setScreen(null);
                CrusadeNetwork.toServer(new WorldPackets.RequestMap(true));
                wait = 80;
            }
            case 3 -> {
                grab.accept(mc, "copie_carte");
                mc.setScreen(null);
                server.execute(() -> {
                    List<Site> sites = new ArrayList<>(CrusadeSavedData.get(server).world().sites());
                    if (!sites.isEmpty()) CityPanel.open(player, sites.get(0));
                });
                wait = 60;
            }
            case 4 -> {
                grab.accept(mc, "copie_panneau");
                mc.setScreen(null);
                wait = 1200;
            }
            // Trois matins de suite, sans rien arranger d'autre que l'heure : tout ce qui se passe chaque jour passe sur ses vraies données.
            case 5, 6, 7 -> {
                server.execute(() -> {
                    ServerLevel level = server.getLevel(Level.OVERWORLD);
                    if (level != null) level.setDayTime((level.getDayTime() / 24000L + 1) * 24000L + 1000);
                });
                wait = 400;
            }
            case 8 -> {
                server.execute(() -> census(server, "trois jours plus tard"));
                grab.accept(mc, "copie_fin");
                wait = 40;
            }
            default -> {
                return true;
            }
        }
        return false;
    }
}
