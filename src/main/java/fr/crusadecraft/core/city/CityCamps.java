package fr.crusadecraft.core.city;

import fr.crusadecraft.compat.hyw.Soldiers;
import fr.crusadecraft.core.build.Banners;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.war.WarMapService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Le camp d'un blocus (P59) : des soldats en cercle autour de la ville, aux couleurs de celui qui la coupe du monde. Ils ne sont là que si un joueur
 * approche : le blocus lui-même se compte dans les réserves, partout et tout le temps. Ils ne s'en prennent qu'aux joueurs en guerre contre leur royaume, et à
 * qui les frappe : un voyageur neutre passe, et les habitants ne risquent rien tant que personne ne sort se battre. Tous tués dans la journée, le blocus est brisé.
 */
public final class CityCamps {
    public static final String TAG = "crusadecraft_blocus";
    private static final String SITE = TAG + "_ville", POST = TAG + "_poste";
    /** On passe les voir tous les tant de ticks. */
    public static final int EVERY = 100;
    /** Un joueur à tant de blocs du cercle : ils sont là. */
    static final int SHOW = 96;
    /** Assez loin des murs pour que les gardes, qui voient à 14 blocs, ne sortent pas d'eux-mêmes. */
    static final int MARGIN = 24;
    /** Au moins, au plus, et tous les tant de blocs. */
    static final int MIN_MEN = 8, MAX_MEN = 24, SPACING = 18;
    /** Ils ne s'éloignent pas de leur poste de plus de tant de blocs, sauf pour se battre. */
    static final int LEASH = 4;
    /** Au-delà, ils y reviennent ; bien au-delà, on les y remet. */
    private static final int ASTRAY = 16, LOST = 64;

    private CityCamps() {}

    /** Le rayon du cercle, pour une ville de cette largeur. */
    public static int ring(int extent) {
        return Math.max(24, extent / 2) + MARGIN;
    }

    /** Combien de soldats pour le tenir. */
    public static int men(int extent) {
        int around = (int) (2 * Math.PI * ring(extent) / SPACING);
        return Math.max(MIN_MEN, Math.min(MAX_MEN, around));
    }

    /** La place de ce poste sur le cercle, par rapport au centre de la ville. */
    public static int[] post(int index, int men, int radius, double nudge) {
        double angle = 2 * Math.PI * (index + nudge) / men;
        return new int[]{(int) Math.round(Math.cos(angle) * radius), (int) Math.round(Math.sin(angle) * radius)};
    }

    private static UUID uuidIn(Entity e, String prefix) {
        for (String tag : e.getTags()) {
            if (!tag.startsWith(prefix + ":")) continue;
            try {
                return UUID.fromString(tag.substring(prefix.length() + 1));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static int postOf(Entity e) {
        for (String tag : e.getTags()) {
            if (!tag.startsWith(POST + ":")) continue;
            try {
                return Integer.parseInt(tag.substring(POST.length() + 1));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    /** Tous les soldats de camp d'une ville, s'il y en a. Pour les bancs d'essai. */
    public static List<Mob> soldiers(MinecraftServer server, UUID site) {
        List<Mob> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof Mob m && m.isAlive() && e.getTags().contains(TAG) && site.equals(uuidIn(e, SITE))) out.add(m);
            }
        }
        return out;
    }

    /** Ce soldat n'a pas à viser celui-là : ni un voyageur en paix, ni un habitant, à moins qu'on ne l'ait frappé. */
    public static boolean refuses(LivingEntity soldier, LivingEntity target) {
        if (target == null || !soldier.getTags().contains(TAG) || target == soldier.getLastHurtByMob()) return false;
        if (!(target instanceof ServerPlayer p)) return true;
        UUID realm = uuidIn(soldier, TAG);
        return realm == null || !CityGuards.enemy(p.server, p, realm);
    }

    /** Tous les {@link #EVERY} ticks : ceux qui n'ont plus rien à faire là s'en vont, ceux qui manquent arrivent, si quelqu'un est là pour les voir. Renvoie le nombre d'arrivants. */
    public static int tick(MinecraftServer server) {
        return tick(server, false);
    }

    /** Pour les bancs d'essai : les joueurs fictifs ne sont pas dans la liste de ceux qui voient. */
    public static int tickForTests(MinecraftServer server) {
        return tick(server, true);
    }

    private static int tick(MinecraftServer server, boolean always) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        Map<UUID, CityBlockades.Blockade> blockades = data.world().blockades();
        Map<UUID, List<Mob>> present = new HashMap<>();
        // D'abord la liste, ensuite les départs : retirer une entité pendant qu'on parcourt celles du monde en fait sauter d'autres, au hasard.
        List<Mob> found = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof Mob m && e.getTags().contains(TAG)) found.add(m);
            }
        }
        for (Mob m : found) {
            UUID site = uuidIn(m, SITE);
            if (site == null || !blockades.containsKey(site)) m.discard();
            else if (m.isAlive()) present.computeIfAbsent(site, k -> new ArrayList<>()).add(m);
        }
        int raised = 0;
        for (Map.Entry<UUID, CityBlockades.Blockade> e : blockades.entrySet()) {
            Site s = data.world().site(e.getKey()).orElse(null);
            ServerLevel level = s == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, s.dimension()));
            if (level != null) raised += camp(level, data, s, e.getValue(), present.getOrDefault(s.id(), List.of()), always);
        }
        return raised;
    }

    private static int camp(ServerLevel level, CrusadeSavedData data, Site s, CityBlockades.Blockade b, List<Mob> here, boolean always) {
        int radius = ring(s.extent()), men = men(s.extent());
        boolean[] taken = new boolean[men];
        int standing = 0;
        for (Mob m : here) {
            int i = postOf(m);
            if (i < 0 || i >= men || taken[i]) {
                m.discard();
                continue;
            }
            taken[i] = true;
            standing++;
            hold(level, s, m, i, men, radius);
        }
        if (!always && !witnessed(level, s, radius)) return 0;
        int raised = 0;
        for (int i = 0; i < men && standing < men - b.fallen(); i++) {
            if (taken[i] || !raise(level, data, s, b, i, men, radius)) continue;
            standing++;
            raised++;
        }
        return raised;
    }

    private static boolean witnessed(ServerLevel level, Site s, int radius) {
        double range = (double) (radius + SHOW) * (radius + SHOW);
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && s.distanceSqr(p.getX(), p.getZ()) <= range) return true;
        }
        return false;
    }

    /** Il retourne à son poste une fois le combat fini. */
    private static void hold(ServerLevel level, Site s, Mob m, int i, int men, int radius) {
        int[] off = post(i, men, radius, 0);
        int x = s.anchor().getX() + off[0], z = s.anchor().getZ() + off[1];
        BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        if (!m.hasRestriction()) m.restrictTo(at, LEASH);
        if (m.getTarget() != null) return;
        double d = Math.hypot(m.getX() - x, m.getZ() - z);
        if (d > LOST && level.isLoaded(at)) m.teleportTo(x + 0.5, at.getY(), z + 0.5);
        else if (d > ASTRAY) m.getNavigation().moveTo(x + 0.5, at.getY(), z + 0.5, 1.0);
    }

    /** Un soldat de plus à ce poste : sur la terre ferme, légèrement à côté s'il le faut. */
    private static boolean raise(ServerLevel level, CrusadeSavedData data, Site s, CityBlockades.Blockade b, int i, int men, int radius) {
        for (int attempt = 0; attempt < 4; attempt++) {
            int[] off = post(i, men, radius, attempt * 0.15);
            int x = s.anchor().getX() + off[0], z = s.anchor().getZ() + off[1];
            // Là où le monde tourne vraiment : au bord de la distance de vue, il resterait figé, et le prochain passage ne le verrait pas.
            if (!level.isPositionEntityTicking(new BlockPos(x, s.anchor().getY(), z))) return false;
            BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.getFluidState(at.below()).isEmpty()) continue;
            Mob m = soldier(level, i);
            if (m == null) return false;
            float facing = (float) Math.toDegrees(Math.atan2(off[0], -off[1]));
            m.moveTo(x + 0.5, at.getY(), z + 0.5, facing, 0);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null, null);
            dress(m, data, b, i);
            m.setCustomName(Component.translatable("crusadecraft.city.blockade.soldier", WarMapService.name(data, b.by())));
            m.setPersistenceRequired();
            m.restrictTo(at, LEASH);
            m.addTag(TAG);
            m.addTag(TAG + ":" + b.by());
            m.addTag(SITE + ":" + s.id());
            m.addTag(POST + ":" + i);
            return level.addFreshEntity(m);
        }
        return false;
    }

    /** Les soldats des guerres du mod ; en paisible, où les monstres s'effacent aussitôt, des golems, qui restent. */
    private static Mob soldier(ServerLevel level, int i) {
        Mob m = Soldiers.enemy(level, i);
        if (level.getDifficulty() != Difficulty.PEACEFUL || !(m instanceof Monster)) return m;
        IronGolem golem = EntityType.IRON_GOLEM.create(level);
        if (golem != null) golem.setPlayerCreated(true);
        return golem;
    }

    /** Un sur quatre porte la bannière de son royaume. Personne n'est chef de patrouille (ce serait le mauvais présage à sa mort), et rien ne se ramasse sur eux. */
    private static void dress(Mob m, CrusadeSavedData data, CityBlockades.Blockade b, int i) {
        if (m instanceof Raider r) r.setCanJoinRaid(false);
        if (m instanceof PatrollingMonster p) p.setPatrolLeader(false);
        if (m.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof BannerItem) m.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        if (i % 4 == 0 && !(m instanceof IronGolem) && m.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(BannerBlock.byColor(Banners.dye(WarMapService.color(data, b.by()))).asItem()));
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) m.setDropChance(slot, 0f);
    }

    /** Un soldat tombe : sous les coups d'un adversaire, c'est un homme de moins jusqu'au matin ; autrement (une chute, un monstre), on le remplace. */
    public static void onDeath(MinecraftServer server, LivingEntity dead, Entity killer) {
        if (!dead.getTags().contains(TAG) || killer == null) return;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        UUID site = uuidIn(dead, SITE), realm = uuidIn(dead, TAG);
        Site s = site == null ? null : data.world().site(site).orElse(null);
        if (s == null) return;
        boolean counts = killer instanceof ServerPlayer p ? !Objects.equals(WorldEconomy.myRealm(p), realm) : data.lineage().person(killer.getUUID()).isPresent();
        if (!counts) return;
        int left = CityBlockades.soldierKilled(server, s, men(s.extent()));
        if (left >= 0 && killer instanceof ServerPlayer p) {
            p.displayClientMessage(left == 0 ? Component.translatable("crusadecraft.city.blockade.broken", s.name()) : Component.translatable("crusadecraft.city.blockade.left", left, s.name()), true);
        }
    }
}
