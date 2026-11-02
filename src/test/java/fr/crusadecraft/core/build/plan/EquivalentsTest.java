package fr.crusadecraft.core.build.plan;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P38 : la table des équivalences (lue telle qu'elle est livrée) et le sol d'une schématique qui apporte son terrain. */
class EquivalentsTest {

    private static Map<BlockEquivalents.Key, BlockEquivalents.Rule> builtIn() throws Exception {
        try (var in = EquivalentsTest.class.getResourceAsStream(BlockEquivalents.BUILT_IN)) {
            return BlockEquivalents.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void clesEtMotifs() {
        BlockEquivalents.Key k = BlockEquivalents.key("me:x[double=true,facing=north]");
        assertEquals("me:x", k.id());
        assertEquals(Map.of("double", "true", "facing", "north"), k.when());
        assertEquals("fence", BlockEquivalents.match("minecraft:pale_oak_*", "minecraft:pale_oak_fence"));
        assertEquals("me:old_bricks", BlockEquivalents.match("*_stairs", "me:old_bricks_stairs"));
        assertNull(BlockEquivalents.match("*_stairs", "me:old_bricks"));
        assertNull(BlockEquivalents.match("minecraft:pale_oak_*", "minecraft:oak_fence"));
    }

    @Test
    void tableLivree() throws Exception {
        var table = builtIn();
        assertTrue(table.size() > 150, "table trop courte : " + table.size());
        for (var rule : table.values()) assertFalse(rule.to().isEmpty());

        // La forme d'abord : une demi-dalle verticale pleine devient un bloc, une demi n'est jamais un bloc plein.
        var full = BlockEquivalents.find(table, "me:black_lebethron_vertical_slab", Map.of("double", "true", "facing", "north"));
        assertEquals("dawnoftimebuilder:charred_spruce_planks", full.orElseThrow().getKey().to().get(0));
        var half = BlockEquivalents.find(table, "me:black_lebethron_vertical_slab", Map.of("double", "false", "facing", "north"));
        assertEquals("dawnoftimebuilder:charred_spruce_planks_plate", half.orElseThrow().getKey().to().get(0));
        assertTrue(half.get().getKey().flip(), "la plaque doit regarder du côté opposé");

        // Un colombage garde son motif, avec son bois et son torchis.
        var cross = BlockEquivalents.find(table, "me:black_wattle_and_white_daub_cross", Map.of()).orElseThrow().getKey();
        assertEquals("domum_ornamentum:double_crossed", cross.to().get(0));
        assertEquals("minecraft:dark_oak_planks", cross.frame());
        assertEquals("minecraft:calcite", cross.center());
        assertTrue(cross.orient());

        // Le haut d'une forge n'est pas son bas.
        assertEquals("minecraft:blast_furnace", BlockEquivalents.find(table, "me:forge", Map.of("part", "bottom")).orElseThrow().getKey().to().get(0));
        assertTrue(BlockEquivalents.find(table, "me:forge", Map.of("part", "top")).orElseThrow().getKey().to().get(0).contains("chimney"));

        // Les versions plus récentes : un motif reporte sa part étoilée.
        var pale = BlockEquivalents.find(table, "minecraft:stripped_pale_oak_wood", Map.of()).orElseThrow();
        assertEquals("minecraft:stripped_birch_*", pale.getKey().to().get(0));
        assertEquals("wood", pale.getValue());
        assertEquals("minecraft:grass", BlockEquivalents.find(table, "minecraft:short_grass", Map.of()).orElseThrow().getKey().to().get(0));

        // En dernier recours, la forme seule ; le motif le plus long l'emporte.
        var generic = BlockEquivalents.find(table, "autre:pierre_vertical_slab", Map.of()).orElseThrow().getKey();
        assertTrue(generic.flip());
        assertEquals("minecraft:cobblestone_stairs", BlockEquivalents.find(table, "autre:pierre_stairs", Map.of()).orElseThrow().getKey().to().get(0));
        assertTrue(BlockEquivalents.find(table, "unmod:bloc_absent", Map.of()).isEmpty());
    }

    @Test
    void toutDolhimluithAUnEquivalent() throws Exception {
        var table = builtIn();
        // Les 130 sortes de blocs de Middle-earth de Dolhimluith (relevées dans le fichier de Romain).
        List<String> ids = List.of("amphora", "artisan_table", "ashen_bricks", "ashen_cobblestone", "ashen_tiles", "bellows", "big_brazier",
                "black_lebethron_bench", "black_lebethron_chair", "black_lebethron_door", "black_lebethron_fence", "black_lebethron_fence_gate",
                "black_lebethron_ladder", "black_lebethron_log", "black_lebethron_planks", "black_lebethron_roofing", "black_lebethron_roofing_slab",
                "black_lebethron_roofing_stairs", "black_lebethron_shingles", "black_lebethron_shingles_slab", "black_lebethron_shingles_stairs",
                "black_lebethron_shingles_vertical_slab", "black_lebethron_slab", "black_lebethron_stairs", "black_lebethron_table",
                "black_lebethron_trapdoor", "black_lebethron_vertical_slab", "black_pine_leaves", "black_pine_log", "black_stained_lead_glass_pane",
                "black_wattle_and_white_daub", "black_wattle_and_white_daub_cross", "black_wattle_and_white_daub_left",
                "black_wattle_and_white_daub_right", "black_wattle_framed_window_pane", "black_wool_slab", "bracken", "calcite_chair", "calcite_slab",
                "calcite_stairs", "calcite_statue", "calcite_vertical_slab", "candle_heap", "chimney", "cobbled_calcite", "cobbled_diorite",
                "cobbled_diorite_stairs", "cobbly_dirt", "cobbly_dirt_slab", "cobbly_dirt_stairs", "cracked_smooth_calcite",
                "cracked_smooth_calcite_stairs", "cracked_smooth_calcite_vertical_slab", "dark_wattle_and_dark_daub", "dark_wattle_and_dark_daub_cross",
                "dark_wattle_and_dark_daub_diamond", "dark_wattle_and_dark_daub_left", "dark_wattle_and_dark_daub_pillar",
                "dark_wattle_and_dark_daub_right", "dark_wattle_framed_window_pane", "diorite_brickwork", "dirt_stairs", "dry_dirt", "dry_dirt_slab",
                "dry_dirt_stairs", "dwarven_ground_book", "forest_moss", "forest_moss_carpet", "forge", "gabbro", "gabbro_brickwork",
                "gabbro_brickwork_vertical_slab", "gabbro_vertical_slab", "galonn_brickwork", "galonn_brickwork_stairs", "gold_coin_pile", "grass_tuft",
                "grassy_dirt", "grassy_dirt_slab", "grassy_dirt_stairs", "green_shrub", "lebethron_leaves", "mixed_stones_brickwork",
                "mossy_cobbled_diorite", "mossy_cobbled_diorite_stairs", "old_bricks", "old_bricks_slab", "old_bricks_stairs",
                "old_bricks_vertical_slab", "old_thatch_slab", "polished_stone", "red_wattle_and_white_daub", "red_wattle_and_white_daub_cross",
                "red_wattle_and_white_daub_left", "red_wattle_and_white_daub_pillar", "red_wattle_and_white_daub_right", "red_wattle_framed_window_pane",
                "rope", "rotten_thatch_slab", "sconce", "small_crate", "smooth_calcite", "smooth_calcite_slab", "smooth_calcite_stairs",
                "smooth_calcite_vertical_slab", "smooth_diorite", "smooth_diorite_stairs", "stone_brickwork", "stripped_black_lebethron_log",
                "stripped_black_lebethron_wood_fence", "stripped_black_lebethron_wood_slab", "stripped_black_lebethron_wood_wall",
                "stripped_white_lebethron_log", "stucco", "stucco_stairs", "thin_barrel", "treated_anvil", "wall_sconce", "wattle_and_white_daub",
                "wattle_and_white_daub_cross", "wattle_and_white_daub_left", "wattle_and_white_daub_right", "wattle_framed_window",
                "white_lebethron_planks", "wild_carrot", "wild_flax", "wild_garlic", "wild_lettuce", "wild_onion", "yellow_stained_lead_glass_pane");
        assertEquals(130, ids.size());
        for (String id : ids) {
            Optional<?> rule = BlockEquivalents.find(table, "me:" + id, Map.of());
            assertTrue(rule.isPresent(), "sans équivalent : me:" + id);
            // Rien de Middle-earth ne tombe dans les règles génériques : chacun a la sienne.
            assertTrue(table.containsKey(new BlockEquivalents.Key("me:" + id, Map.of())), "règle générique pour me:" + id);
        }
    }

    @Test
    void solDUnPlanQuiApporteSonTerrain() {
        // Le sol des bords : la médiane, si assez de colonnes du bord en ont.
        assertEquals(3, SchematicReader.groundOf(List.of(3, 3, 4, 3, 2), 5));
        assertEquals(12, SchematicReader.groundOf(List.of(12, 11, 13, 12, 12, 12), 8));
        // Un bâtiment seul, sans terre sur ses bords : la couche du bas.
        assertEquals(0, SchematicReader.groundOf(List.of(5), 10));
        assertEquals(0, SchematicReader.groundOf(List.of(), 0));
    }
}
