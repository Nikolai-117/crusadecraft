package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.logic.Fr;
import fr.crusadecraft.core.nation.NationFile;
import fr.crusadecraft.core.nation.NationBook;
import fr.crusadecraft.core.nation.NationDef;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CityPanelPackets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Le panneau d'une ville (P46), côté serveur : ce qu'on y lit, ce qu'on y change. Il s'ouvre à la création de la ville et
 * au bâton d'intendant ; tout ce qu'une ville a de réglable y est réuni.
 */
public final class CityPanel {
    /** Un champ de noms ne garde pas plus que ça. */
    static final int NAMES_MAX = 256;
    /** On propose au plus tant de destinations, les plus proches. */
    static final int STOPS_SHOWN = 64;
    /** Une réserve ne garde pas plus de tenues que ça. */
    static final int POOL_MAX = 128;

    private CityPanel() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** Ouvre le panneau, si ce joueur en a le droit. */
    public static boolean open(ServerPlayer player, Site site) {
        if (!CityService.allowed(player, site)) return false;
        CrusadeNetwork.toPlayer(player, new CityPanelPackets.Open(view(player.server, site)));
        return true;
    }

    /** Le panneau de la ville où l'on se tient. */
    public static Outcome openHere(ServerPlayer player) {
        Optional<Site> site = CityService.cityAt(player.server, player.level().dimension().location(), player.blockPosition());
        if (site.isEmpty()) return Outcome.no("crusadecraft.city.none");
        if (!open(player, site.get())) return Outcome.no("crusadecraft.city.denied");
        return Outcome.ok(Component.literal(site.get().name()));
    }

    /** Tout ce que l'écran doit savoir. */
    public static CompoundTag view(MinecraftServer server, Site site) {
        CrusadeSavedData data = data(server);
        LineageState state = data.lineage();
        CompoundTag t = new CompoundTag();
        t.putUUID("site", site.id());
        t.putString("name", site.name());
        t.put("settings", site.settings().save());
        t.putInt("people", CityService.people(server, site));
        t.putInt("max", CityService.max(site));
        t.putInt("cap", CityService.PEOPLE_MAX);
        t.putString("summary", CityLife.summary(site));
        // Son camp.
        Optional<AiRealm> realm = data.world().realm(site.owner());
        t.putString("realm", realm.map(r -> Fr.of(AiNames.realm(r.rank(), r.place())))
                .orElseGet(() -> data.realm(site.owner()).map(fr.crusadecraft.core.realm.Realm::name).orElse("")));
        t.putString("nation", realm.map(AiRealm::nation).orElse(""));
        realm.filter(r -> !r.nation().isEmpty()).ifPresent(r -> t.putUUID("nationRealm", r.id()));
        realm.map(AiRealm::nation).flatMap(NationBook::byKey).ifPresent(d -> t.putString("history", String.join("\n", d.history())));
        ListTag realms = new ListTag();
        for (AiRealm r : data.world().realms()) {
            CompoundTag x = new CompoundTag();
            x.putUUID("id", r.id());
            x.putString("name", Fr.of(AiNames.realm(r.rank(), r.place())));
            x.putInt("color", r.color());
            realms.add(x);
        }
        t.put("realms", realms);
        // Ses bâtiments.
        ListTag buildings = new ListTag();
        for (CityBuilding b : site.buildings()) {
            CompoundTag x = new CompoundTag();
            x.putUUID("id", b.id());
            x.putString("name", b.name());
            x.putInt("function", b.function() == null ? -1 : b.function().ordinal());
            x.putInt("suggestion", b.suggestion() == null ? -1 : b.suggestion().ordinal());
            x.putInt("people", b.occupants().size());
            buildings.add(x);
        }
        t.put("buildings", buildings);
        // Ses gens.
        ListTag people = new ListTag();
        for (CityBuilding b : site.buildings()) {
            for (UUID id : b.occupants()) {
                Person p = state.person(id).filter(Person::alive).orElse(null);
                if (p == null) continue;
                CompoundTag x = new CompoundTag();
                x.putUUID("id", p.id());
                x.putString("name", state.fullName(p));
                x.putString("job", CityTalk.jobTitle(p));
                x.putInt("age", p.age());
                x.putBoolean("female", p.female());
                x.putBoolean("notable", !p.notable().isEmpty());
                x.putBoolean("away", p.trip() != null);
                if (p.hasSettings()) x.putString("look", p.settings().look);
                people.add(x);
            }
        }
        t.put("residents", people);
        // Ses réserves.
        CompoundTag stock = new CompoundTag();
        for (CityGood g : CityGood.values()) stock.putInt(g.name(), site.economy().stock(g));
        t.put("stock", stock);
        t.putLong("purse", site.economy().purse());
        ListTag rumors = new ListTag();
        for (Site.Rumor r : site.rumors()) rumors.add(StringTag.valueOf(r.text()));
        t.put("rumors", rumors);
        ListTag orders = new ListTag();
        for (CityOrders.Order o : site.orders()) orders.add(o.save());
        t.put("orders", orders);
        // P51 : son commerce : les lignes qui la touchent, et où elle peut en ouvrir.
        ListTag lines = new ListTag();
        for (CityLine l : data.world().lines()) {
            if (!l.from.equals(site.id()) && !l.to.equals(site.id())) continue;
            CompoundTag x = l.save();
            x.putBoolean("mine", l.from.equals(site.id()) && !l.ai);
            x.putBoolean("outgoing", l.from.equals(site.id()));
            x.putString("fromName", data.world().site(l.from).map(Site::name).orElse("?"));
            x.putString("toName", data.world().site(l.to).map(Site::name).orElse("?"));
            lines.add(x);
        }
        t.put("lines", lines);
        ListTag stops = new ListTag();
        int shown = 0;
        for (CityCommerce.Stop s : CityCommerce.stops(server, site)) {
            if (shown++ >= STOPS_SHOWN) break;
            CompoundTag x = new CompoundTag();
            x.putUUID("id", s.id());
            x.putString("name", s.name());
            x.putBoolean("ai", s.ai());
            x.putBoolean("war", s.war());
            x.putInt("distance", s.distance());
            stops.add(x);
        }
        t.put("stops", stops);
        return t;
    }

    /** P50 : ce que montre l'onglet « En direct », redemandé toutes les deux secondes tant qu'il est ouvert. */
    public static void sendLive(ServerPlayer player, UUID siteId) {
        Optional<Site> found = data(player.server).world().site(siteId);
        if (found.isEmpty() || !CityService.allowed(player, found.get())) return;
        CrusadeNetwork.toPlayer(player, new CityPanelPackets.Live(live(player.server, found.get(), fr.crusadecraft.core.build.plan.PlanTool.allowed(player))));
    }

    /** Le présent d'une ville : ce qui vient de se passer, ses réserves, ceux qui sont sur les routes. */
    public static CompoundTag live(MinecraftServer server, Site site, boolean canGo) {
        CompoundTag t = new CompoundTag();
        t.putUUID("site", site.id());
        t.putBoolean("canGo", canGo);
        t.put("journal", CityJournal.save(site.id()));
        t.putString("summary", CityLife.summary(site));
        t.putInt("people", CityService.people(server, site));
        ListTag pawns = new ListTag();
        for (CityTravel.Pawn p : CityTravel.pawns(server)) {
            if (!p.home().equals(site.id()) && !p.dest().equals(site.id())) continue;
            CompoundTag x = new CompoundTag();
            x.putUUID("id", p.id());
            x.putString("name", p.name());
            x.putString("from", p.homeName());
            x.putString("to", p.destName());
            x.putString("stage", p.stage().name());
            x.putInt("percent", p.percent());
            x.putBoolean("caravan", p.caravan());
            x.putString("cargo", p.cargo());
            pawns.add(x);
        }
        t.put("pawns", pawns);
        return t;
    }

    /** Un changement venu de l'écran : seules les clés présentes changent. */
    public static Outcome edit(ServerPlayer player, UUID siteId, CompoundTag c) {
        Optional<Site> found = data(player.server).world().site(siteId);
        if (found.isEmpty()) return Outcome.no("crusadecraft.city.unknown");
        Site site = found.get();
        if (!CityService.allowed(player, site)) return Outcome.no("crusadecraft.city.denied");
        Outcome out = apply(player, site, c);
        data(player.server).setDirty();
        // Il a pu céder sa ville à quelqu'un d'autre : il n'y a alors plus rien à lui montrer.
        if (!open(player, site)) CrusadeNetwork.toPlayer(player, new CityPanelPackets.Open(new CompoundTag()));
        return out;
    }

    /** Les changements eux-mêmes. {@code player} ne sert qu'à ce qui se fait au nom de quelqu'un (fonder un royaume pour soi). */
    private static Outcome apply(ServerPlayer player, Site site, CompoundTag c) {
        MinecraftServer server = player.server;
        CitySettings s = site.settings();
        Outcome done = Outcome.ok(Component.translatable("crusadecraft.city.panel.saved"));
        // P50 : aller voir un voyageur, pour qui a la main sur le monde (le créatif, l'éditeur de la carte).
        if (c.hasUUID("goSee")) {
            if (!fr.crusadecraft.core.build.plan.PlanTool.allowed(player)) return Outcome.no("crusadecraft.city.denied");
            if (!CityTravel.goSee(player, c.getUUID("goSee"))) return Outcome.no("crusadecraft.lineage.unknown");
        }
        if (c.contains("rename")) {
            Outcome o = CityAdmin.rename(server, site, c.getString("rename"));
            if (!o.success()) return o;
            done = o;
        }
        if (c.contains("canMarry")) s.canMarry = c.getBoolean("canMarry");
        if (c.contains("canTravel")) s.canTravel = c.getBoolean("canTravel");
        if (c.contains("canLeave")) s.canLeave = c.getBoolean("canLeave");
        if (c.contains("canSpeak")) s.canSpeak = c.getBoolean("canSpeak");
        if (c.contains("peopleMax")) s.peopleMax = Math.max(0, Math.min(CityService.PEOPLE_MAX, c.getInt("peopleMax")));
        if (c.contains("men")) names(s.men, c.getString("men"));
        if (c.contains("women")) names(s.women, c.getString("women"));
        if (c.contains("families")) names(s.families, c.getString("families"));
        // Son camp.
        if (c.hasUUID("join")) {
            done = CityAdmin.join(server, site, c.getUUID("join"));
            if (!done.success()) return done;
        }
        if (c.getBoolean("independent")) {
            ServerLevel level = player.serverLevel();
            done = CityAdmin.independent(server, level, site);
            if (!done.success()) return done;
        }
        if (c.contains("newRealm")) {
            CompoundTag r = c.getCompound("newRealm");
            TitleRank rank = TitleRank.byName(r.getString("rank"), TitleRank.ROYAUME);
            AiPersonality personality;
            try {
                personality = AiPersonality.valueOf(r.getString("personality"));
            } catch (IllegalArgumentException e) {
                personality = AiPersonality.OPPORTUNISTE;
            }
            done = CityAdmin.newRealm(player, site, r.getString("name"), rank, r.getInt("color"), personality, r.getBoolean("mine"));
            if (!done.success()) return done;
        }
        if (c.contains("history")) {
            Optional<NationDef> def = CityPeople.nation(server, site);
            if (def.isEmpty()) return Outcome.no("crusadecraft.city.panel.no_nation");
            if (!NationFile.setHistory(def.get().key(), List.of(c.getString("history").split("\n")))) return Outcome.no("crusadecraft.nation.create.failed");
        }
        // Ses bâtiments.
        if (c.getBoolean("recognize")) done = CityService.recognize(player, site.id());
        if (c.getBoolean("acceptAll")) done = CityService.acceptAll(player, site.id());
        if (c.hasUUID("building")) {
            Optional<CityBuilding> b = site.building(c.getUUID("building"));
            if (b.isEmpty()) return Outcome.no("crusadecraft.city.building.unknown");
            if (c.contains("function")) {
                int i = c.getInt("function");
                CityService.assign(server, site, b.get(), i < 0 ? null : BuildingFunction.byOrdinal(i), player.getRandom().nextLong());
            }
            if (c.contains("buildingName")) b.get().setName(c.getString("buildingName").strip());
            if (c.getBoolean("forget")) done = CityService.forget(player, site.id(), b.get().id());
        }
        // Son commerce.
        if (c.contains("lineAdd")) {
            CompoundTag l = c.getCompound("lineAdd");
            done = CityCommerce.create(server, site, l.getUUID("to"), goodOf(l.getString("good")), l.getInt("amount"), goodOf(l.getString("back")), l.getInt("every"), l.getInt("escort"), l.getBoolean("pave"));
            if (!done.success()) return done;
        }
        if (c.hasUUID("lineToggle") || c.hasUUID("linePave") || c.hasUUID("lineRemove")) {
            UUID id = c.hasUUID("lineToggle") ? c.getUUID("lineToggle") : c.hasUUID("linePave") ? c.getUUID("linePave") : c.getUUID("lineRemove");
            Optional<CityLine> found = CityCommerce.line(server, id).filter(l -> l.from.equals(site.id()));
            if (found.isEmpty()) return Outcome.no("crusadecraft.city.unknown");
            if (c.hasUUID("lineToggle")) found.get().active = !found.get().active;
            if (c.hasUUID("linePave")) found.get().pave = !found.get().pave;
            if (c.hasUUID("lineRemove")) data(server).world().lines().remove(found.get());
        }
        // Son allure.
        if (c.contains("poolAdd")) {
            String look = c.getString("poolAdd"), job = c.getString("poolJob");
            if (look.isEmpty() || look.length() > 200 || poolKey(job).length() > 32) return Outcome.no("crusadecraft.skin.not_found", "");
            if (!SkinService.hashOf(look).isEmpty() && SkinService.entry(SkinService.hashOf(look)).isEmpty()) return Outcome.no("crusadecraft.skin.not_found", look);
            List<String> pool = s.pools.computeIfAbsent(poolKey(job), k -> new ArrayList<>());
            if (!pool.contains(look) && pool.size() < POOL_MAX) pool.add(look);
        }
        if (c.contains("poolRemove")) {
            List<String> pool = s.pools.get(poolKey(c.getString("poolJob")));
            if (pool != null) {
                pool.remove(c.getString("poolRemove"));
                if (pool.isEmpty()) s.pools.remove(poolKey(c.getString("poolJob")));
            }
        }
        if (c.getBoolean("applyPools")) done = Outcome.ok(Component.translatable("crusadecraft.city.panel.dressed", applyPools(server, site, player.getRandom().nextLong())));
        // Ses réserves.
        if (c.contains("good")) {
            try {
                CityGood g = CityGood.valueOf(c.getString("good"));
                int delta = c.getInt("delta");
                if (delta > 0) site.economy().add(g, delta);
                else site.economy().take(g, -delta);
            } catch (IllegalArgumentException e) {
                return Outcome.no("crusadecraft.city.unknown");
            }
        }
        if (c.contains("purse")) site.economy().addPurse(c.getLong("purse"));
        if (c.contains("rumorAdd")) {
            String text = c.getString("rumorAdd").strip();
            if (!text.isEmpty()) CityRumors.addText(server, site, text.length() > 200 ? text.substring(0, 200) : text);
        }
        if (c.contains("rumorRemove")) {
            int i = c.getInt("rumorRemove");
            if (i >= 0 && i < site.rumors().size()) site.rumors().remove(i);
        }
        if (c.contains("orderRemove")) {
            int i = c.getInt("orderRemove");
            if (i >= 0 && i < site.orders().size()) site.orders().remove(i);
        }
        return done;
    }

    private static CityGood goodOf(String name) {
        try {
            return name == null || name.isEmpty() ? null : CityGood.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String poolKey(String job) {
        return job == null || job.isEmpty() ? CitySettings.ALL : job;
    }

    private static void names(List<String> into, String raw) {
        into.clear();
        for (String part : raw.split(",")) {
            String n = part.strip();
            if (!n.isEmpty() && n.length() <= 32 && into.size() < NAMES_MAX) into.add(n);
        }
    }

    /** Tout le monde reçoit une tenue des réserves de la ville, sauf les personnages écrits à la main. Renvoie combien en ont changé. */
    public static int applyPools(MinecraftServer server, Site site, long seed) {
        LineageState state = data(server).lineage();
        Random random = new Random(seed);
        int n = 0;
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, site.dimension()));
        for (CityBuilding b : site.buildings()) {
            for (UUID id : b.occupants()) {
                Person p = state.person(id).filter(Person::alive).orElse(null);
                if (p == null || !p.notable().isEmpty()) continue;
                if (!CityPeople.wardrobe(site, p, random)) continue;
                n++;
                CityNpc.announceLook(server, p);
                Entity e = level == null ? null : level.getEntity(id);
                if (e != null) CityNpc.apply(server, e, p);
            }
        }
        return n;
    }
}
