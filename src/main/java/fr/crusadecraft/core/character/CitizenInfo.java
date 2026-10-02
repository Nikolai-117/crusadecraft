package fr.crusadecraft.core.character;

/**
 * Relevé d'un citoyen dans MineColonies, au moment où on le demande. Rien n'est réécrit chez eux.
 *
 * @param jobKey    clé de traduction du métier MineColonies, ou vide s'il est sans emploi
 * @param skills    niveaux des onze compétences, dans l'ordre de {@link CitizenSkill}
 * @param happiness bonheur du citoyen (0 à 10)
 * @param entityId  identifiant de l'entité si elle est chargée, sinon -1
 */
public record CitizenInfo(CitizenRef ref, String name, boolean female, boolean child, String jobKey, int[] skills,
                          double happiness, int entityId) {
}
