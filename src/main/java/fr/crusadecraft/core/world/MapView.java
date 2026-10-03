package fr.crusadecraft.core.world;

import fr.crusadecraft.core.realm.TitleRank;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * La carte du royaume telle que le serveur la confie à un joueur : les royaumes qu'il connaît (le sien, ceux
 * des autres joueurs, les royaumes IA découverts), leurs sièges, et les royaumes de jure. Ce qu'il n'a pas
 * découvert n'est jamais envoyé : le brouillard n'est pas un simple voile.
 *
 * @param focusX centre de la vue par défaut (sa capitale, sinon sa position)
 */
public record MapView(ResourceLocation dimension, int focusX, int focusZ, int playerX, int playerZ, int territoryRadius,
                      int colonyRadius, List<Territory> territories, List<Seat> seats, List<Dejure> dejure) {

    /**
     * Un royaume sur la carte.
     *
     * @param label nom complet d'un royaume de joueur, ou nom de lieu d'un royaume IA (à composer avec le rang)
     */
    public record Territory(UUID id, boolean ai, boolean mine, String label, TitleRank rank, int color, String ruler,
                            boolean rulerFemale, AiPersonality personality, int population) {}

    /** Un siège (colonie d'un joueur, province d'un royaume IA). */
    /** Un siège sur la carte. {@code province} : une province IA (même conquise), au territoire plus large qu'une colonie. */
    public record Seat(int territory, String name, int x, int z, boolean capital, int dejure, boolean village, boolean province) {}

    public record Dejure(String place, int color) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeVarInt(focusX);
        buf.writeVarInt(focusZ);
        buf.writeVarInt(playerX);
        buf.writeVarInt(playerZ);
        buf.writeVarInt(territoryRadius);
        buf.writeVarInt(colonyRadius);
        buf.writeVarInt(territories.size());
        for (Territory t : territories) {
            buf.writeUUID(t.id());
            buf.writeBoolean(t.ai());
            buf.writeBoolean(t.mine());
            buf.writeUtf(t.label());
            buf.writeEnum(t.rank());
            buf.writeInt(t.color());
            buf.writeUtf(t.ruler());
            buf.writeBoolean(t.rulerFemale());
            buf.writeBoolean(t.personality() != null);
            if (t.personality() != null) buf.writeEnum(t.personality());
            buf.writeVarInt(t.population());
        }
        buf.writeVarInt(seats.size());
        for (Seat s : seats) {
            buf.writeVarInt(s.territory());
            buf.writeUtf(s.name());
            buf.writeVarInt(s.x());
            buf.writeVarInt(s.z());
            buf.writeBoolean(s.capital());
            buf.writeVarInt(s.dejure());
            buf.writeBoolean(s.village());
            buf.writeBoolean(s.province());
        }
        buf.writeVarInt(dejure.size());
        for (Dejure d : dejure) {
            buf.writeUtf(d.place());
            buf.writeInt(d.color());
        }
    }

    public static MapView read(FriendlyByteBuf buf) {
        ResourceLocation dim = buf.readResourceLocation();
        int fx = buf.readVarInt(), fz = buf.readVarInt(), px = buf.readVarInt(), pz = buf.readVarInt();
        int radius = buf.readVarInt(), colonyRadius = buf.readVarInt();
        int n = buf.readVarInt();
        List<Territory> territories = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID id = buf.readUUID();
            boolean ai = buf.readBoolean(), mine = buf.readBoolean();
            String label = buf.readUtf();
            TitleRank rank = buf.readEnum(TitleRank.class);
            int color = buf.readInt();
            String ruler = buf.readUtf();
            boolean female = buf.readBoolean();
            AiPersonality personality = buf.readBoolean() ? buf.readEnum(AiPersonality.class) : null;
            territories.add(new Territory(id, ai, mine, label, rank, color, ruler, female, personality, buf.readVarInt()));
        }
        n = buf.readVarInt();
        List<Seat> seats = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            seats.add(new Seat(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
        }
        n = buf.readVarInt();
        List<Dejure> dejure = new ArrayList<>(n);
        for (int i = 0; i < n; i++) dejure.add(new Dejure(buf.readUtf(), buf.readInt()));
        return new MapView(dim, fx, fz, px, pz, radius, colonyRadius, territories, seats, dejure);
    }
}
