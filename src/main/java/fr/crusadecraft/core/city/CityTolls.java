package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.Territory;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Ce que coûte de passer (P55). Une route traverse des terres : celles d'un royaume en guerre avec la caravane, elle ne les traverse
 * pas ; celles d'un autre, elle y paie le péage qu'il a fixé, sauf traité. Et ce qu'on vend chez un étranger paie sa douane, à
 * moins d'un traité de commerce. Tout cela revient au trésor du royaume qui l'a fixé.
 */
public final class CityTolls {
    /** Les péages qu'un royaume peut fixer, par passage de caravane. */
    public static final int[] TOLLS = {0, 2, 5, 10, 20};
    /** Les douanes, en pour cent de ce qui se vend chez lui. */
    public static final int[] DUTIES = {0, 5, 10, 20, 30};
    /** On regarde à qui est la route tous les tant de blocs : moins qu'un chunk en diagonale n'apprendrait rien de plus sur un royaume entier. */
    static final int SAMPLE = 48;

    /** Ce qu'un royaume a fixé. */
    public record Rules(int toll, int duty) {
        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("toll", toll);
            t.putInt("duty", duty);
            return t;
        }

        public static Rules load(CompoundTag t) {
            return new Rules(t.getInt("toll"), t.getInt("duty"));
        }
    }

    /**
     * Ce qu'il en est d'une route.
     *
     * @param cut    un ennemi tient ses terres
     * @param toll   ce qu'il faut payer en tout pour passer
     * @param payees ceux à qui on le doit, et combien
     */
    public record Passage(boolean cut, int toll, List<Toll> payees) {}

    public record Toll(UUID realm, int amount) {}

    private CityTolls() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** Sans rien de fixé : un royaume de joueur ne prend rien, l'ordinateur prend selon son tempérament. La logique est pure. */
    public static Rules byDefault(AiPersonality p) {
        return switch (p) {
            case MARCHAND, PACIFIQUE -> new Rules(2, 5);
            case OPPORTUNISTE, EXPANSIONNISTE -> new Rules(5, 10);
            case FANATIQUE -> new Rules(10, 20);
        };
    }

    public static Rules rules(MinecraftServer server, UUID realm) {
        Rules set = data(server).world().tradeRules().get(realm);
        if (set != null) return set;
        return data(server).world().realm(realm).filter(r -> r.sovereign() == null).map(r -> byDefault(r.personality())).orElse(new Rules(0, 0));
    }

    public static void set(MinecraftServer server, UUID realm, int toll, int duty) {
        data(server).world().tradeRules().put(realm, new Rules(nearest(TOLLS, toll), nearest(DUTIES, duty)));
        data(server).setDirty();
    }

    /** Une valeur venue de l'écran : on ne garde que celles qu'il propose. */
    static int nearest(int[] allowed, int value) {
        int best = allowed[0];
        for (int v : allowed) if (Math.abs(v - value) < Math.abs(best - value)) best = v;
        return best;
    }

    /** Ceux dont cette route traverse les terres, dans l'ordre. */
    public static Set<UUID> crossed(MinecraftServer server, ResourceLocation dim, CityRoads.Route route) {
        Territory.Snapshot map = Territory.snapshot(server, dim);
        Set<UUID> out = new LinkedHashSet<>();
        int n = Math.max(1, (int) (route.length() / SAMPLE));
        for (int i = 0; i <= n; i++) {
            int[] at = route.at((double) i / n);
            map.ownerAt(at[0] + 0.5, at[1] + 0.5).ifPresent(out::add);
        }
        return out;
    }

    /** Ce qu'il faut payer pour passer chez ces royaumes : ni chez soi, ni à destination (la douane s'en charge), ni à qui en dispense. La logique est pure. */
    public static List<Toll> due(Collection<UUID> crossed, UUID home, UUID dest, ToIntFunction<UUID> tollOf, Predicate<UUID> free) {
        List<Toll> out = new ArrayList<>();
        for (UUID realm : crossed) {
            if (realm.equals(home) || realm.equals(dest) || free.test(realm)) continue;
            int toll = tollOf.applyAsInt(realm);
            if (toll > 0) out.add(new Toll(realm, toll));
        }
        return out;
    }

    /** Un traité de commerce, de passage ou une alliance dispensent du péage. */
    private static boolean exempt(MinecraftServer server, UUID a, UUID b) {
        if (a.equals(b)) return true;
        Relation r = data(server).diplomacy().between(a, b);
        long today = RealmService.currentDay(server);
        return r.has(Treaty.COMMERCE, today) || r.has(Treaty.PASSAGE, today) || r.has(Treaty.ALLIANCE, today) || r.has(Treaty.ALLIANCE_DEFENSIVE, today);
    }

    /** Peut-on passer d'ici à là, et à quel prix ? */
    public static Passage passage(MinecraftServer server, Site home, Site dest) {
        Set<UUID> crossed = crossed(server, home.dimension(), CityRoads.between(server, home, dest));
        for (UUID realm : crossed) if (CityCommerce.atWar(server, home.owner(), realm)) return new Passage(true, 0, List.of());
        List<Toll> due = due(crossed, home.owner(), dest.owner(), r -> rules(server, r).toll(), r -> exempt(server, home.owner(), r));
        int total = 0;
        for (Toll t : due) total += t.amount();
        return new Passage(false, total, due);
    }

    /** La caravane paie, sur la bourse de sa ville. */
    public static void pay(MinecraftServer server, Passage passage, Site home) {
        for (Toll t : passage.payees()) {
            home.economy().addPurse(-t.amount());
            credit(server, t.realm(), t.amount());
        }
    }

    /** Ce qu'un royaume prend sur une vente faite chez lui par un étranger. */
    public static int duty(MinecraftServer server, Site home, Site dest, int sale) {
        if (home.owner().equals(dest.owner()) || CityCommerce.fair(server, home, dest)) return 0;
        return sale * rules(server, dest.owner()).duty() / 100;
    }

    /** Au trésor du royaume : celui d'un joueur a ses livres de comptes, celui de l'ordinateur son or. */
    public static void credit(MinecraftServer server, UUID realm, long amount) {
        if (amount <= 0) return;
        Optional<AiRealm> ai = data(server).world().realm(realm);
        if (ai.isPresent()) ai.get().setGold(ai.get().gold() + amount);
        else data(server).realm(realm).ifPresent(r -> TreasuryService.credit(server, r, LedgerCategory.COMMERCE, amount));
    }
}
