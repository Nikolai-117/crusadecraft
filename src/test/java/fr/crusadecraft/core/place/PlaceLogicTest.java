package fr.crusadecraft.core.place;

import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.realm.TitleRank;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les lieux (P15) : les genres, l'arbre, les boucles, la sauvegarde. */
class PlaceLogicTest {
    private static final ResourceLocation OW = ResourceLocation.tryParse("minecraft:overworld");

    private static UUID id(int n) {
        return new UUID(7, n);
    }

    @Test
    void genresEtRangs() {
        assertEquals(LieuKind.DUCHE, LieuKind.of(TitleRank.DUCHE));
        assertEquals(LieuKind.BARONNIE, LieuKind.of(TitleRank.BARONNIE));
        for (TitleRank r : TitleRank.values()) assertEquals(r, LieuKind.of(r).rank(), "chaque rang a son genre de titre");
        assertTrue(LieuKind.EMPIRE.title() && !LieuKind.VILLE.title());
        // Un jalon n'est qu'une marque, un donjon et un village sont déjà des villes : pas de lieu à eux.
        assertNull(LieuKind.of(SiteKind.BORNE));
        assertNull(LieuKind.of(SiteKind.DONJON));
        assertNull(LieuKind.of(SiteKind.VILLAGE));
        assertEquals(LieuKind.FORT, LieuKind.of(SiteKind.FORT));
        assertEquals(LieuKind.AVANT_POSTE, LieuKind.of(SiteKind.TOUR));
        // On ne pose à la main que les titres et les lieux qui ne naissent de rien d'autre.
        assertTrue(LieuKind.LIEU_SAINT.manual() && LieuKind.REGION.placed() && LieuKind.DUCHE.manual());
        assertFalse(LieuKind.VILLE.manual() || LieuKind.FORT.manual() || LieuKind.DUCHE.placed());
        assertEquals(LieuKind.LIEU_SAINT, LieuKind.byName("inconnu"), "un genre illisible ne fait pas planter");
    }

    @Test
    void lArbreRangeChacunSousSonParent() {
        // Empire 1 › Royaume 2 › Ville 3 ; Lieu saint 4 seul ; 5 relève d'un lieu inconnu.
        Map<UUID, UUID> parent = new HashMap<>();
        parent.put(id(2), id(1));
        parent.put(id(3), id(2));
        parent.put(id(5), id(99));
        List<PlaceState.Row> rows = PlaceState.tree(List.of(id(3), id(5), id(1), id(4), id(2)), parent::get,
                (a, b) -> Long.compare(a.getLeastSignificantBits(), b.getLeastSignificantBits()));
        assertEquals(5, rows.size(), "aucun lieu ne disparaît");
        assertEquals(new PlaceState.Row(id(1), 0), rows.get(0));
        assertEquals(new PlaceState.Row(id(2), 1), rows.get(1));
        assertEquals(new PlaceState.Row(id(3), 2), rows.get(2));
        assertEquals(new PlaceState.Row(id(4), 0), rows.get(3));
        assertEquals(new PlaceState.Row(id(5), 0), rows.get(4), "un parent inconnu en fait une racine");
    }

    @Test
    void pasDeBoucle() {
        Map<UUID, UUID> parent = new HashMap<>();
        parent.put(id(2), id(1));
        parent.put(id(3), id(2));
        assertTrue(PlaceState.wouldCycle(id(1), id(3), parent::get), "l'empire sous sa propre ville : refusé");
        assertTrue(PlaceState.wouldCycle(id(1), id(1), parent::get), "un lieu sous lui-même : refusé");
        assertFalse(PlaceState.wouldCycle(id(3), id(1), parent::get), "la ville directement sous l'empire : permis");
        // Une boucle déjà là (données abîmées) : l'arbre la casse au lieu de tourner sans fin.
        parent.put(id(1), id(3));
        List<PlaceState.Row> rows = PlaceState.tree(List.of(id(1), id(2), id(3)), parent::get, (a, b) -> 0);
        assertEquals(3, rows.size());
    }

    @Test
    void unLieuSeRetrouveParCeQuoiIlRenvoie() {
        PlaceState s = new PlaceState();
        Lieu city = new Lieu(id(1), LieuKind.VILLE, Lieu.Ref.aiProvince(12), "Hautemarche");
        Lieu shrine = new Lieu(id(2), LieuKind.LIEU_SAINT, null, "Abbaye de Saint-Aubin");
        shrine.setParent(id(1));
        s.add(city);
        s.add(shrine);
        assertEquals(city, s.byRef(Lieu.Ref.aiProvince(12)).orElseThrow());
        assertTrue(s.byRef(Lieu.Ref.aiProvince(13)).isEmpty());
        // Disparu, mais avec une fiche : il reste, posé à la main, introuvable par son ancien renvoi.
        city.setText("La première ville du royaume.");
        s.detach(city);
        assertTrue(city.detached() && city.ref() == null && s.byRef(Lieu.Ref.aiProvince(12)).isEmpty());
        // Supprimé : ce qui en relevait retombe sur le parent du monde.
        s.remove(id(1));
        assertNull(shrine.parent());
    }

    @Test
    void sauvegardeAllerRetour() {
        PlaceState s = new PlaceState();
        Lieu region = new Lieu(id(1), LieuKind.REGION, null, "Forêt de Brocéliande");
        region.setPlace(OW, new BlockPos(120, 64, -340));
        region.setRadius(256);
        region.setText("Des chênes à perte de vue.");
        region.setHolder(id(9));
        region.setFoundedDay(42);
        Lieu duchy = new Lieu(id(2), LieuKind.DUCHE, null, "Normandie");
        duchy.setComposed(true);
        region.setParent(id(2));
        Lieu colony = new Lieu(id(3), LieuKind.VILLE, Lieu.Ref.colony(OW, 4), "Valbrume");
        s.add(region);
        s.add(duchy);
        s.add(colony);
        PlaceState back = PlaceState.load(s.save());
        assertEquals(3, back.size());
        Lieu r = back.get(id(1)).orElseThrow();
        assertEquals("Forêt de Brocéliande", r.name());
        assertEquals(new BlockPos(120, 64, -340), r.anchor());
        assertEquals(256, r.radius());
        assertEquals("Des chênes à perte de vue.", r.text());
        assertEquals(id(9), r.holder());
        assertEquals(id(2), r.parent());
        assertEquals(42, r.foundedDay());
        assertTrue(back.get(id(2)).orElseThrow().composed());
        assertEquals(colony.id(), back.byRef(Lieu.Ref.colony(OW, 4)).orElseThrow().id(), "les renvois se relisent");
        // Une entrée abîmée est ignorée, les autres se lisent.
        CompoundTag broken = s.save();
        broken.getList("lieux", 10).add(new CompoundTag());
        assertEquals(3, PlaceState.load(broken).size());
    }

    @Test
    void lesNomsEtLaFicheSontBornes() {
        Lieu l = new Lieu(id(1), LieuKind.RUINE, null, "x");
        l.setName("y".repeat(200));
        assertEquals(Lieu.MAX_NAME, l.name().length());
        l.setText("z".repeat(5000));
        assertEquals(Lieu.MAX_TEXT, l.text().length());
        l.setText("§cRouge");
        assertFalse(l.text().contains("§"), "pas de codes de mise en forme dans une fiche");
    }

    @Test
    void laVueVoyageSansRienPerdre() {
        LieuView.Selection sel = new LieuView.Selection(id(1), LieuKind.FORT.ordinal(), Component.literal("Fort de Valbrume"),
                Component.literal("construction"), Component.literal("Royaume de Valbrume"), id(2), Component.literal("Valbrume"), 12, true,
                -40, 300, 0, 7, "Tenu depuis l'an un.", "Valbrume", true, true, false, true);
        LieuView v = new LieuView(true, List.of(new LieuView.Entry(id(1), LieuKind.FORT.ordinal(), Component.literal("Fort de Valbrume"), 2,
                0x3355AA, true, 12, true)), sel, Component.literal("près de Valbrume"),
                List.of(new LieuView.Choice(id(2), Component.literal("Royaume de Valbrume"))), Component.literal("Fiche enregistrée."), true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        v.write(buf);
        LieuView back = LieuView.read(buf);
        assertEquals(1, back.entries().size());
        assertEquals(2, back.entries().get(0).depth());
        assertEquals("Tenu depuis l'an un.", back.selection().text());
        assertEquals(id(2), back.selection().parent());
        assertEquals(-40, back.selection().x());
        assertTrue(back.selection().textEditable() && !back.selection().deletable());
        assertEquals("Fiche enregistrée.", back.status().getString());
    }
}
