package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.city.CityAdmin;
import fr.crusadecraft.core.city.CityBuilding;
import fr.crusadecraft.core.city.CityGood;
import fr.crusadecraft.core.city.CityNpc;
import fr.crusadecraft.core.city.CityOrders;
import fr.crusadecraft.core.city.CityPanel;
import fr.crusadecraft.core.city.CityPost;
import fr.crusadecraft.core.city.CityService;
import fr.crusadecraft.core.city.CitySettings;
import fr.crusadecraft.core.city.CityTravel;
import fr.crusadecraft.core.city.CityVoice;
import fr.crusadecraft.core.city.NpcSettings;
import fr.crusadecraft.core.city.SkinService;
import fr.crusadecraft.core.city.Trip;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.LineageGenerator;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Repliques;
import fr.crusadecraft.core.nation.NationBook;
import fr.crusadecraft.core.nation.NationDef;
import fr.crusadecraft.core.nation.NationService;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * P46 à P49 : le panneau d'une ville, l'éditeur d'un habitant, les voyages, les commandes et le courrier. Tout passe par ce
 * que les écrans envoient vraiment : des clés NBT, relues et revérifiées côté serveur.
 */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PanelGameTests {
    private PanelGameTests() {}

    private static NationDef nation(String key, String name) {
        return new NationDef(key, name, name, TitleRank.ROYAUME, 0x6D3A7A, AiPersonality.MARCHAND, AiFaith.FIDELE, false, name, null, List.of(),
                new NationDef.Culture("Romian", "Romianisme", List.of("Elio"), List.of("Bianca"), List.of("Corsi")), List.of("Une histoire."), Map.of(), List.of());
    }

    private static Site city(TestWorld w, MinecraftServer server, ServerLevel level, AiRealm realm, String name, int dx, BuildingFunction... functions) {
        var data = CrusadeSavedData.get(server);
        BlockPos at = w.center.offset(dx, 0, 14);
        var province = WorldEditService.addCity(server, level, realm.id(), name, WorldEditService.CitySize.VILLE, at.getX(), at.getZ()).orElseThrow();
        Site s = new Site(UUID.randomUUID(), SiteKind.VILLAGE, name, realm.id(), level.dimension().location(), at, Direction.SOUTH, realm.color(), true, RealmService.currentDay(server));
        s.setProvince(province.id());
        s.setImported("file:plans|Essai/essai.schem", 64, 20, 64, 1);
        s.setBuilt(true);
        s.setCommunity(new LineageGenerator(7).community(data.lineage(), name, fr.crusadecraft.core.lineage.CommunityKind.VILLAGE, realm.id(), 0).id());
        data.world().add(s);
        int i = 0;
        for (BuildingFunction f : functions) {
            BlockPos a = at.offset(i * 7, 0, 0);
            CityBuilding b = new CityBuilding(UUID.randomUUID(), a, a.offset(4, 3, 4), a.offset(2, 0, 2));
            b.setStats(80, 1, Map.of());
            s.buildings().add(b);
            CityService.define(w.ruler, s.id(), b, f, "");
            i++;
        }
        return s;
    }

    private static CompoundTag tag(java.util.function.Consumer<CompoundTag> fill) {
        CompoundTag t = new CompoundTag();
        fill.accept(t);
        return t;
    }

    private static byte[] skin() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) img.setRGB(x, y, 0xFF000000 | (x * 4) << 16 | (y * 4) << 8);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Ce que fait le panneau : renommer, permettre, habiller, changer de camp. Une ville relue a tout gardé. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p46_panneau", timeoutTicks = 300)
    public static void panneauDUneVille(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "PanneauA");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        WorldEditService.grantForTests(w.ruler.getUUID(), true);
        try {
            data.world().clear();
            NationBook.setForTests(List.of(nation("essaia", "Essaia"), nation("essaib", "Essaib")));
            NationService.ensure(server);
            AiRealm a = NationService.realm(server, "essaia").orElseThrow(), b = NationService.realm(server, "essaib").orElseThrow();
            Site s = city(w, server, level, a, "Lanteval", 0, BuildingFunction.BOULANGERIE, BuildingFunction.MAISON);
            // Ce que l'écran lit : rien ne manque, et la vue tient dans un paquet.
            CompoundTag view = CityPanel.view(server, s);
            w.check(view.getString("name").equals("Lanteval") && view.getList("buildings", 10).size() == 2, "la vue du panneau est incomplète");
            w.check(view.getList("residents", 10).size() == CityService.people(server, s), "le panneau ne montre pas tous ses habitants");
            w.check(view.getString("nation").equals("essaia"), "le panneau ne dit pas sa nation");
            w.check(view.getList("realms", 10).size() >= 2, "le panneau ne propose pas les royaumes");
            // Renommer : la province et la communauté suivent.
            Outcome out = CityPanel.edit(w.ruler, s.id(), tag(t -> t.putString("rename", "Nouvelle Lanteval")));
            w.check(out.success() && s.name().equals("Nouvelle Lanteval"), "renommer ne marche pas : " + out.message().getString());
            w.check(a.provinces().stream().anyMatch(p -> p.id() == s.province() && p.name().equals("Nouvelle Lanteval")), "sa province n'a pas suivi");
            w.check(data.lineage().community(s.community()).orElseThrow().name().equals("Nouvelle Lanteval"), "sa communauté n'a pas suivi");
            w.check(!CityPanel.edit(w.ruler, s.id(), tag(t -> t.putString("rename", ""))).success(), "un nom vide est accepté");
            // Permissions et noms.
            CityPanel.edit(w.ruler, s.id(), tag(t -> {
                t.putBoolean("canMarry", false);
                t.putBoolean("canTravel", false);
                t.putInt("peopleMax", 5000);
                t.putString("men", "Alde, Bruno ,, Cino");
                t.putString("women", "Dora");
                t.putString("families", "Ferri");
            }));
            CitySettings cs = s.settings();
            w.check(!cs.canMarry && !cs.canTravel && cs.canLeave, "les permissions ne sont pas appliquées");
            w.check(cs.peopleMax == CityService.PEOPLE_MAX, "le plafond n'est pas borné : " + cs.peopleMax);
            w.check(cs.men.equals(List.of("Alde", "Bruno", "Cino")), "les prénoms sont mal lus : " + cs.men);
            // Les gens nés ensuite portent ces noms-là.
            CityBuilding more = new CityBuilding(UUID.randomUUID(), s.anchor().offset(21, 0, 0), s.anchor().offset(25, 3, 4), s.anchor().offset(23, 0, 2));
            more.setStats(80, 1, Map.of());
            s.buildings().add(more);
            CityService.define(w.ruler, s.id(), more, BuildingFunction.FORGE, "");
            Person smith = data.lineage().person(more.occupants().get(0)).orElseThrow();
            w.check(List.of("Alde", "Bruno", "Cino", "Dora").contains(smith.name()) && smith.surname().equals("Ferri"), "les noms de la ville ne servent pas : " + smith.name() + " " + smith.surname());
            // Réserves et rumeurs.
            int before = s.economy().stock(CityGood.PAIN);
            CityPanel.edit(w.ruler, s.id(), tag(t -> {
                t.putString("good", "PAIN");
                t.putInt("delta", 8);
                t.putLong("purse", 50);
                t.putString("rumorAdd", "On a vu un dragon.");
            }));
            w.check(s.economy().stock(CityGood.PAIN) == before + 8, "les réserves ne se règlent pas");
            w.check(s.rumors().stream().anyMatch(r -> r.text().equals("On a vu un dragon.")), "une rumeur écrite à la main se perd");
            // Rejoindre un autre royaume : la province, les gens, le propriétaire.
            out = CityPanel.edit(w.ruler, s.id(), tag(t -> t.putUUID("join", b.id())));
            w.check(out.success() && s.owner().equals(b.id()), "la ville n'a pas changé de royaume : " + out.message().getString());
            w.check(b.provinces().stream().anyMatch(p -> p.id() == s.province()) && a.provinces().stream().noneMatch(p -> p.id() == s.province()), "sa province n'a pas suivi");
            w.check(smith.realm().equals(b.id()), "ses habitants n'ont pas suivi");
            w.check(!CityPanel.edit(w.ruler, s.id(), tag(t -> t.putUUID("join", b.id()))).success(), "on rejoint un royaume dont on fait déjà partie");
            // Indépendante : un petit État qui porte son nom, sans toucher aux nations écrites.
            out = CityPanel.edit(w.ruler, s.id(), tag(t -> t.putBoolean("independent", true)));
            w.check(out.success() && !s.owner().equals(b.id()), "la ville n'est pas devenue indépendante");
            AiRealm free = data.world().realm(s.owner()).orElseThrow();
            w.check(free.place().equals("Nouvelle Lanteval") && free.nation().isEmpty() && free.ownProvinces().size() == 1, "l'État indépendant est mal formé");
            w.check(!CityPanel.edit(w.ruler, s.id(), tag(t -> t.putBoolean("independent", true))).success(), "on devient deux fois indépendant");
            w.check(data.world().realm(b.id()).isPresent(), "une nation écrite a disparu");
            // Elle rejoint une nation : l'État qui n'existait que par elle s'efface.
            UUID freeId = free.id();
            CityPanel.edit(w.ruler, s.id(), tag(t -> t.putUUID("join", a.id())));
            w.check(data.world().realm(freeId).isEmpty(), "un État vide est resté sur la carte");
            // Enregistrée puis relue.
            Site reread = Site.load(s.save());
            w.check(!reread.settings().canMarry && reread.settings().men.size() == 3, "les réglages ne s'enregistrent pas");
            // Sans droits, rien ne bouge.
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            w.check(!CityPanel.edit(w.ruler, s.id(), tag(t -> t.putString("rename", "Pirate"))).success() && s.name().equals("Nouvelle Lanteval"), "un inconnu renomme la ville");
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    /** Fonder un royaume depuis le panneau écrit un vrai fichier de nation, relu comme les autres, et la couronne suit. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p46_royaume", timeoutTicks = 300)
    public static void fonderUnRoyaume(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "PanneauB");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        WorldEditService.grantForTests(w.ruler.getUUID(), true);
        java.nio.file.Path file = NationBook.folder().resolve("royaume_d_essai_neuf.json");
        try {
            data.world().clear();
            NationBook.setForTests(List.of(nation("essaia", "Essaia")));
            NationService.ensure(server);
            AiRealm a = NationService.realm(server, "essaia").orElseThrow();
            Site s = city(w, server, level, a, "Fondeval", 0, BuildingFunction.MAISON);
            NationBook.setForTests(null);
            Files.deleteIfExists(file);
            Outcome out = CityAdmin.newRealm(w.ruler, s, "Royaume d'essai neuf", TitleRank.DUCHE, 0x336699, AiPersonality.PACIFIQUE, true);
            w.check(out.success(), "le royaume n'est pas fondé : " + out.message().getString());
            w.check(Files.exists(file), "son fichier n'est pas écrit");
            AiRealm made = NationService.realm(server, "royaume_d_essai_neuf").orElseThrow();
            w.check(s.owner().equals(made.id()), "la ville n'est pas passée au royaume neuf");
            w.check(made.rank() == TitleRank.DUCHE && made.color() == 0x336699, "son rang ou sa couleur se perd");
            w.check(w.ruler.getUUID().equals(made.sovereign()), "son fondateur n'en a pas la couronne");
            w.check(fr.crusadecraft.core.nation.NationFile.setHistory("royaume_d_essai_neuf", List.of("Un début.", "", "Une suite.")), "l'histoire ne s'écrit pas");
            w.check(NationBook.byKey("royaume_d_essai_neuf").orElseThrow().history().equals(List.of("Un début.", "Une suite.")), "l'histoire ne se relit pas");
            h.succeed();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // Il resterait un fichier dans le dossier d'essai : rien de grave.
            }
            NationBook.setForTests(null);
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    /** L'éditeur d'un habitant : chaque onglet change ce qu'il dit, et le villageois suit. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p47_editeur", timeoutTicks = 300)
    public static void editeurDUnHabitant(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "PanneauC");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        WorldEditService.grantForTests(w.ruler.getUUID(), true);
        try {
            data.world().clear();
            NationBook.setForTests(List.of(nation("essaia", "Essaia")));
            NationService.ensure(server);
            AiRealm a = NationService.realm(server, "essaia").orElseThrow();
            Site s = city(w, server, level, a, "Editeval", 0, BuildingFunction.BOULANGERIE, BuildingFunction.FORGE);
            CityBuilding bakery = s.buildings().get(0), forge = s.buildings().get(1);
            Person p = data.lineage().person(bakery.occupants().get(0)).orElseThrow();
            CityService.populate(level, s);
            Entity e = CityService.spawnedForTests(p.id());
            w.check(e != null, "l'habitant n'est pas apparu");
            // La fiche.
            Outcome out = CityNpc.edit(w.ruler, p.id(), tag(t -> {
                t.putString("name", "Ilaria");
                t.putString("surname", "Bressan");
                t.putString("title", "la Boulangère");
                t.putBoolean("female", true);
                t.putInt("age", 41);
                t.putString("bio", "Levée avant tout le monde.");
                ListTag traits = new ListTag();
                traits.add(IntTag.valueOf(Trait.DILIGENT.ordinal()));
                t.put("traits", traits);
            }));
            w.check(out.success(), "la fiche ne s'enregistre pas : " + out.message().getString());
            w.check(p.name().equals("Ilaria") && p.surname().equals("Bressan") && p.female() && p.age() == 41, "la fiche n'est pas appliquée");
            w.check(p.bio().startsWith("Levée") && p.traits().equals(List.of(Trait.DILIGENT)), "sa vie ou ses traits ne suivent pas");
            w.check(p.settings().title.equals("la Boulangère"), "son titre se perd");
            w.check(!CityNpc.edit(w.ruler, p.id(), tag(t -> t.putString("name", ""))).success() && p.name().equals("Ilaria"), "un nom vide est accepté");
            // Les statistiques passent au villageois, et s'en vont à la demande.
            CityNpc.edit(w.ruler, p.id(), tag(t -> {
                t.putDouble("maxHealth", 44);
                t.putBoolean("invincible", true);
                t.putBoolean("showName", false);
            }));
            w.check(e instanceof LivingEntity living && living.getMaxHealth() == 44f, "ses points de vie ne suivent pas");
            w.check(e.isInvulnerable(), "il n'est pas invincible");
            w.check(!e.isCustomNameVisible(), "son nom reste affiché");
            // La conduite.
            CityNpc.edit(w.ruler, p.id(), tag(t -> {
                t.putString("behavior", "STATIONARY");
                t.putInt("workStart", 2000);
                t.putInt("workEnd", 8000);
                t.putBoolean("canTravel", false);
                t.putInt("chatty", 9);
            }));
            NpcSettings ns = p.settings();
            w.check(ns.behavior == NpcSettings.Behavior.STATIONARY && ns.workStart == 2000 && !ns.canTravel && ns.chatty == 2, "sa conduite n'est pas appliquée");
            w.check(!CityNpc.edit(w.ruler, p.id(), tag(t -> t.putString("behavior", "VOLER"))).success(), "une conduite inconnue est acceptée");
            // Ses répliques passent avant les autres, et leurs trous se remplissent.
            CityNpc.edit(w.ruler, p.id(), tag(t -> {
                t.putString("moment", "ACCUEIL");
                t.putString("addLine", "Rien n'attend, à {ville}.");
            }));
            boolean heard = false;
            Random random = new Random(1);
            for (int i = 0; i < 40 && !heard; i++) heard = CityVoice.text(server, s, p, Repliques.Moment.ACCUEIL, null, random).orElse("").equals("Rien n'attend, à Editeval.");
            w.check(heard, "ce qu'on a écrit pour lui, il ne le dit pas");
            CityNpc.edit(w.ruler, p.id(), tag(t -> {
                t.putString("moment", "ACCUEIL");
                t.putInt("removeLine", 0);
            }));
            w.check(!ns.lines.containsKey("ACCUEIL"), "une réplique ne se retire pas");
            // Ses échanges sur mesure.
            CompoundTag offer = new CompoundTag();
            offer.put("a", new ItemStack(Items.EMERALD, 3).save(new CompoundTag()));
            offer.put("b", ItemStack.EMPTY.save(new CompoundTag()));
            offer.put("r", new ItemStack(Items.DIAMOND, 1).save(new CompoundTag()));
            offer.putInt("uses", 5);
            int had = e instanceof Villager v0 ? v0.getOffers().size() : 0;
            out = CityNpc.edit(w.ruler, p.id(), tag(t -> t.put("addOffer", offer)));
            w.check(out.success() && ns.offers != null, "un échange ne s'ajoute pas");
            MerchantOffers offers = new MerchantOffers(ns.offers);
            w.check(offers.size() == had + 1 && offers.get(had).getResult().is(Items.DIAMOND) && offers.get(had).getMaxUses() == 5, "l'échange ajouté n'est pas le bon");
            w.check(e instanceof Villager v && v.getOffers().size() == had + 1, "le villageois ne le propose pas");
            CompoundTag bad = new CompoundTag();
            bad.put("a", ItemStack.EMPTY.save(new CompoundTag()));
            bad.put("b", ItemStack.EMPTY.save(new CompoundTag()));
            bad.put("r", new ItemStack(Items.DIAMOND).save(new CompoundTag()));
            w.check(!CityNpc.edit(w.ruler, p.id(), tag(t -> t.put("addOffer", bad))).success(), "un échange gratuit est accepté");
            CityNpc.edit(w.ruler, p.id(), tag(t -> t.putInt("removeOffer", had)));
            w.check(new MerchantOffers(ns.offers).size() == had, "un échange ne se retire pas");
            // Changer de métier : il rejoint le bâtiment voulu.
            out = CityNpc.edit(w.ruler, p.id(), tag(t -> t.putString("job", "FORGE")));
            w.check(out.success() && p.job().equals("FORGE") && forge.occupants().contains(p.id()) && !bakery.occupants().contains(p.id()), "son métier ne change pas");
            // Un skin qu'on n'a pas est refusé ; un qu'on a est accepté.
            w.check(!CityNpc.edit(w.ruler, p.id(), tag(t -> t.putString("look", SkinService.PREFIX + "inconnu"))).success(), "un skin inconnu est accepté");
            Outcome stored = SkinService.store("Essai", "", skin());
            w.check(stored.success(), "le skin d'essai n'entre pas : " + stored.message().getString());
            w.check(!SkinService.store("Pas un skin", "", new byte[128]).success(), "n'importe quoi entre dans la bibliothèque");
            String hash = SkinService.entries().get(SkinService.entries().size() - 1).hash();
            out = CityNpc.edit(w.ruler, p.id(), tag(t -> t.putString("look", SkinService.PREFIX + hash)));
            w.check(out.success() && SkinService.hashOf(ns.look).equals(hash), "un skin importé ne s'applique pas");
            // Enregistré puis relu.
            Person reread = Person.load(p.save());
            w.check(reread.hasSettings() && reread.settings().behavior == NpcSettings.Behavior.STATIONARY && reread.settings().look.equals(ns.look), "ses réglages ne s'enregistrent pas");
            // Sans droits, rien ne bouge.
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            w.check(!CityNpc.edit(w.ruler, p.id(), tag(t -> t.putString("name", "Pirate"))).success() && p.name().equals("Ilaria"), "un inconnu modifie un habitant");
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    /** Les tenues d'une ville : ceux qui arrivent en reçoivent une, et « habiller tout le monde » épargne les personnages écrits. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p47_tenues", timeoutTicks = 300)
    public static void tenuesDUneVille(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "PanneauD");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        WorldEditService.grantForTests(w.ruler.getUUID(), true);
        try {
            data.world().clear();
            NationBook.setForTests(List.of(nation("essaia", "Essaia")));
            NationService.ensure(server);
            AiRealm a = NationService.realm(server, "essaia").orElseThrow();
            Site s = city(w, server, level, a, "Tenueval", 0, BuildingFunction.MARCHE, BuildingFunction.MAISON);
            String him = "mca:skins/clothing/normal/male/none/1.png", her = "mca:skins/clothing/normal/female/none/1.png";
            CityPanel.edit(w.ruler, s.id(), tag(t -> t.putString("poolAdd", him)));
            CityPanel.edit(w.ruler, s.id(), tag(t -> t.putString("poolAdd", her)));
            w.check(s.settings().poolFor("").size() == 2, "les tenues ne s'ajoutent pas");
            w.check(!CityPanel.edit(w.ruler, s.id(), tag(t -> t.putString("poolAdd", SkinService.PREFIX + "inconnu"))).success(), "un skin inconnu entre dans les tenues");
            List<Person> people = new ArrayList<>();
            for (CityBuilding b : s.buildings()) for (UUID id : b.occupants()) people.add(data.lineage().person(id).orElseThrow());
            Person star = people.get(0);
            star.setNotable("essaia/tenueval/marche/star");
            String own = "mca:skins/clothing/normal/male/guard/2.png";
            star.settings().look = own;
            int dressed = CityPanel.applyPools(server, s, 5);
            w.check(dressed == people.size() - 1, "tout le monde n'a pas été habillé : " + dressed + " sur " + people.size());
            w.check(star.settings().look.equals(own), "un personnage écrit a changé de tenue");
            for (Person p : people) {
                if (p == star) continue;
                w.check(p.settings().look.equals(p.female() ? her : him), p.name() + " porte une tenue de l'autre sexe : " + p.settings().look);
            }
            // Par métier : le métier passe avant toute la ville.
            CityPanel.edit(w.ruler, s.id(), tag(t -> {
                t.putString("poolAdd", own);
                t.putString("poolJob", "MARCHE");
            }));
            w.check(s.settings().poolFor("MARCHE").equals(List.of(own)) && s.settings().poolFor("FORGE").size() == 2, "les tenues d'un métier se mélangent");
            CityPanel.edit(w.ruler, s.id(), tag(t -> {
                t.putString("poolRemove", own);
                t.putString("poolJob", "MARCHE");
            }));
            w.check(s.settings().poolFor("MARCHE").size() == 2, "une tenue ne se retire pas");
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    /** Un marchand part vendre le surplus, arrive, repart et rentre : tout le chemin, sans qu'aucun joueur ne soit là. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p48_voyages", timeoutTicks = 300)
    public static void unMarchandEnVoyage(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "PanneauE");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        WorldEditService.grantForTests(w.ruler.getUUID(), true);
        try {
            data.world().clear();
            NationBook.setForTests(List.of(nation("essaia", "Essaia")));
            NationService.ensure(server);
            AiRealm a = NationService.realm(server, "essaia").orElseThrow();
            Site home = city(w, server, level, a, "Depart", 0, BuildingFunction.MARCHE);
            Site dest = city(w, server, level, a, "Arrivee", 200, BuildingFunction.TAVERNE);
            Person p = data.lineage().person(home.buildings().get(0).occupants().get(0)).orElseThrow();
            // Du surplus de pain ici ; là-bas, du fer en abondance alors qu'il en manque ici.
            home.economy().add(CityGood.PAIN, 100);
            home.economy().take(CityGood.FER, home.economy().stock(CityGood.FER));
            dest.economy().add(CityGood.FER, 100);
            long purseHome = home.economy().purse(), purseDest = dest.economy().purse();
            int stockHome = home.economy().stock(CityGood.PAIN), stockDest = dest.economy().stock(CityGood.PAIN);
            int ironDest = dest.economy().stock(CityGood.FER);
            // Un ami l'apprend ; le voyageur est compté comme parti.
            p.addOpinion(w.ruler.getUUID(), 60);
            CityTravel.depart(server, home, dest, p, true);
            Trip t = p.trip();
            w.check(t != null && t.stage() == Trip.Stage.OUT && t.good() == CityGood.PAIN && t.amount() > 0, "il n'emporte pas le surplus");
            w.check(home.economy().stock(CityGood.PAIN) == stockHome - t.amount(), "sa cargaison n'a pas quitté les réserves");
            w.check(home.rumors().stream().anyMatch(r -> r.text().contains("Arrivee")), "personne ne parle de son départ");
            w.check(data.world().post(w.ruler.getUUID()).stream().anyMatch(l -> l.kind() == CityPost.Kind.VOYAGE && l.text().contains("Arrivee")), "son ami n'a pas reçu de lettre");
            CityService.populate(level, home);
            w.check(!p.inWorld(), "il reparaît chez lui alors qu'il est parti");
            // Il arrive : il vend, et achète ce qui manque chez lui, aux prix de là-bas.
            int sell = fr.crusadecraft.core.city.CityCommerce.price(CityGood.PAIN, dest.economy(), true), buy = fr.crusadecraft.core.city.CityCommerce.price(CityGood.FER, dest.economy(), true);
            long now = level.getGameTime();
            CityTravel.advanceForTests(server, p, now);
            t = p.trip();
            w.check(t != null && t.stage() == Trip.Stage.STAY, "il n'arrive pas : " + (t == null ? "rien" : t.stage()));
            w.check(dest.economy().stock(CityGood.PAIN) == stockDest + t.amount(), "la ville visitée n'a pas reçu la marchandise");
            w.check(t.back() == CityGood.FER && t.backAmount() > 0, "il ne rapporte pas ce qui manque chez lui");
            w.check(dest.economy().stock(CityGood.FER) == ironDest - t.backAmount(), "ce qu'il rapporte n'a pas quitté la ville visitée");
            w.check(t.earned() >= 0 && dest.economy().purse() == purseDest - (long) t.amount() * sell + (long) t.backAmount() * buy, "les comptes de la vente ne tombent pas juste : " + dest.economy().purse());
            w.check(dest.rumors().stream().anyMatch(r -> r.text().contains("Depart")), "personne n'en parle là-bas");
            w.check(Trip.load(t.save()).equals(t), "un voyage ne s'enregistre pas");
            w.check(Person.load(p.save()).trip() != null, "un voyageur enregistré oublie son voyage");
            // Il repart, et il rentre.
            CityTravel.advanceForTests(server, p, now);
            t = p.trip();
            w.check(t != null && t.stage() == Trip.Stage.BACK, "il ne repart pas");
            int earned = t.earned(), back = t.backAmount();
            CityTravel.advanceForTests(server, p, now);
            w.check(p.trip() == null, "il ne rentre pas");
            w.check(home.economy().purse() == purseHome + earned, "il ne rapporte pas ses gains : " + home.economy().purse());
            w.check(home.economy().stock(CityGood.FER) == back, "il ne rapporte pas sa marchandise");
            w.check(home.rumors().stream().anyMatch(r -> r.text().contains("Arrivee") && r.text().contains(p.name())), "personne ne parle de son retour");
            // Personne ne voyage vers une ville en guerre avec la sienne, ni d'une ville qui l'interdit, ni s'il est réglé pour rester.
            home.settings().canTravel = false;
            home.economy().add(CityGood.PAIN, 100);
            for (int day = 0; day < 40; day++) fr.crusadecraft.core.city.CityLife.day(server, home, new Random(day));
            w.check(p.trip() == null, "une ville qui interdit les voyages laisse partir");
            home.settings().canTravel = true;
            p.settings().canTravel = false;
            for (int day = 0; day < 40; day++) fr.crusadecraft.core.city.CityLife.day(server, home, new Random(day));
            w.check(p.trip() == null, "celui qui est réglé pour rester est parti");
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    /** Une ville commande ce qui lui manque, le dit par lettre, et paie celui qui livre. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p49_commandes", timeoutTicks = 300)
    public static void commandesEtCourrier(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "PanneauF");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        WorldEditService.grantForTests(w.ruler.getUUID(), true);
        try {
            data.world().clear();
            NationBook.setForTests(List.of(nation("essaia", "Essaia")));
            NationService.ensure(server);
            AiRealm a = NationService.realm(server, "essaia").orElseThrow();
            Site s = city(w, server, level, a, "Commandeval", 0, BuildingFunction.MARCHE, BuildingFunction.MAISON);
            Person p = data.lineage().person(s.buildings().get(0).occupants().get(0)).orElseThrow();
            p.addOpinion(w.ruler.getUUID(), 40);
            s.economy().take(CityGood.BOIS, s.economy().stock(CityGood.BOIS));
            s.economy().addPurse(1000);
            // Une commande finit par tomber, et une lettre part.
            for (int day = 0; day < 60 && s.orders().stream().noneMatch(o -> o.good() == CityGood.BOIS); day++) {
                s.economy().take(CityGood.BOIS, s.economy().stock(CityGood.BOIS));
                fr.crusadecraft.core.city.CityLife.day(server, s, new Random(day));
            }
            CityOrders.Order order = s.orders().stream().filter(o -> o.good() == CityGood.BOIS).findFirst().orElse(null);
            w.check(order != null, "la ville ne commande pas ce qui lui manque");
            w.check(data.world().post(w.ruler.getUUID()).stream().anyMatch(l -> l.kind() == CityPost.Kind.COMMANDE), "la lettre de commande n'est pas partie");
            for (CityPost.Letter l : data.world().post(w.ruler.getUUID())) w.check(!l.text().contains("{"), "une lettre garde un trou : " + l.text());
            // Sans la marchandise, rien.
            w.ruler.getInventory().clearContent();
            w.check(CityOrders.deliverable(w.ruler, s).isEmpty(), "on peut livrer sans rien avoir");
            w.check(!CityOrders.deliver(w.ruler, s, p).success(), "une livraison à vide passe");
            // Avec : n'importe quel bois convient.
            s.orders().clear();
            s.orders().add(order);
            w.ruler.getInventory().add(new ItemStack(Items.OAK_PLANKS, order.count() - 1));
            w.check(CityOrders.deliverable(w.ruler, s).isEmpty(), "il manque un morceau, et ça passe");
            w.ruler.getInventory().add(new ItemStack(Items.SPRUCE_LOG, 1));
            int stock = s.economy().stock(CityGood.BOIS);
            long purse = s.economy().purse();
            int opinion = p.opinionOf(w.ruler.getUUID());
            Outcome out = CityOrders.deliver(w.ruler, s, p);
            w.check(out.success(), "la livraison ne passe pas : " + out.message().getString());
            w.check(s.economy().stock(CityGood.BOIS) == Math.min(stock + order.count(), s.economy().stock(CityGood.BOIS)) && s.economy().stock(CityGood.BOIS) > stock, "le bois n'arrive pas aux réserves");
            w.check(s.economy().purse() == purse - order.reward(), "la ville n'a pas payé");
            int emeralds = 0;
            for (int i = 0; i < w.ruler.getInventory().getContainerSize(); i++) {
                ItemStack st = w.ruler.getInventory().getItem(i);
                if (st.is(Items.EMERALD)) emeralds += st.getCount();
                w.check(!st.is(Items.OAK_PLANKS) && !st.is(Items.SPRUCE_LOG), "le bois livré est resté dans son sac");
            }
            w.check(emeralds == order.reward(), "il n'a pas reçu ses émeraudes : " + emeralds);
            w.check(s.orders().isEmpty(), "la commande reste ouverte");
            w.check(p.opinionOf(w.ruler.getUUID()) > opinion, "on ne lui en sait pas gré");
            w.check(data.world().post(w.ruler.getUUID()).stream().anyMatch(l -> l.kind() == CityPost.Kind.MERCI), "personne ne le remercie");
            // Chaque sorte de lettre a de quoi s'écrire, et aucune, une fois remplie, ne garde un trou.
            Map<String, String> holes = Map.of("commande", "du blé (24)", "recompense", "3 émeraudes", "destination", "Ailleurs", "autre", "Bianca", "marchandise", "du pain", "prix", "une pièce");
            for (CityPost.Kind kind : CityPost.Kind.values()) {
                w.check(CityVoice.letter(server, s, p, kindMoment(kind), w.ruler.getUUID(), "Romain", holes, new Random(1)).isPresent(), "aucun texte pour la lettre " + kind);
            }
            int letters = 0;
            for (Repliques.Entry e : Repliques.entries()) {
                String kindName = e.moment().name();
                if (!kindName.startsWith("MESSAGE_") && !kindName.startsWith("ECHANGE_") && !kindName.startsWith("REMISE_") && !kindName.startsWith("GROS_")
                        && !kindName.startsWith("CAUSETTE") && e.moment() != Repliques.Moment.VOYAGE) continue;
                for (net.minecraft.network.chat.Component line : e.lines()) {
                    for (boolean female : new boolean[]{false, true}) {
                        p.setFemale(female);
                        String raw = fr.crusadecraft.core.nation.NationRules.fill(fr.crusadecraft.core.logic.Fr.of(line), holes);
                        String text = CityVoice.fill(server, s, p, raw, w.ruler, new Random(letters));
                        w.check(!text.contains("{") && !text.contains("}") && !text.contains("|"), "un texte garde un trou : " + text);
                        letters++;
                    }
                }
            }
            w.check(letters >= 2 * 250, "peu de textes chargés : " + letters / 2);
            for (Repliques.Moment m : List.of(Repliques.Moment.ECHANGE_DEMANDE, Repliques.Moment.ECHANGE_REPONSE, Repliques.Moment.REMISE_LIVRE, Repliques.Moment.REMISE_RECOIT,
                    Repliques.Moment.GROS_VEND, Repliques.Moment.GROS_ACHETE, Repliques.Moment.CAUSETTE, Repliques.Moment.CAUSETTE_REPONSE)) {
                w.check(CityVoice.line(server, s, p, m, holes, new Random(1)).isPresent(), "aucun texte pour " + m);
            }
            // Une boîte relue n'a rien perdu.
            var reread = fr.crusadecraft.core.world.WorldState.load(data.world().save());
            w.check(reread.post(w.ruler.getUUID()).size() == data.world().post(w.ruler.getUUID()).size(), "le courrier ne s'enregistre pas");
            // Une commande non livrée finit par tomber.
            s.orders().clear();
            s.orders().add(new CityOrders.Order(UUID.randomUUID(), CityGood.CUIR, 8, 2, RealmService.currentDay(server) - 100));
            s.economy().add(CityGood.CUIR, 100);
            fr.crusadecraft.core.city.CityLife.day(server, s, new Random(9));
            w.check(s.orders().stream().noneMatch(o -> o.good() == CityGood.CUIR), "une vieille commande reste ouverte");
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    /** Deux habitants face à face : ce qui se voit est ce qui se compte, à l'instant où l'objet change de mains. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p50_echange", timeoutTicks = 500)
    public static void unVraiEchange(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "PanneauG");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        WorldEditService.grantForTests(w.ruler.getUUID(), true);
        data.world().clear();
        // L'essai dure plusieurs ticks : un monde vidé mais pas marqué « généré » se regénérerait sous nos pieds.
        data.world().markGenerated(RealmService.currentDay(server));
        NationBook.setForTests(List.of(nation("essaia", "Essaia")));
        NationService.ensure(server);
        AiRealm a = NationService.realm(server, "essaia").orElseThrow();
        Site s = city(w, server, level, a, "Marcheval", 0, BuildingFunction.BOULANGERIE, BuildingFunction.TAVERNE);
        Person baker = data.lineage().person(s.buildings().get(0).occupants().get(0)).orElseThrow();
        Person keeper = data.lineage().person(s.buildings().get(1).occupants().get(0)).orElseThrow();
        // De jour : la nuit, tout le monde dort, et personne ne vend rien.
        level.setDayTime(3000);
        // Ces gens vivent à côté du gabarit : leur terrain doit rester chargé, sinon le monde ne les trouve plus.
        BlockPos at = s.anchor();
        for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++) level.setChunkForced((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, true);
        Runnable cleanup = () -> {
            for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++) level.setChunkForced((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, false);
            NationBook.setForTests(null);
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            for (UUID id : List.of(baker.id(), keeper.id())) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
            fr.crusadecraft.core.city.CityScenes.onServerStopped();
            data.world().clear();
            w.cleanup();
        };
        try {
            CityService.populate(level, s);
            // Hors du gabarit, le sol du monde d'essai est plus haut que la ville : ils y étoufferaient. On les pose dessus.
            for (UUID id : List.of(baker.id(), keeper.id())) {
                Entity e = CityService.spawnedForTests(id);
                if (e != null) e.teleportTo(e.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getBlockX(), e.getBlockZ()), e.getZ());
            }
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }
        Entity[] pair = new Entity[2];
        boolean[] cleaned = {false};
        Runnable once = () -> {
            if (cleaned[0]) return;
            cleaned[0] = true;
            cleanup.run();
        };
        // Un villageois tout juste posé n'est trouvable qu'un peu plus tard.
        h.runAfterDelay(30, () -> {
            try {
                Entity eb = level.getEntity(baker.id()), ek = level.getEntity(keeper.id());
                w.check(eb instanceof net.minecraft.world.entity.PathfinderMob && ek instanceof net.minecraft.world.entity.PathfinderMob, "les deux habitants ne sont pas là");
                pair[0] = eb;
                pair[1] = ek;
                var buyer = (net.minecraft.world.entity.PathfinderMob) ek;
                buyer.teleportTo(eb.getX() + 2, eb.getY(), eb.getZ());
                keeper.setCoins(5);
                baker.setCoins(0);
                keeper.setHunger(80);
                s.economy().take(CityGood.PAIN, s.economy().stock(CityGood.PAIN));
                w.check(!fr.crusadecraft.core.city.CityScenes.sale(server, level, s, keeper, buyer, CityGood.PAIN, true), "on vend ce qu'on n'a plus");
                s.economy().add(CityGood.PAIN, 10);
                w.check(fr.crusadecraft.core.city.CityScenes.sale(server, level, s, keeper, buyer, CityGood.PAIN, true), "personne ne vend le pain");
                w.check(fr.crusadecraft.core.city.CityScenes.busy(baker.id()) && fr.crusadecraft.core.city.CityScenes.busy(keeper.id()), "ils ne sont pas pris par la scène");
                w.check(!fr.crusadecraft.core.city.CityScenes.sale(server, level, s, keeper, buyer, CityGood.PAIN, true), "il commence deux achats à la fois");
                w.check(s.economy().stock(CityGood.PAIN) == 10 && keeper.coins() == 5, "c'est compté avant que l'objet n'ait changé de mains");
            } catch (RuntimeException e) {
                once.run();
                throw e;
            }
        });
        // À mi-chemin : ils se font face, l'objet n'a pas encore changé de mains.
        h.runAfterDelay(70, () -> {
            try {
                w.check(fr.crusadecraft.core.city.CityScenes.busy(baker.id()), "la scène s'arrête trop tôt");
                w.check(s.economy().stock(CityGood.PAIN) == 10, "c'est compté trop tôt");
            } catch (RuntimeException e) {
                once.run();
                throw e;
            }
        });
        h.runAfterDelay(260, () -> {
            try {
                Entity eb = pair[0];
                var buyer = (net.minecraft.world.entity.PathfinderMob) pair[1];
                w.check(!fr.crusadecraft.core.city.CityScenes.busy(baker.id()), "la scène ne finit pas");
                w.check(s.economy().stock(CityGood.PAIN) == 9, "le pain n'a pas quitté les réserves : " + s.economy().stock(CityGood.PAIN));
                w.check(keeper.coins() == 4 && baker.coins() == 1, "la pièce n'a pas changé de mains : " + keeper.coins() + " / " + baker.coins());
                w.check(keeper.hunger() == 30, "il n'a pas mangé : " + keeper.hunger());
                w.check(fr.crusadecraft.core.city.CityJournal.of(s.id()).stream().anyMatch(l -> l.text().contains(keeper.name()) && l.text().contains(baker.name())), "le journal n'en dit rien");
                w.check(buyer.getMainHandItem().isEmpty() && ((net.minecraft.world.entity.PathfinderMob) eb).getMainHandItem().isEmpty(), "ils gardent l'objet en main à la fin");
                // Si le vendeur disparaît en pleine scène, l'acheteur n'attend pas pour rien.
                w.check(fr.crusadecraft.core.city.CityScenes.sale(server, level, s, keeper, buyer, CityGood.PAIN, false), "un second achat ne démarre pas");
                eb.discard();
            } catch (RuntimeException e) {
                once.run();
                throw e;
            }
        });
        h.runAfterDelay(300, () -> {
            try {
                w.check(!fr.crusadecraft.core.city.CityScenes.busy(keeper.id()), "l'acheteur reste pris par une scène sans vendeur");
                h.succeed();
            } finally {
                once.run();
            }
        });
    }

    /** Une caravane : un marchand, son escorte, et tout le monde rentre ; sans traité, on lui paie moins cher. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p50_caravane", timeoutTicks = 300)
    public static void uneCaravaneEtSonEscorte(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "PanneauH");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        WorldEditService.grantForTests(w.ruler.getUUID(), true);
        try {
            data.world().clear();
            NationBook.setForTests(List.of(nation("essaia", "Essaia"), nation("essaib", "Essaib")));
            NationService.ensure(server);
            AiRealm a = NationService.realm(server, "essaia").orElseThrow(), b = NationService.realm(server, "essaib").orElseThrow();
            Site home = city(w, server, level, a, "Convoi", 0, BuildingFunction.MARCHE, BuildingFunction.CASERNE);
            Site dest = city(w, server, level, b, "Ailleurs", 200, BuildingFunction.MARCHE);
            Person p = data.lineage().person(home.buildings().get(0).occupants().get(0)).orElseThrow();
            home.economy().add(CityGood.PAIN, 100);
            long purseDest = dest.economy().purse();
            CityTravel.depart(server, home, dest, p, true);
            Trip t = p.trip();
            w.check(t.good() == CityGood.PAIN && t.leader() == null, "il n'emporte pas le surplus");
            List<Person> guards = new ArrayList<>();
            for (UUID id : home.buildings().get(1).occupants()) {
                Person g = data.lineage().person(id).orElseThrow();
                if (g.trip() != null) guards.add(g);
            }
            w.check(!guards.isEmpty() && guards.size() <= 2, "la caravane part sans escorte : " + guards.size());
            for (Person g : guards) w.check(p.id().equals(g.trip().leader()) && g.trip().end() == t.end(), "un garde ne suit pas le marchand");
            w.check(Person.load(guards.get(0).save()).trip().leader().equals(p.id()), "une escorte oublie qui elle suit");
            // La carte voit une caravane, et une seule : l'escorte marche avec elle.
            var pawns = CityTravel.pawns(server);
            w.check(pawns.size() == 1 && pawns.get(0).caravan() && pawns.get(0).cargo().contains("pain"), "la carte ne montre pas la caravane : " + pawns);
            // Elle arrive : d'un autre royaume, sans traité, on lui paie moitié moins.
            long now = level.getGameTime();
            CityTravel.advanceForTests(server, p, now);
            t = p.trip();
            w.check(t.stage() == Trip.Stage.STAY, "elle n'arrive pas");
            w.check(dest.economy().purse() == purseDest - t.amount(), "sans traité, le prix devrait être d'une pièce : " + dest.economy().purse());
            CityTravel.tick(server);
            for (Person g : guards) w.check(g.trip().stage() == Trip.Stage.STAY, "un garde reste sur la route");
            // Elle rentre, et l'escorte avec elle.
            CityTravel.advanceForTests(server, p, now);
            CityTravel.tick(server);
            for (Person g : guards) w.check(g.trip().stage() == Trip.Stage.BACK, "un garde ne repart pas");
            long purseHome = home.economy().purse();
            int earned = p.trip().earned();
            CityTravel.advanceForTests(server, p, now);
            w.check(p.trip() == null && home.economy().purse() == purseHome + earned, "le marchand ne rentre pas avec ses gains");
            CityTravel.tick(server);
            for (Person g : guards) w.check(g.trip() == null, "un garde reste en voyage");
            // Sans surplus, mais avec un manque : il part avec de quoi acheter, et rend ce qu'il n'a pas dépensé.
            home.economy().take(CityGood.PAIN, home.economy().stock(CityGood.PAIN));
            for (CityGood g : CityGood.values()) {
                if (home.economy().stock(g) > 48) home.economy().take(g, home.economy().stock(g) - 48);
            }
            home.economy().addPurse(1000);
            long before = home.economy().purse();
            CityTravel.depart(server, home, dest, p, true);
            int money = p.trip().earned();
            w.check(money > 0 && home.economy().purse() == before - money, "il part sans bourse pour acheter ce qui manque");
            for (CityGood g : CityGood.values()) if (dest.economy().stock(g) > 48) dest.economy().take(g, dest.economy().stock(g) - 48);
            for (int i = 0; i < 3 && p.trip() != null; i++) CityTravel.advanceForTests(server, p, now);
            w.check(p.trip() == null && home.economy().purse() == before, "ce qu'il n'a pas dépensé se perd : " + home.economy().purse());
            // En guerre, on ne va plus là-bas.
            data.diplomacy().between(a.id(), b.id()).setWar(true);
            w.check(CityTravel.destination(server, home, new Random(1)).isEmpty(), "une caravane part chez l'ennemi");
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    /** Une ligne de commerce, telle que le panneau la crée : elle part, vend, rapporte, et se suspend. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p51_lignes", timeoutTicks = 300)
    public static void lignesDeCommerce(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "CommerceA");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        WorldEditService.grantForTests(w.ruler.getUUID(), true);
        try {
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            NationBook.setForTests(List.of(nation("essaia", "Essaia"), nation("essaib", "Essaib")));
            NationService.ensure(server);
            AiRealm a = NationService.realm(server, "essaia").orElseThrow(), b = NationService.realm(server, "essaib").orElseThrow();
            Site home = city(w, server, level, a, "Lignedep", 0, BuildingFunction.MARCHE, BuildingFunction.CASERNE);
            Site dest = city(w, server, level, a, "Lignearr", 200, BuildingFunction.MARCHE);
            Site foe = city(w, server, level, b, "Lignefoe", 400, BuildingFunction.MARCHE);
            // Ce que propose le panneau.
            var stops = fr.crusadecraft.core.city.CityCommerce.stops(server, home);
            w.check(stops.stream().anyMatch(x -> x.id().equals(dest.id())) && stops.stream().noneMatch(x -> x.id().equals(home.id())), "le panneau ne propose pas les bonnes villes");
            CompoundTag view = CityPanel.view(server, home);
            w.check(view.getList("stops", 10).size() == stops.size() || view.getList("stops", 10).size() == 64, "la vue ne donne pas les destinations");
            // Une ligne : du fer, seize par voyage, un voyage par quart de journée.
            home.economy().add(CityGood.FER, 100);
            dest.economy().add(CityGood.PAIN, 100);
            CompoundTag add = new CompoundTag();
            add.putUUID("to", dest.id());
            add.putString("good", "FER");
            add.putString("back", "PAIN");
            add.putInt("amount", 16);
            add.putInt("every", 1);
            add.putInt("escort", 1);
            add.putBoolean("pave", true);
            Outcome out = CityPanel.edit(w.ruler, home.id(), tag(t -> t.put("lineAdd", add)));
            w.check(out.success(), "la ligne n'est pas créée : " + out.message().getString());
            w.check(data.world().lines().size() == 1, "la ligne n'est pas enregistrée");
            fr.crusadecraft.core.city.CityLine line = data.world().lines().get(0);
            w.check(line.good == CityGood.FER && line.back == CityGood.PAIN && line.amount == 16 && line.pave, "la ligne n'a pas ses réglages");
            w.check(!CityPanel.edit(w.ruler, home.id(), tag(t -> t.put("lineAdd", add))).success(), "on crée deux fois la même ligne");
            CompoundTag self = add.copy();
            self.putUUID("to", home.id());
            w.check(!CityPanel.edit(w.ruler, home.id(), tag(t -> t.put("lineAdd", self))).success(), "une ville commerce avec elle-même");
            w.check(CityPanel.view(server, home).getList("lines", 10).size() == 1, "le panneau ne montre pas la ligne");
            // Elle part : on charge, le marchand prend la route, l'escorte suit.
            int fer = home.economy().stock(CityGood.FER);
            fr.crusadecraft.core.city.CityCommerce.pulse(server);
            Person merchant = null;
            int guards = 0;
            for (Person x : data.lineage().persons()) {
                if (x.trip() == null) continue;
                if (line.id.equals(x.trip().line())) merchant = x;
                if (x.trip().leader() != null) guards++;
            }
            w.check(merchant != null, "la ligne ne fait partir personne");
            w.check(guards == 1, "l'escorte n'est pas celle demandée : " + guards);
            w.check(merchant.trip().good() == CityGood.FER && merchant.trip().amount() == 16 && home.economy().stock(CityGood.FER) == fer - 16, "la charge n'est pas la bonne");
            // Pas deux caravanes à la fois sur une ligne.
            fr.crusadecraft.core.city.CityCommerce.pulse(server);
            int running = 0;
            for (Person x : data.lineage().persons()) if (x.trip() != null && line.id.equals(x.trip().line())) running++;
            w.check(running == 1, "deux caravanes sur la même ligne");
            // Elle arrive sans qu'aucun joueur ne soit là : elle vend, au prix de là-bas, et prend ce qu'on a demandé.
            long purse = dest.economy().purse();
            int unit = fr.crusadecraft.core.city.CityCommerce.price(CityGood.FER, dest.economy(), true);
            long now = level.getGameTime();
            CityTravel.advanceForTests(server, merchant, now);
            Trip t = merchant.trip();
            w.check(t.stage() == Trip.Stage.STAY, "elle n'arrive pas");
            w.check(t.back() == CityGood.PAIN && t.backAmount() > 0, "elle ne rapporte pas ce qu'on a demandé");
            int unitBack = fr.crusadecraft.core.city.CityCommerce.price(CityGood.PAIN, dest.economy(), true);
            w.check(dest.economy().purse() == purse - 16L * unit + (long) t.backAmount() * unitBack, "les comptes ne tombent pas juste : " + dest.economy().purse());
            w.check(line.trips == 1 && line.moved == 16 && line.earned == 16L * unit, "la ligne ne tient pas ses comptes");
            // Elle rentre.
            CityTravel.advanceForTests(server, merchant, now);
            CityTravel.advanceForTests(server, merchant, now);
            w.check(merchant.trip() == null, "elle ne rentre pas");
            // Suspendue, elle ne part plus.
            CityPanel.edit(w.ruler, home.id(), tag(x -> x.putUUID("lineToggle", line.id)));
            w.check(!line.active, "on ne suspend pas une ligne");
            line.waited = 99;
            fr.crusadecraft.core.city.CityCommerce.pulse(server);
            w.check(merchant.trip() == null, "une ligne suspendue fait partir quelqu'un");
            // En guerre, plus de ligne.
            data.diplomacy().between(a.id(), b.id()).setWar(true);
            CompoundTag atWar = add.copy();
            atWar.putUUID("to", foe.id());
            w.check(!CityPanel.edit(w.ruler, home.id(), tag(x -> x.put("lineAdd", atWar))).success(), "une ligne s'ouvre vers l'ennemi");
            data.diplomacy().between(a.id(), b.id()).setWar(false);
            // Enregistrée puis relue.
            var reread = fr.crusadecraft.core.world.WorldState.load(data.world().save());
            w.check(reread.lines().size() == 1 && reread.lines().get(0).trips == 1, "les lignes ne s'enregistrent pas");
            // Retirée.
            CityPanel.edit(w.ruler, home.id(), tag(x -> x.putUUID("lineRemove", line.id)));
            w.check(data.world().lines().isEmpty(), "une ligne ne se retire pas");
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    /** Une ville de l'ordinateur qui n'existe que sur la carte commerce quand même : un marché, ses marchands, ses caravanes. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p51_marches", timeoutTicks = 300)
    public static void villesDeLOrdinateur(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "CommerceB");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        WorldEditService.grantForTests(w.ruler.getUUID(), true);
        try {
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            AiRealm ai = WorldEditService.addRealm(server, level, "Portmarchand", "", TitleRank.COMTE, 0x2E7D32, AiFaith.FIDELE, AiPersonality.MARCHAND, w.center.getX() + 500, w.center.getZ());
            AiRealm ai2 = WorldEditService.addRealm(server, level, "Autreport", "", TitleRank.COMTE, 0x7D2E32, AiFaith.FIDELE, AiPersonality.MARCHAND, w.center.getX() + 800, w.center.getZ());
            var province = ai.capital();
            int sites = data.world().sites().size();
            var stops = fr.crusadecraft.core.city.CityCommerce.stops(server, CityMarkets0(server, ai2));
            w.check(stops.stream().anyMatch(x -> x.ai() && x.name().equals(province.name())), "la ville de la carte n'est pas proposée");
            Site market = fr.crusadecraft.core.city.CityMarkets.of(server, fr.crusadecraft.core.city.CityMarkets.idOf(province.id())).orElseThrow();
            w.check(data.world().isMarket(market.id()), "ce n'est pas un marché");
            w.check(data.world().sites().size() == sites + 0 && data.world().sites().stream().noneMatch(x -> x.id().equals(market.id())), "un marché compte parmi les constructions du monde");
            w.check(data.world().site(market.id()).isPresent(), "on ne retrouve pas le marché");
            w.check(market.owner().equals(ai.id()) && market.name().equals(province.name()), "le marché n'est pas celui de cette ville");
            int people = CityService.people(server, market);
            w.check(people >= 3, "le marché n'a personne pour le tenir : " + people);
            // Toujours le même.
            w.check(fr.crusadecraft.core.city.CityMarkets.of(server, market.id()).orElseThrow() == market, "on crée deux marchés pour une ville");
            // Sa journée : il vit de sa terre.
            int before = market.economy().stock(CityGood.PAIN);
            long purse = market.economy().purse();
            fr.crusadecraft.core.city.CityMarkets.onDay(server, new Random(1));
            w.check(market.economy().purse() > purse, "un marché ne gagne rien");
            // Une ligne vers lui, et sa caravane en retour.
            Site other = CityMarkets0(server, ai2);
            other.economy().add(CityGood.OUTILS, 100);
            Outcome made = fr.crusadecraft.core.city.CityCommerce.create(server, other, market.id(), CityGood.OUTILS, 8, null, 1, 1, false);
            w.check(made.success(), "pas de ligne entre deux villes de l'ordinateur : " + made.message().getString());
            fr.crusadecraft.core.city.CityCommerce.pulse(server);
            Person lead = null;
            for (Person x : data.lineage().persons()) if (x.trip() != null && x.trip().line() != null) lead = x;
            w.check(lead != null, "sa caravane ne part pas");
            int outils = market.economy().stock(CityGood.OUTILS);
            long now = level.getGameTime();
            CityTravel.advanceForTests(server, lead, now);
            w.check(market.economy().stock(CityGood.OUTILS) == outils + 8, "le marché ne reçoit pas la marchandise");
            CityTravel.advanceForTests(server, lead, now);
            CityTravel.advanceForTests(server, lead, now);
            w.check(lead.trip() == null, "la caravane ne rentre pas");
            w.check(!lead.inWorld(), "un marchand de marché reste dans le monde en rentrant");
            // L'ordinateur décide tout seul.
            data.world().lines().clear();
            for (int day = 0; day < 20 && data.world().lines().stream().noneMatch(l -> l.ai); day++) fr.crusadecraft.core.city.CityCommerce.planAi(server, new Random(day));
            w.check(data.world().lines().stream().anyMatch(l -> l.ai), "l'ordinateur n'ouvre aucune ligne");
            // Relu après un arrêt.
            var reread = fr.crusadecraft.core.world.WorldState.load(data.world().save());
            w.check(reread.isMarket(market.id()) && reread.site(market.id()).isPresent() && reread.site(market.id()).orElseThrow().buildings().size() == market.buildings().size(), "un marché ne s'enregistre pas");
            // Sa province disparaît : le marché aussi.
            ai.provinces().clear();
            fr.crusadecraft.core.city.CityMarkets.onDay(server, new Random(2));
            w.check(!data.world().isMarket(market.id()), "un marché survit à sa province");
            h.succeed();
        } finally {
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    private static Site CityMarkets0(MinecraftServer server, AiRealm realm) {
        return fr.crusadecraft.core.city.CityMarkets.ensure(server, realm, realm.capital());
    }

    /** Un garde voit un monstre : il court dessus et il frappe. Ça se joue dans le monde, tick après tick. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p53_gardes", timeoutTicks = 400)
    public static void unGardeSeBat(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "GardesA");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        WorldEditService.grantForTests(w.ruler.getUUID(), true);
        data.world().clear();
        data.world().markGenerated(RealmService.currentDay(server));
        NationBook.setForTests(List.of(nation("essaia", "Essaia")));
        NationService.ensure(server);
        AiRealm a = NationService.realm(server, "essaia").orElseThrow();
        Site s = city(w, server, level, a, "Gardeville", 0, BuildingFunction.CASERNE);
        Person guard = data.lineage().person(s.buildings().get(0).occupants().get(0)).orElseThrow();
        BlockPos at = s.anchor();
        for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++) level.setChunkForced((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, true);
        boolean[] cleaned = {false};
        net.minecraft.world.entity.monster.Zombie[] zombie = new net.minecraft.world.entity.monster.Zombie[1];
        BlockPos[] paved = new BlockPos[1];
        Runnable once = () -> {
            if (cleaned[0]) return;
            cleaned[0] = true;
            for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++) level.setChunkForced((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, false);
            if (zombie[0] != null) zombie[0].discard();
            for (UUID id : s.buildings().get(0).occupants()) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
            fr.crusadecraft.core.city.CityGuards.onServerStopped();
            NationBook.setForTests(null);
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        };
        try {
            CityService.populate(level, s);
            for (UUID id : s.buildings().get(0).occupants()) {
                Entity e = CityService.spawnedForTests(id);
                if (e != null) e.teleportTo(e.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getBlockX(), e.getBlockZ()), e.getZ());
            }
        } catch (RuntimeException e) {
            once.run();
            throw e;
        }
        h.runAfterDelay(30, () -> {
            try {
                Entity e = level.getEntity(guard.id());
                w.check(e != null, "le garde n'est pas là");
                zombie[0] = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
                w.check(zombie[0] != null, "pas de monstre");
                zombie[0].moveTo(e.getX() + 4, e.getY(), e.getZ());
                w.check(level.addFreshEntity(zombie[0]), "le monstre n'entre pas dans le monde");
                // Un joueur qui frappe quelqu'un de cette ville : on s'en souvient.
                w.check(!fr.crusadecraft.core.city.CityGuards.hasGrudge(server, w.ruler.getUUID(), s.id()), "on en veut à un innocent");
                fr.crusadecraft.core.city.CityGuards.provoke(server, w.ruler, s.id());
                w.check(fr.crusadecraft.core.city.CityGuards.hasGrudge(server, w.ruler.getUUID(), s.id()), "on oublie celui qui a frappé");
                w.check(!fr.crusadecraft.core.city.CityGuards.hasGrudge(server, w.ruler.getUUID(), new UUID(1, 2)), "toutes les villes lui en veulent");
                w.check(fr.crusadecraft.core.city.CityGuards.isGuard(guard), "un garde n'est pas reconnu");
                // Sous les pas d'une caravane, l'herbe devient un chemin ; la pierre reste de la pierre.
                BlockPos under = e.blockPosition().below();
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.setBlock(under.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                level.setBlock(under.offset(1, 0, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                paved[0] = under;
            } catch (RuntimeException e) {
                once.run();
                throw e;
            }
        });
        h.runAfterDelay(45, () -> {
            try {
                // La ville elle-même a ses rues : on essaie sur un coin qui n'est à personne.
                data.world().removeSite(s.id());
                var mob = (net.minecraft.world.entity.PathfinderMob) level.getEntity(guard.id());
                if (mob != null && mob.onGround()) {
                    BlockPos stoneAt = paved[0].offset(1, 0, 0);
                    mob.teleportTo(paved[0].getX() + 0.5, paved[0].getY() + 1, paved[0].getZ() + 0.5);
                    fr.crusadecraft.core.city.CityRoads.wear(server, level, mob, new UUID(9, 9));
                    w.check(level.getBlockState(paved[0]).is(net.minecraft.world.level.block.Blocks.DIRT_PATH), "l'herbe ne devient pas un chemin : " + level.getBlockState(paved[0]));
                    w.check(level.getBlockState(stoneAt).is(net.minecraft.world.level.block.Blocks.STONE), "la pierre a été creusée");
                }
                data.world().add(s);
            } catch (RuntimeException e) {
                once.run();
                throw e;
            }
        });
        h.runAfterDelay(200, () -> {
            try {
                Entity e = level.getEntity(guard.id());
                w.check(e instanceof net.minecraft.world.entity.LivingEntity l && l.getMaxHealth() >= 40, "le garde n'est pas plus solide qu'un autre");
                w.check(e instanceof net.minecraft.world.entity.LivingEntity l2 && l2.getMainHandItem().is(Items.IRON_SWORD), "le garde n'a pas d'épée");
                w.check(!zombie[0].isAlive() || zombie[0].getHealth() < zombie[0].getMaxHealth(), "le garde n'a pas touché le monstre : " + zombie[0].getHealth() + ", à " + e.distanceTo(zombie[0]) + " blocs, cible " + fr.crusadecraft.core.city.CityGuards.targetOf(guard.id()));
                h.succeed();
            } finally {
                once.run();
            }
        });
    }

    private static Repliques.Moment kindMoment(CityPost.Kind kind) {
        return Repliques.Moment.valueOf("MESSAGE_" + kind.name());
    }
}
