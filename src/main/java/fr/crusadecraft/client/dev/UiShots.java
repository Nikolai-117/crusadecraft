package fr.crusadecraft.client.dev;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.client.screen.CharacterScreen;
import fr.crusadecraft.client.screen.CouncilScreen;
import fr.crusadecraft.client.screen.DialogueScreen;
import fr.crusadecraft.client.screen.FoundingScreen;
import fr.crusadecraft.client.screen.MayorScreen;
import fr.crusadecraft.client.screen.RealmScreen;
import fr.crusadecraft.client.screen.RegistreScreen;
import fr.crusadecraft.client.screen.RequestScreen;
import fr.crusadecraft.client.screen.TreasuryScreen;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.law.LawsView;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.treasury.TreasuryView;
import fr.crusadecraft.client.toast.CrusadeToast;
import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.ColonyReport;
import fr.crusadecraft.core.council.CouncilTask;
import fr.crusadecraft.core.council.CouncilView;
import fr.crusadecraft.core.council.MayorView;
import fr.crusadecraft.core.council.RequestType;
import fr.crusadecraft.core.council.RequestView;
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
        list.add(new Shot("conseil", () -> new CouncilScreen(sampleCouncil(true)), null));
        list.add(new Shot("conseil_sans_table", () -> new CouncilScreen(sampleCouncil(false)), null));
        list.add(new Shot("bureau_maire", () -> new MayorScreen(sampleMayor(true)), null));
        list.add(new Shot("bureau_sans_maire", () -> new MayorScreen(sampleMayor(false)), null));
        list.add(new Shot("requete", () -> new RequestScreen(sampleRequest()), null));
        list.add(new Shot("tresor_coffre", () -> new TreasuryScreen(sampleTreasury(1250), BlockPos.ZERO), null));
        list.add(new Shot("tresor_dette", () -> new TreasuryScreen(sampleTreasury(-40), BlockPos.ZERO), null));
        list.add(new Shot("menu_tresor", () -> {
            RealmScreen s = RealmScreen.withTab(view, RealmScreen.Tab.TRESOR);
            s.setTreasury(sampleTreasury(1250));
            return s;
        }, null));
        list.add(new Shot("menu_lois", () -> {
            RealmScreen s = RealmScreen.withTab(view, RealmScreen.Tab.LOIS);
            s.setLaws(sampleLaws());
            s.select(Law.FISCALITE, 2);
            return s;
        }, null));
        list.add(new Shot("objets", () -> new ItemShowcaseScreen(List.of(new ItemStack(ModItems.THRONE.get()),
                new ItemStack(ModItems.REGISTRE.get()), new ItemStack(ModItems.SCEPTRE.get()),
                new ItemStack(ModItems.COUNCIL_TABLE.get()), new ItemStack(ModItems.COUNCIL_SEAT.get()),
                new ItemStack(ModItems.MAYOR_DESK.get()), new ItemStack(ModItems.TREASURY.get()))), null));
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

    private static final ResourceLocation OW = ResourceLocation.tryParse("minecraft:overworld");

    private static List<CouncilView.RequestSummary> sampleRequests() {
        return List.of(
                new CouncilView.RequestSummary(UUID.randomUUID(), RequestType.MAYOR_HOMELESS, "Ysolde", "Valbrume", 4, 56),
                new CouncilView.RequestSummary(UUID.randomUUID(), RequestType.SPY_RAID_WARNING, "Gautier", "Hautemarche", 0, 57));
    }

    private static CouncilView sampleCouncil(boolean table) {
        ColonyRef capital = new ColonyRef(OW, 1);
        List<CouncilView.Seat> seats = List.of(
                new CouncilView.Seat(Post.CHANCELIER, null, "", false, 0, 0, CharacterStatus.LIBRE, CouncilTask.GERER_LA_COUR),
                new CouncilView.Seat(Post.MARECHAL, new CitizenRef(capital, 7), "Aldric", false, 13, 51, CharacterStatus.LIBRE, CouncilTask.ENTRAINER_TROUPES),
                new CouncilView.Seat(Post.INTENDANT, new CitizenRef(capital, 9), "Berthe", true, 11, 18, CharacterStatus.LIBRE, CouncilTask.GERER_LE_DOMAINE),
                new CouncilView.Seat(Post.MAITRE_ESPION, new CitizenRef(capital, 4), "Odon", false, 8, -12, CharacterStatus.LIBRE, CouncilTask.SURVEILLER_LES_TERRES),
                new CouncilView.Seat(Post.CHAPELAIN, new CitizenRef(new ColonyRef(OW, 2), 3), "Gautier", false, 9, -32, CharacterStatus.LIBRE, CouncilTask.BONHEUR_DU_PEUPLE));
        return new CouncilView("Royaume de Valbrume", table, table ? "Valbrume" : "", 9, true, table,
                seats, table ? sampleRequests() : List.of(), null, 1250, 12, 1.15);
    }

    private static MayorView sampleMayor(boolean withMayor) {
        ColonyRef colony = new ColonyRef(OW, 1);
        ColonyReport report = new ColonyReport(colony, "Valbrume", 24, 30, 4, 2, 1, 0, 5, 7.4, true, false);
        MayorView.Person mayor = new MayorView.Person(new CitizenRef(colony, 12), "Ysolde", true, 11, 9);
        List<MayorView.Person> candidates = List.of(
                new MayorView.Person(new CitizenRef(colony, 9), "Berthe", true, 11, 18),
                new MayorView.Person(new CitizenRef(colony, 15), "Hugues", false, 8, 4),
                new MayorView.Person(new CitizenRef(colony, 21), "Mahaut", true, 6, -7));
        return new MayorView(colony, "Valbrume", true, withMayor ? mayor : null, report,
                withMayor ? List.of() : candidates, 13, withMayor, withMayor ? sampleRequests().subList(0, 1) : List.of());
    }

    private static RequestView sampleRequest() {
        RequestType type = RequestType.MAYOR_SICK;
        List<RequestView.ChoiceView> choices = new ArrayList<>();
        for (RequestType.Choice c : type.choices()) {
            int gold = fr.crusadecraft.core.council.RequestService.goldCost(c);
            int prestige = fr.crusadecraft.core.council.RequestService.prestigeCost(c);
            choices.add(new RequestView.ChoiceView(c, true, gold, prestige));
        }
        return new RequestView(UUID.randomUUID(), type, "Ysolde", true, Post.MAIRE, "Valbrume", 3, -1, false, choices);
    }

    private static TreasuryView sampleTreasury(long gold) {
        List<TreasuryView.ColonyLine> colonies = List.of(
                new TreasuryView.ColonyLine("Valbrume", true, 7.4, 31, 58, 6, false),
                new TreasuryView.ColonyLine("Hautemarche", false, 5.1, 14, 17, 2, false),
                new TreasuryView.ColonyLine("Pierrefeu", false, 3.2, 7, 4, 0, true));
        List<TreasuryView.Entry> today = List.of(
                new TreasuryView.Entry(LedgerCategory.IMPOTS, 76),
                new TreasuryView.Entry(LedgerCategory.DOMAINE, 9),
                new TreasuryView.Entry(LedgerCategory.DEPOTS, 81),
                new TreasuryView.Entry(LedgerCategory.SALAIRES, -13),
                new TreasuryView.Entry(LedgerCategory.ENTRETIEN, -8),
                new TreasuryView.Entry(LedgerCategory.REQUETES, -5));
        List<TreasuryView.DayLine> history = new ArrayList<>();
        long[][] days = {{62, -21}, {70, -19}, {0, 0}, {151, -24}, {74, -30}, {81, -21}, {166, -26}};
        for (int i = 0; i < days.length; i++) history.add(new TreasuryView.DayLine(51 + i, days[i][0], days[i][1]));
        return new TreasuryView("Royaume de Valbrume", 57, gold, 200, true, 1, 1.0, 0.24, 0.1, 0.8, colonies, 9, 13,
                today, history, fr.crusadecraft.core.treasury.GoldValues.parse(fr.crusadecraft.core.treasury.GoldValues.DEFAULTS).denominations(), true);
    }

    private static LawsView sampleLaws() {
        List<LawsView.LawLine> laws = new ArrayList<>();
        laws.add(new LawsView.LawLine(Law.FISCALITE, 1, 0, List.of(
                new LawsView.LevelOption(0, true, List.of("Aldric", "Berthe", "Odon"), List.of(), 0),
                new LawsView.LevelOption(1, true, List.of(), List.of(), 0),
                new LawsView.LevelOption(2, true, List.of("Aldric"), List.of("Berthe", "Odon", "Gautier"), 60))));
        laws.add(new LawsView.LawLine(Law.AUTORITE, 1, 2, List.of(
                new LawsView.LevelOption(0, true, List.of("Odon", "Gautier"), List.of("Aldric"), 0),
                new LawsView.LevelOption(1, true, List.of(), List.of(), 0),
                new LawsView.LevelOption(2, true, List.of("Aldric"), List.of("Odon", "Gautier"), 40),
                new LawsView.LevelOption(3, false, List.of(), List.of(), 0))));
        laws.add(new LawsView.LawLine(Law.CODE_CHEVALERESQUE, 0, 0, List.of(
                new LawsView.LevelOption(0, true, List.of(), List.of(), 0),
                new LawsView.LevelOption(1, false, List.of(), List.of(), 0))));
        laws.add(new LawsView.LawLine(Law.JUSTICE_ROYALE, 1, 0, List.of(
                new LawsView.LevelOption(0, true, List.of("Odon"), List.of("Aldric", "Berthe"), 40),
                new LawsView.LevelOption(1, true, List.of(), List.of(), 0))));
        return new LawsView(50, 4, laws);
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
