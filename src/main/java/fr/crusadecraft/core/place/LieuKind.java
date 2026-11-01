package fr.crusadecraft.core.place;

import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.realm.TitleRank;

import java.util.Locale;

/**
 * Les genres de lieux, du plus vaste au plus petit. Les cinq premiers sont des <b>titres</b> (un royaume, un duché…),
 * les autres des <b>places</b> qu'on trouve sur la carte.
 */
public enum LieuKind {
    EMPIRE(TitleRank.EMPIRE, '♛'),
    ROYAUME(TitleRank.ROYAUME, '♚'),
    DUCHE(TitleRank.DUCHE, '♜'),
    COMTE(TitleRank.COMTE, '♝'),
    BARONNIE(TitleRank.BARONNIE, '♞'),
    VILLE(null, '■'),
    VILLAGE(null, '▪'),
    AVANT_POSTE(null, '▲'),
    FORT(null, '♖'),
    LIEU_SAINT(null, '✚'),
    RUINE(null, '☗'),
    REGION(null, '◌');

    private final TitleRank rank;
    private final char icon;

    LieuKind(TitleRank rank, char icon) {
        this.rank = rank;
        this.icon = icon;
    }

    /** Un titre (empire, royaume, duché, comté, baronnie) plutôt qu'une place. */
    public boolean title() {
        return rank != null;
    }

    /** Le rang de titre correspondant ({@code null} pour une place). */
    public TitleRank rank() {
        return rank;
    }

    public char icon() {
        return icon;
    }

    public String key() {
        return "crusadecraft.lieu.kind." + name().toLowerCase(Locale.ROOT);
    }

    /** Les genres qu'on pose à la main : les titres, et les lieux qui ne naissent d'aucune autre chose. */
    public boolean manual() {
        return title() || this == LIEU_SAINT || this == RUINE || this == REGION;
    }

    /** Les places qui se posent au bâton ou sur la carte, en un point (une région a en plus un rayon). */
    public boolean placed() {
        return this == LIEU_SAINT || this == RUINE || this == REGION;
    }

    public static LieuKind of(TitleRank rank) {
        for (LieuKind k : values()) if (k.rank == rank) return k;
        return COMTE;
    }

    /**
     * Le genre de lieu d'une construction, ou {@code null} quand elle n'en est pas un à elle seule : un jalon n'est
     * qu'une marque, un donjon fait partie de sa ville, et un village est déjà une ville du registre.
     */
    public static LieuKind of(SiteKind kind) {
        return switch (kind) {
            case TOUR, CAMP, AVANT_POSTE -> AVANT_POSTE;
            case FORT -> FORT;
            case BORNE, DONJON, VILLAGE, QUARTIER -> null;
        };
    }

    public static LieuKind byName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return LIEU_SAINT;
        }
    }

    public static LieuKind byOrdinal(int ordinal) {
        LieuKind[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : LIEU_SAINT;
    }
}
