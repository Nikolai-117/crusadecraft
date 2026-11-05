package fr.crusadecraft.core.city;

import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Le commerce du monde (P51) : les lignes qu'un joueur trace depuis le panneau de sa ville, celles que l'ordinateur décide, et le prix des
 * choses, qui monte là où elles manquent. Rien n'y demande un joueur présent : les caravanes partent, arrivent et vendent, qu'on les voie ou non.
 */
public final class CityCommerce {
    /** Au plus tant de lignes partant d'une ville. */
    public static final int LINES_PER_CITY = 8;
    /** Au plus tant de lignes décidées par l'ordinateur dans tout le monde. */
    static final int AI_LINES_MAX = 16;
    /** Ce qu'une ligne laisse toujours au départ : une ville n'exporte pas sa dernière miche. */
    static final int RESERVE = 8;
    /** L'ordinateur ne commerce pas plus loin que ça. */
    static final int AI_RANGE = 2500;
    /** Une ligne de l'ordinateur qui n'a pas servi depuis tant de jours est oubliée. */
    static final int AI_FORGET = 12;
    /** On ne charge pas plus que ça d'un coup. */
    public static final int LOAD_MAX = 64;

    private static final Random RANDOM = new Random();

    private CityCommerce() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    // ------------------------------------------------------------------ les prix

    /** Ce que vaut une marchandise, en temps normal. La logique est pure. */
    public static int basePrice(CityGood g) {
        return switch (g) {
            case OUTILS -> 6;
            case LIVRE, REPAS, FER -> 3;
            case PAIN, VIANDE, CUIR -> 2;
            case BLE, PIERRE, BOIS -> 1;
        };
    }

    /** Son prix dans cette ville : plus elle en manque, plus elle paie. Sans traité avec elle, on n'en tire que la moitié. */
    public static int price(CityGood g, CityEconomy where, boolean fair) {
        return Math.max(1, quote(g, where, fair, 1));
    }

    /** P54 : ce que rapporte une charge entière : à l'unité, les petites marchandises arrondies au même prix partout ne diraient rien. */
    public static int quote(CityGood g, CityEconomy where, boolean fair, int amount) {
        return Math.round(amount * basePrice(g) * where.demand(g) * (fair ? 1f : 0.5f) / 100f);
    }

    /** P54 : ce qui vient de se vendre dans le monde, pour le graphique de l'écran d'économie. */
    static void recordSale(MinecraftServer server, int amount, long money) {
        long[] moved = data(server).world().tradeMoved(), paid = data(server).world().tradeMoney();
        moved[moved.length - 1] += amount;
        paid[paid.length - 1] += money;
    }

    /** P54 : un jour de plus au graphique. */
    static void rollHistory(MinecraftServer server) {
        for (long[] a : List.of(data(server).world().tradeMoved(), data(server).world().tradeMoney())) {
            System.arraycopy(a, 1, a, 0, a.length - 1);
            a[a.length - 1] = 0;
        }
    }

    /** Même royaume, ou traité de commerce : le juste prix. */
    public static boolean fair(MinecraftServer server, Site a, Site b) {
        if (a.owner().equals(b.owner())) return true;
        Relation r = data(server).diplomacy().between(a.owner(), b.owner());
        return r.has(Treaty.COMMERCE, RealmService.currentDay(server));
    }

    /** En guerre, personne ne passe. */
    public static boolean atWar(MinecraftServer server, UUID a, UUID b) {
        return !a.equals(b) && data(server).diplomacy().between(a, b).war();
    }

    // ------------------------------------------------------------------ où aller

    /** Une destination possible, pour le panneau : une vraie ville, ou une ville de la carte. */
    public record Stop(UUID id, String name, boolean ai, boolean war, int distance) {
        /** P57 : le quai d'une colonie. */
        public boolean dock() {
            return CityDocks.isDockId(id);
        }
    }

    /** Où l'on peut aller depuis cette ville, de la plus proche à la plus lointaine. */
    public static List<Stop> stops(MinecraftServer server, Site from) {
        CrusadeSavedData data = data(server);
        List<Stop> out = new ArrayList<>();
        Set<Integer> covered = new HashSet<>();
        if (from.province() >= 0) covered.add(from.province());
        for (Site s : data.world().sites()) {
            if (!CityService.city(s) || s.buildings().isEmpty()) continue;
            if (s.province() >= 0) covered.add(s.province());
            if (s == from || !s.dimension().equals(from.dimension())) continue;
            out.add(new Stop(s.id(), s.name(), false, atWar(server, from.owner(), s.owner()), distance(from, s.anchor().getX(), s.anchor().getZ())));
        }
        // P57 : les colonies des royaumes : leur entrepôt reçoit ce qu'on leur apporte.
        for (fr.crusadecraft.core.realm.Realm r : data.realms()) {
            for (fr.crusadecraft.core.ColonyRef c : r.allColonies()) {
                UUID id = CityDocks.idOf(c);
                if (id.equals(from.id()) || !c.dimension().equals(from.dimension())) continue;
                Optional<net.minecraft.core.BlockPos> at = fr.crusadecraft.compat.minecolonies.MineColoniesBridge.center(c);
                if (at.isPresent()) out.add(new Stop(id, fr.crusadecraft.compat.minecolonies.MineColoniesBridge.info(c).name(), false, atWar(server, from.owner(), r.id()), distance(from, at.get().getX(), at.get().getZ())));
            }
        }
        for (AiRealm r : data.world().realms()) {
            for (AiProvince p : r.provinces()) {
                if (covered.contains(p.id()) || !p.dimension().equals(from.dimension()) || p.seat().equals(net.minecraft.core.BlockPos.ZERO)) continue;
                out.add(new Stop(CityMarkets.idOf(p.id()), p.name(), true, atWar(server, from.owner(), r.id()), distance(from, p.seat().getX(), p.seat().getZ())));
            }
        }
        out.sort(Comparator.comparingInt(Stop::distance));
        return out;
    }

    private static int distance(Site from, int x, int z) {
        return (int) Math.hypot(from.anchor().getX() - x, from.anchor().getZ() - z);
    }

    // ------------------------------------------------------------------ les lignes

    public static Optional<CityLine> line(MinecraftServer server, UUID id) {
        if (id == null) return Optional.empty();
        for (CityLine l : data(server).world().lines()) if (l.id.equals(id)) return Optional.of(l);
        return Optional.empty();
    }

    /** Une ligne de plus, depuis cette ville. */
    public static Outcome create(MinecraftServer server, Site from, UUID to, CityGood good, int amount, CityGood back, int every, int escort, boolean pave) {
        CrusadeSavedData data = data(server);
        if (from.id().equals(to)) return Outcome.no("crusadecraft.city.line.same");
        Optional<Site> dest = CityMarkets.of(server, to);
        if (dest.isEmpty()) return Outcome.no("crusadecraft.city.unknown");
        if (atWar(server, from.owner(), dest.get().owner())) return Outcome.no("crusadecraft.city.line.war", dest.get().name());
        int mine = 0;
        for (CityLine l : data.world().lines()) {
            if (!l.from.equals(from.id())) continue;
            mine++;
            if (l.to.equals(dest.get().id()) && l.good == good) return Outcome.no("crusadecraft.city.line.exists");
        }
        if (mine >= LINES_PER_CITY) return Outcome.no("crusadecraft.city.line.full", LINES_PER_CITY);
        CityLine l = new CityLine(UUID.randomUUID(), from.id(), dest.get().id());
        l.good = good;
        l.amount = Math.max(1, Math.min(LOAD_MAX, amount));
        l.back = back;
        l.every = Math.max(1, Math.min(8, every));
        l.escort = Math.max(0, Math.min(CityTravel.ESCORT, escort));
        l.pave = pave;
        // Elle part au prochain quart de journée : on veut la voir tourner, pas l'attendre.
        l.waited = l.every;
        data.world().lines().add(l);
        data.setDirty();
        // Le tracé se cherche tout de suite : il sera prêt pour le premier départ.
        CityRoads.between(server, from, dest.get());
        return Outcome.ok(Component.translatable("crusadecraft.city.line.created", from.name(), dest.get().name()));
    }

    /** Quatre fois par jour de jeu : les lignes dont c'est le tour envoient leur caravane. */
    public static void pulse(MinecraftServer server) {
        if (!CrusadeConfig.SERVER.routines.get()) return;
        CrusadeSavedData data = data(server);
        // P57 : ce que les quais ont reçu passe dans les entrepôts, et ce que ceux-ci contiennent devient vendable.
        CityDocks.pulse(server);
        Set<UUID> running = new HashSet<>();
        for (Person p : data.lineage().persons()) if (p.alive() && p.trip() != null && p.trip().line() != null) running.add(p.trip().line());
        for (CityLine l : new ArrayList<>(data.world().lines())) {
            Optional<Site> home = data.world().site(l.from), dest = data.world().site(l.to);
            if (home.isEmpty() || dest.isEmpty()) {
                data.world().lines().remove(l);
                continue;
            }
            if (!l.active) continue;
            l.waited++;
            if (l.waited < l.every || running.contains(l.id)) continue;
            if (atWar(server, home.get().owner(), dest.get().owner())) {
                l.status = "war";
                continue;
            }
            // P55 : une ville coupée du monde, une route qui passe chez l'ennemi, un péage qu'on ne peut pas payer.
            if (CityBlockades.blocked(server, home.get()) || CityBlockades.blocked(server, dest.get())) {
                l.status = "blockade";
                continue;
            }
            CityTolls.Passage passage = CityTolls.passage(server, home.get(), dest.get());
            if (passage.cut()) {
                l.status = "cut";
                continue;
            }
            if (home.get().economy().purse() < passage.toll()) {
                l.status = "toll";
                continue;
            }
            if (dispatch(server, l, home.get(), dest.get())) {
                l.waited = 0;
                CityTolls.pay(server, passage, home.get());
                l.tolls += passage.toll();
            }
        }
    }

    /** Une caravane part sur cette ligne, s'il y a quelqu'un pour la mener et quelque chose à charger. */
    static boolean dispatch(MinecraftServer server, CityLine l, Site home, Site dest) {
        // P59 : on charge d'après ce que l'entrepôt contient à cet instant, pas d'après son état au dernier passage.
        CityDocks.touch(server, home);
        CityEconomy eco = home.economy();
        CityGood good = l.good != null ? l.good : CityTravel.richest(eco);
        int amount = good == null ? 0 : Math.min(l.amount, eco.stock(good) - RESERVE);
        int money = 0;
        if (amount <= 0) {
            // Rien à vendre : elle part quand même si quelque chose manque ici, avec de quoi l'acheter.
            good = null;
            amount = 0;
            if (CityTravel.lacking(eco) == null || eco.purse() < 16) {
                l.status = "empty";
                return false;
            }
            money = (int) Math.min(CityTravel.PURSE_CARRIED, eco.purse() / 4);
        }
        Person lead = merchant(server, home);
        if (lead == null) {
            l.status = "nobody";
            return false;
        }
        if (good != null) eco.take(good, amount);
        if (money > 0) eco.addPurse(-money);
        CityTravel.departLine(server, l, home, dest, lead, good, amount, money);
        CityDocks.touch(server, home);
        l.status = "road";
        l.lastDay = RealmService.currentDay(server);
        return true;
    }

    /**
     * Qui mène la caravane : un marchand ; à défaut quelqu'un sans métier ; à défaut un artisan qui n'est pas un personnage écrit à la main. Personne si
     * la moitié de la ville est déjà sur les routes : une ville ne se vide pas pour son commerce.
     */
    private static Person merchant(MinecraftServer server, Site home) {
        LineageState state = data(server).lineage();
        Person jobless = null, worker = null, trader = null;
        int adults = 0, away = 0;
        for (CityBuilding b : home.buildings()) {
            if (b.function() == null) continue;
            boolean guard = b.function() == BuildingFunction.CASERNE || b.function() == BuildingFunction.TOUR || b.function() == BuildingFunction.CHATEAU;
            for (UUID id : b.occupants()) {
                Person p = state.person(id).filter(Person::alive).orElse(null);
                if (p == null || p.age() < 16) continue;
                adults++;
                if (p.trip() != null) away++;
                if (!CityTravel.free(p) || guard) continue;
                if (b.function() == BuildingFunction.MARCHE || b.function() == BuildingFunction.ENTREPOT) {
                    if (trader == null) trader = p;
                } else if (!p.notable().isEmpty()) {
                    continue;
                } else if (p.job().isEmpty()) {
                    if (jobless == null) jobless = p;
                } else if (worker == null) {
                    worker = p;
                }
            }
        }
        if (away * 2 >= adults) return null;
        return trader != null ? trader : jobless != null ? jobless : worker;
    }

    /** La caravane d'une ligne a vendu là-bas. */
    static void onSold(MinecraftServer server, UUID lineId, int amount, long money) {
        line(server, lineId).ifPresent(l -> {
            l.trips++;
            l.moved += amount;
            l.earned += money;
            l.status = "sold";
            l.lastDay = RealmService.currentDay(server);
        });
    }

    // ------------------------------------------------------------------ ce que décide l'ordinateur

    /** Une fois par jour : une ville de l'ordinateur qui a de quoi vendre se trouve un client ; les lignes qui ne servent plus sont oubliées. */
    public static void planAi(MinecraftServer server, Random random) {
        if (!CrusadeConfig.SERVER.routines.get()) return;
        CrusadeSavedData data = data(server);
        long today = RealmService.currentDay(server);
        int ai = 0;
        for (CityLine l : new ArrayList<>(data.world().lines())) {
            if (!l.ai) continue;
            if (today - l.lastDay > AI_FORGET) data.world().lines().remove(l);
            else ai++;
        }
        if (ai >= AI_LINES_MAX) return;
        // Une ville au hasard parmi celles de l'ordinateur : une province, ou une nation qui n'a pas de souverain.
        List<Runnable> candidates = new ArrayList<>();
        for (AiRealm r : data.world().realms()) {
            if (r.sovereign() != null) continue;
            for (AiProvince p : r.provinces()) {
                if (!p.seat().equals(net.minecraft.core.BlockPos.ZERO)) candidates.add(() -> plan(server, CityMarkets.ensure(server, r, p), random, today));
            }
        }
        if (!candidates.isEmpty()) candidates.get(random.nextInt(candidates.size())).run();
    }

    private static void plan(MinecraftServer server, Site from, Random random, long today) {
        CrusadeSavedData data = data(server);
        for (CityLine l : data.world().lines()) if (l.from.equals(from.id())) return;
        List<Stop> near = new ArrayList<>();
        // L'ordinateur ne livre pas d'office une colonie : son entrepôt ne se remplit que de ce que son maître a demandé.
        for (Stop s : stops(server, from)) if (!s.war() && !s.dock() && s.distance() <= AI_RANGE && !CityBlockades.blocked(server).contains(s.id())) near.add(s);
        if (near.isEmpty()) return;
        // Les voisins d'abord, et de préférence une vraie ville : c'est là que le commerce se voit.
        Stop pick = null;
        for (Stop s : near.subList(0, Math.min(6, near.size()))) if (!s.ai() && random.nextInt(2) == 0) pick = s;
        if (pick == null) pick = near.get(random.nextInt(Math.min(6, near.size())));
        Optional<Site> dest = CityMarkets.of(server, pick.id());
        if (dest.isEmpty()) return;
        CityLine l = new CityLine(UUID.randomUUID(), from.id(), dest.get().id());
        l.ai = true;
        l.amount = 24;
        l.every = 4;
        l.escort = 1;
        l.lastDay = today;
        data.world().lines().add(l);
        CityRoads.between(server, from, dest.get());
        CityJournal.add(server, dest.get(), CityJournal.Kind.ROAD, fr.crusadecraft.core.logic.Fr.key("crusadecraft.city.journal.new_line", from.name()));
        data.setDirty();
    }
}
