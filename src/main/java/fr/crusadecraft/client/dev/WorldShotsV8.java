package fr.crusadecraft.client.dev;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.client.screen.UrbanScreen;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.plan.UrbanParams;
import fr.crusadecraft.core.build.plan.UrbanPresets;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.UrbanPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * -Pshots_v8=true (P60) : l'atelier des villes dans le vrai jeu. Les onglets, les préréglages, une retouche à la main, puis une ville qu'on prend et qu'on pose pour de vrai,
 * jusqu'à ce que le chantier soit fini : c'est là seulement qu'on sait si les rues, les murs et l'eau tombent juste.
 */
final class WorldShotsV8 {
    private record Step(String name, Consumer<MinecraftServer> server, Consumer<Minecraft> client, int ticks, String shot, BooleanSupplier until) {
        Step(String name, Consumer<MinecraftServer> server, Consumer<Minecraft> client, int ticks, String shot) {
            this(name, server, client, ticks, shot, null);
        }
    }

    private static List<Step> steps;
    private static int index = -1, wait, waited;
    private static boolean acted;
    private static BlockPos ground;
    private static long started;

    private WorldShotsV8() {}

    private static void log(String text, Object... args) {
        CrusadeCraft.LOGGER.info("WorldShots v8 : " + text, args);
    }

    private static UrbanScreen screen(Minecraft mc) {
        return mc.screen instanceof UrbanScreen s ? s : null;
    }

    private static void reportStats(Minecraft mc, String what) {
        UrbanScreen s = screen(mc);
        if (s == null) {
            log("{} : pas d'écran", what);
            return;
        }
        int[] st = s.benchStats();
        if (st.length >= 10) log("{} : {} habitants voulus, {} maisons sur {}, {} bâtiments en tout, {} îlots, {} portes, {} tours, {} ponts, {} sans place, {} blocs de large", what, st[0], st[1], st[2], st[3], st[4], st[5], st[6], st[7], st[8], st[9]);
    }

    private static BooleanSupplier idle() {
        return () -> {
            UrbanScreen s = screen(Minecraft.getInstance());
            return s == null || s.benchIdle();
        };
    }

    /** La ville qu'on a posée : à son nom, puisque la ville enregistrée n'a plus les données du chantier. */
    private static Site town(MinecraftServer server) {
        for (Site s : CrusadeSavedData.get(server).world().sites()) if ("Vieille-Forge".equals(s.name())) return s;
        return null;
    }

    private static List<Step> build() {
        List<Step> out = new ArrayList<>();
        out.add(new Step("debut", server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            WorldEditService.grantForTests(p.getUUID(), true);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(fr.crusadecraft.registry.ModItems.PLAN.get()));
            server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        }, mc -> {
            mc.setScreen(null);
            CrusadeNetwork.toServer(new UrbanPackets.Request(UrbanPackets.OPEN, new CompoundTag(), ""));
        }, 40, null, () -> screen(Minecraft.getInstance()) != null));
        out.add(new Step("graine", null, mc -> screen(mc).benchSeed(2026), 30, "v15_atelier_general", idle()));
        String[] names = {"trace", "quartiers", "defenses", "eau", "campagne", "batiments", "reglages"};
        for (int i = 0; i < names.length; i++) {
            int tab = i + 1;
            out.add(new Step("onglet_" + names[i], null, mc -> screen(mc).benchTab(tab), 10, "v15_onglet_" + names[i]));
        }
        out.add(new Step("general", null, mc -> screen(mc).benchTab(0), 5, null));
        for (UrbanPresets.Preset preset : UrbanPresets.all()) {
            String n = preset.name().toLowerCase(java.util.Locale.ROOT);
            out.add(new Step("prereglage_" + n, null, mc -> {
                screen(mc).benchPreset(preset);
                screen(mc).benchSeed(2026);
            }, 40, "v15_plan_" + n, idle()));
            out.add(new Step("bilan_" + n, null, mc -> reportStats(mc, n), 2, null));
        }
        // Une retouche à la main : ça se voit sur le plan, et ça ne casse rien.
        out.add(new Step("retouche", null, mc -> {
            screen(mc).benchPreset(UrbanPresets.Preset.VILLE_RIVIERE);
            screen(mc).benchSeed(2026);
        }, 40, "v15_avant_retouche", idle()));
        out.add(new Step("retouche_retire", null, mc -> {
            for (int i = 0; i < 6; i++) screen(mc).benchClickBuilding(i * 3 + 8, 1);
        }, 40, "v15_apres_retouche", idle()));
        out.add(new Step("retouche_bilan", null, mc -> reportStats(mc, "après retouche"), 2, null));
        out.add(new Step("enregistrer", null, mc -> screen(mc).benchSave("essai de l'atelier"), 20, null));
        // Une ville réelle, à poser : pas trop grande, pour que l'essai reste court, avec de quoi tout voir.
        out.add(new Step("choix", null, mc -> {
            UrbanScreen s = screen(mc);
            s.benchPreset(UrbanPresets.Preset.VILLE_RIVIERE);
            s.benchName("Vieille-Forge");
            s.benchSet(UrbanParams.HABITANTS, 240);
            s.benchSet(UrbanParams.CITADELLE, 1);
            s.benchSet(UrbanParams.TAILLE_CITADELLE, 40);
            s.benchSet(UrbanParams.FORETS, 25);
            s.benchSet(UrbanParams.SURFACE_CHAMPS, 40);
            s.benchSeed(2026);
        }, 40, "v15_ville_choisie", idle()));
        out.add(new Step("prend", null, mc -> {
            reportStats(mc, "la ville qu'on pose");
            screen(mc).benchTake();
        }, 40, null, () -> screen(Minecraft.getInstance()) == null));
        out.add(new Step("place", server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = p.serverLevel();
            BlockPos at = p.blockPosition().offset(200, 0, 200);
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) level.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) - 1;
            ground = new BlockPos(at.getX(), y, at.getZ());
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.connection.teleport(at.getX() + 0.5, y + 90, at.getZ() + 110.5, 180f, 45f);
            log("on pose à {}", ground.toShortString());
        }, null, 60, "v15_fantome"));
        out.add(new Step("pose", server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            started = System.currentTimeMillis();
            fr.crusadecraft.core.build.plan.PlanTool.paste(p, p.getMainHandItem(), ground);
        }, null, 200, "v15_chantier_debut"));
        out.add(new Step("attend", null, null, 40, "v15_ville_finie", () -> {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) return true;
            Site s = town(server);
            return s != null && !fr.crusadecraft.core.build.plan.Journals.pasting(server, s.id());
        }));
        out.add(new Step("bilan", server -> {
            Site s = town(server);
            if (s == null) return;
            log("{} : posée en {} s ; {} pièces, {} cases de rues, {} habitants, {} bâtiments reconnus", s.name(), (System.currentTimeMillis() - started) / 1000, s.town().pieces().size(), s.town().streets().length, fr.crusadecraft.core.city.CityService.people(server, s), s.buildings().size());
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.connection.teleport(s.anchor().getX() + 0.5, s.anchor().getY() + 150, s.anchor().getZ() + 0.5, 0f, 89f);
        }, mc -> mc.options.hideGui = true, 160, "v15_vue_du_ciel"));
        out.add(new Step("oblique", server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            Site s = town(server);
            if (s != null) p.connection.teleport(s.anchor().getX() + 90.5, s.anchor().getY() + 45, s.anchor().getZ() + 90.5, 135f, 22f);
        }, null, 100, "v15_vue_oblique"));
        out.add(new Step("oblique_2", server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            Site s = town(server);
            if (s != null) p.connection.teleport(s.anchor().getX() - 70.5, s.anchor().getY() + 30, s.anchor().getZ() + 60.5, 230f, 15f);
        }, null, 100, "v15_vue_oblique_2"));
        out.add(new Step("rue", server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            Site s = town(server);
            if (s == null) return;
            long[] streets = s.town().streets();
            long mid = streets[streets.length / 3];
            int x = s.anchor().getX() + (int) (mid >> 32), z = s.anchor().getZ() + (int) mid;
            p.connection.teleport(x + 0.5, p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 2, z + 0.5, 30f, 5f);
        }, null, 80, "v15_dans_la_rue"));
        out.add(new Step("fin", null, mc -> log("terminé, sans plantage si cette ligne s'affiche"), 5, null));
        return out;
    }

    static boolean tick(Minecraft mc, BiConsumer<Minecraft, String> grab) {
        if (steps == null) {
            steps = build();
            // -Pshots_only=pose : droit à la ville posée, sans les onglets ni les préréglages.
            if ("pose".equals(System.getProperty("crusadecraft.worldshots.v5only", ""))) {
                int first = 0, choice = 0;
                for (int i = 0; i < steps.size(); i++) {
                    if (steps.get(i).name().equals("graine")) first = i;
                    if (steps.get(i).name().equals("choix")) choice = i;
                }
                List<Step> short_ = new ArrayList<>(steps.subList(0, first));
                short_.addAll(steps.subList(choice, steps.size()));
                steps = short_;
            }
        }
        if (wait > 0) {
            wait--;
            return false;
        }
        if (index >= 0 && acted) {
            Step done = steps.get(index);
            if (done.until() != null && !done.until().getAsBoolean() && waited++ < 36_000) return false;
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
        if (s.server() != null) {
            server.execute(() -> {
                try {
                    s.server().accept(server);
                } catch (RuntimeException e) {
                    CrusadeCraft.LOGGER.error("WorldShots v8 : l'étape {} a échoué côté serveur", s.name(), e);
                }
            });
        }
        if (s.client() != null) {
            try {
                s.client().accept(mc);
            } catch (RuntimeException e) {
                CrusadeCraft.LOGGER.error("WorldShots v8 : l'étape {} a échoué côté client", s.name(), e);
            }
        }
        wait = s.ticks();
        acted = true;
        return false;
    }
}
