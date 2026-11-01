package fr.crusadecraft.core.ai;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.logic.Heraldry;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.war.CasusBelli;
import fr.crusadecraft.core.war.WarService;
import fr.crusadecraft.core.world.AiCharacter;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.NameGenerator;
import fr.crusadecraft.core.world.WorldNews;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Les successions qui défont un royaume de l'ordinateur (P26). Sous la loi égalitaire, un royaume d'au moins trois
 * villes se partage : le cadet emporte les villes les plus éloignées de la capitale, d'un seul tenant, et fonde une
 * branche de la même maison. Sous les lois élective et d'ancienneté, la succession peut tourner à la crise : un
 * prétendant prend un tiers du royaume et lui déclare la guerre pour le reste.
 *
 * <p>Les royaumes posés à la main n'y sont pas soumis, sauf réglage contraire : Romain les a dessinés lui-même.
 */
public final class PartitionService {
    /** Le moins de villes pour qu'un royaume se partage. */
    public static final int MIN_PARTITION = 3;
    /** Le moins de villes pour qu'un prétendant trouve de quoi se tailler un fief. */
    public static final int MIN_CRISIS = 2;

    private PartitionService() {}

    /** Une ville, en logique pure : son numéro, sa place, et ce qu'elle pèse. */
    public record Seat(int id, int x, int z, boolean capital, int population) {
        double distSqr(Seat o) {
            double dx = x - o.x, dz = z - o.z;
            return dx * dx + dz * dz;
        }
    }

    /** La part du cadet : la moitié des villes autres que la capitale (arrondie en dessous), les plus éloignées. */
    public static List<Integer> partitionShare(List<Seat> own) {
        if (own.size() < MIN_PARTITION) return List.of();
        return cluster(own, (own.size() - 1) / 2);
    }

    /** La part d'un prétendant : un tiers des villes, au moins une, jamais la capitale. */
    public static List<Integer> crisisShare(List<Seat> own) {
        if (own.size() < MIN_CRISIS) return List.of();
        return cluster(own, Math.max(1, own.size() / 3));
    }

    /**
     * {@code count} villes d'un seul tenant, loin de la capitale : la plus éloignée d'abord, puis ses plus proches
     * voisines. La capitale n'en fait jamais partie.
     */
    static List<Integer> cluster(List<Seat> own, int count) {
        Seat capital = own.stream().filter(Seat::capital).findFirst().orElse(own.get(0));
        List<Seat> others = new ArrayList<>();
        for (Seat s : own) if (s.id() != capital.id()) others.add(s);
        if (others.isEmpty() || count <= 0) return List.of();
        Seat anchor = others.stream().max(Comparator.comparingDouble((Seat s) -> s.distSqr(capital)).thenComparingInt(Seat::id)).orElseThrow();
        others.sort(Comparator.comparingDouble((Seat s) -> s.distSqr(anchor)).thenComparingInt(Seat::id));
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < Math.min(count, others.size()); i++) out.add(others.get(i).id());
        return out;
    }

    /** La capitale du nouveau royaume : la plus peuplée de sa part. */
    public static int newCapital(List<Seat> share) {
        return share.stream().max(Comparator.comparingInt(Seat::population).thenComparingInt(s -> -s.id())).orElseThrow().id();
    }

    /** Le rang du nouveau royaume : un cran sous celui qu'il quitte, comté au moins. */
    public static TitleRank cadetRank(TitleRank parent) {
        return TitleRank.values()[Math.max(TitleRank.COMTE.ordinal(), parent.ordinal() - 1)];
    }

    static List<Seat> seats(AiRealm realm) {
        AiProvince capital = realm.capital();
        List<Seat> out = new ArrayList<>();
        for (AiProvince p : realm.ownProvinces()) {
            out.add(new Seat(p.id(), p.seat().getX(), p.seat().getZ(), p == capital, p.population()));
        }
        return out;
    }

    /** Ce royaume peut-il être défait par une succession ? */
    static boolean eligible(AiRealm realm) {
        return !realm.handMade() || CrusadeConfig.SERVER.handMadeSuccessions.get();
    }

    /**
     * Après la mort d'un souverain : partage sous la loi égalitaire, crise parfois sous les lois élective et
     * d'ancienneté. Renvoie le royaume né de la succession, s'il y en a un.
     */
    public static Optional<AiRealm> afterSuccession(MinecraftServer server, AiRealm realm, fr.crusadecraft.core.lineage.LifeService.Succession law,
                                                    AiCharacter dead, long day, Random random) {
        if (!eligible(realm)) return Optional.empty();
        if (law == fr.crusadecraft.core.lineage.LifeService.Succession.EGALITAIRE && CrusadeConfig.SERVER.partitions.get()) {
            return partition(server, realm, cadet(dead, random), day);
        }
        boolean contested = law == fr.crusadecraft.core.lineage.LifeService.Succession.ELECTIVE
                || law == fr.crusadecraft.core.lineage.LifeService.Succession.ANCIENNETE;
        if (contested && CrusadeConfig.SERVER.crises.get() && random.nextInt(100) < CrusadeConfig.SERVER.crisisChance.get()) {
            return crisis(server, realm, pretender(realm, dead, random), day);
        }
        return Optional.empty();
    }

    /** Le cadet : un enfant du mort, de son sang. */
    static AiCharacter cadet(AiCharacter dead, Random random) {
        boolean female = random.nextBoolean();
        return new AiCharacter(new NameGenerator(random.nextLong()).personName(female), female, 16 + random.nextInt(14),
                dead.stats().clone(), new ArrayList<>(dead.traits().subList(0, Math.min(1, dead.traits().size()))));
    }

    /** Le prétendant : un conseiller adulte s'il y en a un (il quitte le conseil), sinon un parent éloigné. */
    static AiCharacter pretender(AiRealm realm, AiCharacter dead, Random random) {
        for (Map.Entry<fr.crusadecraft.core.character.Post, AiCharacter> e : new ArrayList<>(realm.council().entrySet())) {
            if (e.getValue().age() >= fr.crusadecraft.core.lineage.LifeService.ADULT) {
                realm.council().remove(e.getKey());
                return e.getValue();
            }
        }
        return cadet(dead, random);
    }

    /** Le partage : le cadet fonde une branche de la même maison, avec la moitié du trésor de sa part. */
    public static Optional<AiRealm> partition(MinecraftServer server, AiRealm realm, AiCharacter heir, long day) {
        List<Integer> share = partitionShare(seats(realm));
        if (share.isEmpty()) return Optional.empty();
        AiRealm cadet = split(server, realm, share, heir, realm.house(), 2, day);
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} se partage ; {} fonde {}", realm.place(), heir.name(), cadet.place());
        AiBrain.publish(server, WorldNews.of(day, "crusadecraft.news.partition", List.of(realm.id(), cadet.id()),
                AiNames.realm(realm.rank(), realm.place()), Component.literal(heir.name()), AiNames.realm(cadet.rank(), cadet.place())), true);
        return Optional.of(cadet);
    }

    /** La crise : le prétendant se taille un fief, avec un quart du trésor, et réclame la capitale par les armes. */
    public static Optional<AiRealm> crisis(MinecraftServer server, AiRealm realm, AiCharacter pretender, long day) {
        List<Integer> share = crisisShare(seats(realm));
        if (share.isEmpty()) return Optional.empty();
        String house = new NameGenerator(pretender.name().hashCode() * 31L + day).place();
        AiRealm rebel = split(server, realm, share, pretender, house, 4, day);
        AiProvince capital = realm.capital();
        if (capital != null && WarService.declarationProblem(server, rebel.id(), realm.id()) == null) {
            WarService.declareBetween(server, rebel.id(), realm.id(), CasusBelli.CLAIM, capital.id());
        }
        CrusadeCraft.LOGGER.info("CrusadeCraft : crise de succession en {} ; {} se soulève", realm.place(), pretender.name());
        AiBrain.publish(server, WorldNews.of(day, "crusadecraft.news.crisis", List.of(realm.id(), rebel.id()),
                AiNames.realm(realm.rank(), realm.place()), Component.literal(pretender.name()), AiNames.realm(rebel.rank(), rebel.place())), true);
        return Optional.of(rebel);
    }

    /**
     * Détache ces villes en un nouveau royaume : mêmes numéros (les lieux, revendications et fiches qui les visent
     * suivent), même foi, même personnalité ; le trésor se divise par {@code goldDivisor}. Ceux qui connaissaient
     * l'ancien royaume connaissent le nouveau.
     */
    static AiRealm split(MinecraftServer server, AiRealm realm, List<Integer> ids, AiCharacter ruler, String house, int goldDivisor, long day) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        List<Seat> shareSeats = new ArrayList<>();
        for (Seat s : seats(realm)) if (ids.contains(s.id())) shareSeats.add(s);
        int capitalId = newCapital(shareSeats);
        AiProvince capitalProvince = null;
        for (AiProvince p : realm.provinces()) if (p.id() == capitalId) capitalProvince = p;
        String place = capitalProvince == null ? realm.place() : capitalProvince.name();
        Heraldry[] colors = Heraldry.values();
        int base = Heraldry.nearest(realm.color()).ordinal();
        int color = colors[Math.floorMod(base + 1 + Math.floorMod(place.hashCode(), colors.length - 1), colors.length)].rgb();
        AiRealm born = new AiRealm(UUID.randomUUID(), place, cadetRank(realm.rank()), color, house, realm.personality(), ruler, day);
        born.setFaith(realm.faith());
        for (AiProvince p : new ArrayList<>(realm.provinces())) {
            if (!ids.contains(p.id())) continue;
            realm.provinces().remove(p);
            AiProvince moved = new AiProvince(p.id(), p.name(), p.dimension(), p.seat(), p.anchor(), p.id() == capitalId, p.dejureKingdom(),
                    p.population(), p.development());
            born.provinces().add(moved);
        }
        long gold = realm.gold() / goldDivisor;
        born.setGold(gold);
        realm.setGold(realm.gold() - gold);
        born.setPrestige(realm.prestige() / 4);
        for (Map.Entry<UUID, Long> e : realm.discoveredBy().entrySet()) born.discover(e.getKey(), day);
        data.world().add(born);
        data.setDirty();
        return born;
    }
}
