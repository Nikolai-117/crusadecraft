package fr.crusadecraft.core.build.plan;

import java.util.ArrayList;
import java.util.List;

/** Les réglages tout prêts de l'atelier (P60) : des points de départ, pour qu'une ville se fasse aussi en deux clics. On change ensuite ce qu'on veut. */
public final class UrbanPresets {
    public enum Preset {
        HAMEAU, VILLAGE, VILLAGE_FORTIFIE, BOURG_MARCHAND, VILLE_FORTIFIEE, CITE_PORTUAIRE, ILE, VILLE_RIVIERE, DAMIER, ANNEAUX, CAPITALE;

        public String key() {
            return "crusadecraft.urban.preset." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private UrbanPresets() {}

    public static List<Preset> all() {
        return new ArrayList<>(List.of(Preset.values()));
    }

    /** Ce préréglage, posé sur des réglages qui gardent leur nom, leur civilisation et leur tirage. */
    public static UrbanParams apply(Preset preset, UrbanParams from) {
        UrbanParams p = new UrbanParams().name(from.name()).seed(from.seed());
        p.set(UrbanParams.STYLE, from.get(UrbanParams.STYLE));
        switch (preset) {
            case HAMEAU -> {
                p.set(UrbanParams.HABITANTS, 40).set(UrbanParams.MURS, 0).set(UrbanParams.PLACE, 0).set(UrbanParams.TEMPLE, 0).set(UrbanParams.ROUTES, 2);
                p.set(UrbanParams.NIVEAU_MAX, 2).set(UrbanParams.PLACEMENT, 2).set(UrbanParams.CHAMPS, 1).set(UrbanParams.SURFACE_CHAMPS, 60).set(UrbanParams.FORETS, 30);
            }
            case VILLAGE -> {
                p.set(UrbanParams.HABITANTS, 150).set(UrbanParams.MURS, 0).set(UrbanParams.TAILLE_PLACE, 14).set(UrbanParams.ROUTES, 3);
                p.set(UrbanParams.NIVEAU_MAX, 3).set(UrbanParams.PLACEMENT, 2).set(UrbanParams.CHAMPS, 1).set(UrbanParams.SURFACE_CHAMPS, 50);
            }
            case VILLAGE_FORTIFIE -> {
                p.set(UrbanParams.HABITANTS, 200).set(UrbanParams.TYPE_MUR, 4).set(UrbanParams.HAUTEUR_MUR, 4).set(UrbanParams.TOUR_TOUS, 30);
                p.set(UrbanParams.NIVEAU_MAX, 3).set(UrbanParams.FAUBOURGS, 0).set(UrbanParams.ROUTES, 2);
            }
            case BOURG_MARCHAND -> {
                p.set(UrbanParams.HABITANTS, 500).set(UrbanParams.TYPE_MUR, 1).set(UrbanParams.Q_MARCHE, 4).set(UrbanParams.Q_MARCHANDS, 6).set(UrbanParams.RICHESSE, 3);
                p.set(UrbanParams.TAILLE_PLACE, 30).set(UrbanParams.b(TownSize.Role.STALL), 16);
            }
            case VILLE_FORTIFIEE -> {
                p.set(UrbanParams.HABITANTS, 900).set(UrbanParams.TYPE_MUR, 2).set(UrbanParams.HAUTEUR_MUR, 10).set(UrbanParams.EPAISSEUR_MUR, 3).set(UrbanParams.TOUR_TOUS, 20);
                p.set(UrbanParams.CITADELLE, 1).set(UrbanParams.DOUVES, 1).set(UrbanParams.Q_MILITAIRE, 3).set(UrbanParams.FAUBOURGS, 20);
            }
            case CITE_PORTUAIRE -> {
                p.set(UrbanParams.HABITANTS, 800).set(UrbanParams.COTE, 1).set(UrbanParams.PART_MER, 35).set(UrbanParams.Q_PORT, 5).set(UrbanParams.Q_MARCHANDS, 5);
                p.set(UrbanParams.TYPE_MUR, 1).set(UrbanParams.TAILLE_PLACE, 26);
            }
            case ILE -> {
                p.set(UrbanParams.HABITANTS, 500).set(UrbanParams.COTE, 2).set(UrbanParams.Q_PORT, 4).set(UrbanParams.TYPE_MUR, 2).set(UrbanParams.FAUBOURGS, 0);
                p.set(UrbanParams.CHAMPS, 0).set(UrbanParams.ROUTES_CAMPAGNE, 0);
            }
            case VILLE_RIVIERE -> {
                p.set(UrbanParams.HABITANTS, 700).set(UrbanParams.RIVIERE, 1).set(UrbanParams.LARGEUR_RIVIERE, 10).set(UrbanParams.PONTS, 3).set(UrbanParams.TYPE_MUR, 1);
                p.set(UrbanParams.CHAMPS, 2).set(UrbanParams.Q_ARTISANS, 5);
            }
            case DAMIER -> {
                p.set(UrbanParams.HABITANTS, 600).set(UrbanParams.TYPE_MUR, 2).set(UrbanParams.PORTES, 4);
                p.applyModel(2);
            }
            case ANNEAUX -> {
                p.set(UrbanParams.HABITANTS, 700).set(UrbanParams.TYPE_MUR, 1);
                p.applyModel(3);
            }
            case CAPITALE -> {
                p.set(UrbanParams.HABITANTS, 2000).set(UrbanParams.TYPE_MUR, 2).set(UrbanParams.HAUTEUR_MUR, 12).set(UrbanParams.EPAISSEUR_MUR, 4).set(UrbanParams.CITADELLE, 1);
                p.set(UrbanParams.CHATEAU_EN_VILLE, 1).set(UrbanParams.TAILLE_CITADELLE, 70).set(UrbanParams.TAILLE_PLACE, 44).set(UrbanParams.RICHESSE, 4).set(UrbanParams.FAUBOURGS, 25);
                p.set(UrbanParams.ROUTES, 6).set(UrbanParams.PONTS, 4).set(UrbanParams.RIVIERE, 1).set(UrbanParams.LARGEUR_RIVIERE, 12);
            }
        }
        return p;
    }
}
