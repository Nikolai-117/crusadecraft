package fr.crusadecraft.client.dev;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.client.screen.CharacterScreen;
import fr.crusadecraft.client.screen.DialogueScreen;
import fr.crusadecraft.client.screen.FoundingScreen;
import fr.crusadecraft.client.screen.RealmScreen;
import fr.crusadecraft.client.screen.RegistreScreen;
import fr.crusadecraft.client.toast.CrusadeToast;
import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CharacterView;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Education;
import fr.crusadecraft.core.character.HistoryEntry;
import fr.crusadecraft.core.character.Opinion;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Stat;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.logic.Heraldry;
import fr.crusadecraft.core.realm.RealmState;
import fr.crusadecraft.core.realm.RealmView;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.network.packet.ToastPacket;
import fr.crusadecraft.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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
        CharacterView character = sampleCharacter();
        list.add(new Shot("fiche_personnage", () -> new CharacterScreen(character, false), null));
        list.add(new Shot("fiche_nomination", () -> new CharacterScreen(character, true), null));
        list.add(new Shot("dialogue", () -> new DialogueScreen(character), null));
        RealmView withCouncil = withSeats(view);
        list.add(new Shot("menu_conseil", () -> RealmScreen.withTab(withCouncil, RealmScreen.Tab.CONSEIL), null));
        list.add(new Shot("objets", () -> new ItemShowcaseScreen(List.of(new ItemStack(ModItems.THRONE.get()),
                new ItemStack(ModItems.REGISTRE.get()), new ItemStack(ModItems.SCEPTRE.get()))), null));
        list.add(new Shot("notification", TitleScreen::new, () -> Minecraft.getInstance().getToasts().addToast(
                new CrusadeToast(ToastPacket.Kind.REALM, Component.translatable("crusadecraft.toast.founded.title"),
                        Component.translatable("crusadecraft.toast.founded.body", view.name())))));
        return list;
    }

    private static CharacterView sampleCharacter() {
        ResourceLocation ow = ResourceLocation.tryParse("minecraft:overworld");
        CitizenRef ref = new CitizenRef(new ColonyRef(ow, 1), 7);
        int[] skills = {22, 14, 31, 9, 18, 6, 12, 7, 15, 20, 17};
        List<Trait> traits = List.of(Trait.BRAVE, Trait.AVARE, Trait.LOYAL);
        int[] base = {7, 13, 9, 6, 5};
        int[] stats = {7, 18, 9, 6, 5};
        List<Opinion.Line> lines = List.of(new Opinion.Line("crusadecraft.opinion.trait", Trait.LOYAL.key(), 20),
                new Opinion.Line("crusadecraft.opinion.happiness", null, 7),
                new Opinion.Line("crusadecraft.opinion.holds_post", null, 10),
                new Opinion.Line(OpinionReason.RECOMPENSE.key(), null, 14));
        List<HistoryEntry> history = List.of(
                new HistoryEntry(55, "crusadecraft.history.rewarded", List.of()),
                new HistoryEntry(41, "crusadecraft.history.appointed", List.of("crusadecraft.post.marechal.m")),
                new HistoryEntry(40, "crusadecraft.history.refused_post", List.of("crusadecraft.post.chancelier.m")));
        List<CharacterView.PostSlot> slots = new ArrayList<>();
        for (Post p : Post.values()) {
            slots.add(new CharacterView.PostSlot(p, p == Post.MARECHAL ? "Aldric" : p == Post.INTENDANT ? "Berthe" : "", p == Post.MARECHAL));
        }
        return new CharacterView(ref, "Aldric", false, false, "com.minecolonies.job.knight", "Valbrume", "Royaume de Valbrume",
                Post.MARECHAL, CharacterStatus.LIBRE, -1, false, skills, base, stats, new Education(Stat.MARTIAL, 3),
                traits, true, 51, lines, 40, 7.3, history, -1, true, false, 57, slots,
                fr.crusadecraft.core.character.CharacterService.sources(fr.crusadecraft.core.character.StatFormula.defaults()));
    }

    private static RealmView withSeats(RealmView v) {
        ResourceLocation ow = ResourceLocation.tryParse("minecraft:overworld");
        List<RealmView.Seat> seats = new ArrayList<>();
        seats.add(new RealmView.Seat(Post.CHANCELIER, null, "", false, 0, ""));
        seats.add(new RealmView.Seat(Post.MARECHAL, new CitizenRef(new ColonyRef(ow, 1), 7), "Aldric", false, 51, ""));
        seats.add(new RealmView.Seat(Post.INTENDANT, new CitizenRef(new ColonyRef(ow, 1), 9), "Berthe", true, 18, ""));
        seats.add(new RealmView.Seat(Post.MAITRE_ESPION, null, "", false, 0, ""));
        seats.add(new RealmView.Seat(Post.CHAPELAIN, new CitizenRef(new ColonyRef(ow, 2), 3), "Gautier", false, -32, ""));
        seats.add(new RealmView.Seat(Post.MAIRE, new CitizenRef(new ColonyRef(ow, 1), 12), "Ysolde", true, 9, "Valbrume"));
        seats.add(new RealmView.Seat(Post.MAIRE, null, "", false, 0, "Hautemarche"));
        seats.add(new RealmView.Seat(Post.MAIRE, null, "", false, 0, "Pierrefeu"));
        return new RealmView(v.id(), v.name(), v.motto(), v.house(), v.color(), v.rulerName(), v.feminineTitle(), v.rank(),
                v.state(), v.foundedDay(), v.currentDay(), v.gold(), v.prestige(), v.piety(), v.dread(), v.colonies(), seats);
    }

    public static RealmView sampleView() {
        ResourceLocation ow = ResourceLocation.tryParse("minecraft:overworld");
        List<ColonyInfo> colonies = List.of(
                new ColonyInfo(new ColonyRef(ow, 1), "Valbrume", 3, 24, 30, 7.4, 31, Heraldry.AZUR.rgb()),
                new ColonyInfo(new ColonyRef(ow, 2), "Hautemarche", 2, 11, 16, 5.1, 14, Heraldry.SINOPLE.rgb()),
                new ColonyInfo(new ColonyRef(ow, 3), "Pierrefeu", 1, 6, 8, 3.2, 7, Heraldry.GUEULES.rgb()));
        return new RealmView(UUID.randomUUID(), "Royaume de Valbrume", "Par le fer et la foi", "Aubépine",
                Heraldry.AZUR.rgb(), "Romain", false, TitleRank.BARONNIE, RealmState.ACTIVE, 12, 57,
                1250, 50, 12, 3, colonies, List.of());
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
