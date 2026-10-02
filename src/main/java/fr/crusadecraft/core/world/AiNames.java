package fr.crusadecraft.core.world;

import fr.crusadecraft.core.realm.TitleRank;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Noms composés des royaumes IA : « Comté de Valcourt », « Duché d'Ambrevent », « Maison de Rochefort ». */
public final class AiNames {

    private AiNames() {}

    /** Vrai si l'on élide « de » devant ce nom (voyelle ou h muet). */
    static boolean elides(String place) {
        if (place == null || place.isEmpty()) return false;
        char c = Character.toLowerCase(place.charAt(0));
        return "aeiouyéèêâîôûh".indexOf(c) >= 0 && !place.toLowerCase(Locale.ROOT).startsWith("hau")
                && !place.toLowerCase(Locale.ROOT).startsWith("hêt");
    }

    public static Component realm(TitleRank rank, String place) {
        return Component.translatable(elides(place) ? "crusadecraft.ai.realm.elided" : "crusadecraft.ai.realm",
                Component.translatable(rank.titleKey()), place);
    }

    /** « du Comté de Valcourt », « de la Baronnie d'Ambrevent » : pour « les terres de… », « le héraut de… ». */
    public static Component realmOf(TitleRank rank, String place) {
        return Component.translatable(elides(place) ? "crusadecraft.ai.realm.elided" : "crusadecraft.ai.realm",
                Component.translatable(rank.ofKey()), place);
    }

    public static Component house(String place) {
        return Component.translatable(elides(place) ? "crusadecraft.ai.house.elided" : "crusadecraft.ai.house", place);
    }
}
