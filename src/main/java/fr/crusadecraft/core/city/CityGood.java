package fr.crusadecraft.core.city;

import java.util.Locale;

/** Ce qu'une ville produit, stocke et vend (P43). */
public enum CityGood {
    BLE, PAIN, VIANDE, REPAS, FER, OUTILS, PIERRE, BOIS, CUIR, LIVRE;

    public String key() {
        return "crusadecraft.city.good." + name().toLowerCase(Locale.ROOT);
    }

    /** La marchandise que représente un objet du jeu (d'après son identifiant), ou {@code null} : ce qui ne compte pas au stock. */
    public static CityGood ofItem(String path) {
        if (path == null) return null;
        String p = path.substring(path.indexOf(':') + 1);
        if (p.equals("emerald")) return null;
        if (p.equals("bread") || p.equals("cookie") || p.equals("cake") || p.equals("pumpkin_pie")) return PAIN;
        if (p.equals("wheat") || p.equals("wheat_seeds") || p.equals("carrot") || p.equals("potato") || p.equals("beetroot") || p.equals("hay_block")
                || p.equals("sugar") || p.equals("pumpkin") || p.equals("melon") || p.equals("melon_slice") || p.equals("apple")) return BLE;
        if (p.endsWith("_stew") || p.equals("baked_potato") || p.equals("beetroot_soup") || p.equals("honey_bottle")) return REPAS;
        if (p.startsWith("cooked_") || p.equals("beef") || p.equals("porkchop") || p.equals("mutton") || p.equals("chicken") || p.equals("rabbit")
                || p.equals("cod") || p.equals("salmon") || p.equals("rotten_flesh")) return VIANDE;
        if (p.endsWith("_sword") || p.endsWith("_pickaxe") || p.endsWith("_axe") || p.endsWith("_shovel") || p.endsWith("_hoe") || p.endsWith("_helmet")
                || p.endsWith("_chestplate") || p.endsWith("_leggings") || p.endsWith("_boots") || p.equals("shield") || p.equals("bell") || p.equals("shears")) return OUTILS;
        if (p.equals("iron_ingot") || p.equals("coal") || p.equals("gold_ingot") || p.equals("iron_nugget")) return FER;
        if (p.startsWith("stone") || p.contains("brick") || p.contains("terracotta") || p.equals("clay_ball") || p.equals("quartz_pillar")
                || p.startsWith("polished_") || p.startsWith("chiseled_") || p.equals("dripstone_block") || p.equals("flint") || p.equals("quartz_block")) return PIERRE;
        if (p.endsWith("_log") || p.endsWith("_planks") || p.equals("stick") || p.equals("arrow") || p.equals("bow") || p.equals("crossbow")) return BOIS;
        if (p.startsWith("leather") || p.equals("saddle") || p.equals("rabbit_hide") || p.endsWith("_wool") || p.equals("string")) return CUIR;
        if (p.equals("book") || p.equals("paper") || p.equals("bookshelf") || p.equals("compass") || p.equals("map") || p.equals("clock") || p.equals("lantern")
                || p.equals("enchanted_book") || p.equals("glass") || p.equals("name_tag")) return LIVRE;
        return null;
    }
}
