package fr.crusadecraft.core.character;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * La fiche d'un personnage telle que le client l'affiche, calculée par le serveur.
 *
 * @param canAct      le lecteur est le souverain de ce personnage : il peut agir sur lui
 * @param hasOpinion  le personnage relève du royaume du lecteur : son opinion a un sens
 * @param rulerFeminine le souverain porte un titre féminin (« Ma dame »)
 */
public record CharacterView(CitizenRef ref, String name, boolean female, boolean child, String jobKey,
                            String colonyName, String realmName, Post post, CharacterStatus status, long statusUntil,
                            boolean deceased, int[] skills, int[] baseStats, int[] stats, Education education,
                            List<Trait> traits, boolean hasOpinion, int opinion, List<Opinion.Line> opinionLines,
                            int stress, double happiness, List<HistoryEntry> history, int entityId, boolean canAct,
                            boolean rulerFeminine, long currentDay, List<PostSlot> slots, List<StatSource> sources) {

    /** D'où vient une aptitude : les compétences pondérées et le diviseur de la règle en vigueur. */
    public record StatSource(int[] skills, float[] weights, float divisor) {}

    /** Un poste proposable et son titulaire actuel (pour l'écran de nomination). */
    public record PostSlot(Post post, String holderName, boolean holderIsThis) {}

    public void write(FriendlyByteBuf buf) {
        ref.write(buf);
        buf.writeUtf(name);
        buf.writeBoolean(female);
        buf.writeBoolean(child);
        buf.writeUtf(jobKey);
        buf.writeUtf(colonyName);
        buf.writeUtf(realmName);
        buf.writeBoolean(post != null);
        if (post != null) buf.writeEnum(post);
        buf.writeEnum(status);
        buf.writeVarLong(statusUntil);
        buf.writeBoolean(deceased);
        buf.writeVarIntArray(skills);
        buf.writeVarIntArray(baseStats);
        buf.writeVarIntArray(stats);
        buf.writeEnum(education.stat());
        buf.writeVarInt(education.tier());
        buf.writeVarInt(traits.size());
        for (Trait t : traits) buf.writeEnum(t);
        buf.writeBoolean(hasOpinion);
        buf.writeVarInt(opinion);
        buf.writeVarInt(opinionLines.size());
        for (Opinion.Line l : opinionLines) {
            buf.writeUtf(l.key());
            buf.writeUtf(l.arg() == null ? "" : l.arg());
            buf.writeVarInt(l.value());
        }
        buf.writeVarInt(stress);
        buf.writeDouble(happiness);
        buf.writeVarInt(history.size());
        for (HistoryEntry h : history) h.write(buf);
        buf.writeVarInt(entityId);
        buf.writeBoolean(canAct);
        buf.writeBoolean(rulerFeminine);
        buf.writeVarLong(currentDay);
        buf.writeVarInt(slots.size());
        for (PostSlot s : slots) {
            buf.writeEnum(s.post());
            buf.writeUtf(s.holderName());
            buf.writeBoolean(s.holderIsThis());
        }
        buf.writeVarInt(sources.size());
        for (StatSource src : sources) {
            buf.writeVarIntArray(src.skills());
            buf.writeVarInt(src.weights().length);
            for (float w : src.weights()) buf.writeFloat(w);
            buf.writeFloat(src.divisor());
        }
    }

    public static CharacterView read(FriendlyByteBuf buf) {
        CitizenRef ref = CitizenRef.read(buf);
        String name = buf.readUtf();
        boolean female = buf.readBoolean();
        boolean child = buf.readBoolean();
        String job = buf.readUtf();
        String colony = buf.readUtf();
        String realm = buf.readUtf();
        Post post = buf.readBoolean() ? buf.readEnum(Post.class) : null;
        CharacterStatus status = buf.readEnum(CharacterStatus.class);
        long until = buf.readVarLong();
        boolean deceased = buf.readBoolean();
        int[] skills = buf.readVarIntArray();
        int[] base = buf.readVarIntArray();
        int[] stats = buf.readVarIntArray();
        Education edu = new Education(buf.readEnum(Stat.class), buf.readVarInt());
        int nt = buf.readVarInt();
        List<Trait> traits = new ArrayList<>(nt);
        for (int i = 0; i < nt; i++) traits.add(buf.readEnum(Trait.class));
        boolean hasOpinion = buf.readBoolean();
        int opinion = buf.readVarInt();
        int nl = buf.readVarInt();
        List<Opinion.Line> lines = new ArrayList<>(nl);
        for (int i = 0; i < nl; i++) {
            String key = buf.readUtf();
            String arg = buf.readUtf();
            lines.add(new Opinion.Line(key, arg.isEmpty() ? null : arg, buf.readVarInt()));
        }
        int stress = buf.readVarInt();
        double happiness = buf.readDouble();
        int nh = buf.readVarInt();
        List<HistoryEntry> history = new ArrayList<>(nh);
        for (int i = 0; i < nh; i++) history.add(HistoryEntry.read(buf));
        int entityId = buf.readVarInt();
        boolean canAct = buf.readBoolean();
        boolean rulerFeminine = buf.readBoolean();
        long day = buf.readVarLong();
        int ns = buf.readVarInt();
        List<PostSlot> slots = new ArrayList<>(ns);
        for (int i = 0; i < ns; i++) slots.add(new PostSlot(buf.readEnum(Post.class), buf.readUtf(), buf.readBoolean()));
        int nsrc = buf.readVarInt();
        List<StatSource> sources = new ArrayList<>(nsrc);
        for (int i = 0; i < nsrc; i++) {
            int[] sk = buf.readVarIntArray();
            int nw = buf.readVarInt();
            float[] w = new float[nw];
            for (int j = 0; j < nw; j++) w[j] = buf.readFloat();
            sources.add(new StatSource(sk, w, buf.readFloat()));
        }
        return new CharacterView(ref, name, female, child, job, colony, realm, post, status, until, deceased, skills,
                base, stats, edu, traits, hasOpinion, opinion, lines, stress, happiness, history, entityId, canAct,
                rulerFeminine, day, slots, sources);
    }
}
