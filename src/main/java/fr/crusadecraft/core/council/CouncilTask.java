package fr.crusadecraft.core.council;

import fr.crusadecraft.core.character.Post;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Les tâches que le souverain confie à ses conseillers, à la manière de CK3.
 *
 * <p>Une tâche « disponible » produit déjà son effet chaque jour. Les autres sont annoncées et
 * s'ouvriront avec les phases qui leur donnent un sens (diplomatie, guerre, intrigues) : l'écran les
 * montre grisées plutôt que de prétendre qu'elles font quelque chose.
 */
public enum CouncilTask {
    // Chancelier
    GERER_LA_COUR(Post.CHANCELIER, true),
    AMELIORER_RELATIONS(Post.CHANCELIER, false),
    FABRIQUER_REVENDICATION(Post.CHANCELIER, false),
    // Maréchal
    ENTRAINER_TROUPES(Post.MARECHAL, true),
    ORGANISER_LEVEES(Post.MARECHAL, false),
    MAINTENIR_ORDRE(Post.MARECHAL, false),
    // Intendant
    GERER_LE_DOMAINE(Post.INTENDANT, true),
    DEVELOPPER_PROVINCE(Post.INTENDANT, false),
    COLLECTER_IMPOTS(Post.INTENDANT, false),
    // Maître-espion
    SURVEILLER_LES_TERRES(Post.MAITRE_ESPION, true),
    DEBUSQUER_COMPLOTS(Post.MAITRE_ESPION, false),
    ESPIONNER_RIVAL(Post.MAITRE_ESPION, false),
    // Chapelain
    LEGITIMITE(Post.CHAPELAIN, true),
    BONHEUR_DU_PEUPLE(Post.CHAPELAIN, true),
    RECHERCHE(Post.CHAPELAIN, false);

    private final Post post;
    private final boolean available;

    CouncilTask(Post post, boolean available) {
        this.post = post;
        this.available = available;
    }

    public Post post() { return post; }
    public boolean available() { return available; }

    public String key() {
        return "crusadecraft.task." + name().toLowerCase(Locale.ROOT);
    }

    public String descriptionKey() {
        return key() + ".desc";
    }

    public static List<CouncilTask> of(Post post) {
        List<CouncilTask> out = new ArrayList<>();
        for (CouncilTask t : values()) if (t.post == post) out.add(t);
        return out;
    }

    /** Tâche par défaut d'un poste : la première disponible. */
    public static CouncilTask defaultFor(Post post) {
        for (CouncilTask t : of(post)) if (t.available) return t;
        return null;
    }

    public static CouncilTask byName(String name) {
        try {
            return CouncilTask.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
