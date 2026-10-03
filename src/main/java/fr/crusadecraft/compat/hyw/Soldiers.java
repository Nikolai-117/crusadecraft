package fr.crusadecraft.compat.hyw;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.raid.Raider;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Les soldats des sièges et des raids. Avec Hundred Years War, ses bandits forment les troupes ennemies et ses
 * soldats, rattachés au joueur, ses levées ; on ne passe que par le registre des entités et une méthode
 * publique (le propriétaire), sans dépendre de ses classes. Sans lui, pillards et vindicateurs vanilla font
 * l'ennemi, et des golems de fer vos hommes d'armes.
 */
public final class Soldiers {
    private static final String HYW = "hundred_years_war";
    private static final String[] ENEMIES = {"bandit_soldier", "bandit_archer", "bandit_shield_axeman", "bandit_crossbowman", "bandit_soldier_elite"};
    private static final String[] ALLIES = {"warrior", "spear_man", "archer", "crossbowman", "shieldman"};
    private static boolean ownerBroken;

    private Soldiers() {}

    public static boolean hyw() {
        return CrusadeConfig.SERVER.hywTroops.get() && ModList.get().isLoaded(HYW);
    }

    private static Mob create(ServerLevel level, String path) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(HYW + ":" + path));
        if (type == null) return null;
        Entity e = type.create(level);
        if (e instanceof Mob m) return m;
        if (e != null) e.discard();
        return null;
    }

    /** Un soldat ennemi (garnison, raid). */
    public static Mob enemy(ServerLevel level, int index) {
        if (hyw()) {
            Mob m = create(level, ENEMIES[Math.floorMod(index, ENEMIES.length)]);
            if (m != null) return m;
        }
        Mob m = Math.floorMod(index, 3) == 1 ? EntityType.PILLAGER.create(level) : EntityType.VINDICATOR.create(level);
        if (m instanceof Raider raider) raider.setCanJoinRaid(false);
        return m;
    }

    /** Un homme d'armes du joueur (levées en campagne). */
    public static Mob ally(ServerLevel level, ServerPlayer owner, int index) {
        if (hyw() && !ownerBroken) {
            Mob m = create(level, ALLIES[Math.floorMod(index, ALLIES.length)]);
            if (m != null) {
                if (setOwner(m, owner.getUUID())) return m;
                m.discard();
            }
        }
        IronGolem golem = EntityType.IRON_GOLEM.create(level);
        if (golem != null) golem.setPlayerCreated(true);
        return golem;
    }

    /**
     * Rattache une unité HYW au joueur. La méthode publique du propriétaire porte, une fois le jeu lancé, son nom
     * intermédiaire ({@code m_30586_}, celui de {@code setOwnerUUID}) ; on la cherche par ce nom ou par le nom lisible.
     */
    private static boolean setOwner(Mob m, UUID owner) {
        for (Class<?> c = m.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != UUID.class) continue;
                if (!method.getName().equals("m_30586_") && !method.getName().equals("setOwnerUUID")) continue;
                try {
                    method.setAccessible(true);
                    method.invoke(m, owner);
                    return true;
                } catch (ReflectiveOperationException | RuntimeException e) {
                    ownerBroken = true;
                    CrusadeCraft.LOGGER.warn("CrusadeCraft : impossible de rattacher une unité Hundred Years War au joueur ; golems de repli", e);
                    return false;
                }
            }
        }
        return false;
    }
}
