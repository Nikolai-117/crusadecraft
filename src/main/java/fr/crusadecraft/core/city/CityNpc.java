package fr.crusadecraft.core.city;

import fr.crusadecraft.compat.mca.McaBridge;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Role;
import fr.crusadecraft.core.logic.TextRules;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.NpcPackets;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * L'éditeur d'un habitant (P47), comme la baguette d'un mod de PNJ : accroupi avec le bâton d'intendant sur lui, ou depuis le
 * panneau de la ville. On y règle sa fiche, son allure, ses statistiques, ses habitudes, ses répliques et ses échanges, et
 * tout s'applique aussitôt au villageois qui l'incarne.
 */
public final class CityNpc {
    private static final int TEXT_MAX = 200;
    public static final int LINES_MAX = 24, OFFERS_MAX = 24;
    private static final int LOOK_MAX = 200;
    public static final int BIO_EDIT_MAX = 800;

    private CityNpc() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    private static Entity entity(MinecraftServer server, Person p) {
        if (!p.inWorld()) return null;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, p.worldDim()));
        return level == null ? null : level.getEntity(p.id());
    }

    // ------------------------------------------------------------------ appliquer

    /** Le villageois prend tout ce qui est réglé sur sa personne. À son apparition, et à chaque changement. */
    public static void apply(MinecraftServer server, Entity e, Person p) {
        if (e == null || !p.hasSettings()) return;
        NpcSettings s = p.settings();
        applyName(server, e, p);
        if (!s.look.isEmpty() && !s.look.startsWith(SkinService.PREFIX)) McaBridge.setClothes(e, s.look);
        if (!s.hair.isEmpty()) McaBridge.setHair(e, s.hair);
        if (s.size >= 0) McaBridge.setSize(e, s.size);
        if (e instanceof LivingEntity living) {
            attribute(living, Attributes.MAX_HEALTH, s.maxHealth);
            attribute(living, Attributes.MOVEMENT_SPEED, s.speed);
            attribute(living, Attributes.ATTACK_DAMAGE, s.damage);
            if (s.maxHealth > 0 && living.getHealth() > living.getMaxHealth()) living.setHealth(living.getMaxHealth());
        }
        e.setInvulnerable(s.invincible);
        if (s.offers != null && e instanceof Villager v) CityTrades.setOffers(v, new MerchantOffers(s.offers));
    }

    private static void attribute(LivingEntity e, Attribute a, double value) {
        if (value <= 0) return;
        AttributeInstance i = e.getAttribute(a);
        // Comes Alive ajoute ses propres bonus : on vise ce qu'on a demandé au total, pas la valeur de base.
        if (i != null) i.setBaseValue(Math.max(0.01, value - (i.getValue() - i.getBaseValue())));
    }

    /** Son nom et son titre, là où ils se voient. */
    private static void applyName(MinecraftServer server, Entity e, Person p) {
        LineageState state = data(server).lineage();
        String full = state.fullName(p);
        if (p.hasSettings() && !p.settings().title.isEmpty()) full += ", " + p.settings().title;
        if (McaBridge.isReady()) McaBridge.rename(e, full);
        else e.setCustomName(Component.literal(full));
        e.setCustomNameVisible(!p.hasSettings() || p.settings().showName);
    }

    /** Une nouvelle allure : la tenue ou le skin importé ; vide, celle qu'il avait de naissance. */
    public static void setLook(ServerPlayer player, UUID id, String look) {
        CompoundTag t = new CompoundTag();
        t.putString("look", look);
        Outcome out = edit(player, id, t);
        player.displayClientMessage(out.message(), true);
    }

    /** Ce que le serveur sait d'une allure, à répéter aux joueurs quand elle change. */
    static void announceLook(MinecraftServer server, Person p) {
        SkinService.broadcast(server, p.id(), SkinService.hashOf(p.settings().look));
    }

    // ------------------------------------------------------------------ l'écran

    /** Ouvre l'éditeur. */
    public static boolean open(ServerPlayer player, UUID id) {
        Optional<Person> p = data(player.server).lineage().person(id).filter(Person::alive);
        if (p.isEmpty()) return false;
        Optional<Site> site = CityTalk.siteOf(player.server, p.get());
        if (site.isEmpty() || !CityService.allowed(player, site.get())) {
            player.displayClientMessage(Component.translatable("crusadecraft.city.denied"), true);
            return false;
        }
        CrusadeNetwork.toPlayer(player, new NpcPackets.Open(view(player.server, site.get(), p.get())));
        return true;
    }

    /** Tout ce que l'écran doit savoir, à plat. */
    static CompoundTag view(MinecraftServer server, Site site, Person p) {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", p.id());
        Entity e = entity(server, p);
        t.putInt("entity", e == null ? -1 : e.getId());
        t.putString("name", p.name());
        t.putString("surname", p.surname());
        t.putBoolean("female", p.female());
        t.putInt("age", p.age());
        t.putString("bio", p.bio());
        t.putString("job", p.job());
        t.putString("jobTitle", CityTalk.jobTitle(p));
        t.putString("city", site.name());
        t.putBoolean("notable", !p.notable().isEmpty());
        ListTag traits = new ListTag();
        for (Trait x : p.traits()) traits.add(IntTag.valueOf(x.ordinal()));
        t.put("traits", traits);
        t.put("settings", p.settings().save());
        if (e instanceof Villager v) t.put("offersNow", v.getOffers().createTag());
        return t;
    }

    // ------------------------------------------------------------------ changer

    /** Un changement venu de l'écran : seules les clés présentes changent. */
    public static Outcome edit(ServerPlayer player, UUID id, CompoundTag c) {
        MinecraftServer server = player.server;
        LineageState state = data(server).lineage();
        Optional<Person> found = state.person(id).filter(Person::alive);
        if (found.isEmpty()) return Outcome.no("crusadecraft.lineage.unknown");
        Person p = found.get();
        Optional<Site> site = CityTalk.siteOf(server, p);
        if (site.isEmpty()) return Outcome.no("crusadecraft.city.unknown");
        if (!CityService.allowed(player, site.get())) return Outcome.no("crusadecraft.city.denied");
        Outcome out = apply(server, site.get(), p, c);
        if (!out.success()) return out;
        Entity e = entity(server, p);
        if (e != null) apply(server, e, p);
        data(server).setDirty();
        CrusadeNetwork.toPlayer(player, new NpcPackets.Open(view(server, site.get(), p)));
        return out;
    }

    /** Les changements eux-mêmes, sur la personne : sans joueur, pour pouvoir les essayer. */
    public static Outcome apply(MinecraftServer server, Site site, Person p, CompoundTag c) {
        NpcSettings s = p.settings();
        if (c.contains("name")) {
            String name = TextRules.clean(c.getString("name"), TextRules.NAME_MAX);
            if (!TextRules.isValidName(name)) return Outcome.no("crusadecraft.name.invalid", TextRules.NAME_MAX);
            p.setName(name);
        }
        if (c.contains("surname")) p.setSurname(TextRules.clean(c.getString("surname"), TextRules.NAME_MAX));
        if (c.contains("title")) s.title = TextRules.clean(c.getString("title"), 32);
        if (c.contains("female")) p.setFemale(c.getBoolean("female"));
        if (c.contains("age")) p.setAge(c.getInt("age"));
        if (c.contains("bio")) {
            String bio = c.getString("bio").strip();
            // Les personnages écrits à la main ont souvent une vie plus longue que celles du mod.
            p.setBio(bio.length() > BIO_EDIT_MAX ? bio.substring(0, BIO_EDIT_MAX) : bio);
        }
        if (c.contains("traits")) {
            List<Trait> traits = new ArrayList<>();
            Trait[] all = Trait.values();
            for (Tag x : c.getList("traits", Tag.TAG_INT)) {
                int i = ((IntTag) x).getAsInt();
                if (i >= 0 && i < all.length) traits.add(all[i]);
            }
            p.setTraits(traits, Person.MAX_TRAITS);
        }
        if (c.contains("look")) {
            String look = c.getString("look");
            String hash = SkinService.hashOf(look);
            if (!hash.isEmpty() && SkinService.entry(hash).isEmpty()) return Outcome.no("crusadecraft.skin.not_found", hash);
            s.look = bounded(look);
            announceLook(server, p);
        }
        if (c.contains("hair")) s.hair = bounded(c.getString("hair"));
        if (c.contains("size")) s.size = c.getFloat("size") < 0 ? -1f : Math.min(1f, c.getFloat("size"));
        if (c.contains("showName")) s.showName = c.getBoolean("showName");
        if (c.contains("maxHealth")) s.maxHealth = clamp(c.getDouble("maxHealth"), 0, 1024);
        if (c.contains("speed")) s.speed = clamp(c.getDouble("speed"), 0, 2);
        if (c.contains("damage")) s.damage = clamp(c.getDouble("damage"), 0, 256);
        if (c.contains("invincible")) s.invincible = c.getBoolean("invincible");
        if (c.contains("behavior")) {
            try {
                s.behavior = NpcSettings.Behavior.valueOf(c.getString("behavior"));
            } catch (IllegalArgumentException e) {
                return Outcome.no("crusadecraft.city.unknown");
            }
        }
        if (c.contains("workStart")) s.workStart = c.getInt("workStart");
        if (c.contains("workEnd")) s.workEnd = c.getInt("workEnd");
        if (c.contains("canTravel")) s.canTravel = c.getBoolean("canTravel");
        if (c.contains("canMarry")) s.canMarry = c.getBoolean("canMarry");
        if (c.contains("canLeave")) s.canLeave = c.getBoolean("canLeave");
        if (c.contains("chatty")) s.chatty = Math.max(0, Math.min(2, c.getInt("chatty")));
        if (c.contains("addLine")) {
            String moment = c.getString("moment"), text = c.getString("addLine").strip();
            if (text.isEmpty() || moment.isEmpty() || moment.length() > 32) return Outcome.no("crusadecraft.city.npc.line.empty");
            if (text.length() > TEXT_MAX) text = text.substring(0, TEXT_MAX);
            List<String> lines = s.lines.computeIfAbsent(moment, k -> new ArrayList<>());
            if (lines.size() >= LINES_MAX) return Outcome.no("crusadecraft.city.npc.line.full", LINES_MAX);
            lines.add(text);
        }
        if (c.contains("removeLine")) {
            List<String> lines = s.lines.get(c.getString("moment"));
            int i = c.getInt("removeLine");
            if (lines != null && i >= 0 && i < lines.size()) lines.remove(i);
            if (lines != null && lines.isEmpty()) s.lines.remove(c.getString("moment"));
        }
        if (c.contains("job")) {
            String error = setJob(server, site, p, c.getString("job"));
            if (error != null) return Outcome.no(error);
        }
        if (c.contains("addOffer")) {
            CompoundTag o = c.getCompound("addOffer");
            ItemStack a = ItemStack.of(o.getCompound("a")), b = ItemStack.of(o.getCompound("b")), r = ItemStack.of(o.getCompound("r"));
            if (a.isEmpty() || r.isEmpty()) return Outcome.no("crusadecraft.city.npc.offer.empty");
            MerchantOffers offers = s.offers == null ? current(server, p) : new MerchantOffers(s.offers);
            if (offers.size() >= OFFERS_MAX) return Outcome.no("crusadecraft.city.npc.offer.full", OFFERS_MAX);
            offers.add(new MerchantOffer(a, b, r, Math.max(1, Math.min(9999, o.getInt("uses"))), 2, 0.05f));
            s.offers = offers.createTag();
        }
        if (c.contains("removeOffer")) {
            MerchantOffers offers = s.offers == null ? current(server, p) : new MerchantOffers(s.offers);
            int i = c.getInt("removeOffer");
            if (i >= 0 && i < offers.size()) offers.remove(i);
            s.offers = offers.createTag();
        }
        if (c.getBoolean("resetOffers")) {
            s.offers = null;
            Entity e = entity(server, p);
            BuildingFunction f = BuildingFunction.byName(p.job());
            if (e != null && f != null && server.overworld() != null) CityTrades.apply(e, f, f.posts().isEmpty() ? "minecraft:none" : f.posts().get(0), server.overworld().random);
        }
        // Son nom a pu changer : on le répète dans l'arbre et au-dessus de sa tête, même sans autre changement.
        Entity e = entity(server, p);
        if (e != null) applyName(server, e, p);
        return Outcome.ok(Component.translatable("crusadecraft.city.npc.saved", p.name()));
    }

    private static MerchantOffers current(MinecraftServer server, Person p) {
        Entity e = entity(server, p);
        if (e instanceof Villager v) return new MerchantOffers(v.getOffers().createTag());
        return new MerchantOffers();
    }

    /** Un chemin de texture : long comme il faut, pas davantage. */
    private static String bounded(String path) {
        return path.length() > LOOK_MAX ? "" : path;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Il change de métier : il passe au bâtiment de ce métier s'il y en a un ; sinon il l'exerce là où il est. Vide : plus de métier. */
    private static String setJob(MinecraftServer server, Site site, Person p, String jobName) {
        BuildingFunction f = BuildingFunction.byName(jobName);
        if (f == null || f.dwelling()) {
            p.setJob("");
            p.setRole(Role.AUCUN);
            return null;
        }
        if (p.age() < 16) return "crusadecraft.city.npc.job.child";
        CityBuilding target = null;
        for (CityBuilding b : site.buildings()) {
            if (b.function() == f) {
                target = b;
                break;
            }
        }
        if (target != null && !target.occupants().contains(p.id())) {
            for (CityBuilding b : site.buildings()) b.occupants().remove(p.id());
            target.occupants().add(p.id());
            p.setWork(target.spot());
        }
        p.setJob(f.name());
        p.setRole(f.role());
        Entity e = entity(server, p);
        if (e != null) {
            String post = f.posts().isEmpty() ? "minecraft:none" : f.posts().get(0);
            CityTrades.apply(e, f, post, server.overworld().random);
        }
        return null;
    }
}
