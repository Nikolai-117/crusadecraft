package fr.crusadecraft.core.character;

import fr.crusadecraft.core.ColonyRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Désigne un citoyen MineColonies : sa colonie (dimension + identifiant) et son identifiant dans
 * cette colonie. C'est la clé de toutes nos données de personnage.
 */
public record CitizenRef(ColonyRef colony, int citizenId) {

    public CompoundTag save() {
        CompoundTag tag = colony.save();
        tag.putInt("citizen", citizenId);
        return tag;
    }

    public static CitizenRef load(CompoundTag tag) {
        return new CitizenRef(ColonyRef.load(tag), tag.getInt("citizen"));
    }

    public void write(FriendlyByteBuf buf) {
        colony.write(buf);
        buf.writeVarInt(citizenId);
    }

    public static CitizenRef read(FriendlyByteBuf buf) {
        return new CitizenRef(ColonyRef.read(buf), buf.readVarInt());
    }

    @Override
    public String toString() {
        return colony + "/" + citizenId;
    }
}
