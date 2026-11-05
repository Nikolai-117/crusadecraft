package fr.crusadecraft.core.city;

import fr.crusadecraft.compat.mca.McaBridge;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.diplomacy.Good;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.TradeService;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.lineage.LineageGenerator;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Role;
import fr.crusadecraft.core.logic.Fr;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * La vie d'une ville, jour après jour (P43), que des joueurs la regardent ou non : on produit, on mange, on paie les
 * salaires et l'impôt, on se marie, on a des enfants, on reprend le poste d'un mort, et quand on a trop faim, on s'en va.
 * C'est aussi ici que les étals suivent les réserves : ce que la ville n'a plus, plus personne ne le vend.
 */
public final class CityLife {
    /** Ce qu'on gagne par jour de la bourse de la ville : un poste, un adulte sans poste (il vit de ses bras, ailleurs). */
    static final int WAGE = 1, ODD_JOBS = 0;
    /** Au-delà de ces économies, le reste retourne à la bourse de la ville. */
    static final int SAVINGS = 40;
    /** Tant de jours de faim, et une famille s'en va. */
    static final int FAMINE_LEAVE = 4;
    /** Chances par jour, sur cent : un mariage, une naissance, quelqu'un qui part pour une autre ville, une arrivée dans une maison vide. */
    static final int MARRY = 6, BIRTH = 8, MOVE = 2, ARRIVE = 20;

    private CityLife() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** Une fois par jour du monde. */
    public static void onNewDay(MinecraftServer server, long day) {
        CrusadeSavedData data = data(server);
        Random random = new Random(day * 6151L + server.getWorldData().worldGenOptions().seed());
        CityCommerce.rollHistory(server);
        for (Site site : new ArrayList<>(data.world().sites())) {
            if (site.buildings().isEmpty() || !CityService.city(site)) continue;
            day(server, site, random);
        }
        // P55 : les blocus se paient, ou tombent.
        CityBlockades.onDay(server, random);
        // P51 : les villes de l'ordinateur vivent et commercent aussi.
        CityMarkets.onDay(server, random);
        CityCommerce.planAi(server, random);
        CityContracts.onDay(server, random);
        data.setDirty();
    }

    /** Qui vit et travaille là. */
    static CityEconomy.Census census(MinecraftServer server, Site site) {
        LineageState state = data(server).lineage();
        Map<BuildingFunction, Integer> workers = new EnumMap<>(BuildingFunction.class);
        Map<BuildingFunction, Integer> bonus = new EnumMap<>(BuildingFunction.class);
        int people = 0, storage = 0;
        for (CityBuilding b : site.buildings()) {
            if (b.function() == null) continue;
            // P45 : les meubles d'un atelier le font produire davantage ; ce qui se range donne de la place.
            bonus.merge(b.function(), Furnishing.bonus(b.function(), b.furniture()), Math::max);
            storage += Furnishing.storage(b.furniture());
            for (UUID id : b.occupants()) {
                Person p = state.person(id).orElse(null);
                if (p == null || !p.alive()) continue;
                people++;
                if (!b.function().dwelling() && p.age() >= 16 && !p.job().isEmpty()) workers.merge(b.function(), 1, Integer::sum);
            }
        }
        return new CityEconomy.Census(workers, people, data(server).wars().atWar(site.owner()), bonus, storage, site.charm(), CityBlockades.blocked(server, site));
    }

    /** Une journée pour cette ville. */
    public static CityEconomy.Day day(MinecraftServer server, Site site, Random random) {
        CityEconomy eco = site.economy();
        CityEconomy.Census census = census(server, site);
        boolean warehouse = has(site, BuildingFunction.ENTREPOT), market = has(site, BuildingFunction.MARCHE);
        if (!census.besieged()) convoy(server, site, eco, market, random);
        CityEconomy.Day today = eco.simulate(census, warehouse, market, random);
        payTax(server, site, today.tax());
        LineageState state = data(server).lineage();
        for (CityBuilding b : site.buildings()) {
            for (UUID id : b.occupants()) {
                state.person(id).filter(Person::alive).ifPresent(p -> {
                    int wage = p.age() < 16 ? 0 : p.job().isEmpty() ? ODD_JOBS : WAGE;
                    if (wage > 0 && eco.purse() >= wage) {
                        eco.addPurse(-wage);
                        p.setCoins(p.coins() + wage);
                    }
                    // La faim vient quand la ville n'a pas pu nourrir tout le monde, et s'en va quand elle le peut.
                    p.setHunger(today.hungry() > 0 ? p.hunger() + 34 : Math.max(0, p.hunger() - 34));
                    // P50 : ce que les boutiques encaissent revient à la ville : sans quoi les pièces finiraient toutes chez elles.
                    if (p.coins() > SAVINGS) {
                        eco.addPurse(p.coins() - SAVINGS);
                        p.setCoins(SAVINGS);
                    }
                    p.forgetABit();
                });
            }
        }
        CityJournal.add(server, site, CityJournal.Kind.LIFE, Fr.key("crusadecraft.city.journal.day", today.hungry(), today.imported(), today.exported(), today.tax()));
        for (CityGood g : today.missing()) CityRumors.add(server, site, "penurie", Fr.of(Component.translatable(g.key())).toLowerCase(java.util.Locale.ROOT));
        if (eco.famine() > 0) CityRumors.add(server, site, "famine");
        marriages(server, site, random);
        births(server, site, random);
        vacancies(server, site, random);
        if (eco.famine() >= FAMINE_LEAVE) leave(server, site, random);
        else if (!census.besieged()) arrivals(server, site, random);
        move(server, site, random);
        // P48 et P49 : on part en voyage, on commande ce qui manque, on écrit aux joueurs.
        CityOrders.onDay(server, site, random);
        CityTravel.onDay(server, site, random);
        CityPost.onDay(server, site, random);
        return today;
    }

    private static boolean has(Site site, BuildingFunction f) {
        for (CityBuilding b : site.buildings()) if (b.function() == f) return true;
        return false;
    }

    /** L'impôt va au royaume : c'est ce qui fait vivre une nation qui n'a que des villes posées. */
    private static void payTax(MinecraftServer server, Site site, long tax) {
        if (tax <= 0) return;
        Optional<AiRealm> ai = data(server).world().realm(site.owner());
        if (ai.isPresent()) ai.get().setGold(ai.get().gold() + tax);
        else data(server).realm(site.owner()).ifPresent(r -> fr.crusadecraft.core.treasury.TreasuryService.credit(server, r,
                fr.crusadecraft.core.treasury.LedgerCategory.IMPOTS, tax));
    }

    /** Un traité de commerce fait venir une caravane : le marché reçoit ce que produit l'autre royaume. */
    private static void convoy(MinecraftServer server, Site site, CityEconomy eco, boolean market, Random random) {
        if (!market || random.nextInt(4) != 0) return;
        long today = RealmService.currentDay(server);
        for (Relation r : data(server).diplomacy().all()) {
            if (!r.involves(site.owner()) || !r.has(Treaty.COMMERCE, today)) continue;
            UUID other = r.other(site.owner());
            Good good = TradeService.good(server, other);
            CityGood g = good == null ? CityGood.BLE : switch (good) {
                case FER -> CityGood.FER;
                case BOIS -> CityGood.BOIS;
                case PIERRE -> CityGood.PIERRE;
                case LAINE, FOURRURES -> CityGood.CUIR;
                case POISSON -> CityGood.VIANDE;
                default -> CityGood.BLE;
            };
            eco.add(g, 6);
            String from = data(server).world().realm(other).map(AiRealm::place).orElseGet(() -> data(server).realm(other).map(fr.crusadecraft.core.realm.Realm::name).orElse("loin"));
            CityRumors.add(server, site, "convoi", (LineageState.elides(from) ? "d'" : "de ") + from, Fr.key(g.key() + ".du"));
            return;
        }
    }

    /** Les vivants de la ville. */
    static List<Person> residents(MinecraftServer server, Site site) {
        LineageState state = data(server).lineage();
        List<Person> out = new ArrayList<>();
        for (CityBuilding b : site.buildings()) for (UUID id : b.occupants()) state.person(id).filter(Person::alive).ifPresent(out::add);
        return out;
    }

    private static void marriages(MinecraftServer server, Site site, Random random) {
        if (!site.settings().canMarry || random.nextInt(100) >= MARRY) return;
        LineageState state = data(server).lineage();
        List<Person> people = residents(server, site);
        java.util.Collections.shuffle(people, random);
        for (Person a : people) {
            boolean freeA = state.person(a.spouse()).filter(Person::alive).isEmpty();
            for (Person b : people) {
                boolean freeB = state.person(b.spouse()).filter(Person::alive).isEmpty();
                if (a == b || !CityRules.canMarry(a.age(), a.female(), freeA, b.age(), b.female(), freeB)) continue;
                if ((a.hasSettings() && !a.settings().canMarry) || (b.hasSettings() && !b.settings().canMarry)) continue;
                // Les personnages écrits d'avance ont leur histoire : on ne la leur change pas au hasard.
                if (!a.notable().isEmpty() || !b.notable().isEmpty()) continue;
                if (a.isChildOf(b.id()) || b.isChildOf(a.id()) || sameParents(a, b)) continue;
                LineageGenerator.marry(a, b);
                Person wife = a.female() ? a : b, husband = a.female() ? b : a;
                if (wife.notable().isEmpty() && !husband.surname().isEmpty()) {
                    wife.setSurname(husband.surname());
                    rename(server, site, wife);
                }
                ServerLevel level = level(server, site);
                if (level != null) McaBridge.syncTree(level, state, List.of(a, b));
                CityRumors.add(server, site, "mariage", a.name(), b.name());
                return;
            }
        }
    }

    private static boolean sameParents(Person a, Person b) {
        return (a.father() != null && a.father().equals(b.father())) || (a.mother() != null && a.mother().equals(b.mother()));
    }

    private static void births(MinecraftServer server, Site site, Random random) {
        if (random.nextInt(100) >= BIRTH) return;
        LineageState state = data(server).lineage();
        int people = CityService.people(server, site);
        List<Person> mothers = new ArrayList<>();
        for (Person p : residents(server, site)) {
            if (!p.female() || state.person(p.spouse()).filter(Person::alive).isEmpty()) continue;
            List<Person> children = state.children(p.id());
            int kids = (int) children.stream().filter(Person::alive).count();
            // Pas deux enfants la même année.
            if (children.stream().anyMatch(c -> c.alive() && c.age() < 1)) continue;
            if (CityRules.canHaveChild(p.age(), kids, people, CityService.PEOPLE_MAX)) mothers.add(p);
        }
        if (mothers.isEmpty()) return;
        Person mother = mothers.get(random.nextInt(mothers.size()));
        Optional<Person> child = CityPeople.generator(server, site, random.nextLong()).child(state, mother);
        if (child.isEmpty()) return;
        Person c = child.get();
        c.setAge(0);
        c.setCommunity(mother.community());
        c.setRole(Role.AUCUN);
        for (CityBuilding b : site.buildings()) {
            if (b.occupants().contains(mother.id())) {
                b.occupants().add(c.id());
                c.setWork(b.spot());
                break;
            }
        }
        CityPeople.dress(server, site, c, BuildingFunction.MAISON, random);
        Person father = state.person(mother.spouse()).orElse(mother);
        CityRumors.add(server, site, "naissance", mother.name(), father.name(), c.name());
    }

    /** Un poste dont le titulaire est mort : un adulte sans métier le reprend. C'est un autre qui en héritera sinon, le lendemain (voir {@link CityService}). */
    private static void vacancies(MinecraftServer server, Site site, Random random) {
        LineageState state = data(server).lineage();
        for (CityBuilding b : site.buildings()) {
            if (b.function() == null || b.function().dwelling()) continue;
            for (int i = 0; i < b.occupants().size(); i++) {
                Person dead = state.person(b.occupants().get(i)).orElse(null);
                if (dead == null || dead.alive()) continue;
                Optional<Person> next = successor(server, site, random);
                if (next.isEmpty()) return;
                take(server, site, b, i, next.get());
                CityRumors.add(server, site, "promotion", next.get().name(), Fr.of(Component.translatable(b.function().key())).toLowerCase(java.util.Locale.ROOT));
            }
        }
    }

    private static Optional<Person> successor(MinecraftServer server, Site site, Random random) {
        List<Person> free = new ArrayList<>();
        for (Person p : residents(server, site)) if (CityRules.canTakePost(p.age(), p.job(), !p.notable().isEmpty())) free.add(p);
        return free.isEmpty() ? Optional.empty() : Optional.of(free.get(random.nextInt(free.size())));
    }

    /** Il change de métier : il quitte sa maison pour son nouveau poste, avec les outils et l'étal qui vont avec. */
    static void take(MinecraftServer server, Site site, CityBuilding b, int index, Person p) {
        for (CityBuilding other : site.buildings()) if (other != b) other.occupants().remove(p.id());
        b.occupants().set(index, p.id());
        p.setJob(b.function().name());
        p.setRole(b.function().role());
        p.setWork(b.spot());
        ServerLevel level = level(server, site);
        Entity e = level == null || !p.inWorld() ? null : level.getEntity(p.id());
        if (e != null) CityTrades.apply(e, b.function(), CityService.post(b, index), level.random);
        CityWork.forgetPoints(b.id());
    }

    /** Une famille qui a trop faim s'en va : du monde et du registre. Les autres resteront plus nourris. */
    private static void leave(MinecraftServer server, Site site, Random random) {
        if (!site.settings().canLeave) return;
        List<CityBuilding> houses = new ArrayList<>();
        for (CityBuilding b : site.buildings()) if (b.function() == BuildingFunction.MAISON && !b.occupants().isEmpty()) houses.add(b);
        if (houses.isEmpty()) return;
        CityBuilding b = houses.get(random.nextInt(houses.size()));
        LineageState state = data(server).lineage();
        Person head = null;
        for (UUID id : b.occupants()) {
            Person p = state.person(id).orElse(null);
            if (p != null && p.alive() && (!p.notable().isEmpty() || (p.hasSettings() && !p.settings().canLeave))) return;
            if (p != null && p.alive() && head == null) head = p;
        }
        if (head != null) CityRumors.add(server, site, "depart", head.name());
        CityService.dismiss(server, b);
    }

    /** Une maison vide, dans une ville qui mange à sa faim : quelqu'un finit par s'y installer. */
    private static void arrivals(MinecraftServer server, Site site, Random random) {
        // P45 : une belle ville attire du monde.
        if (random.nextInt(100) >= ARRIVE + site.charm() / 4) return;
        for (CityBuilding b : site.buildings()) {
            if (b.function() != BuildingFunction.MAISON || !b.occupants().isEmpty() || CityService.full(server, site)) continue;
            CityService.settle(server, site, b, random.nextLong());
            data(server).lineage().person(b.occupants().isEmpty() ? null : b.occupants().get(0))
                    .ifPresent(p -> CityRumors.add(server, site, "arrivee", p.name()));
            return;
        }
    }

    /** Il arrive qu'un inconstant s'en aille vivre dans une autre ville du royaume. */
    private static void move(MinecraftServer server, Site site, Random random) {
        if (!site.settings().canLeave || random.nextInt(100) >= MOVE) return;
        List<Person> candidates = new ArrayList<>();
        for (Person p : residents(server, site)) {
            if ((p.hasSettings() && !p.settings().canLeave) || p.trip() != null) continue;
            if (p.age() >= 18 && p.job().isEmpty() && p.notable().isEmpty() && p.spouse() == null && p.traits().contains(Trait.INCONSTANT)) candidates.add(p);
        }
        if (candidates.isEmpty()) return;
        Person who = candidates.get(random.nextInt(candidates.size()));
        for (Site other : data(server).world().sites()) {
            if (other == site || !CityService.city(other) || !other.owner().equals(site.owner()) || CityService.full(server, other)) continue;
            for (CityBuilding house : other.buildings()) {
                if (house.function() != BuildingFunction.MAISON) continue;
                for (CityBuilding b : site.buildings()) b.occupants().remove(who.id());
                house.occupants().add(who.id());
                ServerLevel level = level(server, site);
                Entity e = level == null || !who.inWorld() ? null : level.getEntity(who.id());
                if (e != null) e.discard();
                who.setInWorld(null, null);
                who.setSite(other.id());
                who.setCommunity(other.community());
                who.setWork(house.spot());
                CityRumors.add(server, site, "depart", who.name());
                CityRumors.add(server, other, "arrivee", who.name());
                return;
            }
        }
    }

    private static ServerLevel level(MinecraftServer server, Site site) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, site.dimension()));
    }

    private static void rename(MinecraftServer server, Site site, Person p) {
        if (!p.inWorld()) return;
        ServerLevel level = level(server, site);
        Entity e = level == null ? null : level.getEntity(p.id());
        if (e == null) return;
        String full = data(server).lineage().fullName(p);
        if (McaBridge.isReady()) McaBridge.rename(e, full);
        else e.setCustomName(Component.literal(full));
    }

    /** P44 : un hospice, et l'on meurt moins : une fois sur deux, on s'en sort. Vrai si cette personne échappe à la mort. */
    public static boolean spared(MinecraftServer server, Person p, Random random) {
        if (p.site() == null) return false;
        Site s = data(server).world().site(p.site()).orElse(null);
        if (s == null) return false;
        LineageState state = data(server).lineage();
        for (CityBuilding b : s.buildings()) {
            if (b.function() != BuildingFunction.HOSPICE) continue;
            for (UUID id : b.occupants()) if (state.person(id).map(Person::alive).orElse(false)) return random.nextBoolean();
        }
        return false;
    }

    /** Les réserves et la bourse, en une ligne. */
    public static String summary(Site site) {
        CityEconomy e = site.economy();
        StringBuilder out = new StringBuilder(Fr.key("crusadecraft.city.economy.charm", site.charm())).append(" · ").append(Fr.key("crusadecraft.city.economy.purse", e.purse()));
        for (CityGood g : CityGood.values()) out.append(" · ").append(Fr.of(Component.translatable(g.key()))).append(' ').append(e.stock(g));
        return out.toString();
    }

    // ------------------------------------------------------------------ les étals

    /** Chaque jour, l'étal se regarnit de ce que la ville a : ce qui manque est épuisé, ce qui est rare coûte plus cher. */
    static void restock(Villager v, Site site) {
        v.restock();
        applyStock(v, site.economy());
    }

    /** Les échanges de ce villageois, d'après les réserves. */
    static void applyStock(Villager v, CityEconomy eco) {
        MerchantOffers offers = new MerchantOffers();
        for (MerchantOffer o : v.getOffers()) {
            CompoundTag t = o.createTag();
            CityGood sold = good(o.getResult());
            if (sold != null) {
                if (eco.stock(sold) <= 0) t.putInt("uses", t.getInt("maxUses"));
                t.putInt("demand", eco.scarcity(sold));
            }
            offers.add(new MerchantOffer(t));
        }
        CityTrades.setOffers(v, offers);
    }

    static CityGood good(ItemStack stack) {
        return stack.isEmpty() ? null : CityGood.ofItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /** Un joueur vient de commercer avec un habitant : les réserves et la bourse de la ville bougent, et il s'en souvient. */
    public static void onTrade(ServerPlayer player, Villager v, MerchantOffer offer) {
        MinecraftServer server = player.server;
        Optional<Person> p = CityTalk.person(server, v);
        if (p.isEmpty()) return;
        Optional<Site> site = CityTalk.siteOf(server, p.get());
        if (site.isEmpty()) return;
        CityEconomy eco = site.get().economy();
        ItemStack paid = offer.getCostA(), got = offer.getResult();
        CityGood sold = good(got), bought = good(paid);
        // Il vend : la réserve baisse, la bourse se remplit d'émeraudes. Il achète : l'inverse.
        if (sold != null) eco.take(sold, got.getCount());
        if (paid.is(net.minecraft.world.item.Items.EMERALD)) eco.addPurse(paid.getCount() * 8L);
        if (bought != null) eco.add(bought, paid.getCount());
        if (got.is(net.minecraft.world.item.Items.EMERALD)) eco.addPurse(-got.getCount() * 8L);
        if (eco.stock(sold == null ? CityGood.BLE : sold) <= 0 && sold != null) applyStock(v, eco);
        p.get().addOpinion(player.getUUID(), 1);
        if (v.level() instanceof ServerLevel level && level.random.nextInt(3) == 0) {
            CityVoice.text(server, site.get(), p.get(), fr.crusadecraft.core.lineage.Repliques.Moment.MERCI, player, new Random()).ifPresent(l -> CityBubbles.say(level, v, l));
        }
        data(server).setDirty();
    }
}
