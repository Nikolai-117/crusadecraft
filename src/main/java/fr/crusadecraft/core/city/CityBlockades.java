package fr.crusadecraft.core.city;

import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.logic.Fr;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.core.war.HostService;
import fr.crusadecraft.core.war.War;
import fr.crusadecraft.core.war.WarFieldService;
import fr.crusadecraft.core.war.WarMapService;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Une ville coupée du monde (P55) : un blocus qu'on a décidé, un siège, une occupation, une armée ennemie à ses portes. Plus rien n'y
 * entre ni n'en sort : la campagne ne vend plus son grain, les caravanes font demi-tour, et les réserves baissent jusqu'à la famine.
 */
public final class CityBlockades {
    /** Ce que coûte de tenir un blocus, par jour. */
    public static final int COST = 8;
    /** Au plus tant de blocus par royaume. */
    public static final int MAX_PER_REALM = 3;
    /** L'ordinateur ne tient pas un blocus plus longtemps : il a d'autres soucis. */
    static final int AI_DAYS = 8;
    /** Une chance sur tant, par jour, qu'un royaume de l'ordinateur en guerre en décide un. */
    static final int AI_CHANCE = 6;
    /** Il lui faut au moins cet or pour s'y lancer. */
    static final long AI_GOLD = 200;
    /** Une armée à moins de tant de blocs : la ville est à sa merci. */
    static final int HOST_RANGE = 96;
    /** Un siège à moins de tant de blocs de son centre : c'est elle qu'on assiège. */
    static final int SIEGE_RANGE = 64;
    /** On relit la situation au plus tous les tant de ticks. */
    private static final long CACHE_TICKS = 100;

    public enum Reason {
        NONE, BLOCKADE, SIEGE, OCCUPIED, HOST;

        public String key() {
            return "crusadecraft.city.blocked." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Qui la bloque, depuis quand. */
    public record Blockade(UUID by, long since) {
        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("by", by);
            t.putLong("since", since);
            return t;
        }

        public static Blockade load(CompoundTag t) {
            return new Blockade(t.getUUID("by"), t.getLong("since"));
        }
    }

    private static Set<UUID> cached;
    private static long cachedAt = Long.MIN_VALUE;

    private CityBlockades() {}

    public static void onServerStopped() {
        invalidate();
    }

    /** La situation vient de changer. */
    public static void invalidate() {
        cached = null;
        cachedAt = Long.MIN_VALUE;
    }

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    private static List<Site> cities(CrusadeSavedData data) {
        List<Site> out = new ArrayList<>();
        for (Site s : data.world().sites()) if (CityService.city(s) && !s.buildings().isEmpty()) out.add(s);
        out.addAll(data.world().markets());
        return out;
    }

    /** Pourquoi cette ville est coupée du monde, si elle l'est. */
    public static Reason reason(MinecraftServer server, Site s) {
        CrusadeSavedData data = data(server);
        if (data.world().blockades().containsKey(s.id())) return Reason.BLOCKADE;
        if (data.wars().all().isEmpty()) return Reason.NONE;
        for (War w : data.wars().all()) if (s.province() >= 0 && w.involves(s.owner()) && w.occupied().contains(s.province())) return Reason.OCCUPIED;
        for (WarFieldService.SiegeView v : WarFieldService.sieges()) {
            if (v.dim().equals(s.dimension()) && v.defender().equals(s.owner()) && near(v.x() - s.anchor().getX(), v.z() - s.anchor().getZ(), SIEGE_RANGE)) return Reason.SIEGE;
        }
        for (HostService.Pawn h : HostService.pawns(server)) {
            if (h.beaten() || !h.dimension().equals(s.dimension()) || !CityCommerce.atWar(server, h.realm(), s.owner())) continue;
            if (near(h.x() - s.anchor().getX(), h.z() - s.anchor().getZ(), HOST_RANGE)) return Reason.HOST;
        }
        return Reason.NONE;
    }

    private static boolean near(int dx, int dz, int range) {
        return (long) dx * dx + (long) dz * dz <= (long) range * range;
    }

    /** Toutes les villes coupées du monde : on ne se le redemande pas à chaque voyageur. */
    public static Set<UUID> blocked(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (cached != null && now - cachedAt < CACHE_TICKS && now >= cachedAt) return cached;
        Set<UUID> out = new HashSet<>();
        CrusadeSavedData data = data(server);
        if (!data.wars().all().isEmpty() || !data.world().blockades().isEmpty()) {
            for (Site s : cities(data)) if (reason(server, s) != Reason.NONE) out.add(s.id());
        }
        cached = out;
        cachedAt = now;
        return out;
    }

    public static boolean blocked(MinecraftServer server, Site s) {
        return blocked(server).contains(s.id());
    }

    private static int count(CrusadeSavedData data, UUID realm) {
        int n = 0;
        for (Blockade b : data.world().blockades().values()) if (b.by().equals(realm)) n++;
        return n;
    }

    /** Ce royaume met cette ville en blocus. Il faut être en guerre avec elle, et de quoi payer le premier jour. */
    public static Outcome declare(MinecraftServer server, UUID by, Site target) {
        CrusadeSavedData data = data(server);
        if (!CityCommerce.atWar(server, by, target.owner())) return Outcome.no("crusadecraft.city.blockade.no_war", target.name());
        if (data.world().blockades().containsKey(target.id())) return Outcome.no("crusadecraft.city.blockade.already", target.name());
        if (count(data, by) >= MAX_PER_REALM) return Outcome.no("crusadecraft.city.blockade.full", MAX_PER_REALM);
        if (!pay(server, by, COST)) return Outcome.no("crusadecraft.city.blockade.broke", COST);
        put(server, by, target);
        return Outcome.ok(Component.translatable("crusadecraft.city.blockade.started", target.name(), COST));
    }

    private static void put(MinecraftServer server, UUID by, Site target) {
        CrusadeSavedData data = data(server);
        data.world().blockades().put(target.id(), new Blockade(by, RealmService.currentDay(server)));
        invalidate();
        data.setDirty();
        CityRumors.add(server, target, "blocus");
        warn(server, target.owner(), Component.translatable("crusadecraft.city.blockade.toast"), Component.translatable("crusadecraft.city.blockade.toast.body", target.name(), WarMapService.name(data, by)));
    }

    /** Ce royaume lève son blocus. */
    public static Outcome lift(MinecraftServer server, UUID by, Site target) {
        CrusadeSavedData data = data(server);
        Blockade b = data.world().blockades().get(target.id());
        if (b == null || !b.by().equals(by)) return Outcome.no("crusadecraft.city.blockade.not_yours", target.name());
        remove(server, target);
        return Outcome.ok(Component.translatable("crusadecraft.city.blockade.lifted", target.name()));
    }

    private static void remove(MinecraftServer server, Site target) {
        data(server).world().blockades().remove(target.id());
        invalidate();
        data(server).setDirty();
        CityRumors.add(server, target, "blocus_fin");
    }

    private static boolean pay(MinecraftServer server, UUID realm, long amount) {
        Optional<AiRealm> ai = data(server).world().realm(realm);
        if (ai.isPresent()) {
            if (ai.get().gold() < amount) return false;
            ai.get().setGold(ai.get().gold() - amount);
            return true;
        }
        Optional<Realm> r = data(server).realm(realm);
        return r.isPresent() && TreasuryService.spend(server, r.get(), LedgerCategory.ARMEE, amount);
    }

    private static void warn(MinecraftServer server, UUID realm, Component title, Component body) {
        data(server).realm(realm).ifPresent(r -> {
            ServerPlayer ruler = server.getPlayerList().getPlayer(r.ruler());
            if (ruler != null) CrusadeNetwork.toPlayer(ruler, ToastPacket.of(ToastPacket.Kind.WAR, title, body));
        });
        data(server).world().realm(realm).filter(r -> r.sovereign() != null).ifPresent(r -> {
            ServerPlayer sovereign = server.getPlayerList().getPlayer(r.sovereign());
            if (sovereign != null) CrusadeNetwork.toPlayer(sovereign, ToastPacket.of(ToastPacket.Kind.WAR, title, body));
        });
    }

    /** La guerre où ces deux royaumes s'affrontent, pour y porter au compte ce que coûte une famine. */
    private static Optional<War> warBetween(CrusadeSavedData data, UUID a, UUID b) {
        return data.wars().between(a, b);
    }

    /** Une fois par jour : les blocus se paient ou tombent ; une ville qui a faim coûte la guerre à qui la tient ; l'ordinateur décide des siens. */
    public static void onDay(MinecraftServer server, Random random) {
        CrusadeSavedData data = data(server);
        long today = RealmService.currentDay(server);
        for (Map.Entry<UUID, Blockade> e : new ArrayList<>(data.world().blockades().entrySet())) {
            Site target = data.world().site(e.getKey()).orElse(null);
            Blockade b = e.getValue();
            if (target == null) {
                data.world().blockades().remove(e.getKey());
                invalidate();
                continue;
            }
            boolean ai = data.world().realm(b.by()).filter(r -> r.sovereign() == null).isPresent();
            if (!CityCommerce.atWar(server, b.by(), target.owner()) || !pay(server, b.by(), COST) || (ai && today - b.since() >= AI_DAYS)) {
                remove(server, target);
                continue;
            }
            if (target.economy().famine() > 0) {
                warBetween(data, b.by(), target.owner()).ifPresent(w -> {
                    w.addBattle(w.side(b.by()));
                    w.log(today, "crusadecraft.war.event.famine", w.side(b.by()), target.name());
                });
            }
        }
        if (!CrusadeConfig.SERVER.routines.get()) return;
        // L'ordinateur en guerre coupe la ville la plus riche de son ennemi.
        for (AiRealm r : data.world().realms()) {
            if (r.sovereign() != null || r.gold() < AI_GOLD || count(data, r.id()) > 0 || random.nextInt(AI_CHANCE) != 0) continue;
            Site best = null;
            for (Site s : cities(data)) {
                if (!CityCommerce.atWar(server, r.id(), s.owner()) || data.world().blockades().containsKey(s.id())) continue;
                if (best == null || s.economy().purse() > best.economy().purse()) best = s;
            }
            if (best != null && pay(server, r.id(), COST)) put(server, r.id(), best);
        }
    }

    /** Pour un texte : pourquoi cette ville est coupée du monde. */
    public static String explain(MinecraftServer server, Site s) {
        Reason why = reason(server, s);
        return why == Reason.NONE ? "" : Fr.of(Component.translatable(why.key()));
    }
}
