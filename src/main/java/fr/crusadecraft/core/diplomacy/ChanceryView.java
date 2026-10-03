package fr.crusadecraft.core.diplomacy;

import fr.crusadecraft.core.realm.TitleRank;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** La Chancery Table : le titre et ses conditions, les contrats des vassaux, les revendications. */
public record ChanceryView(String realmName, TitleRank rank, int provinces, TitleRank nextRank, int needProvinces, int needGold,
                           int needPrestige, long gold, int prestige, boolean sealOk, String sealProblem, boolean hasChancellor,
                           List<Vassal> vassals, List<Claim> claims, List<Claim> candidates, int claimTarget, int claimProgress) {

    /** Un vassal et son contrat. {@code label} : nom complet (joueur) ou nom de lieu (IA). */
    public record Vassal(UUID id, boolean ai, String label, TitleRank rank, int color, int provinces, int tax, int levy,
                         int opinion, long dailyShare) {}

    /** Une province revendiquée, ou que l'on peut faire revendiquer. */
    public record Claim(int province, String name, String realmPlace, TitleRank realmRank, int distance) {}

    public boolean canRankUp() {
        return nextRank != null && provinces >= needProvinces && gold >= needGold && prestige >= needPrestige && sealOk;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(realmName);
        buf.writeEnum(rank);
        buf.writeVarInt(provinces);
        buf.writeBoolean(nextRank != null);
        if (nextRank != null) buf.writeEnum(nextRank);
        buf.writeVarInt(needProvinces);
        buf.writeVarInt(needGold);
        buf.writeVarInt(needPrestige);
        buf.writeLong(gold);
        buf.writeVarInt(prestige);
        buf.writeBoolean(sealOk);
        buf.writeUtf(sealProblem);
        buf.writeBoolean(hasChancellor);
        buf.writeVarInt(vassals.size());
        for (Vassal v : vassals) {
            buf.writeUUID(v.id());
            buf.writeBoolean(v.ai());
            buf.writeUtf(v.label());
            buf.writeEnum(v.rank());
            buf.writeInt(v.color());
            buf.writeVarInt(v.provinces());
            buf.writeVarInt(v.tax());
            buf.writeVarInt(v.levy());
            buf.writeInt(v.opinion());
            buf.writeLong(v.dailyShare());
        }
        writeClaims(buf, claims);
        writeClaims(buf, candidates);
        buf.writeInt(claimTarget);
        buf.writeVarInt(claimProgress);
    }

    private static void writeClaims(FriendlyByteBuf buf, List<Claim> list) {
        buf.writeVarInt(list.size());
        for (Claim c : list) {
            buf.writeVarInt(c.province());
            buf.writeUtf(c.name());
            buf.writeUtf(c.realmPlace());
            buf.writeEnum(c.realmRank());
            buf.writeVarInt(c.distance());
        }
    }

    private static List<Claim> readClaims(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Claim> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Claim(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readEnum(TitleRank.class), buf.readVarInt()));
        }
        return out;
    }

    public static ChanceryView read(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        TitleRank rank = buf.readEnum(TitleRank.class);
        int provinces = buf.readVarInt();
        TitleRank next = buf.readBoolean() ? buf.readEnum(TitleRank.class) : null;
        int np = buf.readVarInt(), ng = buf.readVarInt(), npr = buf.readVarInt();
        long gold = buf.readLong();
        int prestige = buf.readVarInt();
        boolean seal = buf.readBoolean();
        String problem = buf.readUtf();
        boolean chancellor = buf.readBoolean();
        int n = buf.readVarInt();
        List<Vassal> vassals = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            vassals.add(new Vassal(buf.readUUID(), buf.readBoolean(), buf.readUtf(), buf.readEnum(TitleRank.class), buf.readInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readLong()));
        }
        List<Claim> claims = readClaims(buf);
        List<Claim> candidates = readClaims(buf);
        return new ChanceryView(name, rank, provinces, next, np, ng, npr, gold, prestige, seal, problem, chancellor, vassals, claims,
                candidates, buf.readInt(), buf.readVarInt());
    }
}
