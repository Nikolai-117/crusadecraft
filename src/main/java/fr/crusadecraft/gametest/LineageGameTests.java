package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.mca.McaBridge;
import fr.crusadecraft.core.lineage.CommunityKind;
import fr.crusadecraft.core.lineage.House;
import fr.crusadecraft.core.lineage.LineageService;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.LineageView;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Role;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Le registre des lignées (P13) : les cours des royaumes IA, et les gens qu'on fait apparaître avec Comes Alive. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LineageGameTests {

    private LineageGameTests() {}

    private static void cleanup(TestWorld w, List<Entity> spawned) {
        var server = w.helper.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        for (Entity e : spawned) e.discard();
        for (Person p : new ArrayList<>(data.lineage().persons())) {
            Entity e = w.helper.getLevel().getEntity(p.id());
            if (e != null) e.discard();
        }
        data.lineage().clear();
        for (AiRealm r : data.world().realms()) data.diplomacy().forget(r.id());
        data.world().clear();
        WorldEditService.grantForTests(w.ruler.getUUID(), false);
        w.cleanup();
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p13_cour", timeoutTicks = 200)
    public static void chaqueRoyaumeASaCour(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        List<Entity> spawned = new ArrayList<>();
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            AiRealm valcourt = WorldEditService.addRealm(server, h.getLevel(), "Valcourt", "Godefroy", TitleRank.COMTE, 0xB3261E,
                    AiFaith.FIDELE, AiPersonality.PACIFIQUE, w.center.getX() + 800, w.center.getZ());
            // Un royaume que le joueur ne connaît pas encore n'a pas sa cour dans sa vue…
            LineageView before = LineageService.view(w.ruler, null);
            w.check(before.houses().isEmpty(), "une cour d'un royaume inconnu est servie");
            // … il la reçoit dès qu'il le découvre.
            valcourt.discover(w.ruler.getUUID(), RealmService.currentDay(server));
            LineageView view = LineageService.view(w.ruler, null);
            House court = data.lineage().houseOfRealm(valcourt.id()).orElse(null);
            w.check(court != null && view.houses().size() == 1, "le royaume découvert n'a pas de cour");
            w.check(court.name().equals(valcourt.house()), "la cour ne porte pas le nom de la maison du souverain");
            Person ruler = data.lineage().person(court.head()).orElseThrow();
            w.check(ruler.name().equals("Godefroy") && ruler.role() == Role.SOUVERAIN, "le souverain du registre n'est pas celui du royaume");
            w.check(data.lineage().persons().stream().filter(p -> p.role() == Role.CONSEILLER).count() == valcourt.council().size(),
                    "les conseillers manquent");
            int size = data.lineage().persons().size();
            LineageService.view(w.ruler, null);
            w.check(data.lineage().persons().size() == size, "la cour est créée deux fois");
            // Un lecteur ne modifie rien.
            w.check(!LineageService.createHouse(w.ruler, "Aubépine", 2, null, 3).success(), "un lecteur crée une maison");
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            w.check(!LineageService.createHouse(w.ruler, "Aubépine", 2, valcourt.id(), 3).success(), "deux maisons règnent sur le même royaume");
            w.check(LineageService.createHouse(w.ruler, "Aubépine", 2, null, 3).success(), "maison refusée");
            w.check(LineageService.createCommunity(w.ruler, "Saint-Aubin", CommunityKind.VILLAGE, valcourt.id(), 4).success(), "communauté refusée");
            // Supprimer le royaume : sa maison reste, mais ne règne plus.
            WorldEditService.removeRealm(server, valcourt.id());
            w.check(court.realm() == null, "la maison règne encore sur un royaume disparu");
            h.succeed();
        } finally {
            cleanup(w, spawned);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p13_comes_alive", timeoutTicks = 200)
    public static void faireVenirUneFamilleAvecComesAlive(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        List<Entity> spawned = new ArrayList<>();
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            var level = h.getLevel();
            LineageState state = data.lineage();
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            w.check(LineageService.createHouse(w.ruler, "Aubépine", 1, null, 2).success(), "maison refusée");
            House house = state.houses().iterator().next();
            if (!McaBridge.isReady()) {
                // Sans Comes Alive (-Pwith_mca=false) : on le dit au lieu de planter.
                w.check(!LineageService.spawn(w.ruler, house.id()).success(), "faire venir sans Comes Alive réussit");
                h.succeed();
                return;
            }
            var out = LineageService.spawn(w.ruler, house.id());
            w.check(out.success(), "personne n'est venu : " + out.message().getString());
            Person child = state.members(house.id()).stream().filter(p -> p.father() != null && p.alive()).findFirst().orElse(null);
            w.check(child != null, "la maison générée n'a pas d'enfant");
            Entity villager = level.getEntity(child.id());
            w.check(villager != null, "l'enfant n'est pas apparu");
            spawned.add(villager);
            w.check(villager.getTags().contains(McaBridge.TAG) && child.inWorld(), "l'enfant n'est pas marqué comme incarné");
            w.check(McaBridge.read(villager).map(McaBridge.Villager::female).orElse(!child.female()) == child.female(), "le sexe du villageois diffère");
            Optional<McaBridge.TreeEntry> entry = McaBridge.treeEntry(level, child.id());
            w.check(entry.isPresent() && child.father().equals(entry.get().father()) && child.mother().equals(entry.get().mother()),
                    "l'arbre de Comes Alive ne connaît pas ses parents");
            w.check(entry.get().name().equals(state.fullName(child)), "l'arbre de Comes Alive ne porte pas son nom");
            Person father = state.person(child.father()).orElseThrow();
            Optional<McaBridge.TreeEntry> fatherEntry = McaBridge.treeEntry(level, father.id());
            w.check(fatherEntry.isPresent() && father.spouse().equals(fatherEntry.get().partner()), "le couple n'est pas uni dans l'arbre");
            // Le faire venir une deuxième fois ne le dédouble pas : il vient à vous.
            var again = LineageService.spawn(w.ruler, child.id());
            w.check(again.success() && level.getEntity(child.id()) == villager, "l'enfant a été dédoublé");
            // Sa mort dans le monde est inscrite au registre.
            villager.kill();
            w.check(!child.alive() && !child.inWorld(), "la mort du villageois n'est pas inscrite");
            // Inscrire un villageois de Comes Alive qui n'était pas au registre.
            Person stranger = new Person(UUID.randomUUID(), "Ysolde", true, 30);
            Optional<Entity> made = McaBridge.spawn(level, stranger, "Ysolde Meunier", w.ruler.blockPosition().east(2));
            w.check(made.isPresent(), "villageois de test non créé");
            spawned.add(made.get());
            w.check(LineageService.createCommunity(w.ruler, "Saint-Aubin", CommunityKind.VILLAGE, null, 1).success(), "communauté refusée");
            var village = state.communities().iterator().next();
            var adopted = LineageService.adoptNearby(w.ruler, village.id());
            w.check(adopted.success(), "inscription refusée : " + adopted.message().getString());
            Person ysolde = state.person(stranger.id()).orElse(null);
            w.check(ysolde != null && ysolde.name().equals("Ysolde") && ysolde.surname().equals("Meunier") && village.id().equals(ysolde.community()),
                    "le villageois inscrit est mal recopié");
            h.succeed();
        } finally {
            cleanup(w, spawned);
        }
    }
}
