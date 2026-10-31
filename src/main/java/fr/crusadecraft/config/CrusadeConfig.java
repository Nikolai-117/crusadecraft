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
        public final ForgeConfigSpec.BooleanValue taxes;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> taxRates;
        public final ForgeConfigSpec.DoubleValue taxBase;
        public final ForgeConfigSpec.DoubleValue taxPerLevel;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> upkeepRates;
        public final ForgeConfigSpec.IntValue councilSalary;
        public final ForgeConfigSpec.IntValue mayorSalary;
        public final ForgeConfigSpec.IntValue debtLimit;
        public final ForgeConfigSpec.IntValue debtPrestigeLoss;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> goldValues;
        public final ForgeConfigSpec.BooleanValue allowWithdraw;
        public final ForgeConfigSpec.IntValue rewardGold;
        public final ForgeConfigSpec.BooleanValue taxHappiness;
        public final ForgeConfigSpec.IntValue lawCooldownDays;
        public final ForgeConfigSpec.IntValue forceCostPerVote;
        public final ForgeConfigSpec.BooleanValue aiRealms;
        public final ForgeConfigSpec.IntValue aiRealmCount;
        public final ForgeConfigSpec.IntValue aiMinDistance;
        public final ForgeConfigSpec.IntValue aiMaxDistance;
        public final ForgeConfigSpec.IntValue territoryRadius;
        public final ForgeConfigSpec.IntValue colonyRadius;
        public final ForgeConfigSpec.EnumValue<fr.crusadecraft.core.world.WorldEditService.Access> mapEditor;
        public final ForgeConfigSpec.BooleanValue survivalClaims;
        public final ForgeConfigSpec.IntValue claimCellCost;
        public final ForgeConfigSpec.IntValue claimMaxDistance;
        public final ForgeConfigSpec.IntValue claimMaxCells;
        public final ForgeConfigSpec.BooleanValue survivalBuilding;
        public final ForgeConfigSpec.IntValue buildBlocksPerStep;
        public final ForgeConfigSpec.IntValue buildStepSeconds;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends Integer>> claimCaps;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends Integer>> domainLimits;
        public final ForgeConfigSpec.IntValue domainPenalty;
        public final ForgeConfigSpec.IntValue governorTaxShare;
        public final ForgeConfigSpec.IntValue governorLevyShare;
        public final ForgeConfigSpec.IntValue claimCommandRadius;
        public final ForgeConfigSpec.EnumValue<fr.crusadecraft.core.world.EntryTitles.Mode> entryMessages;
        public final ForgeConfigSpec.BooleanValue protection;
        public final ForgeConfigSpec.BooleanValue protectAiLand;
        public final ForgeConfigSpec.BooleanValue protectVassals;
        public final ForgeConfigSpec.BooleanValue alliesInteract;
        public final ForgeConfigSpec.BooleanValue operatorsBypass;
        public final ForgeConfigSpec.BooleanValue webMap;
        public final ForgeConfigSpec.IntValue webMapSeconds;
        public final ForgeConfigSpec.ConfigValue<String> webMapLanguage;
        public final ForgeConfigSpec.BooleanValue webMapAiRealms;
        public final ForgeConfigSpec.BooleanValue webMapPlaces;
        public final ForgeConfigSpec.BooleanValue webMapTreasury;
        public final ForgeConfigSpec.BooleanValue webMapCouncil;
        public final ForgeConfigSpec.BooleanValue webMapWar;
        public final ForgeConfigSpec.DoubleValue webMapOpacity;
        public final ForgeConfigSpec.BooleanValue clientTerritory;
        public final ForgeConfigSpec.IntValue discoveryRadius;
        public final ForgeConfigSpec.BooleanValue heralds;
        public final ForgeConfigSpec.IntValue heraldSeconds;
        public final ForgeConfigSpec.IntValue baronyCount;
        public final ForgeConfigSpec.IntValue letterDelaySeconds;
        public final ForgeConfigSpec.IntValue aiLetterMinutes;
        public final ForgeConfigSpec.IntValue mailboxMax;
        public final ForgeConfigSpec.BooleanValue chanceryNeedsResearch;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends Integer>> titleProvinces;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends Integer>> titleGold;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends Integer>> titlePrestige;
        public final ForgeConfigSpec.IntValue truceDays;
        public final ForgeConfigSpec.BooleanValue sieges;
        public final ForgeConfigSpec.IntValue siegeRadius;
        public final ForgeConfigSpec.IntValue captureSeconds;
        public final ForgeConfigSpec.BooleanValue raids;
        public final ForgeConfigSpec.IntValue raidMinutes;
        public final ForgeConfigSpec.BooleanValue antiGrief;
        public final ForgeConfigSpec.BooleanValue hostileRanks;
        public final ForgeConfigSpec.BooleanValue hywTroops;
        public final ForgeConfigSpec.BooleanValue aiDecisions;
        public final ForgeConfigSpec.IntValue aiDecisionChance;
        public final ForgeConfigSpec.BooleanValue aiWars;
        public final ForgeConfigSpec.BooleanValue aiWarsOnPlayers;
        public final ForgeConfigSpec.IntValue aiGraceDays;
        public final ForgeConfigSpec.IntValue aiWarRange;
        public final ForgeConfigSpec.BooleanValue coalitions;
        public final ForgeConfigSpec.IntValue coalitionThreshold;
        public final ForgeConfigSpec.BooleanValue worldNewsToasts;
        public final ForgeConfigSpec.BooleanValue events;
        public final ForgeConfigSpec.IntValue eventChance;
        public final ForgeConfigSpec.BooleanValue factions;
        public final ForgeConfigSpec.BooleanValue plots;
        public final ForgeConfigSpec.BooleanValue revolts;
        public final ForgeConfigSpec.BooleanValue tournamentNeedsResearch;
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
            rewardGold = b.comment("Or pris dans le trésor pour récompenser un personnage.")
                    .defineInRange("orRecompense", 9, 0, 100000);
            rewardItem = b.comment("Si le trésor n'y suffit pas : objet pris dans l'inventaire du souverain.")
                    .define("objetRecompense", "minecraft:gold_ingot");
            rewardCount = b.comment("Quantité de cet objet.").defineInRange("quantiteRecompense", 1, 1, 64);
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

            b.comment("Trésor, impôts et dépenses. L'unité est la pièce d'or (une pépite au Coffre du Trésor).").push("economie");
            taxes = b.comment("Les colonies du royaume paient l'impôt chaque jour (coupé si War 'N Taxes gère déjà les impôts).")
                    .define("impots", true);
            taxRates = b.comment("Barème par bâtiment MineColonies, en or par jour : \"nom=base:parNiveau\".",
                            "Les bâtiments absents paient le barème par défaut ci-dessous ; les bâtiments militaires ne paient rien.")
                    .defineList("baremeImpots", fr.crusadecraft.core.treasury.EconomyDefaults.TAX_RATES, o -> o instanceof String);
            taxBase = b.comment("Barème par défaut : base, en or par jour.").defineInRange("impotBase", 0.5, 0.0, 1000.0);
            taxPerLevel = b.comment("Barème par défaut : supplément par niveau du bâtiment.").defineInRange("impotParNiveau", 0.5, 0.0, 1000.0);
            upkeepRates = b.comment("Entretien des bâtiments militaires, en or par jour : \"nom=base:parNiveau\".")
                    .defineList("entretienMilitaire", fr.crusadecraft.core.treasury.EconomyDefaults.UPKEEP_RATES, o -> o instanceof String);
            councilSalary = b.comment("Salaire quotidien d'un conseiller.").defineInRange("salaireConseiller", 2, 0, 10000);
            mayorSalary = b.comment("Salaire quotidien d'un maire.").defineInRange("salaireMaire", 1, 0, 10000);
            debtLimit = b.comment("Dette maximale du trésor. Au-delà, salaires et entretien ne sont plus payés.")
                    .defineInRange("detteMaximale", 200, 0, 1000000);
            debtPrestigeLoss = b.comment("Prestige perdu chaque jour passé dans le rouge.").defineInRange("prestigePerduParJourDeDette", 2, 0, 1000);
            goldValues = b.comment("Valeur de l'or physique au Coffre du Trésor : \"objet=valeur\".")
                    .defineList("valeursOr", fr.crusadecraft.core.treasury.GoldValues.DEFAULTS, o -> o instanceof String);
            allowWithdraw = b.comment("Le souverain peut retirer de l'or physique du trésor.").define("retraitsAutorises", true);
            taxHappiness = b.comment("La loi de fiscalité agit sur le bonheur MineColonies (modificateur « Impôts », jamais enregistré chez MineColonies).")
                    .define("bonheurImpots", true);
            b.pop();

            b.comment("Lois du royaume").push("lois");
            lawCooldownDays = b.comment("Jours à attendre avant de rechanger une même loi.").defineInRange("delaiEntreChangements", 3, 0, 365);
            forceCostPerVote = b.comment("Prestige à dépenser par voix contre pour imposer une loi rejetée par le conseil.")
                    .defineInRange("prestigeParVoixContre", 20, 0, 10000);
            b.pop();

            b.comment("Le monde : royaumes IA, découverte, carte").push("monde");
            aiRealms = b.comment("Des royaumes IA naissent autour de la capitale du premier royaume fondé. Ce ne sont que des",
                            "données : aucune colonie, aucun bloc posé ; leurs sièges s'ancrent sur des villages existants.")
                    .define("royaumesIA", true);
            aiRealmCount = b.comment("Nombre de royaumes IA.").defineInRange("nombreRoyaumesIA", 6, 1, 24);
            aiMinDistance = b.comment("Distance minimale, en blocs, entre la capitale fondatrice et une capitale IA.")
                    .defineInRange("distanceMinimale", 600, 150, 20000);
            aiMaxDistance = b.comment("Distance maximale, en blocs.").defineInRange("distanceMaximale", 2500, 300, 50000);
            territoryRadius = b.comment("Rayon du territoire d'une province IA sur la carte, en blocs.").defineInRange("rayonProvince", 160, 32, 2000);
            colonyRadius = b.comment("Rayon du territoire d'une colonie de joueur sur la carte, en blocs.").defineInRange("rayonColonie", 110, 32, 2000);
            discoveryRadius = b.comment("Distance à un siège IA à partir de laquelle on découvre son royaume.")
                    .defineInRange("rayonDecouverte", 192, 32, 2000);
            heralds = b.comment("Un héraut temporaire vient saluer le joueur qui découvre un royaume (il repart ensuite).")
                    .define("herauts", true);
            heraldSeconds = b.comment("Durée de présence du héraut, en secondes.").defineInRange("dureeHeraut", 45, 10, 600);
            baronyCount = b.comment("Seigneuries indépendantes (une province) autour de la capitale : les premières que l'on peut vassaliser.")
                    .defineInRange("seigneuriesIndependantes", 4, 0, 24);
            mapEditor = b.comment("Qui peut ouvrir l'éditeur de la carte (poser royaumes et villes, peindre les frontières) :",
                            "TOUS, HOTE_ET_OPERATEURS (l'hôte d'une partie solo et les opérateurs) ou PERSONNE.")
                    .defineEnum("editeurCarte", fr.crusadecraft.core.world.WorldEditService.Access.HOTE_ET_OPERATEURS);
            b.pop();

            b.comment("Territoire : bâton de bornage et constructions (P14)").push("territoire");
            survivalClaims = b.comment("En survie, un joueur peut étendre son propre royaume au bâton de bornage (l'éditeur de la carte, lui, peut tout).")
                    .define("bornageSurvie", true);
            claimCellCost = b.comment("Or demandé au trésor pour chaque case de 16 blocs prise en survie.").defineInRange("coutCaseOr", 5, 0, 10000);
            claimMaxDistance = b.comment("Distance maximale, en blocs, entre une case prise en survie et une de vos colonies ou constructions.")
                    .defineInRange("distanceMaxBlocs", 256, 16, 10000);
            claimMaxCells = b.comment("Cases prises au plus d'un coup en survie.").defineInRange("casesMaxParTrace", 256, 1, 16384);
            survivalBuilding = b.comment("En survie, un joueur peut bâtir des constructions pour son propre royaume (chantier payé par le trésor).")
                    .define("constructionsSurvie", true);
            buildBlocksPerStep = b.comment("Blocs posés à chaque étape d'un chantier en survie.").defineInRange("chantierBlocsParEtape", 6, 1, 512);
            buildStepSeconds = b.comment("Secondes entre deux étapes d'un chantier en survie.").defineInRange("chantierSecondes", 2, 1, 600);
            claimCaps = b.comment("Chunks qu'un royaume peut revendiquer à la main, selon son rang : baronnie, comté, duché, royaume, empire.",
                            "Les terres autour de ses villes et de ses constructions ne comptent pas.")
                    .defineList("plafondChunks", java.util.List.of(64, 160, 400, 1000, 2500), o -> o instanceof Integer i && i >= 0);
            claimCommandRadius = b.comment("Rayon maximal de « /crusade claim <rayon> », en chunks (1 : un carré de 3 × 3).")
                    .defineInRange("rayonMaxClaim", 3, 0, 10);
            entryMessages = b.comment("À l'entrée sur des terres : un titre en grand, une ligne au-dessus de la barre d'objets, ou rien.")
                    .defineEnum("messagesEntree", fr.crusadecraft.core.world.EntryTitles.Mode.TITRE);
            b.pop();

            b.comment("Protection des terres (P16), chunk par chunk, selon le lien avec leur royaume").push("protection");
            protection = b.comment("Sur les terres d'un royaume de joueur, seuls les siens cassent, posent et ouvrent les coffres.")
                    .define("active", true);
            protectAiLand = b.comment("Les terres des royaumes IA sont protégées de la même façon (non : on y explore librement).")
                    .define("terresIA", false);
            protectVassals = b.comment("Un suzerain et ses vassaux peuvent tout faire sur les terres les uns des autres.")
                    .define("vassauxLibres", true);
            alliesInteract = b.comment("Un allié peut ouvrir portes et coffres (mais ni casser ni poser).")
                    .define("alliesOuvrent", true);
            operatorsBypass = b.comment("Les opérateurs du serveur passent outre.").define("operateursLibres", true);
            b.pop();

            b.comment("Carte web (P17) : les royaumes chunk par chunk sur Dynmap, avec leur fiche au clic. Sans Dynmap, rien ne se passe.")
                    .push("carteWeb");
            webMap = b.comment("Dessiner les royaumes et les lieux sur Dynmap.").define("dynmap", true);
            webMapSeconds = b.comment("Tous les combien de secondes la carte web est remise à jour (seulement si quelque chose a changé).")
                    .defineInRange("secondes", 20, 5, 3600);
            webMapLanguage = b.comment("Langue des fiches : fr_fr ou en_us.").define("langue", "fr_fr");
            webMapAiRealms = b.comment("Montrer aussi les royaumes de l'ordinateur (la page web est la même pour tous : pas de brouillard).")
                    .define("royaumesIA", true);
            webMapPlaces = b.comment("Montrer les lieux : villes, forts, lieux saints, ruines, régions.").define("lieux", true);
            webMapTreasury = b.comment("Montrer le trésor des royaumes dans leur fiche.").define("tresor", true);
            webMapCouncil = b.comment("Montrer le conseil des royaumes de l'ordinateur dans leur fiche.").define("conseil", true);
            webMapWar = b.comment("Montrer la carte de guerre (P18) dans sa propre couche : zones, flèches, repères et sièges en cours.",
                    "Attention : la page web est la même pour tous, les plans de chaque royaume y sont donc visibles de tous.")
                    .define("guerre", true);
            webMapOpacity = b.comment("Opacité de la couleur des terres (0 : transparentes, 1 : pleines).").defineInRange("opacite", 0.35, 0.0, 1.0);
            clientTerritory = b.comment("Envoyer aux joueurs les terres des royaumes qu'ils connaissent, pour Xaero's World Map et Minimap.")
                    .define("xaero", true);
            b.pop();

            b.comment("Les villes du royaume (P19) : les colonies rattachées, tenues en direct ou confiées à un gouverneur.").push("villes");
            domainLimits = b.comment("Villes qu'un souverain tient en direct sans pénalité, selon son rang : baronnie, comté, duché, royaume, empire.",
                            "Au-delà, chaque ville tenue en direct rend moins d'impôts ; une ville confiée à un gouverneur ne compte pas.")
                    .defineList("limiteDomaine", java.util.List.of(2, 3, 4, 6, 8), o -> o instanceof Integer i && i >= 1);
            domainPenalty = b.comment("Impôts perdus, en pour cent, sur chaque ville tenue en direct au-delà de la limite.")
                    .defineInRange("penaliteDomaine", 30, 0, 100);
            governorTaxShare = b.comment("Part des impôts d'une ville qui revient à la couronne quand on la confie à un gouverneur (modifiable ensuite).")
                    .defineInRange("partImpotsCouronne", 50, 0, 100);
            governorLevyShare = b.comment("Part des levées d'une ville qui revient à la couronne quand on la confie à un gouverneur (modifiable ensuite).")
                    .defineInRange("partLeveesCouronne", 50, 0, 100);
            b.pop();

            b.comment("Diplomatie, titres et vassalité").push("diplomatie");
            letterDelaySeconds = b.comment("Temps de vol d'un pigeon : délai avant qu'une réponse arrive au Colombier.")
                    .defineInRange("delaiLettresSecondes", 40, 0, 3600);
            aiLetterMinutes = b.comment("Tous les combien de minutes les royaumes IA peuvent écrire (propositions, exigences, présents).")
                    .defineInRange("lettresIAMinutes", 5, 1, 600);
            mailboxMax = b.comment("Lettres en attente au plus au Colombier.").defineInRange("courrierMax", 8, 1, 50);
            chanceryNeedsResearch = b.comment("La Chancery Table et le Sceau royal demandent la recherche « Chancellerie » de l'Université.")
                    .define("chancellerieDemandeRecherche", true);
            titleProvinces = b.comment("Provinces requises pour Comté, Duché, Royaume, Empire (colonies et provinces des vassaux).")
                    .defineList("provincesParTitre", java.util.List.of(2, 4, 8, 16), o -> o instanceof Integer i && i > 0);
            titleGold = b.comment("Or demandé pour Comté, Duché, Royaume, Empire.")
                    .defineList("orParTitre", java.util.List.of(200, 600, 1500, 4000), o -> o instanceof Integer i && i >= 0);
            titlePrestige = b.comment("Prestige demandé pour Comté, Duché, Royaume, Empire.")
                    .defineList("prestigeParTitre", java.util.List.of(100, 300, 800, 2000), o -> o instanceof Integer i && i >= 0);
            b.pop();

            b.comment("Guerre").push("guerre");
            truceDays = b.comment("Jours de trêve après une paix.").defineInRange("joursDeTreve", 30, 0, 365);
            sieges = b.comment("Sièges en jeu : approcher le siège d'un royaume ennemi fait paraître sa garnison et sa bannière à capturer.")
                    .define("sieges", true);
            siegeRadius = b.comment("Distance au siège ennemi à partir de laquelle le siège commence.").defineInRange("rayonSiege", 48, 16, 256);
            captureSeconds = b.comment("Secondes à tenir la bannière, sans garnison autour, pour prendre la province.")
                    .defineInRange("secondesCapture", 10, 3, 600);
            raids = b.comment("Les ennemis lancent des raids sur vos colonies (vos gardes MineColonies les repoussent).").define("raids", true);
            raidMinutes = b.comment("Minutes réelles entre deux raids d'une même guerre.").defineInRange("minutesEntreRaids", 20, 2, 600);
            antiGrief = b.comment("En territoire ennemi, en guerre, on ne casse que par explosifs ; tout est noté et restauré à la paix.")
                    .define("antiGrief", true);
            hostileRanks = b.comment("Guerre entre joueurs : chaque souverain passe « Hostile » dans les colonies de l'autre, rang rendu à la paix.")
                    .define("rangHostileEnGuerre", true);
            hywTroops = b.comment("Si Hundred Years War est installé : ses bandits forment les garnisons et les raids, ses soldats vos levées.")
                    .define("troupesHundredYearsWar", true);
            b.pop();

            b.comment("Les royaumes IA : leurs décisions, leurs guerres, leurs coalitions").push("ia");
            aiDecisions = b.comment("Les royaumes IA prennent leurs décisions (traités, guerres, coalitions). Désactivé : un monde figé.")
                    .define("decisions", true);
            aiDecisionChance = b.comment("Chance, en pour cent, qu'un royaume IA réfléchisse à sa politique un jour donné.")
                    .defineInRange("chanceDecisionParJour", 30, 0, 100);
            aiWars = b.comment("Les royaumes IA se font la guerre entre eux, et leurs frontières bougent.").define("guerresEntreIA", true);
            aiWarsOnPlayers = b.comment("Un royaume IA peut déclarer la guerre à un joueur (seulement s'il est en rivalité ouverte ou haï).")
                    .define("guerresContreJoueurs", true);
            aiGraceDays = b.comment("Jours de répit après la fondation d'un royaume : aucune IA ne l'attaque avant.")
                    .defineInRange("joursDeRepit", 10, 0, 1000);
            aiWarRange = b.comment("Distance maximale entre deux capitales pour qu'une IA envisage la guerre, en blocs.")
                    .defineInRange("porteeGuerre", 2500, 200, 100000);
            coalitions = b.comment("Les royaumes IA se liguent contre un joueur trop menaçant.").define("coalitions", true);
            coalitionThreshold = b.comment("Menace à partir de laquelle une coalition peut se former (une province conquise en vaut 20).")
                    .defineInRange("seuilCoalition", 60, 10, 200);
            worldNewsToasts = b.comment("Annoncer par notification les guerres et paix entre royaumes que vous connaissez.")
                    .define("notificationsNouvelles", true);
            b.pop();

            b.comment("Intrigues et événements : factions, complots, secrets, révoltes, événements à choix").push("intrigues");
            events = b.comment("Des événements à choix surgissent dans votre royaume (définis en JSON, extensibles par datapack).")
                    .define("evenements", true);
            eventChance = b.comment("Chance, en pour cent, qu'un événement surgisse un jour donné (s'il n'y en a pas déjà un en attente).")
                    .defineInRange("chanceEvenementParJour", 20, 0, 100);
            factions = b.comment("Les courtisans mécontents se liguent en factions, qui posent des ultimatums.").define("factions", true);
            plots = b.comment("Les courtisans qui vous haïssent complotent ; ils ont aussi des secrets à débusquer.").define("complots", true);
            revolts = b.comment("Les colonies malheureuses et trop taxées peuvent se révolter.").define("revoltes", true);
            b.pop();

            b.comment("Vie de cour : tournois, croisades, mariages").push("cour");
            tournamentNeedsResearch = b.comment("La Lice de tournoi demande la recherche « Chevalerie » à l'Université de la capitale.")
                    .define("tournoiDemandeRecherche", true);
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
