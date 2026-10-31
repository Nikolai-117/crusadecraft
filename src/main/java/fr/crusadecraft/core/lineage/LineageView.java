package fr.crusadecraft.core.lineage;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le registre des lignées tel que l'écran le reçoit : les maisons et les communautés qu'on peut voir, les royaumes
 * à qui en rattacher, et la maison ou la communauté ouverte, personne par personne.
 *
 * @param canEdit ce joueur peut modifier le registre (les mêmes droits que l'éditeur de la carte)
 * @param mca     Minecraft Comes Alive est là : on peut faire apparaître les personnes
 */
public record LineageView(boolean canEdit, boolean mca, List<HouseLine> houses, List<CommunityLine> communities,
                          List<RealmChoice> realms, Selection selection) {

    public record HouseLine(UUID id, String name, int color, Component realm, int living, int total) {}

    public record CommunityLine(UUID id, String name, CommunityKind kind, Component realm, int count) {}

    public record RealmChoice(UUID id, Component label) {}

    /**
     * La maison ou la communauté ouverte.
     *
     * @param rows ses personnes, déjà rangées (par génération pour une maison, par foyer pour une communauté)
     */
    public record Selection(boolean house, UUID id, String title, Component realm, CommunityKind kind, List<Row> rows) {}

    /**
     * Une personne.
     *
     * @param group  génération (maison, à partir de 0) ; pour une communauté, l'indice de son foyer
     * @param spouse le nom de son conjoint, ou vide
     */
    public record Row(UUID id, String name, boolean female, int age, boolean alive, Role role, int group, String spouse,
                      boolean inWorld, boolean member, int traitMask, String bio) {
        public Row(UUID id, String name, boolean female, int age, boolean alive, Role role, int group, String spouse,
                   boolean inWorld, boolean member) {
            this(id, name, female, age, alive, role, group, spouse, inWorld, member, 0, "");
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(canEdit);
        buf.writeBoolean(mca);
        buf.writeVarInt(houses.size());
        for (HouseLine h : houses) {
            buf.writeUUID(h.id());
            buf.writeUtf(h.name());
            buf.writeInt(h.color());
            buf.writeComponent(h.realm());
            buf.writeVarInt(h.living());
            buf.writeVarInt(h.total());
        }
        buf.writeVarInt(communities.size());
        for (CommunityLine c : communities) {
            buf.writeUUID(c.id());
            buf.writeUtf(c.name());
            buf.writeEnum(c.kind());
            buf.writeComponent(c.realm());
            buf.writeVarInt(c.count());
        }
        buf.writeVarInt(realms.size());
        for (RealmChoice r : realms) {
            buf.writeUUID(r.id());
            buf.writeComponent(r.label());
        }
        buf.writeBoolean(selection != null);
        if (selection != null) {
            buf.writeBoolean(selection.house());
            buf.writeUUID(selection.id());
            buf.writeUtf(selection.title());
            buf.writeComponent(selection.realm());
            buf.writeBoolean(selection.kind() != null);
            if (selection.kind() != null) buf.writeEnum(selection.kind());
            buf.writeVarInt(selection.rows().size());
            for (Row r : selection.rows()) {
                buf.writeUUID(r.id());
                buf.writeUtf(r.name());
                buf.writeBoolean(r.female());
                buf.writeVarInt(r.age());
                buf.writeBoolean(r.alive());
                buf.writeEnum(r.role());
                buf.writeVarInt(r.group());
                buf.writeUtf(r.spouse());
                buf.writeBoolean(r.inWorld());
                buf.writeBoolean(r.member());
                buf.writeInt(r.traitMask());
                buf.writeUtf(r.bio());
            }
        }
    }

    public static LineageView read(FriendlyByteBuf buf) {
        boolean canEdit = buf.readBoolean(), mca = buf.readBoolean();
        int n = buf.readVarInt();
        List<HouseLine> houses = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            houses.add(new HouseLine(buf.readUUID(), buf.readUtf(), buf.readInt(), buf.readComponent(), buf.readVarInt(), buf.readVarInt()));
        }
        n = buf.readVarInt();
        List<CommunityLine> communities = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            communities.add(new CommunityLine(buf.readUUID(), buf.readUtf(), buf.readEnum(CommunityKind.class), buf.readComponent(), buf.readVarInt()));
        }
        n = buf.readVarInt();
        List<RealmChoice> realms = new ArrayList<>(n);
        for (int i = 0; i < n; i++) realms.add(new RealmChoice(buf.readUUID(), buf.readComponent()));
        Selection selection = null;
        if (buf.readBoolean()) {
            boolean house = buf.readBoolean();
            UUID id = buf.readUUID();
            String title = buf.readUtf();
            Component realm = buf.readComponent();
            CommunityKind kind = buf.readBoolean() ? buf.readEnum(CommunityKind.class) : null;
            n = buf.readVarInt();
            List<Row> rows = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                rows.add(new Row(buf.readUUID(), buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(),
                        buf.readEnum(Role.class), buf.readVarInt(), buf.readUtf(), buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readUtf()));
            }
            selection = new Selection(house, id, title, realm, kind, rows);
        }
        return new LineageView(canEdit, mca, houses, communities, realms, selection);
    }
}
