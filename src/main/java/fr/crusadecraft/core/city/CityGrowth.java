package fr.crusadecraft.core.city;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.build.plan.AiTowns;
import fr.crusadecraft.core.build.plan.Journals;
import fr.crusadecraft.core.logic.Fr;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Un marché qui réussit finit par bâtir (P58). Il gagne en prospérité à chaque caravane et quand sa bourse est pleine, il en perd quand il a
 * faim. Mûr, il attend qu'un joueur approche : son chantier s'ouvre alors, aux bâtiments du style de son royaume, sous les mêmes précautions
 * que les villes de l'ordinateur : jamais là où des joueurs ont longtemps vécu, ni près d'une colonie, ni sur les terres d'un joueur. Ses
 * réserves, sa bourse et ses lignes passent à la ville quand elle a des habitants.
 */
public final class CityGrowth {
    /** Ce que rapporte une caravane qui vend ou qui rentre. */
    static final int PER_SALE = 2;
    /** Une bourse de cette taille : la prospérité monte toute seule, doucement. */
    static final long PURSE_GOOD = 150, PURSE_RICH = 400;
    /** On ne réessaie pas d'habiter une ville sans portes plus d'une fois tous les tant de ticks. */
    private static final long RETRY_TICKS = 6000;

    private static final Map<UUID, Long> TRIED = new HashMap<>();

    private CityGrowth() {}

    public static void onServerStopped() {
        TRIED.clear();
    }

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** Une journée de plus pour ce marché. La logique est pure. */
    public static int next(int prosperity, long purse, int famine, boolean besieged, int cap) {
        int p = prosperity;
        if (famine > 0 || besieged) p -= 3;
        else if (purse >= PURSE_RICH) p += 2;
        else if (purse >= PURSE_GOOD) p += 1;
        return Math.max(0, Math.min(cap, p));
    }

    public static int required() {
        return CrusadeConfig.SERVER.marketGrowAt.get();
    }

    public static boolean mature(Site market) {
        return market.prosperity() >= required();
    }

    /** Une caravane vient de vendre ici, ou y rentre : un marché qui commerce est un marché qui vit. */
    static void onSale(MinecraftServer server, Site s) {
        if (!data(server).world().isMarket(s.id()) || CityDocks.isDockId(s.id())) return;
        s.setProsperity(Math.min(required(), s.prosperity() + PER_SALE));
    }

    /** Sa journée, et ce qu'on en dit. */
    static void onDay(MinecraftServer server, Site market, boolean besieged) {
        boolean was = mature(market);
        market.setProsperity(next(market.prosperity(), market.economy().purse(), market.economy().famine(), besieged, required()));
        if (!was && mature(market)) CityRumors.add(server, market, "prospere");
    }

    /** Toutes les dix secondes : les marchés mûrs qu'un joueur approche ouvrent leur chantier ; les villes finies reçoivent leurs habitants. */
    public static int tick(MinecraftServer server) {
        int opened = 0;
        if (CrusadeConfig.SERVER.marketsBuild.get()) opened = open(server);
        settleFinished(server);
        return opened;
    }

    private static int open(MinecraftServer server) {
        CrusadeSavedData data = data(server);
        int building = 0;
        for (Site s : data.world().sites()) if (s.town() != null && !s.built() && s.auto()) building++;
        int max = CrusadeConfig.SERVER.aiTownsAtOnce.get(), range = CrusadeConfig.SERVER.aiTownRange.get(), opened = 0;
        for (Site m : new ArrayList<>(data.world().markets())) {
            if (building + opened >= max) break;
            if (CityDocks.isDockId(m.id()) || !mature(m) || CityBlockades.blocked(server, m)) continue;
            AiRealm realm = null;
            AiProvince province = null;
            for (AiRealm r : data.world().realms()) {
                for (AiProvince p : r.provinces()) {
                    if (p.id() == m.province()) {
                        realm = r;
                        province = p;
                    }
                }
            }
            // Ni sur les terres d'un joueur, ni dans une nation qu'il dirige : c'est à lui d'y bâtir.
            if (province == null || realm.sovereign() != null) continue;
            if (province.holder() != null && data.realm(province.holder()).isPresent()) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, province.dimension()));
            if (level == null || !level.hasChunkAt(province.seat()) || !AiTowns.playerNear(level, province.seat(), range)) continue;
            if (AiTowns.lived(level, province.seat()) || AiTowns.colonyNear(level, province.seat())) continue;
            if (start(server, province)) opened++;
        }
        return opened;
    }

    /** Le chantier s'ouvre. */
    public static boolean start(MinecraftServer server, AiProvince province) {
        if (SiteService.townOf(server, province.id()).isPresent()) return false;
        for (Site s : data(server).world().sites()) if (s.kind() == SiteKind.VILLAGE && s.province() == province.id()) return false;
        Optional<Site> town = SiteService.startTown(server, province.id(), false, true);
        if (town.isEmpty()) return false;
        town.get().setGrown(true);
        CrusadeCraft.LOGGER.info("CrusadeCraft : le marché de {} a assez prospéré : il devient une ville", province.name());
        return true;
    }

    private static void settleFinished(MinecraftServer server) {
        CrusadeSavedData data = data(server);
        for (Site s : new ArrayList<>(data.world().sites())) {
            if (!s.grown() || !s.built() || !s.buildings().isEmpty() || Journals.pasting(server, s.id())) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, s.dimension()));
            if (level == null || !CityService.watched(level, s)) continue;
            Long tried = TRIED.get(s.id());
            if (tried != null && level.getGameTime() - tried < RETRY_TICKS) continue;
            TRIED.put(s.id(), level.getGameTime());
            settle(server, level, s);
        }
    }

    /** La ville est finie : ses portes disent où sont ses bâtiments, ses meubles ce qu'ils sont, et elle se peuple. Sans meubles qui parlent : une maison. */
    public static int settle(MinecraftServer server, ServerLevel level, Site s) {
        CityService.recognize(level, s);
        Random random = new Random(s.id().getLeastSignificantBits());
        int n = 0;
        for (CityBuilding b : s.buildings()) {
            if (b.function() != null) continue;
            b.setFunction(b.suggestion() != null ? b.suggestion() : BuildingFunction.MAISON);
            CityService.settle(server, s, b, random.nextLong());
            n++;
        }
        if (n > 0) CityJournal.add(server, s, CityJournal.Kind.LIFE, Fr.key("crusadecraft.city.journal.grown", s.name(), n));
        data(server).setDirty();
        return n;
    }

    /** La vraie ville remplace son marché : elle reprend ses réserves, sa bourse, ses lignes et ses contrats. */
    static void absorb(MinecraftServer server, Site market, Site city) {
        CrusadeSavedData data = data(server);
        CityEconomy from = market.economy(), to = city.economy();
        for (CityGood g : CityGood.values()) to.add(g, from.stock(g));
        to.addPurse(from.purse());
        for (CityLine l : data.world().lines()) {
            if (l.from.equals(market.id())) l.from = city.id();
            if (l.to.equals(market.id())) l.to = city.id();
        }
        for (CityContract c : data.world().contracts()) if (c.to.equals(market.id())) c.to = city.id();
        // P59 : un blocus décidé pendant le chantier suit la ville, et son camp avec lui.
        CityBlockades.Blockade held = data.world().blockades().remove(market.id());
        if (held != null) {
            data.world().blockades().put(city.id(), held);
            CityBlockades.invalidate();
        }
        data.setDirty();
    }
}
