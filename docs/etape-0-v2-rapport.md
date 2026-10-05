# CrusadeCraft 2.0 — Étape 0 : audit et plan

*4 octobre 2026. Rien n'a été modifié dans ton instance MultiMC ni dans tes mondes. Tout ce qui touche
l'instance a été fait en lecture seule : liste des mods, `mods.toml`, signatures des classes lues avec
`javap` dans les jars installés. Aucune ligne de la 2.0 n'est écrite : j'attends ton accord.*

---

## 0. En bref

- **P14 est à l'abri.** Le commit `41652ed` est sur la branche `phase/p14-territoire`, sans push. Il
  contient le bâton de bornage, les constructions et la carte papier. Les 117 tests unitaires et les
  47 GameTests passent. Restent les captures en jeu, le guide et le passage en 1.3.0. Au passage, une
  règle `.gitignore` trop large (`build/`) cachait le dossier `core/build/` : il aurait manqué au
  commit. C'est corrigé.
- **Open Parties and Claims, Dynmap, BlueMap et squaremap ne sont pas dans ton instance.** Les claims
  de ta capture Xaero viennent d'ailleurs : Xaero sait afficher ceux d'OPAC, mais OPAC n'est pas
  installé chez toi.
- **Xaero n'a pas d'API publique pour colorer des chunks.** Le mécanisme existe, c'est lui qui
  affiche les claims d'OPAC, mais sa liste est verrouillée dès l'ouverture de la carte. Il faut
  **un mixin client, minuscule et confiné**. C'est faisable, mais fragile : je le dis.
- **Donner une colonie MineColonies en propriété à un PNJ est impossible proprement.**
  MineColonies exige un propriétaire joueur en ligne, et un faux propriétaire a déjà corrompu des
  mondes chez War 'N Taxes. Alternative : la colonie reste à toi, mais un PNJ la *gouverne*.
- **MCA fait déjà une bonne partie de la section « PNJ vivants ».** Il fournit l'éditeur
  d'apparence, l'arbre généalogique, les tombes, les humeurs, les traits et même la discussion par IA
  (avec clé API). On **ouvre ses écrans** au lieu de les refaire : il expose les ouvertures
  `VILLAGER_EDITOR` et `FAMILY_TREE`.
- **Doublon dans notre propre mod** : deux généalogies coexistent, ta maison (citoyens MineColonies,
  P4) et le registre des lignées (P13). La 2.0 doit les fondre en une seule, sinon le cycle de vie
  serait codé deux fois.
- **La sauvegarde avant migration est insuffisante aujourd'hui.** Le `.bak` est réécrit à chaque
  sauvegarde du monde : deux sauvegardes automatiques après une migration, l'original est perdu. La
  première phase de la 2.0 corrige ça, avec une copie datée et versionnée, jamais écrasée.

---

## 1. Mods de ton instance (158 jars, Forge 47.4.10)

### Ceux que la 2.0 concerne

| Mod | Version | Présent | Rôle pour la 2.0 |
|---|---|---|---|
| Minecraft Comes Alive Reborn | 7.6.16 (universal) | ✅ | PNJ incarnés (déjà relié, P13) |
| Architectury | 9.2.14 | ✅ | Requis par MCA |
| MineColonies | 1.1.1214 | ✅ | Villes, hôtel de ville, gardes (déjà relié) |
| Structurize / BlockUI / Domum Ornamentum | 1.0.811 / 1.0.194 / 1.0.296 | ✅ | Dépendances de MineColonies |
| Hundred Years War | 0.7.1r | ✅ | Unités, escouades, engins de siège (déjà relié pour les sièges) |
| Xaero's World Map | 1.39.9 | ✅ | Colorer les terres (mixin client) |
| Xaero's Minimap | 25.2.6 | ✅ | Idem, via la world map |
| **Open Parties and Claims** | — | ❌ | Absent |
| **Dynmap / BlueMap / squaremap** | — | ❌ | Absents ; **squaremap n'existe pas en Forge 1.20.1** (Fabric et Paper seulement) |

### Les autres, utiles à connaître

| Mod | Version | Pourquoi j'en parle |
|---|---|---|
| Custom NPCs (GBPort) | 2025-06-28 | Éditeur de PNJ, dialogues, factions : **doublon potentiel**, on ne refait rien de tout ça |
| Valarian Conquest | 4.2.1 | Soldats, citoyens, catapultes, béliers, pont-levis (MCreator) : doublon d'HYW, **non relié** |
| State Marks | 1.0.0 | Pièces de bronze, d'argent et d'or : monnaie toute trouvée pour le **commerce**, plus tard |
| Simple Shops, Small Ships, AstikorCarts | 1.2.2 / 2.0.0-b1.4 / 1.1.8 | Pour le commerce, plus tard |
| FTB Teams | 2001.3.1 | Équipes multijoueur ; pas nécessaire, nos royaumes en tiennent lieu |
| ClearLag Lite | 1.0 | Vérifié : il ne fait que `kill @e[type=item]`, donc il **ne touche pas** aux soldats ni aux PNJ (seulement au butin au sol) |
| Distant Horizons | 3.0.3 | Aucune API pour lire ses LOD : inutilisable pour notre carte |
| Sinytra Connector + Fabric API | beta.48 / 0.92.6 | Des mods Fabric tournent chez toi. Les mixins cohabitent, mais je ne m'appuierai sur aucun mod Fabric |
| WorldEdit | 7.2.15 | Les opérateurs passent outre nos protections |

**À signaler, sans y toucher (déjà vu à la première étape 0, toujours là)** :
- `$R5TO7LH.jar` est une seconde copie de Custom NPCs (même modid) ; c'est le nom d'un fichier
  restauré de la corbeille.
- Fusion est présent deux fois (1.2.12 et 1.2.7b).
- `Byzantine-1.21.1-51.jar` est bien un pack de styles MineColonies pour Forge, malgré son nom.

---

## 2. Solo et serveur multijoueur

Tout ce qui fait foi vit **côté serveur**, dans les données par monde. En solo, le serveur intégré
joue ce rôle, donc le même code sert aux deux.

- Les écrans, la surcouche de carte papier et le calque Xaero sont **client seulement**, derrière les
  gardes de distribution. Un serveur dédié ne les charge jamais.
- La carte web tourne **côté serveur**. En solo, elle tourne dans le serveur intégré, sur
  `localhost`.
- Le calque Xaero est **par joueur**. Chaque joueur qui a Xaero voit les terres, alimentées par les
  données du serveur. Le serveur, lui, n'a pas besoin de Xaero.
- **Nouveau à chaque phase** : un test de démarrage en **serveur dédié** (`runServer`, sans
  affichage). Il charge le monde, laisse passer quelques secondes et s'arrête. Il attrape les fuites
  de classes client, la faute classique qui fait planter un serveur.
- Les droits d'édition restent réglables (`TOUS`, `HOTE_ET_OPERATEURS`, `PERSONNE`). En multijoueur,
  chaque joueur a son royaume, et les relations de la 2.0 s'appliquent aussi entre joueurs.

---

## 3. Ce que chaque mod permet vraiment (vérifié dans tes jars)

**Xaero's World Map 1.39.9**
- Le paquet `xaero.map.highlight` contient `AbstractHighlighter`, `ChunkHighlighter` et
  `HighlighterRegistry`. Un `ChunkHighlighter` donne, par chunk, une couleur et deux infobulles :
  c'est exactement ce qu'il nous faut.
- **Mais** `WorldMapSession` crée le registre, n'y inscrit que celui d'OPAC, puis appelle `end()`,
  qui rend la liste non modifiable. Personne d'autre ne peut s'y inscrire.
- Solution : un mixin `@Pseudo` en tête de `HighlighterRegistry.end()`, une méthode de 3 lignes. Il
  inscrit notre surligneur juste avant le verrouillage.
- Si Xaero change cette classe, le mixin est ignoré : une ligne au journal, pas de plantage, et le
  calque disparaît simplement. Comme la minimap s'appuie sur ces surligneurs, une seule inscription
  suffit pour les deux cartes.
- Les points de passage (waypoints) n'ont pas d'API : on ne les touche pas.

**Hundred Years War 0.7.1r** (fermé, sans API, mais ses commandes serveur sont propres)
- Unités : `summon` avec propriétaire.
- Escouades : `squad_move`, `squad_attack_target`, `squad_follow`, `squad_form_move`,
  `squad_stop_follow`, `add_to_squad`, `attack_move`.
- Formations : `line`, `loose`, `line_loose`, `wedge`, `circle`, `tight`.
- Bascule de l'IA : `hyw_ai`. Relations : `hyw relation player set`.
- Le propriétaire d'une unité est déjà lu par `compat/hyw/Soldiers`. Les ordres du Maréchal passeront
  par ces commandes, exécutées par le serveur.

**MCA 7.6.16**
- Éditeur : `VillagerEditorScreen`, plus une version limitée. Arbre généalogique, tombes
  (`GraveyardManager`), humeur, traits, personnalité, grossesse.
- Dialogues en données (`Dialogues`), donc extensibles par datapack.
- Discussion par IA (`enableVillagerChatAI`, point d'accès et jeton à fournir).
- **Pas de vieillissement adulte ni de mort de vieillesse** : l'âge s'arrête à l'état adulte. Le
  cycle de vie sera donc le nôtre.
- `OpenGuiRequest` ouvre côté client `VILLAGER_EDITOR`, `FAMILY_TREE`, `INTERACT`… C'est une classe
  interne, isolée dans `compat/mca` comme le reste.

**MineColonies 1.1.1214**
- Rien de neuf par rapport à la première étude.
- La propriété d'une colonie exige un joueur en ligne (`setOwner`). La création aussi
  (`createColony(..., Player)`).
- Le bouton de l'hôtel de ville (P5) se pose sans mixin, par-dessus l'écran BlockUI.
- Tes mondes sont en `allowinfinitecolonies = false`, soit une colonie par joueur. C'est un réglage
  de monde : je n'y touche pas.

**Open Parties and Claims** (absent ; ce qui suit vient de sa documentation, à vérifier sur le jar
si tu l'installes)
- Son API serveur permet de poser des claims « serveur » rangés en sous-configurations, chacune avec
  un nom et une couleur, que Xaero affiche nativement.
- **Mais** OPAC protège ses claims à sa manière (groupes, alliés). Ce serait un **second système de
  protection** en concurrence avec celui de la section 2 de ta demande.

**BlueMap / Dynmap** (absents)
- Tous deux existent en Forge 1.20.1 et ont une API officielle de marqueurs : zones, points, fiches
  HTML au clic. C'est ce qu'il faut pour les frontières et les fiches.
- À vérifier sur le jar exact quand tu auras choisi lequel.

---

## 4. Doublons à éviter

| Tentation | Ce qui existe déjà | Décision |
|---|---|---|
| Éditeur d'apparence de PNJ | MCA (éditeur), Custom NPCs | On ouvre l'éditeur MCA ; notre éditeur ne gère que le registre (rôle, titres, liens, traits) |
| Arbre généalogique | MCA (`FAMILY_TREE`), notre registre P13, ta maison P4 | **Une seule généalogie** chez nous ; l'arbre MCA reste consultable |
| Dialogues, IA conversationnelle | MCA (dialogues en données, discussion IA), Custom NPCs | On ajoute des dialogues par datapack MCA ; pas d'IA maison |
| Protection des terres | MineColonies (permissions de colonie), OPAC si installé | La nôtre **s'efface** dans une colonie ; OPAC déconseillé |
| Soldats et engins de siège | HYW, Valarian Conquest, gardes MineColonies | HYW seul ; Valarian non relié ; les gardes restent à leurs postes |
| Carte détaillée au bloc près | Xaero | Notre carte reste **stratégique** (terrain estimé + politique), pas un second Xaero |
| Équipes | FTB Teams, équipes HYW | Les royaumes en tiennent lieu ; on reflète les relations dans HYW, comme pour les sièges |
| Lieux (2.0) vs sites, provinces, communautés | P12–P14 | Le Lieu **indexe** ces objets, il ne les recopie pas (§5) |

---

## 5. Architecture proposée

### 5.1 Le Lieu, colonne vertébrale de la 2.0

```
Lieu { id, genre, nom, parent (Lieu), tenancier (royaume | personne | joueur | aucun),
       dimension, ancre (BlockPos facultatif), renvois (province IA, site, colonie, communauté),
       fiche (texte libre que TU écris, bannière, couleur, date de fondation) }

genre : EMPIRE › ROYAUME › DUCHÉ › COMTÉ                  ← titres (de jure)
        VILLE · VILLAGE · AVANT_POSTE · FORT · LIEU_SAINT · RUINE · RÉGION   ← lieux posés
```

- **Le Lieu n'est pas un système de plus.** Les objets qui existent gardent leur mécanique et
  reçoivent un identifiant de Lieu : province IA, site de P14, communauté de P13, colonie MineColonies
  rattachée. Le **nom et la fiche vivent une seule fois**, dans le Lieu.
- **Territoire** : la carte des chunks (`TerritoryMap`, déjà alignée sur les chunks) pointera vers un
  **comté**. Le comté pointe vers son suzerain, et ainsi de suite jusqu'au royaume. Le royaume d'un
  chunk se déduit en remontant : plus de propriétaire stocké en double.
- **Migration v12 → v13** :
  - chaque province IA devient un Comté ;
  - chaque royaume devient le titre de son rang ;
  - chaque site devient le Lieu de son genre ;
  - les cases peintes d'un royaume sans province vont à un comté créé pour sa capitale.
  - Le nom d'un comté ainsi créé est **calculé**, jamais inventé : « Comté de *capitale* ».

### 5.2 Une seule personne

- `Person` (P13) devient le modèle unique. Son **corps** est au choix : un citoyen MineColonies (ta
  maison), un villageois MCA (incarné) ou rien (virtuel).
- La maison du joueur (P4) et les maisons du registre fusionnent.
- Migration v14 dans la phase des PNJ, avec la même sauvegarde datée.

### 5.3 Une source, plusieurs cartes

Un modèle serveur unique alimente cinq rendus, sans aucun calcul en double :
- notre carte ;
- la carte papier (P14) ;
- le calque Xaero, sur le client, avec les mêmes données synchronisées ;
- la carte web, sur le serveur ;
- les commandes `/crusade map`.

Ce modèle réunit les chunks, les Lieux, les sièges et les armées. Le passage des chunks aux contours
(polygones) est écrit une seule fois, et partagé par la carte web et notre carte.

**Synchronisation** : les terres partent aux clients **par différences**, région par région, avec un
envoi complet à la connexion. Le calque Xaero en a besoin en continu ; renvoyer toute la carte à
chaque bornage ne tiendrait pas en multijoueur.

### 5.4 Données et sécurité

| Fichier (dans le monde) | Contenu | Si on le perd |
|---|---|---|
| `data/crusadecraft.dat` (+ `.bak`) | Tout l'état, versionné | Grave : d'où les sauvegardes |
| `data/crusadecraft_backups/crusadecraft-v12-2026-10-04.dat` | **Nouveau** : copie avant chaque migration, jamais écrasée, 5 gardées | — |
| `data/crusadecraft_terrain.dat` | **Nouveau** : terrain échantillonné | Rien : régénéré |
| `data/crusadecraft_journal.dat` | **Nouveau** : blocs posés par les constructions et les civilisations | On ne peut plus annuler leurs chantiers |

- **Tests de non-régression des sauvegardes** : je construis les jars 1.0.0, 1.1.0 et 1.2.0 depuis
  leurs commits. Avec chacun, je génère un monde de test, puis j'en garde le `crusadecraft.dat` comme
  référence. Un test vérifie que chaque version se charge, migre et ne perd rien. C'est la preuve du
  « zéro perte ».
- **Modules de compatibilité isolés** : `compat/xaero`, `compat/opac`, `compat/bluemap` ou
  `compat/dynmap`, plus les existants. La garde de `build.gradle` est étendue : un import d'un mod
  optionnel hors de son module fait échouer la compilation.
- Chaque fonction a son interrupteur dans la configuration serveur, et chaque calque le sien dans la
  configuration client.

---

## 6. Plan par phases

Chaque phase suit la même méthode :
- une branche dédiée ;
- des tests unitaires et des GameTests ;
- le démarrage en serveur dédié ;
- des captures en jeu ;
- ta validation avant la suivante ;
- à la fin, quel jar copier et lequel supprimer. Je ne copie rien moi-même.

### P14 (fin) — Bâton de bornage, constructions, carte papier
- **Déjà codée et testée.** Restent les captures, le guide et la version 1.3.0.
- **Risque pour tes mondes** : les constructions posent de vrais blocs. Supprimer un site le retire
  du registre, **pas ses pierres** ; le journal de P15 permettra de les défaire.

### P15 — Fondations 2.0 : les Lieux et les sauvegardes
- **En jeu** :
  - l'écran « Lieux » montre l'arborescence Empire › Royaume › Duché › Comté › Ville… ;
  - chaque Lieu a sa fiche : tenancier, suzerain, population, villes, bannière, et un texte libre que
    tu écris ;
  - le bâton gagne un mode « Lieu » pour poser un lieu saint, une ruine ou une région ;
  - les noms des Lieux s'affichent sur la carte ;
  - `/crusade info` donne la chaîne complète, par exemple « Comté de X › Duché de Y › Royaume de Z ».
- **Faisable** : entièrement (données).
- **Risques** : la migration v12 → v13 de tes *données CrusadeCraft* (aucun bloc). Elle est protégée
  par une sauvegarde datée automatique et les tests de non-régression. Pour revenir en arrière : remettre l'ancien jar et la copie.
- **En plus** : le journal des blocs posés par les constructions (annulation d'un chantier), et le
  test de démarrage en serveur dédié.

### P16 — Le territoire façon Factions
- **Tout au chunk** (précisé le 5 octobre) : aujourd'hui, les cases peintes sont déjà des chunks, mais les
  terres automatiques (autour d'une ville, d'un fort, d'un poste de garde) sont des cercles. Elles
  deviennent des chunks : un chunk appartient au royaume dont le cercle contient son centre. La
  propriété, les protections, les messages d'entrée et **toutes les cartes** (celle du mod, la carte
  papier, Xaero, Dynmap) lisent alors la même chose, chunk par chunk, comme Factions. C'est un calcul,
  pas une réécriture des données : aucune migration.
- **Commandes** :
  - `/crusade claim` et `unclaim` sur le chunk où tu te tiens ;
  - `/crusade map` : une carte 11×11 des chunks voisins, colorée, dans le chat ;
  - `/crusade info`.
- **Règles en survie** : coût, contiguïté, plafond selon le rang et la population, distance à un
  Lieu. Tout est réglable. Le plafond s'affiche partout : « Terres : 2 076 / 2 500 chunks ».
- **Titres à l'entrée**, en grand et une fois par passage : « Comté de X — Royaume de Y ».
- **Protection selon la relation** : casser, poser, ouvrir un coffre ou frapper un animal dépendent
  du lien (suzerain, vassal, allié, neutre, ennemi en guerre). C'est réglable par Lieu.
- **Faisable** : oui, par les événements Forge.
- **Fragile** : les automates.
  - La protection ne vise **que les vrais joueurs** : ni les déployeurs de Create, ni les citoyens
    MineColonies, ni les faux joueurs.
  - Dans une colonie, les permissions MineColonies priment.
  - En guerre, la règle existante s'applique (casse par explosifs seulement, restauration à la paix).
  - Les opérateurs passent outre.
- **Risques** : aucun bloc modifié. Le seul danger est de t'enfermer avec un mauvais réglage : les
  opérateurs passent outre, et `/crusade protection off` coupe tout.

### P17 — Cartes externes : Xaero et carte web
- **En jeu, dans Xaero** :
  - la world map et la minimap colorent les terres par royaume ;
  - au survol, une infobulle donne « Comté de X, Royaume de Y » ;
  - la mise à jour est en direct.
- **Dans ton navigateur, avec Dynmap** (choisi le 5 octobre d'après ta capture « Duchy of France ») :
  - chaque chunk tenu est un carré à la couleur de son royaume, et les frontières suivent les bords
    des chunks, comme les claims de Factions ou de Towny sur Dynmap ;
  - un clic sur un royaume ouvre sa fiche : nom et titre (« Duché de… »), devise, souverain, trésor,
    foi et régime, population, date de fondation, « Terres : X / plafond chunks », conseil, vassaux ;
  - les Lieux (villes, forts, lieux saints) sont des repères cliquables, avec ta fiche ;
  - chaque ligne de la fiche se coupe dans les réglages. Sur un serveur, le trésor peut rester caché
    aux autres.
- **Faisable** :
  - Xaero, par le mixin client décrit au §3 ;
  - la carte web, par l'API de marqueurs de Dynmap (version Forge 1.20.1), à vérifier sur son jar
    au début de la phase.
- **Impossible ou fragile** :
  - Xaero sans API publique : si Xaero change, le calque s'efface proprement.
  - Les waypoints Xaero : pas d'API.
  - squaremap : pas de version Forge 1.20.1.
  - OPAC : absent. Je peux le synchroniser si tu l'installes, mais il apporte un second système de
    protection : **déconseillé**.
- **Risques** :
  - Aucun pour tes mondes (lecture seule).
  - La carte web fait un rendu initial lourd sur un grand monde moddé : du processeur et du disque,
    réglable par rayon.
  - Les blocs moddés apparaissent sans texture tant que ses packs ne sont pas réglés.
  - **Tu l'installes toi-même.** Moi, je télécharge le jar dans `libs/local` pour compiler : ce
    dossier est ignoré par git et rien n'est jamais redistribué.

### P18 — La carte du mod refaite et la carte de guerre
- **En jeu** :
  - **Un vrai terrain sur tout le monde, même inexploré** : océans, montagnes, forêts, déserts,
    relief ombré.
  - Les zones que tu as explorées s'affichent en couleurs réelles.
  - Des calques : politique, de jure, terrain, Lieux, armées, puis routes commerciales.
  - Un zoom fluide.
  - **La carte de guerre**, partagée avec tes alliés.
    - **Où l'éditer : dans le jeu**, à la Table de guerre (bouton « Carte de guerre ») ou sur la carte
      du mod (mode « Guerre »).
    - **Au chunk, comme le reste** : les zones se peignent au pinceau, chunk par chunk (offensive,
      défense, objectif). S'y ajoutent des flèches d'attaque, des lignes de front et des repères avec
      une note.
    - **L'état de la guerre se dessine tout seul**, chunk par chunk : terres occupées, places
      assiégées, chunks disputés.
  - **La carte de guerre est aussi sur Dynmap** (demandé le 4 octobre). Elle forme une couche « Carte
    de guerre » qu'on coche ou décoche, avec ses zones, ses flèches et ses repères cliquables, et se
    met à jour en même temps que le jeu.
  - **Pas d'édition depuis le navigateur.** La page de Dynmap ne fait qu'afficher. Une page d'édition
    web demanderait des comptes et des mots de passe : sur un serveur, ce serait une porte ouverte.
- **Faisable** : le serveur échantillonne le biome et l'altitude **sans générer de chunk**, comme il
  repère déjà les villages. Le calcul est étalé dans le temps et mis en cache.
- **Limites** :
  - L'inexploré est une **estimation** : ni tes constructions ni les structures n'y figurent.
  - Les biomes moddés sont colorés par catégorie, donc approximativement.
- **Risques** : aucun bloc ; un fichier de cache régénérable.
- **À savoir** : la carte web est visible de quiconque connaît son adresse. En solo, c'est toi
  seul. Sur un serveur, la couche « Carte de guerre » sera réglable : visible, ou réservée au jeu,
  pour que tes plans ne soient pas lus par l'ennemi.

### P19 — MineColonies : les villes rattachées
- **En jeu** :
  - le bouton de l'hôtel de ville ouvre « Rattacher cette colonie » : à quel comté, à quel royaume,
    avec quel gouverneur (une personne du registre) ;
  - plusieurs colonies deviennent plusieurs Villes du royaume, chacune avec ses revenus et ses
    levées ;
  - une ville peut être confiée à un vassal PNJ.
- **Impossible** :
  - Céder la colonie *en propriété MineColonies* à un PNJ (§0). Le PNJ la gouverne, et tu restes le
    propriétaire technique.
  - Avoir plusieurs colonies tant que `allowinfinitecolonies = false` : c'est à toi de changer ce
    réglage dans le monde de jeu.
- **Risques** : comme avant, on n'écrit dans MineColonies que du natif (rangs, bonheur à
  expiration).

### P20 — Les PNJ vivants et leur éditeur
- **En jeu** :
  - **Éditeur complet d'une personne** : nom, âge, rôle, titres, maison, parents, conjoint, traits et
    stats CK3, texte libre.
  - Deux boutons, « Apparence » et « Arbre », ouvrent les écrans de MCA.
  - **Routines** : la cour se réunit au donjon le jour et rentre le soir.
  - **Réactions** : fuite en guerre, fête au mariage, deuil.
  - De nouvelles répliques s'ajoutent par les dialogues de MCA (datapack).
- **Unification** : tes citoyens de maison et le registre ne forment plus qu'un (migration v14,
  sauvegarde datée).
- **Impossible** : une IA conversationnelle maison. MCA en a une (`enableVillagerChatAI`, clé API et
  internet requis) : on peut s'en servir, pas la refaire.
- **Risques** : les villageois MCA restent dans le monde. Si tu retires MCA, ils disparaissent : c'est
  le comportement de MCA, pas le nôtre.

### P21 — Le cycle de vie des dynasties
- **En jeu** :
  - **Âge et mort** : chacun vieillit (1 an = N jours, réglable) et meurt de vieillesse, de maladie
    ou au combat.
  - **Héritage** : titres (Lieux), or et objets passent aux héritiers.
  - **Lois de succession** : primogéniture, partage, élection, ancienneté, avec préférence homme,
    femme ou égale. Elles s'ajoutent comme **nouvelle loi** du royaume, dans le système de lois
    existant.
  - **Crises de succession** : les prétendants s'appuient sur les factions de P9.
  - **Arbre illustré** sur plusieurs générations.
- **Faisable** : oui, ce sont des données.
- **Fragile** : le portrait MCA dans l'arbre (rendu d'une entité MCA dans l'écran) dépend de son
  interne. En repli, on affiche le blason.
- **Impossible** : toi, tu ne vieillis pas. Tu es le joueur. Tes proches, tes vassaux et les
  souverains IA, eux, si.
- **Risques** : le partage peut scinder des royaumes IA. Il est réglable et désactivable.

### P22 — Les civilisations autonomes
- **En jeu** :
  - les royaumes IA vivent dans le monde ;
  - leurs villages grandissent : camp, puis village, puis bourg ;
  - ils posent tours et forts à leurs frontières et étendent leurs terres ;
  - ils fondent de nouveaux villages, peuplés de villageois MCA, et se disputent des Lieux.
- **Faisable** : avec les plans de P14, en chantiers étalés, **seulement dans des chunks chargés**.
  Ailleurs, le chantier attend et se rattrape au chargement.
- **Limites** :
  - pas de vraies colonies MineColonies pour l'IA ;
  - des constructions sur plans, pas une architecture libre ;
  - jamais sur tes blocs, dans une colonie ou sur une structure existante (terrain vérifié).
- **Risques** : **c'est la seule phase qui modifie ton monde sans geste de ta part.**
  - Elle sera **désactivée par défaut**, avec une zone autorisée réglable.
  - Chaque bloc posé est inscrit au journal, et une commande défait les chantiers.
  - Je te conseillerai de sauvegarder le monde avant de l'activer.

### P23 — Les levées et les armées physiques
- **En jeu** :
  - au **camp de rassemblement** (un site CAMP), tes levées apparaissent en vraies unités HYW, selon
    les levées calculées ;
  - le Maréchal, ou toi, donne ses **ordres depuis la carte** : marcher, attaquer, suivre, formation ;
  - les armées IA marchent, visibles ;
  - les **pertes sont réelles** : un mort, c'est un homme de moins dans sa province ;
  - à la démobilisation, les survivants rentrent.
- **Faisable** : par les commandes HYW confirmées (§3).
- **Limites** :
  - Les entités se figent dans les chunks non chargés. Hors de vue, une armée devient donc un pion
    sur la carte, qui se matérialise à ton approche.
  - Les effectifs sont plafonnés : 1 soldat représente N hommes.
  - Aucun chargement forcé de chunks.
  - Les gardes MineColonies restent à leurs postes : rien ne permet de les déplacer proprement.
- **Risques** :
  - Des entités dans le monde.
  - Sans HYW, le repli actuel s'applique (golems, pillards).
  - ClearLag Lite ne les supprime pas (vérifié).

### P24 — Finitions, performance et guide ultime
- Un écran de réglages complet, et `/crusade perf`, qui donne le temps passé par système et par
  tick.
- Les budgets par tick sont revus partout.
- Le guide est complet sous trois formes : le Codex en jeu, `GUIDE.md` et le guide claude.ai.
- **Ensuite (P25 et au-delà) : le commerce**, que tu avais annoncé. Ce sont des routes visibles sur
  la carte, avec les pièces de State Marks comme monnaie si tu veux.

---

## 7. Ce qui est impossible ou fragile, en une liste

| Point | Statut | Ce que je propose à la place |
|---|---|---|
| Colonie MineColonies possédée par un PNJ | **Impossible proprement** | Un gouverneur PNJ ; tu restes propriétaire |
| Colorer les terres dans Xaero | **Fragile** (pas d'API publique) | Un mixin client confiné, qui s'efface proprement |
| Waypoints Xaero | Pas d'API | Rien : nos Lieux sont sur nos cartes |
| squaremap | N'existe pas en Forge 1.20.1 | BlueMap ou Dynmap |
| OPAC et carte web | Absents de ton instance | Tu les installes, si tu le veux |
| Armées dans des chunks non chargés | Les entités s'y figent | Armées abstraites hors de vue |
| Terrain inexploré exact | Impossible sans générer les chunks | Estimation par biome et altitude |
| Portraits MCA dans l'arbre | Fragile | Repli sur le blason |
| IA conversationnelle maison | Hors de portée raisonnable | Celle de MCA (clé API) |
| Le joueur qui vieillit et meurt | Non | Ses proches, oui |
| Gardes MineColonies en campagne | Pas de moyen propre | Levées HYW |
| Lire les LOD de Distant Horizons | Pas d'API | — |

---

## 8. Questions avant de coder

1. **Carte web** — *réponse : BlueMap, comme recommandé* : **BlueMap** (recommandé : moderne, API de marqueurs propre, fiches au clic) ou
   **Dynmap** (l'aspect de ta capture « nations ») ? Dans les deux cas, tu l'installes toi-même. Je
   peux télécharger son jar dans `libs/local` pour compiler ?
2. **OPAC** : je recommande de **ne pas l'installer**, parce qu'il ferait doublon avec nos protections
   et que le calque Xaero direct suffit. D'accord ?
3. **Le mixin dans Xaero** (un seul point, côté client) : d'accord ?
4. **Protection des terres IA en survie** : *désactivée* par défaut (recommandé : tu explores
   librement, et casser chez une IA devient un incident diplomatique) ou *active* ?
5. **Ordre des phases** : celui-ci (les Lieux d'abord, car tout s'y accroche), ou veux-tu avancer
   une phase, par exemple les armées ?
6. **P14** : je la termine d'abord (captures, guide, 1.3.0) avant d'ouvrir P15 ?

---

## 9. Réponses de Romain (4 octobre 2026)

« Pour moi tout est bon, continue, fais ce que tu penses être le mieux. » Les recommandations deviennent donc
les décisions :

1. ~~BlueMap~~ **Dynmap** pour la carte web : Romain l'a précisé le 5 octobre, capture à l'appui (claims
   au chunk à la manière de Factions, fiche au clic). Je télécharge son jar dans `libs/local` pour
   compiler, sans jamais le redistribuer. Romain l'installe lui-même.
2. **Pas d'OPAC.**
3. **Mixin Xaero accepté.**
4. **Terres IA non protégées par défaut** en survie (réglable).
5. **Ordre des phases** : celui du §6.
6. **P14 terminée d'abord** (captures, guide, 1.3.0).

Ajout de Romain : **la carte de guerre doit aussi paraître sur la carte web**. C'est intégré à P18, en
couche de Dynmap (§6).

Précision du 5 octobre : **tout fonctionne au chunk**, comme Factions — terres, protections, cartes, et
carte de guerre (P16 à P18) ; la carte de guerre s'édite dans le jeu et s'affiche sur Dynmap.
