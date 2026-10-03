package fr.crusadecraft.core.lineage;

import java.util.List;
import java.util.Locale;

/**
 * La nature d'une communauté : elle décide qui la peuple quand on la génère (des familles de paysans pour un
 * village, des chevaliers et des clercs sans enfants pour un ordre…).
 */
public enum CommunityKind {
    VILLAGE(true, List.of(Role.PAYSAN, Role.PAYSAN, Role.ARTISAN, Role.MARCHAND)),
    GUILDE(true, List.of(Role.ARTISAN, Role.ARTISAN, Role.MARCHAND)),
    CLAN(true, List.of(Role.PAYSAN, Role.SOLDAT, Role.ARTISAN)),
    COMPAGNIE(false, List.of(Role.SOLDAT, Role.SOLDAT, Role.CHEVALIER)),
    ORDRE(false, List.of(Role.CHEVALIER, Role.CHEVALIER, Role.CLERC)),
    MONASTERE(false, List.of(Role.CLERC, Role.CLERC, Role.ERUDIT));

    private final boolean families;
    private final List<Role> roles;

    CommunityKind(boolean families, List<Role> roles) {
        this.families = families;
        this.roles = roles;
    }

    /** Des foyers (couples et enfants), ou des membres seuls (ordres, compagnies, monastères). */
    public boolean families() {
        return families;
    }

    /** Les métiers des adultes, tirés dans cette liste (un métier répété y est plus fréquent). */
    public List<Role> roles() {
        return roles;
    }

    public String key() {
        return "crusadecraft.lineage.kind." + name().toLowerCase(Locale.ROOT);
    }

    public static CommunityKind byName(String name, CommunityKind fallback) {
        try {
            return CommunityKind.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }
}
