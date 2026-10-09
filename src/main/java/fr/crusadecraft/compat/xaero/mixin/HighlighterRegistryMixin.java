package fr.crusadecraft.compat.xaero.mixin;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.xaero.CrusadeHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.highlight.HighlighterRegistry;

/**
 * La seule greffe dans Xaero's World Map (P17). Xaero n'a pas d'API publique pour colorer des chunks : sa liste de
 * « surligneurs » (celle qui sert aux claims d'OPAC) est fermée par {@code end()} dès l'ouverture de la carte. On y
 * inscrit le nôtre juste avant. Sans Xaero, cette classe n'est jamais appliquée ({@link Pseudo}) ; si Xaero change
 * cette méthode, l'injection ne trouve rien et la couche disparaît simplement, sans planter le jeu.
 */
@Pseudo
@Mixin(targets = "xaero.map.highlight.HighlighterRegistry", remap = false)
public abstract class HighlighterRegistryMixin {

    @Inject(method = "end", at = @At("HEAD"), remap = false, require = 0)
    private void crusadecraft$addLands(CallbackInfo ci) {
        ((HighlighterRegistry) (Object) this).register(new CrusadeHighlighter());
        CrusadeCraft.LOGGER.info("CrusadeCraft : terres des royaumes ajoutées à Xaero's World Map");
    }
}
