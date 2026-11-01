# CrusadeCraft — décisions retenues

*Validées par Romain le 2 octobre 2026 (« applique tes recommandations par défaut »). Toute décision
nouvelle prise en cours de route est ajoutée en bas, datée, avec sa raison.*

## Les 7 questions de l'étape 0

| # | Question | Décision |
|---|---|---|
| 1 | JDK 17 | JDK 17 **portable** (Eclipse Temurin) dézippé dans `E:\java\`, référencé **uniquement** par le projet (`org.gradle.java.home` dans `gradle.properties`). Ni installeur, ni PATH, ni réglage global |
| 2 | Version de MineColonies | On compile et on teste contre **la version de ton instance** : MineColonies 1.20.1-1.1.1214 (CurseForge `7962889`), Structurize 1.0.811 (`7966856`), BlockUI 1.0.194 (`7041657`), Domum Ornamentum 1.0.296 (`7585567`). Un shim absorbe les écarts d'API, comme dans War 'N Taxes |
| 3 | Une seule colonie par joueur | Le mod fonctionne avec **une seule colonie** : voir ci-dessous |
| 4 | Royaumes IA | **Modèle hybride** : royaumes abstraits, sièges ancrés dans le monde, rien de permanent posé par le mod (voir ci-dessous) |
| 5 | Compétences → stats | **Variante « paires MineColonies »**, réglable dans la config |
| 6 | Messages des conseillers | **Courrier du mod** (Colombier royal et lettres-objets) pour tout, plus **PNJ qui viennent te parler** avec un dialogue à choix. La bulle native de MineColonies n'est pas utilisée : elle écrirait dans les données des citoyens |
| 7 | War 'n Nobility | **Le wiki suffit**, pas de décompilation |

## Une seule colonie par joueur : la solution retenue

**CrusadeCraft ne crée jamais de colonie et ne change jamais de propriétaire.** Un royaume est un
enregistrement dans nos propres données, propres à chaque monde, qui désigne des colonies par
(dimension, identifiant). Ta colonie fonde la Baronnie. La colonie d'un autre joueur rejoint par un
contrat que **son propriétaire** signe. Une province IA rejoint par vassalisation ou par conquête.
Côté MineColonies, rien ne bouge : une conquête est une allégeance et une occupation fiscale dans nos
données, jamais un transfert de propriété. Il n'y a donc rien à corrompre, et retirer le mod rend les
colonies exactement comme avant.

## Royaumes IA : le modèle retenu

- Un royaume IA est **une donnée** : souverain, conseil, maison, personnalité, provinces,
  population, trésor, armées, relations. Ses personnages ont les mêmes stats et traits que les tiens.
- Chaque province IA a un **siège ancré dans le monde** :
  - de préférence un **village déjà généré**, cherché seulement dans les chunks existants, sans
    rien générer ;
  - à défaut, un point de terre ferme.
- **Rien de permanent n'est posé.** Ce qui apparaît (héraut, émissaire, armée, garnison, camp de
  siège) est temporaire et disparaît ensuite. Les blocs touchés en guerre sont restaurés par le
  registre des dégâts.
- **Aucune colonie MineColonies n'appartient à l'IA**, donc aucun faux propriétaire.
- Les royaumes IA se **découvrent** en approchant de leurs sièges (brouillard sur la carte).
- Les décisions de l'IA tournent dans une tâche espacée et configurable, jamais à chaque tick.

## Correspondance compétences → stats (valeurs par défaut)

| Stat | Formule | Remarque |
|---|---|---|
| Diplomatie | (Adaptabilité + Créativité) ÷ 4 | paire complémentaire |
| Martial | (Force + Athlétisme + ½ Endurance) ÷ 5 | paire complémentaire, plus l'endurance |
| Intendance | (Intelligence + Connaissance) ÷ 4 | Intelligence n'a pas de paire |
| Intrigue | (Dextérité + Agilité) ÷ 4 | paire complémentaire |
| Érudition | (Mana + Concentration + ½ Connaissance) ÷ 5 | paire complémentaire, plus la connaissance |

Les compétences vont de 1 à 99 : un citoyen débutant obtient 1 à 5, un expert 25 à 30.

## Principes d'architecture

- **Données par monde** (`SavedData` dans `saves/<monde>/data/`), versionnées, avec migrations et
  **écriture atomique** (fichier temporaire puis renommage). Jamais dans `config/`.
- **MineColonies en lecture**, et seulement après `ColonyManagerLoadedModEvent`. Les seules
  écritures prévues passent par l'API officielle et restent natives :
  - XP de compétences des dirigeants (réglable) ;
  - modificateurs de bonheur à expiration (à valider) ;
  - rang « Hostile » d'un ennemi pendant une guerre (réversible).
- **Clé d'un personnage** : (dimension, colonie, citoyen), avec réconciliation au chargement.
- **Argent** : le trésor est un **solde** tenu par un registre atomique (paires débit/crédit,
  remboursement en cas d'échec). Le Coffre du Trésor convertit l'or physique ↔ solde (pépite = 1,
  lingot = 9, bloc = 81, réglable).
- **Titres au-dessus des têtes, traits et opinions** : calculés et affichés par nous, sans jamais
  renommer un citoyen dans MineColonies.
- **Tout se joue en jeu** (blocs, objets, GUI, PNJ). `/crusade` sert seulement au debug et à
  l'admin (permission 2).
- **Mods optionnels** : chaque compat (HYW, Xaero, JourneyMap, FTB Teams, SDM, Patchouli) vit dans
  `compat/` et ne se charge que si le mod est présent. Aucun import ailleurs : un garde de build le
  vérifie.
- **War 'N Taxes ou War 'n Nobility présent** : avertissement, et les systèmes en doublon sont
  coupés.
- **Code repris de War 'N Taxes** (MIT) : la notice de licence est conservée dans `CREDITS.md`.

## Choix techniques

- modid `crusadecraft`, paquet Java `fr.crusadecraft`.
- ForgeGradle 6, Gradle 8.8, Parchment `2023.09.03-1.20.1`, Java 17, Forge 47.4.10 (ta version).
- Touches par défaut : **K** pour le menu royal, **,** (virgule) pour la carte. Xaero utilise déjà
  M, d'où ce choix. Les deux sont réglables.
- Antique Atlas écarté. JourneyMap optionnel. Xaero's World Map prioritaire, puisqu'il est installé.
- Git : `main` porte les documents ; chaque phase a sa branche (`phase/p1-fondation`,
  `phase/p2-personnages`…), chacune partant de la précédente. Pas de push ni de PR sans demande.

## Journal des décisions prises en cours de route

**P1 (2 octobre 2026)**

- **Trois niveaux de tests.**
  - Tests JUnit pour la logique pure.
  - **GameTests** (`./gradlew runGameTestServer`) : ils créent de vraies colonies MineColonies, avec
    Hôtel de ville, dans le monde jetable du serveur de test, et vérifient la fondation de bout en
    bout. Les joueurs y sont des `FakePlayer` de Forge, car celui de Minecraft 1.20.1 n'a pas de
    canal réseau.
  - **Banc d'essai visuel** (`./gradlew runUiShots`) : le client capture chaque écran avec des
    données d'exemple, dans `run/screenshots/`, puis se ferme.
- **`runServer` n'est pas utilisé**, car il exigerait d'accepter l'EULA dans `run-server/eula.txt`
  à ta place. `runClient` (serveur intégré) et le serveur de GameTests suffisent.
- **Événements MineColonies traités au tick suivant** (`server.tell`), pas par `server.execute`.
  Depuis le fil serveur, `execute` s'exécute aussitôt. Or MineColonies publie par exemple la
  suppression d'une colonie *avant* de la retirer. La suppression de la capitale est en plus traitée
  explicitement.
- **Les paquets ne partent jamais vers un `FakePlayer`** (machines, tests).
- Le Trône ne se pose que dans l'Hôtel de ville d'une colonie que le joueur gère. Les opérateurs en
  mode créatif peuvent le poser partout, en décor, mais il ne fonde toujours qu'au bon endroit.

**P2 (2 octobre 2026)**

- **Rien n'est copié depuis MineColonies.** Le nom, les compétences, le métier et le bonheur d'un
  citoyen sont relus à chaque affichage. Seul ce qui n'existe que chez nous est enregistré : traits,
  opinion datée, stress, captivité, souvenirs.
- **Traits tirés de façon déterministe** à partir de la graine du monde, de la dimension, de la
  colonie et du citoyen. Les compétences orientent le tirage : un citoyen très fort est plus souvent
  brave. Même si nos données étaient perdues, un citoyen retrouverait sa personnalité.
- **Prison et exil sont des états dans nos données.** Le citoyen continue sa vie dans MineColonies.
  Le renvoyer de la colonie serait une écriture irréversible chez MineColonies, ce qui est exclu.
- **Récompense** : un objet pris dans l'inventaire du souverain (un lingot d'or par défaut, réglable).
  Elle puisera dans le trésor quand celui-ci existera (P4).
- **Titre au-dessus des têtes** : le client remplace le texte de l'étiquette par l'événement
  `RenderNameTagEvent`. Le nom MineColonies n'est jamais modifié.
- **Interception du clic** : avec le Sceptre, ou sur un dignitaire sans Maj, CrusadeCraft prend le clic
  des deux côtés, client et serveur. Avec **Maj**, MineColonies garde la main. Le bouton « Fenêtre
  MineColonies » du dialogue laisse passer le clic suivant.
- **Mort** : `CitizenDiedModEvent`. `CitizenRemovedModEvent` n'est pas utilisé, car il se déclenche
  aussi quand l'entité est simplement déchargée. Un citoyen parti est détecté par la vérification
  périodique (toutes les 30 s).
- Forge 47.4 : le constructeur du mod reçoit le contexte de chargement (`get()` est déprécié).

**P3 (3 octobre 2026)**

- **Les citoyens se déplacent par leur propre IA MineColonies.** On ajoute un état « service » à
  leur automate, déclenché comme un événement, et seulement depuis les états de repos ou de travail.
  Le sommeil, la maladie, la fuite et les raids restent prioritaires. À la fin de l'ordre, le citoyen
  repasse au repos et MineColonies reprend la main. On ne remplace jamais son IA.
- **Les ordres ne sont pas enregistrés.** Après un redémarrage, chacun reprend sa vie normale, et les
  ordres sont recalculés au prochain créneau (réunion à 9 h, heures de bureau à 13 h par défaut).
- **Au moindre souci, l'état « service » se coupe** pour la session (une ligne dans le journal), et
  le mod continue sans déplacements.
- **Les sièges sont ceux de MineColonies** : un conseiller s'assoit comme un citoyen sur une chaise.
- **Effets écrits chez MineColonies** : uniquement par l'API officielle. Il s'agit d'un modificateur
  de bonheur « Couronne » et d'XP de compétences pour les gardes. MineColonies ne recharge pas un
  modificateur qu'il ne connaît pas : retirer CrusadeCraft ne laisse donc pas de trace. L'or et la
  piété restent dans nos données.
- **Réunion tenue** dès qu'un conseiller au moins s'est assis à la table : les tâches rapportent
  25 % de plus jusqu'au lendemain.
- **Requêtes portées en personne** : le citoyen marche jusqu'au souverain s'il est à moins de
  48 blocs. Sinon, une notification arrive et l'on répond depuis la Table du Conseil.
- **Le jour 0 existe** : « jamais traité » vaut `Long.MIN_VALUE`, pas -1, qui serait la veille du
  premier jour d'un monde neuf.
- **Maj + clic sur un citoyen ouvre son inventaire MineColonies** : c'est le comportement de
  MineColonies. Le bouton « Fenêtre MineColonies » du dialogue ouvre sa fenêtre principale.
- **GameTests** : le monde de test est effacé avant chaque exécution. Une colonie laissée par un test
  échoué est défaite avant d'en créer une au même endroit. Les vérifications différées sont toutes
  inscrites d'avance : Minecraft range ces tâches par objet, et une tâche qui se réinscrit est
  aussitôt effacée.

**P4 (3 octobre 2026)**

- **L'or est une pièce, la pépite.** Au Coffre du Trésor : une pépite vaut 1, un lingot 9, un bloc 81
  (réglable). Tous les montants du mod (impôts, salaires, requêtes, récompenses) sont dans cette unité.
- **Impôts quotidiens, versés directement au trésor**, sans réclamation à la main : un barème par
  bâtiment repris de War 'N Taxes (base + par niveau), multiplié par le bonheur (de 0,5 à 1,5), la
  fiscalité, l'intendant et les recherches. Une colonie attaquée paie moitié. Les bâtiments militaires
  ne paient rien, ils coûtent un entretien.
- **Un bâtiment compte dès qu'il a un niveau**, comme MineColonies lui-même au chargement d'une
  sauvegarde (son indicateur « construit » n'est posé qu'à la fin d'un chantier).
- **Trois sortes de mouvements.** Recettes toujours acceptées. Dépenses choisies (requêtes,
  récompenses, retraits) refusées si l'or manque. Dépenses dues (salaires, entretien) payées jusqu'à
  la dette maximale (200 par défaut). Un retrait ne débite que ce qui entre dans l'inventaire.
- **La dette coûte** : 2 prestige par jour, et les salariés impayés s'en souviennent (« Salaire impayé »).
- **Lois** : fiscalité, autorité de la couronne, code chevaleresque, justice. Le conseil vote, de
  façon déterministe : la prévision affichée est le résultat. Sans conseil, on décide seul. Une loi
  rejetée s'impose contre 20 prestige par voix contre, et ces voix gardent rancune. Trois jours
  d'attente entre deux changements d'une même loi.
- **Recherches de gouvernance** : une branche « Gouvernance » ajoutée à l'Université par le datapack
  du mod (Administration, Trésorerie royale, Chancellerie, Chevalerie, Autorité royale). On lit celles de
  la capitale. **Retirer le mod ne gêne pas MineColonies** : à la lecture d'une colonie, il ignore une
  recherche inconnue (`LocalResearchTree.readFromNBT`) et n'en applique pas les effets.
- **Bonheur « Impôts »** : un second modificateur MineColonies, réappliqué comme celui de la couronne,
  donc sans trace si l'on retire le mod.
- **War 'N Taxes** : s'il est installé (et que l'option de cohabitation est active), nos impôts se
  coupent ; le trésor, les salaires et les lois restent.

**P5 (3 octobre 2026)**

- **Les royaumes IA naissent une seule fois**, juste après la fondation du premier royaume, autour de sa
  capitale : 6 royaumes entre 600 et 2 500 blocs (réglable). Un royaume IA tient 1 à 5 provinces selon
  son rang. Une commande d'administration les régénère pour le débogage.
- **Villages repérés sans générer ni charger un seul chunk.** L'algorithme de placement de Minecraft
  donne les emplacements possibles, la biome trie, et la vérification de structure lit les chunks déjà
  enregistrés (village confirmé) ou prévoit le village d'une région jamais visitée. Les villages
  confirmés passent en premier ; à défaut, un point de terre ferme (ni océan ni rivière). Mesuré :
  20 à 90 villages repérés en moins d'une seconde.
- **Les sièges IA s'écartent des colonies MineColonies** existantes (au moins deux rayons de territoire).
- **Le héraut** est un villageois vanilla temporaire : immobile, invulnérable, marqué, retiré au bout de
  45 secondes et au chargement suivant s'il reste d'un arrêt brutal. Il est inscrit avant d'apparaître,
  sinon le garde-fou contre les hérauts égarés l'empêche d'entrer (bug trouvé par un GameTest). Un clic
  droit ouvre la fiche de son royaume au lieu du commerce.
- **Le brouillard n'est pas un voile** : le serveur n'envoie que les royaumes découverts par ce joueur.
- **Royaumes de jure** : un par secteur autour du centre (un pour trois États environ). Les colonies des
  joueurs relèvent du plus proche. La vue de jure de la carte s'en sert ; les revendications aussi, plus tard.
- **La carte est calculée par le client** dans une texture (le siège le plus proche dans son rayon,
  frontières, parchemin, brouillard). La Table de guerre découpe une carte de 192 pixels centrée sur la
  capitale en neuf parts, une par cadre ; un cadre isolé reste un simple parchemin.
- **Xaero's World Map** : pas d'API publique propre pour colorer des territoires (le mod est fermé).
  Pas d'intégration ; la carte du mod et la Table de guerre en tiennent lieu.
- **Banc d'essai en monde réel** (`./gradlew runWorldShots`) : monde plat jetable dans
  `run-worldshots/saves`, Table de guerre, royaumes IA, héraut, puis captures et fermeture.

**P6 (3 octobre 2026)**

- **Une relation par paire de royaumes** (joueurs et IA) : traités datés, hostilité, vassalité et
  contrat, tribut, et l'opinion que chacun porte à l'autre (motifs datés, comme pour les personnages).
- **L'opinion d'un souverain IA se calcule** : tempérament, traits, votre prestige et votre rang, rivalité
  pour un même royaume de jure, renommée de votre chancelier, traités, cadeaux, insultes, contrat de
  vassalité. Le Colombier affiche le détail.
- **Réponses déterministes** : le Colombier montre, pour chaque proposition, la réponse qui viendra et ses
  raisons. La réponse arrive par pigeon (40 s par défaut) au Colombier, où on la prend en objet « Lettre ».
- **Le Sceau royal scelle** traités, hommages, titres et contrats. Il se grave aux armes du premier
  souverain qui s'en sert. Comme la Chancery Table, il demande la recherche « Chancellerie » (réglable).
- **Seigneuries indépendantes** : 4 baronnies d'une province, plus proches que les grands royaumes. Un baron
  peut s'en faire prêter hommage ; un royaume IA plus titré refuse (il faut d'abord monter en rang). C'est
  la première façon de grandir en solo : une seigneurie vassale + la capitale = 2 provinces = Comté.
- **Les mondes déjà générés** (P5) reçoivent leurs seigneuries au chargement, sans rien déplacer.
- **Titres** : Comté 2 provinces / 200 or / 100 prestige, puis Duché 4 / 600 / 300, Royaume 8 / 1500 / 800,
  Empire 16 / 4000 / 2000 (réglables). Les provinces des vassaux comptent.
- **Vassaux IA** : ils versent chaque jour leur part (impôt du contrat) de leur revenu simulé. Un contrat
  dur fait baisser leur opinion. Entre joueurs, la vassalité se propose et se scelle par lettre.
- **Revendications** : le chancelier les fabrique jour après jour sur une province choisie à la
  Chancery Table ; un parchemin arrive au Colombier. Elles serviront de motif de guerre (P7).
- **Statut MineColonies** : lu et affiché pour les royaumes des autres joueurs. Le refléter (rang
  « Hostile » en guerre) viendra avec la P7, derrière une option.
- **Personnages d'une colonie supprimée oubliés** : MineColonies peut redonner le numéro d'une colonie
  supprimée à une nouvelle ; sans cela, ses citoyens héritaient des souvenirs et du statut des anciens
  (bug trouvé par les GameTests).

**P7 (3 octobre 2026)**

- **Cinq motifs de guerre** : revendication (parchemin de la Chancery Table), droit de jure (royaume),
  conquête (duc au moins, 100 de prestige), soumission (royaume moins titré), humiliation (rivalité ou
  opinion à −20). La guerre sainte viendra avec les croisades. Chaque motif refusé dit pourquoi.
- **Ce qui interdit de déclarer** : guerre déjà en cours, suzerain ou vassal, vassal d'un autre, trêve
  (30 jours après chaque paix), alliance, non-agression. Les alliés (alliance totale de l'attaquant ;
  alliances et alliances défensives du défenseur) et les vassaux suivent leur camp.
- **Une armée par royaume** : six hommes par garde MineColonies et un pour trois citoyens. Levée, elle
  compte en entier et coûte une pièce par dizaine d'hommes et par jour ; au repos, un tiers seulement, mais
  elle se reconstitue (10 % par jour de paix). Les levées des vassaux s'ajoutent selon le contrat et leur humeur.
- **La campagne se résout chaque jour** : une escarmouche (forces, Martial du maréchal, hasard), des
  pertes, de la lassitude, un point par jour de tenue de la province visée, et l'occupation qui verse le
  revenu de la province à l'occupant. Les batailles comptent au plus 60 points, l'occupation 60, le but 20.
- **En jeu, deux combats** : le siège (approcher la capitale d'une province ennemie fait sortir sa garnison ;
  tenir sa bannière dix secondes, sans défenseur à moins de 24 blocs, prend la place) et le raid (un
  ennemi assez fort marche sur la colonie la plus proche de son souverain, s'il est là ; sinon le raid se
  résout sur le papier, gardes contre pillards). Vos hommes d'armes vous suivent aux sièges si l'armée est levée.
- **Hundred Years War, s'il est là** : ses bandits font les garnisons et les pillards, ses soldats vos
  hommes d'armes (rattachés au joueur par sa méthode publique de propriétaire, cherchée par nom). Sans lui,
  vindicateurs et pillards vanilla, et golems de fer. Rien n'est pris de son code.
- **Les golems d'un village ne frappent pas sa garnison** : ni cible ni coups entre eux (le premier essai
  en monde réel les a vus massacrer les neuf défenseurs avant l'arrivée du joueur).
- **La bannière se plante au sol** : le point le plus bas autour du centre du village, jamais un toit (le
  premier essai l'avait posée sur une maison, hors d'atteinte).
- **Toutes les troupes sont temporaires**, marquées, suivies et retirées à la fin ; celles qui restent d'un
  arrêt brutal sont refusées au chargement, comme les hérauts.
- **Anti-grief** : en territoire ennemi, on ne casse rien à la main ; ce qu'on y pose se reprend. Les
  explosions d'un théâtre de guerre sont notées bloc par bloc (coffres compris, vidés pour ne rien lâcher)
  dans un registre enregistré avec le monde, et tout est remis en place à la paix, 48 blocs par
  quart de seconde. La TNT elle-même reste au jeu normal.
- **La paix** : blanche, victoire exigée ou capitulation. L'IA répond d'après le score, sa lassitude et
  la durée ; une IA écrasée propose sa reddition, une IA victorieuse exige la vôtre, par lettre à sceller.
  La victoire donne la province visée (elle change de mains et verse son revenu, compte pour les titres),
  le vassal ou le quart du trésor ; l'attaquant vaincu paie 20 % en réparations.
- **Guerre entre joueurs** : chaque souverain devient « Hostile » dans les colonies de l'autre
  (option), rang rendu à la paix.
- **Table de guerre** : le cadre ouvre désormais l'écran de guerre (armée, guerres, déclaration,
  paix) ; la carte du monde s'ouvre de là. L'onglet Guerre du menu royal en donne le résumé.

**P8 (3 octobre 2026)**

- **Un jour, une réflexion** : chaque royaume IA libre (qui tient une terre et ne prête hommage à personne) a
  30 % de chances par jour de réfléchir à sa politique (réglable). Il cherche d'abord une guerre à sa mesure,
  sinon un traité avec un voisin. Tout se coupe dans la configuration, section « ia ».
- **L'envie de guerre dépend du tempérament** : un expansionniste attaque à 1,3 contre 1 (1,1 s'il hait sa
  cible), un opportuniste à 1,6, un fanatique à 1,3 s'il hait, sinon 2 ; un marchand ou un pacifique seulement
  s'il hait franchement. On compte les alliés qui suivraient de chaque côté, et la distance entre capitales.
- **On ne s'acharne pas** : une IA n'attaque pas un royaume déjà en guerre (la première simulation a vu
  quatre royaumes tomber le même jour sur une baronnie). Une province déjà passée à un autre ne se cède pas
  une seconde fois : le vainqueur reçoit de l'or à la place.
- **Les joueurs ne sont visés que s'ils l'ont cherché** : rivalité ouverte, ou opinion à −40 ou moins, et
  jamais pendant les 10 jours qui suivent la fondation. Une IA ne soumet pas un joueur : elle l'humilie, ou
  reprend une province qu'il a conquise. Une lettre de déclaration arrive au Colombier.
- **Les guerres entre IA se jouent seules** : escarmouches quotidiennes, places prises sur le papier (la
  province visée d'abord, et l'on reprend les siennes avant d'avancer), paix quand un camp est écrasé (±50) ou
  que la guerre dure trop (30 jours) ou lasse les deux camps. Une IA lasse propose la paix blanche au joueur.
- **Les frontières bougent** : une province conquise passe sous la bannière de son vainqueur sur la carte,
  lui verse son revenu et compte dans sa force. Les vassaux IA d'un suzerain IA lui paient l'impôt du contrat.
- **La menace** : chaque déclaration de guerre d'un joueur ajoute 5, chaque province conquise 20, chaque
  royaume soumis 15 ; elle retombe chaque jour, et d'un coup de 30 après une défaite. Elle pèse dans l'opinion
  de tous les souverains IA (jusqu'à −40), et la Table de guerre l'affiche.
- **Coalitions** : au-delà du seuil (60), les royaumes qui craignent le joueur et que rien ne lie à lui se
  liguent. Annoncée dès deux membres, la coalition défend chacun des siens ; si elle pèse 1,2 fois plus que le
  joueur et ses alliés, elle attaque, menée par son plus fort membre. Elle tient tant que dure la guerre et se
  défait quand la menace est retombée sous la moitié du seuil.
- **Nouvelles du monde** : guerres, paix, frontières, alliances et coalitions, gardées (40 au plus) et lisibles
  dans l'onglet Chroniques du menu royal ; un joueur n'apprend que ce qui touche des royaumes qu'il connaît. Les
  guerres et les paix sont aussi annoncées par notification (réglable). Les mariages viendront en P10.
- **Simulation de 60 jours** en GameTest, toutes les IA réfléchissant chaque jour : aucune guerre ne dure plus
  de 31 jours, aucun état incohérent.

**P9 (3 octobre 2026)**

- **Les événements sont des données** : un fichier JSON par événement (`data/<espace>/crusade_events/`),
  rechargé à chaque `/reload`. Conditions (or, prestige, guerre, poste au conseil, rang, âge du royaume,
  bonheur moyen, niveau d'une loi…), options avec leurs propres conditions, effets (or, prestige, piété,
  crainte, menace, armée, opinions du conseil, d'un poste ou du personnage en cause, cachot ou exil, trait,
  loi abaissée, révolte), option par défaut et délai. Un datapack peut en ajouter, en texte brut ou en clés
  de traduction. Vingt sont livrés : quatorze au hasard, six appelés par les intrigues.
- **Un événement attend la décision** quelques jours (bouton « Événements » du menu royal, notification) ;
  passé le délai, l'option par défaut s'applique. Un test vérifie que l'option par défaut de chaque
  événement livré ne coûte jamais d'or qu'on pourrait ne pas avoir (il a trouvé les « Pèlerins »).
- **Factions** : baisse des impôts (tous les mécontents, si l'impôt est au moins normal), indépendance (les
  maires d'une province malheureuse), renvoi d'un conseiller honni (corrompu, cruel, arbitraire, fourbe,
  arrogant). Un loyal ne se ligue jamais, un ambitieux dès qu'il n'aime plus. Puissance = part de la cour
  (un conseiller pèse 3, un maire 2), moins la moitié de la crainte et le Martial du maréchal qui maintient
  l'ordre. À 60 %, ultimatum : céder, acheter la paix, ou refuser (grève de l'impôt, province en révolte).
- **Complots** (détournement, sabotage, diffamation) : nés de la haine (5 % par jour), ils avancent selon
  l'Intrigue de leur auteur ; le Maître-espion les évente selon la sienne (trois fois plus s'il
  « débusque les complots », tâche désormais ouverte, comme « Maintenir l'ordre » du maréchal). Éventé :
  cachot, exil ou pardon ; abouti : le coup porte, et l'on apprend ensuite qui l'a porté.
- **Secrets** (dettes de jeu, liaison, hérésie, corruption) : découverts par le Maître-espion, ils servent au
  chantage (deux mois sans faction ni complot) ou se révèlent (prestige, piété pour une hérésie, rancune
  tenace). Sans maître-espion, on ne sait rien, et l'écran le dit.
- **Révoltes paysannes** : une colonie malheureuse (moins de 5) sous un impôt au moins normal peut se
  soulever ; une province en révolte ne paie pas l'impôt.
- **La chronique du règne** s'écrit d'elle-même : fondation, titres, nominations, lois, hommages, guerres,
  conquêtes, coalitions, ultimatums, complots, secrets, décisions d'événements. Onglet Chroniques (Règne /
  Monde) et « Chroniques du royaume », un livre qui s'ouvre comme un livre écrit (recette : livre, plume,
  poche d'encre, papier).

**P10 (3 octobre 2026)**

- **La foi des royaumes IA** : trois sur quatre fidèles, les autres païens ou hérétiques, tirée de l'identifiant
  du royaume (les mondes déjà générés la reçoivent sans rien régénérer). La fiche d'un royaume l'affiche.
- **Croisades** : la « Guerre sainte » est un motif contre un royaume païen ou hérétique (comte au moins, 100 de
  piété). L'appel rallie aussitôt les royaumes IA fidèles (les fanatiques toujours, les autres s'ils estiment le
  meneur à +20) et invite les autres souverains par lettre (« Prendre la croix »). Victoire : la province visée,
  et piété +50, prestige +30 pour chaque croisé. Les IA fanatiques et pieuses appellent elles-mêmes à la
  croisade. Celui qu'un traité lie à la cible ne se croise pas.
- **Tournois** : la Lice (bloc, à poser dans une colonie du royaume) organise un tournoi tous les 15 jours,
  derrière la recherche « Chevalerie » (réglable). Les chevaliers sont vos conseillers et maires (les cinq
  meilleurs en Martial), face aux champions (maréchaux) des royaumes voisins en paix avec vous. Tableau à
  élimination directe : la valeur d'un jouteur vient de son Martial, de ses traits (brave, vétéran, champion,
  blessé, lâche) et de la chance. L'hôte gagne du prestige ; le vainqueur devient champion ; on revient
  vétéran, ou blessé. Avec Epic Knights, un champion de la cour rapporte une lance de ce mod (cherchée par son
  identifiant, sans dépendre de son code).
- **La maison du souverain** : on y fait entrer des conseillers ou des maires (25 de prestige). Leurs conjoints
  et leurs enfants y entrent d'eux-mêmes, d'après la vraie généalogie MineColonies (partenaire, enfants de la
  colonie). On désigne un héritier : prestige +10 la première fois, et les factions pèsent 10 points de moins.
- **Mariages dynastiques** : un membre adulte et libre (sans conjoint dans sa colonie) peut épouser un membre de
  la famille d'un souverain IA de la même foi. La réponse se lit d'avance ; acceptée, elle arrive par lettre à
  sceller. Les maisons s'unissent (+20 d'opinion durable) et signent une alliance défensive. Les IA bien
  disposées proposent elles-mêmes des mariages. Le Colombier reste inchangé : les mariages se demandent depuis
  l'écran de la Maison (bouton dans l'onglet Royaume du menu royal).

**P11 (3 octobre 2026)**

- **Bouton « Mon royaume » sur la fenêtre de l'Hôtel de ville** de MineColonies, sans mixin : la fenêtre est un
  écran BlockUI qu'on ne modifie pas ; on dessine le bouton par-dessus et on intercepte son clic. Deux pièges,
  trouvés en monde réel : BlockUI garde le test de profondeur actif (il faut dessiner devant, sans profondeur),
  et il donne à son écran sa propre largeur (524 au lieu de 427) — le bouton se cale donc sur la largeur de la
  fenêtre du jeu. Si une version de MineColonies change sa fenêtre, le bouton disparaît simplement.
- **Codex Patchouli** « Codex de CrusadeCraft » : cinq chapitres (royaume, monde, guerre, intrigues, cour), en
  français et en anglais. Il n'existe que si Patchouli est installé (recette conditionnelle : livre, pépite
  d'or, plume) ; CrusadeCraft n'en dépend pas.
- **Annexer ses propres colonies** : la Borne de frontière, posée dans une autre colonie dont on est
  propriétaire, en fait une province du royaume (rien ne change côté MineColonies). C'était le seul moyen qui
  manquait pour grandir avec plusieurs colonies.
- **Non faits, et pourquoi** : les armées IA pilotées par les commandes de Hundred Years War (le mod n'a pas
  d'API publique pour commander ses unités ; ses troupes servent déjà aux sièges et aux raids) ; JourneyMap
  (optionnel, absent de l'instance, qui utilise les cartes de Xaero, elles aussi sans API pour colorer des
  territoires).
- **Performances** : tout ce qui est lourd tourne une fois par jour du monde (IA, intrigues, campagne) ; les
  tâches fréquentes (sièges chaque seconde, restauration des blocs) ne parcourent que les guerres en cours, et
  l'anti-grief ne calcule rien en temps de paix. La simulation de 60 jours d'IA reste instantanée en GameTest.
- **Traductions** : 1 470 clés, toutes présentes en français et en anglais.

**P12 (3 octobre 2026) — l'éditeur de la carte**

Demandé par Romain : « faire mes propres frontières, créer des royaumes, mes villes ou celles adverses ».

- **Où** : un bouton « Modifier » sur la carte du royaume, pas un nouvel objet. Tout se fait là où l'on regarde
  déjà le monde. Quatre outils : Choisir (renommer, déplacer, donner, supprimer une ville ; modifier ou
  supprimer un royaume), + Royaume, + Ville, Pinceau. Un formulaire court pour les noms.
- **Qui** : réglage `monde.editeurCarte`. Par défaut l'hôte d'une partie solo (ou ouverte en LAN) et les
  opérateurs : en solo, rien à régler. `TOUS` ou `PERSONNE` au choix.
- **Les royaumes faits main sont des royaumes IA comme les autres** (souverain, conseil, trésor, diplomatie,
  guerre, foi), marqués `handMade`. Une régénération du monde (`/crusade monde generer`, ou la génération à
  la fondation) les garde, s'en écarte et ne reprend ni leurs couleurs ni leurs numéros de province.
  « Monde vierge » efface tous les royaumes créés par le jeu et empêche toute régénération automatique.
- **« Mes villes »** : une colonie MineColonies ne se crée pas depuis une carte (CrusadeCraft ne crée jamais de
  colonie). Une ville « à vous » est donc une province IA que vous tenez, comme une conquête : elle compte pour
  vos titres et vous rapporte son revenu. Elle dépend du royaume IA dont on a peint les terres à cet endroit,
  sinon du plus proche, qui pourra la revendiquer.
- **Frontières peintes** : par cases de 16 blocs (deux points à l'échelle habituelle), par dimension. Une case
  peinte l'emporte sur les cercles automatiques autour des sièges : sur la carte, pour l'annonce « Vous entrez
  sur les terres de… », et pour la guerre (anti-grief et réparation des dégâts). « Terre libre » efface un
  territoire ; la gomme rend les cercles. Les joueurs ne reçoivent que les cases des royaumes qu'ils
  connaissent : le brouillard reste vrai. Dans l'éditeur, en revanche, ni brouillard ni secret.
- **Supprimer un royaume proprement** : ses guerres finissent en paix blanche, ses relations, ses coalitions,
  ses lettres en attente et ses frontières disparaissent ; ce qu'il avait conquis revient à ses
  propriétaires, ce que d'autres lui avaient pris reste à ceux qui le tiennent (rattaché au royaume voisin).
  Une capitale ne se supprime pas seule.
- **Sauvegarde** : version 11 (frontières et marque `handMade`). Une ancienne sauvegarde se lit sans rien
  perdre : aucune frontière peinte, aucun royaume fait main.
- **Banc d'essai** : les tests en jeu laissaient une colonie à moitié créée quand MineColonies échouait à la
  créer, et tous les tests suivants tombaient en cascade (vu en ajoutant ceux de P12, qui décalent les
  emplacements). La colonie ratée est désormais supprimée aussitôt, et l'erreur journalisée en entier.
- **Corrigé au passage** : dans le menu royal, le bouton « Maison » recouvrait les ressources du souverain sur
  les petits écrans ; il est passé dans l'en-tête, et la devise s'arrête avant les boutons.

**P13 (3 octobre 2026) — le registre des lignées et Minecraft Comes Alive**

Demandé par Romain : créer et générer personnages, familles et lignées dynastiques, populations, dirigeants,
générations, relations et communautés, en s'appuyant sur Comes Alive (MCA Reborn 7.6.16, installé dans son instance).

- **Un registre, pas un nouvel objet** : maisons, personnes et communautés vivent dans la sauvegarde de CrusadeCraft
  (version 12). On l'ouvre depuis l'écran « Maison » du menu royal, l'éditeur de la carte (« Registre des lignées »,
  et « Lignée du royaume » sur un royaume choisi) ou `/crusade lignees`. Le modifier demande les droits de
  l'éditeur ; le consulter est ouvert à tous (sans les royaumes IA pas encore découverts).
- **Chaque royaume IA a sa cour**, créée la première fois qu'on ouvre le registre : son souverain tel que le monde le
  connaît (nom, sexe, âge), ses parents, son conjoint, ses enfants et son héritier, ses conseillers. Le nom de la
  maison est celui que le royaume portait déjà. Un royaume effacé dans l'éditeur laisse sa maison, qui ne règne plus.
- **Le générateur n'écrit que des faits** (noms, âges, liens, rôles), tirés d'une graine : maisons de 1 à 5
  générations, conjoints nobles « de Valcourt », roturiers « Lefèvre », villages et clans en foyers, ordres,
  compagnies et monastères en membres seuls. Un parent a toujours au moins 18 ans de plus que son enfant ; les
  ancêtres trop vieux sont morts. Les âges ne bougent pas seuls : les générations s'ajoutent (« Agrandir »).
- **Comes Alive** : compilé contre le jar exact de l'instance (copié dans `libs/local`, ignoré par git, jamais
  redistribué), utilisé seulement dans `compat/mca` (garde d'architecture étendue). Sous Forge, ses classes sont dans
  `forge.net.mca`. « Faire venir » crée un villageois Comes Alive qui porte le même identifiant que la personne, son
  nom complet, son sexe, son âge (bébé à adulte) et un métier selon son rôle (garde, archer, clerc…). L'arbre
  généalogique de Comes Alive accepte des membres absents du monde : on y inscrit toute la lignée, parents, conjoints
  et défunts compris. « Inscrire » fait l'inverse : les villageois Comes Alive alentour entrent au registre, avec
  leurs liens. La mort d'un villageois est inscrite au registre. Sans Comes Alive, le registre fonctionne seul.
- **Bug corrigé, présent chez Romain** : Comes Alive remplace tout villageois de la classe exacte `Villager` ; le
  héraut disparaissait, remplacé par un villageois ordinaire qui restait pour toujours. Le héraut est désormais une
  sous-classe, que Comes Alive laisse tranquille.
- **Banc d'essai** : le fichier de réglages de CrusadeCraft n'est plus relu à chaud sur le serveur de test (les
  réglages changés par les tests pouvaient revenir un instant en arrière, d'où des échecs au hasard). Comes Alive et
  Architectury sont chargés en développement (sauf `-Pwith_mca=false`) ; son écran « Qui es-tu ? » est coupé dans le
  monde jetable des captures.

**P14 (4 octobre 2026) — le bâton de bornage, les constructions, la carte papier**

Demandé par Romain : tracer ses frontières sur le terrain, poser avant-postes et constructions (y compris les villes
des IA), voir les terres sur les cartes papier vanilla ; plus tard le commerce. Validé tel que proposé.

- **Un seul territoire** (`Territory`) : les cases peintes de l'éditeur, les cercles des constructions achevées et
  ceux des postes de garde MineColonies (tour de garde, caserne, tour de caserne) répondent à la même question,
  « à qui est ce bloc ? ». Une case peinte l'emporte ; sinon le cercle le plus proche. La guerre, les messages
  d'entrée, la carte et la carte papier lisent tous cette réponse.
- **Le bâton de bornage** : polygone, rectangle, en marchant, construire. Aperçu en poussière colorée, annulation
  (Ctrl+Z, dix tracés, l'or rendu), plafond de cases par tracé. Dans l'éditeur, tout est permis ; en survie
  (`territoire.bornageSurvie`), on ne trace que pour son royaume, sur des cases libres, contiguës à ses terres et
  à moins de 256 blocs d'une de ses places, à 5 pièces d'or la case (réglable).
- **Les constructions sont des plans écrits en code**, pas des fichiers de structure : jalon, tour de guet, camp,
  avant-poste, fort, donjon, village. Tournées selon le regard, aux couleurs du royaume (bannières, laine, lits).
  Instantanées dans l'éditeur (1 200 blocs par tick au plus), en chantier ailleurs (6 blocs toutes les 2 s). Un
  chantier ne tient aucune terre ; achevée, la construction tient un cercle et peut être prise en siège (sa
  bannière, comme une capitale). Le donjon déplace le siège de la province la plus proche ; le village crée une
  ville et la peuple (avec Comes Alive). Supprimer une construction la retire du registre : ses pierres restent.
- **Doublon de nom évité** : la construction la plus simple s'appelait « Borne », comme la Borne de frontière (le
  bloc qui annexe une colonie). Elle s'appelle désormais « Jalon ».
- **La carte papier** : le paquet vanilla d'une carte n'en donne pas le centre ; le serveur l'envoie, une fois par
  carte et par session, pour les cartes en main et dans les cadres proches. Le client peint frontières et noms
  par-dessus (mixin sur `MapRenderer.render`, côté client seulement). Les icônes vanilla sont une liste fermée :
  elles n'ont pas été touchées.
- **Sauvegarde** : les constructions s'ajoutent sans changer de version (absentes, la liste est vide). Une
  sauvegarde 1.2 se lit sans rien perdre.
- **Banc d'essai** : MineColonies vérifie qu'un chunk est chargé en lisant une table que le serveur ne met à jour
  qu'en fin de tick ; une colonie créée dans un chunk tout juste chargé n'avait pas d'Hôtel de ville. Tous les tests
  à colonie attendent désormais deux ticks avant de commencer.
- **Corrigé au passage** : une règle `.gitignore` (`build/`) ignorait aussi `src/.../core/build/` ; elle ne vise
  plus que le dossier de construction à la racine.

**P15 (4 octobre 2026) — les lieux, et des sauvegardes à l'abri**

Première phase de CrusadeCraft 2.0 (plan : `docs/etape-0-v2-rapport.md`, validé par Romain le 4 octobre).

- **Le lieu indexe, il ne recopie pas.** Chaque royaume (IA ou joueur), province, colonie de joueur et construction a
  son lieu, créé d'office ; son nom, sa position et son tenancier se lisent dans la chose elle-même. Le lieu ajoute
  sa place dans la hiérarchie et sa **fiche**, un texte que seul le joueur écrit : le mod n'y met rien.
- **La hiérarchie est celle du monde** : un royaume relève de son suzerain, une ville du royaume qui la tient, une
  construction de sa ville (sinon de son royaume). L'éditeur peut ranger un lieu ailleurs, jamais en boucle, et
  poser des titres qui en regroupent d'autres (« Empire de… »). Les royaumes de jure restent ce qu'ils étaient : la
  vue de jure de la carte.
- **Les lieux posés à la main** : lieu saint, ruine, région (avec un rayon) — au bâton (mode « Lieu »), sur ses terres
  en survie ; et les titres, dans l'éditeur. Ils s'affichent sur la carte du mod. Un lieu saint, une ruine ou une
  région revient au royaume qui tient la terre où on le pose, et se range sous lui ; un titre, non (il regroupe, et
  son tenancier peut être rangé sous lui sans faire de boucle).
- **Une chose disparue garde sa fiche** : sans texte, son lieu s'en va ; avec, il reste, « disparu du monde », et
  c'est au joueur de le supprimer. Les numéros de province pouvant resservir, le lieu d'une ville effacée est
  détaché **au moment** de l'effacement, pour que sa fiche n'aille pas à la ville suivante.
- **Changé par rapport au plan** : les cases de territoire ne pointent pas vers un comté. La chaîne « où suis-je »
  se déduit sans cela (la place la plus proche, la région, puis le royaume qui tient la terre et ses suzerains), et
  réécrire toutes les cases aurait été une migration risquée sans bénéfice visible. Si les protections par lieu de
  P16 l'exigent, ce sera fait là, avec sa propre sauvegarde.
- **« Près de » sans faute** : « près d'Abbaye de Saint-Aubin » était faux, et l'article d'un nom choisi par le joueur
  ne se devine pas. La position dit donc « Abbaye de Saint-Aubin, à 12 blocs ».
- **Sauvegardes** : version 13. Avant toute mise à niveau (ou devant une sauvegarde plus récente que le mod), une
  copie datée du fichier va dans `data/crusadecraft_backups/` (`crusadecraft-v12-2026-10-04_12-30-00.dat`), jamais
  réécrite ; les dix plus récentes sont gardées. Le `.bak` existant ne suffisait pas : il était remplacé à chaque
  sauvegarde du monde.
- **Zéro perte, prouvé** : les jars 1.0.0, 1.1.0 et 1.2.0 ont été reconstruits depuis leurs commits, chacun a joué le
  banc d'essai en monde réel, et leurs fichiers de données sont gardés dans `src/test/resources/fixtures` avec celui de
  la 1.3.0. Un test les charge, les met à niveau, les réécrit et les relit : aucune section ni aucun royaume ne
  manque, et écrire puis relire ne change rien. Ce test a trouvé que les cases peintes s'écrivaient dans un ordre
  changeant ; elles s'écrivent désormais triées, pour un fichier stable.

**P16 (9 octobre 2026) — le territoire au chunk, façon Factions**

Demandé par Romain (5 octobre) : « que ça fonctionne par chunk directement », comme les claims de Factions sur Dynmap.

- **Un chunk, un maître.** Les cases peintes étaient déjà des chunks ; les terres automatiques (cercles autour des
  villes, des forts, des postes de garde) se décident désormais au centre du chunk : un chunk revient au siège le plus
  proche dont le cercle contient son centre. Deux blocs d'un même chunk ont toujours le même maître, et la carte du
  mod, la carte papier, Xaero et Dynmap dessinent les mêmes carrés. C'est un calcul : aucune donnée réécrite.
- **Pas de cache entre deux questions.** Un cache d'une seconde aurait pu répondre faux juste après un changement de
  terres (et les tests l'ont montré) ; réunir les cercles coûte quelques microsecondes. Dans un même instantané, seul
  le résultat des cercles est mémorisé — la peinture, elle, est relue.
- **Commandes pour tous** (les commandes d'administration restent aux opérateurs) : `/crusade claim [rayon]`,
  `/crusade unclaim [rayon]`, `/crusade map` (25 × 7 chunks en couleurs dans le chat), `/crusade info` (à qui est ce
  chunk, revendiqué ou autour d'une place, vos terres et votre plafond). Les règles sont celles du bâton de bornage
  (contigu, à moins de 256 blocs d'une de vos places, payé), et Ctrl+Z annule aussi un claim. L'ancien
  `/crusade info` des opérateurs devient `/crusade royaume [joueur]`.
- **Plafond de chunks revendiqués** selon le rang (64, 160, 400, 1 000, 2 500 ; réglable) ; les terres autour des
  villes ne comptent pas.
- **Titre à l'entrée** : en passant une frontière, le nom du royaume en grand et son souverain dessous (« Vos
  terres » chez soi, « Terres inconnues » chez un royaume pas encore découvert) ; « Terres sans maître » au-dessus de
  la barre en sortant. Une couleur de royaume trop sombre est éclaircie pour rester lisible. Remplace l'ancien message
  d'entrée, qui ne valait que pour les royaumes IA.
- **Protections selon le lien**, sur les terres des royaumes de joueurs : les siens (souverain, officiers de ses
  colonies) font tout ; suzerain et vassaux aussi ; un allié ouvre portes et coffres ; un ennemi en guerre pose et
  ouvre (casser reste l'affaire des règles de guerre) ; les autres ne touchent à rien. Jamais pour les automates (faux
  joueurs), jamais dans une colonie (MineColonies décide), les opérateurs passent outre ; terres des IA libres par
  défaut. Tout est réglable (section `protection`).
- **Deux tests ajustés, pas contournés** : un test unitaire réutilisait un instantané après avoir peint (d'où la règle
  ci-dessus), et le test des dégâts de guerre comptait sur un mur « près du siège ennemi » — avec la règle au chunk,
  cela dépendait du centre du chunk ; ses chunks sont maintenant peints au nom de l'ennemi.

**P17 (9 octobre 2026) — la carte web Dynmap et Xaero**

- **Dynmap** (choisi par Romain le 5 octobre, capture « Duchy of France » à l'appui), version Forge 1.20
  `3.7-beta-6`, compilé contre son jar (libs/local, jamais redistribué) et utilisé seulement dans `compat/dynmap`.
  Deux couches : « Royaumes » — chaque royaume en rectangles de chunks exacts (remplissage) et en segments de
  frontière (trait), sans polygone à trou — et « Lieux » (villes, forts, lieux saints, ruines ; une région en aplat
  léger). Un clic ouvre la fiche : titre, souverain, trésor, foi, maison, population, fondation, « Terres : X chunks
  (revendiqués : Y / Z) », suzerain, vassaux, conseil, et le texte de la fiche du lieu.
- **Le modèle ne connaît pas Dynmap** (`WebMap`, `WebMapGeometry`) : le module Dynmap ne fait que le recopier en
  marqueurs, en mettant à jour ce qui existe et en retirant ce qui a disparu, seulement quand l'empreinte change
  (vérifiée toutes les 20 s, réglable). Marqueurs non persistants : rien de périmé ne traîne d'une partie à l'autre.
- **Textes côté serveur** : un serveur ne charge pas les traductions ; les fiches web sont composées à partir des
  fichiers de langue du mod (`ServerText`), en français par défaut. Tout est échappé : un nom ou une fiche ne
  deviennent jamais du code dans la page.
- **Pas de brouillard sur le web** : la page est la même pour tous. Les royaumes IA peuvent en être retirés
  (`royaumesIA = false`), le trésor et le conseil aussi.
- **Xaero's World Map et Minimap** : les terres, chunk par chunk, avec le bord souligné là où le maître change et le
  nom au survol — comme Xaero colore les claims d'OPAC, avec ses propres réglages d'opacité et son interrupteur
  « claims ». Xaero n'a pas d'API publique : un seul mixin client, `@Pseudo`, en tête de `HighlighterRegistry.end()`,
  dans sa propre configuration non obligatoire ; sans Xaero il ne s'applique pas, si Xaero change il ne trouve rien
  et la couche disparaît sans planter. Le serveur envoie à chaque joueur, toutes les 5 s et seulement si elles ont
  changé, les terres des royaumes qu'il connaît (brouillard respecté), en rectangles.

**P18 (10 octobre 2026) — la carte de guerre, éditée dans le jeu et sur Dynmap**

- **Demandée le 5 octobre** (« pareil pour la map de guerre, où sera-t-elle éditée ? », et qu'elle soit aussi sur
  Dynmap) : un plan de bataille par royaume de joueur, tracé **dans la carte du royaume** (bouton « ⚔ Guerre ») plutôt
  qu'un nouvel écran : on peint sur les mêmes chunks que les terres, au même zoom, avec les mêmes villes dessous.
- **Ce qu'on trace** : des zones peintes chunk par chunk (offensive, défense, objectif, et la gomme), des flèches
  d'attaque, des repères annotés (objectif, ralliement, danger, note). Bornes : 64 flèches, 64 repères, 120
  caractères par note, 4 096 chunks par coup de pinceau ; les codes de mise en forme et les sauts de ligne sont ôtés.
- **Qui trace, qui voit** : le souverain trace le plan de son royaume ; le serveur revérifie chaque geste. Ses alliés
  (alliance ou alliance défensive), son suzerain et ses vassaux le voient, sans pouvoir y toucher. Les royaumes de
  l'ordinateur ne tracent rien : le plan est un outil de joueur.
- **L'état de la guerre ne se recopie pas** : les sièges en cours sont lus dans le monde à chaque envoi de la carte
  (attaquant, défenseur, garnison qui tient encore), jamais rangés dans le plan. Rien ne peut donc s'y périmer.
- **Sur Dynmap**, une troisième couche « Carte de guerre » : zones en rectangles exacts avec leur bord, flèches en
  trait épais avec leur pointe, repères avec les icônes livrées par Dynmap (drapeau rouge, drapeau bleu, danger,
  panneau), sièges en feu. La page web étant publique pour qui a l'adresse, **les plans y sont visibles de tous** ;
  c'est dit dans le guide, et la couche se coupe (`carteWeb.guerre`).
- **Données en version 14** (`warMap`), absente = aucun plan ; copie datée avant mise à niveau comme pour P15. Une
  sauvegarde faite par la 1.5.0 (format 13) rejoint les sauvegardes de non-régression : elle se charge et se réécrit
  sans rien perdre. Un royaume dissous ou effacé emporte son plan.
- **Un point d'entrée réservé aux tests** (`WarFieldService.siegeForTests`) : un siège ne démarre qu'au passage d'un
  vrai joueur, ce que les GameTests n'ont pas ; on y pose donc un siège sans troupes pour vérifier qu'il s'affiche.
  Le banc de captures, lui, montre un vrai siège.

**P18b (11 octobre 2026) — la carte du mod sur le vrai monde**

- **Ce que demandait le plan** (§6, P18) : un vrai terrain partout, même inexploré ; les couleurs réelles là où l'on
  est passé ; des calques ; un zoom fluide ; l'état de la guerre qui se dessine tout seul.
- **L'estimation sans générer** : chaque point est une colonne de bruit du générateur (`getBaseColumn` : roche, eau,
  air) et le biome à sa surface, comme `/locate` et le repérage des villages de P5. Un GameTest vérifie qu'une tuile
  calculée à 200 000 blocs ne charge aucun chunk. Ni constructions ni structures : c'est dit au joueur.
- **Hors du fil du serveur** : un fil à part, de basse priorité, calcule les tuiles demandées, les plus grossières
  d'abord ; les fils de génération de Minecraft font déjà ces mêmes appels en parallèle. Si un générateur de mod s'y
  refuse, la tuile reste inconnue (parchemin) et un avertissement unique est écrit, sans planter.
- **Trois niveaux de détail** (un point par chunk, par 4 chunks, par 16 chunks), en tuiles de 32 × 32 points,
  choisis selon le zoom ; une tuile fine qui manque laisse voir la grossière. Cache en mémoire et sur disque
  (`data/crusadecraft_terrain/v1`), jetable : il se refait.
- **Les vraies couleurs** : autour de chaque joueur, les chunks déjà en mémoire sont relevés (quatre points, couleur de
  carte du bloc en surface, eau assombrie par sa profondeur) ; ceux qu'il foule sont relus à chaque passage. Le relevé
  remplace l'estimation à l'envoi. Il s'écrit à l'arrêt et toutes les cinq minutes.
- **Palette d'atlas** : des genres de paysage tirés des étiquettes de biomes (Minecraft puis Forge, que suivent les
  mods), la température en dernier recours ; la mer s'assombrit au large, la roche puis la neige gagnent en altitude,
  le relief s'ombre comme au soleil du nord-ouest, avec la même ombre à tous les zooms.
- **Trois vues** : politique (les royaumes en voile sur le paysage), de jure, terrain (le paysage seul, frontières en
  trait). Le brouillard assombrit et hachure le paysage au lieu de le cacher : il cache des royaumes, pas des montagnes.
- **L'état de la guerre** : provinces occupées rayées à la couleur de l'occupant, ligne de front en rouge entre les
  deux camps, lues dans les guerres à chaque envoi de la carte. La Table de guerre ouvre la carte en mode guerre.
- **Zoom fluide** : pendant un geste, l'image déjà calculée est étirée ou décalée ; elle n'est recalculée qu'une fois
  le geste fini, et au plus quatre fois par seconde quand des tuiles arrivent.
- La carte papier et la Table de guerre murale gardent leur parchemin : ce sont des objets du monde.
- **Mesuré** : lire la colonne entière du générateur coûtait 4,5 s par tuile de 1 024 points (presque tout en
  calcul des nappes d'eau souterraines) ; la surface préliminaire (une fonction de densité, de 8 en 8 blocs, affinée
  entre deux mesures) descend à 0,45 s. Sous le niveau de la mer, de l'eau seulement dans les biomes d'eau ou dans une
  vraie cuvette (4 blocs) : sinon les plaines basses se criblaient de flaques.
- **Vérifié** : GameTests (aucun chunk chargé par l'estimation, vraies couleurs relevées, tuile livrée par le fil à part,
  état de la guerre transmis, aperçu de la carte dessiné hors écran dans `run-gametest/apercu_terrain.png`) ; une
  première capture en jeu sur un monde normal a montré les défauts corrigés ensuite (gros carrés, flaques). Romain a
  demandé le 11 octobre de ne plus lancer le jeu : la capture en jeu du rendu final n'a pas été refaite.

**P19 (31 octobre 2026) — les villes du royaume**

- **Rattacher** une autre colonie qu'on possède : depuis l'écran « Villes » du menu royal, en se tenant dedans (la
  Borne de frontière reste un second chemin). On ne passe que par `annexOwnColony` : CrusadeCraft ne crée, ne cède ni
  ne transfère jamais de colonie MineColonies ; le souverain en reste le propriétaire technique.
- **Limite de domaine**, à la manière de CK3 : un souverain tient en direct 2, 3, 4, 6 ou 8 villes selon son rang
  (réglable) ; chaque ville tenue en direct au-delà rend 30 % d'impôts en moins (réglable). La capitale compte
  d'abord, puis les villes dans leur ordre d'entrée : c'est toujours la dernière rattachée qui pâtit.
- **Gouverneurs** : une personne vivante du registre des lignées, de la maison du souverain ou rattachée à son
  royaume. Elle tient la ville pour la couronne, qui n'en reçoit que la part convenue des impôts et des levées
  (50 % chacune par défaut, réglable ensuite ville par ville, de 10 en 10). Une ville confiée sort du domaine.
  L'entretien militaire de la ville se partage dans la même proportion.
- **Un gouverneur mort ou effacé du registre, une ville perdue** : les termes tombent d'eux-mêmes (vérifiés à chaque
  calcul du Trésor et à l'ouverture de l'écran), la ville revient en direct. Rien ne reste attaché à un fantôme.
- **Ce qui n'est pas fait** : céder une colonie en propriété MineColonies à un PNJ est impossible (MineColonies exige
  un joueur propriétaire) ; le gouverneur n'agit pas encore de lui-même (il viendra avec les PNJ vivants de P20).
- **Données en version 15** (`cities` dans chaque royaume), absentes = toutes les villes en direct, comme avant ;
  canal réseau en version 16.
- **Vérifié** sans lancer le jeu (demande de Romain du 11 octobre) : tests unitaires des règles et de la sauvegarde,
  GameTest qui crée une vraie seconde colonie MineColonies, la rattache, la confie, la voit dans le Trésor, perd son
  gouverneur et la reprend. L'écran « Villes » n'a pas été photographié en jeu.


**P20 (31 octobre 2026) — les gens de la cour**

- **Fiche d'une personne** dans le registre des lignées (bouton « Fiche ») : prénom, âge, jusqu'à quatre traits de
  caractère pris dans le catalogue existant (deux contraires ne vont pas ensemble), biographie libre de 300
  caractères sans codes de mise en forme. Le texte est celui de Romain : rien n'est pré-rempli.
- **La journée** des personnes incarnées (Minecraft Comes Alive) : à la cour de leur royaume de 1 000 à 11 000
  (centre de la capitale), chez elles ensuite, à la cour jour et nuit si leur royaume est en guerre. On ne leur donne
  qu'un but de marche (mémoire `WALK_TARGET` des villageois), jamais plus loin que 512 blocs : leur propre
  intelligence fait le reste et peut l'emporter. Réglable (`pnj.routines`).
- **Pas fait** : boutons « Apparence » et « Arbre » de MCA (son écran ne s'ouvre que par ses propres paquets),
  fêtes et deuils animés, unification des deux généalogies (maison P4 et registre P13), répliques par datapack.
  Les traits et la biographie s'ajoutent aux personnes sans changer la version des données (absents = aucun).

**P21 (31 octobre 2026) — le cycle de vie**

- **Les années passent** : tous les 20 jours du monde (réglable, `vie.joursParAn`), chacun vieillit d'un an — les gens
  du registre et les souverains de l'ordinateur avec leur conseil. Mourir dans l'année : 3 ‰ avant 50 ans, puis
  3 + (âge − 50)² / 2 ‰, plafonné à 90 %, et personne ne passe 100 ans. Le joueur ne vieillit pas.
- **Héritage** : à la mort du chef d'une maison, l'héritier selon la nouvelle loi **Succession** du royaume
  (primogéniture, fils d'abord ; égalitaire ; ancienneté ; élective, l'adulte qui a le plus de traits). Sans enfant,
  le doyen. La loi entre dans le système de lois existant (vote du conseil, ou passage en force au prestige).
- **Trônes de l'ordinateur** : un successeur (un conseiller adulte pour les lois élective et d'ancienneté, sinon un
  héritier tiré au hasard), annoncé dans les nouvelles du monde.
- Une personne incarnée qui meurt de vieillesse meurt aussi dans le monde (son villageois). Les morts du registre se
  coupent seules (`vie.mortsDuRegistre`) si Romain veut garder ses personnages.
- **Pas fait** : héritage de l'or et des objets (seul le joueur en possède), crises de succession appuyées sur les
  factions, arbre illustré sur plusieurs générations, partage d'un royaume IA entre héritiers.

**P22 (31 octobre 2026) — les civilisations autonomes**

- Les royaumes de l'ordinateur posent d'eux-mêmes une tour, un camp, un avant-poste puis un fort (duchés et
  au-delà), quatre au plus par royaume, sur leurs propres terres, à 40–100 blocs d'une de leurs villes, en chantier
  bloc par bloc comme ceux des joueurs.
- **Coupé par défaut** (`civilisations.actif = false`) : c'est la seule partie du mod qui change le monde sans geste
  de Romain. Zone permise (3 000 blocs autour de l'origine par défaut), chunks déjà chargés seulement, jamais dans une
  colonie, seulement sur un sol naturel (terre, herbe, sable, pierre, neige, gravier) et plat à trois blocs près.
- **Tout se défait** : chaque bloc remplacé est noté au registre des dégâts sous l'identifiant du chantier ;
  `/crusade civ annuler` retire ces constructions du registre et remet les blocs d'avant, quarante-huit par
  quart de seconde. `/crusade civ` dit où on en est.
- **Pas fait** : villages qui grandissent de camp en bourg, nouveaux villages peuplés, disputes de lieux.
- Un test du conseil (un citoyen MineColonies qui marche jusqu'à la réunion) devient facultatif : il dépend des
  chemins que MineColonies trouve et échouait au hasard, sans rapport avec le mod.

**P23 (31 octobre 2026) — les levées en chair et en os**

- Armée levée à la Table de guerre, le souverain se rend près d'un **camp militaire** bâti de son royaume (48 blocs)
  et tape `/crusade armee rassembler` : l'armée apparaît en vraies unités Hundred Years War (des golems de fer à
  défaut, comme pour les sièges), un soldat pour 10 hommes, 24 au plus (réglable, section `guerre`). Le reste de
  l'armée reste un chiffre.
- **Ordres** : `suivre` (le souverain), `tenir` (là où il se tient) ; un soldat qui se bat garde sa cible.
  `demobiliser` : les survivants quittent le monde, les hommes restent comptés.
- **Pertes réelles** : un soldat tué retire 10 hommes à l'armée du royaume (l'armée se reforme ensuite comme avant,
  avec les jours). Les soldats portent l'étiquette de leur royaume : on les retrouve après un redémarrage, mais leur
  ordre retombe sur « tenir » leur place.
- **Pas fait** : ordres depuis la carte (seulement par commande), formations, armées de l'ordinateur visibles et en
  marche, pions sur la carte hors des chunks chargés, bouton dans l'écran de guerre.

**P24 (31 octobre 2026) — finitions, version 2.0.0**

- **`/crusade perf`** : chaque appel périodique du serveur est chronométré (deux lectures d'horloge), avec le total,
  la moyenne par tick et le pire appel par système ; `/crusade perf reset` remet à zéro.
- **Budgets revus** : l'armée en chair et en os ne parcourt plus les entités qu'une fois par monde et par seconde (au
  lieu d'une fois par royaume) ; les journées de la cour ne regardent que les personnes incarnées ; années et
  constructions de l'ordinateur ne tournent qu'une fois par jour du monde.
- **Guide complet** : `GUIDE.md` (sections 63 à 74), Codex (gens de la cour, cycle de vie, royaumes qui bâtissent,
  armée), et le guide en ligne.
- **Pas fait** : l'écran de réglages en jeu (les réglages restent dans le fichier du monde), et le commerce (prévu
  après P24).

**P25 (31 octobre 2026) — le commerce, version 2.1.0**

- Le traité de commerce existait (Colombier) et rapportait déjà de l'or ; il trace maintenant une **route** entre les
  deux capitales (centre de la colonie d'un joueur, siège d'un royaume IA), visible sur la carte du royaume (pointillés
  dorés, revenu au survol) et sur Dynmap (couche « Routes commerciales », coupable : `carteWeb.commerce`).
- **La distance rapporte** : une pièce de plus par jour tous les 400 blocs de route, dix au plus, en plus de la part
  de base d'avant. Une route courte rapporte comme avant.
- Sur la carte du mod, un joueur voit ses propres routes, et celles entre deux royaumes qu'il connaît.
- **Pas fait** : caravanes qui marchent sur le monde, marchandises une à une, monnaie State Marks.

- Le GameTest des constructions de l'ordinateur (P22) prépare désormais sa propre parcelle d'herbe plate : le relief du
  monde de test change à chaque partie, et le test échouait au hasard selon le terrain tiré.

**P26 (1er novembre 2026) — la cour au complet, version 2.2.0**

- **Fêtes et deuils** : bouton « Banquet » dans l'écran de la Maison (60 pièces par rang, prestige, opinion des
  conseillers, des maires et de trois voisins invités, une fois par semaine au plus, jamais en guerre ni en deuil).
  Pendant une demi-journée, les gens du registre incarnés se rassemblent à la cour. À la mort d'un chef de maison, du
  souverain, de son conjoint ou de son héritier, trois jours de deuil ; le premier, les funérailles rassemblent tout
  le monde. État dans `CourtState`, champs facultatifs : pas de nouvelle version des données.
- **Répliques par datapack** (`crusadecraft_repliques`) : selon le moment, le rôle et les traits ; dites dans le chat
  aux joueurs à moins de 12 blocs, une par personne toutes les 45 secondes au plus. Le mod en fournit une vingtaine,
  en clés de traduction.
- **Comes Alive** : « Apparence » ouvre son éditeur **limité** (visage, cheveux, vêtements ; le nom, l'âge et la
  famille restent ceux du registre), seulement si le villageois est chargé à moins de 48 blocs — son écran cherche
  l'entité chez le client. « Arbre Comes Alive » inscrit la maison dans son arbre puis ouvre son écran, qui demande
  lui-même la famille au serveur : il marche aussi pour ceux qui ne sont jamais apparus.
- **Arbre illustré** pour chaque maison du registre (disposition en logique pure, testée), glisser et zoomer.
- **Les deux généalogies** (maison des citoyens MineColonies de P4, registre de P13) **ne sont pas fondues** : la
  fusion toucherait aux sauvegardes pour un gain mince. Elles se montrent l'une l'autre : le registre liste les
  citoyens de la maison du souverain, l'écran Maison ouvre le registre sur la maison du royaume.
- **Héritage** : une ville confiée passe à l'héritier du gouverneur, aux mêmes conditions. Les personnes du registre
  ne possèdent ni or ni objets : il n'y a rien d'autre à hériter, et le joueur ne meurt pas de vieillesse.
- **Successions de l'ordinateur** : partage sous la loi égalitaire (trois villes au moins, le cadet emporte la moitié
  des autres villes, d'un seul tenant, et la moitié du trésor) ; crise une fois sur quatre sous les lois élective et
  d'ancienneté (un tiers des villes, guerre de revendication sur la capitale). Les numéros des villes ne changent pas :
  lieux, revendications et fiches suivent. Les royaumes posés à la main sont épargnés par défaut. Une limite : si un
  royaume a des frontières peintes à la main, elles restent à l'ancien royaume.
- Canal réseau en version 17.

**P27 (1er novembre 2026) — les armées, version 2.3.0**

- **Ordres** : l'écran de guerre a ses boutons (rassembler, suivre, tenir, formation, démobiliser) ; la carte, en mode
  Guerre, a l'outil « Marcher » ; `/crusade armee marcher <x> <z>` et `formation <nom>` par commande.
- **Formations** en logique pure (masse, ligne sur deux rangs, colonne par deux, carré), orientées vers la marche ou le
  regard du souverain ; les soldats sont rangés par identifiant pour garder leur place d'une seconde à l'autre.
- **Marche par étapes** de 28 blocs : la navigation de Minecraft ne trouve pas de chemin plus long. Un soldat dans un
  chunk non chargé s'arrête ; rien n'est simulé hors de la vue (ce serait un second moteur de déplacement à tenir).
- **Ostes de l'ordinateur** : en guerre, l'attaquant et le défenseur IA lèvent chacun un ost (enregistré dans la
  guerre, champ facultatif). Il part de la capitale à 6 blocs toutes les 5 secondes (réglable), vers la province
  réclamée ou la capitale ennemie ; le défenseur marche à la rencontre de l'ost adverse s'il approche à moins de
  1 500 blocs de sa capitale, sinon il la garde. **Bataille** quand l'armée rassemblée d'un joueur ennemi passe à
  moins de 48 blocs : l'ost paraît (un soldat pour dix hommes de sa force, plafonné comme celle du joueur) ; s'il
  tombe en entier, +15 au score pour l'autre camp et +10 de fatigue au sien, et il se reforme chez lui trois jours
  plus tard. Les raids sur les colonies restent ce qu'ils étaient.
- **Pions** sur la carte du mod (armée du joueur rassemblée, ostes des royaumes connus) et couche Dynmap « Armées »
  (`carteWeb.armees`).
- **Pas fait** : marche simulée hors des chunks chargés, alliés de l'ordinateur qui lèvent aussi leur ost (seuls
  l'attaquant et le défenseur principaux en ont un), choix des cibles des soldats d'ost (ce sont des monstres :
  ils attaquent tout joueur proche).

**P28 (1er novembre 2026) — les villages de l'ordinateur, version 2.4.0**

- Un camp bâti de lui-même depuis 10 jours fait fonder un **village** à 40–80 blocs (plan « village » de P14 : quatre
  maisons, un puits) : une ville « hameau » sur la carte, une communauté de deux familles au registre, qui
  apparaissent avec Comes Alive une fois le village achevé. Deux villages au plus par royaume.
- **Croissance par quartiers** (nouvelle construction, deux maisons et une rue) contre le village, tous les 8 jours, un
  à la fois, trois au plus : +12 habitants et +5 de développement à la ville, une famille de plus au registre.
  Un quartier ne se pose que sur une parcelle plate des terres du royaume, à quatre blocs de hauteur du village.
  Hameau, bourg (un quartier), ville (trois).
- **Disputes** : fonder à moins de 200 blocs des terres d'un autre royaume (huit points regardés) baisse l'opinion des
  deux côtés (−15, oubliée en 30 jours) et fait une nouvelle. Pas de guerre automatique pour autant : l'IA décide de
  ses guerres comme avant, avec cette opinion en plus.
- Les places fortes comptent à part : un royaume peut avoir ses quatre places fortes **et** ses deux villages.
- **Annuler** défait aussi ce qui n'est pas des blocs : la ville sur la carte (et les fiches et revendications qui la
  visaient), la communauté et ses personnes, et les villageois qui les incarnaient s'ils sont chargés.
- Toujours coupé par défaut (`civilisations.actif`).
- **Pas fait** : villages sur terrain accidenté (il faut une parcelle plate), villages fondés loin de tout camp.

**P29 (1er novembre 2026) — caravanes, marchandises, State Marks, version 2.5.0**

- **Marchandises** : une par royaume, d'après le biome de sa capitale, lu au générateur (rien n'est chargé) : l'eau
  donne le poisson, puis les badlands la pierre, le relief le fer, la jungle les épices, le sec le sel, le froid les
  fourrures, la savane la laine, la forêt le bois, le chaud le vin, le blé à défaut. Deux marchandises différentes sur
  une route : +3 pièces par jour. Pas de stocks ni de marché : ce serait un second jeu dans le jeu.
- **Caravanes** : une par route, qui va et vient à 4 blocs toutes les 5 secondes (réglable). Un chariot sur la carte du
  mod et sur Dynmap (couche « Routes commerciales »). À moins de 64 blocs d'un joueur, elle paraît : un marchand
  ambulant à son nom et deux lamas de marchand en laisse, qui suivent la route par étapes de 24 blocs ; plus de joueur
  à 112 blocs, elle redevient un chariot.
- **Pillage** : tuer le marchand fait tomber cinq jours de revenu de la route en pièces, coupe la route 3 jours, et
  les deux royaumes de la route en veulent au royaume du pillard (−25, 30 jours) sauf s'il est en guerre avec eux.
  L'état des caravanes n'est pas enregistré : un redémarrage les remet à leur première capitale et oublie un pillage.
- **State Marks** (mod « Holy Guac! State Marks! », identifiant `stans_funtime_rapey`, présent chez Romain) : bronze,
  argent, or valent 1, 9, 81 au Coffre du Trésor quand le mod est là ; le butin d'une caravane tombe en ces pièces.
  On ne dépend pas du mod : sans lui, rien ne change, et le butin tombe en or.

**P30 (1er novembre 2026) — l'écran de réglages, version 3.0.0**

- **Tous les réglages en jeu** : l'écran lit le fichier tel que Forge le décrit (sections, commentaires, bornes) ; rien
  n'est recopié à la main, un réglage ajouté plus tard y paraîtra de lui-même. Oui/non, nombres et choix se modifient ;
  les listes se lisent seulement (les saisir dans une case serait trop fragile).
- **Réservé** aux opérateurs et au propriétaire d'un monde solo, même sans les commandes. Le serveur revérifie chaque
  valeur contre les bornes du réglage, l'enregistre dans le fichier du monde, et elle s'applique aussitôt.
- Bouton ⚙ dans l'en-tête de l'onglet Royaume, et `/crusade reglages`. Canal réseau en version 18.
- **Version 3.0.0** : la fin de tout ce qui restait de la feuille de route 2.0 (P26 à P30).
- **Bancs d'essai fiabilisés** : les tests des ostes et des caravanes forcent leurs chunks (une entité qui paraît dans
  un chunk chargé sans billet n'est pas encore visible), leurs nettoyages ne suppriment plus d'entités en parcourant la
  liste (elle rendait parfois un trou, et un nettoyage interrompu laissait un royaume qui faisait échouer les tests
  suivants), et les refus de l'ordinateur de bâtir disent leur raison (`CivService.refusal`). La lecture du paysage
  d'une capitale se replie sans erreur si le générateur refuse la question (une ligne au journal).

**P31 (1er novembre 2026) — finitions vues en jeu, version 3.0.1**

- Romain a demandé de lancer le jeu : tous les écrans du mod (66) ont été repris au banc d'essai visuel, et un
  scénario en monde réel (`runWorldShots -Pshots_v3=true`) joue les nouveautés de bout en bout : fondation, banquet,
  arbre, réglages, armée rassemblée au camp puis en ligne, ost ennemi qui paraît et se bat, carte de guerre, caravane,
  village de l'ordinateur et son quartier. Tout s'est déroulé sans erreur.
- **Corrigé d'après les captures** : textes qui débordaient (ligne du domaine des villes, aide des traits, état de la
  cour, ligne de l'armée), boutons tronqués (parts de la couronne, ordres de l'armée), titre de l'arbre sur son mode
  d'emploi, arbre cadré d'emblée, phrase « L'ost de du Royaume… » (les noms prennent leur préposition), noms des
  réglages lisibles et accentués (« Trône dans hôtel de ville »), flèche quand une liste continue, et l'en-tête du
  menu royal : Villes, Lieux, Maison et ⚙ montent sur la ligne du titre, le rang et la devise ne sont plus coupés.
- Le registre : le bouton qui inscrit une maison dans l'arbre de Comes Alive s'appelle « Vers CA » (« Arbre » se
  confondait avec l'arbre illustré).
- Un envoi au serveur sans connexion (écran de titre, banc d'essai) est simplement perdu au lieu de planter.
- Un texte d'une ligne trop long rétrécit jusqu'aux deux tiers avant d'être coupé (`Ui.fitted`).

**P32 (1er novembre 2026) — la carte fantasy et les noms, version 3.1.0**

- Demandé d'après une capture de Dynmap : le nom du royaume (Levànzia) et ses villes (Khalyos) écrits sur la carte,
  avec une icône, « comme une vraie carte fantasy ».
- **Noms des royaumes** : une couche Dynmap « Noms des royaumes », un repère sans icône au cœur de la plus grande
  terre d'un seul tenant (le chunk le plus loin des bords, en évitant les villes et leurs abords), le nom en
  capitales espacées, à l'encre teintée de la couleur du royaume, avec un halo de parchemin. La taille suit
  l'étendue (14 à 26 pixels) ; les petits royaumes ne paraissent qu'en zoomant (un nom ne grandit pas avec le zoom :
  de loin, tout se chevaucherait).
- **Villes et lieux** : icônes dessinées par le mod (sceau doré, point cerclé, point, tour, croix, ruine), noms
  toujours affichés à partir d'un zoom qui dépend du genre (capitales, puis villes, puis villages et petits lieux).
- **Le cadre de Dynmap** : Dynmap filtre le style des étiquettes (il garde police, couleur, halo, et retire classes,
  feuilles de style et transformations). Le seul moyen de retirer son cadre sombre est sa feuille `override.css`,
  livrée en commentaire dans sa page : le mod l'écrit (en complétant un fichier existant entre deux marqueurs) et
  rend la ligne active, à chaque démarrage, puisque Dynmap réécrit sa page quand il change de version. Coupable.
- **Renommer** depuis le jeu : royaume (✎ du menu royal, ou commande), ville (✎ de l'écran des Villes : la colonie
  MineColonies est renommée par son API, comme depuis l'Hôtel de ville), et l'écran des Lieux renvoie désormais à
  la source au lieu de refuser. Les royaumes et villes de l'ordinateur restent réservés à l'éditeur de la carte.
- Canal réseau en version 19. Vérifié dans le jeu de développement et sur la page Dynmap.

**P33 (1er novembre 2026) — zones dessinées d'avance et fusions, version 3.2.0**

- Demandé : « créer des royaumes, des zones préfaites, pour ensuite y mettre des colonies IA ou son propre royaume »,
  et des fusions comme les nations de Towny ou Factions sur Dynmap.
- **Reprendre un royaume dessiné** : à la fondation, si la capitale tombe dans les cases peintes d'un royaume posé à
  la main, le nouveau royaume du joueur en reprend les terres peintes et les villes (tenues par lui, comme des villes
  prises) ; le royaume dessiné reste sans terre, à l'écart, pour ne rien perdre de ses fiches. Le nom et la couleur
  restent ceux de l'écran de fondation.
- **Fusion** dans l'éditeur : un royaume de l'ordinateur passe sous un autre. Vers l'ordinateur, ses villes
  déménagent (mêmes numéros) et il disparaît ; vers un joueur, ses villes lui sont tenues et il reste sans terre.
  Trésor transféré, cases peintes repeintes, nouvelle dans le monde.
- **Colonies qui rejoignent leur zone** : toutes les vingt secondes, une colonie MineColonies dont le souverain est
  propriétaire, à personne encore, et dont le centre est sur les terres de son royaume (peintes ou revendiquées),
  s'y rattache. Réglable.
- Canal réseau en version 20.

**P34 (1er novembre 2026) — villes réelles selon leur civilisation, plan d'architecte, version 3.3.0**

- Demandé : « faire ville IA réelle et outil de construction créatif », avec les deux mods de base (MineColonies et
  Byzantine) pour des nations chinoise, japonaise, égyptienne fantasy et européenne ; puis « s'inspirer de sources
  officielles et de sites spécialisés » pour l'urbanisme de chaque civilisation.
- **Aucun plan copié** : les packs sont lus au moment du jeu par l'API de Structurize (`StructurePacks`), y compris
  ceux de Byzantine et les scans du joueur. Les fichiers `.nbt` et `.schem` (Sponge 1 à 3) sont lus dans
  `crusadecraft/plans/`, `schematics/` et `config/worldedit/schematics/` ; un chemin qui sort du dossier est refusé.
- **Traduction des plans** : les blocs de substitution de Structurize deviennent terrain laissé, terre, eau ou le bloc
  porté ; les cabanes MineColonies deviennent des tonneaux dans une ville de l'ordinateur (elle n'a pas de colonie), mais
  restent des cabanes posées à l'outil ; les données d'entité sont gardées (textures Domum Ornamentum, coffres). Sous le
  sol, le vide d'un plan n'est creusé qu'entre des murs. L'entrée d'un plan est déduite de ses portes.
- **Urbanisme** (`TownLayout`), d'après :
  - Rome : cardo nord-sud et decumanus est-ouest se croisant près du forum, insulae en damier, castrum rectangulaire
    (Britannica, *Western architecture* ; article « Decumanus ») ;
  - Chine : Kaogong ji — ville carrée, palais au centre tourné au sud, temple des ancêtres à gauche (est), autel à
    droite, cour devant, marché derrière (dossier UNESCO de l'axe central de Pékin, 2024) ;
  - Japon : Heian-kyō d'après Chang'an — palais au nord du centre, Suzaku-ōji vers le sud, grille jōbō, pas de muraille
    continue, temples de l'Est et de l'Ouest près de Rashōmon, marchés de l'Est et de l'Ouest ;
  - Égypte : Kahun (El-Lahun) — ville planifiée, rues en grille alignées sur les points cardinaux, maisons en rangées,
    enceinte, temple et résidence du maire (Petrie ; N. Moeller, Oriental Institute) ; axe processionnel des temples ;
  - Scandinavie : Trelleborg — rempart circulaire, quatre portes aux points cardinaux, deux rues en croix, seize maisons
    par carrés de quatre dans les quartiers, basse-cour extérieure au sud-est ;
  - Europe médiévale : place du marché au carrefour des routes, église près du marché, parcelles en lanière le long de
    la grand-rue (études des burgage plots d'Édimbourg, de Hungerford, de Blandford).
  Adaptations assumées : la halle de Trelleborg (qui n'en avait pas au carrefour) occupe seule un quartier ; l'autel du
  sol chinois n'a pas de bâtiment propre dans les packs ; l'ordre des enceintes suit la taille de la ville.
- **Journal** : chaque bloc remplacé (et le contenu des coffres) est noté dans un fichier à part, compact ; « Défaire la
  ville », `/crusade civ annuler` et `/crusade plan annuler` rendent le terrain bloc par bloc.
- Les villes de l'ordinateur qui prennent corps à l'approche sont **coupées par défaut** (cela change le monde) ; jamais
  là où des joueurs ont passé plus de vingt minutes, ni à moins de 160 blocs d'une colonie.
- Byzantine est ajouté en développement depuis `libs/local` (jamais redistribué). Canal réseau en version 21.
- **Poser ses propres villes en créatif** (demandé en cours de phase) : une ville entière posée au plan d'architecte
  devient une ville du monde — elle rejoint le royaume dont elle est sur les terres, ou fonde sur une terre libre une
  nation de son style dont elle est la capitale ; sa construction porte l'identifiant de la pose, si bien que
  « Défaire la ville » la retire comme les autres. L'outil vise à 96 blocs (pose et cadre d'aperçu) : un bâtiment de
  trente blocs ne se pose pas à bout de bras.
- Vérifié : 198 tests unitaires, 76 GameTests ; dans le jeu de développement, une ville de chaque style (Medieval Oak,
  Pagoda, Shogun, Nile, Byzantine — chargé sans souci en 1.20.1 —, Nordic Spruce), le catalogue et le cadre d'aperçu.

**P34.1 (1er novembre 2026) — HUD du plan d'architecte, catalogue refait, version 3.3.1**

- Demandé après essai sur son serveur : « un beau HUD avec des infos », un catalogue « fluide et joli ».
- HUD (overlay au-dessus de la barre d'objets) : plan, pack, taille, côté de l'entrée, bloc visé et distance, gestes,
  barre d'avancement envoyée par le serveur pendant la pose ; fondu à l'apparition.
- Catalogue en trois colonnes, défilement doux, clavier ; panneau de détails avec aperçu vu du ciel calculé par le
  serveur (couleurs de carte du dessus de chaque colonne ; pour une ville entière, son plan par rôle de bâtiment, rues
  et enceinte, avec un tirage qu'on peut refaire — la graine voyage dans la référence choisie).
- Les habitants d'une ville arrivent sur la case de rue la plus proche du centre (place, forum, avenue), plus dans
  l'hôtel de ville. Canal réseau en version 22 : client et serveur doivent avoir la même version.

