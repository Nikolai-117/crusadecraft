package fr.crusadecraft.core.city;

import com.mojang.authlib.GameProfile;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Repliques;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CityPostPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Le courrier des habitants (P49). Ils écrivent aux joueurs qu'ils connaissent : un ami donne des nouvelles, un voyageur dit
 * où il part, un marchand rend compte d'une vente, une ville commande ce qui lui manque, et celui à qui on a fait du tort
 * ne se prive pas de le dire. Le texte vient de lots écrits d'avance. Une lettre attend qu'on se connecte.
 */
public final class CityPost {
    /** On garde au plus tant de lettres : les plus vieilles déjà lues s'en vont d'abord. */
    public static final int KEPT = 40;
    /** Au plus tant de lettres par joueur et par jour : personne n'a envie d'une boîte pleine. */
    static final int PER_DAY = 2;
    /** Un ami à partir de cet avis, un ennemi en dessous de celui-là. */
    static final int FRIEND = 25, FOE = -30;
    /** Chances, sur cent, qu'une ville donne des nouvelles à quelqu'un aujourd'hui. */
    static final int WRITE = 12;

    /** Ce qu'est une lettre. */
    public enum Kind {
        AMI(Repliques.Moment.MESSAGE_AMI), COMMANDE(Repliques.Moment.MESSAGE_COMMANDE), MERCI(Repliques.Moment.MESSAGE_MERCI), PLAINTE(Repliques.Moment.MESSAGE_PLAINTE),
        VOYAGE(Repliques.Moment.MESSAGE_VOYAGE), VENTE(Repliques.Moment.MESSAGE_VENTE);

        final Repliques.Moment moment;

        Kind(Repliques.Moment moment) {
            this.moment = moment;
        }

        public String key() {
            return "crusadecraft.city.post.kind." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Une lettre reçue. */
    public record Letter(Kind kind, String from, String city, String text, long day, boolean read) {
        public Letter asRead() {
            return new Letter(kind, from, city, text, day, true);
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("kind", kind.name());
            t.putString("from", from);
            t.putString("city", city);
            t.putString("text", text);
            t.putLong("day", day);
            t.putBoolean("read", read);
            return t;
        }

        public static Letter load(CompoundTag t) {
            Kind kind;
            try {
                kind = Kind.valueOf(t.getString("kind"));
            } catch (IllegalArgumentException e) {
                kind = Kind.AMI;
            }
            return new Letter(kind, t.getString("from"), t.getString("city"), t.getString("text"), t.getLong("day"), t.getBoolean("read"));
        }
    }

    /** Combien chacun a reçu aujourd'hui, et quel jour on en est. */
    private static final Map<UUID, Integer> SENT = new HashMap<>();
    private static long sentDay = -1;

    private CityPost() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    public static void onServerStopped() {
        SENT.clear();
        sentDay = -1;
    }

    /** Une boîte bornée : au-delà de {@link #KEPT}, la plus vieille lettre déjà lue s'en va, à défaut la plus vieille. La logique est pure. */
    public static void trim(List<Letter> box) {
        while (box.size() > KEPT) {
            int oldest = 0;
            for (int i = 0; i < box.size(); i++) {
                if (box.get(i).read()) {
                    oldest = i;
                    break;
                }
            }
            box.remove(oldest);
        }
    }

    private static String nameOf(MinecraftServer server, UUID player) {
        ServerPlayer on = server.getPlayerList().getPlayer(player);
        if (on != null) return on.getGameProfile().getName();
        if (server.getProfileCache() == null) return "";
        return server.getProfileCache().get(player).map(GameProfile::getName).orElse("");
    }

    /** La lettre arrive dans sa boîte, et s'il est là, il le sait tout de suite. {@code force} : une lettre qu'on doit à quelqu'un, sans plafond. */
    static boolean send(MinecraftServer server, UUID player, Letter letter, boolean force) {
        if (letter.day() != sentDay) {
            sentDay = letter.day();
            SENT.clear();
        }
        if (!force && SENT.getOrDefault(player, 0) >= PER_DAY) return false;
        SENT.merge(player, 1, Integer::sum);
        List<Letter> box = data(server).world().post(player);
        box.add(letter);
        trim(box);
        data(server).setDirty();
        ServerPlayer on = server.getPlayerList().getPlayer(player);
        if (on != null) notifyOf(on, letter);
        return true;
    }

    private static void notifyOf(ServerPlayer player, Letter l) {
        MutableComponent read = Component.translatable("crusadecraft.city.post.read").withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/crusade courrier"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(l.text()))));
        player.sendSystemMessage(Component.translatable("crusadecraft.city.post.arrived", l.from(), l.city()).withStyle(ChatFormatting.GRAY).append(" ").append(read));
    }

    /** À son arrivée : ce qui l'attend. */
    public static void onLogin(ServerPlayer player) {
        long unread = data(player.server).world().post(player.getUUID()).stream().filter(l -> !l.read()).count();
        if (unread == 0) return;
        MutableComponent read = Component.translatable("crusadecraft.city.post.read").withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/crusade courrier")));
        player.sendSystemMessage(Component.translatable("crusadecraft.city.post.waiting", unread).withStyle(ChatFormatting.GRAY).append(" ").append(read));
    }

    /** Sa boîte, à l'écran. */
    public static void open(ServerPlayer player) {
        CrusadeNetwork.toPlayer(player, new CityPostPackets.Open(new ArrayList<>(data(player.server).world().post(player.getUUID()))));
    }

    /** Une lettre lue, une lettre jetée, toutes jetées. */
    public static void act(ServerPlayer player, int op, int index) {
        if (op == CityPostPackets.OPEN) {
            open(player);
            return;
        }
        List<Letter> box = data(player.server).world().post(player.getUUID());
        if (op == CityPostPackets.READ && index >= 0 && index < box.size()) box.set(index, box.get(index).asRead());
        else if (op == CityPostPackets.DELETE && index >= 0 && index < box.size()) box.remove(index);
        else if (op == CityPostPackets.CLEAR) box.clear();
        else return;
        data(player.server).setDirty();
        if (op != CityPostPackets.READ) open(player);
    }

    // ------------------------------------------------------------------ ce qu'ils écrivent

    private static Optional<Letter> compose(MinecraftServer server, Site site, Person p, Kind kind, UUID to, Map<String, String> extra, Random random) {
        Optional<String> line = CityVoice.letter(server, site, p, kind.moment, to, nameOf(server, to), extra, random);
        if (line.isEmpty()) return Optional.empty();
        return Optional.of(new Letter(kind, data(server).lineage().fullName(p), site.name(), line.get(), RealmService.currentDay(server), false));
    }

    /** Une fois par jour, pour une ville : quelqu'un qui connaît un joueur lui écrit, en bien ou en mal. */
    static void onDay(MinecraftServer server, Site site, Random random) {
        if (!site.settings().canSpeak || random.nextInt(100) >= WRITE) return;
        List<Person> people = CityLife.residents(server, site);
        java.util.Collections.shuffle(people, random);
        for (Person p : people) {
            if (p.age() < 16 || (p.hasSettings() && p.settings().chatty == 0)) continue;
            for (UUID player : p.knownPlayers()) {
                int opinion = p.opinionOf(player);
                Kind kind = opinion >= FRIEND ? Kind.AMI : opinion <= FOE ? Kind.PLAINTE : null;
                if (kind == null) continue;
                compose(server, site, p, kind, player, Map.of(), random).ifPresent(l -> send(server, player, l, false));
                return;
            }
        }
    }

    /** Qui écrit au nom d'une ville : son chef s'il en est un parmi les gens, à défaut quelqu'un d'une boutique ; à défaut, le premier venu. */
    private static Optional<Person> clerk(MinecraftServer server, Site site, Random random) {
        Person found = null;
        List<Person> all = CityLife.residents(server, site);
        java.util.Collections.shuffle(all, random);
        for (Person p : all) {
            if (p.age() < 16) continue;
            BuildingFunction f = BuildingFunction.byName(p.job());
            if (f == BuildingFunction.MAIRIE || f == BuildingFunction.CHATEAU) return Optional.of(p);
            if (found == null || (found.job().isEmpty() && !p.job().isEmpty())) found = p;
        }
        return Optional.ofNullable(found);
    }

    /** À qui écrit une ville : au joueur qui la gouverne, sinon à celui que ses gens estiment le plus. */
    static Optional<UUID> patron(MinecraftServer server, Site site) {
        CrusadeSavedData data = data(server);
        Optional<UUID> ruler = data.realm(site.owner()).map(fr.crusadecraft.core.realm.Realm::ruler);
        if (ruler.isPresent()) return ruler;
        Optional<UUID> sovereign = data.world().realm(site.owner()).map(fr.crusadecraft.core.world.AiRealm::sovereign);
        if (sovereign.isPresent()) return sovereign;
        UUID best = null;
        int top = 0;
        Map<UUID, Integer> total = new HashMap<>();
        for (Person p : CityLife.residents(server, site)) {
            for (UUID player : p.knownPlayers()) {
                int v = total.merge(player, p.opinionOf(player), Integer::sum);
                if (v > top) {
                    top = v;
                    best = player;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** La ville manque de quelque chose : elle l'écrit à qui peut l'aider. */
    static void order(MinecraftServer server, Site site, CityOrders.Order o, Random random) {
        Optional<UUID> to = patron(server, site);
        Optional<Person> from = clerk(server, site, random);
        if (to.isEmpty() || from.isEmpty()) return;
        Map<String, String> extra = Map.of("commande", CityOrders.describe(o.good(), o.count()), "recompense", CityOrders.money(o.reward()));
        compose(server, site, from.get(), Kind.COMMANDE, to.get(), extra, random).ifPresent(l -> send(server, to.get(), l, true));
    }

    /** La ville a reçu sa commande : elle le dit, et celui à qui on l'a remise signe. */
    static void thanks(MinecraftServer server, Site site, Person speaker, ServerPlayer player, CityOrders.Order o) {
        Random random = new Random();
        Person from = speaker != null ? speaker : clerk(server, site, random).orElse(null);
        if (from == null) return;
        Map<String, String> extra = Map.of("commande", CityOrders.describe(o.good(), o.count()), "recompense", CityOrders.money(o.reward()));
        compose(server, site, from, Kind.MERCI, player.getUUID(), extra, random).ifPresent(l -> send(server, player.getUUID(), l, true));
    }

    /** Il part en voyage : ceux qui comptent pour lui le savent. */
    static void travelNews(MinecraftServer server, Site home, Site dest, Person p) {
        Random random = new Random();
        for (UUID player : p.knownPlayers()) {
            if (p.opinionOf(player) < FRIEND) continue;
            compose(server, home, p, Kind.VOYAGE, player, Map.of("destination", dest.name()), random).ifPresent(l -> send(server, player, l, false));
        }
    }

    /** Un marchand a vendu sa cargaison : il en rend compte à qui gouverne sa ville. */
    static void saleNews(MinecraftServer server, Site home, Site dest, Person p, CityGood good, int amount, int earned) {
        Optional<UUID> to = patron(server, home);
        if (to.isEmpty()) return;
        Map<String, String> extra = Map.of("destination", dest.name(), "commande", CityOrders.describe(good, amount), "recompense", CityOrders.money(earned));
        compose(server, home, p, Kind.VENTE, to.get(), extra, new Random()).ifPresent(l -> send(server, to.get(), l, false));
    }
}
