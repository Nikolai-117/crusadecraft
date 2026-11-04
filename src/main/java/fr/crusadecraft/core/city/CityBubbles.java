package fr.crusadecraft.core.city;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Les paroles au-dessus des têtes (P42) : quand un habitant dit quelque chose, une petite bulle de texte le suit quelques
 * secondes. C'est un texte flottant de Minecraft, que le client affiche sans rien savoir de ce mod. Elle n'est jamais
 * enregistrée dans le monde ; celle qu'un arrêt brutal y laisserait est balayée à la visite suivante.
 */
public final class CityBubbles {
    /** La marque des bulles, pour balayer celles qui resteraient. */
    public static final String TAG = "crusadecraft_bulle";
    /** Une bulle dure de 4 à 9 secondes, selon sa longueur. */
    private static final int MIN_TICKS = 80, MAX_TICKS = 180;
    /** Au plus, tant de bulles à la fois dans le monde : une foule qui parle ne se lit plus. */
    private static final int MAX = 12;

    private record Bubble(Display.TextDisplay display, UUID speaker, ServerLevel level, long until, double height) {}

    private static final List<Bubble> ACTIVE = new ArrayList<>();

    private CityBubbles() {}

    public static void onServerStopped() {
        for (Bubble b : ACTIVE) b.display().discard();
        ACTIVE.clear();
    }

    public static int active() {
        return ACTIVE.size();
    }

    private static final float SIZE = 0.62f;

    private static net.minecraft.nbt.ListTag floats(float... values) {
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        for (float v : values) l.add(net.minecraft.nbt.FloatTag.valueOf(v));
        return l;
    }

    /** La durée d'une bulle : assez pour la lire. */
    static int duration(int length) {
        return Math.max(MIN_TICKS, Math.min(MAX_TICKS, 50 + length * 2));
    }

    /** Une bulle au-dessus de cette personne, qui remplace la précédente. */
    public static void say(ServerLevel level, Entity speaker, String text) {
        if (text == null || text.isBlank()) return;
        ACTIVE.removeIf(b -> {
            boolean same = b.speaker().equals(speaker.getUUID());
            if (same) b.display().discard();
            return same;
        });
        if (ACTIVE.size() >= MAX) return;
        Display.TextDisplay d = EntityType.TEXT_DISPLAY.create(level);
        if (d == null) return;
        CompoundTag t = new CompoundTag();
        t.putString("text", Component.Serializer.toJson(Component.literal(text)));
        t.putString("billboard", "center");
        t.putInt("line_width", 160);
        t.putInt("background", 0x90201814);
        t.putByte("shadow", (byte) 1);
        // Plus petit que le texte flottant par défaut : on lit une bulle, on n'y lit pas un panneau.
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", floats(0f, 0f, 0f, 1f));
        tr.put("right_rotation", floats(0f, 0f, 0f, 1f));
        tr.put("translation", floats(0f, 0f, 0f));
        tr.put("scale", floats(SIZE, SIZE, SIZE));
        t.put("transformation", tr);
        d.load(t);
        d.setNoGravity(true);
        d.addTag(TAG);
        double height = speaker.getBbHeight() + 0.45;
        d.setPos(speaker.getX(), speaker.getY() + height, speaker.getZ());
        if (!level.addFreshEntity(d)) return;
        ACTIVE.add(new Bubble(d, speaker.getUUID(), level, level.getGameTime() + duration(text.length()), height));
    }

    /** À chaque tick : les bulles suivent leur personne et s'éteignent à leur heure. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        ACTIVE.removeIf(b -> {
            Entity who = b.level().getEntity(b.speaker());
            if (who == null || !who.isAlive() || b.level().getGameTime() >= b.until()) {
                b.display().discard();
                return true;
            }
            b.display().setPos(who.getX(), who.getY() + b.height(), who.getZ());
            return false;
        });
    }

    /** Celles qu'un arrêt brutal aurait laissées dans la ville qu'on visite. */
    static int sweep(ServerLevel level, net.minecraft.world.phys.AABB box) {
        int n = 0;
        for (Display.TextDisplay d : level.getEntitiesOfClass(Display.TextDisplay.class, box, x -> x.getTags().contains(TAG))) {
            if (ACTIVE.stream().anyMatch(b -> b.display() == d)) continue;
            d.discard();
            n++;
        }
        return n;
    }
}
