package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.plan.PlanTool;
import fr.crusadecraft.core.logic.Fr;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.war.WarMapService;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.WorldEconomyPackets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'économie du monde d'un coup d'œil (P54) : les villes et leur bourse, les prix ville par ville, les lignes et ce qu'elles
 * rapportent, ceux qui sont sur les routes, les royaumes. Rien de calculé ici ne décide de quoi que ce soit : c'est une lecture.
 * On ne voit que ce qu'on connaît : ses terres, et les royaumes qu'on a découverts.
 */
public final class WorldEconomy {
    static final int CITIES_SHOWN = 256, LINES_SHOWN = 128, ROADS_SHOWN = 64;
    /** Les prix qu'on affiche sont ceux d'une charge de caravane : un prix à l'unité serait trop grossier pour lire un écart. */
    public static final int QUOTE_FOR = CityTravel.LOAD;

    private WorldEconomy() {}

    /** L'écran, ou, s'il est déjà ouvert, ses chiffres à jour. */
    public static void send(ServerPlayer player, boolean refresh) {
        CrusadeNetwork.toPlayer(player, new WorldEconomyPackets.Open(view(player), refresh));
    }

    private static boolean visible(CrusadeSavedData data, UUID owner, ServerPlayer player, boolean all) {
        return all || WorldService.known(data, owner, player);
    }

    private static List<Site> cities(CrusadeSavedData data) {
        List<Site> out = new ArrayList<>();
        for (Site s : data.world().sites()) if (CityService.city(s) && !s.buildings().isEmpty()) out.add(s);
        out.addAll(data.world().markets());
        return out;
    }

    public static CompoundTag view(ServerPlayer player) {
        MinecraftServer server = player.server;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        boolean all = PlanTool.allowed(player);
        UUID mine = myRealm(player);
        java.util.Set<UUID> blocked = CityBlockades.blocked(server);
        CompoundTag t = new CompoundTag();
        t.putInt("quoteFor", QUOTE_FOR);

        // Les villes.
        Map<UUID, long[]> perRealm = new HashMap<>();
        ListTag cities = new ListTag();
        for (Site s : cities(data)) {
            if (!visible(data, s.owner(), player, all) || cities.size() >= CITIES_SHOWN) continue;
            CityEconomy e = s.economy();
            int people = data.world().isMarket(s.id()) ? 0 : CityService.people(server, s);
            CompoundTag x = new CompoundTag();
            x.putUUID("id", s.id());
            x.putString("name", s.name());
            x.putString("realm", Fr.of(WarMapService.name(data, s.owner())));
            x.putBoolean("mine", s.owner().equals(mine));
            x.putBoolean("market", data.world().isMarket(s.id()));
            x.putBoolean("dock", CityDocks.isDockId(s.id()));
            x.putInt("people", people);
            x.putLong("purse", e.purse());
            x.putInt("famine", e.famine());
            // P55 : coupée du monde, et ce qu'on peut y faire.
            if (blocked.contains(s.id())) x.putString("blocked", CityBlockades.explain(server, s));
            CityBlockades.Blockade held = data.world().blockades().get(s.id());
            x.putBoolean("byMe", held != null && held.by().equals(mine));
            x.putBoolean("canBlock", held == null && mine != null && CityCommerce.atWar(server, mine, s.owner()));
            x.putInt("distance", s.dimension().equals(player.level().dimension().location()) ? (int) Math.hypot(player.getX() - s.anchor().getX(), player.getZ() - s.anchor().getZ()) : -1);
            CompoundTag stock = new CompoundTag(), quote = new CompoundTag();
            for (CityGood g : CityGood.values()) {
                stock.putInt(g.name(), e.stock(g));
                quote.putInt(g.name(), CityCommerce.quote(g, e, true, QUOTE_FOR));
            }
            x.put("stock", stock);
            x.put("quote", quote);
            cities.add(x);
            long[] r = perRealm.computeIfAbsent(s.owner(), k -> new long[5]);
            r[0]++;
            r[1] += people;
            r[2] += e.purse();
        }
        t.put("cities", cities);

        // Les lignes, les plus rentables d'abord.
        List<CityLine> sorted = new ArrayList<>(data.world().lines());
        sorted.sort((a, b) -> Long.compare(b.earned, a.earned));
        ListTag lines = new ListTag();
        for (CityLine l : sorted) {
            Site from = data.world().site(l.from).orElse(null), to = data.world().site(l.to).orElse(null);
            if (from == null || to == null) continue;
            long[] r = perRealm.computeIfAbsent(from.owner(), k -> new long[5]);
            r[3]++;
            r[4] += l.earned;
            if ((!visible(data, from.owner(), player, all) && !visible(data, to.owner(), player, all)) || lines.size() >= LINES_SHOWN) continue;
            CompoundTag x = new CompoundTag();
            x.putString("from", from.name());
            x.putString("to", to.name());
            if (l.good != null) x.putString("good", l.good.name());
            x.putInt("amount", l.amount);
            x.putInt("trips", l.trips);
            x.putLong("moved", l.moved);
            x.putLong("earned", l.earned);
            x.putLong("tolls", l.tolls);
            x.putString("status", l.active ? l.status : "paused");
            x.putBoolean("ai", l.ai);
            x.putBoolean("mine", from.owner().equals(mine));
            lines.add(x);
        }
        t.put("lines", lines);

        // Ceux qui sont sur les routes.
        ListTag roads = new ListTag();
        for (CityTravel.Pawn p : CityTravel.pawns(server)) {
            UUID owner = data.world().site(p.home()).map(Site::owner).orElse(null);
            if (owner == null || !visible(data, owner, player, all) || roads.size() >= ROADS_SHOWN) continue;
            CompoundTag x = new CompoundTag();
            x.putString("name", p.name());
            x.putString("from", p.homeName());
            x.putString("to", p.destName());
            x.putString("stage", p.stage().name());
            x.putInt("percent", p.percent());
            x.putBoolean("caravan", p.caravan());
            x.putString("cargo", p.cargo());
            roads.add(x);
        }
        t.put("roads", roads);
        t.put("moved", new LongArrayTag(data.world().tradeMoved()));
        t.put("money", new LongArrayTag(data.world().tradeMoney()));

        // Les royaumes.
        ListTag realms = new ListTag();
        for (AiRealm r : data.world().realms()) {
            if (visible(data, r.id(), player, all)) realms.add(realm(server, data, r.id(), r.gold(), perRealm, mine));
        }
        for (Realm r : data.realms()) realms.add(realm(server, data, r.id(), r.gold(), perRealm, mine));
        t.put("realms", realms);
        // P56 : ce que les villes demandent aux joueurs.
        t.put("contracts", CityContracts.view(player, all));
        t.put("orders", CityContracts.orders(player, all));
        return t;
    }

    /** Le royaume qu'il dirige : le sien, ou la nation dont il est le souverain. */
    public static UUID myRealm(ServerPlayer player) {
        return RealmService.realmOf(player.server, player.getUUID()).map(Realm::id)
                .orElseGet(() -> fr.crusadecraft.core.nation.NationService.ruledBy(player.server, player.getUUID()).map(AiRealm::id).orElse(null));
    }

    /** Ce qu'on fait depuis l'écran : mettre une ville en blocus, ou lever le sien. */
    public static fr.crusadecraft.core.council.CouncilActions.Outcome act(ServerPlayer player, CompoundTag c) {
        if (c.hasUUID("accept")) return CityContracts.accept(player, c.getUUID("accept"));
        if (c.hasUUID("abandon")) return CityContracts.abandon(player, c.getUUID("abandon"));
        if (c.hasUUID("deliver")) return CityContracts.deliverHere(player, c.getUUID("deliver"));
        UUID me = myRealm(player);
        if (me == null) return fr.crusadecraft.core.council.CouncilActions.Outcome.no("crusadecraft.realm.none");
        if (!c.hasUUID("site")) return fr.crusadecraft.core.council.CouncilActions.Outcome.no("crusadecraft.city.unknown");
        Site s = CrusadeSavedData.get(player.server).world().site(c.getUUID("site")).orElse(null);
        if (s == null) return fr.crusadecraft.core.council.CouncilActions.Outcome.no("crusadecraft.city.unknown");
        return c.getBoolean("lift") ? CityBlockades.lift(player.server, me, s) : CityBlockades.declare(player.server, me, s);
    }

    private static CompoundTag realm(MinecraftServer server, CrusadeSavedData data, UUID id, long gold, Map<UUID, long[]> perRealm, UUID mine) {
        long[] r = perRealm.getOrDefault(id, new long[5]);
        CompoundTag x = new CompoundTag();
        CityTolls.Rules rules = CityTolls.rules(server, id);
        x.putInt("toll", rules.toll());
        x.putInt("duty", rules.duty());
        x.put("name", StringTag.valueOf(Fr.of(WarMapService.name(data, id))));
        x.putInt("color", WarMapService.color(data, id));
        x.putBoolean("mine", id.equals(mine));
        x.putLong("gold", gold);
        x.putLong("cities", r[0]);
        x.putLong("people", r[1]);
        x.putLong("purses", r[2]);
        x.putLong("lines", r[3]);
        x.putLong("earned", r[4]);
        return x;
    }
}
