package fr.crusadecraft.client.dev;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.client.screen.FoundingScreen;
import fr.crusadecraft.client.screen.RealmScreen;
import fr.crusadecraft.client.screen.RegistreScreen;
import fr.crusadecraft.client.toast.CrusadeToast;
import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.logic.Heraldry;
import fr.crusadecraft.core.realm.RealmState;
import fr.crusadecraft.core.realm.RealmView;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Banc d'essai visuel, réservé au développement ({@code ./gradlew runUiShots}).
 *
 * <p>Depuis l'écran titre, ouvre chaque écran de CrusadeCraft avec des données d'exemple, en fait une
 * capture dans {@code run/screenshots/}, puis ferme le jeu. Aucun monde n'est chargé, rien n'est
 * enregistré. Inactif sans la propriété système {@code crusadecraft.uishots}.
 */
@Mod.EventBusSubscriber(modid = CrusadeCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class UiShots {
    private static final boolean ENABLED = Boolean.getBoolean("crusadecraft.uishots");
    private static final int SETTLE_TICKS = 25;

    private record Shot(String name, Supplier<Screen> screen, Runnable before) {}

    private static List<Shot> shots;
    private static boolean started;
    private static int index = -1;
    private static int wait;

    private UiShots() {}

    /** Écrans à capturer. Chaque phase y ajoute les siens. */
    private static List<Shot> buildShots() {
        RealmView view = sampleView();
        List<Shot> list = new ArrayList<>();
        list.add(new Shot("fondation", () -> new FoundingScreen(BlockPos.ZERO, view.capital(), "Romain"), null));
        list.add(new Shot("menu_royaume", () -> RealmScreen.withTab(view, RealmScreen.Tab.ROYAUME), null));
        list.add(new Shot("menu_conseil_vide", () -> RealmScreen.withTab(view, RealmScreen.Tab.CONSEIL), null));
        list.add(new Shot("registre", () -> new RegistreScreen(view), null));
        list.add(new Shot("notification", TitleScreen::new, () -> Minecraft.getInstance().getToasts().addToast(
                new CrusadeToast(ToastPacket.Kind.REALM, Component.translatable("crusadecraft.toast.founded.title"),
                        Component.translatable("crusadecraft.toast.founded.body", view.name())))));
        return list;
    }

    public static RealmView sampleView() {
        ResourceLocation ow = ResourceLocation.tryParse("minecraft:overworld");
        List<ColonyInfo> colonies = List.of(
                new ColonyInfo(new ColonyRef(ow, 1), "Valbrume", 3, 24, 30, 7.4, 31, Heraldry.AZUR.rgb()),
                new ColonyInfo(new ColonyRef(ow, 2), "Hautemarche", 2, 11, 16, 5.1, 14, Heraldry.SINOPLE.rgb()),
                new ColonyInfo(new ColonyRef(ow, 3), "Pierrefeu", 1, 6, 8, 3.2, 7, Heraldry.GUEULES.rgb()));
        return new RealmView(UUID.randomUUID(), "Royaume de Valbrume", "Par le fer et la foi", "Aubépine",
                Heraldry.AZUR.rgb(), "Romain", false, TitleRank.BARONNIE, RealmState.ACTIVE, 12, 57,
                1250, 50, 12, 3, colonies);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started) {
            // Au premier lancement, Minecraft montre l'écran d'accessibilité au lieu de l'écran titre.
            if (mc.screen != null && mc.level == null && mc.getOverlay() == null) {
                started = true;
                shots = buildShots();
                wait = 60; // laisser le jeu finir de charger
            }
            return;
        }
        if (--wait > 0) return;
        if (index >= 0 && index < shots.size()) {
            String file = "crusadecraft_" + String.format("%02d", index + 1) + "_" + shots.get(index).name() + ".png";
            Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), msg -> CrusadeCraft.LOGGER.info("UiShots : {}", msg.getString()));
        }
        index++;
        if (index < shots.size()) {
            Shot shot = shots.get(index);
            mc.setScreen(shot.screen().get());
            if (shot.before() != null) shot.before().run();
            wait = SETTLE_TICKS;
        } else {
            CrusadeCraft.LOGGER.info("UiShots : terminé, fermeture du jeu");
            mc.stop();
        }
    }
}
