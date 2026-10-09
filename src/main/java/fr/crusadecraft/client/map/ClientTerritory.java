package fr.crusadecraft.client.map;

import fr.crusadecraft.core.world.TerritoryMap;
import fr.crusadecraft.network.packet.TerritorySyncPacket;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les terres que le serveur a confiées au client (P17), chunk par chunk : ce que lisent les cartes des autres mods
 * (Xaero). Une région (32 × 32 chunks, celle des cartes de Xaero) porte une empreinte qui change avec ses chunks.
 */
public final class ClientTerritory {
    /** Une dimension : les royaumes, le royaume de chaque chunk, et l'empreinte de chaque région. */
    public static final class Dim {
        public final List<TerritorySyncPacket.Owner> owners;
        final Long2IntOpenHashMap chunks = new Long2IntOpenHashMap();
        final Long2IntOpenHashMap regions = new Long2IntOpenHashMap();

        Dim(List<TerritorySyncPacket.Owner> owners) {
            this.owners = owners;
            chunks.defaultReturnValue(-1);
            regions.defaultReturnValue(0);
        }
    }

    private static final Map<ResourceLocation, Dim> DIMS = new HashMap<>();
    private static int version;

    private ClientTerritory() {}

    public static void set(TerritorySyncPacket p) {
        Dim d = new Dim(p.owners());
        int[] r = p.rects();
        for (int i = 0; i < r.length; i += 5) {
            for (int x = r[i + 1]; x <= r[i + 3]; x++) {
                for (int z = r[i + 2]; z <= r[i + 4]; z++) {
                    d.chunks.put(TerritoryMap.key(x, z), r[i]);
                    long region = TerritoryMap.key(x >> 5, z >> 5);
                    d.regions.put(region, d.regions.get(region) * 31 + (x * 73856093 ^ z * 19349663) + r[i] * 83492791 + p.owners().get(r[i]).color());
                }
            }
        }
        DIMS.put(p.dimension(), d);
        version++;
    }

    public static void clear() {
        DIMS.clear();
        version++;
    }

    public static int version() {
        return version;
    }

    /** Le royaume de ce chunk, ou {@code null}. */
    public static TerritorySyncPacket.Owner owner(ResourceLocation dim, int cx, int cz) {
        Dim d = DIMS.get(dim);
        if (d == null) return null;
        int i = d.chunks.get(TerritoryMap.key(cx, cz));
        return i < 0 || i >= d.owners.size() ? null : d.owners.get(i);
    }

    /** L'indice du royaume de ce chunk (pour comparer deux voisins), ou −1. */
    public static int ownerIndex(ResourceLocation dim, int cx, int cz) {
        Dim d = DIMS.get(dim);
        return d == null ? -1 : d.chunks.get(TerritoryMap.key(cx, cz));
    }

    /** Une région de 32 × 32 chunks a-t-elle des terres ? */
    public static boolean regionHasLand(ResourceLocation dim, int rx, int rz) {
        Dim d = DIMS.get(dim);
        return d != null && d.regions.containsKey(TerritoryMap.key(rx, rz));
    }

    /** L'empreinte d'une région : elle change quand ses terres changent. */
    public static int regionHash(ResourceLocation dim, int rx, int rz) {
        Dim d = DIMS.get(dim);
        return d == null ? 0 : d.regions.get(TerritoryMap.key(rx, rz));
    }
}
