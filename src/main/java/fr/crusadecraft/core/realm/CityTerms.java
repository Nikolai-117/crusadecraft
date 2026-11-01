package fr.crusadecraft.core.realm;

import fr.crusadecraft.core.ColonyRef;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Une ville du royaume confiée à un gouverneur (P19) : une personne du registre des lignées, et la part de ses impôts
 * et de ses levées qui revient à la couronne ; le reste, il le garde. Une ville sans gouverneur est tenue en direct et
 * n'a pas de termes.
 *
 * @param taxShare  part des impôts pour la couronne, en pour cent
 * @param levyShare part des levées pour la couronne, en pour cent
 */
public record CityTerms(UUID governor, int taxShare, int levyShare) {
    /** Les mêmes conditions, pour un autre gouverneur (son héritier). */
    public CityTerms withGovernor(UUID next) {
        return new CityTerms(next, taxShare, levyShare);
    }

    public CityTerms {
        taxShare = Math.max(0, Math.min(100, taxShare));
        levyShare = Math.max(0, Math.min(100, levyShare));
    }

    CompoundTag save(ColonyRef colony) {
        CompoundTag t = new CompoundTag();
        t.put("colony", colony.save());
        t.putUUID("governor", governor);
        t.putInt("tax", taxShare);
        t.putInt("levy", levyShare);
        return t;
    }

    static CityTerms load(CompoundTag t) {
        if (!t.hasUUID("governor")) return null;
        return new CityTerms(t.getUUID("governor"), t.getInt("tax"), t.getInt("levy"));
    }
}
