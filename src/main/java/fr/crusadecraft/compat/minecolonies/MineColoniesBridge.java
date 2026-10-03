package fr.crusadecraft.compat.minecolonies;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessSupplier;
import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.ColonyReport;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CitizenInfo;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.CitizenSkill;
import fr.crusadecraft.core.treasury.TaxRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

/**
 * Le seul point de contact avec l'API MineColonies (un garde de build interdit ses imports ailleurs).
 *
 * <p><b>Presque toujours en lecture.</b> Les seules écritures passent par l'API officielle, dans des
 * méthodes distinctes, et jamais avant {@link #isReady()} : l'XP de compétences (native, elle reste si
 * le mod est retiré) et des modificateurs de bonheur que MineColonies n'enregistre pas (ils disparaissent
 * au redémarrage, on les réapplique). Aucune colonie n'est créée, aucun propriétaire n'est changé.
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

    /** Centre (x, z) de chaque colonie d'une dimension : les sièges des royaumes IA s'en tiennent à l'écart. */
    public static List<int[]> colonyCenters(Level level) {
        List<int[]> out = new ArrayList<>();
        if (broken) return out;
        try {
            for (IColony c : IMinecoloniesAPI.getInstance().getColonyManager().getColonies(level)) {
                BlockPos center = c.getCenter();
                out.add(new int[]{center.getX(), center.getZ()});
            }
        } catch (LinkageError e) {
            breakBridge(e);
        }
        return out;
    }

    /** Nom du rang MineColonies d'un joueur dans une colonie (« Neutral », « Friend », « Hostile »…), vide si inconnu. */
    public static String rankName(ColonyRef ref, UUID player) {
        if (broken) return "";
        try {
            IColony c = colony(ref);
            if (c == null) return "";
            Rank rank = c.getPermissions().getRank(player);
            return rank == null ? "" : rank.getName();
        } catch (LinkageError e) {
            breakBridge(e);
            return "";
        }
    }

    /**
     * Passe un joueur « Hostile » dans une colonie, par l'API officielle (guerre entre joueurs). Renvoie le nom de
     * son rang d'avant, pour le lui rendre à la paix, ou {@code null} si rien n'a changé.
     */
    public static String setHostile(ColonyRef ref, UUID player) {
        if (broken) return null;
        try {
            IColony c = colony(ref);
            if (c == null) return null;
            Rank before = c.getPermissions().getRank(player);
            Rank hostile = c.getPermissions().getRankHostile();
            if (before == null || hostile == null || before.equals(hostile) || before.equals(c.getPermissions().getRankOwner())) return null;
            c.getPermissions().setPlayerRank(player, hostile, c.getWorld());
            return before.getName();
        } catch (LinkageError e) {
            breakBridge(e);
            return null;
        }
    }

    /** Rend à un joueur un rang retrouvé par son nom. */
    public static void setRankByName(ColonyRef ref, UUID player, String rankName) {
        if (broken || rankName == null) return;
        try {
            IColony c = colony(ref);
            if (c == null) return;
            for (Rank r : c.getPermissions().getRanks().values()) {
                if (r.getName().equals(rankName)) {
                    c.getPermissions().setPlayerRank(player, r, c.getWorld());
                    return;
                }
            }
        } catch (LinkageError e) {
            breakBridge(e);
        }
    }

    /** Centre d'une colonie (son Hôtel de ville, ou à défaut son point de fondation). */
    public static Optional<BlockPos> center(ColonyRef ref) {
        if (broken) return Optional.empty();
        try {
            IColony c = colony(ref);
            return c == null ? Optional.empty() : Optional.of(c.getCenter());
        } catch (LinkageError e) {
            breakBridge(e);
            return Optional.empty();
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

    // ------------------------------------------------------------------ rapports et effets du conseil

    /** Le rapport d'un maire : sans-logis, chômeurs, malades, affamés, gardes, menace de raid. */
    public static ColonyReport report(ColonyRef ref) {
        if (broken) return ColonyReport.missing(ref);
        try {
            IColony c = colony(ref);
            if (c == null) return ColonyReport.missing(ref);
            int homeless = 0, jobless = 0, sick = 0, hungry = 0, guards = 0;
            List<ICitizenData> citizens = c.getCitizenManager().getCitizens();
            for (ICitizenData d : citizens) {
                if (d.getHomeBuilding() == null) homeless++;
                if (!d.isChild() && d.getJob() == null) jobless++;
                if (d.getCitizenDiseaseHandler().isSick()) sick++;
                if (d.getSaturation() < HUNGRY_SATURATION) hungry++;
                if (d.getJob() != null && d.getJob().isGuard()) guards++;
            }
            return new ColonyReport(ref, c.getName(), citizens.size(), c.getCitizenManager().getMaxCitizens(), homeless,
                    jobless, sick, hungry, guards, c.getOverallHappiness(), c.getRaiderManager().willRaidTonight(),
                    c.getRaiderManager().isRaided());
        } catch (LinkageError e) {
            breakBridge(e);
            return ColonyReport.missing(ref);
        }
    }

    /** En dessous, un citoyen a faim (la satiété de MineColonies va de 0 à 20). */
    private static final double HUNGRY_SATURATION = 4.0;

    /** Les gardes d'une colonie. */
    public static List<CitizenRef> guards(ColonyRef ref) {
        List<CitizenRef> out = new ArrayList<>();
        if (broken) return out;
        try {
            IColony c = colony(ref);
            if (c == null) return out;
            for (ICitizenData d : c.getCitizenManager().getCitizens()) {
                if (d.getJob() != null && d.getJob().isGuard()) out.add(new CitizenRef(ref, d.getId()));
            }
        } catch (LinkageError e) {
            breakBridge(e);
        }
        return out;
    }

    /**
     * Ajoute de l'expérience à une compétence, par l'API officielle (le rôle fait progresser). Écriture
     * native de MineColonies : elle reste valable si CrusadeCraft est retiré.
     */
    public static void addSkillXp(CitizenRef ref, CitizenSkill skill, double xp) {
        if (broken || xp <= 0) return;
        try {
            ICitizenData d = citizenData(ref);
            if (d != null) d.getCitizenSkillHandler().addXpToSkill(Skill.valueOf(skill.name()), xp, d);
        } catch (LinkageError e) {
            breakBridge(e);
        } catch (IllegalArgumentException ignored) {
            // compétence absente de cette version de MineColonies
        }
    }

    /** Identifiant du modificateur de bonheur de la couronne dans MineColonies (traduit dans nos langues). */
    public static final String HAPPINESS_ID = "crusadecraft";
    /** Identifiant du modificateur de bonheur lié à la loi de fiscalité. */
    public static final String TAX_HAPPINESS_ID = "crusadecraft_impots";

    /**
     * Applique le facteur de bonheur de la couronne aux adultes d'une colonie (1.0 = neutre, ignoré par
     * MineColonies). MineColonies ne recharge pas les modificateurs qu'il ne connaît pas : celui-ci
     * disparaît au redémarrage, et on le réapplique périodiquement. Retirer le mod ne laisse donc rien.
     */
    public static void applyCrownHappiness(ColonyRef ref, double factor) {
        applyHappiness(ref, HAPPINESS_ID, factor);
    }

    /** Même principe, pour le modificateur « Impôts » de la loi de fiscalité. */
    public static void applyTaxHappiness(ColonyRef ref, double factor) {
        applyHappiness(ref, TAX_HAPPINESS_ID, factor);
    }

    private static void applyHappiness(ColonyRef ref, String id, double factor) {
        if (broken) return;
        try {
            IColony c = colony(ref);
            if (c == null) return;
            for (ICitizenData d : c.getCitizenManager().getCitizens()) {
                if (d.isChild()) continue;
                d.getCitizenHappinessHandler().addModifier(new StaticHappinessModifier(id, 1.0,
                        new StaticHappinessSupplier(factor)));
            }
        } catch (LinkageError e) {
            breakBridge(e);
        }
    }

    // ------------------------------------------------------------------ économie

    /** Les bâtiments construits d'une colonie (identifiant MineColonies et niveau), pour l'impôt. */
    public static List<TaxRules.Building> buildings(ColonyRef ref) {
        List<TaxRules.Building> out = new ArrayList<>();
        if (broken) return out;
        try {
            IColony c = colony(ref);
            if (c == null) return out;
            // Le niveau suffit : MineColonies tient lui-même pour construit un bâtiment chargé avec un niveau.
            for (IBuilding b : c.getServerBuildingManager().getBuildings().values()) {
                if (b.getBuildingLevel() <= 0) continue;
                out.add(new TaxRules.Building(b.getBuildingType().getRegistryName().toString(), b.getBuildingLevel()));
            }
        } catch (LinkageError e) {
            breakBridge(e);
        }
        return out;
    }

    /**
     * Force d'un effet de recherche dans une colonie (0 si la recherche n'est pas terminée). Nos recherches
     * de gouvernance sont un datapack : MineColonies ignore lui-même une recherche inconnue au chargement,
     * si bien que retirer le mod ne gêne pas ses sauvegardes.
     */
    public static double researchEffect(ColonyRef ref, ResourceLocation effect) {
        if (broken || effect == null) return 0;
        try {
            IColony c = colony(ref);
            return c == null ? 0 : c.getResearchManager().getResearchEffects().getEffectStrength(effect);
        } catch (LinkageError e) {
            breakBridge(e);
            return 0;
        }
    }

    /** L'entité du citoyen si elle est chargée. */
    public static Optional<Entity> citizenEntity(CitizenRef ref) {
        if (broken) return Optional.empty();
        try {
            ICitizenData d = citizenData(ref);
            return d == null ? Optional.empty() : d.getEntity().map(e -> (Entity) e);
        } catch (LinkageError e) {
            breakBridge(e);
            return Optional.empty();
        }
    }

    private static int colorOf(IColony c) {
        ChatFormatting f = c.getTeamColonyColor();
        Integer rgb = f == null ? null : f.getColor();
        return rgb == null ? 0x808080 : rgb;
    }
}
