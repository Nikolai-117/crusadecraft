package fr.crusadecraft.core.build.plan;

import java.util.Locale;
import java.util.Optional;

/**
 * Où trouver un plan (P34) : un pack de style lu par Structurize (ceux de MineColonies, Byzantine, ceux du joueur),
 * ou un fichier de structure (.nbt) ou de WorldEdit (.schem) dans un dossier de plans. Rien n'est copié : le plan est
 * relu là où il est, au moment du jeu.
 *
 * @param source d'où il vient
 * @param pack   le nom du pack (PACK), ou le dossier de plans (FILE : {@code plans}, {@code schematics}, {@code worldedit})
 * @param path   son chemin dans le pack ou le dossier, avec l'extension, séparé par des « / »
 */
public record PlanRef(Source source, String pack, String path) {

    public enum Source { PACK, FILE }

    public String encode() {
        return (source == Source.PACK ? "pack:" : "file:") + pack + "|" + path;
    }

    public static Optional<PlanRef> decode(String s) {
        if (s == null) return Optional.empty();
        Source source;
        if (s.startsWith("pack:")) source = Source.PACK;
        else if (s.startsWith("file:")) source = Source.FILE;
        else return Optional.empty();
        String rest = s.substring(5);
        int bar = rest.indexOf('|');
        if (bar <= 0 || bar == rest.length() - 1) return Optional.empty();
        return Optional.of(new PlanRef(source, rest.substring(0, bar), rest.substring(bar + 1)));
    }

    /** « townhall3 » pour « fundamentals/townhall3.blueprint » : le dernier morceau, sans extension. */
    public String label() {
        return label(path);
    }

    public static String label(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Le dossier du plan dans son pack (« fundamentals »), vide à la racine. */
    public String category() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    /** Un nom de pack ou de dossier ramené à ses lettres et chiffres, en minuscules : « Medieval Oak » → « medievaloak ». */
    public static String normalize(String name) {
        StringBuilder b = new StringBuilder();
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) if (Character.isLetterOrDigit(c)) b.append(c);
        return b.toString();
    }
}
