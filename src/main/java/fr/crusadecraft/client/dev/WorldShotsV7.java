package fr.crusadecraft.client.dev;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.client.screen.CityPanelScreen;
import fr.crusadecraft.client.screen.InhabitantScreen;
import fr.crusadecraft.client.screen.NpcEditorScreen;
import fr.crusadecraft.client.screen.PostScreen;
import fr.crusadecraft.client.skin.SkinClient;
import fr.crusadecraft.client.skin.Wardrobe;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.plan.PlanRef;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.city.CityBuilding;
import fr.crusadecraft.core.city.CityGood;
import fr.crusadecraft.core.city.CityLife;
import fr.crusadecraft.core.city.CityNpc;
import fr.crusadecraft.core.city.CityOrders;
import fr.crusadecraft.core.city.CityPanel;
import fr.crusadecraft.core.city.CityPost;
import fr.crusadecraft.core.city.CityService;
import fr.crusadecraft.core.city.CityTalk;
import fr.crusadecraft.core.city.CityTravel;
import fr.crusadecraft.core.city.NpcSettings;
import fr.crusadecraft.core.city.SkinService;
import fr.crusadecraft.core.city.Trip;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * -Pshots_v7=true : le panneau d'une ville, l'éditeur d'un habitant, les skins (par vrais téléchargements), les voyages, le
 * courrier, les commandes, et une ville qu'on fonde. Les boutons sont réellement pressés : tout passe par le réseau.
 */
final class WorldShotsV7 {
    private record Step(String name, Consumer<MinecraftServer> server, Consumer<Minecraft> client, int ticks, String shot, BooleanSupplier until) {
        Step(String name, Consumer<MinecraftServer> server, Consumer<Minecraft> client, int ticks, String shot) {
            this(name, server, client, ticks, shot, null);
        }
    }

    private static final String DOL = new PlanRef(PlanRef.Source.FILE, "plans", "Levenzia/dolhimluith.schem").encode();
    private static final String GRU = new PlanRef(PlanRef.Source.FILE, "plans", "Varenhold/grunbach.schem").encode();

    private static List<Step> steps;
    private static int index = -1, wait, waited;
    private static boolean acted;
    private static UUID home, dest, baker, guard, merchant, stander, walker, roadster;
    private static BlockPos standerAt, walkerAt;
    private static String notchHash = "";
    private static volatile int imports;
    private static UUID founded, caravanner, guardian, monster;
    private static UUID blockaded, contract, market;
    private static int emeralds, province;
    private static fr.crusadecraft.core.ColonyRef colony;
    private static int[] field;
    private static final int PLATFORM = 180;
    private static BlockPos origin;
    private static volatile boolean pasted;
    private static final BuildingFunction[] MIX = {BuildingFunction.MARCHE, BuildingFunction.BOULANGERIE, BuildingFunction.TAVERNE, BuildingFunction.FORGE,
            BuildingFunction.MAIRIE, BuildingFunction.TEMPLE, BuildingFunction.FERME, BuildingFunction.CASERNE};

    private WorldShotsV7() {}

    private static void log(String text, Object... args) {
        CrusadeCraft.LOGGER.info("WorldShots v7 : " + text, args);
    }

    static boolean tick(Minecraft mc, BiConsumer<Minecraft, String> grab) {
        if (steps == null) {
            steps = build();
            if (Boolean.getBoolean("crusadecraft.worldshots.v7civ")) {
                // P54 à P58 : les villes, ce qu'il faut de commerce et d'ordinateur, puis la suite.
                int depart = 0, founding = 0, lines = 0, eco = 0, end = steps.size();
                for (int i = 0; i < steps.size(); i++) {
                    String n = steps.get(i).name();
                    if (n.equals("depart")) depart = i + 1;
                    if (n.equals("fonder_ville")) founding = i;
                    if (n.equals("carte_lignes")) lines = i + 1;
                    if (n.equals("eco_ouvre")) eco = i;
                }
                List<Step> short_ = new ArrayList<>(steps.subList(0, depart));
                short_.addAll(steps.subList(founding, lines));
                short_.addAll(steps.subList(eco, end));
                steps = short_;
            } else if (Boolean.getBoolean("crusadecraft.worldshots.v7gardes")) {
                int from = 0, to = 0;
                for (int i = 0; i < steps.size(); i++) {
                    if (steps.get(i).name().equals("depart")) to = i + 1;
                    if (steps.get(i).name().equals("garde_prepare")) from = i;
                }
                List<Step> short_ = new ArrayList<>(steps.subList(0, to));
                short_.addAll(steps.subList(from, steps.size()));
                steps = short_;
            }
        }
        if (wait > 0) {
            wait--;
            return false;
        }
        if (index >= 0 && acted) {
            Step done = steps.get(index);
            if (done.until() != null && !done.until().getAsBoolean() && waited++ < 12_000) return false;
            acted = false;
            if (done.shot() != null) {
                grab.accept(mc, done.shot());
                wait = 6;
                return false;
            }
        }
        index++;
        waited = 0;
        if (index >= steps.size()) return true;
        Step s = steps.get(index);
        MinecraftServer server = mc.getSingleplayerServer();
        log("étape {}", s.name());
        if (s.server() != null) server.execute(() -> {
            try {
                s.server().accept(server);
            } catch (RuntimeException e) {
                CrusadeCraft.LOGGER.error("WorldShots v7 : l'étape {} a échoué côté serveur", s.name(), e);
            }
        });
        if (s.client() != null) {
            try {
                s.client().accept(mc);
            } catch (RuntimeException e) {
                CrusadeCraft.LOGGER.error("WorldShots v7 : l'étape {} a échoué côté client", s.name(), e);
            }
        }
        wait = s.ticks();
        acted = true;
        return false;
    }

    // ------------------------------------------------------------------ outils

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static void look(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.connection.teleport(x, y, z, yaw, pitch);
    }

    private static Site site(MinecraftServer server, UUID id) {
        return id == null ? null : CrusadeSavedData.get(server).world().site(id).orElse(null);
    }

    private static Person person(MinecraftServer server, UUID id) {
        return CrusadeSavedData.get(server).lineage().person(id).orElse(null);
    }

    private static void click(Minecraft mc, String label) {
        if (mc.screen == null) {
            log("aucun écran pour presser « {} »", label);
            return;
        }
        for (GuiEventListener c : mc.screen.children()) {
            if (c instanceof AbstractButton b && b.getMessage().getString().startsWith(label)) {
                log("on presse « {} » (actif : {})", b.getMessage().getString(), b.active);
                if (b.active) b.onPress();
                return;
            }
        }
        log("pas de bouton « {} » sur {}", label, mc.screen.getClass().getSimpleName());
    }

    private static List<EditBox> boxes(Minecraft mc) {
        List<EditBox> out = new ArrayList<>();
        if (mc.screen != null) for (GuiEventListener c : mc.screen.children()) if (c instanceof EditBox e) out.add(e);
        return out;
    }

    private static void importing(String input, UUID npc) {
        imports++;
        SkinClient.importSkin(input, npc, c -> {
            log("import de « {} » : {}", input, c.getString());
            imports--;
        });
    }

    private static void writeLocalSkin() {
        try {
            Path folder = SkinClient.localFolder();
            Files.createDirectories(folder);
            BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) img.setRGB(x, y, 0xFF000000 | ((x * 4) << 16) | ((y * 4) << 8) | 0x60);
            ImageIO.write(img, "png", folder.resolve("essai_local.png").toFile());
        } catch (IOException e) {
            CrusadeCraft.LOGGER.warn("WorldShots v7 : skin local non écrit", e);
        }
    }

    /** À côté de lui, au-dessus du sol, et le regardant : pas dans une colline, où la capture serait noire. */
    private static void standNear(ServerPlayer p, Entity e, double dx, double dz) {
        int x = (int) (e.getX() + dx), z = (int) (e.getZ() + dz);
        double ground = p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        double y = Math.max(ground + 1, e.getY() + 1.2);
        double horizontal = Math.hypot(dx, dz);
        float pitch = (float) Math.toDegrees(Math.atan2(y - e.getY() - 1, horizontal));
        look(p, e.getX() + dx, y, e.getZ() + dz, (float) Math.toDegrees(Math.atan2(dx, -dz)), pitch);
    }

    /** Au-dessus de lui, assez haut pour voir toute la caravane. */
    private static void overlook(ServerPlayer p, Entity e) {
        double y = e.getY() + 15;
        float pitch = (float) Math.toDegrees(Math.atan2(y + 1.62 - e.getY() - 1, Math.hypot(4, 4)));
        look(p, e.getX() + 4, y, e.getZ() + 4, (float) Math.toDegrees(Math.atan2(4, -4)), pitch);
    }

    /** Sa position, en texte, et à quelle distance il est de ce point. */
    private static String where(Entity e, BlockPos from) {
        return e == null ? "pas dans le monde" : e.blockPosition().toShortString() + " à " + (int) Math.sqrt(e.blockPosition().distSqr(from)) + " blocs";
    }

    // ------------------------------------------------------------------ le scénario

    private static List<Step> build() {
        List<Step> out = new ArrayList<>();
        // Les deux villes : elles restent d'une fois sur l'autre dans la sauvegarde ; sinon, on les pose.
        out.add(new Step("debut", server -> {
            ServerPlayer p = player(server);
            WorldEditService.grantForTests(p.getUUID(), true);
            origin = p.blockPosition();
            log("{} constructions dans cette sauvegarde", CrusadeSavedData.get(server).world().sites().size());
        }, mc -> {
            mc.setScreen(null);
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL);
            mc.options.broadcastOptions();
        }, 20, null));
        place(out, "dolhimluith", DOL, 300);
        place(out, "grunbach", GRU, 720);
        out.add(new Step("depart", server -> {
            ServerPlayer p = player(server);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ServerLevel level = p.serverLevel();
            level.setDayTime(3000);
            CrusadeSavedData data = CrusadeSavedData.get(server);
            for (Site s : data.world().sites()) {
                if (!s.imported()) continue;
                if (DOL.equals(s.importedRef())) home = s.id();
                if (GRU.equals(s.importedRef())) dest = s.id();
            }
            Site h = site(server, home);
            if (h == null) {
                log("ATTENTION : Dolhimluith n'existe pas dans cette sauvegarde");
                return;
            }
            log("une sauvegarde de la version d'avant : {} au registre à Dolhimluith, réglages {}", CityService.people(server, h), h.settings().isDefault() ? "vierges" : "présents");
            var state = data.lineage();
            for (CityBuilding b : h.buildings()) {
                for (UUID id : b.occupants()) {
                    Person x = state.person(id).filter(Person::alive).orElse(null);
                    if (x == null || x.age() < 16) continue;
                    if (b.function() == BuildingFunction.BOULANGERIE && baker == null) baker = id;
                    if (b.function() == BuildingFunction.MARCHE && merchant == null) merchant = id;
                    if (b.function() == BuildingFunction.CASERNE && guard == null) guard = id;
                    if (b.function() == BuildingFunction.MARCHE && !id.equals(merchant) && roadster == null) roadster = id;
                    if (b.function() == BuildingFunction.MAIRIE && stander == null) stander = id;
                    if (b.function() == BuildingFunction.TEMPLE && walker == null) walker = id;
                }
            }
            log("boulanger {}, marchand {}, garde {}, second marchand {}, posté {}, en ronde {}", baker, merchant, guard, roadster, stander, walker);
            BlockPos at = CityService.plazaOf(h);
            for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) level.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
            look(p, at.getX() + 0.5, at.getY() + 30, at.getZ() + 30.5, 180f, 40f);
        }, mc -> mc.setScreen(null), 200, null));
        out.add(new Step("faire_apparaitre", server -> {
            Site h = site(server, home);
            if (h != null) log("{} apparus à Dolhimluith", CityService.populate(player(server).serverLevel(), h));
        }, null, 60, null));

        // ------------------------------------------------------------ le panneau, par le bâton vers le ciel
        out.add(new Step("panneau_ouvre", server -> {
            ServerPlayer p = player(server);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(fr.crusadecraft.registry.ModItems.INTENDANT.get()));
            Site h = site(server, home);
            BlockPos at = CityService.plazaOf(h);
            look(p, at.getX() + 0.5, at.getY() + 8, at.getZ() + 8.5, 180f, 20f);
        }, null, 20, null));
        out.add(new Step("panneau_general", server -> {
            ServerPlayer p = player(server);
            fr.crusadecraft.core.city.CityStaff.useInAir(p, p.getMainHandItem(), false);
        }, null, 30, "v10_panneau_general"));
        out.add(new Step("panneau_voyages_off", null, mc -> click(mc, "Voyages"), 20, null));
        out.add(new Step("panneau_voyages_lu", server -> log("voyages permis à Dolhimluith : {}", site(server, home).settings().canTravel), mc -> click(mc, "Voyages"), 20, null));
        out.add(new Step("panneau_voyages_lu2", server -> log("voyages permis à Dolhimluith : {}", site(server, home).settings().canTravel), null, 5, null));
        out.add(new Step("panneau_camp", null, mc -> click(mc, "Son camp"), 20, "v10_panneau_camp"));
        out.add(new Step("panneau_batiments", null, mc -> click(mc, "Bâtiments"), 20, "v10_panneau_batiments"));
        out.add(new Step("panneau_habitants", null, mc -> click(mc, "Habitants"), 20, "v10_panneau_habitants"));
        out.add(new Step("panneau_reserves", null, mc -> click(mc, "Réserves"), 20, null));
        out.add(new Step("panneau_plus_8", server -> log("pain avant : {}", site(server, home).economy().stock(CityGood.PAIN)), mc -> click(mc, "+8"), 20, "v10_panneau_reserves"));
        out.add(new Step("panneau_plus_8_lu", server -> log("pain après : {}", site(server, home).economy().stock(CityGood.PAIN)), null, 5, null));

        // ------------------------------------------------------------ les skins, pour de vrai
        out.add(new Step("skins_import", null, mc -> {
            writeLocalSkin();
            click(mc, "Apparence");
            importing("Notch", null);
            importing("https://namemc.com/skin/12b92a9206470fe2", null);
            importing("https://minotar.net/skin/jeb_", null);
            importing("https://www.minecraftskins.com/skin/23000000/un-skin/", null);
            importing("essai_local", null);
            importing("https://example.com/", null);
            importing("skin_qui_n_existe_pas_du_tout.png", null);
        }, 60, null, () -> imports == 0));
        out.add(new Step("skins_bibliotheque", null, mc -> {
            SkinClient.askLibrary();
            log("{} skins dans la bibliothèque, {} tenues et {} coiffures de Comes Alive", SkinClient.library().size(), Wardrobe.clothes().size(), Wardrobe.hair().size());
            for (SkinService.Entry e : SkinClient.library()) {
                log("skin {} « {} », source {}, fin {}", e.hash(), e.name(), e.source(), e.slim());
                if (e.name().equals("Notch")) notchHash = e.hash();
            }
        }, 40, "v10_panneau_apparence_skins"));
        out.add(new Step("panneau_tenues_femmes", null, mc -> click(mc, "Tenues de femmes"), 30, "v10_panneau_apparence_tenues"));
        out.add(new Step("panneau_tenues_clic", server -> {
            Site h = site(server, home);
            log("tenues de la ville : {}", h.settings().pools);
        }, mc -> {
            // Un vrai clic sur la première figure de la grille.
            if (mc.screen instanceof CityPanelScreen s) {
                int[] at = s.gridCellForShots(0);
                log("clic en {} {} : {}", at[0], at[1], s.mouseClicked(at[0], at[1], 0));
            }
        }, 30, null));
        out.add(new Step("panneau_tenues_lu", server -> log("tenues de la ville après le clic : {}", site(server, home).settings().pools), null, 30, "v10_panneau_apparence_choisie"));
        out.add(new Step("habiller", server -> {
            Site h = site(server, home);
            int n = CityPanel.applyPools(server, h, 11);
            log("{} habitants habillés", n);
            if (!notchHash.isEmpty()) CityNpc.setLook(player(server), baker, SkinService.PREFIX + notchHash);
            Entity e = player(server).serverLevel().getEntity(baker);
            log("le boulanger : {} ; {}", person(server, baker).settings().look, where(e, CityService.plazaOf(h)));
            if (e != null) standNear(player(server), e, 0, 3);
        }, mc -> mc.setScreen(null), 60, null));
        out.add(new Step("skin_dans_le_monde", server -> {
            Entity e = player(server).serverLevel().getEntity(baker);
            if (e != null) standNear(player(server), e, 0, 3);
        }, mc -> log("côté client, le boulanger porte {} (texture {})", SkinClient.hashOf(baker), notchHash.isEmpty() ? null : SkinClient.texture(notchHash)), 40, "v10_skin_dans_le_monde"));

        // ------------------------------------------------------------ l'éditeur, au bâton sur l'habitant
        out.add(new Step("editeur_fiche", server -> log("éditeur : {}", CityNpc.open(player(server), baker)), null, 40, "v10_editeur_fiche"));
        out.add(new Step("editeur_fiche_ecrit", null, mc -> {
            List<EditBox> b = boxes(mc);
            log("{} champs sur la fiche", b.size());
            if (b.size() >= 3) b.get(2).setValue("la Boulangère");
            click(mc, "Enregistrer");
        }, 30, "v10_editeur_fiche_titre"));
        out.add(new Step("editeur_fiche_lu", server -> {
            Person x = person(server, baker);
            Entity e = player(server).serverLevel().getEntity(baker);
            log("titre {} ; nom au-dessus de sa tête : {}", x.settings().title, e == null ? "?" : e.getDisplayName().getString());
        }, mc -> click(mc, "Allure"), 40, "v10_editeur_allure"));
        out.add(new Step("editeur_stats", null, mc -> click(mc, "Stats"), 20, null));
        out.add(new Step("editeur_stats_ecrit", null, mc -> {
            List<EditBox> b = boxes(mc);
            log("{} champs de statistiques", b.size());
            if (!b.isEmpty()) b.get(0).setValue("44");
            click(mc, "Enregistrer");
        }, 30, "v10_editeur_stats"));
        out.add(new Step("editeur_stats_lu", server -> {
            Entity e = player(server).serverLevel().getEntity(baker);
            log("points de vie du boulanger : {}", e instanceof LivingEntity l ? l.getMaxHealth() : -1f);
        }, mc -> click(mc, "Conduite"), 30, "v10_editeur_conduite"));
        out.add(new Step("editeur_metier", null, mc -> click(mc, "Métier"), 30, "v10_editeur_metier"));
        out.add(new Step("editeur_repliques", null, mc -> click(mc, "Répliques"), 20, null));
        out.add(new Step("editeur_replique_ecrit", null, mc -> {
            List<EditBox> b = boxes(mc);
            if (!b.isEmpty()) b.get(0).setValue("Le pain de {ville} n'attend pas, {joueur}.");
            click(mc, "Ajouter");
        }, 30, "v10_editeur_repliques"));
        out.add(new Step("editeur_echanges", server -> {
            ServerPlayer p = player(server);
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 3));
            p.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 1));
            p.getInventory().setItem(2, new ItemStack(Items.WHEAT, 64));
            p.getInventory().setItem(3, new ItemStack(Items.WHEAT, 64));
            log("répliques du boulanger : {}", person(server, baker).settings().lines);
        }, mc -> click(mc, "Échanges"), 30, null));
        out.add(new Step("editeur_echange_compose", null, mc -> {
            if (mc.screen instanceof NpcEditorScreen s) {
                // Ce qu'il demande : les émeraudes ; la case du milieu reste vide ; ce qu'il donne : le diamant.
                int[] emerald = s.inventorySlotForShots(0), diamond = s.inventorySlotForShots(1), result = s.resultSlotForShots();
                s.mouseClicked(emerald[0], emerald[1], 0);
                s.mouseClicked(result[0], result[1], 0);
                s.mouseClicked(diamond[0], diamond[1], 0);
            }
        }, 20, "v10_editeur_echanges_compose"));
        out.add(new Step("editeur_echange_ajoute", null, mc -> click(mc, "Ajouter"), 30, "v10_editeur_echanges"));
        out.add(new Step("etal_ouvre", server -> {
            ServerPlayer p = player(server);
            NpcSettings n = person(server, baker).settings();
            log("échanges sur mesure : {}", n.offers != null);
            Entity e = p.serverLevel().getEntity(baker);
            if (e != null) {
                standNear(p, e, 0, 3);
                CityTalk.trade(p, baker);
            }
        }, mc -> mc.setScreen(null), 40, "v10_etal_sur_mesure"));

        // ------------------------------------------------------------ à son poste, en ronde
        out.add(new Step("conduite_regle", server -> {
            ServerPlayer p = player(server);
            ServerLevel level = p.serverLevel();
            Site h = site(server, home);
            Entity a = stander == null ? null : level.getEntity(stander), b = walker == null ? null : level.getEntity(walker);
            standerAt = a == null ? null : a.blockPosition();
            walkerAt = b == null ? null : b.blockPosition();
            log("posté : {} ; en ronde : {}", where(a, CityService.plazaOf(h)), where(b, CityService.plazaOf(h)));
            if (stander != null) CityNpc.edit(p, stander, tagOf("behavior", "STATIONARY"));
            if (walker != null) CityNpc.edit(p, walker, tagOf("behavior", "PATROL"));
            look(p, CityService.plazaOf(h).getX() + 0.5, CityService.plazaOf(h).getY() + 16, CityService.plazaOf(h).getZ() + 12.5, 180f, 40f);
        }, mc -> mc.setScreen(null), 900, null));
        out.add(new Step("conduite_bilan", server -> {
            ServerLevel level = player(server).serverLevel();
            Entity a = stander == null ? null : level.getEntity(stander), b = walker == null ? null : level.getEntity(walker);
            if (a != null) log("le posté, poste {}, est en {}", person(server, stander).work(), a.blockPosition().toShortString());
            if (b != null && walkerAt != null) log("le rondier s'est déplacé de {} blocs", (int) Math.sqrt(b.blockPosition().distSqr(walkerAt)));
        }, null, 10, null));

        // ------------------------------------------------------------ un voyage en entier, sous les yeux du joueur
        out.add(new Step("voyage_depart", server -> {
            ServerPlayer p = player(server);
            Site h = site(server, home), d = site(server, dest);
            Person x = person(server, merchant);
            h.economy().add(CityGood.PAIN, 100);
            h.economy().take(CityGood.FER, h.economy().stock(CityGood.FER));
            d.economy().add(CityGood.FER, 100);
            x.addOpinion(p.getUUID(), 60);
            log("Dolhimluith : pain {}, bourse {} ; Grünbach : fer {}, bourse {}", h.economy().stock(CityGood.PAIN), h.economy().purse(), d.economy().stock(CityGood.FER), d.economy().purse());
            CityTravel.depart(server, h, d, x, true);
            Entity e = p.serverLevel().getEntity(merchant);
            log("il part : {} ; lui : {}", x.trip(), where(e, h.anchor()));
            if (e != null) standNear(p, e, 3, 3);
        }, null, 300, "v10_voyage_depart"));
        for (int i = 1; i <= 5; i++) {
            int n = i;
            out.add(new Step("voyage_route_" + n, server -> {
                ServerPlayer p = player(server);
                Person x = person(server, merchant);
                Entity e = p.serverLevel().getEntity(merchant);
                Trip t = x.trip();
                Site h = site(server, home), d = site(server, dest);
                if (t == null) {
                    log("route {} : il n'a plus de voyage", n);
                    return;
                }
                double f = t.progress(p.serverLevel().getGameTime());
                double rx = h.anchor().getX() + (d.anchor().getX() - h.anchor().getX()) * f, rz = h.anchor().getZ() + (d.anchor().getZ() - h.anchor().getZ()) * f;
                log("route {} : étape {}, {} %, lui {}, à {} blocs de sa route", n, t.stage(), (int) (f * 100), e == null ? "absent" : e.blockPosition().toShortString(),
                        e == null ? -1 : (int) Math.hypot(e.getX() - rx, e.getZ() - rz));
                // Le joueur suit, un peu au-dessus de la route.
                int y = p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) rx, (int) rz);
                look(p, rx + 6, y + 6, rz + 6, 135f, 30f);
            }, null, 600, n == 3 ? "v10_voyage_route" : null));
        }
        out.add(new Step("voyage_arrive", server -> {
            ServerPlayer p = player(server);
            Person x = person(server, merchant);
            Site d = site(server, dest);
            look(p, CityService.plazaOf(d).getX() + 0.5, CityService.plazaOf(d).getY() + 8, CityService.plazaOf(d).getZ() + 8.5, 180f, 20f);
            if (x.trip() != null && x.trip().stage() == Trip.Stage.OUT) CityTravel.advanceForTests(server, x, p.serverLevel().getGameTime());
            log("il arrive : {} ; rumeurs de Grünbach : {}", x.trip(), d.rumors().stream().map(Site.Rumor::text).toList());
        }, null, 600, null));
        out.add(new Step("voyage_visite", server -> {
            ServerPlayer p = player(server);
            Person x = person(server, merchant);
            Site d = site(server, dest);
            Entity e = p.serverLevel().getEntity(merchant);
            log("il visite : {} ; {} ; Grünbach : pain {}, fer {}, bourse {}", x.trip(), where(e, CityService.plazaOf(d)), d.economy().stock(CityGood.PAIN), d.economy().stock(CityGood.FER), d.economy().purse());
            if (e != null) {
                standNear(p, e, 2, 2);
                CityTalk.open(p, e);
            }
        }, null, 40, "v10_voyageur_parle"));
        out.add(new Step("voyage_retour", server -> {
            ServerPlayer p = player(server);
            Person x = person(server, merchant);
            long now = p.serverLevel().getGameTime();
            if (x.trip() != null) CityTravel.advanceForTests(server, x, now);
            log("il repart : {}", x.trip());
            if (x.trip() != null) CityTravel.advanceForTests(server, x, now);
            Site h = site(server, home);
            log("il est rentré : voyage {}, point d'attache {}, Dolhimluith : pain {}, fer {}, bourse {}", x.trip(), x.worldPos(), h.economy().stock(CityGood.PAIN), h.economy().stock(CityGood.FER), h.economy().purse());
            log("rumeurs de Dolhimluith : {}", h.rumors().stream().map(Site.Rumor::text).toList());
        }, mc -> mc.setScreen(null), 20, null));

        // Un voyageur qu'on croise : sans joueur, aucun villageois ; le joueur arrive, il paraît ; le joueur s'en va, il disparaît.
        out.add(new Step("croiser_depart", server -> {
            ServerPlayer p = player(server);
            Site h = site(server, home), d = site(server, dest);
            look(p, h.anchor().getX() + 0.5, h.anchor().getY() + 40, h.anchor().getZ() - 400.5, 0f, 30f);
            if (roadster != null) CityTravel.depart(server, h, d, person(server, roadster), false);
        }, null, 200, null));
        out.add(new Step("croiser_personne", server -> {
            ServerPlayer p = player(server);
            log("loin de tout : le voyageur est dans le monde : {}", roadster != null && p.serverLevel().getEntity(roadster) != null);
            Person x = person(server, roadster);
            Site h = site(server, home), d = site(server, dest);
            if (x == null || x.trip() == null) return;
            double f = x.trip().progress(p.serverLevel().getGameTime());
            double rx = h.anchor().getX() + (d.anchor().getX() - h.anchor().getX()) * f, rz = h.anchor().getZ() + (d.anchor().getZ() - h.anchor().getZ()) * f;
            p.serverLevel().getChunk((int) rx >> 4, (int) rz >> 4);
            int y = p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) rx, (int) rz);
            look(p, rx + 10, y + 6, rz + 10, 135f, 20f);
        }, null, 160, null));
        out.add(new Step("croiser_vue", server -> {
            ServerPlayer p = player(server);
            Entity e = roadster == null ? null : p.serverLevel().getEntity(roadster);
            if (e != null) overlook(p, e);
        }, null, 6, "v10_voyageur_croise"));
        out.add(new Step("croiser_paru", server -> {
            ServerPlayer p = player(server);
            Entity e = roadster == null ? null : p.serverLevel().getEntity(roadster);
            log("on arrive sur la route : le voyageur est {} ; joueur en {}", e == null ? "absent" : "là, en " + e.blockPosition().toShortString() + ", à " + (int) p.distanceTo(e) + " blocs", p.blockPosition().toShortString());
            Site h = site(server, home);
            look(p, h.anchor().getX() + 0.5, h.anchor().getY() + 60, h.anchor().getZ() - 500.5, 0f, 30f);
        }, null, 200, null));
        out.add(new Step("croiser_parti", server -> {
            ServerPlayer p = player(server);
            log("on s'éloigne : le voyageur est dans le monde : {}", roadster != null && p.serverLevel().getEntity(roadster) != null);
        }, null, 10, null));

        // ------------------------------------------------------------ de vrais échanges, face à face
        out.add(new Step("retour_en_ville", server -> {
            ServerPlayer p = player(server);
            Site h = site(server, home);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            BlockPos at = CityService.plazaOf(h);
            look(p, at.getX() + 0.5, at.getY() + 8, at.getZ() + 8.5, 180f, 20f);
            p.serverLevel().setDayTime(3000);
        }, mc -> mc.setScreen(null), 200, null));
        out.add(new Step("echange_prepare", server -> {
            ServerPlayer p = player(server);
            ServerLevel level = p.serverLevel();
            Site h = site(server, home);
            log("{} apparus en revenant", CityService.populate(level, h));
            h.economy().add(CityGood.PAIN, 40);
            Person buyer = person(server, stander), seller = person(server, baker);
            Entity b = stander == null ? null : level.getEntity(stander), e = level.getEntity(baker);
            log("acheteur {} ({} pièces), vendeuse {} ({} pièces) ; pain {}", buyer == null ? "?" : buyer.name(), buyer == null ? 0 : buyer.coins(), seller.name(), seller.coins(), h.economy().stock(CityGood.PAIN));
            if (e != null && b != null) {
                // On les réunit sur la place pour la photo : le chemin à pied, lui, se voit dans la tournée qui suit.
                Site d = site(server, dest);
                int ax = h.anchor().getX() + (d.anchor().getX() - h.anchor().getX()) * 3 / 10, az = h.anchor().getZ() + (d.anchor().getZ() - h.anchor().getZ()) * 3 / 10;
                level.getChunk(ax >> 4, az >> 4);
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ax, az);
                e.teleportTo(ax + 0.5, ground, az + 0.5);
                b.teleportTo(ax + 3.5, ground, az + 0.5);
                buyer.setCoins(Math.max(buyer.coins(), 3));
                look(p, ax + 2.0, ground + 2.5, az + 6.5, 180f, 14f);
                log("échange lancé : {}", fr.crusadecraft.core.city.CityScenes.sale(server, level, h, buyer, (net.minecraft.world.entity.PathfinderMob) b, CityGood.PAIN, false));
            }
        }, mc -> mc.setScreen(null), 90, "v11_echange_demande"));
        out.add(new Step("echange_reponse", null, null, 50, "v11_echange_reponse"));
        out.add(new Step("echange_main", null, null, 50, "v11_echange_passe"));
        out.add(new Step("echange_bilan", server -> {
            Person buyer = person(server, stander), seller = person(server, baker);
            Site h = site(server, home);
            log("après l'échange : scène en cours {}, acheteur {} pièces, vendeuse {} pièces, pain {}", fr.crusadecraft.core.city.CityScenes.busy(baker), buyer == null ? 0 : buyer.coins(), seller.coins(), h.economy().stock(CityGood.PAIN));
        }, null, 60, null));
        // Une vraie tournée, sans rien forcer : midi au marché. On compte ce qui se passe de soi-même.
        out.add(new Step("vie_midi", server -> {
            ServerPlayer p = player(server);
            Site h = site(server, home);
            p.serverLevel().setDayTime(5300);
            BlockPos at = CityService.plazaOf(h);
            look(p, at.getX() + 0.5, at.getY() + 14, at.getZ() + 14.5, 180f, 35f);
            log("début de la tournée libre : {} lignes au journal", fr.crusadecraft.core.city.CityJournal.of(h.id()).size());
        }, mc -> mc.setScreen(null), 500, null));
        for (int i = 1; i <= 4; i++) {
            int n = i;
            out.add(new Step("vie_compte_" + n, server -> log("tournée {} : {} scènes en cours", n, fr.crusadecraft.core.city.CityScenes.active()), null, 400, null));
            out.add(new Step("vie_vue_" + n, server -> {
                ServerPlayer p = player(server);
                UUID who = fr.crusadecraft.core.city.CityScenes.someoneInScene();
                Entity e = who == null ? null : p.serverLevel().getEntity(who);
                if (e != null) standNear(p, e, 3, 3);
                log("sur place : {}", who == null ? "personne en scène" : person(server, who).name());
            }, null, 30, "v11_vie_de_la_ville_" + n));
        }
        out.add(new Step("vie_journal", server -> {
            Site h = site(server, home);
            for (var l : fr.crusadecraft.core.city.CityJournal.of(h.id())) log("journal {} [{}] {}", l.time(), l.kind(), l.text());
            CityPanel.open(player(server), h);
        }, null, 20, null));
        out.add(new Step("vie_onglet", null, mc -> click(mc, "En direct"), 70, "v11_panneau_en_direct"));

        // ------------------------------------------------------------ une caravane, avec ses bêtes et son escorte
        out.add(new Step("caravane_part", server -> {
            ServerPlayer p = player(server);
            Site h = site(server, home), d = site(server, dest);
            h.economy().add(CityGood.PAIN, 80);
            Person x = person(server, roadster != null && person(server, roadster).trip() == null ? roadster : merchant);
            if (x.trip() != null) {
                log("le marchand est déjà parti : {}", x.trip());
                return;
            }
            caravanner = x.id();
            CityTravel.depart(server, h, d, x, true);
            Entity e = p.serverLevel().getEntity(x.id());
            log("caravane : {} ; {}", x.trip(), where(e, h.anchor()));
            if (e != null) standNear(p, e, 6, 6);
        }, mc -> mc.setScreen(null), 200, null));
        for (int i = 1; i <= 6; i++) {
            int n = i;
            out.add(new Step("caravane_route_" + n, server -> {
                ServerPlayer p = player(server);
                ServerLevel level = p.serverLevel();
                Entity e = caravanner == null ? null : level.getEntity(caravanner);
                Person x = caravanner == null ? null : person(server, caravanner);
                int beasts = 0, leashed = 0, guards = 0;
                if (e != null) {
                    for (var l : level.getEntitiesOfClass(net.minecraft.world.entity.animal.horse.Llama.class, e.getBoundingBox().inflate(32), q -> q.getTags().contains(CityTravel.PACK_TAG))) {
                        beasts++;
                        if (l.isLeashed()) leashed++;
                    }
                    for (Person g : CrusadeSavedData.get(server).lineage().persons()) {
                        if (g.trip() != null && caravanner.equals(g.trip().leader()) && level.getEntity(g.id()) != null) guards++;
                    }
                    overlook(p, e);
                }
                log("caravane {} : étape {}, {} bêtes dont {} en laisse, {} gardes présents, lui {}", n, x == null || x.trip() == null ? "?" : x.trip().stage(), beasts, leashed, guards, e == null ? "absent" : e.blockPosition().toShortString());
            }, null, 250, null));
            if (n >= 3) {
                out.add(new Step("caravane_vue_" + n, server -> {
                    ServerPlayer p = player(server);
                    Entity e = caravanner == null ? null : p.serverLevel().getEntity(caravanner);
                    if (e != null) overlook(p, e);
                }, mc -> {
                    if (mc.level == null || mc.player == null) return;
                    StringBuilder seen = new StringBuilder();
                    for (Entity x : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(32), q -> q instanceof LivingEntity)) {
                        seen.append(x.getType().getDescriptionId().replace("entity.", "")).append(" « ").append(x.getName().getString()).append(" » en ").append(x.blockPosition().toShortString()).append(x.isInvisible() ? " INVISIBLE" : "").append(" ; ");
                    }
                    log("le client voit autour de lui, depuis {} : {}", mc.player.blockPosition().toShortString(), seen);
                }, 12, "v11_caravane_sur_la_route_" + n));
            }
        }
        out.add(new Step("caravane_carte", null, mc -> fr.crusadecraft.network.CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.WorldPackets.RequestMap(true)), 40, "v11_carte_caravane"));
        out.add(new Step("caravane_panneau", server -> CityPanel.open(player(server), site(server, home)), mc -> mc.setScreen(null), 90, "v11_en_direct_voyageurs"));
        out.add(new Step("caravane_voir", null, mc -> click(mc, "Voir"), 60, "v11_bouton_voir"));
        out.add(new Step("caravane_rentre", server -> {
            ServerPlayer p = player(server);
            ServerLevel level = p.serverLevel();
            Person x = caravanner == null ? null : person(server, caravanner);
            if (x == null) return;
            long now = level.getGameTime();
            for (int i = 0; i < 4 && x.trip() != null; i++) CityTravel.advanceForTests(server, x, now);
            int left = 0;
            for (Person g : CrusadeSavedData.get(server).lineage().persons()) if (g.trip() != null) left++;
            CityTravel.tick(server);
            log("caravane rentrée : voyage {} ; encore {} voyageurs", x.trip(), left);
            Site h = site(server, home);
            BlockPos at = CityService.plazaOf(h);
            look(p, at.getX() + 0.5, at.getY() + 8, at.getZ() + 8.5, 180f, 20f);
        }, mc -> mc.setScreen(null), 200, null));
        out.add(new Step("caravane_betes", server -> {
            int beasts = 0;
            for (ServerLevel level : server.getAllLevels()) beasts += level.getEntities(net.minecraft.world.entity.EntityType.LLAMA, q -> q.getTags().contains(CityTravel.PACK_TAG)).size();
            log("lamas restant dans le monde : {}", beasts);
        }, null, 40, null));

        // ------------------------------------------------------------ commandes et courrier
        out.add(new Step("commande", server -> {
            ServerPlayer p = player(server);
            Site h = site(server, home);
            h.economy().take(CityGood.BLE, h.economy().stock(CityGood.BLE));
            h.economy().addPurse(500);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            Random r = new Random(3);
            for (int i = 0; i < 12 && h.orders().stream().noneMatch(o -> o.good() == CityGood.BLE); i++) {
                h.economy().take(CityGood.BLE, h.economy().stock(CityGood.BLE));
                CityLife.day(server, h, r);
            }
            log("commandes : {}", h.orders());
            for (CityPost.Letter l : CrusadeSavedData.get(server).world().post(p.getUUID())) log("lettre {} de {} : {}", l.kind(), l.from(), l.text());
            Entity e = p.serverLevel().getEntity(baker);
            if (e != null) standNear(p, e, 0, 3);
        }, mc -> mc.setScreen(null), 40, "v10_lettre_dans_le_chat"));
        out.add(new Step("courrier_ouvre", null, mc -> mc.player.connection.sendCommand("crusade courrier"), 30, "v10_courrier", () -> Minecraft.getInstance().screen instanceof PostScreen));
        out.add(new Step("courrier_lit", null, mc -> {
            if (mc.screen instanceof PostScreen s) {
                int[] at = s.rowForShots(0);
                s.mouseClicked(at[0], at[1], 0);
            } else log("pas d'écran de courrier : {}", mc.screen);
        }, 20, "v10_courrier_lettre"));
        out.add(new Step("livrer_ouvre", server -> {
            ServerPlayer p = player(server);
            Entity e = p.serverLevel().getEntity(baker);
            Site h = site(server, home);
            if (!h.orders().isEmpty()) {
                CityOrders.Order o = h.orders().get(0);
                p.getInventory().add(new ItemStack(fr.crusadecraft.core.city.CityScenes.item(o.good()), Math.min(o.count(), 64)));
                log("le joueur prend de quoi honorer : {} ; bourse de la ville {}", o, h.economy().purse());
            }
            if (e != null) look(p, e.getX() + 1.5, e.getY(), e.getZ() + 1.5, 135f, 10f);
            log("on parle au boulanger : {}", e != null && CityTalk.open(p, e));
        }, mc -> mc.setScreen(null), 40, "v10_livrer_bouton", () -> Minecraft.getInstance().screen instanceof InhabitantScreen));
        out.add(new Step("livrer", server -> log("bourse de Dolhimluith avant : {}", site(server, home).economy().purse()), mc -> {
            if (mc.screen instanceof InhabitantScreen) click(mc, "Livrer");
            else log("pas de fiche : {}", mc.screen);
        }, 30, "v10_livre"));
        out.add(new Step("livrer_bilan", server -> {
            ServerPlayer p = player(server);
            Site h = site(server, home);
            int emeralds = 0;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(Items.EMERALD)) emeralds += p.getInventory().getItem(i).getCount();
            log("commandes restantes {}, bourse {}, blé {}, émeraudes {}", h.orders(), h.economy().purse(), h.economy().stock(CityGood.BLE), emeralds);
            for (CityPost.Letter l : CrusadeSavedData.get(server).world().post(p.getUUID())) if (l.kind() == CityPost.Kind.MERCI) log("merci : {}", l.text());
        }, mc -> mc.setScreen(null), 10, null));

        // ------------------------------------------------------------ fonder une ville, et le panneau s'ouvre tout seul
        out.add(new Step("fonder_ville", server -> {
            ServerPlayer p = player(server);
            ServerLevel level = p.serverLevel();
            Site h = site(server, home);
            int x = h.anchor().getX() - 260, z = h.anchor().getZ() - 260;
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) level.getChunk((x >> 4) + dx, (z >> 4) + dz);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            // Une petite maison, pour que la ville ait de quoi être reconnue.
            BlockPos base = new BlockPos(x + 4, y, z + 4);
            for (BlockPos q : BlockPos.betweenClosed(base, base.offset(6, 3, 6))) {
                boolean wall = q.getX() == base.getX() || q.getX() == base.getX() + 6 || q.getZ() == base.getZ() || q.getZ() == base.getZ() + 6;
                boolean roof = q.getY() == base.getY() + 3;
                level.setBlock(q, wall || roof ? net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            for (BlockPos q : BlockPos.betweenClosed(base.offset(0, -1, 0), base.offset(6, -1, 6))) level.setBlock(q, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(), 3);
            BlockPos door = base.offset(3, 0, 0);
            level.setBlock(door, net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH), 3);
            level.setBlock(door.above(), net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH)
                    .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
            level.setBlock(base.offset(3, 0, 3), net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState(), 3);
            look(p, x + 0.5, y + 1, z + 0.5, 135f, 10f);
        }, mc -> mc.setScreen(null), 40, null));
        out.add(new Step("fonder_panneau", server -> {
            ServerPlayer p = player(server);
            var res = CityService.found(p, "Libreval", 48);
            log("fonder : {} — {}", res.success(), res.message().getString());
            var here = CityService.cityAt(server, p.level().dimension().location(), p.blockPosition());
            log("ville fondée : {}, {} bâtiments", here.map(Site::name).orElse("?"), here.map(s -> s.buildings().size()).orElse(0));
            here.ifPresent(s -> founded = s.id());
        }, null, 40, "v10_ville_fondee_panneau", () -> Minecraft.getInstance().screen instanceof CityPanelScreen));
        out.add(new Step("fonder_camp", null, mc -> {
            log("écran ouvert à la fondation : {}", mc.screen == null ? null : mc.screen.getClass().getSimpleName());
            click(mc, "Son camp");
        }, 30, "v10_ville_fondee_camp"));
        out.add(new Step("fonder_independante", server -> {
            Site s = site(server, founded);
            if (s != null) log("son camp au départ : {}", CrusadeSavedData.get(server).world().realm(s.owner()).map(AiRealm::place).orElse("?"));
        }, mc -> click(mc, "La rendre indépendante"), 30, "v10_ville_independante"));
        out.add(new Step("fonder_royaume", server -> {
            Site s = site(server, founded);
            if (s != null) log("indépendante : {}", CrusadeSavedData.get(server).world().realm(s.owner()).map(AiRealm::place).orElse("?"));
        }, mc -> {
            List<EditBox> b = boxes(mc);
            log("{} champs dans Son camp", b.size());
            if (!b.isEmpty()) b.get(0).setValue("Marche de Libreval");
            click(mc, "Fonder");
        }, 40, "v10_royaume_fonde"));
        out.add(new Step("fonder_bilan", server -> {
            Site s = site(server, founded);
            ServerPlayer p = player(server);
            if (s == null) return;
            CrusadeSavedData.get(server).world().realm(s.owner()).ifPresent(r -> log("royaume : {}, nation {}, souverain {}, rang {}", r.place(), r.nation(), r.sovereignName(), r.rank()));
            log("le joueur règne sur : {}", fr.crusadecraft.core.nation.NationService.ruledBy(server, p.getUUID()).map(AiRealm::place).orElse("rien"));
        }, null, 10, null));
        // ------------------------------------------------------------ un simulateur : lignes, marchés, routes
        out.add(new Step("ligne_panneau", server -> {
            ServerPlayer p = player(server);
            Site h = site(server, home);
            BlockPos at = CityService.plazaOf(h);
            look(p, at.getX() + 0.5, at.getY() + 8, at.getZ() + 8.5, 180f, 20f);
            CityPanel.open(p, h);
        }, mc -> mc.setScreen(null), 30, null));
        out.add(new Step("ligne_onglet", null, mc -> click(mc, "Commerce"), 20, "v12_panneau_commerce"));
        // La ville la plus proche est à deux pas : on prend la cinquième, pour avoir une vraie route à regarder.
        out.add(new Step("ligne_choisit", null, mc -> {
            for (int i = 0; i < 5; i++) {
                for (GuiEventListener c : mc.screen == null ? List.<GuiEventListener>of() : new ArrayList<>(mc.screen.children())) {
                    if (c instanceof AbstractButton b && b.getMessage().getString().equals("▸")) {
                        b.onPress();
                        break;
                    }
                }
            }
        }, 10, null));
        out.add(new Step("ligne_cree", null, mc -> click(mc, "Créer la ligne"), 30, "v12_ligne_creee"));
        out.add(new Step("ligne_part", server -> {
            CrusadeSavedData data = CrusadeSavedData.get(server);
            for (fr.crusadecraft.core.city.CityLine l : data.world().lines()) log("ligne {} → {} : {} ({}), rythme {}, chemin {}", data.world().site(l.from).map(Site::name).orElse("?"), data.world().site(l.to).map(Site::name).orElse("?"), l.good, l.amount, l.every, l.pave);
            site(server, home).economy().add(CityGood.PAIN, 120);
            fr.crusadecraft.core.city.CityCommerce.pulse(server);
            for (fr.crusadecraft.core.city.CityLine l : data.world().lines()) log("après le départ : statut {}", l.status);
        }, null, 120, null));
        out.add(new Step("ordinateur", server -> {
            CrusadeSavedData data = CrusadeSavedData.get(server);
            Random r = new Random(11);
            for (int i = 0; i < 12; i++) fr.crusadecraft.core.city.CityCommerce.planAi(server, r);
            for (int i = 0; i < 12; i++) fr.crusadecraft.core.city.CityMarkets.onDay(server, r);
            for (int i = 0; i < 4; i++) {
                for (fr.crusadecraft.core.city.CityLine l : data.world().lines()) l.waited = 99;
                fr.crusadecraft.core.city.CityCommerce.pulse(server);
            }
            log("{} marchés, {} lignes, {} routes tracées, {} voyageurs", data.world().markets().size(), data.world().lines().size(), data.world().roads().size(), CityTravel.pawns(server).size());
            for (fr.crusadecraft.core.city.CityLine l : data.world().lines()) log("ligne {} → {} : {}, statut {}, ordinateur {}", data.world().site(l.from).map(Site::name).orElse("?"), data.world().site(l.to).map(Site::name).orElse("?"), l.good, l.status, l.ai);
            for (Site m : data.world().markets()) log("marché {} : {}", m.name(), CityLife.summary(m));
        }, mc -> mc.setScreen(null), 200, null));
        out.add(new Step("routes", server -> {
            CrusadeSavedData data = CrusadeSavedData.get(server);
            data.world().roads().forEach((k, r) -> log("route : {} points, {} blocs", r.xs().length, (int) r.length()));
            Site h = site(server, home), d = site(server, dest);
            log("Dolhimluith → Grunbach : tout droit {} blocs, route {} blocs", (int) Math.sqrt(h.anchor().distSqr(d.anchor())), (int) fr.crusadecraft.core.city.CityRoads.between(server, h, d).length());
        }, null, 10, null));
        out.add(new Step("carte_lignes", null, mc -> fr.crusadecraft.network.CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.WorldPackets.RequestMap(true)), 60, "v12_carte_lignes"));
        // ------------------------------------------------------------ une caravane marche sur sa ligne, et creuse son chemin
        out.add(new Step("chemin_suit", server -> {
            ServerPlayer p = player(server);
            for (Person x : CrusadeSavedData.get(server).lineage().persons()) {
                if (x.trip() == null || x.trip().line() == null || !x.trip().home().equals(home)) continue;
                caravanner = x.id();
                Site h = site(server, home), d = site(server, x.trip().dest());
                if (d == null) continue;
                fr.crusadecraft.core.city.CityRoads.Route route = fr.crusadecraft.core.city.CityRoads.between(server, x.trip().stage() == Trip.Stage.BACK ? d : h, x.trip().stage() == Trip.Stage.BACK ? h : d);
                log("sa route : {} points, {} blocs", route.xs().length, (int) route.length());
                int[] at = route.at(x.trip().progress(p.serverLevel().getGameTime()));
                p.serverLevel().getChunk(at[0] >> 4, at[1] >> 4);
                look(p, at[0] + 8.5, p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at[0] + 8, at[1] + 8) + 12, at[1] + 8.5, 135f, 40f);
                log("on rejoint la caravane de {} : {} ; {}", x.name(), x.trip().stage(), x.trip().good());
                break;
            }
        }, mc -> mc.setScreen(null), 300, null));
        for (int i = 1; i <= 2; i++) {
            int n = i;
            out.add(new Step("chemin_vue_" + n, server -> {
                ServerPlayer p = player(server);
                Entity e = caravanner == null ? null : p.serverLevel().getEntity(caravanner);
                int path = 0;
                if (e != null) {
                    overlook(p, e);
                    for (BlockPos q : BlockPos.betweenClosed(e.blockPosition().offset(-24, -4, -24), e.blockPosition().offset(24, 4, 24))) {
                        if (p.serverLevel().getBlockState(q).is(net.minecraft.world.level.block.Blocks.DIRT_PATH)) path++;
                    }
                }
                log("caravane sur sa ligne {} : {} ; sur {} ; {} blocs de chemin autour d'elle", n, e == null ? "absente" : e.blockPosition().toShortString(), e == null ? "?" : p.serverLevel().getBlockState(e.blockPosition().below()).getBlock(), path);
            }, null, 300, "v12_chemin_" + n));
        }
        // ------------------------------------------------------------ les gardes se battent
        out.add(new Step("garde_prepare", server -> {
            ServerPlayer p = player(server);
            ServerLevel level = p.serverLevel();
            Site h = site(server, home);
            guardian = null;
            log("difficulté du monde : {} ; on passe en normal pour que les coups portent", server.getWorldData().getDifficulty());
            server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            if (h.buildings().stream().noneMatch(b -> b.function() == BuildingFunction.CASERNE)) {
                CityBuilding last = h.buildings().get(h.buildings().size() - 1);
                log("{} ; on en fait une caserne", CityService.define(p, h.id(), last, BuildingFunction.CASERNE, "").message().getString());
            }
            for (CityBuilding b : h.buildings()) {
                if (b.function() != BuildingFunction.CASERNE) continue;
                for (UUID id : b.occupants()) {
                    Person x = person(server, id);
                    if (x != null && x.alive() && x.trip() == null) guardian = id;
                }
            }
            BlockPos at = CityService.plazaOf(h);
            look(p, at.getX() + 0.5, at.getY() + 8, at.getZ() + 8.5, 180f, 20f);
            CityService.populate(level, h);
            log("le garde : {}", guardian == null ? "aucun de libre" : person(server, guardian).name());
        }, mc -> mc.setScreen(null), 120, null));
        out.add(new Step("garde_monstre", server -> {
            ServerPlayer p = player(server);
            ServerLevel level = p.serverLevel();
            Entity g = guardian == null ? null : level.getEntity(guardian);
            if (g == null) {
                log("pas de garde dans le monde");
                return;
            }
            Site h = site(server, home), d = site(server, dest);
            field = new int[]{h.anchor().getX() + (d.anchor().getX() - h.anchor().getX()) * 3 / 10, h.anchor().getZ() + (d.anchor().getZ() - h.anchor().getZ()) * 3 / 10};
            level.getChunk(field[0] >> 4, field[1] >> 4);
            int ground = PLATFORM + 1;
            for (BlockPos q : BlockPos.betweenClosed(new BlockPos(field[0] - 16, PLATFORM, field[1] - 16), new BlockPos(field[0] + 16, PLATFORM, field[1] + 16))) level.setBlock(q, net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            g.teleportTo(field[0] + 0.5, ground, field[1] + 0.5);
            int gx = field[0] + 5, gz = field[1];
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            zombie.moveTo(gx + 0.5, ground, gz + 0.5);
            zombie.setCustomName(net.minecraft.network.chat.Component.literal("Le monstre d'essai"));
            level.addFreshEntity(zombie);
            monster = zombie.getUUID();
            look(p, field[0] + 2.5, ground + 5, field[1] + 9.5, 180f, 25f);
            log("un monstre à {} blocs du garde", (int) g.distanceTo(zombie));
        }, null, 40, "v12_garde_voit_monstre"));
        out.add(new Step("garde_frappe", server -> {
            ServerPlayer p = player(server);
            Entity g = guardian == null ? null : p.serverLevel().getEntity(guardian);
            Entity z = monster == null ? null : p.serverLevel().getEntity(monster);
            log("le garde se bat : {}, à {} blocs de sa cible ; le monstre : {}", g != null && fr.crusadecraft.core.city.CityGuards.fighting(guardian), g == null || z == null ? -1 : (int) g.distanceTo(z), z instanceof LivingEntity l ? l.getHealth() + " points de vie" : "plus là");
        }, null, 100, "v12_garde_frappe"));
        out.add(new Step("garde_bilan", server -> {
            ServerPlayer p = player(server);
            Entity z = monster == null ? null : p.serverLevel().getEntity(monster);
            log("le monstre, cent ticks plus tard : {}", z instanceof LivingEntity l && l.isAlive() ? l.getHealth() + " points de vie" : "mort");
            if (z != null) z.discard();
        }, null, 10, null));
        out.add(new Step("joueur_frappe", server -> {
            ServerPlayer p = player(server);
            ServerLevel level = p.serverLevel();
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.setHealth(p.getMaxHealth());
            Entity g = guardian == null ? null : level.getEntity(guardian);
            if (!(g instanceof LivingEntity guardMob) || field == null) return;
            int ground = PLATFORM + 1;
            g.teleportTo(field[0] + 0.5, ground, field[1] + 0.5);
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            int px = field[0] + 6, pz = field[1] + 6;
            p.connection.teleport(px + 0.5, ground, pz + 0.5, 135f, 5f);
            // Le joueur frappe un habitant, sous les yeux du garde : pour de vrai, comme une épée.
            Entity victim = baker == null ? null : level.getEntity(baker);
            Entity hit = victim != null && victim.distanceTo(g) < 24 ? victim : g;
            hit.hurt(level.damageSources().playerAttack(p), 1f);
            log("le joueur frappe {} ; {} points de vie ; rancune : {}", hit.getName().getString(), p.getHealth(), fr.crusadecraft.core.city.CityGuards.hasGrudge(server, p.getUUID(), home));
        }, null, 50, "v12_garde_attaque_joueur"));
        out.add(new Step("joueur_mi_course", server -> {
            ServerPlayer p = player(server);
            Entity g = guardian == null ? null : p.serverLevel().getEntity(guardian);
            log("à mi-course : joueur {} points de vie, le garde à {} blocs", p.getHealth(), g == null ? -1 : (int) g.distanceTo(p));
        }, null, 50, "v12_garde_attaque_joueur_2"));
        out.add(new Step("joueur_bilan", server -> {
            ServerPlayer p = player(server);
            Entity g = guardian == null ? null : p.serverLevel().getEntity(guardian);
            var last = p.getLastDamageSource();
            log("cent ticks plus tard : joueur {} points de vie, dernier coup : {} par {}, le garde vise le joueur : {}, à {} blocs", p.getHealth(), last == null ? "aucun" : last.getMsgId(), last == null || last.getEntity() == null ? "personne" : last.getEntity().getName().getString(),
                    guardian != null && p.getUUID().equals(fr.crusadecraft.core.city.CityGuards.targetOf(guardian)), g == null ? -1 : (int) g.distanceTo(p));
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            p.setHealth(p.getMaxHealth());
            fr.crusadecraft.core.city.CityGuards.onServerStopped();
        }, null, 40, null));
        // ------------------------------------------------------------ P54 : l'économie du monde
        out.add(new Step("eco_ouvre", server -> fr.crusadecraft.core.city.WorldEconomy.send(player(server), false), mc -> mc.setScreen(null), 30, "v13_eco_villes"));
        for (String[] t : new String[][]{{"Prix", "v13_eco_prix"}, {"Lignes", "v13_eco_lignes"}, {"Routes", "v13_eco_routes"}, {"Royaumes", "v13_eco_royaumes"}}) {
            out.add(new Step("eco_" + t[1], null, mc -> click(mc, t[0]), 20, t[1]));
        }
        // ------------------------------------------------------------ P55 : la guerre, le blocus, la famine, le péage
        out.add(new Step("blocus_prepare", server -> {
            ServerPlayer p = player(server);
            CrusadeSavedData data = CrusadeSavedData.get(server);
            java.util.UUID me = fr.crusadecraft.core.city.WorldEconomy.myRealm(p);
            Site h = site(server, dest);
            log("je dirige : {} ; on déclare la guerre au royaume de {}", me, h.name());
            if (me != null) {
                data.diplomacy().between(me, h.owner()).setWar(true);
                data.world().realm(me).ifPresent(r -> r.setGold(500));
            }
            fr.crusadecraft.core.city.WorldEconomy.send(p, false);
        }, mc -> mc.setScreen(null), 30, null));
        out.add(new Step("blocus_onglet", null, mc -> {
            click(mc, "Villes");
            // Jusqu'à ce que les ennemies passent en tête.
            for (int i = 0; i < 4; i++) click(mc, "Trier");
        }, 20, "v13_blocus_avant"));
        out.add(new Step("blocus_bloque", null, mc -> click(mc, "Bloquer"), 30, "v13_blocus_ecran"));
        out.add(new Step("blocus_famine", server -> {
            CrusadeSavedData data = CrusadeSavedData.get(server);
            log("{} blocus ; villes coupées : {}", data.world().blockades().size(), fr.crusadecraft.core.city.CityBlockades.blocked(server).size());
            Random r = new Random(7);
            for (java.util.UUID id : new ArrayList<>(data.world().blockades().keySet())) {
                Site s = site(server, id);
                if (s == null) continue;
                blockaded = id;
                log("{} : {} ; avant : {}", s.name(), fr.crusadecraft.core.city.CityBlockades.explain(server, s), CityLife.summary(s));
                for (int i = 0; i < 8; i++) {
                    var day = CityLife.day(server, s, r);
                    log("jour {} : {} ont faim, famine {}, blé {}, pain {}", i + 1, day.hungry(), s.economy().famine(), s.economy().stock(CityGood.BLE), s.economy().stock(CityGood.PAIN));
                }
            }
            for (fr.crusadecraft.core.city.CityLine l : data.world().lines()) l.waited = 99;
            fr.crusadecraft.core.city.CityCommerce.pulse(server);
            for (fr.crusadecraft.core.city.CityLine l : data.world().lines()) log("ligne {} : {}", data.world().site(l.from).map(Site::name).orElse("?"), l.status);
            fr.crusadecraft.core.city.WorldEconomy.send(player(server), true);
        }, null, 40, "v13_blocus_famine"));
        // On se place loin d'elle : la carte se centre sur le joueur, et la bulle sous la souris cacherait l'anneau.
        out.add(new Step("blocus_carte_place", server -> {
            ServerPlayer p = player(server);
            Site s = blockaded == null ? site(server, dest) : site(server, blockaded);
            if (s != null) look(p, s.anchor().getX() - 1400.5, 200, s.anchor().getZ() + 0.5, 0f, 20f);
        }, mc -> mc.setScreen(null), 20, null));
        out.add(new Step("blocus_carte", null, mc -> fr.crusadecraft.network.CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.WorldPackets.RequestMap(true)), 60, "v13_blocus_carte"));
        out.add(new Step("blocus_panneau", server -> {
            Site s = blockaded == null ? site(server, home) : site(server, blockaded);
            if (s != null) CityPanel.open(player(server), s);
        }, mc -> mc.setScreen(null), 30, null));
        out.add(new Step("peage_onglet", null, mc -> click(mc, "Commerce"), 20, "v13_peage_avant"));
        out.add(new Step("peage_clic", null, mc -> click(mc, "Péage"), 20, null));
        out.add(new Step("douane_clic", null, mc -> click(mc, "Douane"), 20, "v13_peage_apres"));
        out.add(new Step("paix", server -> {
            CrusadeSavedData data = CrusadeSavedData.get(server);
            Site s = blockaded == null ? null : site(server, blockaded);
            if (s != null) log("péage et douane de son royaume : {}", fr.crusadecraft.core.city.CityTolls.rules(server, s.owner()));
            java.util.UUID me = fr.crusadecraft.core.city.WorldEconomy.myRealm(player(server));
            if (me != null) data.diplomacy().between(me, site(server, dest).owner()).setWar(false);
            fr.crusadecraft.core.city.CityBlockades.onDay(server, new Random(1));
            log("la paix : {} blocus restent", data.world().blockades().size());
            if (s != null) s.economy().add(CityGood.BLE, 200);
        }, mc -> mc.setScreen(null), 10, null));
        // ------------------------------------------------------------ P56 : les contrats
        out.add(new Step("contrat_offre", server -> {
            ServerPlayer p = player(server);
            CrusadeSavedData data = CrusadeSavedData.get(server);
            Site h = site(server, home);
            h.economy().addPurse(800);
            data.world().contracts().clear();
            long today = fr.crusadecraft.core.realm.RealmService.currentDay(server);
            Random r = new Random(4);
            for (int i = 0; i < 40 && contract == null; i++) {
                var o = fr.crusadecraft.core.city.CityContracts.post(server, h, r, today);
                if (o.isPresent() && o.get().kind == fr.crusadecraft.core.city.CityContract.Kind.COURIER) contract = o.get().id;
                else o.ifPresent(c -> data.world().contracts().remove(c));
            }
            BlockPos at = CityService.plazaOf(h);
            look(p, at.getX() + 0.5, at.getY() + 8, at.getZ() + 8.5, 180f, 20f);
            log("un pli à porter : {}", contract);
            fr.crusadecraft.core.city.WorldEconomy.send(p, false);
        }, mc -> mc.setScreen(null), 30, null));
        out.add(new Step("contrat_onglet", null, mc -> click(mc, "Contrats"), 20, "v13_contrats"));
        out.add(new Step("contrat_accepte", null, mc -> click(mc, "Accepter"), 30, "v13_contrat_accepte"));
        out.add(new Step("contrat_route", server -> {
            ServerPlayer p = player(server);
            log("un pli dans le sac : {}", p.getInventory().countItem(Items.PAPER));
        }, mc -> mc.setScreen(null), 200, "v13_contrat_indice"));
        out.add(new Step("contrat_arrive", server -> {
            ServerPlayer p = player(server);
            var c = fr.crusadecraft.core.city.CityContracts.find(server, contract).orElse(null);
            Site to = c == null ? null : site(server, c.to);
            emeralds = p.getInventory().countItem(Items.EMERALD);
            if (to == null) {
                log("le contrat n'est plus là");
                return;
            }
            log("on court à {}", to.name());
            ServerLevel level = p.serverLevel();
            level.getChunk(to.anchor().getX() >> 4, to.anchor().getZ() >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, to.anchor().getX(), to.anchor().getZ());
            look(p, to.anchor().getX() + 4.5, y + 3, to.anchor().getZ() + 4.5, 135f, 20f);
        }, null, 120, "v13_contrat_paye"));
        out.add(new Step("contrat_bilan", server -> {
            ServerPlayer p = player(server);
            log("émeraudes : {} puis {} ; contrat encore là : {}", emeralds, p.getInventory().countItem(Items.EMERALD), fr.crusadecraft.core.city.CityContracts.find(server, contract).isPresent());
        }, null, 5, null));
        out.add(new Step("escorte_offre", server -> {
            ServerPlayer p = player(server);
            CrusadeSavedData data = CrusadeSavedData.get(server);
            Site h = site(server, home), d = site(server, dest);
            data.world().contracts().clear();
            h.economy().add(CityGood.PAIN, 200);
            h.economy().addPurse(500);
            // Une ligne libre, sans caravane en route.
            data.world().lines().removeIf(l -> l.from.equals(home));
            for (Person x : data.lineage().persons()) if (x.trip() != null && x.trip().line() != null) x.setTrip(null);
            var created = fr.crusadecraft.core.city.CityCommerce.create(server, h, d.id(), CityGood.PAIN, 24, null, 4, 1, false);
            log("ligne pour l'escorte : {}", created.message().getString());
            Random r = new Random(9);
            long today = fr.crusadecraft.core.realm.RealmService.currentDay(server);
            contract = null;
            for (int i = 0; i < 60 && contract == null; i++) {
                var o = fr.crusadecraft.core.city.CityContracts.post(server, h, r, today);
                if (o.isPresent() && o.get().kind == fr.crusadecraft.core.city.CityContract.Kind.ESCORT) contract = o.get().id;
                else o.ifPresent(c -> data.world().contracts().remove(c));
            }
            BlockPos at = CityService.plazaOf(h);
            look(p, at.getX() + 0.5, at.getY() + 8, at.getZ() + 8.5, 180f, 20f);
            log("une escorte : {}", contract);
            fr.crusadecraft.core.city.WorldEconomy.send(p, false);
        }, mc -> mc.setScreen(null), 30, "v13_escorte_offre"));
        out.add(new Step("escorte_accepte", null, mc -> click(mc, "Accepter"), 60, null));
        for (int i = 1; i <= 3; i++) {
            int n = i;
            out.add(new Step("escorte_vue_" + n, server -> {
                ServerPlayer p = player(server);
                var c = fr.crusadecraft.core.city.CityContracts.find(server, contract).orElse(null);
                Entity e = c == null || c.merchant == null ? null : p.serverLevel().getEntity(c.merchant);
                if (e != null) overlook(p, e);
                else if (c != null && c.merchant != null) {
                    for (CityTravel.Pawn pawn : CityTravel.pawns(server)) {
                        if (!pawn.id().equals(c.merchant)) continue;
                        p.serverLevel().getChunk(pawn.x() >> 4, pawn.z() >> 4);
                        look(p, pawn.x() + 6.5, p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pawn.x(), pawn.z()) + 8, pawn.z() + 6.5, 135f, 35f);
                    }
                }
                log("escorte {} : marchand {} ; présence {}/{}", n, e == null ? "pas encore là" : e.blockPosition().toShortString(), c == null ? -1 : c.near, c == null ? -1 : c.total);
            }, mc -> mc.setScreen(null), 240, "v13_escorte_" + n));
        }
        out.add(new Step("escorte_fin", server -> {
            ServerPlayer p = player(server);
            var c = fr.crusadecraft.core.city.CityContracts.find(server, contract).orElse(null);
            if (c == null || c.merchant == null) return;
            emeralds = p.getInventory().countItem(Items.EMERALD);
            Entity e = p.serverLevel().getEntity(c.merchant);
            if (e != null) e.discard();
            Person m = person(server, c.merchant);
            if (m != null) CityTravel.advanceForTests(server, m, p.serverLevel().getGameTime());
            log("il arrive ; stade {} ; présence {}/{}", m == null || m.trip() == null ? "?" : m.trip().stage(), c.near, c.total);
        }, null, 100, "v13_escorte_payee"));
        out.add(new Step("escorte_bilan", server -> log("émeraudes : {} puis {}", emeralds, player(server).getInventory().countItem(Items.EMERALD)), null, 5, null));
        // ------------------------------------------------------------ P57 : le quai d'une colonie, et son vrai entrepôt
        out.add(new Step("quai_colonie", server -> {
            ServerPlayer p = player(server);
            ServerLevel level = p.serverLevel();
            fr.crusadecraft.core.nation.NationService.abdicate(p);
            Site h = site(server, home);
            int cx = h.anchor().getX(), cz = h.anchor().getZ() + 700, ground = PLATFORM + 1;
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) level.getChunk((cx >> 4) + dx, (cz >> 4) + dz);
            for (BlockPos q : BlockPos.betweenClosed(new BlockPos(cx - 20, PLATFORM, cz - 20), new BlockPos(cx + 20, PLATFORM, cz + 20))) level.setBlock(q, net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            BlockPos at = new BlockPos(cx, ground, cz);
            fr.crusadecraft.config.CrusadeConfig.SERVER.throneMustBeInTownHall.set(false);
            look(p, cx + 2.5, ground + 2, cz + 5.5, 180f, 15f);
            colony = fr.crusadecraft.compat.minecolonies.MineColoniesTestKit.createColony(level, at, p, "Port-Royal", 2);
            var realm = fr.crusadecraft.core.realm.RealmService.found(p, new fr.crusadecraft.core.realm.RealmService.FoundingRequest(at.east(), "Royaume du Port", "", "", 0, false));
            log("colonie {} ; royaume : {}", colony, realm.map(fr.crusadecraft.core.realm.Realm::name).orElse("refusé"));
            fr.crusadecraft.compat.minecolonies.MineColoniesTestKit.addWarehouse(level, colony, at.offset(6, 0, 0), 1);
            var handler = fr.crusadecraft.compat.minecolonies.MineColoniesBridge.warehouse(colony).orElse(null);
            log("entrepôt : {} cases", handler == null ? -1 : handler.getSlots());
            if (handler != null) for (int i = 0; i < 2; i++) net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(handler, new ItemStack(Items.IRON_INGOT, 64), false);
            realm.ifPresent(r -> r.setGold(2000));
            log("panneau : {}", CityPanel.openHere(p).message().getString());
        }, mc -> mc.setScreen(null), 40, "v13_quai_panneau"));
        out.add(new Step("quai_reserves", null, mc -> click(mc, "Réserves"), 20, "v13_quai_reserves"));
        out.add(new Step("quai_ligne", server -> {
            var dock = fr.crusadecraft.core.city.CityDocks.ensure(server, colony).orElse(null);
            Site d = site(server, dest);
            if (dock == null || d == null) {
                log("pas de quai");
                return;
            }
            log("ligne : {}", fr.crusadecraft.core.city.CityCommerce.create(server, dock, d.id(), CityGood.FER, 32, CityGood.PAIN, 1, 1, false).message().getString());
            d.economy().add(CityGood.PAIN, 100);
            fr.crusadecraft.core.city.CityCommerce.pulse(server);
            fr.crusadecraft.core.city.CityDocks.pass(server, dock);
            logShelf("après le départ");
            for (fr.crusadecraft.core.city.CityLine l : CrusadeSavedData.get(server).world().lines()) if (l.from.equals(dock.id())) log("statut de la ligne : {}", l.status);
            CityPanel.open(player(server), dock);
        }, mc -> mc.setScreen(null), 40, null));
        out.add(new Step("quai_commerce", null, mc -> click(mc, "Commerce"), 20, "v13_quai_commerce"));
        out.add(new Step("quai_caravane", server -> {
            var dock = fr.crusadecraft.core.city.CityDocks.ensure(server, colony).orElse(null);
            for (Person x : CrusadeSavedData.get(server).lineage().persons()) {
                if (x.trip() != null && dock != null && dock.id().equals(x.trip().home()) && x.trip().leader() == null) caravanner = x.id();
            }
            log("le marchand de la colonie : {}", caravanner == null ? "aucun" : person(server, caravanner).name());
        }, mc -> mc.setScreen(null), 120, "v13_quai_caravane"));
        out.add(new Step("quai_retour", server -> {
            ServerPlayer p = player(server);
            var dock = fr.crusadecraft.core.city.CityDocks.ensure(server, colony).orElse(null);
            Person m = caravanner == null ? null : person(server, caravanner);
            Entity e = m == null ? null : p.serverLevel().getEntity(m.id());
            if (e != null) e.discard();
            long now = p.serverLevel().getGameTime();
            for (int i = 0; i < 3 && m != null && m.trip() != null; i++) {
                CityTravel.advanceForTests(server, m, now);
                log("étape : {}", m.trip() == null ? "rentré" : m.trip().stage() + ", rapporte " + m.trip().back() + " " + m.trip().backAmount());
            }
            if (dock != null) {
                log("sur le quai : pain {}, bourse {}", dock.economy().stock(CityGood.PAIN), dock.economy().purse());
                fr.crusadecraft.core.city.CityDocks.pass(server, dock);
            }
            logShelf("au retour");
            if (dock != null) CityPanel.open(p, dock);
        }, mc -> mc.setScreen(null), 40, null));
        out.add(new Step("quai_reserves_apres", null, mc -> click(mc, "Réserves"), 20, "v13_quai_apres"));
        // ------------------------------------------------------------ P58 : un marché devient une ville
        out.add(new Step("marche_mur", server -> {
            ServerPlayer p = player(server);
            CrusadeSavedData data = CrusadeSavedData.get(server);
            Site pick = null;
            for (Site m : data.world().markets()) {
                if (fr.crusadecraft.core.city.CityDocks.isDockId(m.id())) continue;
                pick = m;
            }
            if (pick == null) {
                log("aucun marché");
                return;
            }
            pick.setProsperity(fr.crusadecraft.core.city.CityGrowth.required());
            pick.economy().add(CityGood.FER, 77);
            market = pick.id();
            province = pick.province();
            log("{} est mûr ; bourse {}", pick.name(), pick.economy().purse());
            fr.crusadecraft.config.CrusadeConfig.SERVER.townBlocksPerSecond.set(3000);
            ServerLevel level = p.serverLevel();
            BlockPos seat = pick.anchor();
            level.getChunk(seat.getX() >> 4, seat.getZ() >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, seat.getX(), seat.getZ());
            look(p, seat.getX() + 50.5, y + 70, seat.getZ() + 50.5, 135f, 40f);
            fr.crusadecraft.core.city.WorldEconomy.send(p, false);
        }, mc -> mc.setScreen(null), 60, null));
        out.add(new Step("marche_ouvre", server -> {
            int opened = fr.crusadecraft.core.city.CityGrowth.tick(server);
            log("chantiers ouverts : {}", opened);
            for (Site s : CrusadeSavedData.get(server).world().sites()) if (s.grown()) log("{} : {} blocs à poser", s.name(), s.town() == null ? -1 : s.town().total());
        }, mc -> mc.setScreen(null), 200, "v13_marche_chantier"));
        out.add(new Step("marche_fini", null, null, 20, "v13_marche_ville", () -> {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) return true;
            for (Site s : CrusadeSavedData.get(server).world().sites()) if (s.grown()) return s.built() && !s.buildings().isEmpty();
            return true;
        }));
        out.add(new Step("marche_bilan", server -> {
            CrusadeSavedData data = CrusadeSavedData.get(server);
            for (Site s : data.world().sites()) {
                if (!s.grown()) continue;
                log("{} : finie {} ; {} bâtiments, {} habitants", s.name(), s.built(), s.buildings().size(), CityService.people(server, s));
                int before = s.economy().stock(CityGood.FER);
                fr.crusadecraft.core.city.CityMarkets.onDay(server, new Random(3));
                log("le marché a passé la main : encore là {} ; fer {} puis {}", market != null && data.world().isMarket(market), before, s.economy().stock(CityGood.FER));
            }
            fr.crusadecraft.config.CrusadeConfig.SERVER.townBlocksPerSecond.set(80);
        }, null, 40, "v13_marche_habitee"));
        out.add(new Step("fin", server -> {
            ServerPlayer p = player(server);
            fr.crusadecraft.core.nation.NationService.abdicate(p);
            try {
                Files.deleteIfExists(fr.crusadecraft.core.nation.NationBook.folder().resolve("marche_de_libreval.json"));
            } catch (IOException ignored) {
                // Il resterait une nation de plus dans le dossier d'essai.
            }
            log("terminé, sans plantage si cette ligne s'affiche");
        }, mc -> mc.setScreen(null), 10, null));
        return out;
    }

    /** Ce que contient vraiment l'entrepôt de la colonie. */
    private static void logShelf(String when) {
        var handler = colony == null ? null : fr.crusadecraft.compat.minecolonies.MineColoniesBridge.warehouse(colony).orElse(null);
        if (handler == null) {
            log("{} : pas d'entrepôt chargé", when);
            return;
        }
        int iron = 0, bread = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack x = handler.getStackInSlot(i);
            if (x.is(Items.IRON_INGOT)) iron += x.getCount();
            if (x.is(Items.BREAD)) bread += x.getCount();
        }
        log("{} : dans l'entrepôt, {} fer et {} pain", when, iron, bread);
    }

    private static Site byRef(MinecraftServer server, String ref) {
        for (Site s : CrusadeSavedData.get(server).world().sites()) if (s.imported() && ref.equals(s.importedRef())) return s;
        return null;
    }

    /** Pose cette ville si elle n'y est pas encore, et donne une fonction à ses bâtiments. */
    private static void place(List<Step> out, String label, String ref, int offset) {
        out.add(new Step("pose_" + label, server -> {
            pasted = false;
            if (byRef(server, ref) != null) {
                pasted = true;
                return;
            }
            ServerPlayer p = player(server);
            ServerLevel level = p.serverLevel();
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(fr.crusadecraft.registry.ModItems.PLAN.get()));
            log("{}", fr.crusadecraft.core.build.plan.PlanTool.choose(p, ref).message().getString());
            int x = origin.getX() + offset, z = origin.getZ() + 60;
            for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) level.getChunk((x >> 4) + dx, (z >> 4) + dz);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            look(p, x + 0.5, y + 60, z + 120.5, 180f, 25f);
            fr.crusadecraft.core.build.plan.PlanTool.paste(p, p.getMainHandItem(), new BlockPos(x, y, z));
            log("{} : pose lancée", label);
        }, mc -> {
            mc.setScreen(null);
            fr.crusadecraft.client.map.PlanControl.reset();
        }, 40, null, () -> {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) server.execute(() -> {
                Site s = byRef(server, ref);
                if (s != null && !fr.crusadecraft.core.build.plan.Journals.pasting(server, s.id())) pasted = true;
            });
            return pasted;
        }));
        out.add(new Step("fonctions_" + label, server -> {
            ServerPlayer p = player(server);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            Site s = byRef(server, ref);
            if (s == null) {
                log("ATTENTION : {} n'a pas pu être posée", label);
                return;
            }
            int i = 0;
            for (CityBuilding b : s.buildings()) {
                if (b.function() == null) CityService.define(p, s.id(), b, i < MIX.length ? MIX[i] : BuildingFunction.MAISON, "");
                i++;
            }
            log("{} : {} bâtiments, {} au registre", label, s.buildings().size(), CityService.people(server, s));
        }, mc -> mc.setScreen(null), 40, null));
    }

    private static net.minecraft.nbt.CompoundTag tagOf(String key, String value) {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putString(key, value);
        return t;
    }
}
