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
