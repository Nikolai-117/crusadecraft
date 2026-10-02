package fr.crusadecraft.core.realm;

/**
 * Rangs de titre, du plus bas au plus haut, à la manière de CK3.
 *
 * <p>Le nombre de provinces requis n'est qu'une valeur par défaut : la Chancery Table et la
 * configuration en décident. Le titre féminin sert quand la souveraine choisit cette forme.
 */
public enum TitleRank {
    BARONNIE("barony"),
    COMTE("county"),
    DUCHE("duchy"),
    ROYAUME("kingdom"),
    EMPIRE("empire");

    private final String key;

    TitleRank(String key) {
        this.key = key;
    }

    /** Clé de traduction du nom du titre (« Baronnie »). */
    public String titleKey() {
        return "crusadecraft.rank." + key;
    }

    /** Clé de traduction du titre porté (« Baron » / « Baronne »). */
    public String holderKey(boolean feminine) {
        return "crusadecraft.rank." + key + (feminine ? ".holder.f" : ".holder.m");
    }

    public TitleRank next() {
        TitleRank[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1] : this;
    }

    public boolean isAtLeast(TitleRank other) {
        return ordinal() >= other.ordinal();
    }

    public static TitleRank byName(String name, TitleRank fallback) {
        for (TitleRank r : values()) {
            if (r.name().equals(name)) return r;
        }
        return fallback;
    }
}
