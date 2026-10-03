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
