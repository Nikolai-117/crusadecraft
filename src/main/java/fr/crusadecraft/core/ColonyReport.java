package fr.crusadecraft.core;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Le rapport d'une colonie, tel qu'un maire le présenterait : tiré de MineColonies au moment où on le
 * demande.
 *
 * @param raidTonight MineColonies prévoit une attaque de pillards cette nuit
 * @param raided      la colonie est en ce moment attaquée
 */
public record ColonyReport(ColonyRef ref, String name, int citizens, int maxCitizens, int homeless, int jobless,
                           int sick, int hungry, int guards, double happiness, boolean raidTonight, boolean raided) {

    public static ColonyReport missing(ColonyRef ref) {
        return new ColonyReport(ref, "", 0, 0, 0, 0, 0, 0, 0, 0, false, false);
    }

    public void write(FriendlyByteBuf buf) {
        ref.write(buf);
        buf.writeUtf(name);
        buf.writeVarInt(citizens);
        buf.writeVarInt(maxCitizens);
        buf.writeVarInt(homeless);
        buf.writeVarInt(jobless);
        buf.writeVarInt(sick);
        buf.writeVarInt(hungry);
        buf.writeVarInt(guards);
        buf.writeDouble(happiness);
        buf.writeBoolean(raidTonight);
        buf.writeBoolean(raided);
    }

    public static ColonyReport read(FriendlyByteBuf buf) {
        return new ColonyReport(ColonyRef.read(buf), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readBoolean(),
                buf.readBoolean());
    }
}
