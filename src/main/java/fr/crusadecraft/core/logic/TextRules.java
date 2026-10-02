package fr.crusadecraft.core.logic;

/**
 * Nettoyage des textes saisis par le joueur (nom du royaume, devise, maison).
 *
 * <p>Tout texte venant du client est revalidé côté serveur : on retire les codes de mise en forme
 * (§), les caractères de contrôle, les espaces superflus, et on borne la longueur.
 */
public final class TextRules {
    public static final int NAME_MAX = 32;
    public static final int MOTTO_MAX = 64;
    public static final int HOUSE_MAX = 32;

    private TextRules() {}

    public static String clean(String raw, int maxLength) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(raw.length());
        boolean lastSpace = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§') { // code de mise en forme : on saute aussi le caractère qui suit
                i++;
                continue;
            }
            if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT) continue;
            if (Character.isWhitespace(c)) {
                if (!lastSpace && out.length() > 0) out.append(' ');
                lastSpace = true;
                continue;
            }
            out.append(c);
            lastSpace = false;
        }
        String result = out.toString().trim();
        if (result.length() > maxLength) {
            result = result.substring(0, maxLength).trim();
        }
        return result;
    }

    /** Un nom est valable s'il reste au moins deux caractères visibles après nettoyage. */
    public static boolean isValidName(String cleaned) {
        return cleaned != null && cleaned.codePointCount(0, cleaned.length()) >= 2;
    }
}
