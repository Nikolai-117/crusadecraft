package fr.crusadecraft.core.council;

import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.ColonyReport;
import fr.crusadecraft.core.character.CitizenRef;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * L'écran du Bureau du Maire : le maire, le rapport de la ville, des candidats si le poste est à
 * pourvoir, et les requêtes du maire en attente.
 */
public record MayorView(ColonyRef colony, String colonyName, boolean canAct, Person mayor, ColonyReport report,
                        List<Person> candidates, int officeHour, boolean deskPlaced,
                        List<CouncilView.RequestSummary> requests) {

    /** Un citoyen tel qu'il apparaît ici : nom, intendance, opinion. */
    public record Person(CitizenRef ref, String name, boolean female, int stewardship, int opinion) {
        void write(FriendlyByteBuf buf) {
            ref.write(buf);
            buf.writeUtf(name);
            buf.writeBoolean(female);
            buf.writeVarInt(stewardship);
            buf.writeVarInt(opinion);
        }

        static Person read(FriendlyByteBuf buf) {
            return new Person(CitizenRef.read(buf), buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
        }
    }

    public void write(FriendlyByteBuf buf) {
        colony.write(buf);
        buf.writeUtf(colonyName);
        buf.writeBoolean(canAct);
        buf.writeBoolean(mayor != null);
        if (mayor != null) mayor.write(buf);
        report.write(buf);
        buf.writeVarInt(candidates.size());
        for (Person p : candidates) p.write(buf);
        buf.writeVarInt(officeHour);
        buf.writeBoolean(deskPlaced);
        buf.writeVarInt(requests.size());
        for (CouncilView.RequestSummary r : requests) r.write(buf);
    }

    public static MayorView read(FriendlyByteBuf buf) {
        ColonyRef colony = ColonyRef.read(buf);
        String name = buf.readUtf();
        boolean canAct = buf.readBoolean();
        Person mayor = buf.readBoolean() ? Person.read(buf) : null;
        ColonyReport report = ColonyReport.read(buf);
        int n = buf.readVarInt();
        List<Person> candidates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) candidates.add(Person.read(buf));
        int hour = buf.readVarInt();
        boolean desk = buf.readBoolean();
        int nr = buf.readVarInt();
        List<CouncilView.RequestSummary> requests = new ArrayList<>(nr);
        for (int i = 0; i < nr; i++) requests.add(CouncilView.RequestSummary.read(buf));
        return new MayorView(colony, name, canAct, mayor, report, candidates, hour, desk, requests);
    }
}
