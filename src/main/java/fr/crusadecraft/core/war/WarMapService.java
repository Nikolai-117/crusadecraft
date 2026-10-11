package fr.crusadecraft.core.war;

import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.diplomacy.Diplomacy;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.data.CrusadeSavedData;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * La carte de guerre (P18) : qui la voit, qui la trace, et ce qu'on y montre. Chaque royaume de joueur trace son
 * propre plan, par la main de son souverain ; il voit aussi ceux de ses alliés, de son suzerain et de ses vassaux.
 * Les sièges en cours qui les concernent s'y ajoutent d'eux-mêmes.
 */
public final class WarMapService {
    /** Chunks peints au plus d'un coup. */
    public static final int MAX_CELLS = 4096;
    /** Une flèche plus courte qu'un chunk n'en est pas une. */
    static final int MIN_ARROW = 16;

    private WarMapService() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** Le royaume dont ce joueur trace le plan : le sien. */
    static Optional<Realm> author(ServerPlayer player) {
        return RealmService.realmOf(player.server, player.getUUID());
    }

    /** Les royaumes dont ce royaume voit les plans : lui-même, ses alliés, son suzerain, ses vassaux. */
    public static Set<UUID> sharedWith(MinecraftServer server, UUID realm) {
        Set<UUID> out = new LinkedHashSet<>();
        out.add(realm);
        Diplomacy dip = data(server).diplomacy();
        long today = RealmService.currentDay(server);
        for (Relation r : dip.all()) {
            if (!r.involves(realm)) continue;
            if (r.has(Treaty.ALLIANCE, today) || r.has(Treaty.ALLIANCE_DEFENSIVE, today)) out.add(r.other(realm));
        }
        dip.suzerainOf(realm).ifPresent(out::add);
        out.addAll(dip.vassalsOf(realm));
        return out;
    }

    public static Outcome paint(ServerPlayer player, WarMap.Zone zone, long[] cells) {
        Optional<Realm> realm = author(player);
        if (realm.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        if (cells.length == 0) return Outcome.no("crusadecraft.warmap.nothing");
        if (cells.length > MAX_CELLS) return Outcome.no("crusadecraft.warmap.too_big", MAX_CELLS);
        int changed = data(player.server).warMap().plan(realm.get().id()).paint(player.level().dimension().location(), cells, zone);
        if (changed == 0) return Outcome.no("crusadecraft.warmap.nothing");
        data(player.server).setDirty();
        Component what = Component.translatable(zone == null ? "crusadecraft.warmap.tool.erase" : zoneKey(zone));
        return Outcome.ok(Component.translatable(zone == null ? "crusadecraft.warmap.erased" : "crusadecraft.warmap.painted", changed, what));
    }

    public static Outcome arrow(ServerPlayer player, int x0, int z0, int x1, int z1) {
        Optional<Realm> realm = author(player);
        if (realm.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        if (Math.hypot(x1 - x0, z1 - z0) < MIN_ARROW) return Outcome.no("crusadecraft.warmap.arrow.short");
        boolean ok = data(player.server).warMap().plan(realm.get().id())
                .addArrow(new WarMap.Arrow(UUID.randomUUID(), player.level().dimension().location(), x0, z0, x1, z1));
        if (!ok) return Outcome.no("crusadecraft.warmap.full", WarMap.MAX_ARROWS);
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.warmap.arrow.added"));
    }

    public static Outcome pin(ServerPlayer player, int x, int z, WarMap.Mark kind, String note) {
        Optional<Realm> realm = author(player);
        if (realm.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        String text = cleanNote(note);
        boolean ok = data(player.server).warMap().plan(realm.get().id())
                .addPin(new WarMap.Pin(UUID.randomUUID(), player.level().dimension().location(), x, z, kind, text));
        if (!ok) return Outcome.no("crusadecraft.warmap.full", WarMap.MAX_PINS);
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.warmap.pin.added"));
    }

    /** Une note sans codes de mise en forme ni sauts de ligne, et pas plus longue que permis. */
    static String cleanNote(String note) {
        if (note == null) return "";
        String text = note.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", " ").strip();
        return text.length() > WarMap.MAX_NOTE ? text.substring(0, WarMap.MAX_NOTE) : text;
    }

    /** Retire une flèche ou un repère de son propre plan. */
    public static Outcome remove(ServerPlayer player, UUID id) {
        Optional<Realm> realm = author(player);
        if (realm.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        Optional<WarMap.Plan> plan = data(player.server).warMap().find(realm.get().id());
        if (plan.isEmpty() || !plan.get().remove(id)) return Outcome.no("crusadecraft.warmap.not_yours");
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.warmap.removed"));
    }

    /** Efface tout son plan, dans toutes les dimensions. */
    public static Outcome clear(ServerPlayer player) {
        Optional<Realm> realm = author(player);
        if (realm.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        Optional<WarMap.Plan> plan = data(player.server).warMap().find(realm.get().id());
        if (plan.isEmpty() || plan.get().isEmpty()) return Outcome.no("crusadecraft.warmap.nothing");
        plan.get().clear();
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.warmap.cleared"));
    }

    /** Ce que la carte du mod montre de la guerre à ce joueur, dans cette dimension. */
    public static MapView.War view(ServerPlayer player, ResourceLocation dim) {
        MinecraftServer server = player.server;
        CrusadeSavedData data = data(server);
        Optional<Realm> mine = author(player);
        if (mine.isEmpty()) return MapView.War.NONE;
        Set<UUID> shared = sharedWith(server, mine.get().id());
        List<MapView.WarPlan> plans = new ArrayList<>();
        for (UUID id : shared) {
            Optional<WarMap.Plan> plan = data.warMap().find(id);
            boolean own = id.equals(mine.get().id());
            if (plan.isEmpty() && !own) continue;
            long[] keys = new long[0];
            byte[] kinds = new byte[0];
            List<WarMap.Arrow> arrows = List.of();
            List<WarMap.Pin> pins = List.of();
            if (plan.isPresent()) {
                Long2ByteMap zones = plan.get().zones(dim);
                keys = zones.keySet().toLongArray();
                Arrays.sort(keys);
                kinds = new byte[keys.length];
                for (int i = 0; i < keys.length; i++) kinds[i] = zones.get(keys[i]);
                arrows = plan.get().arrows().stream().filter(a -> a.dim().equals(dim)).toList();
                pins = plan.get().pins().stream().filter(p -> p.dim().equals(dim)).toList();
            }
            plans.add(new MapView.WarPlan(id, color(data, id), own, name(data, id), keys, kinds, arrows, pins));
        }
        List<MapView.SiegeMark> sieges = new ArrayList<>();
        for (WarFieldService.SiegeView s : WarFieldService.sieges()) {
            if (!s.dim().equals(dim)) continue;
            if (!shared.contains(s.attacker()) && !shared.contains(s.defender())) continue;
            sieges.add(new MapView.SiegeMark(s.x(), s.z(), color(data, s.attacker()), name(data, s.attacker()), name(data, s.defender()), s.garrison()));
        }
        // L'état de ses guerres : les provinces prises par l'un ou l'autre camp, et qui se bat contre qui.
        List<MapView.Occupation> occupied = new ArrayList<>();
        Set<UUID> friends = new LinkedHashSet<>(), foes = new LinkedHashSet<>();
        friends.add(mine.get().id());
        for (War w : data.wars().involving(mine.get().id())) {
            int side = w.side(mine.get().id());
            for (UUID r : sides(w)) {
                int s = w.side(r);
                if (s == side) friends.add(r);
                else if (s != 0) foes.add(r);
            }
            int occupier = color(data, w.attacker());
            for (int province : w.occupied()) occupied.add(new MapView.Occupation(province, occupier));
        }
        return new MapView.War(true, plans, sieges, occupied, List.copyOf(friends), List.copyOf(foes));
    }

    /** Tous ceux qui prennent part à une guerre. */
    static Set<UUID> sides(War w) {
        Set<UUID> out = new LinkedHashSet<>();
        out.add(w.attacker());
        out.add(w.defender());
        out.addAll(w.attackerAllies());
        out.addAll(w.defenderAllies());
        return out;
    }

    static String zoneKey(WarMap.Zone zone) {
        return "crusadecraft.warmap.zone." + zone.name().toLowerCase(Locale.ROOT);
    }

    public static int color(CrusadeSavedData data, UUID id) {
        Optional<AiRealm> ai = data.world().realm(id);
        if (ai.isPresent()) return ai.get().color() & 0xFFFFFF;
        return data.realm(id).map(r -> r.color() & 0xFFFFFF).orElse(0x808080);
    }

    public static Component name(CrusadeSavedData data, UUID id) {
        Optional<AiRealm> ai = data.world().realm(id);
        if (ai.isPresent()) return AiNames.realm(ai.get().rank(), ai.get().place());
        return Component.literal(data.realm(id).map(Realm::name).orElse("?"));
    }
}
