package fr.crusadecraft.core.city;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Les réserves et la bourse d'une ville (P43). Chaque jour, ceux qui travaillent produisent, la campagne vend son grain,
 * tout le monde mange, et le royaume prélève sa part. Rien n'y est décoratif : ce qui manque ici manque aussi aux étals,
 * et une ville qui a faim finit par perdre ses habitants. Tout est en logique pure, pour pouvoir être vérifié.
 */
public final class CityEconomy {
    /** Au plus, ce qu'on garde de chaque marchandise (deux fois plus avec un entrepôt). */
    public static final int CAP = 128;
    /** Une marchandise sous ce seuil, le marché en fait venir. */
    static final int LOW = 16;
    /** Ce qu'il fait venir d'un coup. */
    static final int IMPORT = 8;
    /** Ce que le marché laisse dans la bourse quand il fait venir des marchandises. */
    static final int RESERVE = 60;
    /** Au-dessus de cette réserve, la ville verse une part au royaume. */
    static final long TAX_ABOVE = 300;

    private final EnumMap<CityGood, Integer> stock = new EnumMap<>(CityGood.class);
    private long purse;
    private int famine;

    /** Qui vit et travaille là : par fonction, les vivants qui y ont un poste. */
    public record Census(Map<BuildingFunction, Integer> workers, int people, boolean atWar, Map<BuildingFunction, Integer> bonus, int storage, int charm, boolean besieged) {
        public Census(Map<BuildingFunction, Integer> workers, int people, boolean atWar) {
            this(workers, people, atWar, Map.of(), 0, 0, false);
        }

        public Census(Map<BuildingFunction, Integer> workers, int people, boolean atWar, Map<BuildingFunction, Integer> bonus, int storage, int charm) {
            this(workers, people, atWar, bonus, storage, charm, false);
        }

        int of(BuildingFunction f) {
            return workers.getOrDefault(f, 0);
        }

        /** Ce que ces travailleurs produisent, meubles compris, et avec dix pour cent de plus par école qui enseigne. */
        int output(BuildingFunction f, int base) {
            int pct = 100 + bonus.getOrDefault(f, 0) + 10 * Math.min(2, of(BuildingFunction.ECOLE));
            return base * pct / 100;
        }
    }

    /** Ce qu'une journée a donné. */
    public record Day(int hungry, List<CityGood> missing, long tax, int imported, int exported) {}

    /** De quoi tenir au début : une ville ne naît pas affamée. */
    public static CityEconomy fresh() {
        CityEconomy e = new CityEconomy();
        e.stock.put(CityGood.BLE, 48);
        e.stock.put(CityGood.PAIN, 48);
        e.stock.put(CityGood.VIANDE, 24);
        e.stock.put(CityGood.REPAS, 12);
        e.stock.put(CityGood.FER, 16);
        e.stock.put(CityGood.OUTILS, 12);
        e.stock.put(CityGood.PIERRE, 16);
        e.stock.put(CityGood.BOIS, 16);
        e.stock.put(CityGood.CUIR, 8);
        e.stock.put(CityGood.LIVRE, 4);
        e.purse = 200;
        return e;
    }

    public int stock(CityGood g) {
        return stock.getOrDefault(g, 0);
    }

    public long purse() {
        return purse;
    }

    public int famine() {
        return famine;
    }

    public void add(CityGood g, int n) {
        stock.put(g, Math.max(0, stock(g) + n));
    }

    /** Prend autant que possible ; renvoie ce qu'on a pris. */
    public int take(CityGood g, int n) {
        int got = Math.min(stock(g), Math.max(0, n));
        stock.put(g, stock(g) - got);
        return got;
    }

    /** P57 : ce que montre l'entrepôt d'une colonie. */
    void setStock(CityGood g, int n) {
        stock.put(g, Math.max(0, n));
    }

    /** Un marché, qui n'a pas d'habitants à compter, tient lui-même ses jours de faim. */
    void setFamine(int days) {
        famine = Math.max(0, days);
    }

    public void addPurse(long n) {
        purse = Math.max(0, purse + n);
    }

    /** Le prix d'une marchandise selon ce qu'il en reste : rare, elle coûte plus cher. Un surcroît de « demande », de 0 à 40. */
    public int scarcity(CityGood g) {
        int s = stock(g);
        if (s >= LOW * 2) return 0;
        return (LOW * 2 - s) * 40 / (LOW * 2);
    }

    /**
     * P54 : ce qu'elle est prête à payer, en pour cent du prix normal, selon ce qu'il lui reste : jusqu'au double de ce qui vient à
     * manquer, la moitié de ce qui déborde de ses réserves. C'est cet écart qui donne une raison de transporter les choses.
     */
    public int demand(CityGood g) {
        int s = stock(g), enough = LOW * 2;
        if (s < enough) return 200 - s * 100 / enough;
        return s >= CAP ? 50 : 100 - (s - enough) * 50 / (CAP - enough);
    }

    /** Une journée de la ville. */
    public Day simulate(Census c, boolean warehouse, boolean market, java.util.Random random) {
        produce(c);
        // La campagne alentour vend son grain ; la guerre coupe les routes.
        // P55 : coupée du monde, elle ne reçoit plus rien du tout : c'est le blocus qui l'affame.
        int fromCountry = c.besieged() ? 0 : (int) Math.min(c.atWar() ? c.people() / 2 : c.people(), purse * 2);
        if (fromCountry > 0) {
            purse -= (fromCountry + 1) / 2;
            add(CityGood.BLE, fromCountry);
        }
        int imported = market && !c.besieged() ? imports(c) : 0;
        int hungry = eat(c.people());
        // Ceux qui travaillent et ceux qui louent leurs bras font vivre la bourse.
        purse += c.people() + Furnishing.visitors(c.charm());
        int cap = Math.min(CAP * 6, (warehouse ? CAP * 2 : CAP) + c.storage());
        int exported = 0;
        for (CityGood g : CityGood.values()) {
            int over = stock(g) - cap;
            if (over <= 0) continue;
            stock.put(g, cap);
            // Le marché écoule le surplus ; sans lui, il se perd.
            if (market && !c.besieged()) {
                purse += over;
                exported += over;
            }
        }
        long tax = purse > TAX_ABOVE ? purse / 20 : 0;
        purse -= tax;
        famine = c.people() > 0 && hungry * 3 > c.people() ? famine + 1 : 0;
        List<CityGood> missing = new ArrayList<>();
        for (CityGood g : new CityGood[]{CityGood.PAIN, CityGood.BLE, CityGood.VIANDE}) if (stock(g) == 0) missing.add(g);
        return new Day(hungry, missing, tax, imported, exported);
    }

    private void produce(Census c) {
        // P55 : les champs sont hors les murs : coupée du monde, elle n'en tire presque plus rien. Sans ça, une ville à fermes ne connaîtrait jamais la faim.
        int harvest = c.output(BuildingFunction.FERME, 12 * c.of(BuildingFunction.FERME));
        add(CityGood.BLE, c.besieged() ? harvest / 4 : harvest);
        int bakers = c.of(BuildingFunction.BOULANGERIE);
        if (bakers > 0) {
            // Un moulin : la même farine fait plus de pain.
            int wheat = take(CityGood.BLE, c.output(BuildingFunction.BOULANGERIE, 20 * bakers));
            add(CityGood.PAIN, c.of(BuildingFunction.MOULIN) > 0 ? wheat * 2 : wheat * 4 / 3);
        }
        int butchers = c.of(BuildingFunction.BOUCHERIE);
        if (butchers > 0) {
            take(CityGood.BLE, 2 * butchers);
            add(CityGood.VIANDE, c.output(BuildingFunction.BOUCHERIE, 5 * butchers));
        }
        int taverns = c.of(BuildingFunction.TAVERNE);
        if (taverns > 0) {
            int k = Math.min(c.output(BuildingFunction.TAVERNE, 3 * taverns), Math.min(stock(CityGood.PAIN), stock(CityGood.VIANDE)));
            take(CityGood.PAIN, k);
            take(CityGood.VIANDE, k);
            add(CityGood.REPAS, k * 2);
        }
        int smiths = c.of(BuildingFunction.FORGE);
        if (smiths > 0) add(CityGood.OUTILS, take(CityGood.FER, c.output(BuildingFunction.FORGE, 2 * smiths)));
        add(CityGood.PIERRE, c.output(BuildingFunction.ATELIER, 6 * c.of(BuildingFunction.ATELIER)));
        int grooms = c.of(BuildingFunction.ECURIE);
        if (grooms > 0) {
            take(CityGood.BLE, 2 * grooms);
            add(CityGood.CUIR, c.output(BuildingFunction.ECURIE, 3 * grooms));
        }
        add(CityGood.LIVRE, c.output(BuildingFunction.BIBLIOTHEQUE, c.of(BuildingFunction.BIBLIOTHEQUE)));
    }

    /** Le marché fait venir ce qui manque, tant que la bourse suit ; la guerre coupe une route sur deux. */
    private int imports(Census c) {
        int total = 0;
        for (CityGood g : new CityGood[]{CityGood.BLE, CityGood.FER, CityGood.BOIS, CityGood.VIANDE}) {
            if (stock(g) >= LOW) continue;
            int n = c.atWar() ? IMPORT / 2 : IMPORT;
            int price = n * 2;
            // On garde de quoi payer la campagne et les salaires : ce sont eux qui nourrissent la ville.
            if (purse < price + RESERVE) continue;
            purse -= price;
            add(g, n);
            total += n;
        }
        return total;
    }

    /**
     * Tout le monde mange une fois. La moitié des foyers cuit son grain chez soi : c'est ce qui laisse du pain aux étals.
     * Les autres prennent du pain, un repas ou de la viande ; en dernier recours, le grain qui reste. Renvoie ceux qui n'ont rien eu.
     */
    private int eat(int people) {
        int left = people;
        left -= take(CityGood.BLE, left / 2);
        for (CityGood g : new CityGood[]{CityGood.PAIN, CityGood.REPAS, CityGood.VIANDE, CityGood.BLE}) left -= take(g, left);
        return left;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        for (Map.Entry<CityGood, Integer> e : stock.entrySet()) t.putInt(e.getKey().name(), e.getValue());
        t.putLong("purse", purse);
        t.putInt("famine", famine);
        return t;
    }

    public static CityEconomy load(CompoundTag t) {
        CityEconomy e = new CityEconomy();
        for (CityGood g : CityGood.values()) if (t.contains(g.name())) e.stock.put(g, t.getInt(g.name()));
        e.purse = t.getLong("purse");
        e.famine = t.getInt("famine");
        return e;
    }
}
