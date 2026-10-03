package fr.crusadecraft.data;

import fr.crusadecraft.CrusadeCraft;
import net.minecraft.nbt.CompoundTag;

/**
 * Mise à niveau des anciennes sauvegardes.
 *
 * <p>Chaque étape fait passer les données de la version {@code n} à {@code n + 1}. On ne supprime
 * jamais une donnée qu'on ne comprend pas : une sauvegarde plus récente que le mod est chargée
 * telle quelle, avec un avertissement, plutôt que tronquée.
 */
public final class DataMigrations {

    private DataMigrations() {}

    public static CompoundTag migrate(CompoundTag tag) {
        int version = tag.contains("version") ? tag.getInt("version") : CrusadeSavedData.DATA_VERSION;
        if (version > CrusadeSavedData.DATA_VERSION) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : sauvegarde en version {} (plus récente que le mod, version {}). "
                    + "Elle est lue au mieux ; ne la réenregistrez pas avec cette version du mod si possible.",
                    version, CrusadeSavedData.DATA_VERSION);
            return tag;
        }
        // v1 → v2 : ajout des personnages et des postes du royaume. Rien à convertir : les listes
        // absentes se lisent vides.
        // v2 → v3 : ajout de la cour (table du conseil, bureaux, tâches, requêtes). Lue vide si absente.
        // v3 → v4 : ajout des lois et du livre de comptes. Lois absentes = niveaux par défaut.
        // v4 → v5 : ajout du monde (royaumes IA, royaumes de jure). Absent = pas encore généré.
        // v5 → v6 : diplomatie, courrier, revendications. Absents = aucune relation, aucune lettre.
        if (version < CrusadeSavedData.DATA_VERSION) {
            CrusadeCraft.LOGGER.info("CrusadeCraft : données mises à niveau de la version {} à {}", version,
                    CrusadeSavedData.DATA_VERSION);
        }
        return tag;
    }
}
