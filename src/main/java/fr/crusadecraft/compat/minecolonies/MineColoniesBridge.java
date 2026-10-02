package fr.crusadecraft.compat.minecolonies;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CitizenInfo;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.CitizenSkill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

/**
 * Le seul point de contact avec l'API MineColonies (un garde de build interdit ses imports ailleurs).
 *
 * <p><b>Lecture seule.</b> Aucune méthode n'écrit dans les données de MineColonies. Les quelques
 * écritures prévues plus tard (XP de compétences, rang « Hostile » en guerre) passeront par l'API
 * officielle, dans des méthodes distinctes, et jamais avant {@link #isReady()}.
 *
 * <p><b>Tolérance aux versions.</b> L'API de MineColonies change entre versions : le gestionnaire
 * des bâtiments a été renommé, {@code setOwner} a changé de signature. Chaque appel est donc protégé.
 * Une {@link LinkageError} (méthode absente dans la version installée) coupe proprement le pont, avec
 * un message, au lieu de planter le serveur.
 */
public final class MineColoniesBridge {

    private static volatile boolean ready;
    private static volatile boolean broken;

    private MineColoniesBridge() {}

    /** MineColonies a fini de charger ses colonies, et le pont fonctionne. */
    public static boolean isReady() {
        return ready && !broken;
    }

    public static boolean isBroken() {
        return broken;
    }

    static void markReady(boolean value) {
        ready = value;
    }

    private static void breakBridge(Throwable t) {
        if (!broken) {
            broken = true;
            CrusadeCraft.LOGGER.error("CrusadeCraft : l'API de la version de MineColonies installée ne correspond pas "
                    + "à celle attendue. Les fonctions liées aux colonies sont désactivées pour cette session.", t);
        }
    }

    private static IColonyManager manager() {
        return IMinecoloniesAPI.getInstance().getColonyManager();
    }

    private static IColony colony(ColonyRef ref) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ref.dimension());
        return manager().getColonyByDimension(ref.colonyId(), key);
    }

    private static ColonyRef refOf(IColony colony) {
        return new ColonyRef(colony.getDimension().location(), colony.getID());
    }

    /** La colonie qui revendique cette position, s'il y en a une. */
    public static Optional<ColonyRef> colonyAt(Level level, BlockPos pos) {
        if (broken) return Optional.empty();
        try {
            IColony c = manager().getColonyByPosFromWorld(level, pos);
            return c == null ? Optional.empty() : Optional.of(refOf(c));
        } catch (LinkageError e) {
            breakBridge(e);
            return Optional.empty();
        }
    }

    public static boolean exists(ColonyRef ref) {
        if (broken) return false;
        try {
            return colony(ref) != null;
        } catch (LinkageError e) {
            breakBridge(e);
            return false;
        }
    }

    /** Propriétaire déclaré de la colonie. Peut être vide sur une colonie abîmée. */
    public static Optional<UUID> owner(ColonyRef ref) {
        if (broken) return Optional.empty();
        try {
            IColony c = colony(ref);
            return c == null ? Optional.empty() : Optional.ofNullable(c.getPermissions().getOwner());
        } catch (LinkageError e) {
            breakBridge(e);
            return Optional.empty();
        }
    }

    public static boolean isOwner(ColonyRef ref, UUID player) {
        return owner(ref).map(player::equals).orElse(false);
    }

    /** Propriétaire ou officier : un gestionnaire de la colonie au sens de MineColonies. */
    public static boolean isManager(ColonyRef ref, UUID player) {
        if (broken) return false;
        try {
            IColony c = colony(ref);
            if (c == null) return false;
            if (player.equals(c.getPermissions().getOwner())) return true;
            Rank rank = c.getPermissions().getRank(player);
            return rank != null && rank.isColonyManager();
        } catch (LinkageError e) {
            breakBridge(e);
            return false;
        }
    }

    /** La position est-elle dans le volume de l'Hôtel de ville ? */
    public static boolean isInsideTownHall(ColonyRef ref, BlockPos pos) {
        if (broken) return false;
        try {
            ITownHall th = townHall(ref);
            return th != null && th.isInBuilding(pos);
        } catch (LinkageError e) {
            breakBridge(e);
            return false;
        }
    }

    public static int townHallLevel(ColonyRef ref) {
        if (broken) return 0;
        try {
            ITownHall th = townHall(ref);
            return th == null ? 0 : th.getBuildingLevel();
        } catch (LinkageError e) {
            breakBridge(e);
            return 0;
        }
    }

    private static ITownHall townHall(ColonyRef ref) {
        IColony c = colony(ref);
        if (c == null || !c.getServerBuildingManager().hasTownHall()) return null;
        return c.getServerBuildingManager().getTownHall();
    }

    /** Relevé complet d'une colonie, ou {@link ColonyInfo#missing} si elle est introuvable. */
    public static ColonyInfo info(ColonyRef ref) {
        if (broken) return ColonyInfo.missing(ref);
        try {
            IColony c = colony(ref);
            if (c == null) return ColonyInfo.missing(ref);
            ITownHall th = c.getServerBuildingManager().hasTownHall() ? c.getServerBuildingManager().getTownHall() : null;
            return new ColonyInfo(ref,
                    c.getName(),
                    th == null ? 0 : th.getBuildingLevel(),
                    c.getCitizenManager().getCurrentCitizenCount(),
                    c.getCitizenManager().getMaxCitizens(),
                    c.getOverallHappiness(),
                    c.getServerBuildingManager().getBuildings().size(),
                    colorOf(c));
        } catch (LinkageError e) {
            breakBridge(e);
            return ColonyInfo.missing(ref);
        }
    }

    // ------------------------------------------------------------------ citoyens

    /**
     * Le citoyen qu'incarne cette entité. Fonctionne aussi côté client : la colonie et l'identifiant
     * sont des données synchronisées de l'entité MineColonies.
     */
    public static Optional<CitizenRef> refOf(Entity entity) {
        if (broken || !(entity instanceof AbstractEntityCitizen citizen)) return Optional.empty();
        try {
            int colonyId = citizen.getEntityData().get(AbstractEntityCitizen.DATA_COLONY_ID);
            int citizenId = citizen.getEntityData().get(AbstractEntityCitizen.DATA_CITIZEN_ID);
            if (colonyId <= 0 || citizenId <= 0) return Optional.empty();
            return Optional.of(new CitizenRef(new ColonyRef(entity.level().dimension().location(), colonyId), citizenId));
        } catch (LinkageError e) {
            breakBridge(e);
            return Optional.empty();
        }
    }

    private static ICitizenData citizenData(CitizenRef ref) {
        IColony c = colony(ref.colony());
        return c == null ? null : c.getCitizenManager().getCivilian(ref.citizenId());
    }

    /** Relevé d'un citoyen, ou vide s'il n'existe pas (ou plus). */
    public static Optional<CitizenInfo> citizen(CitizenRef ref) {
        if (broken) return Optional.empty();
        try {
            IColony c = colony(ref.colony());
            ICitizenData d = c == null ? null : c.getCitizenManager().getCivilian(ref.citizenId());
            if (d == null) return Optional.empty();
            int[] skills = new int[CitizenSkill.COUNT];
            for (CitizenSkill s : CitizenSkill.values()) {
                try {
                    skills[s.ordinal()] = d.getCitizenSkillHandler().getLevel(Skill.valueOf(s.name()));
                } catch (IllegalArgumentException missing) {
                    skills[s.ordinal()] = 1;
                }
            }
            String job = d.getJob() == null ? "" : d.getJob().getJobRegistryEntry().getTranslationKey();
            double happiness = d.getCitizenHappinessHandler().getHappiness(c, d);
            int entityId = d.getEntity().map(Entity::getId).orElse(-1);
            return Optional.of(new CitizenInfo(ref, d.getName(), d.isFemale(), d.isChild(), job, skills, happiness, entityId));
        } catch (LinkageError e) {
            breakBridge(e);
            return Optional.empty();
        }
    }

    public static boolean citizenExists(CitizenRef ref) {
        if (broken) return false;
        try {
            return citizenData(ref) != null;
        } catch (LinkageError e) {
            breakBridge(e);
            return false;
        }
    }

    /** Les citoyens d'une colonie (identifiants). */
    public static List<CitizenRef> citizensOf(ColonyRef ref) {
        List<CitizenRef> out = new ArrayList<>();
        if (broken) return out;
        try {
            IColony c = colony(ref);
            if (c == null) return out;
            for (ICitizenData d : c.getCitizenManager().getCitizens()) out.add(new CitizenRef(ref, d.getId()));
        } catch (LinkageError e) {
            breakBridge(e);
        }
        return out;
    }

    private static int colorOf(IColony c) {
        ChatFormatting f = c.getTeamColonyColor();
        Integer rgb = f == null ? null : f.getColor();
        return rgb == null ? 0x808080 : rgb;
    }
}
