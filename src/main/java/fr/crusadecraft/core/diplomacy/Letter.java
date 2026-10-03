package fr.crusadecraft.core.diplomacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Une lettre portée par pigeon jusqu'au Colombier royal. Elle devient un objet qu'on lit ; si elle demande
 * une réponse, ses choix restent valables côté serveur jusqu'à ce qu'on réponde ou qu'elle expire.
 */
public final class Letter {

    /** Ce qu'un choix déclenche. */
    public enum Action {
        /** Rien : on a lu. */
        NONE,
        /** Sceller le traité accepté (ou proposé par l'autre). */
        SEAL_TREATY,
        /** Sceller l'hommage d'un vassal. */
        SEAL_VASSALAGE,
        /** Prêter hommage à l'autre (proposé par un autre joueur). */
        SEAL_HOMAGE,
        /** Payer le tribut exigé. */
        PAY_TRIBUTE,
        /** Sceller la paix aux termes de la lettre. */
        SEAL_PEACE,
        /** Rejoindre la croisade à laquelle la lettre appelle (P10). */
        JOIN_CRUSADE,
        /** Sceller un mariage dynastique (P10). */
        SEAL_MARRIAGE,
        /** Refuser. */
        REFUSE;

        public String key() {
            return "crusadecraft.letter.action." + name().toLowerCase(Locale.ROOT);
        }
    }

    public record Choice(Action action, boolean needsSeal) {}

    private final UUID id;
    private final UUID from;
    private final boolean fromAi;
    private final UUID to;
    private final Component sender;
    private final Component subject;
    private final Component body;
    private final List<Choice> choices;
    private final long createdDay;
    private final long expiresDay;
    private final long arrivalTick;
    private boolean delivered;
    // Contenu d'une proposition
    private Treaty treaty;
    private int amount;
    private int tax;
    private int levy;
    private int claimProvince = -1;
    /** Guerre concernée et termes de paix, pour une lettre de paix. */
    private UUID war;
    private String peace = "";

    public Letter(UUID id, UUID from, boolean fromAi, UUID to, Component sender, Component subject, Component body,
                  List<Choice> choices, long createdDay, long expiresDay, long arrivalTick) {
        this.id = id;
        this.from = from;
        this.fromAi = fromAi;
        this.to = to;
        this.sender = sender;
        this.subject = subject;
        this.body = body;
        this.choices = List.copyOf(choices);
        this.createdDay = createdDay;
        this.expiresDay = expiresDay;
        this.arrivalTick = arrivalTick;
    }

    public UUID id() { return id; }
    public UUID from() { return from; }
    public boolean fromAi() { return fromAi; }
    public UUID to() { return to; }
    public Component sender() { return sender; }
    public Component subject() { return subject; }
    public Component body() { return body; }
    public List<Choice> choices() { return choices; }
    public long createdDay() { return createdDay; }
    public long expiresDay() { return expiresDay; }
    public long arrivalTick() { return arrivalTick; }
    public boolean delivered() { return delivered; }
    public Treaty treaty() { return treaty; }
    public int amount() { return amount; }
    public int tax() { return tax; }
    public int levy() { return levy; }
    public int claimProvince() { return claimProvince; }
    public UUID war() { return war; }
    public String peace() { return peace; }

    public Letter withPeace(UUID war, String peace) { this.war = war; this.peace = peace; return this; }

    public boolean needsAnswer() {
        return !choices.isEmpty();
    }

    public boolean arrived(long gameTime) {
        return gameTime >= arrivalTick;
    }

    public boolean expired(long today) {
        return today >= expiresDay;
    }

    public void markDelivered() { this.delivered = true; }

    public Letter withTreaty(Treaty t) { this.treaty = t; return this; }
    public Letter withAmount(int a) { this.amount = a; return this; }
    public Letter withContract(int tax, int levy) { this.tax = tax; this.levy = levy; return this; }
    public Letter withClaim(int province) { this.claimProvince = province; return this; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putUUID("from", from);
        t.putBoolean("fromAi", fromAi);
        t.putUUID("to", to);
        t.putString("sender", Component.Serializer.toJson(sender));
        t.putString("subject", Component.Serializer.toJson(subject));
        t.putString("body", Component.Serializer.toJson(body));
        ListTag ch = new ListTag();
        for (Choice c : choices) {
            CompoundTag x = new CompoundTag();
            x.putString("action", c.action().name());
            x.putBoolean("seal", c.needsSeal());
            ch.add(x);
        }
        t.put("choices", ch);
        t.putLong("created", createdDay);
        t.putLong("expires", expiresDay);
        t.putLong("arrival", arrivalTick);
        t.putBoolean("delivered", delivered);
        if (treaty != null) t.putString("treaty", treaty.name());
        t.putInt("amount", amount);
        t.putInt("tax", tax);
        t.putInt("levy", levy);
        t.putInt("claim", claimProvince);
        if (war != null) t.putUUID("war", war);
        t.putString("peace", peace);
        return t;
    }

    public static Letter load(CompoundTag t) {
        List<Choice> choices = new ArrayList<>();
        ListTag ch = t.getList("choices", Tag.TAG_COMPOUND);
        for (int i = 0; i < ch.size(); i++) {
            try {
                choices.add(new Choice(Action.valueOf(ch.getCompound(i).getString("action")), ch.getCompound(i).getBoolean("seal")));
            } catch (IllegalArgumentException ignored) {
                // choix inconnu : ignoré
            }
        }
        Letter l = new Letter(t.getUUID("id"), t.getUUID("from"), t.getBoolean("fromAi"), t.getUUID("to"),
                json(t.getString("sender")), json(t.getString("subject")), json(t.getString("body")), choices,
                t.getLong("created"), t.getLong("expires"), t.getLong("arrival"));
        l.delivered = t.getBoolean("delivered");
        if (t.contains("treaty")) l.treaty = Treaty.byName(t.getString("treaty"));
        l.amount = t.getInt("amount");
        l.tax = t.getInt("tax");
        l.levy = t.getInt("levy");
        l.claimProvince = t.contains("claim") ? t.getInt("claim") : -1;
        if (t.hasUUID("war")) l.war = t.getUUID("war");
        l.peace = t.getString("peace");
        return l;
    }

    private static Component json(String s) {
        try {
            Component c = Component.Serializer.fromJson(s);
            return c == null ? Component.empty() : c;
        } catch (RuntimeException e) {
            return Component.literal(s);
        }
    }
}
