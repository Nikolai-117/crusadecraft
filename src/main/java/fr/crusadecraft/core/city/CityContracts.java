package fr.crusadecraft.core.city;

import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Ce que les villes demandent aux joueurs (P56) : porter un pli urgent, escorter une caravane. Une ville qui propose un contrat met
 * la somme de côté dès qu'on l'accepte : on est sûr d'être payé, et si l'on échoue, l'argent lui revient. Tout tient à ce que le joueur
 * fait vraiment : il faut aller là-bas, ou marcher à côté de la caravane.
 */
public final class CityContracts {
    /** Au plus tant de contrats ouverts par ville. */
    static final int OPEN_PER_CITY = 2;
    /** Au plus tant de contrats à la fois pour un joueur. */
    public static final int TAKEN_MAX = 3;
    /** Sans preneur en tant de jours, l'offre tombe. */
    static final int DAYS_OPEN = 3;
    /** Une chance sur tant, par jour et par ville, qu'une offre paraisse. */
    static final int CHANCE = 2;
    /** Il faut être à moins de tant de blocs de la ville pour accepter : on ne signe pas de loin. */
    public static final int SIGN_RANGE = 96;
    /** À moins de tant de blocs de son but, le pli est remis. */
    public static final int ARRIVED = 32;
    /** À moins de tant de blocs du marchand, on l'escorte. */
    public static final int ESCORT_NEAR = 64;
    /** Il faut avoir marché à ses côtés au moins tant de pour cent du trajet. */
    public static final int PRESENCE = 60;
    /** Un joueur ne reçoit pas une offre de plus loin : ni trop près pour que ça compte, ni trop loin pour qu'on y aille. */
    static final int DISTANCE_MIN = 150, DISTANCE_MAX = 3000;
    /** Ce que gagne, dans l'estime de la ville, celui qui tient parole, et ce qu'il perd sinon. */
    static final int THANKS = 3, BROKEN = 2;
    /** L'étiquette du pli qu'on emporte, avec l'identifiant de son contrat. */
    static final String LETTER_TAG = "crusadecraft_contrat";

    private static ServerPlayer testPlayer;

    private CityContracts() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    // ------------------------------------------------------------------ ce que ça vaut : la logique est pure

    /** Un pli : d'autant mieux payé que c'est loin. */
    public static int courierReward(int distance) {
        return Math.max(2, Math.min(24, distance / 150));
    }

    /** Une escorte : on y passe tout le trajet, et cela se paie davantage. */
    public static int escortReward(int distance) {
        return Math.max(4, Math.min(40, distance / 80));
    }

    /** Les jours qu'on a pour porter un pli : un de base, et un par tranche de deux mille blocs. */
    public static int courierDays(int distance) {
        return 1 + distance / 2000;
    }

    /** A-t-il assez marché à côté d'elle ? */
    public static boolean present(long near, long total) {
        return total > 0 && near * 100 >= total * PRESENCE;
    }

    // ------------------------------------------------------------------ chaque jour

    /** Les offres anciennes tombent, les plis en retard échouent, de nouvelles offres paraissent. */
    public static void onDay(MinecraftServer server, Random random) {
        CrusadeSavedData data = data(server);
        long today = RealmService.currentDay(server);
        for (CityContract c : new ArrayList<>(data.world().contracts())) {
            if (today <= c.expiresDay) continue;
            if (c.taker == null) data.world().contracts().remove(c);
            else if (c.kind == CityContract.Kind.COURIER) fail(server, c, "crusadecraft.contract.failed.late");
        }
        if (!CrusadeConfig.SERVER.routines.get()) return;
        for (Site s : new ArrayList<>(data.world().sites())) {
            if (!CityService.city(s) || s.buildings().isEmpty() || random.nextInt(CHANCE) != 0) continue;
            int open = 0;
            for (CityContract c : data.world().contracts()) if (c.from.equals(s.id()) && c.taker == null) open++;
            if (open < OPEN_PER_CITY && !CityBlockades.blocked(server, s)) post(server, s, random, today);
        }
    }

    /** Une offre de plus dans cette ville, si elle a où l'envoyer et de quoi payer. */
    public static Optional<CityContract> post(MinecraftServer server, Site from, Random random, long today) {
        CrusadeSavedData data = data(server);
        // Une escorte d'abord, si elle a une ligne qui ne tourne pas en ce moment.
        CityLine line = null;
        for (CityLine l : data.world().lines()) {
            if (l.from.equals(from.id()) && l.active && !running(data, l.id) && !hasEscort(data, l.id) && CityMarkets.of(server, l.to).isPresent()) line = l;
        }
        if (line != null && random.nextBoolean()) {
            Site to = CityMarkets.of(server, line.to).orElseThrow();
            int reward = escortReward(distance(from, to));
            if (from.economy().purse() < reward) return Optional.empty();
            CityContract c = new CityContract(UUID.randomUUID(), CityContract.Kind.ESCORT, from.id(), to.id(), reward, today, today + DAYS_OPEN);
            c.line = line.id;
            data.world().contracts().add(c);
            data.setDirty();
            return Optional.of(c);
        }
        List<CityCommerce.Stop> near = new ArrayList<>();
        for (CityCommerce.Stop s : CityCommerce.stops(server, from)) {
            if (!s.war() && s.distance() >= DISTANCE_MIN && s.distance() <= DISTANCE_MAX) near.add(s);
        }
        if (near.isEmpty()) return Optional.empty();
        CityCommerce.Stop pick = near.get(random.nextInt(Math.min(8, near.size())));
        Optional<Site> to = CityMarkets.of(server, pick.id());
        int reward = courierReward(pick.distance());
        if (to.isEmpty() || from.economy().purse() < reward) return Optional.empty();
        CityContract c = new CityContract(UUID.randomUUID(), CityContract.Kind.COURIER, from.id(), to.get().id(), reward, today, today + DAYS_OPEN);
        data.world().contracts().add(c);
        data.setDirty();
        return Optional.of(c);
    }

    private static boolean running(CrusadeSavedData data, UUID line) {
        for (Person p : data.lineage().persons()) if (p.alive() && p.trip() != null && line.equals(p.trip().line())) return true;
        return false;
    }

    private static boolean hasEscort(CrusadeSavedData data, UUID line) {
        for (CityContract c : data.world().contracts()) if (line.equals(c.line)) return true;
        return false;
    }

    private static int distance(Site a, Site b) {
        return (int) Math.hypot(a.anchor().getX() - b.anchor().getX(), a.anchor().getZ() - b.anchor().getZ());
    }

    // ------------------------------------------------------------------ ce que fait le joueur

    public static Optional<CityContract> find(MinecraftServer server, UUID id) {
        for (CityContract c : data(server).world().contracts()) if (c.id.equals(id)) return Optional.of(c);
        return Optional.empty();
    }

    private static int takenBy(MinecraftServer server, UUID player) {
        int n = 0;
        for (CityContract c : data(server).world().contracts()) if (player.equals(c.taker)) n++;
        return n;
    }

    private static int distance(ServerPlayer player, Site s) {
        return s.dimension().equals(player.level().dimension().location()) ? (int) Math.hypot(player.getX() - s.anchor().getX(), player.getZ() - s.anchor().getZ()) : Integer.MAX_VALUE;
    }

    /** Il accepte. Il doit être sur place ; la ville met la somme de côté ; le pli lui est remis, ou la caravane se met en route. */
    public static Outcome accept(ServerPlayer player, UUID id) {
        MinecraftServer server = player.server;
        Optional<CityContract> found = find(server, id);
        if (found.isEmpty() || found.get().taker != null) return Outcome.no("crusadecraft.contract.gone");
        CityContract c = found.get();
        CrusadeSavedData data = data(server);
        Site from = data.world().site(c.from).orElse(null), to = data.world().site(c.to).orElse(null);
        if (from == null || to == null) return Outcome.no("crusadecraft.contract.gone");
        if (takenBy(server, player.getUUID()) >= TAKEN_MAX) return Outcome.no("crusadecraft.contract.too_many", TAKEN_MAX);
        if (distance(player, from) > SIGN_RANGE) return Outcome.no("crusadecraft.contract.too_far", from.name());
        if (from.economy().purse() < c.reward) return Outcome.no("crusadecraft.city.order.broke", from.name());
        if (c.kind == CityContract.Kind.ESCORT) {
            Optional<CityLine> line = CityCommerce.line(server, c.line);
            if (line.isEmpty()) return Outcome.no("crusadecraft.contract.gone");
            if (CityCommerce.atWar(server, from.owner(), to.owner()) || CityBlockades.blocked(server, from) || CityBlockades.blocked(server, to)) return Outcome.no("crusadecraft.contract.road_closed", to.name());
            CityTolls.Passage passage = CityTolls.passage(server, from, to);
            if (passage.cut() || from.economy().purse() < c.reward + passage.toll()) return Outcome.no("crusadecraft.contract.road_closed", to.name());
            if (!CityCommerce.dispatch(server, line.get(), from, to)) return Outcome.no("crusadecraft.contract.no_caravan", line.get().status);
            CityTolls.pay(server, passage, from);
            line.get().tolls += passage.toll();
            line.get().waited = 0;
            for (Person p : data.lineage().persons()) if (p.alive() && p.trip() != null && c.line.equals(p.trip().line()) && p.trip().leader() == null) c.merchant = p.id();
            if (c.merchant == null) return Outcome.no("crusadecraft.contract.no_caravan", line.get().status);
        } else {
            c.expiresDay = RealmService.currentDay(server) + courierDays(distance(from, to));
            ItemStack letter = new ItemStack(Items.PAPER);
            letter.setHoverName(Component.translatable("crusadecraft.contract.letter", to.name()));
            letter.getOrCreateTag().putUUID(LETTER_TAG, c.id);
            if (!player.getInventory().add(letter)) player.drop(letter, false);
        }
        from.economy().addPurse(-c.reward);
        c.taker = player.getUUID();
        data.setDirty();
        return Outcome.ok(Component.translatable(c.kind == CityContract.Kind.ESCORT ? "crusadecraft.contract.accepted.escort" : "crusadecraft.contract.accepted.courier", to.name(), CityOrders.money(c.reward)));
    }

    /** Il y renonce : l'argent retourne à la ville, qui s'en souviendra. */
    public static Outcome abandon(ServerPlayer player, UUID id) {
        Optional<CityContract> found = find(player.server, id);
        if (found.isEmpty() || !player.getUUID().equals(found.get().taker)) return Outcome.no("crusadecraft.contract.gone");
        fail(player.server, found.get(), "crusadecraft.contract.failed.given_up");
        return Outcome.ok(Component.translatable("crusadecraft.contract.abandoned"));
    }

    /** Il a échoué : la ville reprend son argent, le pli disparaît, et on le sait. */
    private static void fail(MinecraftServer server, CityContract c, String why) {
        CrusadeSavedData data = data(server);
        data.world().contracts().remove(c);
        Optional<Site> from = data.world().site(c.from);
        from.ifPresent(s -> {
            s.economy().addPurse(c.reward);
            for (Person p : CityLife.residents(server, s)) p.addOpinion(c.taker, -BROKEN);
        });
        ServerPlayer player = online(server, c.taker);
        if (player != null) {
            removeLetter(player.getInventory(), c.id);
            player.sendSystemMessage(Component.translatable(why, from.map(Site::name).orElse("?")).withStyle(ChatFormatting.RED));
        }
        data.setDirty();
    }

    /** Il a tenu parole : il est payé, et la ville s'en souvient. */
    private static void succeed(MinecraftServer server, CityContract c, ServerPlayer player) {
        CrusadeSavedData data = data(server);
        data.world().contracts().remove(c);
        removeLetter(player.getInventory(), c.id);
        CityOrders.pay(player, c.reward);
        data.world().site(c.from).ifPresent(s -> {
            for (Person p : CityLife.residents(server, s)) p.addOpinion(player.getUUID(), THANKS);
            CityRumors.add(server, s, "contrat", player.getGameProfile().getName());
        });
        String to = data.world().site(c.to).map(Site::name).orElse("?");
        player.sendSystemMessage(Component.translatable(c.kind == CityContract.Kind.ESCORT ? "crusadecraft.contract.done.escort" : "crusadecraft.contract.done.courier", to, CityOrders.money(c.reward)).withStyle(ChatFormatting.GOLD));
        data.setDirty();
    }

    private static void removeLetter(Inventory inv, UUID id) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.PAPER) && s.hasTag() && s.getTag().hasUUID(LETTER_TAG) && id.equals(s.getTag().getUUID(LETTER_TAG))) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    // ------------------------------------------------------------------ pendant ce temps

    /** Toutes les deux secondes : un pli arrive-t-il ? marche-t-il encore à côté de la caravane ? */
    public static void tick(MinecraftServer server, boolean remind) {
        CrusadeSavedData data = data(server);
        for (CityContract c : new ArrayList<>(data.world().contracts())) {
            if (c.taker == null) continue;
            Site from = data.world().site(c.from).orElse(null), to = data.world().site(c.to).orElse(null);
            if (from == null || to == null) {
                fail(server, c, "crusadecraft.contract.failed.gone");
                continue;
            }
            ServerPlayer player = online(server, c.taker);
            if (c.kind == CityContract.Kind.COURIER) {
                if (player == null) continue;
                int d = distance(player, to);
                if (d <= ARRIVED) succeed(server, c, player);
                else if (remind) hint(player, Component.translatable("crusadecraft.contract.hint.courier", to.name(), d == Integer.MAX_VALUE ? "?" : d, direction(player, to)));
                continue;
            }
            escort(server, c, player, to, remind);
        }
    }

    private static void escort(MinecraftServer server, CityContract c, ServerPlayer player, Site to, boolean remind) {
        Person merchant = data(server).lineage().person(c.merchant).filter(Person::alive).orElse(null);
        if (merchant == null || merchant.trip() == null) {
            // Mort en route, ou rentré par un autre chemin.
            fail(server, c, "crusadecraft.contract.failed.merchant");
            return;
        }
        if (merchant.trip().stage() != Trip.Stage.OUT) {
            // Il est arrivé, ou il a fait demi-tour.
            if (merchant.trip().stage() == Trip.Stage.STAY && player != null && present(c.near, c.total)) succeed(server, c, player);
            else fail(server, c, merchant.trip().stage() == Trip.Stage.STAY ? "crusadecraft.contract.failed.absent" : "crusadecraft.contract.failed.turned");
            return;
        }
        c.total++;
        if (player == null) return;
        int[] at = where(server, merchant);
        int d = at == null || !player.level().dimension().location().equals(to.dimension()) ? Integer.MAX_VALUE : (int) Math.hypot(player.getX() - at[0], player.getZ() - at[1]);
        if (d <= ESCORT_NEAR) c.near++;
        if (remind) hint(player, Component.translatable(d <= ESCORT_NEAR ? "crusadecraft.contract.hint.escort" : "crusadecraft.contract.hint.escort.far", merchant.name(), to.name(), d == Integer.MAX_VALUE ? "?" : d));
    }

    /** Où il est : là où on le voit, sinon là où il devrait être. */
    private static int[] where(MinecraftServer server, Person p) {
        for (CityTravel.Pawn pawn : CityTravel.pawns(server)) if (pawn.id().equals(p.id())) return new int[]{pawn.x(), pawn.z()};
        return null;
    }

    private static void hint(ServerPlayer player, Component text) {
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.YELLOW), true);
    }

    /** « au nord-est » : de quoi s'orienter sans ouvrir la carte. */
    private static Component direction(ServerPlayer player, Site to) {
        return Component.translatable(directionKey(to.anchor().getX() - player.getX(), to.anchor().getZ() - player.getZ()));
    }

    /** La logique est pure. Minecraft compte le nord en z négatif. */
    public static String directionKey(double dx, double dz) {
        String[] names = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
        int i = (int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4));
        return "crusadecraft.contract.dir." + names[Math.floorMod(i, 8)];
    }

    // ------------------------------------------------------------------ l'écran

    public static ListTag view(ServerPlayer player, boolean all) {
        MinecraftServer server = player.server;
        CrusadeSavedData data = data(server);
        long today = RealmService.currentDay(server);
        ListTag out = new ListTag();
        for (CityContract c : data.world().contracts()) {
            boolean mine = player.getUUID().equals(c.taker);
            if (c.taker != null && !mine) continue;
            Site from = data.world().site(c.from).orElse(null), to = data.world().site(c.to).orElse(null);
            if (from == null || to == null) continue;
            if (!all && !mine && !fr.crusadecraft.core.world.WorldService.known(data, from.owner(), player)) continue;
            CompoundTag x = new CompoundTag();
            x.putUUID("id", c.id);
            x.putString("kind", c.kind.name());
            x.putString("from", from.name());
            x.putString("to", to.name());
            x.putInt("reward", c.reward);
            x.putLong("days", c.expiresDay - today);
            x.putBoolean("mine", mine);
            x.putInt("near", distance(player, mine && c.kind == CityContract.Kind.COURIER ? to : from));
            x.putInt("route", distance(from, to));
            if (mine && c.total > 0) x.putInt("presence", (int) (c.near * 100 / c.total));
            out.add(x);
        }
        return out;
    }

    /** Ce que les villes qu'on connaît ont commandé, à livrer sur place : on n'a rien à accepter, il suffit de l'apporter. */
    public static ListTag orders(ServerPlayer player, boolean all) {
        CrusadeSavedData data = data(player.server);
        Optional<Site> here = CityService.cityAt(player.server, player.level().dimension().location(), player.blockPosition());
        ListTag out = new ListTag();
        long today = RealmService.currentDay(player.server);
        for (Site s : data.world().sites()) {
            if (s.orders().isEmpty() || (!all && !fr.crusadecraft.core.world.WorldService.known(data, s.owner(), player))) continue;
            for (CityOrders.Order o : s.orders()) {
                CompoundTag x = new CompoundTag();
                x.putUUID("site", s.id());
                x.putString("city", s.name());
                x.putString("what", CityOrders.describe(o.good(), o.count()));
                x.putInt("reward", o.reward());
                x.putLong("days", o.day() + CityOrders.EXPIRES - today);
                x.putInt("near", distance(player, s));
                x.putBoolean("urgent", s.economy().stock(o.good()) == 0);
                x.putBoolean("can", here.filter(h -> h == s).isPresent() && CityOrders.carried(player.getInventory(), o.good()) >= o.count());
                out.add(x);
            }
        }
        return out;
    }

    /** Il livre ce qu'il a sur lui, à la ville où il se tient : c'est ce que fait un habitant, sans avoir à en trouver un. */
    public static Outcome deliverHere(ServerPlayer player, UUID site) {
        Optional<Site> here = CityService.cityAt(player.server, player.level().dimension().location(), player.blockPosition());
        if (here.isEmpty() || !here.get().id().equals(site)) return Outcome.no("crusadecraft.contract.not_here");
        List<Person> people = CityLife.residents(player.server, here.get());
        return CityOrders.deliver(player, here.get(), people.isEmpty() ? null : people.get(0));
    }

    /** Pour les essais : un joueur fictif n'est pas sur la liste du serveur. */
    public static void tickForTests(MinecraftServer server, ServerPlayer player) {
        testPlayer = player;
        try {
            tick(server, true);
        } finally {
            testPlayer = null;
        }
    }

    private static ServerPlayer online(MinecraftServer server, UUID id) {
        if (testPlayer != null && testPlayer.getUUID().equals(id)) return testPlayer;
        return server.getPlayerList().getPlayer(id);
    }
}
