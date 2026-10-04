package fr.crusadecraft.core.place;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Les lieux tels que le serveur les confie à un joueur : l'arbre (les lieux qu'il connaît, dans l'ordre), le lieu
 * choisi avec sa fiche, et la chaîne des lieux où il se tient.
 *
 * @param canEdit  droits de l'éditeur (tout modifier)
 * @param here     la chaîne des lieux à sa position, du plus proche au plus vaste (déjà composée)
 * @param realms   les royaumes qu'on peut désigner comme tenancier d'un lieu posé à la main
 * @param status   le résultat de la dernière action, ou {@code null}
 */
public record LieuView(boolean canEdit, List<Entry> entries, Selection selection, Component here, List<Choice> realms,
                       Component status, boolean statusOk) {

    /** Une ligne de l'arbre. */
    public record Entry(UUID id, int kind, Component name, int depth, int color, boolean linked, int population, boolean hasText) {}

    /** Un royaume qu'on peut choisir. */
    public record Choice(UUID id, Component name) {}

    /**
     * Le lieu choisi, avec ce que le joueur peut en faire.
     *
     * @param origin    d'où vient le lieu (« Province d'un royaume IA », « Posé à la main »…)
     * @param rawName   le nom tel qu'il se modifie (le nom de lieu d'un titre, sans « Duché de »)
     */
    public record Selection(UUID id, int kind, Component name, Component origin, Component holder, UUID parent, Component parentName,
                            int population, boolean hasPos, int x, int z, int radius, long foundedDay, String text, String rawName,
                            boolean textEditable, boolean renamable, boolean deletable, boolean reparentable) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(canEdit);
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeUUID(e.id());
            buf.writeVarInt(e.kind());
            buf.writeComponent(e.name());
            buf.writeVarInt(e.depth());
            buf.writeInt(e.color());
            buf.writeBoolean(e.linked());
            buf.writeVarInt(e.population());
            buf.writeBoolean(e.hasText());
        }
        buf.writeBoolean(selection != null);
        if (selection != null) {
            Selection s = selection;
            buf.writeUUID(s.id());
            buf.writeVarInt(s.kind());
            buf.writeComponent(s.name());
            buf.writeComponent(s.origin());
            buf.writeComponent(s.holder());
            buf.writeBoolean(s.parent() != null);
            if (s.parent() != null) buf.writeUUID(s.parent());
            buf.writeComponent(s.parentName());
            buf.writeVarInt(s.population());
            buf.writeBoolean(s.hasPos());
            buf.writeInt(s.x());
            buf.writeInt(s.z());
            buf.writeVarInt(s.radius());
            buf.writeLong(s.foundedDay());
            buf.writeUtf(s.text(), Lieu.MAX_TEXT * 4);
            buf.writeUtf(s.rawName(), 256);
            buf.writeBoolean(s.textEditable());
            buf.writeBoolean(s.renamable());
            buf.writeBoolean(s.deletable());
            buf.writeBoolean(s.reparentable());
        }
        buf.writeComponent(here);
        buf.writeVarInt(realms.size());
        for (Choice c : realms) {
            buf.writeUUID(c.id());
            buf.writeComponent(c.name());
        }
        buf.writeBoolean(status != null);
        if (status != null) buf.writeComponent(status);
        buf.writeBoolean(statusOk);
    }

    public static LieuView read(FriendlyByteBuf buf) {
        boolean canEdit = buf.readBoolean();
        int n = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            entries.add(new Entry(buf.readUUID(), buf.readVarInt(), buf.readComponent(), buf.readVarInt(), buf.readInt(), buf.readBoolean(),
                    buf.readVarInt(), buf.readBoolean()));
        }
        Selection selection = null;
        if (buf.readBoolean()) {
            UUID id = buf.readUUID();
            int kind = buf.readVarInt();
            Component name = buf.readComponent(), origin = buf.readComponent(), holder = buf.readComponent();
            UUID parent = buf.readBoolean() ? buf.readUUID() : null;
            Component parentName = buf.readComponent();
            int population = buf.readVarInt();
            boolean hasPos = buf.readBoolean();
            int x = buf.readInt(), z = buf.readInt(), radius = buf.readVarInt();
            long founded = buf.readLong();
            String text = buf.readUtf(Lieu.MAX_TEXT * 4), rawName = buf.readUtf(256);
            selection = new Selection(id, kind, name, origin, holder, parent, parentName, population, hasPos, x, z, radius, founded, text, rawName,
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }
        Component here = buf.readComponent();
        int r = buf.readVarInt();
        List<Choice> realms = new ArrayList<>(r);
        for (int i = 0; i < r; i++) realms.add(new Choice(buf.readUUID(), buf.readComponent()));
        Component status = buf.readBoolean() ? buf.readComponent() : null;
        return new LieuView(canEdit, entries, selection, here, realms, status, buf.readBoolean());
    }
}
