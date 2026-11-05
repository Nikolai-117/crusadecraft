package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.diplomacy.Good;
import fr.crusadecraft.core.diplomacy.TradeService;
import fr.crusadecraft.core.lineage.CommunityKind;
import fr.crusadecraft.core.lineage.LineageGenerator;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Le marché d'une ville de l'ordinateur (P51). La plupart de ses villes n'existent que sur la carte : pas un bloc, pas un habitant. Pour
 * commercer, il leur faut pourtant des réserves, une bourse et des marchands. C'est ce qu'est un marché : une ville sans murs, tenue à part des
 * constructions du monde, qui produit ce que sa terre donne et consomme le reste. Il ne naît que lorsqu'une ligne en a besoin.
 */
public final class CityMarkets {
    /** La partie haute de l'identifiant d'un marché : le bas est le numéro de la province. */
    private static final long MARK = 0x4D41524348455F31L;
    /** Une bourse de marché ne monte pas au-delà : le surplus va au trésor de son royaume. */
    static final long PURSE_MAX = 800;

    private CityMarkets() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    public static UUID idOf(int province) {
        return new UUID(MARK, province);
    }

    public static boolean isMarketId(UUID id) {
        return id.getMostSignificantBits() == MARK;
    }

    /** Une ville de la carte, pour le commerce : la vraie ville si elle a pris corps, sinon son marché (créé à la demande). */
    public static Optional<Site> of(MinecraftServer server, UUID id) {
        CrusadeSavedData data = data(server);
        Optional<Site> known = data.world().site(id);
        if (known.isPresent() || !isMarketId(id)) return known;
        int province = (int) id.getLeastSignificantBits();
        for (AiRealm r : data.world().realms()) {
            for (AiProvince p : r.provinces()) if (p.id() == province) return Optional.of(ensure(server, r, p));
        }
        return Optional.empty();
    }

    /** La vraie ville de cette province, si elle a pris corps : bâtie par l'ordinateur, ou posée d'après un fichier. */
    static Optional<Site> realCity(MinecraftServer server, int province) {
        for (Site s : data(server).world().sites()) if (s.province() == province && CityService.city(s) && !s.buildings().isEmpty()) return Optional.of(s);
        return Optional.empty();
    }

    /** La ville de cette province, ou son marché. */
    public static Site ensure(MinecraftServer server, AiRealm realm, AiProvince p) {
        Optional<Site> town = realCity(server, p.id());
        if (town.isPresent()) return town.get();
        CrusadeSavedData data = data(server);
        UUID id = idOf(p.id());
        Optional<Site> known = data.world().site(id);
        if (known.isPresent()) return known.get();
        LineageState state = data.lineage();
        Site s = new Site(id, SiteKind.VILLAGE, p.name(), realm.id(), p.dimension(), p.seat(), Direction.SOUTH, realm.color(), true, RealmService.currentDay(server));
        s.setProvince(p.id());
        s.setBuilt(true);
        long seed = id.getLeastSignificantBits() * 31 + p.seat().asLong();
        s.setCommunity(new LineageGenerator(seed).community(state, p.name(), CommunityKind.VILLAGE, realm.id(), 0).id());
        data.world().addMarket(s);
        staff(server, s, BuildingFunction.MARCHE, seed);
        staff(server, s, BuildingFunction.CASERNE, seed + 1);
        data.setDirty();
        return s;
    }

    /** Ceux qui le tiennent : ses marchands, ses gardes. Ils n'habitent nulle part dans le monde ; ils n'y paraissent qu'en voyage. */
    private static void staff(MinecraftServer server, Site s, BuildingFunction f, long seed) {
        BlockPos at = s.anchor().above();
        CityBuilding b = new CityBuilding(UUID.randomUUID(), at, at, at);
        b.setStats(60, 1, Map.of());
        b.setFunction(f);
        s.buildings().add(b);
        CityService.settle(server, s, b, seed);
    }

    /** Ce que sa terre lui donne, d'après la marchandise de son royaume. */
    static CityGood specialty(Good g) {
        return g == null ? CityGood.BLE : switch (g) {
            case FER -> CityGood.FER;
            case BOIS -> CityGood.BOIS;
            case PIERRE -> CityGood.PIERRE;
            case LAINE, FOURRURES -> CityGood.CUIR;
            case POISSON -> CityGood.VIANDE;
            default -> CityGood.BLE;
        };
    }

    /** Une journée d'un marché : il produit sa spécialité, mange, et ce qui ne se renouvelle pas s'épuise. La logique est pure. */
    public static void simulate(CityEconomy eco, CityGood specialty, int development) {
        eco.add(specialty, 10 + development / 2);
        eco.add(CityGood.BLE, 6);
        eco.add(CityGood.PAIN, 4);
        eco.take(CityGood.BLE, 3);
        eco.take(CityGood.PAIN, 4);
        eco.take(CityGood.VIANDE, 2);
        for (CityGood g : CityGood.values()) {
            if (g != specialty && g != CityGood.BLE && g != CityGood.PAIN && g != CityGood.VIANDE) eco.take(g, 1);
            if (eco.stock(g) > CityEconomy.CAP) eco.take(g, eco.stock(g) - CityEconomy.CAP);
        }
        eco.addPurse(10 + development / 2);
    }

    /** Une fois par jour : chaque marché vit, suit le sort de sa province, et remplace ceux qu'il a perdus. */
    public static void onDay(MinecraftServer server, Random random) {
        CrusadeSavedData data = data(server);
        for (Site m : new ArrayList<>(data.world().markets())) {
            AiRealm owner = null;
            AiProvince province = null;
            for (AiRealm r : data.world().realms()) {
                for (AiProvince p : r.provinces()) {
                    if (p.id() == m.province()) {
                        owner = r;
                        province = p;
                    }
                }
            }
            // Sa province a disparu, ou elle a pris corps en vraie ville : le marché n'a plus lieu d'être.
            boolean built = province != null && realCity(server, province.id()).isPresent();
            if (province == null || built) {
                for (CityBuilding b : m.buildings()) CityService.dismiss(server, b);
                data.world().removeMarket(m.id());
                continue;
            }
            if (!owner.id().equals(m.owner())) m.setOwner(owner.id());
            if (!province.name().equals(m.name())) m.setName(province.name());
            simulate(m.economy(), specialty(TradeService.good(server, owner.id())), province.development());
            long over = m.economy().purse() - PURSE_MAX;
            if (over > 0) {
                m.economy().addPurse(-over);
                owner.setGold(owner.gold() + over);
            }
            for (CityBuilding b : m.buildings()) {
                boolean someone = b.occupants().stream().anyMatch(id -> data.lineage().person(id).map(fr.crusadecraft.core.lineage.Person::alive).orElse(false));
                if (!someone) {
                    b.occupants().clear();
                    CityService.settle(server, m, b, random.nextLong());
                }
            }
        }
    }
}
