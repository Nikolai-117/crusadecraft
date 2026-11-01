package fr.crusadecraft.core.build.plan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Les plans favoris du monde (P35) : les pièces que l'on préfère, marquées d'une étoile dans le catalogue. Ils sont
 * communs à tout le monde (ce sont ceux que les villes du monde prendront d'abord), enregistrés avec lui.
 */
public final class PlanFavorites extends SavedData {
    private static final String NAME = "crusadecraft_plans_favoris";
    private final Set<String> refs = new LinkedHashSet<>();

    public PlanFavorites() {}

    /** Les favoris du monde ouvert (pour le plan des villes, qui n'a pas le serveur sous la main). */
    private static volatile PlanFavorites current;

    public static PlanFavorites get(MinecraftServer server) {
        PlanFavorites f = server.overworld().getDataStorage().computeIfAbsent(PlanFavorites::load, PlanFavorites::new, NAME);
        current = f;
        return f;
    }

    /** Les favoris du monde ouvert, vides s'il n'y en a pas. */
    public static Set<String> current() {
        PlanFavorites f = current;
        return f == null ? Set.of() : Set.copyOf(f.refs);
    }

    public static void onServerStopped() {
        current = null;
    }

    public List<String> all() {
        return new ArrayList<>(refs);
    }

    public boolean has(String ref) {
        return refs.contains(ref);
    }

    /** Ajoute ou retire ; renvoie vrai si le plan est désormais favori. */
    public boolean toggle(String ref) {
        boolean now = !refs.remove(ref);
        if (now) refs.add(ref);
        setDirty();
        return now;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        for (String r : refs) l.add(StringTag.valueOf(r));
        t.put("refs", l);
        return t;
    }

    public static PlanFavorites load(CompoundTag t) {
        PlanFavorites f = new PlanFavorites();
        ListTag l = t.getList("refs", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) f.refs.add(l.getString(i));
        return f;
    }
}
