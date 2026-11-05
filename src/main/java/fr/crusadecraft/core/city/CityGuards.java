package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Repliques;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Les gardes se battent (P53). Ceux d'une ville et ceux d'une escorte s'en prennent aux monstres qui passent à portée, et au joueur qui a
 * frappé l'un des leurs, ou qui est en guerre contre leur royaume. Rien ne se passe sans témoin : loin de tout joueur, ils ne sont pas là.
 */
public final class CityGuards {
    /** On passe les voir tous les tant de ticks. */
    public static final int EVERY = 10;
    /** Ils voient un monstre à tant de blocs, un ennemi à tant. */
    static final int SIGHT = 14, ENEMY_SIGHT = 28;
    /** Assez près pour frapper ; assez près pour tirer. */
    static final double REACH = 3.2, BOW_RANGE = 18;
    /** Un coup tous les tant de ticks. */
    static final int COOLDOWN = 25;
    /** Ce que fait un coup d'épée, une flèche. */
    static final float SWORD = 3f;
    /** À cette distance, on se touche : pas besoin de se voir. */
    static final double POINT_BLANK = 2;
    static final double ARROW = 2.5;
    /** Celui qui frappe un habitant reste un ennemi pendant tant de ticks. */
    static final int GRUDGE = 1800;
    /** Ce qu'un garde a de points de vie, au moins. */
    static final double HEALTH = 40;

    private record Fight(UUID target, long until) {}

    /** Qui se bat contre qui. */
    private static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    /** Leur dernier coup. */
    private static final Map<UUID, Long> LAST_HIT = new HashMap<>();
    /** Pour chaque joueur, les villes qui lui en veulent, et jusqu'à quand. */
    private static final Map<UUID, Map<UUID, Long>> GRUDGES = new HashMap<>();
    private static final Random RANDOM = new Random();

    private CityGuards() {}

    public static void onServerStopped() {
        FIGHTS.clear();
        LAST_HIT.clear();
        GRUDGES.clear();
    }

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    public static boolean fighting(UUID person) {
        return FIGHTS.containsKey(person);
    }

    /** Pour les bancs d'essai : qui ils visent. */
    public static UUID targetOf(UUID person) {
        Fight f = FIGHTS.get(person);
        return f == null ? null : f.target();
    }

    /** Un garde, d'après son poste, ou parce qu'on l'a réglé pour se battre. */
    public static boolean isGuard(Person p) {
        if (p.age() < 16) return false;
        if (p.trip() != null && p.trip().leader() != null) return true;
        if (p.hasSettings() && p.settings().damage > 0) return true;
        BuildingFunction f = BuildingFunction.byName(p.job());
        return f == BuildingFunction.CASERNE || f == BuildingFunction.TOUR || f == BuildingFunction.CHATEAU;
    }

    /** Un joueur a frappé quelqu'un de cette ville : ses gardes s'en souviennent. */
    public static void provoke(MinecraftServer server, ServerPlayer player, UUID site) {
        if (player.isCreative() || player.isSpectator() || server.overworld() == null) return;
        GRUDGES.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(site, server.overworld().getGameTime() + GRUDGE);
    }

    /** Il est mort : on en reste là. Sans quoi on le tuerait de nouveau à sa résurrection, s'il renaît près d'eux. */
    public static void forgive(UUID player) {
        GRUDGES.remove(player);
    }

    public static boolean hasGrudge(MinecraftServer server, UUID player, UUID site) {
        Map<UUID, Long> g = GRUDGES.get(player);
        Long until = g == null ? null : g.get(site);
        return until != null && server.overworld() != null && until > server.overworld().getGameTime();
    }

    /** Sa ville d'attache : celle d'où il vient, même en voyage. */
    private static UUID homeOf(Person p) {
        return p.trip() != null ? p.trip().home() : p.site();
    }

    /** En guerre contre le royaume de ce garde ? */
    static boolean enemy(MinecraftServer server, ServerPlayer player, UUID owner) {
        Optional<fr.crusadecraft.core.realm.Realm> mine = fr.crusadecraft.core.realm.RealmService.realmOf(server, player.getUUID());
        if (mine.isPresent() && !mine.get().id().equals(owner) && data(server).diplomacy().between(mine.get().id(), owner).war()) return true;
        Optional<fr.crusadecraft.core.world.AiRealm> nation = fr.crusadecraft.core.nation.NationService.ruledBy(server, player.getUUID());
        return nation.isPresent() && !nation.get().id().equals(owner) && data(server).diplomacy().between(nation.get().id(), owner).war();
    }

    /** Tous les {@link #EVERY} ticks. */
    public static void tick(MinecraftServer server) {
        CrusadeSavedData data = data(server);
        LineageState state = data.lineage();
        List<Site> all = new ArrayList<>(data.world().sites());
        all.addAll(data.world().markets());
        for (Site site : all) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, site.dimension()));
            if (level == null) continue;
            for (CityBuilding b : site.buildings()) {
                for (UUID id : b.occupants()) {
                    Person p = state.person(id).filter(Person::alive).orElse(null);
                    if (p == null || !p.inWorld() || !isGuard(p)) continue;
                    Entity e = level.getEntity(id);
                    if (e instanceof PathfinderMob mob && mob.isAlive()) guard(server, level, site, p, mob);
                }
            }
        }
    }

    /** Un garde, à cet instant. */
    public static void guard(MinecraftServer server, ServerLevel level, Site site, Person p, PathfinderMob mob) {
        if (CityScenes.busy(p.id()) || (mob instanceof Villager v && v.isTrading())) return;
        equip(mob, p);
        LivingEntity target = choose(server, level, site, p, mob);
        if (target == null) {
            if (FIGHTS.remove(p.id()) != null) {
                mob.getNavigation().stop();
                CityWork.afterScene(p.id());
            }
            return;
        }
        boolean fresh = !FIGHTS.containsKey(p.id()) || !FIGHTS.get(p.id()).target().equals(target.getUUID());
        FIGHTS.put(p.id(), new Fight(target.getUUID(), level.getGameTime() + 100));
        if (fresh) shout(server, level, site, p, mob, target);
        engage(level, p, mob, target);
    }

    /** Une épée en main, et de quoi tenir un moment. Une fois pour toutes, sauf si l'éditeur en a décidé autrement. */
    private static void equip(PathfinderMob mob, Person p) {
        BuildingFunction f = BuildingFunction.byName(p.job());
        boolean archer = f == BuildingFunction.TOUR;
        var weapon = archer ? Items.BOW : Items.IRON_SWORD;
        if (!mob.getMainHandItem().is(weapon)) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
            mob.setDropChance(EquipmentSlot.MAINHAND, 0f);
        }
        if (p.hasSettings() && p.settings().maxHealth > 0) return;
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getValue() < HEALTH) {
            health.setBaseValue(HEALTH - (health.getValue() - health.getBaseValue()));
            mob.setHealth((float) HEALTH);
        }
    }

    /** Sur qui il va : d'abord le joueur qui s'en est pris aux siens ou l'ennemi du royaume, puis le monstre le plus proche. */
    private static LivingEntity choose(MinecraftServer server, ServerLevel level, Site site, Person p, PathfinderMob mob) {
        UUID home = homeOf(p);
        ServerPlayer foe = null;
        double best = (double) ENEMY_SIGHT * ENEMY_SIGHT;
        for (ServerPlayer pl : level.players()) {
            if (pl.isCreative() || pl.isSpectator() || !pl.isAlive()) continue;
            double d = pl.distanceToSqr(mob);
            if (d >= best) continue;
            if ((home != null && hasGrudge(server, pl.getUUID(), home)) || enemy(server, pl, site.owner())) {
                best = d;
                foe = pl;
            }
        }
        if (foe != null) return foe;
        // Il finit ce qu'il a commencé, si sa cible est encore à portée.
        Fight f = FIGHTS.get(p.id());
        if (f != null && level.getEntity(f.target()) instanceof LivingEntity held && held.isAlive() && held.distanceToSqr(mob) < ENEMY_SIGHT * ENEMY_SIGHT && !(held instanceof ServerPlayer)) return held;
        LivingEntity monster = null;
        double top = (double) SIGHT * SIGHT;
        AABB around = mob.getBoundingBox().inflate(SIGHT);
        for (LivingEntity m : level.getEntitiesOfClass(LivingEntity.class, around, x -> x instanceof Enemy && !(x instanceof NeutralMob) && x.isAlive())) {
            double d = m.distanceToSqr(mob);
            if (d < top) {
                top = d;
                monster = m;
            }
        }
        return monster;
    }

    private static void shout(MinecraftServer server, ServerLevel level, Site site, Person p, PathfinderMob mob, LivingEntity target) {
        Repliques.Moment m = target instanceof ServerPlayer ? Repliques.Moment.GARDE_ALERTE : Repliques.Moment.GARDE_COMBAT;
        CityVoice.line(server, site, p, m, Map.of(), RANDOM).ifPresent(text -> CityBubbles.say(level, mob, text));
    }

    /** Il court sur sa cible et la frappe, ou il lui tire dessus. */
    private static void engage(ServerLevel level, Person p, PathfinderMob mob, LivingEntity target) {
        if (CitySeats.seated(mob)) mob.stopRiding();
        if (mob.isSleeping()) mob.stopSleeping();
        long now = level.getGameTime();
        double d = mob.distanceTo(target);
        boolean archer = BuildingFunction.byName(p.job()) == BuildingFunction.TOUR;
        double reach = archer ? BOW_RANGE : REACH;
        if (mob instanceof Villager v) {
            var brain = v.getBrain();
            // Un villageois ordinaire s'enfuit quand on le frappe : pas celui-là.
            brain.eraseMemory(MemoryModuleType.HURT_BY);
            brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            brain.eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
            brain.useDefaultActivity();
            if (brain.checkMemory(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED)) brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
            if (d > reach - 1 && brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
                brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), 0.95f, 1));
            } else if (brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            }
        } else if (d > reach - 1) {
            mob.getNavigation().moveTo(target, 0.95);
        }
        mob.getLookControl().setLookAt(target, 30f, 30f);
        // Collés l'un à l'autre, il frappe même si la vue passe mal (un muret, une marche, une clôture à hauteur des yeux) : sans quoi ils resteraient plantés face à face.
        if (d > reach || (d > POINT_BLANK && !mob.hasLineOfSight(target))) return;
        Long last = LAST_HIT.get(p.id());
        if (last != null && now - last < COOLDOWN * (archer ? 2 : 1)) return;
        LAST_HIT.put(p.id(), now);
        if (archer) {
            Arrow arrow = new Arrow(level, mob);
            double dx = target.getX() - mob.getX(), dz = target.getZ() - mob.getZ(), dy = target.getY(0.33) - arrow.getY();
            arrow.shoot(dx, dy + Math.hypot(dx, dz) * 0.15, dz, 1.6f, 4f);
            arrow.setBaseDamage(ARROW);
            level.addFreshEntity(arrow);
            level.playSound(null, mob.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 0.8f, 1f);
            return;
        }
        mob.swing(InteractionHand.MAIN_HAND);
        float damage = p.hasSettings() && p.settings().damage > 0 ? (float) p.settings().damage : SWORD;
        target.hurt(mob.damageSources().mobAttack(mob), damage);
    }
}
