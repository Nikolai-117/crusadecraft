package fr.crusadecraft.compat.mca;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * La seule porte vers Minecraft Comes Alive (P13). Rien ici ne touche à ses classes : {@link McaCompat} le fait, et
 * n'est chargé que si le mod est présent. Sans Comes Alive, le registre des lignées fonctionne quand même ; on ne
 * peut simplement pas faire apparaître ses personnes en villageois.
 *
 * <p>Si une version de Comes Alive change ce dont on se sert, la compatibilité se coupe (une ligne au journal) au
 * lieu de faire planter le jeu.
 */
public final class McaBridge {
    public static final String TAG = "crusadecraft_person";
    private static Boolean loaded;
    private static boolean broken;

    private McaBridge() {}

    /** Ce que CrusadeCraft lit d'un villageois de Comes Alive pour l'inscrire au registre. */
    public record Villager(UUID id, String name, boolean female, int age, UUID father, UUID mother, UUID partner) {}

    public static boolean isReady() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("mca");
        return loaded && !broken;
    }

    private static void fail(Throwable e) {
        broken = true;
        CrusadeCraft.LOGGER.warn("CrusadeCraft : compatibilité Comes Alive désactivée ({})", e.toString());
    }

    /** Fait apparaître cette personne en villageois de Comes Alive : même identifiant, même nom, même sexe, son âge. */
    public static Optional<Entity> spawn(ServerLevel level, Person person, String fullName, BlockPos pos) {
        if (!isReady()) return Optional.empty();
        try {
            return McaCompat.spawn(level, person, fullName, pos);
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return Optional.empty();
        }
    }

    /** Inscrit ces personnes dans l'arbre généalogique de Comes Alive, avec leurs parents et leurs conjoints. */
    public static boolean syncTree(ServerLevel level, LineageState state, Collection<Person> people) {
        if (!isReady()) return false;
        try {
            McaCompat.syncTree(level, state, people);
            return true;
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return false;
        }
    }

    /** Ce villageois est-il un villageois de Comes Alive ? Si oui, ce qu'on en sait. */
    public static Optional<Villager> read(Entity entity) {
        if (!isReady()) return Optional.empty();
        try {
            return McaCompat.read(entity);
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return Optional.empty();
        }
    }

    /** Une entrée de l'arbre généalogique de Comes Alive : son nom, ses parents, son conjoint. */
    public record TreeEntry(String name, UUID father, UUID mother, UUID partner, boolean deceased) {}

    /** Ce que l'arbre de Comes Alive sait de cet identifiant (vérifications du banc d'essai, fiche). */
    public static Optional<TreeEntry> treeEntry(ServerLevel level, UUID id) {
        if (!isReady()) return Optional.empty();
        try {
            return McaCompat.treeEntry(level, id);
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return Optional.empty();
        }
    }

    /** Renomme le villageois qui incarne une personne (et son nom dans l'arbre). */
    public static void rename(Entity entity, String fullName) {
        if (!isReady()) return;
        try {
            McaCompat.rename(entity, fullName);
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        }
    }
}
