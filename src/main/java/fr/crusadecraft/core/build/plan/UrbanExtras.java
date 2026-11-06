package fr.crusadecraft.core.build.plan;

import net.minecraft.nbt.CompoundTag;

/**
 * Ce qu'une ville de l'atelier (P60) a de plus qu'une ville ordinaire : son eau, ses ponts, ses champs, ses arbres, ses lanternes, la matière de ses rues et de ses murs.
 * Tout est relevé sur le terrain une seule fois, avec le reste du plan : un chantier repris rejoue exactement les mêmes blocs.
 */
public final class UrbanExtras {
    public int families, streetMaterial, wallType, wallHeight, crop, treeKind, waterLevel = Integer.MIN_VALUE;
    public String name = "";
    public long[] water = new long[0], bridges = new long[0], fields = new long[0], gardens = new long[0], trees = new long[0], lamps = new long[0], towers = new long[0], lintels = new long[0];
    public int[] waterY = new int[0], bridgeY = new int[0], fieldY = new int[0], gardenY = new int[0], treeY = new int[0], lampY = new int[0], lintelY = new int[0];
    /** Le niveau de l'eau, case par case : il suit le terrain par paliers. */
    public int[] waterLevels = new int[0], bridgeLevels = new int[0];
    /** Le fond du monde, par rapport au centre : on ne creuse jamais la roche d'un monde plat. */
    public int floor = Integer.MIN_VALUE;

    /** On n'a rien voulu creuser : les ponts sont alors de simples tabliers sur le sol. */
    public boolean dryBridges() {
        return water.length == 0;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("families", families);
        t.putInt("street", streetMaterial);
        t.putInt("wallType", wallType);
        t.putInt("wallHeight", wallHeight);
        t.putInt("crop", crop);
        t.putInt("treeKind", treeKind);
        t.putInt("waterLevel", waterLevel);
        t.putString("name", name);
        t.putLongArray("water", water);
        t.putLongArray("bridges", bridges);
        t.putLongArray("fields", fields);
        t.putLongArray("gardens", gardens);
        t.putLongArray("trees", trees);
        t.putLongArray("lamps", lamps);
        t.putLongArray("towers", towers);
        t.putLongArray("lintels", lintels);
        t.putIntArray("waterY", waterY);
        t.putIntArray("bridgeY", bridgeY);
        t.putIntArray("fieldY", fieldY);
        t.putIntArray("gardenY", gardenY);
        t.putIntArray("treeY", treeY);
        t.putIntArray("lampY", lampY);
        t.putIntArray("lintelY", lintelY);
        t.putIntArray("waterLevels", waterLevels);
        t.putIntArray("bridgeLevels", bridgeLevels);
        t.putInt("floor", floor);
        return t;
    }

    public static UrbanExtras load(CompoundTag t) {
        UrbanExtras e = new UrbanExtras();
        e.families = t.getInt("families");
        e.streetMaterial = t.getInt("street");
        e.wallType = t.getInt("wallType");
        e.wallHeight = t.getInt("wallHeight");
        e.crop = t.getInt("crop");
        e.treeKind = t.getInt("treeKind");
        e.waterLevel = t.getInt("waterLevel");
        e.name = t.getString("name");
        e.water = t.getLongArray("water");
        e.bridges = t.getLongArray("bridges");
        e.fields = t.getLongArray("fields");
        e.gardens = t.getLongArray("gardens");
        e.trees = t.getLongArray("trees");
        e.lamps = t.getLongArray("lamps");
        e.towers = t.getLongArray("towers");
        e.lintels = t.getLongArray("lintels");
        e.waterY = t.getIntArray("waterY");
        e.bridgeY = t.getIntArray("bridgeY");
        e.fieldY = t.getIntArray("fieldY");
        e.gardenY = t.getIntArray("gardenY");
        e.treeY = t.getIntArray("treeY");
        e.lampY = t.getIntArray("lampY");
        e.lintelY = t.getIntArray("lintelY");
        e.waterLevels = t.getIntArray("waterLevels");
        e.bridgeLevels = t.getIntArray("bridgeLevels");
        e.floor = t.getInt("floor");
        return e;
    }
}
