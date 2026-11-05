package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Repliques;
import fr.crusadecraft.core.logic.Fr;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Deux habitants, face à face (P50). L'acheteur va jusqu'au vendeur, ils s'arrêtent, se regardent, se parlent, et l'objet passe
 * de main en main contre une pièce : ce qui est vu est ce qui est compté, les réserves et les bourses bougent à ce moment-là.
 * C'est aussi la livraison remise à quelqu'un, le marchand qui arrive et traite avec le marché, et deux voisins qui causent.
 * Rien de tout ça n'a lieu sans témoin : loin des joueurs, la ville continue en comptes (voir {@link CityLife}).
 */
public final class CityScenes {
    /** Ce qui se joue. */
    public enum Kind { SALE, SUPPLY, DELIVERY, WHOLESALE, CHAT }

    private enum Phase { WALK, ASK, ANSWER, SWAP, DONE }

    /** On passe voir les scènes tous les tant de ticks. */
    public static final int EVERY = 10;
    /** Au plus tant de scènes à la fois dans une ville. */
    static final int MAX_PER_CITY = 4;
    /** Assez près pour se parler. */
    static final int REACH = 3;
    /** On ne cherche pas un vendeur plus loin que ça. */
    static final int FIND = 64;
    /** Deux voisins ne causent que s'ils sont à moins de tant de blocs. */
    static final int NEIGHBOUR = 12;
    /** Sans y être arrivé en tant de ticks, il renonce. */
    static final int WALK_LIMIT = 800;
    /** Le temps entre deux répliques. */
    static final int BEAT = 50;
    /** Une causette par personne en tant de ticks, au plus. */
    static final int CHAT_EVERY = 2400;
    /** Sans avoir avancé depuis tant de ticks, il n'y arrivera pas tout seul. */
    static final int BLOCKED = 120;
    /** Un joueur à moins de tant de blocs voit les bulles. */
    static final int WITNESS = 24;

    private static final class Scene {
        final Kind kind;
        final UUID site, a, b;
        final CityGood good;
        final int amount, price;
        final boolean eats;
        final long giveUp;
        Phase phase = Phase.WALK;
        long next;
        BlockPos lastPos;
        long lastMoved;

        Scene(Kind kind, UUID site, UUID a, UUID b, CityGood good, int amount, int price, boolean eats, long now) {
            this.kind = kind;
            this.site = site;
            this.a = a;
            this.b = b;
            this.good = good;
            this.amount = amount;
            this.price = price;
            this.eats = eats;
            this.giveUp = now + WALK_LIMIT;
        }
    }

    private static final List<Scene> ALL = new ArrayList<>();
    private static final Map<UUID, Scene> BUSY = new HashMap<>();
    private static final Map<UUID, Long> LAST_CHAT = new HashMap<>();
    private static final Random RANDOM = new Random();

    private CityScenes() {}

    public static void onServerStopped() {
        ALL.clear();
        BUSY.clear();
        LAST_CHAT.clear();
    }

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    public static boolean busy(UUID person) {
        return BUSY.containsKey(person);
    }

    public static int active() {
        return ALL.size();
    }

    /** Pour le banc d'essai : quelqu'un qui est en train de traiter, face à l'autre, s'il y en a un ; à défaut, quelqu'un qui y va. */
    public static UUID someoneInScene() {
        for (Scene s : ALL) if (s.phase != Phase.WALK) return s.a;
        return ALL.isEmpty() ? null : ALL.get(0).a;
    }

    // ------------------------------------------------------------------ qui vend quoi

    /** Les boutiques qui vendent cette marchandise, de la plus indiquée à la moins indiquée. La logique est pure. */
    public static List<BuildingFunction> shops(CityGood g) {
        return switch (g) {
            case PAIN -> List.of(BuildingFunction.BOULANGERIE, BuildingFunction.MARCHE);
            case VIANDE -> List.of(BuildingFunction.BOUCHERIE, BuildingFunction.MARCHE);
            case REPAS -> List.of(BuildingFunction.TAVERNE);
            case BLE -> List.of(BuildingFunction.MOULIN, BuildingFunction.FERME, BuildingFunction.MARCHE);
            case OUTILS -> List.of(BuildingFunction.FORGE, BuildingFunction.MARCHE);
            case FER -> List.of(BuildingFunction.ENTREPOT, BuildingFunction.MARCHE);
            case LIVRE -> List.of(BuildingFunction.BIBLIOTHEQUE, BuildingFunction.ECOLE, BuildingFunction.MARCHE);
            case PIERRE -> List.of(BuildingFunction.ATELIER, BuildingFunction.ENTREPOT);
            case BOIS -> List.of(BuildingFunction.ENTREPOT, BuildingFunction.ATELIER, BuildingFunction.MARCHE);
            case CUIR -> List.of(BuildingFunction.ECURIE, BuildingFunction.MARCHE);
        };
    }

    /** Ce qu'un artisan achète pour son ouvrage ; {@code null} : rien. La logique est pure. */
    public static CityGood supply(BuildingFunction f, boolean first) {
        return switch (f) {
            case BOULANGERIE -> CityGood.BLE;
            case TAVERNE -> first ? CityGood.PAIN : CityGood.VIANDE;
            case FORGE -> CityGood.FER;
            case ATELIER -> first ? CityGood.PIERRE : CityGood.BOIS;
            case ECURIE -> CityGood.BLE;
            case BIBLIOTHEQUE, ECOLE -> CityGood.CUIR;
            default -> null;
        };
    }

    /** Ce que coûte une pièce de cette marchandise, en pièces. */
    public static int price(CityGood g) {
        return switch (g) {
            case OUTILS -> 3;
            case FER, LIVRE, CUIR -> 2;
            default -> 1;
        };
    }

    /** L'objet qui se voit dans les mains. */
    public static Item item(CityGood g) {
        return switch (g) {
            case BLE -> Items.WHEAT;
            case PAIN -> Items.BREAD;
            case VIANDE -> Items.COOKED_BEEF;
            case REPAS -> Items.MUSHROOM_STEW;
            case FER -> Items.IRON_INGOT;
            case OUTILS -> Items.IRON_PICKAXE;
            case PIERRE -> Items.STONE_BRICKS;
            case BOIS -> Items.OAK_PLANKS;
            case CUIR -> Items.LEATHER;
            case LIVRE -> Items.BOOK;
        };
    }

    // ------------------------------------------------------------------ ce qui commence

    private record Partner(Person person, PathfinderMob mob) {}

    private static int inCity(UUID site) {
        int n = 0;
        for (Scene s : ALL) if (s.site.equals(site)) n++;
        return n;
    }

    /** Le plus proche de {@code from} qui tient l'une de ces boutiques, qu'il soit libre, debout et bien là. */
    private static Optional<Partner> keeper(MinecraftServer server, ServerLevel level, Site site, Person self, List<BuildingFunction> functions, BlockPos from, int range) {
        LineageState state = data(server).lineage();
        for (BuildingFunction f : functions) {
            Partner best = null;
            double top = (double) range * range;
            for (CityBuilding b : site.buildings()) {
                if (b.function() != f) continue;
                for (UUID id : b.occupants()) {
                    Partner p = available(server, level, state, id, self, from, top);
                    if (p == null) continue;
                    best = p;
                    top = p.mob().blockPosition().distSqr(from);
                }
            }
            if (best != null) return Optional.of(best);
        }
        return Optional.empty();
    }

    private static Partner available(MinecraftServer server, ServerLevel level, LineageState state, UUID id, Person self, BlockPos from, double maxDist) {
        if (id.equals(self.id()) || busy(id)) return null;
        Person x = state.person(id).filter(Person::alive).orElse(null);
        if (x == null || x.age() < 16 || x.trip() != null || !x.inWorld()) return null;
        Entity e = level.getEntity(id);
        if (!(e instanceof PathfinderMob mob) || !mob.isAlive() || mob.isSleeping()) return null;
        if (mob instanceof Villager v && v.isTrading()) return null;
        if (mob.blockPosition().distSqr(from) > maxDist) return null;
        if (CityWork.activity(level.getDayTime(), x) == CityRules.Activity.SLEEP) return null;
        return new Partner(x, mob);
    }

    private static Scene begin(Scene s) {
        ALL.add(s);
        BUSY.put(s.a, s);
        BUSY.put(s.b, s);
        return s;
    }

    /**
     * Un habitant va acheter quelque chose. Faux s'il n'y a personne pour le lui vendre, ou rien à vendre, ou s'il n'a pas de quoi
     * payer : à celui qui appelle de voir. {@code eats} : il le mange ensuite.
     */
    public static boolean sale(MinecraftServer server, ServerLevel level, Site site, Person buyer, PathfinderMob mob, CityGood good, boolean eats) {
        if (busy(buyer.id()) || inCity(site.id()) >= MAX_PER_CITY || site.economy().stock(good) <= 0) return false;
        int price = price(good);
        if (buyer.coins() < price && !eats) return false;
        Optional<Partner> seller = keeper(server, level, site, buyer, shops(good), mob.blockPosition(), FIND);
        if (seller.isEmpty()) return false;
        begin(new Scene(Kind.SALE, site.id(), buyer.id(), seller.get().person().id(), good, 1, price, eats, level.getGameTime()));
        return true;
    }

    /** Un artisan va chercher ce qu'il lui faut chez un autre : la marchandise change de mains sans quitter la ville. */
    public static boolean supplyRun(MinecraftServer server, ServerLevel level, Site site, Person buyer, PathfinderMob mob, CityGood good) {
        if (busy(buyer.id()) || inCity(site.id()) >= MAX_PER_CITY || site.economy().stock(good) <= 0) return false;
        Optional<Partner> seller = keeper(server, level, site, buyer, shops(good), mob.blockPosition(), FIND);
        if (seller.isEmpty()) return false;
        begin(new Scene(Kind.SUPPLY, site.id(), buyer.id(), seller.get().person().id(), good, 8, price(good), false, level.getGameTime()));
        return true;
    }

    /** Une livraison remise à quelqu'un : celui qui tient le bâtiment visé. Faux s'il n'y a personne : on la pose alors et c'est tout. */
    public static boolean delivery(MinecraftServer server, ServerLevel level, Site site, Person carrier, PathfinderMob mob, BlockPos to, CityGood good, int amount) {
        if (busy(carrier.id()) || inCity(site.id()) >= MAX_PER_CITY) return false;
        BuildingFunction there = null;
        for (CityBuilding b : site.buildings()) if (b.spot().equals(to)) there = b.function();
        if (there == null) return false;
        Optional<Partner> to2 = keeper(server, level, site, carrier, List.of(there), to, FIND / 2);
        if (to2.isEmpty()) return false;
        begin(new Scene(Kind.DELIVERY, site.id(), carrier.id(), to2.get().person().id(), good, amount, 1, false, level.getGameTime()));
        return true;
    }

    /** Le marchand qui arrive traite avec le marché de la ville : tout est déjà compté, mais ça se voit. */
    public static boolean wholesale(MinecraftServer server, ServerLevel level, Site site, Person merchant, PathfinderMob mob, CityGood good, int amount) {
        if (busy(merchant.id())) return false;
        Optional<Partner> buyer = keeper(server, level, site, merchant, List.of(BuildingFunction.MARCHE, BuildingFunction.ENTREPOT, BuildingFunction.TAVERNE), mob.blockPosition(), FIND * 2);
        if (buyer.isEmpty()) return false;
        begin(new Scene(Kind.WHOLESALE, site.id(), merchant.id(), buyer.get().person().id(), good, amount, 0, false, level.getGameTime()));
        return true;
    }

    /** Deux voisins s'arrêtent pour causer. */
    public static boolean chat(MinecraftServer server, ServerLevel level, Site site, Person p, PathfinderMob mob) {
        long now = level.getGameTime();
        if (!site.settings().canSpeak || busy(p.id()) || inCity(site.id()) >= MAX_PER_CITY || p.age() < 16) return false;
        Long last = LAST_CHAT.get(p.id());
        if (last != null && now - last < CHAT_EVERY) return false;
        LineageState state = data(server).lineage();
        Partner best = null;
        double top = (double) NEIGHBOUR * NEIGHBOUR;
        for (CityBuilding b : site.buildings()) {
            for (UUID id : b.occupants()) {
                Partner x = available(server, level, state, id, p, mob.blockPosition(), top);
                if (x == null) continue;
                best = x;
                top = x.mob().blockPosition().distSqr(mob.blockPosition());
            }
        }
        if (best == null) return false;
        LAST_CHAT.put(p.id(), now);
        LAST_CHAT.put(best.person().id(), now);
        begin(new Scene(Kind.CHAT, site.id(), p.id(), best.person().id(), null, 0, 0, false, now));
        return true;
    }

    // ------------------------------------------------------------------ ce qui se joue

    public static void tick(MinecraftServer server) {
        for (Scene s : new ArrayList<>(ALL)) {
            Site site = data(server).world().site(s.site).orElse(null);
            ServerLevel level = site == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, site.dimension()));
            Entity ea = level == null ? null : level.getEntity(s.a), eb = level == null ? null : level.getEntity(s.b);
            if (!(ea instanceof PathfinderMob a) || !(eb instanceof PathfinderMob b) || !a.isAlive() || !b.isAlive()) {
                finish(server, level, s);
                continue;
            }
            long now = level.getGameTime();
            if (s.phase == Phase.WALK) {
                if (a.distanceToSqr(b) <= (REACH + 1) * (REACH + 1)) {
                    s.phase = Phase.ASK;
                    s.next = now;
                } else {
                    if (s.lastPos == null || a.blockPosition().distSqr(s.lastPos) > 1) {
                        s.lastPos = a.blockPosition();
                        s.lastMoved = now;
                    }
                    boolean blocked = now - s.lastMoved > BLOCKED;
                    if ((blocked || now > s.giveUp) && !witnessed(level, a) && !witnessed(level, b)) {
                        // Sans témoin, il y est : comme les colons de MineColonies, plutôt qu'un achat perdu pour un escalier.
                        a.teleportTo(b.getX(), b.getY(), b.getZ());
                        s.phase = Phase.ASK;
                        s.next = now;
                    } else if (now > s.giveUp) {
                        // Sous les yeux d'un joueur, on ne saute pas : il renonce à la scène, mais l'affaire se fait quand même.
                        Person pa = data(server).lineage().person(s.a).orElse(null), pb = data(server).lineage().person(s.b).orElse(null);
                        if (pa != null && pb != null && site != null) settle(server, level, site, s, pa, pb);
                        finish(server, level, s);
                        continue;
                    } else {
                        // Le vendeur reste à son affaire tant que l'autre n'est pas là. S'il est tout près, il l'attend ; si l'autre n'y arrive pas, il va à sa rencontre.
                        if (blocked) CityWork.toward(b, a.blockPosition(), REACH - 1);
                        else if (a.distanceToSqr(b) < 64) still(b, a);
                        CityWork.toward(a, b.blockPosition(), REACH - 1);
                        continue;
                    }
                }
            }
            still(a, b);
            still(b, a);
            if (now < s.next) continue;
            step(server, level, site, s, a, b);
        }
    }

    private static void step(MinecraftServer server, ServerLevel level, Site site, Scene s, PathfinderMob a, PathfinderMob b) {
        long now = level.getGameTime();
        Person pa = data(server).lineage().person(s.a).orElse(null), pb = data(server).lineage().person(s.b).orElse(null);
        if (pa == null || pb == null) {
            finish(server, level, s);
            return;
        }
        switch (s.phase) {
            case ASK -> {
                if (s.kind == Kind.DELIVERY || s.kind == Kind.WHOLESALE) hold(a, item(s.good));
                else if (s.kind != Kind.CHAT) hold(a, Items.GOLD_NUGGET);
                bubble(server, level, site, a, pa, pb, s, true);
                s.phase = Phase.ANSWER;
                s.next = now + BEAT;
            }
            case ANSWER -> {
                b.swing(InteractionHand.MAIN_HAND);
                if (s.kind == Kind.SALE || s.kind == Kind.SUPPLY) hold(b, item(s.good));
                else if (s.kind != Kind.CHAT) hold(b, Items.GOLD_NUGGET);
                bubble(server, level, site, b, pb, pa, s, false);
                s.phase = s.kind == Kind.CHAT ? Phase.DONE : Phase.SWAP;
                s.next = now + BEAT;
            }
            case SWAP -> {
                a.swing(InteractionHand.MAIN_HAND);
                b.swing(InteractionHand.MAIN_HAND);
                ItemStack fromA = a.getMainHandItem().copy(), fromB = b.getMainHandItem().copy();
                a.setItemSlot(EquipmentSlot.MAINHAND, fromB);
                b.setItemSlot(EquipmentSlot.MAINHAND, fromA);
                a.setDropChance(EquipmentSlot.MAINHAND, 0f);
                b.setDropChance(EquipmentSlot.MAINHAND, 0f);
                level.playSound(null, a.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.5f, 1f);
                level.playSound(null, a.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.25f, 1.4f);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, (a.getX() + b.getX()) / 2, a.getY() + 1.2, (a.getZ() + b.getZ()) / 2, 5, 0.3, 0.2, 0.3, 0);
                settle(server, level, site, s, pa, pb);
                s.phase = Phase.DONE;
                s.next = now + BEAT;
            }
            case DONE -> {
                if (s.eats) {
                    a.swing(InteractionHand.MAIN_HAND);
                    level.playSound(null, a.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.5f, 1f);
                }
                finish(server, level, s);
            }
            default -> {
            }
        }
    }

    /** Ce qui se passe vraiment, au moment où l'objet change de mains. */
    private static void settle(MinecraftServer server, ServerLevel level, Site site, Scene s, Person a, Person b) {
        CityEconomy eco = site.economy();
        String good = s.good == null ? "" : Fr.key(s.good.key() + ".du");
        switch (s.kind) {
            case SALE -> {
                int got = eco.take(s.good, s.amount);
                if (a.coins() >= s.price) {
                    a.setCoins(a.coins() - s.price);
                    b.setCoins(b.coins() + s.price);
                }
                if (got > 0 && s.eats) a.setHunger(Math.max(0, a.hunger() - 50));
                if (got > 0) CityJournal.add(server, site, CityJournal.Kind.SALE, Fr.key("crusadecraft.city.journal.sale", a.name(), good, b.name()));
            }
            case SUPPLY -> {
                // Ça reste dans la ville : l'artisan transforme ce qu'il achète, les comptes de la journée s'en occupent.
                int pay = Math.min(a.coins(), s.price);
                a.setCoins(a.coins() - pay);
                b.setCoins(b.coins() + pay);
                CityJournal.add(server, site, CityJournal.Kind.SALE, Fr.key("crusadecraft.city.journal.supply", a.name(), good, b.name()));
            }
            case DELIVERY -> {
                eco.add(s.good, s.amount);
                int pay = Math.min(b.coins(), s.price);
                b.setCoins(b.coins() - pay);
                a.setCoins(a.coins() + pay);
                CityJournal.add(server, site, CityJournal.Kind.SALE, Fr.key("crusadecraft.city.journal.delivery", a.name(), good, b.name()));
            }
            case WHOLESALE -> CityJournal.add(server, site, CityJournal.Kind.ROAD, Fr.key("crusadecraft.city.journal.wholesale", a.name(), b.name(), good));
            default -> {
            }
        }
        data(server).setDirty();
    }

    private static void finish(MinecraftServer server, ServerLevel level, Scene s) {
        ALL.remove(s);
        BUSY.remove(s.a, s);
        BUSY.remove(s.b, s);
        if (level != null) {
            for (UUID id : List.of(s.a, s.b)) {
                Entity e = level.getEntity(id);
                if (e instanceof PathfinderMob m) hold(m, null);
            }
        }
        CityWork.afterScene(s.a);
        CityWork.afterScene(s.b);
    }

    private static void hold(PathfinderMob mob, Item item) {
        mob.setItemSlot(EquipmentSlot.MAINHAND, item == null ? ItemStack.EMPTY : new ItemStack(item));
        mob.setDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    /** Il cesse de marcher et regarde l'autre. */
    private static void still(PathfinderMob me, Entity other) {
        if (CitySeats.seated(me)) me.stopRiding();
        if (me.isSleeping()) me.stopSleeping();
        me.getNavigation().stop();
        if (me instanceof Villager v) {
            var brain = v.getBrain();
            if (brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            if (brain.checkMemory(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED)) brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(other, true));
        }
        me.getLookControl().setLookAt(other, 30f, 30f);
    }

    private static boolean witnessed(ServerLevel level, Entity e) {
        for (ServerPlayer pl : level.players()) if (pl.distanceToSqr(e) < WITNESS * WITNESS) return true;
        return false;
    }

    private static void bubble(MinecraftServer server, ServerLevel level, Site site, PathfinderMob mob, Person speaker, Person other, Scene s, boolean first) {
        if (!site.settings().canSpeak || (speaker.hasSettings() && speaker.settings().chatty == 0)) return;
        if (!witnessed(level, mob)) return;
        Repliques.Moment moment = switch (s.kind) {
            case SALE, SUPPLY -> first ? Repliques.Moment.ECHANGE_DEMANDE : Repliques.Moment.ECHANGE_REPONSE;
            case DELIVERY -> first ? Repliques.Moment.REMISE_LIVRE : Repliques.Moment.REMISE_RECOIT;
            case WHOLESALE -> first ? Repliques.Moment.GROS_VEND : Repliques.Moment.GROS_ACHETE;
            case CHAT -> first ? Repliques.Moment.CAUSETTE : Repliques.Moment.CAUSETTE_REPONSE;
        };
        Map<String, String> extra = new HashMap<>();
        extra.put("autre", other.name());
        extra.put("marchandise", s.good == null ? "" : Fr.key(s.good.key() + ".du"));
        extra.put("prix", s.price == 1 ? "une pièce" : s.price + " pièces");
        CityVoice.line(server, site, speaker, moment, extra, RANDOM).ifPresent(text -> CityBubbles.say(level, mob, text));
    }
}
