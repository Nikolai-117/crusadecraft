package fr.crusadecraft.core.realm;

import fr.crusadecraft.core.ColonyRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealmSerializationTest {

    private static final ResourceLocation OVERWORLD = ResourceLocation.tryParse("minecraft:overworld");
    private static final ResourceLocation NETHER = ResourceLocation.tryParse("minecraft:the_nether");

    private static Realm sample() {
        Realm realm = new Realm(UUID.randomUUID(), "Valbrume", "Par le fer", "Aubépine", 0xB3261E,
                UUID.randomUUID(), "Romain", true, new ColonyRef(OVERWORLD, 3), TitleRank.BARONNIE, 42);
        realm.addProvince(new ColonyRef(NETHER, 3));
        realm.setPrestige(50);
        realm.setPiety(7);
        realm.setDread(2);
        realm.setState(RealmState.DORMANT_CAPITAL_LOST);
        return realm;
    }

    @Test
    void allerRetourNbt() {
        Realm a = sample();
        CompoundTag tag = a.save();
        Realm b = Realm.load(tag);
        assertEquals(a.id(), b.id());
        assertEquals(a.name(), b.name());
        assertEquals(a.motto(), b.motto());
        assertEquals(a.house(), b.house());
        assertEquals(a.color(), b.color());
        assertEquals(a.ruler(), b.ruler());
        assertEquals(a.feminineTitle(), b.feminineTitle());
        assertEquals(a.capital(), b.capital());
        assertEquals(a.provinces(), b.provinces());
        assertEquals(a.rank(), b.rank());
        assertEquals(a.state(), b.state());
        assertEquals(a.foundedDay(), b.foundedDay());
        assertEquals(50, b.prestige());
        assertEquals(7, b.piety());
        assertEquals(2, b.dread());
    }

    @Test
    void memeIdentifiantDansDeuxDimensionsNeSeConfondPas() {
        Realm realm = sample();
        assertTrue(realm.holds(new ColonyRef(OVERWORLD, 3)));
        assertTrue(realm.holds(new ColonyRef(NETHER, 3)));
        assertFalse(realm.holds(new ColonyRef(OVERWORLD, 4)));
        assertEquals(List.of(new ColonyRef(OVERWORLD, 3), new ColonyRef(NETHER, 3)), realm.allColonies());
    }

    @Test
    void laCapitaleNEstPasAjouteeCommeProvince() {
        Realm realm = sample();
        realm.addProvince(realm.capital());
        assertEquals(1, realm.provinces().size());
    }

    @Test
    void valeursInconnuesRetombentSurDesDefauts() {
        CompoundTag tag = sample().save();
        tag.putString("rank", "PAPAUTE");
        tag.putString("state", "???");
        Realm b = Realm.load(tag);
        assertEquals(TitleRank.BARONNIE, b.rank());
        assertEquals(RealmState.ACTIVE, b.state());
    }
}
