package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.core.world.WorldState;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/** Le monde autour du royaume : génération des royaumes IA, découverte, héraut, carte. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldGameTests {

    private WorldGameTests() {}

    @GameTest(template = TestWorld.TEMPLATE, batch = "p5_monde", timeoutTicks = 260)
    public static void generationDecouverteEtCarte(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        boolean heralds = CrusadeConfig.SERVER.heralds.get();
        double px = w.ruler.getX(), py = w.ruler.getY(), pz = w.ruler.getZ();
        List<long[]> forced = new ArrayList<>();
        List<Villager> spawned = new ArrayList<>();
        Runnable cleanup = () -> {
            spawned.forEach(Entity::discard);
            for (long[] c : forced) level.setChunkForced((int) c[0], (int) c[1], false);
            CrusadeConfig.SERVER.heralds.set(heralds);
            CrusadeSavedData.get(server).world().clear();
            w.ruler.setPos(px, py, pz);
            w.cleanup();
        };
        AiRealm target, other;
        try {
            CrusadeConfig.SERVER.heralds.set(true);
            int n = WorldService.generate(server, level, w.center.getX(), w.center.getZ());
            WorldState world = CrusadeSavedData.get(server).world();
            w.check(world.generated(), "monde non généré");
            // Les grands royaumes sont tous posés ; les seigneuries le sont au mieux (le planificateur renonce quand
            // il ne trouve plus de place), et leur nombre dépend donc de l'emplacement du test.
            int majors = CrusadeConfig.SERVER.aiRealmCount.get();
            int expected = majors + CrusadeConfig.SERVER.baronyCount.get();
            w.check(n >= majors + 1 && n <= expected, "royaumes IA : " + n + " (attendu de " + (majors + 1) + " à " + expected + ")");
            int avoid = Math.max(CrusadeConfig.SERVER.territoryRadius.get() * 2, 250);
            for (AiRealm r : world.realms()) {
                for (AiProvince p : r.provinces()) {
                    double d = Math.hypot(p.seat().getX() - w.center.getX(), p.seat().getZ() - w.center.getZ());
                    w.check(d >= avoid, "siège IA sur la colonie du joueur (" + Math.round(d) + " blocs)");
                }
            }
            Iterator<AiRealm> it = world.realms().iterator();
            target = it.next();
            other = it.next();
            // Comme un joueur qui arrive : les chunks autour de la capitale IA sont chargés pour de bon.
            AiProvince capital = target.capital();
            int cx = capital.seat().getX() >> 4, cz = (capital.seat().getZ() + 10) >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setChunkForced(cx + dx, cz + dz, true);
                    forced.add(new long[]{cx + dx, cz + dz});
                }
            }
        } catch (RuntimeException | AssertionError e) {
            cleanup.run();
            throw e;
        }

        AiRealm t = target, o = other;
        // On attend que le terrain autour de la capitale soit vraiment chargé (une génération peut prendre du
        // temps). Les vérifications sont inscrites d'avance, chacune par un objet distinct.
        net.minecraft.core.BlockPos spot = net.minecraft.core.BlockPos.containing(t.capital().seat().getX() + 0.5,
                t.capital().seat().getY() + 1, t.capital().seat().getZ() + 10.5);
        boolean[] done = {false};
        for (int i = 1; i <= 15; i++) {
            boolean last = i == 15;
            h.runAfterDelay(10L * i + 10, () -> {
                if (done[0] || (!level.isPositionEntityTicking(spot) && !last)) return;
                done[0] = true;
                verify(h, w, level, t, o, spawned, cleanup);
            });
        }
    }

    private static void verify(GameTestHelper h, TestWorld w, net.minecraft.server.level.ServerLevel level, AiRealm t, AiRealm o,
                               List<Villager> spawned, Runnable cleanup) {
        {
            try {
                AiProvince capital = t.capital();
                w.ruler.setPos(capital.seat().getX() + 0.5, capital.seat().getY() + 1, capital.seat().getZ() + 10.5);
                Optional<AiRealm> found = WorldService.discoverNear(w.ruler);
                w.check(found.isPresent() && found.get().id().equals(t.id()), "royaume non découvert : " + found.map(AiRealm::place));
                w.check(t.discoveredBy(w.ruler.getUUID()), "découverte non enregistrée");
                w.check(WorldService.discoverNear(w.ruler).isEmpty(), "découvert deux fois");

                // Le héraut est venu, il est marqué, et il présente son royaume.
                spawned.addAll(level.getEntitiesOfClass(Villager.class, new AABB(w.ruler.blockPosition()).inflate(10),
                        e -> e.getTags().contains(WorldService.HERALD_TAG)));
                w.check(spawned.size() == 1, "hérauts : " + spawned.size());
                w.check(WorldService.heraldRealm(spawned.get(0)).map(t.id()::equals).orElse(false), "héraut sans royaume");
                w.check(spawned.get(0).isInvulnerable(), "héraut vulnérable");

                // La carte montre le royaume découvert, pas l'autre ; la fiche suit la même règle.
                MapView map = WorldService.mapView(w.ruler);
                w.check(map.territories().stream().anyMatch(x -> x.ai() && x.id().equals(t.id())), "royaume découvert absent de la carte");
                w.check(map.territories().stream().noneMatch(x -> x.id().equals(o.id())), "royaume inconnu visible sur la carte");
                w.check(map.territories().stream().anyMatch(MapView.Territory::mine), "le royaume du joueur manque sur la carte");
                long seats = map.seats().stream().filter(s -> map.territories().get(s.territory()).id().equals(t.id())).count();
                w.check(seats == t.provinces().size(), "sièges sur la carte : " + seats);
                w.check(WorldService.realmView(w.ruler, t.id()).isPresent(), "fiche du royaume découvert refusée");
                w.check(WorldService.realmView(w.ruler, o.id()).isEmpty(), "fiche d'un royaume inconnu accordée");
                h.succeed();
            } finally {
                cleanup.run();
            }
        }
    }
}
