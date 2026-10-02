package fr.crusadecraft.core.character;

import java.util.Locale;

/**
 * Traits de personnage, à la manière de CK3.
 *
 * <p>Les traits de <b>personnalité</b> vont par paires opposées : un personnage ne peut pas être à la
 * fois brave et lâche. Les traits <b>acquis</b> viennent de ce qu'il vit (guerres, blessures, intrigues).
 *
 * <p>Chaque trait modifie les cinq aptitudes (dans l'ordre de {@link Stat}) et l'opinion du personnage
 * envers son souverain. {@code affinity} rend le trait plus probable chez un citoyen fort dans cette
 * compétence MineColonies : un citoyen très fort est plus souvent brave.
 */
public enum Trait {
    // --- personnalité (paires) ---
    BRAVE(Kind.PERSONALITY, "LACHE", new int[]{0, 2, 0, 0, 0}, 0, CitizenSkill.Strength),
    LACHE(Kind.PERSONALITY, "BRAVE", new int[]{0, -2, 0, 2, 0}, 0, CitizenSkill.Agility),
    AMBITIEUX(Kind.PERSONALITY, "CONTENT", new int[]{1, 1, 1, 1, 1}, -15, CitizenSkill.Intelligence),
    CONTENT(Kind.PERSONALITY, "AMBITIEUX", new int[]{0, 0, 0, -1, 0}, 10, null),
    AVARE(Kind.PERSONALITY, "GENEREUX", new int[]{-1, 0, 2, 0, 0}, 0, CitizenSkill.Knowledge),
    GENEREUX(Kind.PERSONALITY, "AVARE", new int[]{2, 0, -1, 0, 0}, 5, CitizenSkill.Creativity),
    HONNETE(Kind.PERSONALITY, "FOURBE", new int[]{2, 0, 0, -4, 0}, 5, null),
    FOURBE(Kind.PERSONALITY, "HONNETE", new int[]{-2, 0, 0, 4, 0}, -5, CitizenSkill.Dexterity),
    JUSTE(Kind.PERSONALITY, "ARBITRAIRE", new int[]{0, 0, 2, 0, 1}, 5, CitizenSkill.Focus),
    ARBITRAIRE(Kind.PERSONALITY, "JUSTE", new int[]{0, 0, -1, 3, 0}, -5, null),
    CONFIANT(Kind.PERSONALITY, "PARANOIAQUE", new int[]{2, 0, 0, -2, 0}, 10, CitizenSkill.Adaptability),
    PARANOIAQUE(Kind.PERSONALITY, "CONFIANT", new int[]{-1, 0, 0, 3, 0}, -10, null),
    CALME(Kind.PERSONALITY, "COLERIQUE", new int[]{1, 0, 0, 1, 0}, 0, CitizenSkill.Focus),
    COLERIQUE(Kind.PERSONALITY, "CALME", new int[]{-1, 3, 0, -1, 0}, -5, CitizenSkill.Strength),
    COMPATISSANT(Kind.PERSONALITY, "CRUEL", new int[]{2, 0, 0, -2, 0}, 5, null),
    CRUEL(Kind.PERSONALITY, "COMPATISSANT", new int[]{-2, 1, 0, 2, 0}, -5, null),
    DILIGENT(Kind.PERSONALITY, "PARESSEUX", new int[]{1, 1, 1, 1, 1}, 0, CitizenSkill.Stamina),
    PARESSEUX(Kind.PERSONALITY, "DILIGENT", new int[]{-1, -1, -1, -1, -1}, 0, null),
    ZELE(Kind.PERSONALITY, "CYNIQUE", new int[]{0, 1, 0, 0, 2}, 0, CitizenSkill.Mana),
    CYNIQUE(Kind.PERSONALITY, "ZELE", new int[]{0, 0, 0, 2, -1}, 0, CitizenSkill.Intelligence),
    LOYAL(Kind.PERSONALITY, "INCONSTANT", new int[]{0, 0, 0, 0, 0}, 20, null),
    INCONSTANT(Kind.PERSONALITY, "LOYAL", new int[]{0, 0, 0, 1, 0}, -10, CitizenSkill.Agility),
    HUMBLE(Kind.PERSONALITY, "ARROGANT", new int[]{0, 0, 0, 0, 1}, 5, null),
    ARROGANT(Kind.PERSONALITY, "HUMBLE", new int[]{-1, 0, 0, 0, 0}, -5, CitizenSkill.Athletics),

    // --- acquis ---
    VETERAN(Kind.ACQUIRED, null, new int[]{0, 2, 0, 0, 0}, 0, null),
    BLESSE(Kind.ACQUIRED, null, new int[]{0, -1, 0, 0, 0}, 0, null),
    CORROMPU(Kind.ACQUIRED, null, new int[]{0, 0, -2, 2, 0}, 0, null),
    CHAMPION(Kind.ACQUIRED, null, new int[]{0, 3, 0, 0, 0}, 5, null),
    PIEUX(Kind.ACQUIRED, null, new int[]{0, 0, 0, 0, 2}, 5, null);

    public enum Kind { PERSONALITY, ACQUIRED }

    private final Kind kind;
    private final String oppositeName;
    private final int[] statModifiers;
    private final int liegeOpinion;
    private final CitizenSkill affinity;

    Trait(Kind kind, String oppositeName, int[] statModifiers, int liegeOpinion, CitizenSkill affinity) {
        this.kind = kind;
        this.oppositeName = oppositeName;
        this.statModifiers = statModifiers;
        this.liegeOpinion = liegeOpinion;
        this.affinity = affinity;
    }

    public Kind kind() { return kind; }
    public int liegeOpinion() { return liegeOpinion; }
    public CitizenSkill affinity() { return affinity; }

    public int statModifier(Stat stat) {
        return statModifiers[stat.ordinal()];
    }

    public Trait opposite() {
        return oppositeName == null ? null : Trait.valueOf(oppositeName);
    }

    public boolean conflictsWith(Trait other) {
        return other == this || other == opposite();
    }

    /** Clé du nom ; la forme féminine éventuelle est sous « .f ». */
    public String key() {
        return "crusadecraft.trait." + name().toLowerCase(Locale.ROOT);
    }

    public String descriptionKey() {
        return key() + ".desc";
    }

    /** Somme des effets sur les aptitudes : sert à colorer le trait (vert, rouge, neutre). */
    public int balance() {
        int sum = liegeOpinion / 5;
        for (int m : statModifiers) sum += m;
        return sum;
    }

    public static Trait byName(String name) {
        try {
            return Trait.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
