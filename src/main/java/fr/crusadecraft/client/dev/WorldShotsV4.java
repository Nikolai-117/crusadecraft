package fr.crusadecraft.client.dev;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.build.plan.NationStyle;
import fr.crusadecraft.core.build.plan.PlanLibrary;
import fr.crusadecraft.core.build.plan.PlanTool;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.PlanPackets;
import fr.crusadecraft.item.PlanItem;
import fr.crusadecraft.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Le banc d'essai en monde réel de la phase 34 ({@code ./gradlew runWorldShots -Pshots_v4=true}) : une ville de chaque
 * style, bâtie par l'éditeur à côté des autres, photographiée de haut ; puis le catalogue du plan d'architecte et son
 * cadre d'aperçu.
 */
final class WorldShotsV4 {
    record Step(String name, Consumer<MinecraftServer> server, Consumer<Minecraft> client, int ticks, String shot) {}

    private static List<Step> steps;
    private static int index = -1, wait;
    private static boolean acted;
    private static BlockPos origin;
    private static final Map<NationStyle, BlockPos> TOWNS = new EnumMap<>(NationStyle.class);
    /** Écart entre deux villes : une ville fortifiée en couvre près de deux cents blocs. */
    private static final int SPACING = 420;

    private WorldShotsV4() {}

    static boolean tick(Minecraft mc, BiConsumer<Minecraft, String> grab) {
        if (steps == null) steps = build();
        if (wait > 0) {
            wait--;
            return false;
        }
        if (index >= 0 && acted) {
            Step done = steps.get(index);
            if (done.shot() != null) grab.accept(mc, done.shot());
            acted = false;
        }
        index++;
        if (index >= steps.size()) return true;
        Step s = steps.get(index);
        MinecraftServer server = mc.getSingleplayerServer();
        CrusadeCraft.LOGGER.info("WorldShots v4 : étape {}", s.name());
        if (s.server() != null) server.execute(() -> {
            try {
                s.server().accept(server);
            } catch (RuntimeException e) {
                CrusadeCraft.LOGGER.error("WorldShots v4 : l'étape {} a échoué côté serveur", s.name(), e);
            }
        });
        if (s.client() != null) s.client().accept(mc);
        wait = s.ticks();
        acted = true;
        return false;
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static void look(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.connection.teleport(x, y, z, yaw, pitch);
    }

    private static List<Step> build() {
        List<Step> out = new ArrayList<>();
        out.add(new Step("depart", server -> {
            ServerPlayer p = player(server);
            WorldEditService.grantForTests(p.getUUID(), true);
            origin = p.blockPosition();
            for (NationStyle s : NationStyle.values()) CrusadeCraft.LOGGER.info("WorldShots v4 : style {} → pack {}", s, PlanLibrary.packFor(s).orElse("aucun"));
        }, mc -> mc.setScreen(null), 20, null));
        // -Pshots_quick=true : une seule ville (pour regarder l'outil et le catalogue sans attendre les six).
        boolean quick = Boolean.getBoolean("crusadecraft.worldshots.v4quick");
        for (NationStyle style : quick ? new NationStyle[]{NationStyle.EUROPEENNE} : NationStyle.values()) {
            out.add(new Step("ville_" + style.name().toLowerCase(), server -> buildTown(server, style), null, 60, null));
            out.add(new Step("haut_" + style.name().toLowerCase(), server -> overlook(server, style, 95, 70f), null, 260,
                    "v4_ville_" + style.name().toLowerCase()));
            out.add(new Step("rue_" + style.name().toLowerCase(), server -> overlook(server, style, 28, 30f), null, 160,
                    "v4_rue_" + style.name().toLowerCase()));
        }
        out.add(new Step("outil", server -> {
            ServerPlayer p = player(server);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PLAN.get()));
            BlockPos town = TOWNS.getOrDefault(NationStyle.EUROPEENNE, origin);
            look(p, town.getX() - 70.5, town.getY() + 12, town.getZ() + 0.5, -90f, 35f);
        }, mc -> CrusadeNetwork.toServer(new PlanPackets.Request("")), 60, "v4_catalogue"));
        out.add(new Step("outil_choix", server -> {
            String pack = PlanLibrary.packFor(NationStyle.JAPONAISE).orElse(PlanLibrary.packFor(NationStyle.EUROPEENNE).orElse(""));
            String ref = PlanLibrary.entries(pack).stream().map(PlanLibrary.Entry::ref).filter(r -> r.contains("townhall")).findFirst().orElse("");
            CrusadeCraft.LOGGER.info("WorldShots v4 : outil — {}", PlanTool.choose(player(server), ref).message().getString());
        }, mc -> mc.setScreen(null), 60, "v4_apercu"));
        out.add(new Step("outil_pose", server -> {
            ServerPlayer p = player(server);
            PlanItem.aim(p).ifPresent(hit -> PlanTool.paste(p, p.getMainHandItem(), hit.getBlockPos()));
        }, null, 3, "v4_pose"));
        out.add(new Step("fin", null, mc -> mc.setScreen(null), 10, null));
        return out;
    }

    /** Un royaume de ce style, une ville de la taille d'un bourg (ou d'une ville), bâtie d'un coup par l'éditeur. */
    private static void buildTown(MinecraftServer server, NationStyle style) {
        ServerPlayer p = player(server);
        ServerLevel level = p.serverLevel();
        int x = origin.getX() + 320 + style.ordinal() * SPACING, z = origin.getZ() + 40;
        for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) level.getChunk((x >> 4) + dx, (z >> 4) + dz);
        AiRealm ai = WorldEditService.addRealm(server, level, "Essai " + style.name().toLowerCase(), "", TitleRank.DUCHE, 0x7B2D8B, AiFaith.FIDELE,
                AiPersonality.PACIFIQUE, x + 200, z + 200);
        ai.setStyle(style);
        ai.discover(p.getUUID(), 0);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        AiProvince city = WorldEditService.addCity(server, level, ai.id(), style.name().toLowerCase(), WorldEditService.CitySize.VILLE, x, z).orElse(null);
        if (city == null) {
            CrusadeCraft.LOGGER.warn("WorldShots v4 : ville {} refusée", style);
            return;
        }
        var out = SiteService.buildTown(p, city.id());
        CrusadeCraft.LOGGER.info("WorldShots v4 : {} — {}", style, out.message().getString());
        Site site = SiteService.townOf(server, city.id()).orElse(null);
        if (site == null) return;
        for (int i = 0; i < 200 && !site.built(); i++) SiteService.advanceForTests(level, site, 50_000);
        TOWNS.put(style, new BlockPos(x, y, z));
        CrusadeCraft.LOGGER.info("WorldShots v4 : {} achevée={} ({} bâtiments, emprise {})", style, site.built(), site.town().pieces().size(),
                site.town().extent());
    }

    private static void overlook(MinecraftServer server, NationStyle style, int height, float pitch) {
        BlockPos t = TOWNS.get(style);
        if (t == null) return;
        double back = height / Math.tan(Math.toRadians(pitch));
        look(player(server), t.getX() + 0.5, t.getY() + height, t.getZ() + back + 0.5, 180f, pitch);
    }
}
