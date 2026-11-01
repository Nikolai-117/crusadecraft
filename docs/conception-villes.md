# Fiche de conception des villes

## Décidé avec Romain (1er novembre 2026)

- **Cinq tailles** : hameau, village, bourg, ville, capitale.
- **Niveaux des bâtiments** : hameau et village de 1 à 3, jamais plus ; bourg au niveau 3 (« un début de grand
  village ») ; ville et capitale au niveau maximal.
- **Enceintes, en pièces des packs uniquement** : aucune pour le hameau et le village ; une palissade pour le bourg ;
  pour la ville et la capitale, les grandes murailles du pack de chaque thème, avec leurs portes et leurs tours.
- **Toute ville et toute capitale** ont leur marché, leurs étals, leur entrepôt, leur temple, leur cimetière, leur
  caserne, leurs tours, leurs portes et un port quand elles touchent l'eau ; le nombre de maisons suit le nombre
  d'habitants.
- **Fait** (3.5.0 et 3.5.1) : tout ce qui précède, plus le remplissage jusqu'aux murs, les rues qui serpentent du
  bourg européen, les pavés mêlés et les favoris pris d'abord. **Reste** : le port (repérer l'eau autour de la ville).


Le but : des villes « incroyables », composées seulement de pièces des packs installés, que tu choisis. Cette fiche
propose une base pour chaque taille et chaque style. Change ce que tu veux : ajoute, retire, remplace.
Les noms des pièces viennent de [l'inventaire](catalogue-plans.md) ; tu peux les voir dans le jeu (plan d'architecte,
catalogue, filtres Murs, Routes, Décors) et les marquer d'une ★ : **les villes prendront d'abord tes favoris**.

## Comment une ville sera composée

1. **Le plan historique** de sa civilisation : forum et damier romains, palais au sud et ruelles chinoises, avenue
   Suzaku japonaise, axe des temples égyptien, forteresse ronde viking, place du marché européenne.
2. **La recette de sa taille** : combien de bâtiments, lesquels, à quel niveau.
3. **Sa spécialité**, si elle en a une : port, forteresse, ville sainte, marché, mine.
4. **Les murailles en pièces des packs**, assemblées par leurs raccords : segments, angles, portes sur les grandes
   rues, tours aux angles. Plus de murs faits bloc par bloc.
5. **Les places et décors** du pack : puits, fontaines, lanternes, étals, statues, jardins, le long des rues et sur la
   place.

## Les tailles (proposition)

| Taille | Bâtiments | Niveau des plans | Place et décors | Enceinte |
|---|---|---|---|---|
| Hameau | 5 : hôtel de ville, 3 maisons, 1 ferme | 1–2 | un puits | aucune |
| Village | 8 : hôtel de ville, 4 maisons, taverne, ferme, chapelle | 2 | puits, 2 lanternes, une charrette | palissade (nordique, romain, japonais) ou aucune |
| Bourg | 13 : hôtel de ville, 6 maisons, taverne, 2 fermes, marché, forge, tour de garde | 3 | place, fontaine, étals, lanternes | petite muraille, 2 portes |
| Ville | 20 : hôtel de ville, 9 maisons, taverne, 2 fermes, marché, temple, caserne, 2 artisans, hôpital, bibliothèque | 4 | grande place, fontaine, étals, statues, jardins | muraille de pierre, 4 portes, tours aux angles |
| Capitale | 30 : palais, 13 maisons, taverne, 3 fermes, marché, temple majeur, caserne, académie militaire, 3 artisans, hôpital, université, entrepôt, monument | 5 | forum ou grande place, monuments, jardins, avenues bordées de lanternes | grande muraille, 4 portes, tours, porte monumentale |

## Les spécialités (proposition)

| Spécialité | Ce qu'elle ajoute | Pièces des packs |
|---|---|---|
| Port | pêcherie, entrepôt, quais, phare ou tour côtière | dossiers `harbor`, `coastal`, `ships` |
| Forteresse | caserne, académie militaire, tours, double enceinte, champ de tir | `military`, murailles, `castle` (Shogun) |
| Ville sainte | temple majeur, cimetière, sanctuaires, jardins | `mystic`, `shrine`, `wonders` |
| Ville marchande | marché, entrepôts, étals, grande place | `plazamarket`, `bazaar`, `stall_*`, `forum` |
| Ville minière | mine, fonderie, forgerons, tailleur de pierre | `mineshafts`, `smeltery`, `stonemason` |

## Les pièces proposées par style

### Européen (Medieval Oak, Spruce, Dark Oak, Birch)
- **Murailles** : `segment`, `corner`, `innercorner`, `tcorner`, `gate`, `tower` (niveaux 1 à 5, la hauteur monte
  avec le niveau : niveau 2 pour un bourg, 5 pour une capitale).
- **Places et décors** : `towncenter`, `citysquare`, `bigwell`, `smallwell`, `small_wheat_cart`, `roofed_carriage`,
  `noble_guard_post`, `pillory` et `gallows` (place de justice), `war_encampment` (forteresse).
- **Routes** : `small_crossroads`.

### Chinois (Pagoda)
- **Murailles** : `big_walls` (`corner_tower`, `gatehouse`, `cross`), `walls` (`panel_a/b`, `tower`, `redoubt`),
  `gates/gatehouse` ; `fences` (`fancy_*`) pour clore les cours.
- **Décors** : `well_a/b`, lanternes `kongming_*`, `pillar_a–d`, `purification_shrine`, `waystone`, `small_cart_a/b`,
  `bathhouse`.
- **Ouvrages** : 38 ponts (`bridges`).

### Japonais (Shogun)
- **Murailles** : `city_flat` (`straight`, `corner*`), `city_gate` (`offsetavearchgate`, `straightavearchgateguards`),
  `city_walltower` (`bigendtower`, `biginwalltower`…), `castle` pour le palais ; `palisades` pour les villages.
- **Routes** : `avenue` (38), `road` (47), `bridge` (61), `terrace`, `waterway`.
- **Décors** : `lampstand_*`, `lantern_paper`, `lantern_red`, `lanternstd_avebanners`, `torii_bigbirch`,
  `sjinto_wayshrine`, jardins d'eau (`stream*`, `pond*`, `island*`).

### Égyptien (Nile)
- **Murailles** : `flat` (`straight`, `gatebig`, `gatesmall`, `corner*`), `tower` (`corner`, `midwall`, `tee`,
  `towerguard`), `moat` (douves), `incline` (pentes).
- **Routes** : `avenue`, `boulevard`, `avenue_odd`, `waterway`, `harbor`.
- **Décors** : `obeliskseats`, `minimid`, jardins (`beds`, `paths`, `pools` : 64 pièces), statues (19), `nile` (98).

### Romain (Byzantine)
- **Murailles** : `wall_main` (`wallmain`, `gate`, `stair`, `twoup`, `threeup`, `diag`), `wall_small` (bourgs),
  `palisades` (villages), `field_structures/lookout`.
- **Places** : `forum` (76 pièces), `avenue`, `road`, `aqueduct`, `sewer`.
- **Décors** : `statue`, `gardens` (arches, `memorial`, `tholos`, `shrine`), `wonders` (86).

### Nordique (Nordic Spruce, Caledonia)
- **Murailles** : `wall` (`long`, `short`, `inner`, `outer`, `gate`, `climb_*`), `fancy` (`fancy_round_tower`,
  `fancy_round_barbican`, `fancy_gate`), `palisade` (`palisade_wall`, `palisade_corner`, `palisade_gate`,
  `palisade_platform`) pour les villages.
- **Routes** : `roads` (27), `bridge`.
- **Décors** : `old_stone_park`, `waterside_park`, `skadi's_rest` ; Caledonia : `lamp_plaza`, `market_plaza`,
  `sitting_plaza`, `waystone`.

## Ce que j'attends de toi

1. Les tailles : garder les cinq (avec le village), ou en enlever, en ajouter ?
2. Pour chaque taille : les bâtiments à ajouter ou retirer, et leur nombre.
3. Le niveau des plans : progressif comme ci-dessus, ou tout au plus haut niveau ?
4. Les enceintes : à partir de quelle taille, et de quel type pour chaque style.
5. Les spécialités : lesquelles garder, et comment une ville reçoit la sienne (au hasard, selon le terrain, choisie
   dans l'éditeur).
6. Tes pièces préférées : marque-les d'une ★ dans le catalogue du jeu, ou cite-les ici.
