package fr.crusadecraft.data;

import fr.crusadecraft.CrusadeCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.saveddata.SavedData;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * {@link SavedData} dont l'écriture est atomique.
 *
 * <p>En 1.20.1, Minecraft écrit le fichier directement : un plantage pendant l'écriture laisse un
 * fichier tronqué, et l'état du mod est perdu. On écrit donc dans un fichier temporaire, puis on
 * le renomme d'un coup. Sur disque, on trouve toujours soit l'ancienne version, soit la nouvelle.
 * La version précédente est gardée en {@code .bak}.
 */
public abstract class AtomicSavedData extends SavedData {

    @Override
    public void save(File file) {
        if (!isDirty()) return;
        CompoundTag root = new CompoundTag();
        root.put("data", save(new CompoundTag()));
        NbtUtils.addCurrentDataVersion(root);
        Path target = file.toPath();
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Path backup = target.resolveSibling(target.getFileName() + ".bak");
        try {
            NbtIo.writeCompressed(root, tmp.toFile());
            if (Files.exists(target)) {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            setDirty(false);
        } catch (IOException e) {
            // On garde l'état « modifié » : la prochaine sauvegarde réessaiera.
            CrusadeCraft.LOGGER.error("CrusadeCraft : impossible d'enregistrer {}", target, e);
        }
    }
}
