package fr.crusadecraft.core.realm;

import fr.crusadecraft.core.ColonyRef;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Les villes du royaume telles que l'écran « Villes » les montre (P19) : chaque colonie rattachée, tenue en direct ou
 * confiée à un gouverneur, les personnes du registre qui peuvent gouverner, et la colonie où se tient le souverain
 * s'il peut l'y rattacher.
 *
 * @param domainLimit villes tenues en direct sans pénalité, pour ce rang
 * @param penalty     impôts perdus, en pour cent, par ville tenue en direct au-delà
 * @param here        la colonie du souverain où il se tient et qu'il peut rattacher (vide : aucune)
 */
public record CityView(String realmName, int domainLimit, int directCount, int penalty, List<Row> rows, List<Candidate> candidates,
                       String here, int defaultTax, int defaultLevy) {

    /** Une ville. {@code governor} : {@code null} si elle est tenue en direct. */
    public record Row(ColonyRef colony, String name, boolean capital, int citizens, UUID governor, String governorName,
                      int taxShare, int levyShare, boolean overLimit) {}

    /** Une personne du registre qui peut gouverner une ville. */
    public record Candidate(UUID id, String name) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(realmName);
        buf.writeVarInt(domainLimit);
        buf.writeVarInt(directCount);
        buf.writeVarInt(penalty);
        buf.writeVarInt(rows.size());
        for (Row r : rows) {
            buf.writeResourceLocation(r.colony().dimension());
            buf.writeVarInt(r.colony().colonyId());
            buf.writeUtf(r.name());
            buf.writeBoolean(r.capital());
            buf.writeVarInt(r.citizens());
            buf.writeBoolean(r.governor() != null);
            if (r.governor() != null) buf.writeUUID(r.governor());
            buf.writeUtf(r.governorName());
            buf.writeVarInt(r.taxShare());
            buf.writeVarInt(r.levyShare());
            buf.writeBoolean(r.overLimit());
        }
        buf.writeVarInt(candidates.size());
        for (Candidate c : candidates) {
            buf.writeUUID(c.id());
            buf.writeUtf(c.name());
        }
        buf.writeUtf(here);
        buf.writeVarInt(defaultTax);
        buf.writeVarInt(defaultLevy);
    }

    public static CityView read(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        int limit = buf.readVarInt(), direct = buf.readVarInt(), penalty = buf.readVarInt();
        int n = buf.readVarInt();
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ColonyRef colony = new ColonyRef(buf.readResourceLocation(), buf.readVarInt());
            String city = buf.readUtf();
            boolean capital = buf.readBoolean();
            int citizens = buf.readVarInt();
            UUID governor = buf.readBoolean() ? buf.readUUID() : null;
            rows.add(new Row(colony, city, capital, citizens, governor, buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        }
        n = buf.readVarInt();
        List<Candidate> candidates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) candidates.add(new Candidate(buf.readUUID(), buf.readUtf()));
        return new CityView(name, limit, direct, penalty, rows, candidates, buf.readUtf(), buf.readVarInt(), buf.readVarInt());
    }
}
