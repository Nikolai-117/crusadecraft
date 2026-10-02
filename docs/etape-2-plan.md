# CrusadeCraft — plan par phases

*2 octobre 2026. Ce plan intègre le rapport d'étape 0, les décisions (`decisions.md`) et tes demandes :
tout se joue en jeu, avec blocs, objets, GUI et PNJ vivants ; tournois, croisades, mariages et
chroniques.*

## Pourquoi cet ordre

Tu joues en solo, avec une colonie par monde. Les vassaux, la diplomatie et les conquêtes n'ont donc
de sens qu'**une fois les royaumes IA en place**. L'ordre est conçu pour que chaque phase apporte
quelque chose de jouable :

1. ton royaume ;
2. ses personnages ;
3. leur conseil ;
4. son économie ;
5. le monde qui l'entoure ;
6. la diplomatie ;
7. la guerre ;
8. l'IA qui vit d'elle-même ;
9. les intrigues ;
10. la vie de cour ;
11. les finitions.

## Règles communes à chaque phase

- `./gradlew build` réussi, tests unitaires verts, et lancement en `runClient` sans erreur, avec les
  versions exactes de ton instance (MineColonies 1.1.1214…).
- Un commit sur la branche de la phase, sans push.
- Un résumé : quoi crafter, où le poser, quoi essayer.
- Ton instance et tes mondes restent intouchés. Le jar n'est copié que sur ta demande.

## Blocs et objets : déblocage

Chaque bloc a une recette. Son **usage** dépend de la progression MineColonies : niveau de l'Hôtel de
ville de la capitale, puis, à partir de la P4, des **recherches de gouvernance** ajoutées à
l'Université par datapack. Les niveaux ci-dessous sont des valeurs par défaut, toutes réglables.

| Bloc / objet | Phase | Condition d'usage (défaut) |
|---|---|---|
| Trône royal | P1 | Posé dans l'Hôtel de ville de ta colonie, Hôtel de ville niveau ≥ 2 |
| Registre du royaume | P1 | Avoir un royaume |
| Sceptre | P2 | Avoir un royaume |
| Table du Conseil + Sièges du conseil | P3 | Hôtel de ville ≥ 3 |
| Bureau du Maire | P3 | Dans une colonie du royaume |
| Coffre du Trésor | P4 | Avoir un royaume |
| Table de guerre (cadres muraux 3×3) | P5 (carte), P7 (guerre) | Hôtel de ville ≥ 3 |
| Borne de frontière | P5 | Décorative |
| Colombier royal | P6 | Avoir un royaume |
| Chancery Table, Sceau royal, Parchemins de revendication | P6 | Recherche « Chancellerie » |
| Lice de tournoi | P10 | Recherche « Chevalerie » |
| Chroniques | P9 | Avoir un royaume |

---

## P1 — Fondation du royaume

- Projet Forge 1.20.1 complet :
  - config TOML (chaque système activable) ;
  - données par monde versionnées, avec écriture atomique ;
  - pont MineColonies en lecture seule, actif après le chargement complet ;
  - réseau ;
  - langues fr_fr et en_us ;
  - tests JUnit.
- **Trône royal**. Clic droit dans l'Hôtel de ville de ta colonie : un **écran de fondation**
  demande le nom du royaume, la devise, le nom de la maison/dynastie et la couleur. La bannière est
  le drapeau de la colonie. Le royaume naît au rang de **Baronnie**, avec cette colonie pour capitale.
- **Menu royal** façon CK3, ouvert par le trône ou par la touche **K**. Il a huit onglets : Royaume,
  Conseil, Vassaux, Diplomatie, Trésor, Guerre, Lois, Chroniques.
  - L'onglet **Royaume** fonctionne : rang, capitale, niveau de l'Hôtel de ville, population,
    bonheur, ressources (or, prestige, piété, crainte), colonies du royaume.
  - Les autres onglets affichent un état vide soigné en attendant leur phase.
- **Registre du royaume** : livre de poche qui résume l'état du royaume.
- **Notifications** façon CK3 en haut de l'écran (infrastructure, puis « Royaume fondé »).
- Réconciliation : si la capitale disparaît ou change de propriétaire, le royaume se met en sommeil
  sans rien casser.
- Détection de War 'N Taxes et War 'n Nobility, avec avertissement.
- `/crusade` pour le debug et l'admin : info, liste, dissoudre.

**Jouable** : crafter le trône, le poser dans ton Hôtel de ville, fonder et nommer ton royaume,
l'ouvrir avec K, consulter le registre.

## P2 — Les personnages

- Chaque citoyen a une **fiche CK3** :
  - les 5 stats calculées depuis ses compétences MineColonies (elles évoluent avec elles) ;
  - une **éducation** déduite de ses stats ;
  - des **traits de personnalité** (ambitieux, loyal, avare, généreux, brave, lâche, cruel, juste,
    paranoïaque, honnête, fourbe…), stables pour un citoyen donné ;
  - des traits acquis (vétéran, blessé, corrompu…) ;
  - une **opinion** envers toi (−100 à +100) faite de modificateurs datés qui s'estompent ;
  - du **stress**.
- **Sceptre** : clic droit sur un citoyen pour ouvrir sa fiche ; accroupi + clic droit pour lui
  proposer un poste.
- **Titre au-dessus de la tête** des dirigeants (« Aldric, Maréchal de Valbrume »), affiché par
  nous, sans renommer le citoyen.
- **Dialogue** : clic droit (sans sceptre) sur un dirigeant ouvre un choix d'actions (donner un
  ordre, récompenser, réprimander, exiler, emprisonner, ou « ouvrir la fenêtre MineColonies »).
- Mort ou disparition d'un dirigeant : il est retiré proprement et le poste se libère, avec une
  notification. La succession complète vient avec le conseil.
- Tests : calcul des stats, opinion et estompage, attribution déterministe des traits.

**Jouable** : prendre le sceptre, inspecter tes citoyens, découvrir leurs traits et leur opinion,
nommer un premier dirigeant et voir son titre au-dessus de sa tête.

## P3 — Le Conseil et les dirigeants locaux

- **Table du Conseil** et **Sièges du conseil**. On y nomme le Chancelier, le Maréchal,
  l'Intendant, le Maître-espion et le Chapelain, puis on choisit leur **tâche**. Chaque tâche
  produit un effet périodique qui dépend de sa stat (améliorer les relations, entraîner les troupes,
  collecter les impôts, débusquer les complots, légitimité…).
- **Bureau du Maire**, un par colonie. On y nomme le maire et on y lit les **rapports de la ville** :
  pénuries, demandes ouvertes, sans-logis, malades, bonheur, tirés de MineColonies.
- **PNJ vivants** :
  - les conseillers rejoignent la table aux heures de réunion et s'assoient sur les sièges ;
  - le maire rejoint son bureau ;
  - quand un dirigeant a une **requête**, il marche jusqu'à toi et ouvre un **dialogue à choix**
    façon événement CK3.

  Cela passe par un état ajouté à l'IA du citoyen, jamais sauvegardé chez MineColonies.
- Les rôles donnent de l'**XP** dans les compétences liées (réglable). Les salaires du conseil
  pèsent sur l'opinion.
- Succession d'un conseiller mort : proposition de remplaçants.

**Jouable** : poser la table et les sièges, former ton conseil, voir les conseillers s'y réunir,
recevoir les requêtes de ton maire.

## P4 — Économie, Trésor et lois

- **Coffre du Trésor** : déposer et retirer de l'or physique (converti en solde).
- **Impôts** par bâtiment et par niveau MineColonies (formule War 'N Taxes) :
  - modulés par le bonheur, l'Intendant et ses tâches ;
  - entretien des bâtiments militaires ;
  - dette plafonnée ;
  - transferts atomiques.
- **Onglet Trésor** : revenus, dépenses (salaires, entretien), historique.
- **Onglet Lois** : fiscalité (légère, normale ou lourde, qui joue sur le bonheur), **autorité de la
  couronne** (ce qu'on peut exiger des vassaux), et lois débloquées par les recherches.
- **Prestige, piété/légitimité, crainte** : sources et usages.
- **Recherches de gouvernance** ajoutées à l'Université par datapack (Chancellerie, Chevalerie,
  Administration…). C'est la progression qui débloque les blocs suivants. On vérifie d'abord que
  retirer le mod ne gêne pas MineColonies.

**Jouable** : percevoir les impôts de ta colonie, remplir et vider le trésor, voter des lois et en
voir l'effet sur le bonheur.

## P5 — Le monde : royaumes IA et carte

- **Génération des royaumes IA** : sièges ancrés sur des villages existants (ou des points de terre
  ferme), souverain, conseil et maison virtuels avec stats et traits, une **personnalité**
  (expansionniste, marchand, pacifique, fanatique, opportuniste…) et une économie simulée.
- **Découverte** : approcher d'un siège révèle son royaume (notification, héraut).
- **Carte du royaume** (touche **,**) :
  - vue **politique** et vue **de jure** ;
  - capitales et villes ;
  - statut diplomatique au survol ;
  - **brouillard** sur l'inconnu.
- **Table de guerre** (cadres muraux 3×3) : la carte en grand sur ton mur.
- **Xaero's World Map** : territoires colorés sur ta world map, si l'API le permet proprement.
- **Borne de frontière** (décorative).

**Jouable** : explorer, découvrir les royaumes voisins, les voir apparaître sur la carte et sur la
Table de guerre.

## P6 — Diplomatie, titres et vassalité

- **Colombier royal** et **lettres-objets** lisibles, avec boutons de choix, des PNJ comme des
  royaumes IA.
- **Statuts** : guerre, hostile, neutre, non-agression, commerce, alliance défensive, alliance
  totale, vassal/suzerain.
- **Actions** : cadeau, insulte, traité, accord commercial, droit de passage, tribut, exiger la
  vassalisation, utiliser une faveur ou un secret.
- **Opinion** des souverains IA, influence du Chancelier.
- Le statut natif MineColonies (Gate House) est lu et reflété.
- **Chancery Table** et **Sceau royal** :
  - titres de jure : Comté → Duché → Royaume → Empire, avec coût en or et prestige et provinces
    requises ;
  - **contrats de vassalité** à taux d'impôt et de levées négociables ;
  - **revendications** (parchemins).
- **Vassalisation pacifique** d'une province IA, première façon de grandir en solo.
- **Multijoueur** : la colonie d'un autre joueur rejoint par contrat signé par son propriétaire.

**Jouable** : écrire à tes voisins, signer un traité au sceau, vassaliser une province IA et monter
au rang de Comte.

## P7 — La guerre

- **Casus belli** (revendication, titre de jure, conquête, vassalisation, humiliation ; la guerre
  sainte arrive en P10) et « pas de guerre contre un supérieur ».
- Déclaration et buts de guerre à la **Table de guerre**.
- **Levées** fournies par les vassaux selon leur contrat et leur opinion (un vassal mécontent en
  fournit moins). Les **alliés** aident selon leurs traités.
- **Armées** : gardes MineColonies ; **vraies troupes HYW** si présent (levées, chevaliers, siège,
  propriétaire correct, protection anti tir-ami) ; soldats de repli sinon. Le **général PNJ** donne
  des bonus selon sa stat Martial.
- **Raids et sièges** : attaquer un siège IA (capturer la bannière), voir l'IA attaquer tes
  colonies.
- **Score de guerre**, fatigue de guerre, **traité de paix** signé au sceau.
- Issue : occupation fiscale, vassalisation ou conquête de la province.
- **Anti-grief** : en guerre, la casse ne se fait que par explosifs. Les dégâts sont **journalisés
  et restaurés** à la fin, y compris après un redémarrage (registre repris de War 'N Taxes).

**Jouable** : forger un casus belli, déclarer une guerre, lever ton armée, assiéger un siège IA,
négocier la paix, voir les dégâts réparés.

## P8 — L'IA des royaumes

- Décisions périodiques selon la personnalité et la situation : traités, tributs, mariages, guerres
  entre eux et contre toi, **coalitions** contre un joueur trop puissant.
- Frontières qui bougent entre royaumes IA. Le tout est testé unitairement et réglable en fréquence.

**Jouable** : un monde qui bouge sans toi, avec des alliances qui se forment, des voisins qui
s'entre-déchirent et une coalition si tu grossis trop vite.

## P9 — Intrigues, factions, événements, chroniques

- **Factions** (indépendance, baisse des impôts, changement de dirigeant), **complots**, **secrets**
  découverts par le Maître-espion, **révoltes**.
- **Événements à choix** en JSON/datapack, faciles à ajouter :
  - famine, peste, prospérité ;
  - complot découvert, vassal ambitieux, héritier, succession contestée ;
  - révolte paysanne, duel, festin, chasse…
- **Chroniques** : livre qui se remplit tout seul (règnes, guerres, successions, mariages) et onglet
  Chroniques du menu royal.

**Jouable** : des événements qui surgissent, des conseillers qui complotent, une chronique de ton
règne qui s'écrit.

## P10 — Vie de cour : tournois, croisades, mariages

- **Lice de tournoi** : organiser des joutes (équipement Epic Knights si présent), gagner du
  prestige, faire naître des traits (vétéran, champion).
- **Croisades** : un appel est lancé, les royaumes IA y répondent ou non selon leur foi et leur
  personnalité, et la croisade devient un casus belli.
- **Mariages** : appuyés sur la vraie généalogie MineColonies (partenaires, enfants), alliances
  dynastiques avec les royaumes IA, héritiers.

**Jouable** : organiser un tournoi, lancer ou rejoindre une croisade, marier un membre de ta maison
à une dynastie voisine.

## P11 — Finitions

- **HYW complet** : les armées IA pilotées par ses commandes.
- **JourneyMap** (optionnel).
- **Bouton CrusadeCraft dans la fenêtre de l'Hôtel de ville** MineColonies, si c'est faisable
  proprement (sans mixin, par l'événement d'écran Forge).
- **Codex Patchouli** « Codex de CrusadeCraft ».
- Équilibrage, configuration complète, traductions relues, audit de performance.

**Jouable** : le mod complet, documenté en jeu.
