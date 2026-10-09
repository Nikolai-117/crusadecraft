package fr.crusadecraft.compat.xaero;

import fr.crusadecraft.client.map.ClientTerritory;
import fr.crusadecraft.network.packet.TerritorySyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.WorldMap;
import xaero.map.highlight.ChunkHighlighter;

import java.util.List;

/**
 * Nos terres sur la carte et la minimap de Xaero (P17), chunk par chunk, comme Xaero colore les claims d'Open Parties
 * and Claims : chaque chunk à la couleur de son royaume, son bord souligné du côté où le voisin change de maître, et
 * le nom du royaume au survol. On reprend les réglages d'opacité des claims de Xaero, et son interrupteur.
 *
 * <p>Les couleurs de Xaero sont rangées en BGRA (rouge et bleu inversés, opacité dans l'octet de poids faible).
 */
public final class CrusadeHighlighter extends ChunkHighlighter {

    public CrusadeHighlighter() {
        super(true);
    }

    private static boolean shown() {
        return WorldMap.settings == null || WorldMap.settings.displayClaims;
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dim, int regionX, int regionZ) {
        return shown() && ClientTerritory.regionHasLand(dim.location(), regionX, regionZ);
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dim, int regionX, int regionZ) {
        return ClientTerritory.regionHash(dim.location(), regionX, regionZ) * 31 + (shown() ? 1 : 0) + opacityHash();
    }

    private static int opacityHash() {
        return WorldMap.settings == null ? 0 : WorldMap.settings.claimsFillOpacity * 131 + WorldMap.settings.claimsBorderOpacity;
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        return shown() && ClientTerritory.ownerIndex(dim.location(), chunkX, chunkZ) >= 0;
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        if (!shown()) return null;
        var loc = dim.location();
        int owner = ClientTerritory.ownerIndex(loc, chunkX, chunkZ);
        if (owner < 0) return null;
        TerritorySyncPacket.Owner o = ClientTerritory.owner(loc, chunkX, chunkZ);
        int rgb = o == null ? 0x808080 : o.color();
        int bgr = ((rgb & 0xFF) << 24) | (((rgb >> 8) & 0xFF) << 16) | (((rgb >> 16) & 0xFF) << 8);
        int fillOpacity = WorldMap.settings == null ? 46 : WorldMap.settings.claimsFillOpacity;
        int borderOpacity = WorldMap.settings == null ? 80 : WorldMap.settings.claimsBorderOpacity;
        int fill = bgr | (fillOpacity * 255 / 100);
        int border = bgr | (borderOpacity * 255 / 100);
        resultStore[0] = fill;
        resultStore[1] = ClientTerritory.ownerIndex(loc, chunkX, chunkZ - 1) == owner ? fill : border;
        resultStore[2] = ClientTerritory.ownerIndex(loc, chunkX + 1, chunkZ) == owner ? fill : border;
        resultStore[3] = ClientTerritory.ownerIndex(loc, chunkX, chunkZ + 1) == owner ? fill : border;
        resultStore[4] = ClientTerritory.ownerIndex(loc, chunkX - 1, chunkZ) == owner ? fill : border;
        return resultStore;
    }

    private static Component name(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        TerritorySyncPacket.Owner o = ClientTerritory.owner(dim.location(), chunkX, chunkZ);
        if (o == null) return null;
        return o.name().copy().withStyle(s -> s.withColor(TextColor.fromRgb(o.color())));
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        return shown() ? name(dim, chunkX, chunkZ) : null;
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        Component n = shown() ? name(dim, chunkX, chunkZ) : null;
        return n == null ? null : Component.translatable("crusadecraft.xaero.lands", n);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dim, int blockX, int blockZ, int width) {
        Component n = shown() ? name(dim, blockX >> 4, blockZ >> 4) : null;
        if (n != null) list.add(n);
    }
}
