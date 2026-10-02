# CrusadeCraft — Étape 0 : rapport d'étude

*2 octobre 2026. Aucun fichier de ton instance MultiMC ni de tes mondes n'a été modifié. Tout ce qui
concerne l'instance a été fait en lecture seule (liste des mods, lecture des `mods.toml` dans les jars,
réglages MineColonies des mondes).*

Dossier du projet : `E:\crusadecraft` (dépôt git initialisé, rien de commité). Les références sont dans
`E:\crusadecraft\reference\`, ignoré par git.

---

## 0. En bref

- **War 'N Taxes** est une mine d'or, mais pas une base à forker. Sous licence MIT, il compte environ
  55 000 lignes et de vrais audits de stabilité. Son code est conçu pour le **PvP multijoueur** : une
  guerre y exige deux joueurs en ligne, avec des vies. Ses données vivent dans `config/`, donc elles
  sont **partagées entre tous tes mondes**. On reprend ses formules, ses utilitaires robustes, son
  registre de dégâts et ses leçons. On réécrit l'architecture autour de données par monde et d'une
  guerre contre l'IA.
- **War 'n Nobility** : contrairement à ce qu'annonce la page CurseForge, **le code n'est pas
  public**. Le lien « Source » mène à un wiki (README, guide, images). On reprend ses **concepts**,
  qui sont bien documentés. Le décompiler est possible, mais je pose la question plus bas.
- **Hundred Years War** est installé chez toi (0.7.1r). Le mod est fermé (« All Rights Reserved »)
  et sans API publique. Il expose en revanche proprement trois choses : son **registre d'unités**
  (environ 100 types), ses **commandes serveur** (invocation avec propriétaire, escouades, ordres,
  relations) et un **système d'équipes et de relations**. L'intégration est réaliste par ces voies,
  plus la réflexion pour le reste, comme le fait War 'N Taxes.
- **MineColonies** : l'API de ta version (1.20.1-1.1.1214) expose tout ce qu'il nous faut. On y
  trouve les **11 compétences des citoyens** (lecture et ajout d'XP) et le bonheur. Elle donne aussi
  les liens familiaux (partenaire, enfants, parents), une base toute trouvée pour les dynasties. Les
  événements y figurent aussi, dont *« gestionnaire de colonies chargé »*, le signal qui manquait à
  War 'N Taxes. **Découverte** : MineColonies a désormais ses propres **connexions entre colonies**,
  avec un statut diplomatique natif (Alliés / Neutre / Hostile).
- **Trois points bloquants à trancher** avant toute ligne de code :
  - il n'y a pas de JDK 17 sur ta machine ;
  - **tous tes mondes n'autorisent qu'une colonie par joueur** ;
  - le choix du modèle des royaumes IA.

---

## 1. Ton environnement (constaté)

| Élément | Constat |
|---|---|
| Minecraft / Forge | 1.20.1 / **47.4.10** |
| MineColonies | **1.20.1-1.1.1214** (CurseForge file `7962889`, avril 2026) |
| Dépendances MC | Structurize 1.0.811, BlockUI 1.0.194, Domum Ornamentum 1.0.296. **Pas de MultiPiston, et c'est normal** : la 1.1.1214 ne l'exige plus (vérifié dans son `mods.toml`) |
| Hundred Years War | 0.7.1r (`hundred_years_war`) |
| Cartes | Xaero's Minimap 25.2.6 + **Xaero's World Map 1.39.9**. Ni JourneyMap, ni Antique Atlas |
| Utiles pour nous | Patchouli 84.1 (Codex), FTB Teams, Epic Knights (équipement des troupes), plusieurs mods de villages (Epic Villages, Towns and Towers, Villages & Pillages, MCA) |
| War 'N Taxes / War 'n Nobility | **Absents**, donc aucun conflit à gérer chez toi |
| SDM Economy | Absent. Monnaie par objet configurable par défaut |
| Java | **Seul un JDK 24 est installé**, et MultiMC lance l'instance en Java 24. Le développement Forge 1.20.1 (ForgeGradle 6, Gradle 8.8) exige un **JDK 17**. Gradle 8.8 est déjà dans ton cache, mais il ne démarre pas sous Java 24 |
| Réglage MineColonies des mondes | **`allowinfinitecolonies = false` dans tous les mondes** qui ont MineColonies (Edhellond, Osgiliath, Greek world 8, Imperialcity, Trydar's World of Norrath…), soit **une seule colonie par joueur** |

**À signaler, sans y toucher** : il y a des doublons dans `mods/`. Custom NPCs y figure deux fois
(`$R5TO7LH.jar` et `CustomNPCs-GBPort-Unofficial…jar`, même modid `customnpcs`), de même que Fusion
(1.2.12 et 1.2.7b). Enfin, `Byzantine-1.21.1-51.jar` porte un nom de version 1.21.1. Si l'instance
démarre bien, Forge s'en accommode, mais c'est une source possible de comportements bizarres.

---

## 2. Ce qui a été cloné

| Dossier | Contenu | Licence |
|---|---|---|
| `reference/war-n-taxes` | Branche `1.20.1`, version 5.0.9 (dernier commit : écritures atomiques). 209 fichiers Java, ~55 000 lignes, 13 tests JUnit, audits (`audit/`, `reviews/`), wiki | **MIT** |
| `reference/minecolonies` | Branche `release/main`. C'est la vraie ligne 1.20.1 (`release/1.20` est figée depuis 2023). Tête : 1.1.1276 | GPL-3.0 |
| `reference/war-n-nobility-wiki` | Le dépôt que la page CurseForge présente comme « Source » : README, guide HTML/PDF, images. **Aucun code** | aucune déclarée (le README dit GPL-3.0) |

Hundred Years War n'a pas de dépôt public. Je l'ai étudié en lisant le jar installé (noms des
classes, langue, `mods.toml`), sans rien extraire ni modifier.

---

## 3. War 'N Taxes : ce qu'on reprend

### 3.1 Verdict par système

| Système (fichiers WnT) | Verdict | Pourquoi |
|---|---|---|
| `ColonyBuildingUtil` (accès aux bâtiments par réflexion) | **Reprendre** | MineColonies a renommé `getBuildingManager` en `getServerBuildingManager`, ce qui a fait planter les joueurs |
| `ColonyLookup` (recherche de colonie tenant compte de la dimension) | **Reprendre** | Les identifiants de colonie ne sont uniques **que par dimension** |
| `TickScheduler` (tâches différées sur le thread serveur) | **Reprendre** | Jamais de `Timer` ni de thread : tout reste sur le thread principal |
| Formule d'impôt de `TaxManager` | **Reprendre** en fonction pure testée | Par bâtiment : (base + bonus × niveau) × multiplicateur de bonheur × modificateurs (fatigue de guerre, politique fiscale, événements), avec plancher, entretien des bâtiments militaires, dette plafonnée |
| `TreasuryManager` (War Chest, ponction pendant la guerre) | **Adapter** | Devient le trésor de la couronne et le trésor de guerre |
| Politiques fiscales | **Adapter** en « lois fiscales » | Taxer plus coûte du bonheur : exactement l'arbitrage voulu |
| `WarBlockLedger` (instantané des blocs détruits par explosion, NBT des coffres et panneaux, restauration étalée, survie au redémarrage, nettoyage des orphelins) | **Reprendre presque tel quel** | C'est le joyau du mod, testé contre les cas tordus (arrêt pendant une restauration, fichier corrompu…) |
| `BlockInteractionFilterHandler` (en guerre : casse uniquement par explosifs) | **Reprendre** | Avec ses raccourcis de performance |
| `WarExhaustionManager` (fatigue de guerre) | **Reprendre** | |
| `PeaceProposalManager` (paix blanche, réparations, reddition) | **Adapter** en traités | |
| `OccupationManager` (occupation fiscale) | **Adapter** | L'occupation détourne les impôts **sans transférer la propriété** : le bon modèle contre l'IA |
| `VassalManager` (tribut en %) | **Adapter** en contrats de vassalité | |
| Raids, milice, mercenaires (`RaidManager`, `CitizenMilitiaManager`, `MilitiaSpawner`) | **Adapter** | Pour les raids de l'IA sur tes colonies |
| Objectifs de siège (planter la bannière, démolir l'Hôtel de ville) | **Adapter** | Ils deviennent les objectifs de siège de nos guerres |
| `SpyManager` (missions, intel par paliers, entité espion qui fuit) | **Adapter** | Pour le Maître-espion |
| `RandomEventManager` | **Remplacer** | Par notre moteur d'événements en JSON/datapack. On garde les idées (festival, peste, grève, désertion…) |
| `HywCompat` (réflexion, propriétaire lisible hors ligne) + protection anti tir-ami | **Reprendre** | Déjà vérifié par l'auteur sur HYW 0.3.8 → 0.6.4 |
| `CurrencyService` / SDM Shop | **Adapter** | Objet configurable, plus SDM Economy optionnel |
| `FtbTeamsCompat` | **Reprendre** si multijoueur | |
| `WarSystem` (4 430 lignes) | **Réécrire** | Pensé pour joueur contre joueur (vies, présence des deux camps en ligne, choix de camp). On en garde les phases et la paix |
| Arène PvP, base MySQL de stats, désactivation de recettes, colonies abandonnées, Easy Factions, LuckPerms | **Laisser** | Hors sujet |
| Les deux « gardes » de `build.gradle` | **Reprendre** | Ils font échouer la compilation sur les erreurs qui ont déjà touché des joueurs : appel direct à l'API des bâtiments, comparaison numérique des rangs, recherche de colonie hors dimension, import d'un mod optionnel hors de sa classe de compat |

### 3.2 Leçons de stabilité (changelog, audits, `OPTIMIZATION_AUDIT`, `PORTING_NOTES`)

1. **Jamais d'écriture sur propriétaires ou permissions pendant le chargement.** Leur réparation
   automatique tournait dès `ServerStartingEvent`, avant que MineColonies ait fini de charger les
   colonies. Une colonie montrant un propriétaire nul *le temps du chargement* recevait un faux
   propriétaire `[AUTO_OWNER]`, ce qui **corrompait le monde pour de bon**, rétrogradation comprise.
   → Chez nous, **aucune** écriture sur les permissions sans action explicite de ta part, et
   seulement après l'événement MineColonies `ColonyManagerLoadedModEvent`.
2. **Jamais de faux propriétaire.** Dans ta version, `setOwner` n'accepte plus qu'un **joueur en
   ligne** (l'ancien `setOwner(UUID)` a disparu). Un propriétaire synthétique ou hors ligne est
   impossible proprement, ce qui pèse lourd dans le choix des royaumes IA (§7).
3. **Argent atomique.** Les impôts étaient retirés de la colonie *avant* la livraison, sans
   remboursement en cas d'échec, et des pièces disparaissaient pendant les guerres. Il a fallu deux
   versions (5.0.7, 5.0.8) pour y remédier. → On débite *après* une livraison réussie, sinon on
   rembourse ; chaque transfert est une paire débit/crédit vérifiée, avec des tests d'invariants.
4. **Écritures atomiques** (fichier temporaire puis renommage) : un plantage pendant la sauvegarde
   effaçait l'état du mod.
5. **Données par monde.** WnT écrit tout dans `config/warntax/`, un dossier commun à **tous les
   mondes de l'instance** : avec tes 20+ mondes, ce serait un vrai problème. → `SavedData` par monde
   (`saves/<monde>/data/crusadecraft_*.dat`), versionné, avec migrations et écriture atomique.
6. **Identifiants de colonie uniques par dimension seulement** : la clé est (dimension, id).
7. **Les rangs MineColonies se lisent à l'envers** (Propriétaire = 0 … Hostile = 4, rangs
   personnalisés à partir de 5) : utiliser les accesseurs nommés, jamais `>=`.
8. **`getOwner()` peut être nul** sur une colonie abîmée : écrire `uuid.equals(getOwner())`.
9. **Mods optionnels** : un `import` hors de la classe de compat provoque un `NoClassDefFoundError`
   au chargement quand le mod est absent.
10. **API MineColonies mouvante** (bâtiments, `setOwner`, dépendances Structurize obligatoires) :
    compiler contre la version exacte de ton instance, avec un shim pour le reste.
11. **Performance** :
    - pas de sauvegarde par colonie dans une boucle, qui coûtait O(N²) écritures par cycle ;
    - pas de parcours de toutes les colonies toutes les 5 secondes ;
    - sortie immédiate dans les événements fréquents (dégâts, casse de blocs) ;
    - toute tâche répétée garde son identifiant pour être annulée (une fuite par guerre a été
      trouvée).

---

## 4. War 'n Nobility : ce qu'on reprend (d'après le wiki)

| Concept | Verdict |
|---|---|
| Échelle de rangs **pilotée par la config** (nom ; type d'exigence ; paramètre), forme féminine de chaque titre | **Reprendre le principe** pour Baronnie → Comté → Duché → Royaume → Empire, avec des exigences typées (colonies contrôlées, or, prestige, Couronne) |
| Sceaux = serments de fidélité, monnaie de l'ascension | **Adapter** : chez nous, ce sont les serments des seigneurs PNJ |
| Durée minimale de vassalité (7 jours par défaut) | **Adapter** en durée minimale d'un contrat |
| « Pas de guerre contre un supérieur » | **Reprendre** (config) |
| Renier son suzerain, c'est être déclaré hostile dans ses colonies | **Adapter** : la révolte d'un vassal PNJ |
| Domaines agrégés le long de l'arbre vassalique, nom au centre | **Reprendre** pour la vue de jure de la carte |
| Chancery Table (console filtrée selon le rang) | **Adapter** : un bloc qui ouvre les écrans royaume et conseil |
| Couronne : objet consommé au couronnement | **Reprendre** pour la fondation et l'élévation des titres |
| War Table (grille 3×3 de cadres + atlas, couche de commandement HYW) | **Plus tard** (P7, optionnel) : c'est la partie la plus coûteuse |
| Easy Factions, Antique Atlas | **Laisser** : l'auteur a lui-même retiré Antique Atlas en 1.1.0 |

Leur modèle est **centré joueurs** (des joueurs nobles se prêtent serment), le nôtre **centré PNJ**.
C'est pour cela que les concepts suffisent en grande partie.

---

## 5. MineColonies : points d'accroche (vérifiés dans ton jar 1.1.1214)

**Colonies.**
- `IColonyManager` : `getAllColonies`, `getColonies(level)`, `getColonyByPosFromWorld`,
  `getIColonyByOwner`.
- `IColony` : `getID`, `getName`, `getCenter`, `getDimension`, `getTeamColonyColor`, `getColonyFlag`
  (pour la bannière), `getPermissions`, `getOverallHappiness`, `getCitizenManager`,
  `getServerBuildingManager`, `getDay`, `getResearchManager`, `getRaiderManager`.

**Hôtel de ville et territoire.**
- Le niveau de l'Hôtel de ville passe par le gestionnaire de bâtiments.
- Chaque bâtiment donne `getBuildingLevel`, `isBuilt` et `getClaimRadius(niveau)`. On **recalcule
  le territoire depuis les bâtiments**, sans scanner de chunks, et on le met en cache, invalidé par
  les événements de construction.

**Citoyens (le cœur des PNJ dirigeants).**
- Identité : `getId`, `getName`, `isFemale`, `isChild`, métier, lieu de travail, maison.
- **Famille** : `getPartner`, `getChildren`, `getSiblings`, `getParents`. Les dynasties, les
  mariages et la succession peuvent s'appuyer sur la vraie généalogie MineColonies.
- **Compétences** : `getCitizenSkillHandler().getLevel(Skill)` et `addXpToSkill(skill, xp, citoyen)`,
  méthode officielle pour « le rôle fait progresser ».
- Bonheur individuel : `getCitizenHappinessHandler().getHappiness(colonie, citoyen)`.

**Les 11 compétences** (niveaux 1 à 99) : Athlétisme, Dextérité, Force, Agilité, Endurance, Mana,
Adaptabilité, Concentration, Créativité, Connaissance, Intelligence. MineColonies les organise en
**paires complémentaires** : Athlétisme↔Force, Dextérité↔Agilité, Endurance↔Connaissance,
Mana↔Concentration et Adaptabilité↔Créativité. Intelligence reste seule. Un nouveau citoyen tire ses
niveaux de départ entre 1 et le bonheur de la colonie (environ 10), il faut donc un barème qui
distingue bien les débutants.

**Correspondance compétences → stats CK3** (deux versions, entièrement configurables) :

| Stat | Ta proposition | Variante « paires MineColonies » (recommandée) |
|---|---|---|
| Diplomatie | Adaptabilité + Créativité | Adaptabilité + Créativité |
| Martial | Force + Agilité (+ Endurance) | **Force + Athlétisme** + ½ Endurance |
| Intendance | Intelligence + Connaissance | Intelligence + Connaissance |
| Intrigue | Dextérité + Concentration | **Dextérité + Agilité** |
| Érudition | Connaissance + Mana | **Mana + Concentration** + ½ Connaissance |

Pourquoi la variante ? Dans MineColonies, **Force et Agilité sont « adverses »** : un citoyen fort
dans l'une tend à être faible dans l'autre, si bien que leur somme aplatirait la stat Martial pour
tout le monde. La variante additionne des paires *complémentaires*, ce qui fait ressortir les
profils. Elle utilise aussi les 11 compétences. Échelle proposée : somme ÷ 4 environ, soit 2 à 5
pour un citoyen débutant et 25 à 30 pour un expert, comme dans CK3.

**Bonheur.** `IColony.getOverallHappiness()` et les modificateurs à expiration natifs (ceux que WnT
utilise pour ses effets). Les décisions du conseil peuvent agir sur le *vrai* bonheur MineColonies.
Leur comportement si l'on retire le mod reste à valider en P5.

**Événements MineColonies.**
- `ColonyManagerLoadedModEvent`, le signal « tout est chargé » ;
- colonie : créée, supprimée, renommée, drapeau changé, rang d'un joueur changé ;
- bâtiment : ajouté, retiré, construit ;
- citoyen : ajouté, **mort**, retiré, changement de métier, avec succession automatique ;
- joueur entrant ou sortant de la colonie.

**Permissions.** Rangs nommés (Propriétaire, Officier, Ami, Neutre, Hostile) et `setPlayerRank`.
Marquer un ennemi « Hostile » dans tes colonies pendant une guerre est réversible.

**Interactions des citoyens.** `triggerInteraction(…)` déclenche la bulle « ! » avec des réponses au
choix, déjà utilisée par MineColonies pour ses demandes. C'est un support possible pour les messages
des conseillers, à condition de n'utiliser que les types natifs, pour que retirer CrusadeCraft ne
laisse rien d'illisible dans les données des citoyens.

**★ Connexions entre colonies.** MineColonies a ajouté en 2025 des liaisons par **Gate House**
(`IColonyConnectionManager`), avec un **statut diplomatique natif** `ALLIES / NEUTRAL / HOSTILE`,
des événements de connexion et la liste des colonies connectées directement ou indirectement. C'est
présent dans ta 1.1.1214. Notre diplomatie peut **lire et refléter** ce statut au lieu d'en inventer
un concurrent.

**Création de colonie.** `createColony(niveau, pos, Player, …)` exige un **joueur**, ce qui confirme
qu'une colonie gérée par l'IA demanderait un faux joueur.

---

## 6. Hundred Years War : options

**Ce qu'il expose proprement :**
- **Registre d'entités** (~100 types) :
  - fantassins : archer, crossbowman, spear_man, shieldman, warrior, militia, handgonne_man,
    matchlock_man ;
  - cavalerie : cavalry, mounted_lancer, mounted_light_lancer, mounted_archer ;
  - engins de siège : battering_ram, siege_tower, trebuchets, mangonels, springald, bombard,
    great_bombard, cannon, culverin, ribauldequin ;
  - factions intégrées : bandits, pillards du désert, morts-vivants, elfes ;
  - ouvriers.
- **Commandes serveur** : invocation d'unités avec propriétaire, escouades (`squad_move`,
  `squad_attack_target`, `squad_follow`, formations), relations entre joueurs et équipes, bascule de
  l'IA.
- **Équipes et relations** (création, candidatures, relations entre équipes).
- **Propriétaire d'une unité** : lisible même hors ligne (méthode SRG `m_21805_`, établie par WnT).

**Options :**

| Option | Contenu | Risque |
|---|---|---|
| A. Minimale | Protection anti tir-ami (reprise WnT), unités HYW comptées dans le score de guerre | Faible |
| B. Levées réelles | À l'appel aux armes, les vassaux et le Maréchal font apparaître de vraies unités HYW (registre, propriétaire), en nombre égal aux levées calculées (population, contrat, opinion). Le général PNJ donne des bonus. Les armées IA sont aussi en HYW | Moyen : une mise à jour d'HYW peut casser la réflexion, d'où une désactivation propre et un message |
| C. Pilotage IA | Les royaumes IA donnent leurs ordres par les commandes HYW (marcher, assiéger) et s'appuient sur ses relations d'équipes | Moyen à élevé |

**Recommandation** : A en P6, B en P6/P11, C en P11 si B tient. Sans HYW, le mod reste complet :
gardes MineColonies pour toi, soldats de repli pour l'IA (mercenaires MineColonies, que WnT utilise
déjà, ou une entité simple équipée d'Epic Knights si présent).

---

## 7. Royaumes IA : les options concrètes

| # | Option | Avantages | Risques |
|---|---|---|---|
| 1 | **Vraies colonies MineColonies pour l'IA** | Visuellement parfait | Exige un propriétaire joueur, donc un faux joueur ou un UUID synthétique : **exactement ce qui a corrompu des mondes chez WnT**. Les colonies ne tournent que chargées. L'API ne prévoit aucune construction automatique. Très lourd. **Déconseillé** |
| 2 | **Équipes HYW comme royaumes** | Armées réelles, relations déjà gérées | Ni économie, ni territoire, ni personnages ; n'existe que si HYW est présent. **En complément, pas en base** |
| 3 | **Royaumes abstraits** (données pures) | Zéro écriture MineColonies, robuste, performant. Chacun a un territoire (zones de chunks), une capitale, des provinces, une population et une économie simulées, un souverain et un conseil PNJ virtuels (mêmes stats et traits que les tiens) | Risque de « royaumes fantômes » si rien n'est visible dans le monde |
| 4 | **★ Hybride (recommandé)** : 3 + un **ancrage dans le monde** | Chaque province IA est accrochée à un vrai lieu : un village généré (tu en as beaucoup grâce à Epic Villages, Towns and Towers…) ou un « siège » posé par le mod (bannière et petite structure). On découvre un royaume en approchant de ses villages : c'est le brouillard de guerre. Assiéger, c'est attaquer ce lieu (objectifs repris de WnT). Une province conquise devient la tienne ; si tu y fondes une colonie, **tu en es le vrai propriétaire**. Si l'IA conquiert une de tes colonies, c'est une **occupation fiscale**, jamais un transfert de propriété | Plus de travail sur la génération et la découverte des sièges |

Les armées IA se matérialisent seulement quand c'est utile : émissaires à ta cour, troupes qui
marchent vers tes frontières (HYW ou repli), garnisons pendant un siège. Les décisions de l'IA
tournent dans une tâche espacée et configurable, jamais à chaque tick.

---

## 8. Cartes

- **Écran carte maison** (cœur, P7) : rendu depuis nos données, avec vues politique et de jure,
  capitales, villes, armées connues, statut au survol et brouillard. Il ne dépend d'aucun mod de
  carte.
- **Xaero's World Map** (installé chez toi, donc prioritaire) : son paquet `xaero.map.highlight`
  (`ChunkHighlighter`, `HighlighterRegistry`) est le mécanisme qui sert à colorer les claims sur la
  world map. On peut y peindre nos territoires. Il n'est pas documenté officiellement : il sera
  isolé dans `compat.xaero`, avec désactivation propre.
- **JourneyMap** : API officielle et propre (MineColonies s'en sert déjà), mais pas installé chez
  toi. Optionnel, basse priorité.
- **Antique Atlas** : en 1.20.1, c'est un mod Fabric, et War 'n Nobility l'a abandonné. **Je
  propose de l'écarter.**
- **War Table murale** : fin de P7, optionnelle.

---

## 9. Autres pièges repérés pour CrusadeCraft

- **Une colonie par joueur** dans tous tes mondes (voir les questions).
- **Retirer le mod ne doit rien casser.** On n'écrit dans MineColonies que des choses *natives* :
  rangs, XP de compétences, modificateurs de bonheur à expiration. Tout le reste vit dans nos
  fichiers.
- **Un dirigeant est un citoyen** : il peut mourir, déménager, être renommé ou disparaître avec une
  colonie supprimée. La clé est (dimension, colonie, citoyen), avec une **réconciliation au
  chargement** qui déclenche la succession si un dirigeant manque.
- **`SavedData` de Minecraft 1.20.1 n'écrit pas de façon atomique** : on surcharge l'écriture pour
  passer par un fichier temporaire.

---

## 10. Esquisse d'architecture (détaillée à l'étape 2)

```
fr.romain.crusadecraft
├─ api/            types publics (royaume, titre, personnage, événements CrusadeCraft)
├─ core/           realm, title, character, council, opinion, faction, economy, war, diplomacy, ai, event
│  └─ logic/       logique pure sans Minecraft (stats, opinion, impôts, score de guerre, IA) → JUnit
├─ data/           SavedData versionné + migrations + écriture atomique
├─ network/        paquets
├─ client/         écrans : fiche personnage, conseil, royaume, diplomatie, carte
└─ compat/         minecolonies, hyw, xaero, journeymap, ftbteams, sdm, patchouli, conflits (WnT/WnN)
```

Build : ForgeGradle 6, Gradle 8.8, Parchment `2023.09.03-1.20.1`, toolchain Java 17, MineColonies
via Curse Maven. En développement, on lance avec **exactement tes versions**
(MineColonies 1.1.1214, Structurize 1.0.811, BlockUI 1.0.194, Domum 1.0.296).

---

## 11. Questions à trancher

1. **JDK 17.** Proposition : je télécharge un JDK 17 *portable* (Eclipse Temurin, archive zip de
   ~190 Mo), je le dézippe dans `E:\java\` (vide aujourd'hui) et seul ce projet y fait référence
   (`org.gradle.java.home`). Ni installeur, ni PATH, ni changement global. D'accord ?
2. **Version de MineColonies.** On compile contre **ta 1.1.1214** (recommandé : rien à changer dans
   ton instance), ou on passe à la 1.1.1276, ce qui t'obligerait à mettre à jour Structurize et les
   autres dans ton instance ?
3. **Plusieurs colonies.** Tes mondes n'en autorisent qu'une par joueur. Trois possibilités :
   - tu actives `allowinfinitecolonies` toi-même dans le monde de jeu ;
   - les provinces viennent surtout de la conquête de provinces IA ;
   - les deux (ma recommandation).
4. **Royaumes IA.** D'accord pour l'option hybride (§7) ? Les sièges IA s'accrochent-ils aux
   **villages existants**, à des **sièges générés par le mod**, ou aux deux ?
5. **Compétences → stats.** Ta correspondance ou la variante « paires MineColonies » ?
6. **Messages des conseillers.** Trois possibilités :
   - un **courrier maison** (boîte aux lettres dans l'écran du conseil + message cliquable dans le
     chat) ;
   - la **bulle « ! » native** de MineColonies au-dessus du citoyen ;
   - les deux (ma recommandation : le courrier pour tout, la bulle pour les requêtes urgentes).
7. **War 'n Nobility.** Je décompile le jar Forge 1.20.1 (`warnnobility-forge-1.0.8-1.20.1-java17.jar`,
   129 Ko, CurseForge) pour lire son code, ou le wiki suffit ? Mon avis : le wiki suffit, parce que
   son modèle est centré joueurs. Mais c'est ton choix.

Une fois ces points validés, je passe à l'étape 2 : le plan par phases, avec ce qui sera jouable à la
fin de chacune.
