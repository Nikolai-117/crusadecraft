# CrusadeCraft — contexte pour Claude

Ce fichier est lu par Claude (Claude Code) en ouvrant le dépôt. Il dit ce qu'est le projet, les règles à ne jamais
enfreindre, comment construire et tester, où est quoi, et les pièges déjà rencontrés. Il complète le `README.md`
(installation, « Travailler à deux ») et `docs/decisions.md` (le journal de toutes les décisions, phase par phase).

## Le projet

**CrusadeCraft** est un mod Minecraft **Forge 1.20.1** (Forge 47.4.10, Java 17) : un *Crusader Kings 3* dans Minecraft,
branché sur **MineColonies** (1.20.1-1.1.1214, avec Structurize, BlockUI, Domum Ornamentum). Une colonie devient un
royaume : conseil, impôts, lois, diplomatie et guerres avec des royaumes gérés par l'ordinateur, lignées et familles
(villageois de *Minecraft Comes Alive*), carte du monde, plans de villes, villes vivantes.

- Auteur et joueur : **Romain** (compte GitHub `Nikolai-117`). On lui parle en **français simple** ; tout le jeu, les
  commentaires du code, les commits et la documentation sont en français.
- Il joue en solo (instance MultiMC) et sur un **serveur Hosterfy** : le client et le serveur doivent avoir **la même
  version** du mod (le canal réseau la vérifie, voir plus bas).
- Version courante : voir `mod_version` dans `gradle.properties`.

## Les règles qui priment sur tout

1. **Usage personnel, rien des autres auteurs dans le dépôt ni dans le jar.** Les plans (blueprints) de MineColonies,
   Byzantine et des autres packs de style sont **lus pendant le jeu**, jamais copiés. Les jars des autres mods vont
   dans `libs/local/` (ignoré par git) et ne servent qu'à compiler ou à essayer en développement. Le code repris de
   War 'N Taxes (MIT) est signalé en tête de chaque fichier ; voir `CREDITS.md`.
2. **Les villes que Romain a données** (Dolhimluith, Grunbach : des `.schem`) ne sont **pas** dans git : elles vivent
   dans `src/main/resources/crusadecraft_plans/` (les `*.schem` y sont ignorés) et Romain les transmet à part.
3. **Ne jamais toucher à l'instance ni aux mondes de Romain** : on développe et on essaie dans les dossiers de
   développement (`run/`, `run-gametest/`, `run-worldshots/`, tous ignorés). On ne copie pas de jar dans ses mods :
   on lui dit quel fichier mettre et lequel retirer.
4. **Zéro perte** : un monde d'une ancienne version se charge intact. Toute donnée enregistrée nouvelle est
   facultative au chargement (clé absente → valeur par défaut). Un changement de structure augmente
   `CrusadeSavedData.DATA_VERSION`, avec une migration, et une copie datée est rangée avant de migrer
   (`saves/<monde>/data/crusadecraft_backups/`).
5. **Être honnête** : si une chose est impossible ou fragile, le dire plutôt que la bricoler. Rien d'irréversible ni
   de public sans l'accord de Romain.

## Construire et tester

Il faut un **JDK 17** donné par `JAVA_HOME`, et les jars de `libs/local/` listés dans le README.

```bash
export JAVA_HOME="/chemin/vers/jdk-17"
./gradlew build                      # compile + tests unitaires (src/test) → build/libs/crusadecraft-<version>.jar
rm -rf run-gametest/world && ./gradlew runGameTestServer   # GameTests en monde réel (MineColonies, MCA…)
./gradlew runWorldShots -Pshots_v5=true -Pshots_world=normal   # client de développement scénarisé + captures
./gradlew runWorldShots -Pshots_v7=true -Pshots_v7_reel=true -Pshots_world=normal   # commerce, guerre, chantiers à vitesse réelle : une bonne heure
./gradlew runWorldShots -Pshots_v8=true   # l'atelier des villes : ses onglets, ses préréglages, une ville posée pour de vrai
./gradlew runWorldShots -Pshots_open=dossier -Pshots_uuid=...   # une COPIE de monde posée dans run-worldshots/saves (level.dat neutre, voir decisions.md, P59)
```

- **Tout doit passer avant un commit** : les tests unitaires (210 environ) et les GameTests (83 environ). L'essai
  `unvillagenaitgranditetsedefait` (phase 26) dépend du terrain tiré au hasard et échoue parfois : relancer.
- `runWorldShots` lance un vrai client (monde `run-worldshots/saves/crusadecraft-shots`, que l'on peut effacer pour
  repartir de zéro) et range les captures dans `run-worldshots/screenshots/`. Scénarios : `client/dev/WorldShots*.java`
  (`-Pshots_v4=true` villes par style, `-Pshots_v5=true` villes importées et bâton d'intendant, `-Pshots_quick=true`
  pour une seule ville). Regarder les captures : c'est la seule vraie preuve qu'un écran ou une ville est juste.
- Pour essayer avec les blocs de Dawn of Time : jars dans `libs/local/` (sinon les villes importées prennent les blocs
  de secours de Minecraft) ; `-Pwith_byzantine=false`, `-Pwith_mca=false`, `-Pwith_decor=false` pour s'en passer.

## Façon de travailler

- **Une branche par phase** : `phase/pNN-sujet` (par exemple `phase/p39-villes-finitions`), partie de la précédente ;
  on fusionne dans `main` une fois vérifiée. Les commits disent en français ce qui change et pourquoi.
- À chaque phase : augmenter `mod_version` (`gradle.properties`), noter les décisions dans `docs/decisions.md`, le mode
  d'emploi dans `docs/GUIDE.md`, et dire à Romain **quel jar installer et lequel retirer**.
- **Textes du jeu** : jamais écrits à la main dans `lang/*.json`. On écrit `scripts/lang/pNN_fr.json` et `pNN_en.json`,
  puis `node scripts/lang/merge.mjs pNN`. Ne jamais refusionner un ancien fichier de phase après un plus récent : il
  écraserait des textes changés depuis.
- **Réseau** : tout paquet nouveau ou changé augmente `VERSION` dans `network/CrusadeNetwork.java` (le client et le
  serveur doivent porter le même). Le serveur revérifie tout ce que le client envoie.
- Le style du code : commentaires et javadoc en français, courts, qui disent *pourquoi* ; noms de classes en anglais,
  noms de champs parlants. Suivre ce qui est autour.

## Où est quoi (`src/main/java/fr/crusadecraft/`)

| Dossier | Ce qu'il contient |
|---|---|
| `core/realm`, `council`, `law`, `treasury`, `court` | Le royaume du joueur : fondation, conseil, lois, trésor et impôts, cour, fêtes |
| `core/world` | Le monde : royaumes et provinces de l'ordinateur (`AiRealm`, `AiProvince`), territoires, carte, éditeur de la carte (`WorldEditService`) |
| `core/diplomacy`, `war`, `intrigue`, `event`, `ai` | Diplomatie et lettres, guerres et armées, intrigues, événements, décisions de l'ordinateur |
| `core/lineage`, `character` | Registre des lignées : personnes, familles, communautés, routines du jour (`RoutineService`) ; personnages et traits |
| `core/build` | Les constructions d'un royaume (`Site`, `SiteService`) : forts, villages, villes posées |
| `core/build/plan` | Le **plan d'architecte** : lecture des packs et des fichiers (`PlanLibrary`, `SchematicReader`, `BlockEquivalents`), plans des villes par civilisation (`TownPlanner`, `TownLayout`, `WallSets`), pose et retour en arrière (`Journals`, `PlanPlacer`, `BuildJournal`), fantôme (`PlanTool.ghost`) |
| `core/city` | Les **villes vivantes** : bâton d'intendant (`CityStaff`), reconnaissance des bâtiments (`BuildingScan`), fonctions (`BuildingFunction`), habitants et échanges (`CityService`, `CityTrades`), commandes (`CityCommands`) |
| `compat/minecolonies` | **Tout ce qui touche à l'API de MineColonies et Structurize**, et seulement ici |
| `compat/mca`, `dynmap`, `xaero`, `hyw` | Comes Alive, Dynmap, Xaero, Hundred Years Warfare : chacun derrière un pont (`*Bridge`) qui se coupe sans planter si le mod manque ou change |
| `client` | Écrans (`client/screen`, outils `Ui` et `Kit`), HUD (`client/hud`), carte et fantôme (`client/map`), villes (`client/city`), scénarios de captures (`client/dev`) |
| `network/packet` | Les paquets, par sujet (`PlanPackets`, `BuildingPackets`, `CityPackets`…) |
| `data/CrusadeSavedData` | Tout l'état enregistré du mod, versionné |
| `gametest` | Les GameTests (`*GameTests.java`, outil `TestWorld`) |

Documents : `docs/decisions.md` (pourquoi chaque chose est comme elle est), `docs/GUIDE.md` (mode d'emploi joueur),
`docs/conception-villes.md` (ce que Romain a décidé pour les villes), `docs/catalogue-plans.md` (inventaire des pièces
des packs), `docs/etape-2-plan.md` (le plan des phases).

## Le kit Varenhold (P61)

- `client/screen/Kit.java` tient la maquette de Romain : couleurs, panneau ancré, carte, puits, acte, boutons, pastilles,
  sections, icônes (`textures/gui/kit/icons.png`, 10 × 10, teintées au dessin) et ressources. `Ui` pointe sur ses
  couleurs : un écran pas encore refait en hérite déjà. Un bouton s'écrit `.build(KitButton::new)`, ou
  `KitButton::primary` pour l'action dorée.
- La gothique (`Kit.title`, police `crusadecraft:gothique`) ne sert qu'aux titres, à l'échelle 1 ou 2. Elle est plus
  large que la police du jeu : couper avec `fitGothic`, jamais avec la mesure de la police ordinaire.
- `RealmScreen` (le menu royal) dessine tout au rendu et note ses zones cliquables ; il baisse l'échelle d'interface
  sous 600 × 340 (`echelleMaquette`) et la rend dans `removed()`. Ne pas y appeler `resizeDisplay()` : l'écran encore
  ouvert remettrait aussitôt son échelle.
- Captures à la taille de l'écran de Romain : `./gradlew runUiShots -Pshots_only=menu_ -Pui_size=1920x1080 -Pui_scale=4`.

## La carte telle quelle (P62)

- `core/world/terrain/TerrainSurvey` relit au démarrage chaque chunk enregistré (en-têtes des régions, puis
  `ChunkMap.read`), hors du fil du serveur ; ce qu'il trouve arrive dans `TerrainService` par une file. Les chunks qui
  s'enregistrent ensuite sont relevés par `ChunkDataEvent.Save`. `TerrainSampler.fromSaved` et `explored` doivent
  rester identiques (même points, mêmes couleurs) : l'essai `leReleveSurDisqueVoitCommeEnJeu` y veille.
- Les cartes posent les royaumes sur `ClientTerrain.land` : `MapScreen`, `KingdomMap` (menu royal), `WarMapRenderer`.
  P63 : `land` prend d'abord la carte de Xaero (`client/map/XaeroCache`, ses images en cache sur le disque, dossier donné
  par `compat/xaero/XaeroWorldMapIds`), puis notre paysage, assombri là où Xaero n'a rien.
- Les titres en créatif : `TitleService.freeHand` (créatif + `WorldEditService.canEdit`), `grant`, `setRank`.

## Nations et habitants (P41 à P43)

- `core/nation/` : `NationDef` (un fichier JSON), `NationBook` (les lit dans `crusadecraft/nations/`), `NationService`
  (naissance, couronne, diplomatie du souverain, courrier), `NationScreen`. Les fichiers de Romain sont **hors du dépôt**
  (`src/main/resources/crusadecraft_nations/*.json`, comme les `.schem`) : ne suppose pas qu'ils existent, et ne les recrée pas.
- `core/city/` : `CityPeople` (noms de culture, fiches, personnages écrits d'avance), `CityVoice` (choisit un texte et remplit
  ses trous : `{ville}`, et les accords `{Né|Née}`), `CityTalk` (la conversation, accroupi + clic droit, et les souvenirs),
  `CityEggs`, `CityBubbles`, `CityWork` (la journée visible, près d'un joueur), `CityLife` (la journée en comptes, pour toutes
  les villes), `CityEconomy` et `CityRules` (logique pure, testée dans `src/test`).
- Les textes sont dans `data/crusadecraft/crusadecraft_repliques/*.json`. Un lot peut se réserver à des métiers, des traits, un
  âge, un sexe, une nation, une ville, une opinion : voir `Repliques.Filter`. Tout texte doit passer `aucunTexteATrou`.
- `core/city/CitySigns` et `block/BuildingSignBlock` : les enseignes, un bloc par `BuildingFunction`. Une fonction ajoutée
  demande son modèle, son `blockstate`, sa table de butin, sa recette et ses clés de langue (voir ceux d'une autre).
- `core/city/Furnishing` (ce qu'est un meuble, d'après son nom), `CitySeats` (s'asseoir), `CityFurniture` (recompter les meubles,
  le charme, la cloche, le comptoir, les lits). `Furnishing` est en logique pure et testé ; ajoute des mots là, pas ailleurs.
- `core/city/CityPanel` + `client/screen/CityPanelScreen` : le panneau d'une ville ; `CityNpc` + `NpcEditorScreen` : l'éditeur d'un habitant. Tout
  passe en NBT : une clé présente est une chose à changer, le serveur revérifie. `NpcSettings` et `CitySettings` ne s'enregistrent que s'ils
  diffèrent du défaut. `CityAdmin` : renommer, changer de camp, fonder ; `core/nation/NationFile` écrit le fichier d'une nation.
- `core/city/SkinService`, `SkinSources`, `client/skin/` : les skins. Le client télécharge, le serveur garde et redistribue.
- `core/city/CityScenes` : deux habitants face à face ; les comptes bougent dans `settle`, pas avant. `CityJournal` : l'onglet En direct, rien
  n'y est enregistré. `CityTravel` + `Trip` : voyages et caravanes, positions calculées ; une escorte a un `Trip.leader`. `CityPost` et
  `CityOrders` : le courrier, les commandes.
- `core/city/CityCommerce` (lignes, prix, plan de l'ordinateur), `CityLine`, `CityMarkets` (les villes sans murs : des `Site` hors de `sites()`, dans `world().markets()`),
  `CityRoads` (A* en logique pure, tracés dans `world().roads()`), `CityGuards` (les gardes se battent, rancune par ville).
- `core/city/WorldEconomy` et `client/screen/EconomyScreen` (l'écran d'économie, `WorldEconomyPackets` : `EconomyPackets` est celui du trésor), `CityTolls` (péage, douane), `CityBlockades` (villes coupées du monde),
  `CityContracts` + `CityContract`, `CityDocks` (le quai d'une colonie : un `Site` rangé avec les marchés, identifiant `idOf(ColonyRef)`), `CityGrowth` (la prospérité d'un marché).
- Pas de modèle de langage : Romain l'a écarté. Ne pas en ajouter sans qu'il le demande.
- Pas de bandits : Romain n'en veut pas.

## Pièges déjà rencontrés

- **GameTests qui durent plusieurs ticks** : après `world().clear()`, appeler `markGenerated`, sinon le monde se regénère pendant l'essai et remplace
  ce qu'on observe. Ne pas programmer une tâche depuis une tâche programmée (`runAfterDelay`, `onEachTick`) : tout planifier d'avance. Hors du gabarit, le
  sol du monde d'essai est plus haut que `w.center` : un villageois posé là étouffe. Regarder le journal (`died`) avant d'accuser le code.
- **Le tchat masqué** (une ancienne étape de `WorldShots`) fait refuser les commandes sans bruit : `Tchat désactivé`.
- **Le monde d'essai du vrai client est en paisible** : les monstres s'y effacent aussitôt, et un coup n'y fait aucun dégât. Passer en normal avant de juger un combat.
- **Un essai en jeu doit toujours finir par `w.cleanup()`** : sinon sa colonie et son royaume restent, et les essais suivants échouent en cascade, avec des messages qui n'ont rien à voir (« fondation impossible »).
- **Avant l'aube, tout le monde dort** : au début d'une série d'essais, l'heure du monde est tôt, et personne ne vend rien. Fixer l'heure dans l'essai.

- **MineColonies** : n'appeler son API que depuis `compat/minecolonies`. Certaines méthodes des entités de bâtiment ne
  compilent pas (classe de Domum absente du classpath de compilation) : passer par `IBuilding`. Un `Blueprint` de
  Structurize est indexé `[y][z][x]`.
- **Villageois** : côté serveur, `overrideOffers` ne fait rien ; pour poser des échanges, passer par les données
  enregistrées du villageois (`CityTrades.setOffers`). Un villageois posé dans un tronçon à peine chargé n'est
  trouvable par `level.getEntity` que quelques ticks plus tard (garder la référence rendue au moment de l'apparition).
- **GameTests** : le faux joueur (`FakePlayer`) ne bouge pas par `teleportTo` : utiliser `setPos`. Un essai qui pose
  des choses loin du gabarit doit charger son terrain et tout défaire à la fin.
- **Rendu** : la `BufferBuilder` de Minecraft 1.20.1 ne se libère pas ; en créer une par dessin perd de la mémoire.
  Le fantôme en garde une seule et prépare les grands dessins hors du fil de rendu.
- **Schématiques** : celles d'autres versions ou d'autres mods passent par `BlockEquivalents` (table
  `data/crusadecraft/equivalences.json`, surchargeable par `crusadecraft/equivalences.json` du jeu) ; au-delà de
  262 144 cases, les cases sont lues à la demande (`GridCells`, `GridPlacements`) pour ne pas remplir la mémoire.
- **Mémoire des plans** : `PlanLibrary` garde 160 plans ; une ville générée en utilise plus de deux cents différents.
- Les fichiers de langue font plus de 2 000 clés : chercher une clé existante avant d'en créer une.
