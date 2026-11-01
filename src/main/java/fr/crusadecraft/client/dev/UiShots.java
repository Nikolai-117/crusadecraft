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
import fr.crusadecraft.client.screen.MapScreen;
import fr.crusadecraft.client.screen.AiRealmScreen;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiRealmView;
import fr.crusadecraft.core.world.MapView;
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
        MapView map = sampleMap();
        list.add(new Shot("carte_politique", () -> new MapScreen(map), null));
        list.add(new Shot("carte_dejure", () -> {
            MapScreen s = new MapScreen(map);
            s.toggleDejureForPreview();
            return s;
        }, null));
        list.add(new Shot("fiche_royaume_ia", () -> new AiRealmScreen(sampleAiRealm()), null));
        fr.crusadecraft.core.diplomacy.DiplomacyView diplo = sampleDiplomacy();
        list.add(new Shot("colombier", () -> new fr.crusadecraft.client.screen.DiplomacyScreen(diplo), null));
        list.add(new Shot("lettre", () -> new fr.crusadecraft.client.screen.LetterScreen(sampleLetter()), null));
        list.add(new Shot("chancellerie", () -> new fr.crusadecraft.client.screen.ChanceryScreen(sampleChancery()), null));
        list.add(new Shot("menu_diplomatie", () -> {
            RealmScreen s = RealmScreen.withTab(view, RealmScreen.Tab.DIPLOMATIE);
            s.setDiplomacy(diplo);
            return s;
        }, null));
        list.add(new Shot("menu_vassaux", () -> {
            RealmScreen s = RealmScreen.withTab(view, RealmScreen.Tab.VASSAUX);
            s.setChancery(sampleChancery());
            return s;
        }, null));
        fr.crusadecraft.core.war.WarView war = sampleWar();
        list.add(new Shot("table_guerre", () -> {
            fr.crusadecraft.client.screen.WarScreen s = new fr.crusadecraft.client.screen.WarScreen(war);
            s.select(war.wars().get(0).id());
            return s;
        }, null));
        list.add(new Shot("table_guerre_cible", () -> {
            fr.crusadecraft.client.screen.WarScreen s = new fr.crusadecraft.client.screen.WarScreen(war);
            s.select(war.targets().get(1).party().id());
            return s;
        }, null));
        list.add(new Shot("table_guerre_allie", () -> {
            fr.crusadecraft.client.screen.WarScreen s = new fr.crusadecraft.client.screen.WarScreen(war);
            s.select(war.targets().get(2).party().id());
            return s;
        }, null));
        list.add(new Shot("menu_guerre", () -> {
            RealmScreen s = RealmScreen.withTab(view, RealmScreen.Tab.GUERRE);
            s.setWar(war);
            return s;
        }, null));
        list.add(new Shot("menu_chroniques", () -> {
            RealmScreen s = RealmScreen.withTab(view, RealmScreen.Tab.CHRONIQUES);
            s.setNews(sampleNews(), sampleChronicle());
            return s;
        }, null));
        list.add(new Shot("evenement", () -> new fr.crusadecraft.client.screen.EventScreen(sampleEvent()), null));
        list.add(new Shot("intrigues", () -> new fr.crusadecraft.client.screen.IntrigueScreen(sampleIntrigue()), null));
        list.add(new Shot("lice_tournoi", () -> new fr.crusadecraft.client.screen.TournamentScreen(sampleTournament()), null));
        list.add(new Shot("maison", () -> new fr.crusadecraft.client.screen.HouseScreen(sampleHouse()), null));
        list.add(new Shot("maison_mariage", () -> {
            fr.crusadecraft.client.screen.HouseScreen s = new fr.crusadecraft.client.screen.HouseScreen(sampleHouse());
            s.preview(0, true);
            return s;
        }, null));
        MapView edit = sampleEditMap();
        list.add(new Shot("carte_editeur", () -> {
            MapScreen s = new MapScreen(edit);
            s.previewEditor(0, 7);
            return s;
        }, null));
        list.add(new Shot("carte_editeur_pinceau", () -> {
            MapScreen s = new MapScreen(edit);
            s.previewEditor(3, -1);
            return s;
        }, null));
        list.add(new Shot("carte_editeur_royaume", () -> fr.crusadecraft.client.screen.MapEditForm.newRealm(new MapScreen(edit), 420, -980,
                List.of(Heraldry.AZUR.rgb(), Heraldry.GUEULES.rgb())), null));
        list.add(new Shot("carte_editeur_ville", () -> fr.crusadecraft.client.screen.MapEditForm.newCity(new MapScreen(edit), edit.territories().get(2),
                fr.crusadecraft.core.world.AiNames.realm(TitleRank.DUCHE, "Rochegarde"), 1050, -820), null));
        fr.crusadecraft.core.lineage.LineageView lineage = sampleLineage(true);
        list.add(new Shot("registre_lignees", () -> {
            fr.crusadecraft.client.screen.LineageScreen s = new fr.crusadecraft.client.screen.LineageScreen(lineage);
            s.previewPerson(2);
            return s;
        }, null));
        list.add(new Shot("registre_communaute", () -> new fr.crusadecraft.client.screen.LineageScreen(sampleLineage(false)), null));
        list.add(new Shot("registre_nouvelle_maison", () -> fr.crusadecraft.client.screen.LineageForm.newHouse(
                new fr.crusadecraft.client.screen.LineageScreen(lineage), lineage.realms()), null));
        fr.crusadecraft.core.place.LieuView lieux = sampleLieux();
        list.add(new Shot("lieux", () -> new fr.crusadecraft.client.screen.LieuxScreen(lieux), null));
        list.add(new Shot("lieux_nouveau", () -> fr.crusadecraft.client.screen.LieuForm.create(
                new fr.crusadecraft.client.screen.LieuxScreen(lieux), null, BlockPos.ZERO, lieux.selection().id()), null));
        list.add(new Shot("lieux_fiche", () -> new fr.crusadecraft.client.screen.LieuTextScreen(new fr.crusadecraft.client.screen.LieuxScreen(lieux),
                lieux.selection().id(), lieux.selection().name(), lieux.selection().text()), null));
        list.add(new Shot("livre_chroniques", () -> {
            List<fr.crusadecraft.network.packet.WorldPackets.NewsLine> lines = new ArrayList<>(sampleChronicle());
            java.util.Collections.reverse(lines);
            // La page des faits, pas celle du titre : on tourne la page une fois l'écran prêt.
            return new net.minecraft.client.gui.screens.inventory.BookViewScreen(
                    new fr.crusadecraft.client.screen.ChronicleBook("Royaume de Valbrume", "Aubépine", lines)) {
                @Override
                protected void init() {
                    super.init();
                    setPage(1);
                }
            };
        }, null));
        list.add(new Shot("notification_coalition", TitleScreen::new, () -> Minecraft.getInstance().getToasts().addToast(
                new CrusadeToast(ToastPacket.Kind.ALERT, Component.translatable("crusadecraft.toast.coalition.title"),
                        Component.translatable("crusadecraft.toast.coalition.body", 3)))));
        list.add(new Shot("notification_raid", TitleScreen::new, () -> Minecraft.getInstance().getToasts().addToast(
                new CrusadeToast(ToastPacket.Kind.ALERT, Component.translatable("crusadecraft.toast.war.raid.title"),
                        Component.translatable("crusadecraft.toast.war.raid.body", fr.crusadecraft.core.world.AiNames.realmOf(TitleRank.COMTE, "Ambrevent"),
                                "Hautemarche", 6)))));
        list.add(new Shot("objets", () -> new ItemShowcaseScreen(List.of(new ItemStack(ModItems.THRONE.get()),
                new ItemStack(ModItems.REGISTRE.get()), new ItemStack(ModItems.SCEPTRE.get()),
                new ItemStack(ModItems.COUNCIL_TABLE.get()), new ItemStack(ModItems.COUNCIL_SEAT.get()),
                new ItemStack(ModItems.MAYOR_DESK.get()), new ItemStack(ModItems.TREASURY.get()),
                new ItemStack(ModItems.WAR_MAP.get()), new ItemStack(ModItems.BOUNDARY_STONE.get()))), null));
        list.add(new Shot("objets_cour", () -> new ItemShowcaseScreen(List.of(new ItemStack(ModItems.TOURNAMENT.get()),
                new ItemStack(ModItems.CHRONIQUES.get()))), null));
        list.add(new Shot("objets_diplomatie", () -> new ItemShowcaseScreen(List.of(new ItemStack(ModItems.COLOMBIER.get()),
                new ItemStack(ModItems.CHANCERY.get()), new ItemStack(ModItems.SEAL.get()), new ItemStack(ModItems.LETTER.get()),
                new ItemStack(ModItems.CLAIM_SCROLL.get()))), null));
        list.add(new Shot("objets_territoire", () -> new ItemShowcaseScreen(List.of(new ItemStack(ModItems.BORNAGE.get()),
                new ItemStack(ModItems.BOUNDARY_STONE.get()))), null));
        list.add(new Shot("notification", TitleScreen::new, () -> Minecraft.getInstance().getToasts().addToast(
                new CrusadeToast(ToastPacket.Kind.REALM, Component.translatable("crusadecraft.toast.founded.title"),
                        Component.translatable("crusadecraft.toast.founded.body", view.name())))));
        // Les écrans des phases 19 à 30.
        for (UiShotsV3.Scene s : UiShotsV3.scenes(map, war, sampleHouse())) list.add(new Shot(s.name(), s.screen(), null));
        // -Pshots_only=v3_,carte : seulement les captures dont le nom contient l'un de ces morceaux.
        String only = System.getProperty("crusadecraft.uishots.only", "").trim();
        if (!only.isEmpty()) {
            List<String> parts = java.util.Arrays.stream(only.split(",")).map(String::trim).filter(p -> !p.isEmpty()).toList();
            list.removeIf(shot -> parts.stream().noneMatch(p -> shot.name().contains(p)));
        }
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

    private static fr.crusadecraft.core.diplomacy.DiplomacyView sampleDiplomacy() {
        java.util.Map<fr.crusadecraft.core.diplomacy.DiplomacyRules.Proposal, fr.crusadecraft.core.diplomacy.DiplomacyRules.Verdict> verdicts =
                new java.util.EnumMap<>(fr.crusadecraft.core.diplomacy.DiplomacyRules.Proposal.class);
        for (var p : fr.crusadecraft.core.diplomacy.DiplomacyRules.Proposal.values()) {
            boolean ok = p.threshold() <= 20;
            verdicts.put(p, new fr.crusadecraft.core.diplomacy.DiplomacyRules.Verdict(ok, ok ? 12 : -18, List.of(
                    new fr.crusadecraft.core.diplomacy.DiplomacyRules.Factor("crusadecraft.diplo.f.opinion", 22),
                    new fr.crusadecraft.core.diplomacy.DiplomacyRules.Factor("crusadecraft.diplo.f.threshold", -p.threshold())), null));
        }
        verdicts.put(fr.crusadecraft.core.diplomacy.DiplomacyRules.Proposal.COMMERCE,
                fr.crusadecraft.core.diplomacy.DiplomacyRules.Verdict.blocked("crusadecraft.diplo.block.already"));
        java.util.Map<fr.crusadecraft.core.diplomacy.Treaty, Long> treaties = new java.util.EnumMap<>(fr.crusadecraft.core.diplomacy.Treaty.class);
        treaties.put(fr.crusadecraft.core.diplomacy.Treaty.COMMERCE, 31L);
        List<Opinion.Line> lines = List.of(new Opinion.Line("crusadecraft.diplo.op.personality", "crusadecraft.personality.marchand", 5),
                new Opinion.Line("crusadecraft.treaty.commerce", null, 20), new Opinion.Line("crusadecraft.opinion.cadeau", null, 9),
                new Opinion.Line("crusadecraft.diplo.op.rank", null, -5));
        List<fr.crusadecraft.core.diplomacy.DiplomacyView.Entry> entries = new ArrayList<>();
        entries.add(new fr.crusadecraft.core.diplomacy.DiplomacyView.Entry(UUID.randomUUID(), true, "Rochegarde", TitleRank.DUCHE,
                Heraldry.SINOPLE.rgb(), "Mathilde", true, AiPersonality.MARCHAND, fr.crusadecraft.core.diplomacy.DiploStatus.COMMERCE, 29,
                lines, treaties, 20, 20, false, false, 0, 1.3, "", verdicts));
        entries.add(new fr.crusadecraft.core.diplomacy.DiplomacyView.Entry(UUID.randomUUID(), true, "Ambrevent", TitleRank.ROYAUME,
                Heraldry.GUEULES.rgb(), "Godefroy", false, AiPersonality.EXPANSIONNISTE, fr.crusadecraft.core.diplomacy.DiploStatus.HOSTILE, -34,
                List.of(), java.util.Map.of(), 20, 20, false, true, 6, 0.4, "", verdicts));
        entries.add(new fr.crusadecraft.core.diplomacy.DiplomacyView.Entry(UUID.randomUUID(), true, "Saulepré", TitleRank.BARONNIE,
                Heraldry.OR.rgb(), "Hugues", false, AiPersonality.PACIFIQUE, fr.crusadecraft.core.diplomacy.DiploStatus.SUZERAIN, 41,
                List.of(), java.util.Map.of(), 15, 10, false, false, 0, 3.2, "", verdicts));
        entries.add(new fr.crusadecraft.core.diplomacy.DiplomacyView.Entry(UUID.randomUUID(), true, "Cendrecourt", TitleRank.COMTE,
                Heraldry.POURPRE.rgb(), "Thibaut", false, AiPersonality.FANATIQUE, fr.crusadecraft.core.diplomacy.DiploStatus.NEUTRE, 3,
                List.of(), java.util.Map.of(), 20, 20, false, false, 0, 1.1, "", verdicts));
        return new fr.crusadecraft.core.diplomacy.DiplomacyView(1250, 140, 2, 1, true, "", null, true, entries);
    }

    private static net.minecraft.nbt.CompoundTag sampleLetter() {
        fr.crusadecraft.core.diplomacy.Letter l = new fr.crusadecraft.core.diplomacy.Letter(UUID.randomUUID(), UUID.randomUUID(), true,
                UUID.randomUUID(), Component.translatable("crusadecraft.letter.sender", "Hugues", Component.translatable("crusadecraft.rank.barony.holder.m"),
                        fr.crusadecraft.core.world.AiNames.realm(TitleRank.BARONNIE, "Saulepré")),
                Component.translatable("crusadecraft.letter.homage.subject"),
                Component.translatable("crusadecraft.letter.homage.body", fr.crusadecraft.core.world.AiNames.realmThe(TitleRank.BARONNIE, "Saulepré"), 15, 10),
                List.of(new fr.crusadecraft.core.diplomacy.Letter.Choice(fr.crusadecraft.core.diplomacy.Letter.Action.SEAL_VASSALAGE, true),
                        new fr.crusadecraft.core.diplomacy.Letter.Choice(fr.crusadecraft.core.diplomacy.Letter.Action.REFUSE, false)), 57, 60, 0);
        return fr.crusadecraft.item.LetterItem.data(fr.crusadecraft.item.LetterItem.create(l));
    }

    private static fr.crusadecraft.core.diplomacy.ChanceryView sampleChancery() {
        List<fr.crusadecraft.core.diplomacy.ChanceryView.Vassal> vassals = List.of(
                new fr.crusadecraft.core.diplomacy.ChanceryView.Vassal(UUID.randomUUID(), true, "Saulepré", TitleRank.BARONNIE, Heraldry.OR.rgb(),
                        1, 15, 10, 41, 4),
                new fr.crusadecraft.core.diplomacy.ChanceryView.Vassal(UUID.randomUUID(), true, "Givrefond", TitleRank.BARONNIE, Heraldry.CELESTE.rgb(),
                        1, 30, 25, -6, 7));
        List<fr.crusadecraft.core.diplomacy.ChanceryView.Claim> claims = List.of(
                new fr.crusadecraft.core.diplomacy.ChanceryView.Claim(4, "Brisemer", "Ambrevent", TitleRank.ROYAUME, 1320));
        List<fr.crusadecraft.core.diplomacy.ChanceryView.Claim> candidates = List.of(
                new fr.crusadecraft.core.diplomacy.ChanceryView.Claim(7, "Cendrecourt", "Cendrecourt", TitleRank.COMTE, 910),
                new fr.crusadecraft.core.diplomacy.ChanceryView.Claim(2, "Ormesval", "Ambrevent", TitleRank.ROYAUME, 1240),
                new fr.crusadecraft.core.diplomacy.ChanceryView.Claim(9, "Saulepré", "Rochegarde", TitleRank.DUCHE, 1410));
        return new fr.crusadecraft.core.diplomacy.ChanceryView("Royaume de Valbrume", TitleRank.COMTE, 3, TitleRank.DUCHE, 4, 600, 300,
                1250, 140, true, "", true, vassals, claims, candidates, 7, 62);
    }

    private static List<fr.crusadecraft.network.packet.WorldPackets.NewsLine> sampleChronicle() {
        return List.of(
                new fr.crusadecraft.network.packet.WorldPackets.NewsLine(57, Component.translatable("crusadecraft.chronicle.event",
                        Component.translatable("crusadecraft.event.famine.title"), Component.translatable("crusadecraft.event.famine.opt.grain"))),
                new fr.crusadecraft.network.packet.WorldPackets.NewsLine(52, Component.translatable("crusadecraft.chronicle.conquest",
                        Component.literal("Ormesval"), fr.crusadecraft.core.world.AiNames.realm(TitleRank.COMTE, "Ambrevent"))),
                new fr.crusadecraft.network.packet.WorldPackets.NewsLine(46, Component.translatable("crusadecraft.chronicle.war.declared",
                        fr.crusadecraft.core.world.AiNames.realm(TitleRank.COMTE, "Ambrevent"), Component.translatable("crusadecraft.cb.claim"))),
                new fr.crusadecraft.network.packet.WorldPackets.NewsLine(31, Component.translatable("crusadecraft.chronicle.law",
                        Component.translatable("crusadecraft.law.fiscalite"), Component.translatable("crusadecraft.law.fiscalite.lourde"))),
                new fr.crusadecraft.network.packet.WorldPackets.NewsLine(14, Component.translatable("crusadecraft.chronicle.appointed",
                        Component.literal("Aldric"), Component.translatable("crusadecraft.post.marechal.m"))),
                new fr.crusadecraft.network.packet.WorldPackets.NewsLine(12, Component.translatable("crusadecraft.chronicle.founded",
                        Component.literal("Romain"), Component.literal("Royaume de Valbrume"), Component.literal("Valbrume"))));
    }

    private static fr.crusadecraft.core.court.TournamentView sampleTournament() {
        return new fr.crusadecraft.core.court.TournamentView(100, 1250, "", 0,
                List.of("Aldric", "Berthe", "Odon", "Bérenger (Royaume d'Ambrevent)", "Aude (Duché de Rochegarde)", "Thibaut (Baronnie de Cendrecourt)"),
                3,
                List.of(Component.translatable("crusadecraft.tournament.bout", 1, "Aldric", "Aude", "Aldric"),
                        Component.translatable("crusadecraft.tournament.bout", 1, "Berthe", "Bérenger", "Bérenger"),
                        Component.translatable("crusadecraft.tournament.bout.wounded", 1, "Odon", "Thibaut", "Odon"),
                        Component.translatable("crusadecraft.tournament.bout", 2, "Aldric", "Bérenger", "Aldric"),
                        Component.translatable("crusadecraft.tournament.bout", 3, "Aldric", "Odon", "Aldric"),
                        Component.translatable("crusadecraft.tournament.champion.mine", "Aldric", "Royaume de Valbrume", 51)));
    }

    private static fr.crusadecraft.core.court.HouseView sampleHouse() {
        ResourceLocation ow = ResourceLocation.tryParse("minecraft:overworld");
        ColonyRef c = new ColonyRef(ow, 1);
        return new fr.crusadecraft.core.court.HouseView(5, 8, 140, 25,
                List.of(new fr.crusadecraft.core.court.HouseView.Member(new CitizenRef(c, 7), "Aldric", false, false, true, "", 0, true),
                        new fr.crusadecraft.core.court.HouseView.Member(new CitizenRef(c, 9), "Berthe", true, false, false, "Hugues", 2, false),
                        new fr.crusadecraft.core.court.HouseView.Member(new CitizenRef(c, 10), "Hugues", false, false, false, "Berthe", 2, false),
                        new fr.crusadecraft.core.court.HouseView.Member(new CitizenRef(c, 21), "Aélis", true, true, false, "", 0, false),
                        new fr.crusadecraft.core.court.HouseView.Member(new CitizenRef(c, 22), "Raoul", false, true, false, "", 0, false)),
                List.of(new fr.crusadecraft.core.court.HouseView.Candidate(new CitizenRef(c, 4), "Odon", "crusadecraft.post.maitre_espion.m"),
                        new fr.crusadecraft.core.court.HouseView.Candidate(new CitizenRef(c, 3), "Gautier", "crusadecraft.post.chapelain.m")),
                List.of(new fr.crusadecraft.core.court.HouseView.Match(UUID.randomUUID(), "Ambrevent", TitleRank.ROYAUME, Heraldry.GUEULES.rgb(),
                                "Ormesval", fr.crusadecraft.core.world.AiFaith.FIDELE, false, "", -12),
                        new fr.crusadecraft.core.court.HouseView.Match(UUID.randomUUID(), "Rochegarde", TitleRank.DUCHE, Heraldry.SINOPLE.rgb(),
                                "Rochegarde", fr.crusadecraft.core.world.AiFaith.FIDELE, true, "", 14),
                        new fr.crusadecraft.core.court.HouseView.Match(UUID.randomUUID(), "Cendrecourt", TitleRank.BARONNIE, Heraldry.POURPRE.rgb(),
                                "Cendrecourt", fr.crusadecraft.core.world.AiFaith.PAIEN, false, "crusadecraft.house.block.faith", 0)));
    }

    private static fr.crusadecraft.core.event.EventView sampleEvent() {
        return new fr.crusadecraft.core.event.EventView(UUID.randomUUID(),
                Component.translatable("crusadecraft.event.famine.title", "", "", "Royaume de Valbrume"),
                Component.translatable("crusadecraft.event.famine.text", "", "", "Royaume de Valbrume"),
                ResourceLocation.tryParse("minecraft:wheat"),
                List.of(new fr.crusadecraft.core.event.EventView.OptionView(Component.translatable("crusadecraft.event.famine.opt.grain"), true,
                                List.of(Component.translatable("crusadecraft.event.fx.gold", "-60"), Component.translatable("crusadecraft.event.fx.prestige", "+10"))),
                        new fr.crusadecraft.core.event.EventView.OptionView(Component.translatable("crusadecraft.event.famine.opt.pray"), true,
                                List.of(Component.translatable("crusadecraft.event.fx.piety", "+15"), Component.translatable("crusadecraft.event.fx.council", "-5"))),
                        new fr.crusadecraft.core.event.EventView.OptionView(Component.translatable("crusadecraft.event.famine.opt.nothing"), true,
                                List.of(Component.translatable("crusadecraft.event.fx.council", "-15")))),
                2, 2, 2);
    }

    private static fr.crusadecraft.core.intrigue.IntrigueView sampleIntrigue() {
        return new fr.crusadecraft.core.intrigue.IntrigueView("Odon", true, true,
                List.of(new fr.crusadecraft.core.intrigue.IntrigueView.FactionLine(fr.crusadecraft.core.intrigue.Faction.Kind.BAISSE_IMPOTS,
                                List.of("Berthe", "Gautier", "Ysolde"), 64, ""),
                        new fr.crusadecraft.core.intrigue.IntrigueView.FactionLine(fr.crusadecraft.core.intrigue.Faction.Kind.INDEPENDANCE,
                                List.of("Hugues"), 18, "Pierrefeu")),
                1,
                List.of(new fr.crusadecraft.core.intrigue.IntrigueView.PlotLine("Gautier", fr.crusadecraft.core.intrigue.Plot.Kind.DETOURNEMENT, 42)),
                List.of(new fr.crusadecraft.core.intrigue.IntrigueView.SecretLine(UUID.randomUUID(), "Berthe", fr.crusadecraft.core.intrigue.Secret.Kind.LIAISON),
                        new fr.crusadecraft.core.intrigue.IntrigueView.SecretLine(UUID.randomUUID(), "Aldric", fr.crusadecraft.core.intrigue.Secret.Kind.DETTES_DE_JEU)),
                List.of("Pierrefeu"), 1, 12);
    }

    private static List<fr.crusadecraft.network.packet.WorldPackets.NewsLine> sampleNews() {
        var amb = fr.crusadecraft.core.world.AiNames.realm(TitleRank.COMTE, "Ambrevent");
        var roc = fr.crusadecraft.core.world.AiNames.realm(TitleRank.DUCHE, "Rochegarde");
        var cen = fr.crusadecraft.core.world.AiNames.realm(TitleRank.BARONNIE, "Cendrecourt");
        return List.of(
                new fr.crusadecraft.network.packet.WorldPackets.NewsLine(61, Component.translatable("crusadecraft.news.coalition",
                        Component.literal("Royaume de Valbrume"), Component.literal("3"))),
                new fr.crusadecraft.network.packet.WorldPackets.NewsLine(58, Component.translatable("crusadecraft.news.province",
                        Component.literal("Saulepré"), roc, cen)),
                new fr.crusadecraft.network.packet.WorldPackets.NewsLine(58, Component.translatable("crusadecraft.news.peace.victory", roc, cen)),
                new fr.crusadecraft.network.packet.WorldPackets.NewsLine(52, Component.translatable("crusadecraft.news.alliance", amb, roc,
                        Component.translatable("crusadecraft.treaty.alliance_defensive"))),
                new fr.crusadecraft.network.packet.WorldPackets.NewsLine(47, Component.translatable("crusadecraft.news.war", roc, cen,
                        Component.translatable("crusadecraft.cb.conquest"))),
                new fr.crusadecraft.network.packet.WorldPackets.NewsLine(40, Component.translatable("crusadecraft.news.peace.white", amb, cen)));
    }

    private static fr.crusadecraft.core.war.WarView sampleWar() {
        UUID me = UUID.randomUUID(), ambrevent = UUID.randomUUID(), rochegarde = UUID.randomUUID(), cendrecourt = UUID.randomUUID();
        var mine = new fr.crusadecraft.core.war.WarView.Party(me, false, "Royaume de Valbrume", TitleRank.COMTE, Heraldry.AZUR.rgb());
        var amb = new fr.crusadecraft.core.war.WarView.Party(ambrevent, true, "Ambrevent", TitleRank.COMTE, Heraldry.GUEULES.rgb());
        var roc = new fr.crusadecraft.core.war.WarView.Party(rochegarde, true, "Rochegarde", TitleRank.DUCHE, Heraldry.SINOPLE.rgb());
        var cen = new fr.crusadecraft.core.war.WarView.Party(cendrecourt, true, "Cendrecourt", TitleRank.BARONNIE, Heraldry.POURPRE.rgb());
        java.util.Map<fr.crusadecraft.core.war.WarRules.Peace, fr.crusadecraft.core.war.WarRules.Verdict> peace =
                new java.util.EnumMap<>(fr.crusadecraft.core.war.WarRules.Peace.class);
        for (var p : fr.crusadecraft.core.war.WarRules.Peace.values()) {
            peace.put(p, fr.crusadecraft.core.war.WarRules.aiAnswer(p, -34, 40, 12));
        }
        List<fr.crusadecraft.core.war.War.Event> events = List.of(
                new fr.crusadecraft.core.war.War.Event(57, "crusadecraft.war.log.captured", List.of("Ormesval"), 5),
                new fr.crusadecraft.core.war.War.Event(56, "crusadecraft.war.log.skirmish", List.of("Royaume de Valbrume"), 4),
                new fr.crusadecraft.core.war.War.Event(55, "crusadecraft.war.log.raid_repelled", List.of("Hautemarche"), 8),
                new fr.crusadecraft.core.war.War.Event(54, "crusadecraft.war.log.skirmish", List.of("Comté d'Ambrevent"), -3),
                new fr.crusadecraft.core.war.War.Event(46, "crusadecraft.war.log.declared", List.of("CLAIM", "Ormesval"), 0));
        var line = new fr.crusadecraft.core.war.WarView.WarLine(UUID.randomUUID(), mine, amb, List.of("Baronnie de Cendrecourt"), List.of(),
                fr.crusadecraft.core.war.CasusBelli.CLAIM, "Ormesval", 1, true, 34, 96, 120, 18, 40, List.of("Ormesval"), 11, events, peace);
        List<fr.crusadecraft.core.war.WarView.CbOption> cbs = new ArrayList<>();
        for (var cb : fr.crusadecraft.core.war.CasusBelli.values()) {
            String problem = switch (cb) {
                case CLAIM -> null;
                case DEJURE -> "crusadecraft.war.cb.need_kingdom";
                case CONQUEST -> "crusadecraft.war.cb.need_duchy";
                case VASSALIZE -> "crusadecraft.war.cb.rank";
                case HUMILIATE -> null;
                case GUERRE_SAINTE -> "crusadecraft.war.cb.not_infidel";
            };
            List<fr.crusadecraft.core.war.WarView.ProvinceOption> provinces = cb == fr.crusadecraft.core.war.CasusBelli.CLAIM
                    ? List.of(new fr.crusadecraft.core.war.WarView.ProvinceOption(4, "Saulepré"), new fr.crusadecraft.core.war.WarView.ProvinceOption(5, "Rochegarde"))
                    : List.of();
            cbs.add(new fr.crusadecraft.core.war.WarView.CbOption(cb, problem, provinces));
        }
        List<fr.crusadecraft.core.war.WarView.Target> targets = List.of(
                new fr.crusadecraft.core.war.WarView.Target(amb, fr.crusadecraft.core.diplomacy.DiploStatus.GUERRE, 120,
                        "crusadecraft.war.block.at_war", cbs),
                new fr.crusadecraft.core.war.WarView.Target(roc, fr.crusadecraft.core.diplomacy.DiploStatus.HOSTILE, 210, null, cbs),
                new fr.crusadecraft.core.war.WarView.Target(cen, fr.crusadecraft.core.diplomacy.DiploStatus.ALLIANCE, 40,
                        "crusadecraft.war.block.alliance", cbs));
        return new fr.crusadecraft.core.war.WarView(96, 132, true, 10, 1250, 180, true, "", List.of(line), targets, 72, 60,
                List.of("Duché de Rochegarde", "Comté d'Ambrevent"));
    }

    private static MapView sampleMap() {
        List<MapView.Territory> t = new ArrayList<>();
        t.add(new MapView.Territory(UUID.randomUUID(), false, true, "Royaume de Valbrume", TitleRank.BARONNIE, Heraldry.AZUR.rgb(),
                "Romain", false, null, 41));
        t.add(new MapView.Territory(UUID.randomUUID(), true, false, "Ambrevent", TitleRank.ROYAUME, Heraldry.GUEULES.rgb(),
                "Godefroy", false, AiPersonality.EXPANSIONNISTE, 160));
        t.add(new MapView.Territory(UUID.randomUUID(), true, false, "Rochegarde", TitleRank.DUCHE, Heraldry.SINOPLE.rgb(),
                "Mathilde", true, AiPersonality.MARCHAND, 90));
        t.add(new MapView.Territory(UUID.randomUUID(), true, false, "Cendrecourt", TitleRank.COMTE, Heraldry.POURPRE.rgb(),
                "Thibaut", false, AiPersonality.FANATIQUE, 35));
        List<MapView.Seat> seats = List.of(
                new MapView.Seat(0, "Valbrume", 0, 0, true, 0, false, false),
                new MapView.Seat(0, "Hautemarche", 260, 120, false, 0, false, false),
                new MapView.Seat(1, "Ambrevent", -1100, -350, true, 0, true, true),
                new MapView.Seat(1, "Ormesval", -1400, -100, false, 0, true, true),
                new MapView.Seat(1, "Givrefond", -900, -650, false, 1, false, true),
                new MapView.Seat(1, "Brisemer", -1350, -600, false, 0, true, true),
                new MapView.Seat(2, "Rochegarde", 900, -700, true, 1, true, true),
                new MapView.Seat(2, "Saulepré", 1150, -450, false, 1, false, true),
                new MapView.Seat(3, "Cendrecourt", 700, 800, true, 1, true, true));
        return new MapView(ResourceLocation.tryParse("minecraft:overworld"), 0, 0, 40, 60, 160, 110, t, seats,
                List.of(new MapView.Dejure("Aquilée", 0xD9A88E), new MapView.Dejure("Montvert", 0x9DB58C)), true, false, MapView.Borders.EMPTY);
    }

    /** Le registre des lignées : la maison Aubépine sur trois générations, ou le village de Saint-Aubin. */
    /** Les lieux d'un monde de démonstration : le royaume du joueur, un empire posé à la main, une région. */
    private static fr.crusadecraft.core.place.LieuView sampleLieux() {
        record E(int depth, fr.crusadecraft.core.place.LieuKind kind, String name, int color, boolean linked, int population, boolean text) {}
        int azur = Heraldry.AZUR.rgb(), pourpre = 0x8E24AA, sinople = 0x2E7D32, neutre = 0x9A8A70;
        List<E> rows = List.of(
                new E(0, fr.crusadecraft.core.place.LieuKind.ROYAUME, "Royaume de Valbrume", azur, true, 41, false),
                new E(1, fr.crusadecraft.core.place.LieuKind.VILLE, "Valbrume", azur, true, 24, false),
                new E(1, fr.crusadecraft.core.place.LieuKind.VILLE, "Hautemarche", azur, true, 11, false),
                new E(1, fr.crusadecraft.core.place.LieuKind.FORT, "Fort frontalier de Valbrume", azur, true, 0, false),
                new E(1, fr.crusadecraft.core.place.LieuKind.LIEU_SAINT, "Abbaye de Saint-Aubin", azur, false, 0, true),
                new E(0, fr.crusadecraft.core.place.LieuKind.EMPIRE, "Empire de Lothaire", pourpre, false, 120, false),
                new E(1, fr.crusadecraft.core.place.LieuKind.ROYAUME, "Royaume d'Ambrevent", pourpre, true, 89, false),
                new E(2, fr.crusadecraft.core.place.LieuKind.VILLE, "Ambrevent", pourpre, true, 58, false),
                new E(2, fr.crusadecraft.core.place.LieuKind.VILLE, "Ormesval", pourpre, true, 31, false),
                new E(1, fr.crusadecraft.core.place.LieuKind.DUCHE, "Duché de Rochegarde", sinople, true, 31, false),
                new E(2, fr.crusadecraft.core.place.LieuKind.VILLE, "Rochegarde", sinople, true, 31, false),
                new E(0, fr.crusadecraft.core.place.LieuKind.REGION, "Forêt de Brocéliande", neutre, false, 0, false),
                new E(0, fr.crusadecraft.core.place.LieuKind.RUINE, "Ruines de Karak", neutre, false, 0, true));
        List<fr.crusadecraft.core.place.LieuView.Entry> entries = new ArrayList<>();
        UUID abbey = null, realm = null;
        for (E e : rows) {
            UUID id = UUID.randomUUID();
            if (e.kind() == fr.crusadecraft.core.place.LieuKind.LIEU_SAINT) abbey = id;
            if (realm == null) realm = id;
            entries.add(new fr.crusadecraft.core.place.LieuView.Entry(id, e.kind().ordinal(), Component.literal(e.name()), e.depth(), e.color(),
                    e.linked(), e.population(), e.text()));
        }
        var selection = new fr.crusadecraft.core.place.LieuView.Selection(abbey, fr.crusadecraft.core.place.LieuKind.LIEU_SAINT.ordinal(),
                Component.literal("Abbaye de Saint-Aubin"), Component.translatable("crusadecraft.lieu.origin.manual"),
                Component.literal("Royaume de Valbrume"), realm, Component.literal("Royaume de Valbrume"), 0, true, 412, -96, 0, 12,
                "Votre texte ici : l'histoire du lieu, ce qu'on y trouve, ce qu'on en raconte. Le mod n'écrit rien dans les fiches ;"
                        + " elles sont à vous, et chacun les lit dans l'écran des lieux.",
                "Abbaye de Saint-Aubin", true, true, true, true);
        List<fr.crusadecraft.core.place.LieuView.Choice> realms = List.of(
                new fr.crusadecraft.core.place.LieuView.Choice(UUID.randomUUID(), Component.literal("Royaume de Valbrume")),
                new fr.crusadecraft.core.place.LieuView.Choice(UUID.randomUUID(), Component.literal("Royaume d'Ambrevent")));
        return new fr.crusadecraft.core.place.LieuView(true, entries, selection,
                Component.literal("Abbaye de Saint-Aubin, à 12 blocs › Royaume de Valbrume"), realms, null, true);
    }

    private static fr.crusadecraft.core.lineage.LineageView sampleLineage(boolean house) {
        java.util.function.BiFunction<String, Object[], fr.crusadecraft.core.lineage.LineageView.Row> row = (n, o) ->
                new fr.crusadecraft.core.lineage.LineageView.Row(UUID.randomUUID(), n, (boolean) o[0], (int) o[1], (boolean) o[2],
                        (fr.crusadecraft.core.lineage.Role) o[3], (int) o[4], (String) o[5], (boolean) o[6], (boolean) o[7]);
        List<fr.crusadecraft.core.lineage.LineageView.Row> rows = new ArrayList<>();
        if (house) {
            rows.add(row.apply("Hugues d'Aubépine", new Object[]{false, 81, false, fr.crusadecraft.core.lineage.Role.FAMILLE, 0, "Blanche", false, true}));
            rows.add(row.apply("Blanche de Valcourt", new Object[]{true, 77, false, fr.crusadecraft.core.lineage.Role.FAMILLE, 0, "Hugues", false, false}));
            rows.add(row.apply("Godefroy d'Aubépine", new Object[]{false, 54, true, fr.crusadecraft.core.lineage.Role.SOUVERAIN, 1, "Mathilde", true, true}));
            rows.add(row.apply("Mathilde de Rochegarde", new Object[]{true, 51, true, fr.crusadecraft.core.lineage.Role.CONSORT, 1, "Godefroy", true, false}));
            rows.add(row.apply("Aliénor d'Aubépine", new Object[]{true, 49, true, fr.crusadecraft.core.lineage.Role.FAMILLE, 1, "", false, true}));
            rows.add(row.apply("Raoul d'Aubépine", new Object[]{false, 29, true, fr.crusadecraft.core.lineage.Role.HERITIER, 2, "Ysolde", true, true}));
            rows.add(row.apply("Ysolde de Brisemer", new Object[]{true, 27, true, fr.crusadecraft.core.lineage.Role.FAMILLE, 2, "Raoul", false, false}));
            rows.add(row.apply("Aude d'Aubépine", new Object[]{true, 24, true, fr.crusadecraft.core.lineage.Role.CHEVALIER, 2, "", false, true}));
            rows.add(row.apply("Thibaut d'Aubépine", new Object[]{false, 19, true, fr.crusadecraft.core.lineage.Role.FAMILLE, 2, "", false, true}));
        } else {
            rows.add(row.apply("Josselin Lefèvre", new Object[]{false, 41, true, fr.crusadecraft.core.lineage.Role.ARTISAN, 0, "Margot", true, true}));
            rows.add(row.apply("Margot Lefèvre", new Object[]{true, 38, true, fr.crusadecraft.core.lineage.Role.PAYSAN, 0, "Josselin", true, true}));
            rows.add(row.apply("Agnès Lefèvre", new Object[]{true, 16, true, fr.crusadecraft.core.lineage.Role.AUCUN, 0, "", false, true}));
            rows.add(row.apply("Amaury Lefèvre", new Object[]{false, 9, true, fr.crusadecraft.core.lineage.Role.AUCUN, 0, "", false, true}));
            rows.add(row.apply("Eudes Meunier", new Object[]{false, 52, true, fr.crusadecraft.core.lineage.Role.PAYSAN, 1, "Pernelle", false, true}));
            rows.add(row.apply("Pernelle Meunier", new Object[]{true, 50, true, fr.crusadecraft.core.lineage.Role.MARCHAND, 1, "Eudes", false, true}));
            rows.add(row.apply("Anseau Meunier", new Object[]{false, 77, false, fr.crusadecraft.core.lineage.Role.AUCUN, 1, "", false, true}));
            rows.add(row.apply("Clémence Fontaine", new Object[]{true, 33, true, fr.crusadecraft.core.lineage.Role.PAYSAN, 2, "", false, true}));
        }
        UUID selected = UUID.randomUUID();
        var houses = List.of(
                new fr.crusadecraft.core.lineage.LineageView.HouseLine(house ? selected : UUID.randomUUID(), "Aubépine", Heraldry.SINOPLE.rgb(),
                        Component.literal("Comté de Valcourt"), 7, 9),
                new fr.crusadecraft.core.lineage.LineageView.HouseLine(UUID.randomUUID(), "Ambrevent", Heraldry.GUEULES.rgb(),
                        Component.literal("Royaume d'Ambrevent"), 6, 8),
                new fr.crusadecraft.core.lineage.LineageView.HouseLine(UUID.randomUUID(), "Rochegarde", Heraldry.AZUR.rgb(),
                        Component.literal("Duché de Rochegarde"), 9, 13));
        var communities = List.of(
                new fr.crusadecraft.core.lineage.LineageView.CommunityLine(house ? UUID.randomUUID() : selected, "Saint-Aubin",
                        fr.crusadecraft.core.lineage.CommunityKind.VILLAGE, Component.literal("Royaume de Valbrume"), 8),
                new fr.crusadecraft.core.lineage.LineageView.CommunityLine(UUID.randomUUID(), "Ordre du Lys",
                        fr.crusadecraft.core.lineage.CommunityKind.ORDRE, Component.literal("Duché de Rochegarde"), 6));
        var realms = List.of(new fr.crusadecraft.core.lineage.LineageView.RealmChoice(UUID.randomUUID(), Component.literal("Royaume de Valbrume")),
                new fr.crusadecraft.core.lineage.LineageView.RealmChoice(UUID.randomUUID(), Component.literal("Comté de Valcourt")));
        var selection = house
                ? new fr.crusadecraft.core.lineage.LineageView.Selection(true, selected, "Aubépine", Component.literal("Comté de Valcourt"), null, rows)
                : new fr.crusadecraft.core.lineage.LineageView.Selection(false, selected, "Saint-Aubin", Component.literal("Royaume de Valbrume"),
                        fr.crusadecraft.core.lineage.CommunityKind.VILLAGE, rows);
        return new fr.crusadecraft.core.lineage.LineageView(true, true, houses, communities, realms, selection);
    }

    /** La carte de l'éditeur : des provinces numérotées, une frontière peinte pour Rochegarde et une terre libre. */
    private static MapView sampleEditMap() {
        MapView base = sampleMap();
        List<MapView.Territory> t = new ArrayList<>();
        for (MapView.Territory x : base.territories()) {
            t.add(new MapView.Territory(x.id(), x.ai(), x.mine(), x.label(), x.rank(), x.color(), x.ruler(), x.rulerFemale(), x.personality(),
                    x.population(), x.ai() ? fr.crusadecraft.core.world.AiFaith.FIDELE : null, x.label().equals("Rochegarde")));
        }
        List<MapView.Seat> seats = new ArrayList<>();
        int id = 0;
        for (MapView.Seat s : base.seats()) {
            seats.add(new MapView.Seat(s.territory(), s.name(), s.x(), s.z(), s.capital(), s.dejure(), s.village(), s.province(),
                    s.province() ? id++ : -1, s.province() ? s.territory() : -1));
        }
        List<Long> keys = new ArrayList<>();
        List<Integer> owners = new ArrayList<>();
        // Une marche peinte pour Rochegarde, en biais vers Valbrume, et une forêt laissée libre chez Ambrevent.
        for (int cx = 30; cx <= 60; cx++) {
            for (int cz = -50; cz <= -28; cz++) {
                if (cz > -60 + (60 - cx) && cz < -20 + (60 - cx) / 2) {
                    keys.add(fr.crusadecraft.core.world.TerritoryMap.key(cx, cz));
                    owners.add(2);
                }
            }
        }
        for (int cx = -78; cx <= -66; cx++) {
            for (int cz = -30; cz <= -20; cz++) {
                keys.add(fr.crusadecraft.core.world.TerritoryMap.key(cx, cz));
                owners.add(MapView.Borders.NEUTRAL);
            }
        }
        long[] k = new long[keys.size()];
        int[] o = new int[owners.size()];
        for (int i = 0; i < k.length; i++) {
            k[i] = keys.get(i);
            o[i] = owners.get(i);
        }
        return new MapView(base.dimension(), 0, 0, 40, 60, 160, 110, t, seats, base.dejure(), true, true, new MapView.Borders(k, o));
    }

    private static AiRealmView sampleAiRealm() {
        AiRealmView.Person ruler = new AiRealmView.Person("Godefroy", false, 47, new int[]{9, 14, 7, 5, 8},
                List.of(Trait.AMBITIEUX, Trait.BRAVE, Trait.CRUEL));
        List<AiRealmView.Seat> council = List.of(
                new AiRealmView.Seat(Post.CHANCELIER, new AiRealmView.Person("Bérenger", false, 39, new int[]{12, 4, 6, 7, 5}, List.of(Trait.HONNETE))),
                new AiRealmView.Seat(Post.MARECHAL, new AiRealmView.Person("Aude", true, 33, new int[]{5, 13, 4, 6, 3}, List.of(Trait.BRAVE))),
                new AiRealmView.Seat(Post.INTENDANT, new AiRealmView.Person("Eudes", false, 51, new int[]{6, 3, 11, 4, 7}, List.of(Trait.AVARE))),
                new AiRealmView.Seat(Post.MAITRE_ESPION, new AiRealmView.Person("Sibylle", true, 28, new int[]{7, 4, 5, 12, 6}, List.of(Trait.FOURBE))),
                new AiRealmView.Seat(Post.CHAPELAIN, new AiRealmView.Person("Anseau", false, 60, new int[]{6, 2, 5, 4, 13}, List.of(Trait.ZELE))));
        List<AiRealmView.Province> provinces = List.of(
                new AiRealmView.Province("Ambrevent", true, fr.crusadecraft.core.world.AiProvince.Anchor.VILLAGE, 58, 31, -1100, -350),
                new AiRealmView.Province("Ormesval", false, fr.crusadecraft.core.world.AiProvince.Anchor.VILLAGE_PREDICTED, 24, 18, -1400, -100),
                new AiRealmView.Province("Givrefond", false, fr.crusadecraft.core.world.AiProvince.Anchor.LAND, 17, 12, -900, -650),
                new AiRealmView.Province("Brisemer", false, fr.crusadecraft.core.world.AiProvince.Anchor.VILLAGE, 31, 22, -1350, -600));
        return new AiRealmView(UUID.randomUUID(), "Ambrevent", TitleRank.ROYAUME, Heraldry.GUEULES.rgb(), "Ormesval",
                AiPersonality.EXPANSIONNISTE, ruler, council, provinces, 742, 263, 41, 12, 1164, fr.crusadecraft.core.world.AiFaith.FIDELE);
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
                v.state(), v.foundedDay(), v.currentDay(), v.gold(), v.prestige(), v.piety(), v.dread(), v.colonies(), seats, v.pendingEvents());
    }

    public static RealmView sampleView() {
        ResourceLocation ow = ResourceLocation.tryParse("minecraft:overworld");
        List<ColonyInfo> colonies = List.of(
                new ColonyInfo(new ColonyRef(ow, 1), "Valbrume", 3, 24, 30, 7.4, 31, Heraldry.AZUR.rgb()),
                new ColonyInfo(new ColonyRef(ow, 2), "Hautemarche", 2, 11, 16, 5.1, 14, Heraldry.SINOPLE.rgb()),
                new ColonyInfo(new ColonyRef(ow, 3), "Pierrefeu", 1, 6, 8, 3.2, 7, Heraldry.GUEULES.rgb()));
        return new RealmView(UUID.randomUUID(), "Royaume de Valbrume", "Par le fer et la foi", "Aubépine",
                Heraldry.AZUR.rgb(), "Romain", false, TitleRank.BARONNIE, RealmState.ACTIVE, 12, 57,
                1250, 50, 12, 3, colonies, List.of(), 1);
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
            // Une notification de la prise précédente ne doit pas recouvrir celle-ci.
            mc.getToasts().clear();
            mc.setScreen(shot.screen().get());
            if (shot.before() != null) shot.before().run();
            wait = SETTLE_TICKS;
        } else {
            CrusadeCraft.LOGGER.info("UiShots : terminé, fermeture du jeu");
            mc.stop();
        }
    }
}
