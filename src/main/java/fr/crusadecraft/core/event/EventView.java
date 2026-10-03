package fr.crusadecraft.core.event;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Un événement en attente, tel que l'écran le montre. {@code waiting} : combien d'événements attendent en tout. */
public record EventView(UUID id, Component title, Component text, ResourceLocation icon, List<OptionView> options, long daysLeft,
                        int defaultOption, int waiting) {

    /** Une option : son texte, si on peut la choisir, et ses effets en clair. */
    public record OptionView(Component text, boolean allowed, List<Component> effects) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeComponent(title);
        buf.writeComponent(text);
        buf.writeResourceLocation(icon);
        buf.writeVarInt(options.size());
        for (OptionView o : options) {
            buf.writeComponent(o.text());
            buf.writeBoolean(o.allowed());
            buf.writeVarInt(o.effects().size());
            for (Component c : o.effects()) buf.writeComponent(c);
        }
        buf.writeVarLong(daysLeft);
        buf.writeVarInt(defaultOption);
        buf.writeVarInt(waiting);
    }

    public static EventView read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        Component title = buf.readComponent(), text = buf.readComponent();
        ResourceLocation icon = buf.readResourceLocation();
        int n = buf.readVarInt();
        List<OptionView> options = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Component t = buf.readComponent();
            boolean allowed = buf.readBoolean();
            int m = buf.readVarInt();
            List<Component> fx = new ArrayList<>(m);
            for (int j = 0; j < m; j++) fx.add(buf.readComponent());
            options.add(new OptionView(t, allowed, fx));
        }
        return new EventView(id, title, text, icon, options, buf.readVarLong(), buf.readVarInt(), buf.readVarInt());
    }
}
