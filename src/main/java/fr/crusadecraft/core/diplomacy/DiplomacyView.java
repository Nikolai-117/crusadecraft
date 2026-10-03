package fr.crusadecraft.core.diplomacy;

import fr.crusadecraft.core.character.Opinion;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiPersonality;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le Colombier royal : les royaumes connus, leur statut, ce que leurs souverains pensent de vous, les traités en
 * cours, et pour chaque proposition la réponse prévue (le serveur fait le calcul, l'écran n'invente rien).
 */
public record DiplomacyView(long gold, int prestige, int mailArrived, int mailAwaiting, boolean sealUsable, String sealProblem,
                            UUID chancellorTarget, boolean hasChancellor, List<Entry> entries) {

    /**
     * Un royaume connu.
     *
     * @param label         nom complet (royaume de joueur) ou nom de lieu (royaume IA, à composer avec le rang)
     * @param treatyDays    traités en cours et jours restants ({@code -1} : permanent)
     * @param verdicts      réponse prévue pour chaque proposition (royaumes IA seulement)
     * @param mcRank        rang MineColonies de son souverain dans votre capitale (royaumes de joueurs), sinon vide
     */
    public record Entry(UUID id, boolean ai, String label, TitleRank rank, int color, String ruler, boolean rulerFemale,
                        AiPersonality personality, DiploStatus status, int opinion, List<Opinion.Line> opinionLines,
                        Map<Treaty, Long> treatyDays, int vassalTax, int vassalLevy, boolean tributeToMe, boolean tributeFromMe,
                        int tributePerDay, double strengthRatio, String mcRank,
                        Map<DiplomacyRules.Proposal, DiplomacyRules.Verdict> verdicts) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeLong(gold);
        buf.writeVarInt(prestige);
        buf.writeVarInt(mailArrived);
        buf.writeVarInt(mailAwaiting);
        buf.writeBoolean(sealUsable);
        buf.writeUtf(sealProblem);
        buf.writeBoolean(chancellorTarget != null);
        if (chancellorTarget != null) buf.writeUUID(chancellorTarget);
        buf.writeBoolean(hasChancellor);
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeUUID(e.id());
            buf.writeBoolean(e.ai());
            buf.writeUtf(e.label());
            buf.writeEnum(e.rank());
            buf.writeInt(e.color());
            buf.writeUtf(e.ruler());
            buf.writeBoolean(e.rulerFemale());
            buf.writeBoolean(e.personality() != null);
            if (e.personality() != null) buf.writeEnum(e.personality());
            buf.writeEnum(e.status());
            buf.writeInt(e.opinion());
            buf.writeVarInt(e.opinionLines().size());
            for (Opinion.Line l : e.opinionLines()) {
                buf.writeUtf(l.key());
                buf.writeUtf(l.arg() == null ? "" : l.arg());
                buf.writeInt(l.value());
            }
            buf.writeVarInt(e.treatyDays().size());
            for (Map.Entry<Treaty, Long> t : e.treatyDays().entrySet()) {
                buf.writeEnum(t.getKey());
                buf.writeLong(t.getValue());
            }
            buf.writeVarInt(e.vassalTax());
            buf.writeVarInt(e.vassalLevy());
            buf.writeBoolean(e.tributeToMe());
            buf.writeBoolean(e.tributeFromMe());
            buf.writeVarInt(e.tributePerDay());
            buf.writeDouble(e.strengthRatio());
            buf.writeUtf(e.mcRank());
            buf.writeVarInt(e.verdicts().size());
            for (Map.Entry<DiplomacyRules.Proposal, DiplomacyRules.Verdict> v : e.verdicts().entrySet()) {
                buf.writeEnum(v.getKey());
                DiplomacyRules.Verdict d = v.getValue();
                buf.writeBoolean(d.accepted());
                buf.writeInt(d.total());
                buf.writeUtf(d.blockedKey() == null ? "" : d.blockedKey());
                buf.writeVarInt(d.factors().size());
                for (DiplomacyRules.Factor f : d.factors()) {
                    buf.writeUtf(f.key());
                    buf.writeInt(f.value());
                }
            }
        }
    }

    public static DiplomacyView read(FriendlyByteBuf buf) {
        long gold = buf.readLong();
        int prestige = buf.readVarInt();
        int arrived = buf.readVarInt();
        int awaiting = buf.readVarInt();
        boolean seal = buf.readBoolean();
        String sealProblem = buf.readUtf();
        UUID target = buf.readBoolean() ? buf.readUUID() : null;
        boolean hasChancellor = buf.readBoolean();
        int n = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID id = buf.readUUID();
            boolean ai = buf.readBoolean();
            String label = buf.readUtf();
            TitleRank rank = buf.readEnum(TitleRank.class);
            int color = buf.readInt();
            String ruler = buf.readUtf();
            boolean female = buf.readBoolean();
            AiPersonality personality = buf.readBoolean() ? buf.readEnum(AiPersonality.class) : null;
            DiploStatus status = buf.readEnum(DiploStatus.class);
            int opinion = buf.readInt();
            int m = buf.readVarInt();
            List<Opinion.Line> lines = new ArrayList<>(m);
            for (int j = 0; j < m; j++) {
                String key = buf.readUtf();
                String arg = buf.readUtf();
                lines.add(new Opinion.Line(key, arg.isEmpty() ? null : arg, buf.readInt()));
            }
            m = buf.readVarInt();
            Map<Treaty, Long> treaties = new EnumMap<>(Treaty.class);
            for (int j = 0; j < m; j++) treaties.put(buf.readEnum(Treaty.class), buf.readLong());
            int tax = buf.readVarInt(), levy = buf.readVarInt();
            boolean toMe = buf.readBoolean(), fromMe = buf.readBoolean();
            int perDay = buf.readVarInt();
            double ratio = buf.readDouble();
            String mcRank = buf.readUtf();
            m = buf.readVarInt();
            Map<DiplomacyRules.Proposal, DiplomacyRules.Verdict> verdicts = new EnumMap<>(DiplomacyRules.Proposal.class);
            for (int j = 0; j < m; j++) {
                DiplomacyRules.Proposal p = buf.readEnum(DiplomacyRules.Proposal.class);
                boolean accepted = buf.readBoolean();
                int total = buf.readInt();
                String blocked = buf.readUtf();
                int k = buf.readVarInt();
                List<DiplomacyRules.Factor> factors = new ArrayList<>(k);
                for (int x = 0; x < k; x++) factors.add(new DiplomacyRules.Factor(buf.readUtf(), buf.readInt()));
                verdicts.put(p, new DiplomacyRules.Verdict(accepted, total, factors, blocked.isEmpty() ? null : blocked));
            }
            entries.add(new Entry(id, ai, label, rank, color, ruler, female, personality, status, opinion, lines, treaties,
                    tax, levy, toMe, fromMe, perDay, ratio, mcRank, verdicts));
        }
        return new DiplomacyView(gold, prestige, arrived, awaiting, seal, sealProblem, target, hasChancellor, entries);
    }
}
