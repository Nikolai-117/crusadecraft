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
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> statRules;
        public final ForgeConfigSpec.IntValue traitsPerCharacter;
        public final ForgeConfigSpec.IntValue refuseOfficeBelowOpinion;
        public final ForgeConfigSpec.ConfigValue<String> rewardItem;
        public final ForgeConfigSpec.IntValue rewardCount;
        public final ForgeConfigSpec.IntValue exileDays;
        public final ForgeConfigSpec.IntValue councilMinTownHallLevel;
        public final ForgeConfigSpec.IntValue meetingHour;
        public final ForgeConfigSpec.IntValue meetingMinutes;
        public final ForgeConfigSpec.IntValue officeHour;
        public final ForgeConfigSpec.IntValue officeMinutes;
        public final ForgeConfigSpec.BooleanValue npcMovement;
        public final ForgeConfigSpec.BooleanValue roleXp;
        public final ForgeConfigSpec.DoubleValue roleXpPerDay;
        public final ForgeConfigSpec.IntValue homelessThreshold;
        public final ForgeConfigSpec.IntValue joblessThreshold;
        public final ForgeConfigSpec.IntValue sickThreshold;
        public final ForgeConfigSpec.IntValue hungryThreshold;
        public final ForgeConfigSpec.IntValue requestExpiryDays;
        public final ForgeConfigSpec.IntValue maxPendingRequests;
        public final ForgeConfigSpec.BooleanValue crownHappiness;
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

            b.comment("Personnages : aptitudes CK3 calculées depuis les compétences MineColonies").push("personnages");
            statRules = b
                    .comment("Une règle par aptitude, dans l'ordre : Diplomatie, Martial, Intendance, Intrigue, Érudition.",
                            "Format : \"Competence:poids, Competence:poids / diviseur\". Compétences MineColonies : Athletics,",
                            "Dexterity, Strength, Agility, Stamina, Mana, Adaptability, Focus, Creativity, Knowledge, Intelligence.",
                            "Par défaut : paires complémentaires de MineColonies (Force et Agilité y sont adverses).")
                    .defineList("reglesAptitudes", fr.crusadecraft.core.character.StatFormula.DEFAULT_RULES, o -> o instanceof String);
            traitsPerCharacter = b.comment("Nombre de traits de personnalité par personnage.")
                    .defineInRange("traitsParPersonnage", 3, 1, 5);
            refuseOfficeBelowOpinion = b.comment("En dessous de cette opinion, un personnage refuse le poste qu'on lui propose.")
                    .defineInRange("refusPosteSousOpinion", -25, -100, 100);
            rewardItem = b.comment("Objet offert pour récompenser un personnage (pris dans l'inventaire du souverain).")
                    .define("objetRecompense", "minecraft:gold_ingot");
            rewardCount = b.comment("Quantité offerte.").defineInRange("quantiteRecompense", 1, 1, 64);
            exileDays = b.comment("Durée d'un exil, en jours du monde.").defineInRange("dureeExil", 30, 1, 3650);
            b.pop();

            b.comment("Conseil royal, maires et requêtes").push("conseil");
            councilMinTownHallLevel = b.comment("Niveau d'Hôtel de ville requis dans la colonie où l'on pose la Table du Conseil.")
                    .defineInRange("niveauHotelDeVilleTable", 3, 1, 5);
            meetingHour = b.comment("Heure de la réunion quotidienne du conseil (0 à 23 ; 6 = lever du jour).")
                    .defineInRange("heureReunion", 9, 0, 23);
            meetingMinutes = b.comment("Durée de la réunion, en minutes du monde (une heure du monde = 50 secondes).")
                    .defineInRange("dureeReunion", 90, 10, 600);
            officeHour = b.comment("Heure à laquelle les maires rejoignent leur bureau.").defineInRange("heureBureau", 13, 0, 23);
            officeMinutes = b.comment("Durée des heures de bureau, en minutes du monde.").defineInRange("dureeBureau", 120, 10, 600);
            npcMovement = b.comment("Les dignitaires se déplacent (réunions, bureau, audiences). Sans cela, tout reste dans les écrans.")
                    .define("deplacementsPnj", true);
            roleXp = b.comment("Un poste fait progresser les compétences liées de son titulaire (écriture native MineColonies).")
                    .define("xpDesPostes", true);
            roleXpPerDay = b.comment("Expérience gagnée chaque jour par compétence liée au poste.")
                    .defineInRange("xpParJour", 4.0, 0.0, 100.0);
            homelessThreshold = b.comment("Sans-logis à partir desquels le maire vient se plaindre.").defineInRange("seuilSansLogis", 2, 1, 100);
            joblessThreshold = b.comment("Chômeurs à partir desquels le maire vient se plaindre.").defineInRange("seuilChomeurs", 3, 1, 100);
            sickThreshold = b.comment("Malades à partir desquels le maire vient se plaindre.").defineInRange("seuilMalades", 2, 1, 100);
            hungryThreshold = b.comment("Affamés à partir desquels le maire vient se plaindre.").defineInRange("seuilAffames", 2, 1, 100);
            requestExpiryDays = b.comment("Jours avant qu'une requête restée sans réponse soit considérée comme ignorée.")
                    .defineInRange("expirationRequetes", 3, 1, 30);
            maxPendingRequests = b.comment("Requêtes en attente au plus, par royaume.").defineInRange("requetesMax", 6, 1, 30);
            crownHappiness = b.comment("Le chapelain peut agir sur le bonheur MineColonies (modificateur « Couronne », jamais enregistré chez MineColonies).")
                    .define("bonheurCouronne", true);
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
