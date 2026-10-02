package fr.crusadecraft.core;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Photographie d'une colonie, lue dans MineColonies au moment où on la demande.
 *
 * <p>C'est un simple relevé : rien n'est conservé ni réécrit chez MineColonies.
 *
 * @param happiness bonheur moyen de MineColonies, de 0 à 10
 */
public record ColonyInfo(ColonyRef ref, String name, int townHallLevel, int citizens, int maxCitizens,
                         double happiness, int buildings, int color) {

    public void write(FriendlyByteBuf buf) {
        ref.write(buf);
        buf.writeUtf(name);
        buf.writeVarInt(townHallLevel);
        buf.writeVarInt(citizens);
        buf.writeVarInt(maxCitizens);
        buf.writeDouble(happiness);
        buf.writeVarInt(buildings);
        buf.writeInt(color);
    }

    public static ColonyInfo read(FriendlyByteBuf buf) {
        return new ColonyInfo(ColonyRef.read(buf), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readInt());
    }

    /** Colonie inconnue ou introuvable : on garde la référence pour l'afficher quand même. */
    public static ColonyInfo missing(ColonyRef ref) {
        return new ColonyInfo(ref, "", 0, 0, 0, 0, 0, 0x808080);
    }

    public boolean isMissing() {
        return name.isEmpty();
    }
}
