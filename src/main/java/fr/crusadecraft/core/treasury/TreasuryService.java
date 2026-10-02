package fr.crusadecraft.core.treasury;

import fr.crusadecraft.compat.ConflictDetector;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyReport;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CharacterRecord;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CitizenInfo;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Stat;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.council.CouncilTask;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.law.LawEffects;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Le trésor du royaume. Tout mouvement d'or passe ici et s'inscrit au livre de comptes.
 *
 * <p>Trois sortes de mouvements : les <b>recettes</b> (impôts, domaine, dépôts) toujours acceptées ;
 * les <b>dépenses choisies</b> (requêtes, récompenses, retraits), refusées si l'or manque ; les
 * <b>dépenses dues</b> (salaires, entretien), payées jusqu'à la dette maximale et pas au-delà.
 * Le serveur traite un mouvement d'un seul tenant : vérification et écriture ne sont jamais séparées.
 */
public final class TreasuryService {

    private TreasuryService() {}

    // ------------------------------------------------------------------ mouvements

    public static long debtLimit() {
        return CrusadeConfig.SERVER.debtLimit.get();
    }

    /** Recette. */
    public static void credit(MinecraftServer server, Realm realm, LedgerCategory category, long amount) {
        if (amount <= 0) return;
        realm.setGold(realm.gold() + amount);
        realm.ledger().record(RealmService.currentDay(server), category, amount);
        CrusadeSavedData.get(server).setDirty();
    }

    /** Dépense choisie : rien n'est débité si le trésor n'a pas toute la somme. */
    public static boolean spend(MinecraftServer server, Realm realm, LedgerCategory category, long amount) {
        if (amount <= 0) return true;
        if (realm.gold() < amount) return false;
        realm.setGold(realm.gold() - amount);
        realm.ledger().record(RealmService.currentDay(server), category, -amount);
        CrusadeSavedData.get(server).setDirty();
        return true;
    }

    /** Dépense due : payée jusqu'à la dette maximale. Renvoie ce qui a été réellement payé. */
    public static long charge(MinecraftServer server, Realm realm, LedgerCategory category, long amount) {
        long paid = TaxRules.chargeable(realm.gold(), amount, debtLimit());
        if (paid <= 0) return 0;
        realm.setGold(realm.gold() - paid);
        realm.ledger().record(RealmService.currentDay(server), category, -paid);
        CrusadeSavedData.get(server).setDirty();
        return paid;
    }

    // ------------------------------------------------------------------ barèmes

    private static TaxTable taxTable() {
        return TaxTable.parse(CrusadeConfig.SERVER.taxRates.get(),
                new TaxTable.Rate(CrusadeConfig.SERVER.taxBase.get(), CrusadeConfig.SERVER.taxPerLevel.get()));
    }

    private static TaxTable upkeepTable() {
        return TaxTable.parse(CrusadeConfig.SERVER.upkeepRates.get(), new TaxTable.Rate(0, 0));
    }

    public static GoldValues goldValues() {
        return GoldValues.parse(CrusadeConfig.SERVER.goldValues.get());
    }

    /** Les impôts sont-ils perçus ? Non si la configuration les coupe, ou si War 'N Taxes s'en charge déjà. */
    public static boolean taxesActive() {
        return CrusadeConfig.SERVER.taxes.get() && !ConflictDetector.economyAndWarDisabled();
    }

    // ------------------------------------------------------------------ prévision

    /** Ce que le royaume percevra et paiera au prochain jour, colonie par colonie. */
    public record Forecast(List<TreasuryView.ColonyLine> colonies, long domain, long salaries, double stewardBonus,
                           double researchBonus) {
        public long taxes() {
            long sum = 0;
            for (TreasuryView.ColonyLine c : colonies) sum += c.collected();
            return sum;
        }

        public long upkeep() {
            long sum = 0;
            for (TreasuryView.ColonyLine c : colonies) sum += c.upkeep();
            return sum;
        }
    }

    public static Forecast forecast(MinecraftServer server, Realm realm, double meetingBonus) {
        TaxTable taxes = taxTable(), upkeep = upkeepTable();
        boolean active = taxesActive();
        double fiscal = LawEffects.fiscalMultiplier(realm.laws());
        double province = LawEffects.provinceMultiplier(realm.laws());
        double steward = stewardBonus(server, realm);
        double research = MineColoniesBridge.researchEffect(realm.capital(), Law.Research.FISCALITE);
        List<TreasuryView.ColonyLine> lines = new ArrayList<>();
        int population = 0;
        for (ColonyRef colony : realm.allColonies()) {
            if (!MineColoniesBridge.exists(colony)) continue;
            ColonyReport report = MineColoniesBridge.report(colony);
            population += report.citizens();
            List<TaxRules.Building> buildings = MineColoniesBridge.buildings(colony);
            boolean capital = colony.equals(realm.capital());
            TaxRules.Modifiers m = new TaxRules.Modifiers(report.happiness(), fiscal, steward, research, report.raided(),
                    capital ? 1.0 : province);
            TaxRules.ColonyTax tax = TaxRules.compute(buildings, taxes, upkeep, m);
            lines.add(new TreasuryView.ColonyLine(report.name(), capital, report.happiness(), buildings.size(),
                    active ? tax.collected() : 0, tax.upkeep(), report.raided()));
        }
        return new Forecast(lines, domainIncome(server, realm, population, meetingBonus), salaries(server, realm),
                steward, research);
    }

    /** Bonus de l'intendant sur les impôts (0 sans intendant libre). */
    static double stewardBonus(MinecraftServer server, Realm realm) {
        CitizenRef steward = realm.council().get(Post.INTENDANT);
        if (steward == null) return 0;
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(steward);
        if (info.isEmpty()) return 0;
        CharacterRecord rec = CharacterService.record(server, info.get());
        if (rec.status() != CharacterStatus.LIBRE) return 0;
        int stewardship = CharacterService.stats(info.get().skills(), rec.traits()).total()[Stat.INTENDANCE.ordinal()];
        return TaxRules.stewardBonus(stewardship, realm.court().task(Post.INTENDANT) == CouncilTask.COLLECTER_IMPOTS);
    }

    /** Ce que rapporte la tâche « Gérer le domaine » (0 si l'intendant fait autre chose). */
    static long domainIncome(MinecraftServer server, Realm realm, int population, double meetingBonus) {
        CitizenRef steward = realm.council().get(Post.INTENDANT);
        if (steward == null || realm.court().task(Post.INTENDANT) != CouncilTask.GERER_LE_DOMAINE) return 0;
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(steward);
        if (info.isEmpty()) return 0;
        CharacterRecord rec = CharacterService.record(server, info.get());
        if (rec.status() != CharacterStatus.LIBRE) return 0;
        int stewardship = CharacterService.stats(info.get().skills(), rec.traits()).total()[Stat.INTENDANCE.ordinal()];
        return TaxRules.domainIncome(population, stewardship, meetingBonus);
    }

    /** Salaires dus chaque jour aux conseillers et aux maires en fonction. */
    static long salaries(MinecraftServer server, Realm realm) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        long sum = 0;
        for (CitizenRef ref : realm.council().values()) if (onDuty(data, ref)) sum += CrusadeConfig.SERVER.councilSalary.get();
        for (CitizenRef ref : realm.mayors().values()) if (onDuty(data, ref)) sum += CrusadeConfig.SERVER.mayorSalary.get();
        return sum;
    }

    private static boolean onDuty(CrusadeSavedData data, CitizenRef ref) {
        return data.character(ref).map(r -> r.status() == CharacterStatus.LIBRE && !r.deceased()).orElse(true);
    }

    // ------------------------------------------------------------------ chaque jour

    /**
     * Le passage d'un jour : impôts, domaine, entretien, salaires, effets quotidiens des lois et de la dette.
     * Appelé par le conseil, une fois par jour du monde et par royaume actif.
     */
    public static void onNewDay(MinecraftServer server, Realm realm, long day, double meetingBonus) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        Forecast f = forecast(server, realm, meetingBonus);
        long before = realm.gold();

        credit(server, realm, LedgerCategory.IMPOTS, f.taxes());
        credit(server, realm, LedgerCategory.DOMAINE, f.domain());
        charge(server, realm, LedgerCategory.ENTRETIEN, f.upkeep());
        long paid = charge(server, realm, LedgerCategory.SALAIRES, f.salaries());
        boolean unpaid = paid < f.salaries();

        // Lois : prestige quotidien, crainte qui retombe.
        realm.setPrestige(realm.prestige() + LawEffects.dailyPrestige(realm.laws()));
        if (realm.dread() > 0 && Math.floorMod(day, LawEffects.dreadDecayEveryDays(realm.laws())) == 0) {
            realm.setDread(realm.dread() - 1);
        }

        // Dette : le prestige s'effrite, et ceux qu'on ne paie pas s'en souviennent.
        if (realm.gold() < 0) {
            realm.setPrestige(Math.max(0, realm.prestige() - CrusadeConfig.SERVER.debtPrestigeLoss.get()));
        }
        if (unpaid) {
            List<CitizenRef> salaried = new ArrayList<>(realm.council().values());
            salaried.addAll(realm.mayors().values());
            for (CitizenRef ref : salaried) {
                data.character(ref).ifPresent(r -> r.addModifier(OpinionModifier.of(OpinionReason.SALAIRE_IMPAYE, day, 1)));
            }
        }
        data.setDirty();

        ServerPlayer ruler = server.getPlayerList().getPlayer(realm.ruler());
        if (ruler != null && (f.taxes() + f.domain() + f.upkeep() + f.salaries()) > 0) {
            long delta = realm.gold() - before;
            Component body = Component.translatable(unpaid ? "crusadecraft.toast.treasury.unpaid" : "crusadecraft.toast.treasury.body",
                    f.taxes() + f.domain(), signed(delta), realm.gold());
            CrusadeNetwork.toPlayer(ruler, ToastPacket.of(realm.gold() < 0 ? ToastPacket.Kind.ALERT : ToastPacket.Kind.TREASURY,
                    Component.translatable("crusadecraft.toast.treasury.title"), body));
        }
    }

    private static String signed(long v) {
        return (v > 0 ? "+" : "") + v;
    }

    /** Réapplique le modificateur de bonheur « Impôts » (MineColonies ne le recharge pas au redémarrage). */
    public static void applyHappiness(MinecraftServer server) {
        if (!MineColoniesBridge.isReady()) return;
        boolean on = CrusadeConfig.SERVER.taxHappiness.get() && taxesActive();
        for (Realm realm : CrusadeSavedData.get(server).realms()) {
            double factor = on && realm.state().isActive() ? LawEffects.taxHappinessFactor(realm.laws()) : 1.0;
            for (ColonyRef colony : realm.allColonies()) MineColoniesBridge.applyTaxHappiness(colony, factor);
        }
    }

    // ------------------------------------------------------------------ Coffre du Trésor

    /** Dépose tout l'or reconnu de l'inventaire du souverain. */
    public static CouncilActions.Outcome depositAll(ServerPlayer player) {
        Optional<Realm> found = RealmService.realmOf(player.server, player.getUUID());
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        GoldValues values = goldValues();
        Inventory inv = player.getInventory();
        long total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
            int value = id == null ? 0 : values.valueOf(id);
            if (value <= 0) continue;
            total += (long) value * s.getCount();
            inv.setItem(i, ItemStack.EMPTY);
        }
        if (total == 0) return CouncilActions.Outcome.no("crusadecraft.treasury.nothing_to_deposit");
        credit(player.server, found.get(), LedgerCategory.DEPOTS, total);
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.treasury.deposited", total, found.get().gold()));
    }

    /**
     * Retire {@code count} objets d'or. Seul ce qui entre dans l'inventaire est débité : un inventaire
     * plein ne fait rien perdre, ni au trésor ni au joueur.
     */
    public static CouncilActions.Outcome withdraw(ServerPlayer player, ResourceLocation itemId, int count) {
        Optional<Realm> found = RealmService.realmOf(player.server, player.getUUID());
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        if (!CrusadeConfig.SERVER.allowWithdraw.get()) return CouncilActions.Outcome.no("crusadecraft.treasury.withdraw_disabled");
        Realm realm = found.get();
        int value = goldValues().valueOf(itemId);
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (value <= 0 || item == null || item == Items.AIR || count <= 0) return CouncilActions.Outcome.no("crusadecraft.treasury.bad_item");
        long cost = (long) value * count;
        if (realm.gold() < cost) return CouncilActions.Outcome.no("crusadecraft.treasury.not_enough", cost, Math.max(0, realm.gold()));
        int given = 0;
        int left = count;
        while (left > 0) {
            int batch = Math.min(left, item.getMaxStackSize());
            ItemStack stack = new ItemStack(item, batch);
            player.getInventory().add(stack);
            int inserted = batch - stack.getCount();
            given += inserted;
            left -= batch;
            if (!stack.isEmpty()) break;
        }
        if (given == 0) return CouncilActions.Outcome.no("crusadecraft.treasury.inventory_full");
        spend(player.server, realm, LedgerCategory.RETRAITS, (long) value * given);
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.treasury.withdrawn", given,
                new ItemStack(item).getHoverName(), realm.gold()));
    }

    // ------------------------------------------------------------------ vue

    public static TreasuryView view(MinecraftServer server, Realm realm) {
        long today = RealmService.currentDay(server);
        double bonus = realm.court().meetingBonusDay() >= today ? 1.25 : 1.0;
        Forecast f = forecast(server, realm, bonus);
        List<TreasuryView.Entry> entries = new ArrayList<>();
        for (Map.Entry<LedgerCategory, Long> e : realm.ledger().peek(today).amounts().entrySet()) {
            entries.add(new TreasuryView.Entry(e.getKey(), e.getValue()));
        }
        List<TreasuryView.DayLine> history = new ArrayList<>();
        for (long d = today - 6; d <= today; d++) {
            Ledger.Day day = realm.ledger().peek(d);
            history.add(new TreasuryView.DayLine(d, day.income(), day.expenses()));
        }
        return new TreasuryView(realm.name(), today, realm.gold(), debtLimit(), taxesActive(),
                realm.laws().level(Law.FISCALITE), LawEffects.fiscalMultiplier(realm.laws()), f.stewardBonus(),
                f.researchBonus(), LawEffects.provinceMultiplier(realm.laws()), f.colonies(), f.domain(), f.salaries(),
                entries, history, goldValues().denominations(), CrusadeConfig.SERVER.allowWithdraw.get());
    }
}
