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
 * <p>Dans l'éditeur de la carte ({@code editing}), tout le monde IA est envoyé : on ne dessine pas à l'aveugle.
 *
 * @param focusX  centre de la vue par défaut (sa capitale, sinon sa position)
 * @param canEdit ce joueur a le droit d'ouvrir l'éditeur de la carte
 * @param borders les frontières peintes à la main, pour les royaumes de cette carte
 */
public record MapView(ResourceLocation dimension, int focusX, int focusZ, int playerX, int playerZ, int territoryRadius,
                      int colonyRadius, List<Territory> territories, List<Seat> seats, List<Dejure> dejure,
                      boolean canEdit, boolean editing, Borders borders, List<Place> places) {

    public MapView(ResourceLocation dimension, int focusX, int focusZ, int playerX, int playerZ, int territoryRadius,
                   int colonyRadius, List<Territory> territories, List<Seat> seats, List<Dejure> dejure,
                   boolean canEdit, boolean editing, Borders borders) {
        this(dimension, focusX, focusZ, playerX, playerZ, territoryRadius, colonyRadius, territories, seats, dejure, canEdit, editing, borders, List.of());
    }

    public MapView(ResourceLocation dimension, int focusX, int focusZ, int playerX, int playerZ, int territoryRadius,
                   int colonyRadius, List<Territory> territories, List<Seat> seats, List<Dejure> dejure) {
        this(dimension, focusX, focusZ, playerX, playerZ, territoryRadius, colonyRadius, territories, seats, dejure, false, false, Borders.EMPTY);
    }

    /**
     * Un royaume sur la carte.
     *
     * @param label    nom complet d'un royaume de joueur, ou nom de lieu d'un royaume IA (à composer avec le rang)
     * @param faith    sa foi (royaume IA), sinon {@code null}
     * @param handMade posé à la main dans l'éditeur
     */
    public record Territory(UUID id, boolean ai, boolean mine, String label, TitleRank rank, int color, String ruler,
                            boolean rulerFemale, AiPersonality personality, int population, AiFaith faith, boolean handMade) {
        public Territory(UUID id, boolean ai, boolean mine, String label, TitleRank rank, int color, String ruler,
                         boolean rulerFemale, AiPersonality personality, int population) {
            this(id, ai, mine, label, rank, color, ruler, rulerFemale, personality, population, null, false);
        }
    }

    /**
     * Un siège sur la carte. {@code province} : une province IA (même conquise), au territoire plus large qu'une colonie.
     *
     * @param provinceId l'identifiant de la province IA, −1 pour une colonie
     * @param origin     l'indice du royaume IA d'origine d'une province (celui qui la tient s'il n'a pas été conquis), sinon −1
     */
    public record Seat(int territory, String name, int x, int z, boolean capital, int dejure, boolean village, boolean province,
                       int provinceId, int origin, SiteInfo site) {
        public Seat(int territory, String name, int x, int z, boolean capital, int dejure, boolean village, boolean province) {
            this(territory, name, x, z, capital, dejure, village, province, -1, -1, null);
        }

        public Seat(int territory, String name, int x, int z, boolean capital, int dejure, boolean village, boolean province,
                    int provinceId, int origin) {
            this(territory, name, x, z, capital, dejure, village, province, provinceId, origin, null);
        }
    }

    /**
     * Une construction sur la carte (P14) : son identifiant (vide pour un poste de garde MineColonies), sa nature
     * ({@link #GUARD_POST} pour un poste de garde), son territoire, l'avancement du chantier et sa garnison.
     */
    public record SiteInfo(java.util.UUID id, int kind, int radius, int percent, int garrison) {
        public static final int GUARD_POST = 99;
    }

    public record Dejure(String place, int color) {}

    /**
     * Un lieu posé à la main (P15) : un lieu saint, une ruine, une région (avec son rayon), ou ce qui reste d'un lieu
     * disparu. Les villes et les constructions sont déjà des sièges.
     */
    public record Place(UUID id, int kind, net.minecraft.network.chat.Component name, int x, int z, int radius, int color) {}

    /**
     * Les cases peintes : la clé de chaque case (voir {@link TerritoryMap#key}) et l'indice de son royaume dans
     * {@link #territories()}, ou {@link #NEUTRAL} pour une terre libre.
     */
    public record Borders(long[] keys, int[] owners) {
        public static final int NEUTRAL = -1;
        public static final Borders EMPTY = new Borders(new long[0], new int[0]);

        public int size() {
            return keys.length;
        }
    }

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
            buf.writeBoolean(t.faith() != null);
            if (t.faith() != null) buf.writeEnum(t.faith());
            buf.writeBoolean(t.handMade());
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
            buf.writeVarInt(s.provinceId());
            buf.writeVarInt(s.origin());
            buf.writeBoolean(s.site() != null);
            if (s.site() != null) {
                buf.writeBoolean(s.site().id() != null);
                if (s.site().id() != null) buf.writeUUID(s.site().id());
                buf.writeVarInt(s.site().kind());
                buf.writeVarInt(s.site().radius());
                buf.writeVarInt(s.site().percent());
                buf.writeVarInt(s.site().garrison());
            }
        }
        buf.writeVarInt(dejure.size());
        for (Dejure d : dejure) {
            buf.writeUtf(d.place());
            buf.writeInt(d.color());
        }
        buf.writeBoolean(canEdit);
        buf.writeBoolean(editing);
        buf.writeVarInt(borders.size());
        for (int i = 0; i < borders.size(); i++) {
            buf.writeLong(borders.keys()[i]);
            buf.writeVarInt(borders.owners()[i] + 1);
        }
        buf.writeVarInt(places.size());
        for (Place p : places) {
            buf.writeUUID(p.id());
            buf.writeVarInt(p.kind());
            buf.writeComponent(p.name());
            buf.writeVarInt(p.x());
            buf.writeVarInt(p.z());
            buf.writeVarInt(p.radius());
            buf.writeInt(p.color());
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
            int population = buf.readVarInt();
            AiFaith faith = buf.readBoolean() ? buf.readEnum(AiFaith.class) : null;
            territories.add(new Territory(id, ai, mine, label, rank, color, ruler, female, personality, population, faith, buf.readBoolean()));
        }
        n = buf.readVarInt();
        List<Seat> seats = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int territory = buf.readVarInt();
            String name = buf.readUtf();
            int x = buf.readVarInt(), z = buf.readVarInt();
            boolean capital = buf.readBoolean();
            int dj = buf.readVarInt();
            boolean village = buf.readBoolean(), province = buf.readBoolean();
            int provinceId = buf.readVarInt(), origin = buf.readVarInt();
            SiteInfo site = null;
            if (buf.readBoolean()) {
                UUID siteId = buf.readBoolean() ? buf.readUUID() : null;
                site = new SiteInfo(siteId, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            }
            seats.add(new Seat(territory, name, x, z, capital, dj, village, province, provinceId, origin, site));
        }
        n = buf.readVarInt();
        List<Dejure> dejure = new ArrayList<>(n);
        for (int i = 0; i < n; i++) dejure.add(new Dejure(buf.readUtf(), buf.readInt()));
        boolean canEdit = buf.readBoolean(), editing = buf.readBoolean();
        n = buf.readVarInt();
        long[] keys = new long[n];
        int[] owners = new int[n];
        for (int i = 0; i < n; i++) {
            keys[i] = buf.readLong();
            owners[i] = buf.readVarInt() - 1;
        }
        n = buf.readVarInt();
        List<Place> places = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            places.add(new Place(buf.readUUID(), buf.readVarInt(), buf.readComponent(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt()));
        }
        return new MapView(dim, fx, fz, px, pz, radius, colonyRadius, territories, seats, dejure, canEdit, editing, new Borders(keys, owners), places);
    }
}
