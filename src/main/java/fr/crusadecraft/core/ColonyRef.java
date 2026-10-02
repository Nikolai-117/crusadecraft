package fr.crusadecraft.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Désigne une colonie MineColonies sans dépendre de ses classes.
 *
 * <p>MineColonies tient une liste de colonies par dimension, chacune avec son propre compteur :
 * un identifiant n'est donc unique que <b>dans sa dimension</b>. La clé complète est le couple
 * (dimension, identifiant). C'est l'erreur qui a fait viser la mauvaise colonie à War 'N Taxes.
 */
public record ColonyRef(ResourceLocation dimension, int colonyId) {

    public ColonyRef {
        Objects.requireNonNull(dimension, "dimension");
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", dimension.toString());
        tag.putInt("id", colonyId);
        return tag;
    }

    public static ColonyRef load(CompoundTag tag) {
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dim"));
        return new ColonyRef(dim == null ? OVERWORLD : dim, tag.getInt("id"));
    }

    private static final ResourceLocation OVERWORLD = ResourceLocation.tryParse("minecraft:overworld");

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeVarInt(colonyId);
    }

    public static ColonyRef read(FriendlyByteBuf buf) {
        return new ColonyRef(buf.readResourceLocation(), buf.readVarInt());
    }

    @Override
    public String toString() {
        return dimension + "#" + colonyId;
    }
}
