package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Iterator;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Ce qu'une ville commande (P49). Quand une marchandise vient à manquer, elle écrit à qui elle connaît, et paie en émeraudes,
 * sur sa propre bourse, celui qui la lui livre. C'est le pendant réel de ses réserves : ce qu'on lui apporte arrive au stock.
 */
public final class CityOrders {
    /** Au plus tant de commandes ouvertes par ville. */
    static final int OPEN_MAX = 3;
    /** Sans livraison en tant de jours, la commande tombe. */
    static final int EXPIRES = 12;
    /** Une chance sur tant, par marchandise qui manque, chaque jour. */
    static final int CHANCE = 3;
    /** Ce que gagne la personne qui livre, dans l'estime de la ville. */
    static final int THANKS = 4;

    /**
     * Une commande.
     *
     * @param count  combien d'unités
     * @param reward ce qu'elle paie, en émeraudes
     * @param day    le jour où elle a été passée
     */
    public record Order(UUID id, CityGood good, int count, int reward, long day) {
        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("good", good.name());
            t.putInt("count", count);
            t.putInt("reward", reward);
            t.putLong("day", day);
            return t;
        }

        public static Order load(CompoundTag t) {
            CityGood good;
            try {
                good = CityGood.valueOf(t.getString("good"));
            } catch (IllegalArgumentException e) {
                good = CityGood.BLE;
            }
            return new Order(t.getUUID("id"), good, t.getInt("count"), t.getInt("reward"), t.getLong("day"));
        }
    }

    private CityOrders() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** Combien d'unités on demande d'un coup, et combien en vaut une émeraude. La logique est pure. */
    public static int amount(CityGood g, Random random) {
        return switch (g) {
            case OUTILS -> 1 + random.nextInt(3);
            case LIVRE -> 3 + random.nextInt(6);
            case REPAS -> 6 + random.nextInt(11);
            default -> 16 + random.nextInt(17);
        };
    }

    /** Ce que paie une commande de tant d'unités : jamais moins d'une émeraude. */
    public static int reward(CityGood g, int count) {
        int perEmerald = switch (g) {
            case OUTILS -> 0;
            case LIVRE -> 2;
            case REPAS -> 3;
            case FER, CUIR -> 4;
            case PAIN, VIANDE -> 6;
            case BLE -> 8;
            case PIERRE, BOIS -> 12;
        };
        return perEmerald == 0 ? count * 3 : Math.max(1, Math.round((float) count / perEmerald));
    }

    /** Une marchandise et sa quantité, pour un texte : « du blé (24) ». */
    public static String describe(CityGood g, int count) {
        return fr.crusadecraft.core.logic.Fr.key(g.key() + ".du") + " (" + count + ")";
    }

    /** Une somme, pour un texte : « 1 émeraude », « 3 émeraudes ». */
    public static String money(int n) {
        return n + (n == 1 ? " émeraude" : " émeraudes");
    }

    /** Ce dont elle a besoin aujourd'hui : un coup d'œil aux réserves, et une lettre pour qui peut l'aider. */
    static void onDay(MinecraftServer server, Site site, Random random) {
        long today = RealmService.currentDay(server);
        for (Iterator<Order> it = site.orders().iterator(); it.hasNext(); ) {
            if (today - it.next().day() > EXPIRES) it.remove();
        }
        CityEconomy eco = site.economy();
        for (CityGood g : CityGood.values()) {
            if (site.orders().size() >= OPEN_MAX) return;
            if (eco.stock(g) >= CityEconomy.LOW || random.nextInt(CHANCE) != 0) continue;
            if (site.orders().stream().anyMatch(o -> o.good() == g)) continue;
            int count = amount(g, random), reward = reward(g, count);
            if (eco.purse() < reward) continue;
            Order order = new Order(UUID.randomUUID(), g, count, reward, today);
            site.orders().add(order);
            CityPost.order(server, site, order, random);
            data(server).setDirty();
        }
    }

    /** Ce que ce joueur a sur lui de cette marchandise. */
    static int carried(Inventory inv, CityGood g) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (CityLife.good(s) == g) n += s.getCount();
        }
        return n;
    }

    /** La première commande qu'il a de quoi honorer, si elle existe. */
    public static Optional<Order> deliverable(ServerPlayer player, Site site) {
        for (Order o : site.orders()) if (carried(player.getInventory(), o.good()) >= o.count()) return Optional.of(o);
        return Optional.empty();
    }

    /** Il livre : les marchandises passent aux réserves, les émeraudes lui reviennent, et la ville s'en souvient. */
    public static Outcome deliver(ServerPlayer player, Site site, Person speaker) {
        Optional<Order> found = deliverable(player, site);
        if (found.isEmpty()) return Outcome.no("crusadecraft.city.order.nothing");
        Order o = found.get();
        CityEconomy eco = site.economy();
        if (eco.purse() < o.reward()) return Outcome.no("crusadecraft.city.order.broke", site.name());
        take(player.getInventory(), o.good(), o.count());
        eco.add(o.good(), o.count());
        eco.addPurse(-o.reward());
        int left = o.reward();
        while (left > 0) {
            int n = Math.min(left, Items.EMERALD.getMaxStackSize());
            ItemStack pay = new ItemStack(Items.EMERALD, n);
            if (!player.getInventory().add(pay)) player.drop(pay, false);
            left -= n;
        }
        site.orders().remove(o);
        for (Person p : CityLife.residents(player.server, site)) p.addOpinion(player.getUUID(), p == speaker ? THANKS * 2 : THANKS);
        CityRumors.add(player.server, site, "livraison", player.getGameProfile().getName());
        CityPost.thanks(player.server, site, speaker, player, o);
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.city.order.done", describe(o.good(), o.count()), money(o.reward())));
    }

    private static void take(Inventory inv, CityGood g, int count) {
        int left = count;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (CityLife.good(s) != g) continue;
            int n = Math.min(left, s.getCount());
            s.shrink(n);
            left -= n;
        }
        inv.setChanged();
    }
}
