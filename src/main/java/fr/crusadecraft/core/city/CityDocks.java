package fr.crusadecraft.core.city;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.lineage.CommunityKind;
import fr.crusadecraft.core.lineage.LineageGenerator;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.core.war.WarMapService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le quai d'une colonie MineColonies (P57). Pour le commerce, une colonie est une ville comme une autre : des réserves, une bourse, des
 * marchands. Ses réserves sont le reflet de son entrepôt, moins ce qu'on lui laisse pour vivre. Ce que le commerce y prend ou y dépose
 * s'écrit dans l'entrepôt dès qu'il est chargé ; loin de tout joueur, cela attend sur le quai, et les caravanes continuent de tourner.
 *
 * <p>On ne touche jamais à un objet qui a des données (enchanté, nommé, usé) : le commerce ne vend pas l'épée du capitaine.
 */
public final class CityDocks {
    /** La partie haute de l'identifiant d'un quai. */
    private static final long MARK = 0x5155414943504C31L;
    /** Ce qu'on laisse toujours à la colonie, de chaque marchandise : elle en a besoin pour vivre. */
    public static final int KEEP = 32;
    /** Le trésor du royaume tient la bourse du quai entre ces deux seuils. */
    static final long PURSE_LOW = 50, PURSE_FILL = 100, PURSE_HIGH = 200;

    private CityDocks() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** Ce qu'il faut pour écrire dans un entrepôt, marchandise par marchandise. */
    public interface Warehouse {
        int count(CityGood g);

        /** Prend autant que possible ; renvoie ce qu'on a pris. */
        int take(CityGood g, int n);

        /** Dépose autant que possible ; renvoie ce qu'on a déposé. */
        int give(CityGood g, int n);
    }

    /** Où en est le quai : ce que l'entrepôt montrait au dernier passage, et ce qu'on lui doit encore. */
    public static final class Dock {
        public final ColonyRef colony;
        private final int[] synced = new int[CityGood.values().length], owed = new int[CityGood.values().length];

        public Dock(ColonyRef colony) {
            this.colony = colony;
        }

        public int owed(CityGood g) {
            return owed[g.ordinal()];
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("dim", colony.dimension().toString());
            t.putInt("colony", colony.colonyId());
            t.putIntArray("synced", synced);
            t.putIntArray("owed", owed);
            return t;
        }

        public static Dock load(CompoundTag t) {
            Dock d = new Dock(new ColonyRef(ResourceLocation.tryParse(t.getString("dim")), t.getInt("colony")));
            int[] s = t.getIntArray("synced"), o = t.getIntArray("owed");
            System.arraycopy(s, 0, d.synced, 0, Math.min(s.length, d.synced.length));
            System.arraycopy(o, 0, d.owed, 0, Math.min(o.length, d.owed.length));
            return d;
        }
    }

    public static UUID idOf(ColonyRef ref) {
        return new UUID(MARK, ((long) ref.dimension().hashCode() << 32) | (ref.colonyId() & 0xFFFFFFFFL));
    }

    public static boolean isDockId(UUID id) {
        return id != null && id.getMostSignificantBits() == MARK;
    }

    /**
     * Le passage au quai. Ce que le commerce a fait depuis le dernier passage se lit dans l'écart aux réserves ; on l'écrit dans l'entrepôt, et les réserves
     * redeviennent le reflet de ce qu'il contient. Sans entrepôt chargé, on ne fait que noter. La logique est pure.
     */
    public static void sync(CityEconomy eco, Dock dock, Warehouse w) {
        for (CityGood g : CityGood.values()) {
            int i = g.ordinal();
            int owed = dock.owed[i] + eco.stock(g) - dock.synced[i];
            if (w == null) {
                dock.owed[i] = owed;
                dock.synced[i] = eco.stock(g);
                continue;
            }
            if (owed > 0) owed -= w.give(g, owed);
            else if (owed < 0) {
                // Ce qu'on a vendu et qui n'y est plus (la colonie l'a utilisé entre-temps) : tant pis, on n'insiste pas.
                w.take(g, -owed);
                owed = 0;
            }
            int visible = Math.max(0, w.count(g) - KEEP) + Math.max(0, owed);
            eco.setStock(g, visible);
            dock.owed[i] = owed;
            dock.synced[i] = visible;
        }
    }

    /** Les rayonnages de l'entrepôt, vus marchandise par marchandise. */
    private static Warehouse warehouse(IItemHandler h) {
        return new Warehouse() {
            private boolean plain(ItemStack s, CityGood g) {
                return !s.isEmpty() && !s.hasTag() && CityLife.good(s) == g;
            }

            @Override
            public int count(CityGood g) {
                int n = 0;
                for (int i = 0; i < h.getSlots(); i++) {
                    ItemStack s = h.getStackInSlot(i);
                    if (plain(s, g)) n += s.getCount();
                }
                return n;
            }

            @Override
            public int take(CityGood g, int want) {
                int got = 0;
                for (int i = 0; i < h.getSlots() && got < want; i++) {
                    ItemStack s = h.getStackInSlot(i);
                    if (plain(s, g)) got += h.extractItem(i, Math.min(want - got, s.getCount()), false).getCount();
                }
                return got;
            }

            @Override
            public int give(CityGood g, int want) {
                Item item = CityScenes.item(g);
                int left = want;
                while (left > 0) {
                    int part = Math.min(left, item.getMaxStackSize());
                    int done = part - ItemHandlerHelper.insertItemStacked(h, new ItemStack(item, part), false).getCount();
                    left -= done;
                    if (done < part) break;
                }
                return want - left;
            }
        };
    }

    /** Le quai de cette colonie, créé à la demande. Une colonie hors de tout royaume n'en a pas : son commerce n'aurait pas de trésor où aller. */
    public static Optional<Site> ensure(MinecraftServer server, ColonyRef ref) {
        CrusadeSavedData data = data(server);
        Optional<Realm> realm = data.realmHolding(ref);
        if (realm.isEmpty()) return Optional.empty();
        UUID id = idOf(ref);
        Optional<Site> known = data.world().site(id);
        if (known.isPresent()) return known;
        Optional<BlockPos> center = MineColoniesBridge.center(ref);
        if (center.isEmpty()) return Optional.empty();
        String name = MineColoniesBridge.info(ref).name();
        Site s = new Site(id, SiteKind.VILLAGE, name, realm.get().id(), ref.dimension(), center.get(), Direction.SOUTH, WarMapService.color(data, realm.get().id()), true, RealmService.currentDay(server));
        s.setBuilt(true);
        CityEconomy eco = s.economy();
        for (CityGood g : CityGood.values()) eco.setStock(g, 0);
        eco.addPurse(-eco.purse());
        long seed = id.getLeastSignificantBits() * 31 + center.get().asLong();
        s.setCommunity(new LineageGenerator(seed).community(data.lineage(), name, CommunityKind.VILLAGE, realm.get().id(), 0).id());
        data.world().addMarket(s);
        data.world().docks().put(id, new Dock(ref));
        CityMarkets.staff(server, s, BuildingFunction.MARCHE, seed);
        CityMarkets.staff(server, s, BuildingFunction.CASERNE, seed + 1);
        pass(server, s);
        data.setDirty();
        return Optional.of(s);
    }

    /** Le quai que désigne cet identifiant. */
    static Optional<Site> of(MinecraftServer server, UUID id) {
        CrusadeSavedData data = data(server);
        Optional<Site> known = data.world().site(id);
        if (known.isPresent()) return known;
        for (Realm r : data.realms()) {
            for (ColonyRef c : r.allColonies()) if (idOf(c).equals(id)) return ensure(server, c);
        }
        return Optional.empty();
    }

    /** Un entrepôt à portée de main ? */
    public static boolean hasWarehouse(ColonyRef ref) {
        return MineColoniesBridge.warehouse(ref).isPresent();
    }

    public static Optional<ColonyRef> colonyOf(MinecraftServer server, UUID id) {
        return Optional.ofNullable(data(server).world().docks().get(id)).map(d -> d.colony);
    }

    /** Un passage au quai : l'entrepôt, puis la bourse, tenue par le trésor du royaume. */
    public static void pass(MinecraftServer server, Site s) {
        CrusadeSavedData data = data(server);
        Dock dock = data.world().docks().get(s.id());
        if (dock == null) return;
        sync(s.economy(), dock, MineColoniesBridge.warehouse(dock.colony).map(CityDocks::warehouse).orElse(null));
        CityEconomy eco = s.economy();
        data.realm(s.owner()).ifPresent(r -> {
            if (eco.purse() > PURSE_HIGH) {
                long over = eco.purse() - PURSE_HIGH;
                eco.addPurse(-over);
                TreasuryService.credit(server, r, LedgerCategory.COMMERCE, over);
            } else if (eco.purse() < PURSE_LOW && TreasuryService.spend(server, r, LedgerCategory.AUTRES, PURSE_FILL)) {
                eco.addPurse(PURSE_FILL);
            }
        });
    }

    /**
     * P59 : une caravane charge ou décharge sur ce quai. L'entrepôt le sait tout de suite, sans attendre le prochain passage : sinon la colonie aurait le
     * temps d'utiliser ce qui est déjà parti, et la marchandise existerait deux fois. Loin de tout joueur, cela reste noté sur le quai : la colonie, elle non plus, n'y touche pas.
     */
    static void touch(MinecraftServer server, Site s) {
        if (isDockId(s.id())) pass(server, s);
    }

    /** Quatre fois par jour : tous les quais passent. */
    static void pulse(MinecraftServer server) {
        CrusadeSavedData data = data(server);
        for (UUID id : new ArrayList<>(data.world().docks().keySet())) data.world().site(id).ifPresent(s -> pass(server, s));
    }

    /** Une fois par jour : un quai suit sa colonie, et s'en va avec elle. */
    static void onDay(MinecraftServer server) {
        if (!MineColoniesBridge.isReady()) return;
        CrusadeSavedData data = data(server);
        for (Map.Entry<UUID, Dock> e : new ArrayList<>(data.world().docks().entrySet())) {
            Site s = data.world().site(e.getKey()).orElse(null);
            Optional<Realm> realm = data.realmHolding(e.getValue().colony);
            if (s == null || realm.isEmpty() || !MineColoniesBridge.exists(e.getValue().colony)) {
                data.world().docks().remove(e.getKey());
                if (s != null) {
                    for (CityBuilding b : s.buildings()) CityService.dismiss(server, b);
                    data.world().removeMarket(s.id());
                }
                continue;
            }
            if (!realm.get().id().equals(s.owner())) s.setOwner(realm.get().id());
            String name = MineColoniesBridge.info(e.getValue().colony).name();
            if (!name.equals(s.name())) s.setName(name);
        }
    }
}
