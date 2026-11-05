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
 * @param war     la carte de guerre de ce joueur et de ses alliés (P18)
 */
public record MapView(ResourceLocation dimension, int focusX, int focusZ, int playerX, int playerZ, int territoryRadius,
                      int colonyRadius, List<Territory> territories, List<Seat> seats, List<Dejure> dejure,
                      boolean canEdit, boolean editing, Borders borders, List<Place> places, War war, List<TradeLine> routes,
                      List<ArmyPawn> armies, List<Traveller> travellers, List<CommerceLine> commerce, List<Blocked> blocked) {

    /** P51 : une ligne de commerce, et le tracé de sa route ; P55 : coupée, et pourquoi. */
    public record CommerceLine(int[] xs, int[] zs, String from, String to, String what, int trips, boolean ai, String trouble) {}

    /** P55 : une ville coupée du monde. */
    public record Blocked(int x, int z, String name, String why) {}

    public MapView(ResourceLocation dimension, int focusX, int focusZ, int playerX, int playerZ, int territoryRadius,
                   int colonyRadius, List<Territory> territories, List<Seat> seats, List<Dejure> dejure,
                   boolean canEdit, boolean editing, Borders borders, List<Place> places, War war, List<TradeLine> routes, List<ArmyPawn> armies, List<Traveller> travellers) {
        this(dimension, focusX, focusZ, playerX, playerZ, territoryRadius, colonyRadius, territories, seats, dejure, canEdit, editing, borders, places, war, routes, armies, travellers, List.of(), List.of());
    }

    /** P50 : un voyageur sur la route, une caravane s'il porte quelque chose. */
    public record Traveller(int x, int z, int toX, int toZ, String name, String from, String to, int percent, boolean caravan, String cargo) {}

    public MapView(ResourceLocation dimension, int focusX, int focusZ, int playerX, int playerZ, int territoryRadius,
                   int colonyRadius, List<Territory> territories, List<Seat> seats, List<Dejure> dejure,
                   boolean canEdit, boolean editing, Borders borders, List<Place> places, War war, List<TradeLine> routes, List<ArmyPawn> armies) {
        this(dimension, focusX, focusZ, playerX, playerZ, territoryRadius, colonyRadius, territories, seats, dejure, canEdit, editing, borders, places, war, routes, armies, List.of());
    }

    public MapView(ResourceLocation dimension, int focusX, int focusZ, int playerX, int playerZ, int territoryRadius,
                   int colonyRadius, List<Territory> territories, List<Seat> seats, List<Dejure> dejure,
                   boolean canEdit, boolean editing, Borders borders, List<Place> places, War war, List<TradeLine> routes) {
        this(dimension, focusX, focusZ, playerX, playerZ, territoryRadius, colonyRadius, territories, seats, dejure, canEdit, editing, borders, places, war,
                routes, List.of());
    }

    /**
     * Le pion d'une armée sur la carte (P27) : la tienne quand elle est rassemblée, les ostes de l'ordinateur en
     * campagne. {@code soldiers} : soldats dans le monde (0 si ce n'est qu'un pion) ; {@code target} : où elle va.
     */
    public record ArmyPawn(int x, int z, int men, int soldiers, int color, net.minecraft.network.chat.Component name, boolean mine, boolean beaten,
                           boolean hasTarget, int targetX, int targetZ) {}

    public MapView(ResourceLocation dimension, int focusX, int focusZ, int playerX, int playerZ, int territoryRadius,
                   int colonyRadius, List<Territory> territories, List<Seat> seats, List<Dejure> dejure,
                   boolean canEdit, boolean editing, Borders borders, List<Place> places, War war) {
        this(dimension, focusX, focusZ, playerX, playerZ, territoryRadius, colonyRadius, territories, seats, dejure, canEdit, editing, borders, places, war, List.of());
    }

    /** Une route de commerce (P25) entre deux capitales, ce qu'elle rapporte par jour, et ses deux royaumes. */
    public record TradeLine(int x0, int z0, int x1, int z1, long income, net.minecraft.network.chat.Component a, net.minecraft.network.chat.Component b,
                            net.minecraft.network.chat.Component goods, boolean caravan, int cx, int cz, boolean raided) {
        public TradeLine(int x0, int z0, int x1, int z1, long income, net.minecraft.network.chat.Component a, net.minecraft.network.chat.Component b) {
            this(x0, z0, x1, z1, income, a, b, net.minecraft.network.chat.Component.empty(), false, 0, 0, false);
        }
    }

    public MapView(ResourceLocation dimension, int focusX, int focusZ, int playerX, int playerZ, int territoryRadius,
                   int colonyRadius, List<Territory> territories, List<Seat> seats, List<Dejure> dejure,
                   boolean canEdit, boolean editing, Borders borders, List<Place> places) {
        this(dimension, focusX, focusZ, playerX, playerZ, territoryRadius, colonyRadius, territories, seats, dejure, canEdit, editing, borders, places, War.NONE);
    }

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
                            boolean rulerFemale, AiPersonality personality, int population, AiFaith faith, boolean handMade, int style) {
        public Territory(UUID id, boolean ai, boolean mine, String label, TitleRank rank, int color, String ruler,
                         boolean rulerFemale, AiPersonality personality, int population) {
            this(id, ai, mine, label, rank, color, ruler, rulerFemale, personality, population, null, false, -1);
        }

        public Territory(UUID id, boolean ai, boolean mine, String label, TitleRank rank, int color, String ruler,
                         boolean rulerFemale, AiPersonality personality, int population, AiFaith faith, boolean handMade) {
            this(id, ai, mine, label, rank, color, ruler, rulerFemale, personality, population, faith, handMade, -1);
        }
    }

    /**
     * Un siège sur la carte. {@code province} : une province IA (même conquise), au territoire plus large qu'une colonie.
     *
     * @param provinceId l'identifiant de la province IA, −1 pour une colonie
     * @param origin     l'indice du royaume IA d'origine d'une province (celui qui la tient s'il n'a pas été conquis), sinon −1
     */
    public record Seat(int territory, String name, int x, int z, boolean capital, int dejure, boolean village, boolean province,
                       int provinceId, int origin, SiteInfo site, int town) {
        public Seat(int territory, String name, int x, int z, boolean capital, int dejure, boolean village, boolean province) {
            this(territory, name, x, z, capital, dejure, village, province, -1, -1, null, -1);
        }

        public Seat(int territory, String name, int x, int z, boolean capital, int dejure, boolean village, boolean province,
                    int provinceId, int origin) {
            this(territory, name, x, z, capital, dejure, village, province, provinceId, origin, null, -1);
        }

        public Seat(int territory, String name, int x, int z, boolean capital, int dejure, boolean village, boolean province,
                    int provinceId, int origin, SiteInfo site) {
            this(territory, name, x, z, capital, dejure, village, province, provinceId, origin, site, -1);
        }
    }

    /**
     * Une construction sur la carte (P14) : son identifiant (vide pour un poste de garde MineColonies), sa nature
     * ({@link #GUARD_POST} pour un poste de garde), son territoire, l'avancement du chantier et sa garnison.
     */
    public record SiteInfo(java.util.UUID id, int kind, int radius, int percent, int garrison, boolean town) {
        public static final int GUARD_POST = 99;

        public SiteInfo(java.util.UUID id, int kind, int radius, int percent, int garrison) {
            this(id, kind, radius, percent, garrison, false);
        }
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

    /**
     * La carte de guerre (P18) telle que ce joueur la voit : les plans de son royaume et de ses alliés, et les sièges en
     * cours qui les concernent. {@code enabled} : il a un royaume, donc un plan à tracer.
     *
     * <p>L'état des guerres de son royaume s'y ajoute (P18b) : les provinces occupées et leur occupant, et les deux
     * camps, pour tracer la ligne de front.
     */
    public record War(boolean enabled, List<WarPlan> plans, List<SiegeMark> sieges, List<Occupation> occupied,
                      List<UUID> friends, List<UUID> foes) {
        public static final War NONE = new War(false, List.of(), List.of());

        public War(boolean enabled, List<WarPlan> plans, List<SiegeMark> sieges) {
            this(enabled, plans, sieges, List.of(), List.of(), List.of());
        }

        /** Son propre plan, s'il est dans la liste. */
        public WarPlan own() {
            for (WarPlan p : plans) if (p.mine()) return p;
            return null;
        }
    }

    /**
     * Le plan d'un royaume dans cette dimension : ses zones (chunk et rang de {@link fr.crusadecraft.core.war.WarMap.Zone}),
     * ses flèches et ses repères. {@code mine} : le sien, celui qu'on modifie.
     */
    public record WarPlan(UUID realm, int color, boolean mine, net.minecraft.network.chat.Component name, long[] keys, byte[] kinds,
                          List<fr.crusadecraft.core.war.WarMap.Arrow> arrows, List<fr.crusadecraft.core.war.WarMap.Pin> pins) {}

    /** Une province IA occupée pendant une guerre, et la couleur de son occupant. */
    public record Occupation(int province, int color) {}

    /** Un siège en cours : où, par qui, contre qui, et combien de défenseurs tiennent encore. */
    public record SiegeMark(int x, int z, int color, net.minecraft.network.chat.Component attacker,
                            net.minecraft.network.chat.Component defender, int garrison) {}

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
            buf.writeVarInt(t.style() + 1);
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
                buf.writeBoolean(s.site().town());
            }
            buf.writeVarInt(s.town() + 1);
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
        writeWar(buf, war);
        buf.writeVarInt(routes.size());
        for (TradeLine r : routes) {
            buf.writeInt(r.x0());
            buf.writeInt(r.z0());
            buf.writeInt(r.x1());
            buf.writeInt(r.z1());
            buf.writeVarLong(r.income());
            buf.writeComponent(r.a());
            buf.writeComponent(r.b());
            buf.writeComponent(r.goods());
            buf.writeBoolean(r.caravan());
            buf.writeInt(r.cx());
            buf.writeInt(r.cz());
            buf.writeBoolean(r.raided());
        }
        buf.writeVarInt(armies.size());
        for (ArmyPawn a : armies) {
            buf.writeInt(a.x());
            buf.writeInt(a.z());
            buf.writeVarInt(a.men());
            buf.writeVarInt(a.soldiers());
            buf.writeInt(a.color());
            buf.writeComponent(a.name());
            buf.writeBoolean(a.mine());
            buf.writeBoolean(a.beaten());
            buf.writeBoolean(a.hasTarget());
            buf.writeInt(a.targetX());
            buf.writeInt(a.targetZ());
        }
        buf.writeVarInt(travellers.size());
        for (Traveller t : travellers) {
            buf.writeInt(t.x());
            buf.writeInt(t.z());
            buf.writeInt(t.toX());
            buf.writeInt(t.toZ());
            buf.writeUtf(t.name());
            buf.writeUtf(t.from());
            buf.writeUtf(t.to());
            buf.writeVarInt(t.percent());
            buf.writeBoolean(t.caravan());
            buf.writeUtf(t.cargo());
        }
        buf.writeVarInt(commerce.size());
        for (CommerceLine l : commerce) {
            buf.writeVarIntArray(l.xs());
            buf.writeVarIntArray(l.zs());
            buf.writeUtf(l.from());
            buf.writeUtf(l.to());
            buf.writeUtf(l.what());
            buf.writeVarInt(l.trips());
            buf.writeBoolean(l.ai());
            buf.writeUtf(l.trouble());
        }
        buf.writeVarInt(blocked.size());
        for (Blocked b : blocked) {
            buf.writeInt(b.x());
            buf.writeInt(b.z());
            buf.writeUtf(b.name());
            buf.writeUtf(b.why());
        }
    }

    private static void writeWar(FriendlyByteBuf buf, War war) {
        buf.writeBoolean(war.enabled());
        buf.writeVarInt(war.plans().size());
        for (WarPlan p : war.plans()) {
            buf.writeUUID(p.realm());
            buf.writeInt(p.color());
            buf.writeBoolean(p.mine());
            buf.writeComponent(p.name());
            buf.writeLongArray(p.keys());
            buf.writeByteArray(p.kinds());
            buf.writeVarInt(p.arrows().size());
            for (fr.crusadecraft.core.war.WarMap.Arrow a : p.arrows()) {
                buf.writeUUID(a.id());
                buf.writeInt(a.x0());
                buf.writeInt(a.z0());
                buf.writeInt(a.x1());
                buf.writeInt(a.z1());
            }
            buf.writeVarInt(p.pins().size());
            for (fr.crusadecraft.core.war.WarMap.Pin pin : p.pins()) {
                buf.writeUUID(pin.id());
                buf.writeInt(pin.x());
                buf.writeInt(pin.z());
                buf.writeEnum(pin.kind());
                buf.writeUtf(pin.note());
            }
        }
        buf.writeVarInt(war.sieges().size());
        for (SiegeMark s : war.sieges()) {
            buf.writeInt(s.x());
            buf.writeInt(s.z());
            buf.writeInt(s.color());
            buf.writeComponent(s.attacker());
            buf.writeComponent(s.defender());
            buf.writeVarInt(s.garrison());
        }
        buf.writeVarInt(war.occupied().size());
        for (Occupation o : war.occupied()) {
            buf.writeVarInt(o.province());
            buf.writeInt(o.color());
        }
        buf.writeCollection(war.friends(), FriendlyByteBuf::writeUUID);
        buf.writeCollection(war.foes(), FriendlyByteBuf::writeUUID);
    }

    /** Les flèches et les repères reçus sont tous dans la dimension de la carte : elle n'est pas répétée. */
    private static War readWar(FriendlyByteBuf buf, ResourceLocation dim) {
        boolean enabled = buf.readBoolean();
        int n = buf.readVarInt();
        List<WarPlan> plans = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID realm = buf.readUUID();
            int color = buf.readInt();
            boolean mine = buf.readBoolean();
            net.minecraft.network.chat.Component name = buf.readComponent();
            long[] keys = buf.readLongArray();
            byte[] kinds = buf.readByteArray();
            int na = buf.readVarInt();
            List<fr.crusadecraft.core.war.WarMap.Arrow> arrows = new ArrayList<>(na);
            for (int k = 0; k < na; k++) {
                arrows.add(new fr.crusadecraft.core.war.WarMap.Arrow(buf.readUUID(), dim, buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
            }
            int np = buf.readVarInt();
            List<fr.crusadecraft.core.war.WarMap.Pin> pins = new ArrayList<>(np);
            for (int k = 0; k < np; k++) {
                pins.add(new fr.crusadecraft.core.war.WarMap.Pin(buf.readUUID(), dim, buf.readInt(), buf.readInt(),
                        buf.readEnum(fr.crusadecraft.core.war.WarMap.Mark.class), buf.readUtf()));
            }
            plans.add(new WarPlan(realm, color, mine, name, keys, kinds, arrows, pins));
        }
        n = buf.readVarInt();
        List<SiegeMark> sieges = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sieges.add(new SiegeMark(buf.readInt(), buf.readInt(), buf.readInt(), buf.readComponent(), buf.readComponent(), buf.readVarInt()));
        }
        n = buf.readVarInt();
        List<Occupation> occupied = new ArrayList<>(n);
        for (int i = 0; i < n; i++) occupied.add(new Occupation(buf.readVarInt(), buf.readInt()));
        List<UUID> friends = buf.readList(FriendlyByteBuf::readUUID), foes = buf.readList(FriendlyByteBuf::readUUID);
        return new War(enabled, plans, sieges, occupied, friends, foes);
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
            boolean handMade = buf.readBoolean();
            territories.add(new Territory(id, ai, mine, label, rank, color, ruler, female, personality, population, faith, handMade, buf.readVarInt() - 1));
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
                site = new SiteInfo(siteId, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
            }
            seats.add(new Seat(territory, name, x, z, capital, dj, village, province, provinceId, origin, site, buf.readVarInt() - 1));
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
        War war = readWar(buf, dim);
        n = buf.readVarInt();
        List<TradeLine> routes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            routes.add(new TradeLine(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarLong(), buf.readComponent(), buf.readComponent(),
                    buf.readComponent(), buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readBoolean()));
        }
        n = buf.readVarInt();
        List<ArmyPawn> armies = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            armies.add(new ArmyPawn(buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readComponent(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readInt()));
        }
        n = buf.readVarInt();
        List<Traveller> travellers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            travellers.add(new Traveller(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readBoolean(), buf.readUtf()));
        }
        n = buf.readVarInt();
        List<CommerceLine> commerce = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            commerce.add(new CommerceLine(buf.readVarIntArray(), buf.readVarIntArray(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readBoolean(), buf.readUtf()));
        }
        n = buf.readVarInt();
        List<Blocked> blocked = new ArrayList<>(n);
        for (int i = 0; i < n; i++) blocked.add(new Blocked(buf.readInt(), buf.readInt(), buf.readUtf(), buf.readUtf()));
        return new MapView(dim, fx, fz, px, pz, radius, colonyRadius, territories, seats, dejure, canEdit, editing, new Borders(keys, owners), places,
                war, routes, armies, travellers, commerce, blocked);
    }
}
