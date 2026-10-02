package fr.crusadecraft.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Configuration de CrusadeCraft.
 *
 * <ul>
 *   <li><b>Serveur</b> ({@code saves/<monde>/serverconfig/crusadecraft-server.toml}) : les règles du
 *       jeu, propres à chaque monde.</li>
 *   <li><b>Client</b> ({@code config/crusadecraft-client.toml}) : l'affichage.</li>
 * </ul>
 *
 * Chaque système est activable séparément ; les phases suivantes ajoutent leurs sections ici.
 */
public final class CrusadeConfig {

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    public static final class Server {
        public final ForgeConfigSpec.IntValue minTownHallLevel;
        public final ForgeConfigSpec.BooleanValue throneMustBeInTownHall;
        public final ForgeConfigSpec.IntValue startingPrestige;
        public final ForgeConfigSpec.BooleanValue disableDuplicatesWithWarNTaxes;
        public final ForgeConfigSpec.BooleanValue disableDuplicatesWithWarNNobility;

        Server(ForgeConfigSpec.Builder b) {
            b.comment("Fondation d'un royaume avec le Trône royal").push("fondation");
            minTownHallLevel = b
                    .comment("Niveau minimum de l'Hôtel de ville de la colonie pour y fonder un royaume.")
                    .defineInRange("niveauMinimumHotelDeVille", 2, 1, 5);
            throneMustBeInTownHall = b
                    .comment("Le Trône royal ne peut se poser que dans l'Hôtel de ville d'une colonie que vous gérez.")
                    .define("troneDansHotelDeVille", true);
            startingPrestige = b
                    .comment("Prestige accordé à la fondation du royaume.")
                    .defineInRange("prestigeDeDepart", 50, 0, 100000);
            b.pop();

            b.comment("Cohabitation avec d'autres mods qui font la même chose").push("compatibilite");
            disableDuplicatesWithWarNTaxes = b
                    .comment("Si War 'N Taxes est installé, couper les systèmes de CrusadeCraft qui feraient doublon "
                            + "(impôts, guerre, espionnage). Un avertissement est affiché dans tous les cas.")
                    .define("couperDoublonsWarNTaxes", true);
            disableDuplicatesWithWarNNobility = b
                    .comment("Si War 'n Nobility est installé, couper les systèmes de CrusadeCraft qui feraient doublon "
                            + "(titres, sceaux, vassalité).")
                    .define("couperDoublonsWarNNobility", true);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue showToasts;
        public final ForgeConfigSpec.IntValue toastSeconds;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("Notifications façon CK3 en haut de l'écran").push("notifications");
            showToasts = b.comment("Afficher les notifications.").define("afficher", true);
            toastSeconds = b.comment("Durée d'affichage d'une notification, en secondes.")
                    .defineInRange("duree", 6, 2, 30);
            b.pop();
        }
    }

    private CrusadeConfig() {}
}
