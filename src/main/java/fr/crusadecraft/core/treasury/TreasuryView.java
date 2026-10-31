package fr.crusadecraft.core.treasury;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * L'écran du Trésor, calculé par le serveur : le solde, les comptes du jour, la prévision de demain et
 * l'historique des derniers jours.
 *
 * @param taxesActive faux si les impôts sont coupés (configuration, ou War 'N Taxes s'en charge)
 */
public record TreasuryView(String realmName, long day, long gold, long debtLimit, boolean taxesActive,
                           int fiscalLevel, double fiscalMultiplier, double stewardBonus, double researchBonus,
                           double provinceMultiplier, List<ColonyLine> colonies, long domain, long salaries,
                           List<Entry> today, List<DayLine> history, List<GoldValues.Denomination> denominations,
                           boolean canWithdraw) {

    /** Une colonie dans la prévision : son impôt de demain, son entretien militaire. */
    /**
     * Une ville et ce qu'elle rend à la couronne. {@code governor} : celui qui la tient pour la couronne (vide : tenue en
     * direct) ; {@code crownShare} : la part des impôts qui revient à la couronne, en pour cent ; {@code overDomain} :
     * tenue en direct au-delà de la limite de domaine (P19).
     */
    public record ColonyLine(String name, boolean capital, double happiness, int buildings, long collected,
                             long upkeep, boolean raided, String governor, int crownShare, boolean overDomain) {
        public ColonyLine(String name, boolean capital, double happiness, int buildings, long collected, long upkeep, boolean raided) {
            this(name, capital, happiness, buildings, collected, upkeep, raided, "", 100, false);
        }
    }

    /** Un poste des comptes du jour. */
    public record Entry(LedgerCategory category, long amount) {}

    /** Un jour de l'historique. */
    public record DayLine(long day, long income, long expenses) {}

    public long forecastIncome() {
        long sum = domain;
        for (ColonyLine c : colonies) sum += c.collected();
        return sum;
    }

    public long forecastExpenses() {
        long sum = salaries;
        for (ColonyLine c : colonies) sum += c.upkeep();
        return sum;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(realmName);
        buf.writeLong(day);
        buf.writeLong(gold);
        buf.writeLong(debtLimit);
        buf.writeBoolean(taxesActive);
        buf.writeVarInt(fiscalLevel);
        buf.writeDouble(fiscalMultiplier);
        buf.writeDouble(stewardBonus);
        buf.writeDouble(researchBonus);
        buf.writeDouble(provinceMultiplier);
        buf.writeVarInt(colonies.size());
        for (ColonyLine c : colonies) {
            buf.writeUtf(c.name());
            buf.writeBoolean(c.capital());
            buf.writeDouble(c.happiness());
            buf.writeVarInt(c.buildings());
            buf.writeLong(c.collected());
            buf.writeLong(c.upkeep());
            buf.writeBoolean(c.raided());
            buf.writeUtf(c.governor());
            buf.writeVarInt(c.crownShare());
            buf.writeBoolean(c.overDomain());
        }
        buf.writeLong(domain);
        buf.writeLong(salaries);
        buf.writeVarInt(today.size());
        for (Entry e : today) {
            buf.writeEnum(e.category());
            buf.writeLong(e.amount());
        }
        buf.writeVarInt(history.size());
        for (DayLine d : history) {
            buf.writeLong(d.day());
            buf.writeLong(d.income());
            buf.writeLong(d.expenses());
        }
        buf.writeVarInt(denominations.size());
        for (GoldValues.Denomination d : denominations) {
            buf.writeResourceLocation(d.item());
            buf.writeVarInt(d.value());
        }
        buf.writeBoolean(canWithdraw);
    }

    public static TreasuryView read(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        long day = buf.readLong();
        long gold = buf.readLong();
        long debt = buf.readLong();
        boolean active = buf.readBoolean();
        int fiscal = buf.readVarInt();
        double fm = buf.readDouble(), sb = buf.readDouble(), rb = buf.readDouble(), pm = buf.readDouble();
        int n = buf.readVarInt();
        List<ColonyLine> colonies = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            colonies.add(new ColonyLine(buf.readUtf(), buf.readBoolean(), buf.readDouble(), buf.readVarInt(),
                    buf.readLong(), buf.readLong(), buf.readBoolean(), buf.readUtf(), buf.readVarInt(), buf.readBoolean()));
        }
        long domain = buf.readLong();
        long salaries = buf.readLong();
        n = buf.readVarInt();
        List<Entry> today = new ArrayList<>(n);
        for (int i = 0; i < n; i++) today.add(new Entry(buf.readEnum(LedgerCategory.class), buf.readLong()));
        n = buf.readVarInt();
        List<DayLine> history = new ArrayList<>(n);
        for (int i = 0; i < n; i++) history.add(new DayLine(buf.readLong(), buf.readLong(), buf.readLong()));
        n = buf.readVarInt();
        List<GoldValues.Denomination> denoms = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ResourceLocation item = buf.readResourceLocation();
            denoms.add(new GoldValues.Denomination(item, buf.readVarInt()));
        }
        return new TreasuryView(name, day, gold, debt, active, fiscal, fm, sb, rb, pm, colonies, domain, salaries,
                today, history, denoms, buf.readBoolean());
    }
}
