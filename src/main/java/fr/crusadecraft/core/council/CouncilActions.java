package fr.crusadecraft.core.council;

import net.minecraft.network.chat.Component;

/** Résultat d'une action du souverain sur sa cour : réussite ou non, et le message à lui montrer. */
public final class CouncilActions {

    private CouncilActions() {}

    public record Outcome(boolean success, Component message) {
        public static Outcome ok(Component message) {
            return new Outcome(true, message);
        }

        public static Outcome no(String key, Object... args) {
            return new Outcome(false, Component.translatable(key, args));
        }
    }
}
