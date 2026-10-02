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
        // Aucune migration pour l'instant : la version 1 est la première.
        return tag;
    }
}
