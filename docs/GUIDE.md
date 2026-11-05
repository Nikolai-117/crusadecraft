# CrusadeCraft 3.5.1 — installer et tester

## 1. Ce qu'il faut

Ton instance MultiMC **1.20.1** a déjà tout le nécessaire :

| Requis | Dans ton instance |
| --- | --- |
| Minecraft 1.20.1, Forge 47.x | Forge 47.4.10 (la même version que celle du développement) |
| MineColonies 1.1.1200 ou plus récent | MineColonies 1.1.1214, avec Structurize, BlockUI et Domum Ornamentum |

Optionnels, déjà présents chez toi, donc activés automatiquement :

- **Hundred Years War** : ses bandits forment les garnisons et les pillards, ses soldats tes hommes d'armes.
- **Patchouli** : le *Codex de CrusadeCraft*, un manuel en jeu (recette : livre, pépite d'or, plume).
- **Epic Knights** : un champion de ta cour qui gagne un tournoi te rapporte une lance de ce mod.

## 2. Installer

1. Le fichier du mod est `E:\crusadecraft\build\libs\crusadecraft-3.6.1.jar`.
2. Mets-le dans le dossier des mods de l'instance : `D:\MultiMC\instances\1.20.1\.minecraft\mods\`.
   Dans MultiMC, tu peux aussi faire clic droit sur l'instance → *Modifier* → *Mods* → *Ajouter un fichier*.
   **Retire l'ancien** (`crusadecraft-1.8.0.jar`, ou une version plus vieille) : deux versions du mod ensemble
   empêchent le jeu de démarrer.
3. Lance l'instance comme d'habitude.

**Avant de jouer sur un monde qui compte, fais une sauvegarde de ce monde** (copie de son dossier dans
`D:\MultiMC\instances\1.20.1\.minecraft\saves\`). Le plus simple pour un premier essai : **un nouveau monde de
test**.

**Tes données sont copiées avant chaque mise à jour.** Quand une nouvelle version du mod change le format de ses
données, elle en garde d'abord une copie datée dans `saves/<ton monde>/data/crusadecraft_backups/`. Pour revenir en
arrière : remets l'ancien jar et recopie ce fichier à la place de `data/crusadecraft.dat` (monde fermé).

**Pour désinstaller**, il suffit de retirer le jar. CrusadeCraft ne modifie jamais MineColonies : tes colonies
restent exactement comme avant. Seuls les blocs du mod (trône, tables, coffre…) disparaissent du monde. Les
données du mod (fichier `crusadecraft.dat` du monde) restent sur place, sans effet.

## 3. Premier essai, pas à pas

### Fonder le royaume
1. Fonde une colonie MineColonies et monte son **Hôtel de ville au niveau 2**.
2. Fabrique le **Trône royal** (bloc d'or en haut ; planches de chêne noir et laine rouge au milieu ; bûches de
   chêne noir et planche en bas). Pose-le **dans l'Hôtel de ville**, fais un clic droit dessus et fonde ta
   baronnie : nom, devise, maison, couleurs.
3. La touche **K** ouvre le **menu royal**. Un bouton **« ♔ Mon royaume »** apparaît aussi en haut à droite de la
   fenêtre de l'Hôtel de ville.
4. Les royaumes IA se génèrent autour de toi à la fondation. Des hérauts viennent se présenter ; voyager te
   fait découvrir les autres.

### Le conseil et le trésor
5. Fabrique le **Sceptre** (diamant sur deux lingots d'or). Clic droit avec, sur un citoyen : sa fiche, puis
   « Nommer » (chancelier, maréchal, intendant, maître-espion, chapelain).
6. La **Table du conseil** demande un Hôtel de ville de niveau 3. Le **Coffre du trésor** reçoit tes pièces
   d'or. Les impôts tombent chaque jour du monde (un jour = 20 minutes). Les lois se votent dans l'onglet *Lois*.

### Le monde et la diplomatie
7. **Table de guerre** : un cadre (bâtons, cuir, carte) à poser sur un mur. En clic droit, elle ouvre la guerre ;
   son bouton « Carte » ouvre la carte du monde.
8. **Colombier** : écrire aux autres souverains. Leur réponse arrive par lettre. Les lettres se scellent avec le
   **Sceau royal** (lingot d'or, rayon de miel, colorant rouge, bâton).
9. **Chancery Table** : créer tes titres (Comté, Duché…), gérer tes vassaux, revendiquer des provinces.
10. **Borne de frontière** : posée dans une **autre colonie qui t'appartient**, elle en fait une province de ton
    royaume.

### La guerre
11. À la Table de guerre, choisis un royaume et un motif. Le plus simple pour un premier essai : insulte un voisin
    au Colombier, ce qui ouvre l'« Humiliation ». Déclare la guerre en cliquant deux fois, puis **lève l'armée**.
12. Marche vers sa capitale : la garnison sort, tes hommes d'armes te suivent. **Tiens la bannière 10 secondes**
    pour prendre la place.
13. La paix se négocie depuis la Table : paix blanche, victoire exigée ou capitulation, puis la lettre de l'IA à
    sceller. Les dégâts faits à la TNT en territoire ennemi se réparent à la paix.

### Les intrigues et les événements
14. Quand une décision t'attend, une notification l'annonce et un bouton **« Événements »** apparaît dans le menu
    royal.
15. Le bouton **« Intrigues »** (onglet *Conseil*) montre les factions, complots et secrets découverts par ton
    maître-espion. Donne-lui la tâche **« Débusquer les complots »** pour en découvrir davantage.
16. L'onglet **Chroniques** raconte ton règne et les nouvelles du monde. Le livre **Chroniques du royaume** se
    fabrique avec un livre, une plume, une poche d'encre et du papier.

### La cour
17. La **Lice de tournoi** (laine rouge, laine blanche, bâtons, bûche) se pose dans ton royaume. Elle demande la
    recherche « Chevalerie » à l'Université de la capitale. Tu peux couper cette exigence dans les réglages
    (section `cour`).
18. Le bouton **« Maison »** (onglet *Royaume*) sert à accueillir des citoyens dans ta dynastie, désigner un
    héritier et marier un membre à une maison IA de ta foi.
19. **Croisade** : il faut être comte et avoir 100 de piété, contre un royaume païen ou hérétique (la foi
    s'affiche sur sa fiche). Choisis le motif « Guerre sainte » à la Table de guerre.

### Dessiner son propre monde (éditeur de la carte)
20. Ouvre la carte (touche `;` sur un clavier français, ou le bouton « Carte » de la Table de guerre), puis
    **Modifier**. En solo, l'hôte y a droit sans rien régler.
21. **+ Royaume** : clique sur la carte, choisis le nom, le souverain (au hasard si vide), le rang, la foi, le
    caractère et la couleur, puis **Créer**. C'est un vrai royaume IA : il écrit, commerce et fait la guerre.
22. **+ Ville** : choisis à droite à qui elle appartient (un royaume IA, ou le tien), puis clique sur la carte.
    Une ville à toi compte pour tes titres et te rapporte son revenu, comme une conquête.
23. **Pinceau** : choisis à droite le royaume (ou « Terre libre », ou la gomme) et la taille, puis peins en
    maintenant le clic. Le clic droit glissé déplace la carte. Les frontières peintes comptent aussi pour la guerre.
24. **Choisir** : clique sur une ville pour la renommer, la déplacer, la donner ou la supprimer, et modifier ou
    supprimer son royaume. Les suppressions demandent un second clic.
25. **Monde vierge** (outil Choisir, rien de sélectionné) : efface tous les royaumes créés par le jeu. Les tiens
    restent, et le jeu n'en recrée plus.

### Familles, dynasties et populations (avec Minecraft Comes Alive)
26. Ouvre le **Registre des lignées** : bouton dans l'écran « Maison » du menu royal, ou dans l'éditeur de la carte.
    Chaque royaume IA que tu connais y a déjà sa cour (souverain, famille, héritier, conseillers).
27. **+ Nouvelle maison** : un nom, un royaume (ou aucun), de 1 à 5 générations, une couleur, puis **Générer**.
    **+ Communauté** : un village, une guilde, un clan, une compagnie, un ordre ou un monastère, et sa taille.
28. Clique sur une personne : **Renommer**, **Rôle**, **+ Enfant**, **Marier**, **Mort/Vivant**, **Supprimer**.
    **Agrandir** ajoute une génération (ou trois foyers).
29. **Faire venir** fait apparaître les gens près de toi en villageois Comes Alive (16 au plus d'un coup), avec leur
    nom, leur âge et leur métier ; **Arbre** remplit l'arbre généalogique de Comes Alive ; **Inscrire** ajoute au
    registre les villageois Comes Alive qui t'entourent.

### Tracer ses frontières et construire (bâton de bornage)
30. Fabrique le **Bâton de bornage** : deux bâtons, une ficelle et un colorant rouge, dans n'importe quel ordre.
31. **Clic droit dans le vide** ouvre son menu, en trois colonnes :
    - **Pour qui** : un royaume, la terre libre ou la gomme (la gomme seulement dans l'éditeur) ;
    - **Tracer** : la façon de tracer ;
    - **Construire** : la construction à poser.
32. **Tracer** :
    - **Polygone** : clic droit sur des blocs pour poser les coins, puis reclique près du premier ;
    - **Rectangle** : deux coins opposés ;
    - **En marchant** : chaque case où tu entres, le bâton en main, est peinte. Accroupi, elle redevient libre.

    Le tracé s'affiche en poussière colorée avant d'être validé. **Ctrl+Z** annule le dernier tracé et rend l'or.
33. **En survie**, on ne trace que pour son royaume, sur des cases libres, collées à ses terres et à moins de 256 blocs
    d'une de ses places. Chaque case coûte 5 pièces d'or (réglable). Une case tenue par un autre royaume se prend à la
    guerre, pas au bâton.
34. **Construire** : choisis une construction, puis fais un clic droit sur le sol. L'entrée regarde vers toi.
    - Les constructions possibles sont le jalon, la tour de guet, le camp, l'avant-poste et le fort, plus, dans
      l'éditeur, le donjon et le village peuplé.
    - Dans l'éditeur, elles se posent d'un coup ; en survie, elles s'élèvent bloc par bloc, payées d'avance.
    - Achevée, une construction tient les terres alentour, avec une garnison. Un ennemi peut l'assiéger : il prend sa
      bannière.
    - Sur la carte du mod, clique sur une construction pour la donner ou la supprimer. Ses pierres restent dans le
      monde.
35. **Carte papier** : une carte vanilla, en main ou dans un cadre, montre les terres des royaumes et le nom des
    capitales. Au survol, son infobulle dit à qui sont les terres en son centre.

### Les lieux : ranger le monde et écrire ses fiches
36. Le bouton **« Lieux »** (menu royal, onglet *Royaume* ; ou au pied de la carte) ouvre l'arbre de tous les lieux :
    les royaumes sous leurs suzerains, les villes sous leurs royaumes, les forts sous leurs villes. En haut, la ligne
    « Vous êtes : » dit où tu te tiens.
37. Clique sur un lieu : sa **fiche** s'affiche (qui le tient, de quoi il relève, sa population, sa position).
    **Écrire** ouvre la fiche à rédiger : son histoire, ce qu'on y trouve. Le mod n'y écrit jamais rien. Tu peux écrire
    la fiche des lieux de ton royaume ; l'hôte, celle de tous.
38. **Poser un lieu saint, une ruine ou une région** : bâton de bornage, colonne *Poser un lieu*, puis clic droit sur le
    sol et un nom. En survie, seulement sur tes terres. Ces lieux apparaissent sur la carte du mod (une région avec son
    cercle) ; un clic dessus ouvre leur fiche.
39. **L'hôte** peut aussi poser des **titres** qui regroupent d'autres lieux (« + Nouveau lieu », genre *Empire*,
    *Royaume*… ; on tape seulement « Lothaire » pour « Empire de Lothaire »), puis **Rattacher** chaque lieu où il veut.
    « Rendre au monde » le remet sous le parent que le monde lui donne.
40. Une ville ou une construction effacée **garde sa fiche** si tu en avais écrit une : elle reste dans la liste,
    « disparue du monde », jusqu'à ce que tu la supprimes.

### Le territoire au chunk, comme Factions
41. Les terres se comptent en **chunks** (carrés de 16 × 16 blocs). Autour de tes colonies, de tes forts et de tes
    postes de garde, les chunks sont à toi d'office ; tu peux en **revendiquer** d'autres.
42. Les commandes, pour tous les joueurs :
    - `/crusade claim` revendique le chunk où tu te tiens ; `/crusade claim 2` un carré de 5 × 5 autour de toi ;
    - `/crusade unclaim` (ou `/crusade unclaim 2`) le rend : il devient terre libre ;
    - `/crusade map` affiche les chunks autour de toi, en couleurs, dans le chat ;
    - `/crusade info` dit à qui est ce chunk et combien de terres a ton royaume.

    En survie, comme au bâton : collé à tes terres, à moins de 256 blocs d'une de tes places, 5 pièces d'or le chunk,
    et un **plafond** selon ton rang (64 chunks revendiqués pour une baronnie, 160 pour un comté…). Ctrl+Z annule.
43. En passant une frontière, le **nom du royaume s'affiche en grand**.
44. **Protections** : sur les terres d'un royaume de joueur, seuls les siens cassent, posent et ouvrent les coffres.
    Un vassal ou son suzerain aussi ; un allié ouvre portes et coffres ; un ennemi en guerre pose ses échelles. Dans une
    colonie, ce sont les permissions de MineColonies qui comptent. Les terres des royaumes de l'ordinateur restent
    libres (réglable).

### La carte web Dynmap
45. **Installer Dynmap** : copie `E:\crusadecraft\libs\local\Dynmap-3.7-beta-6-forge-1.20.jar` (le fichier officiel,
    pris sur Modrinth) dans le dossier `mods` de l'instance, à côté de CrusadeCraft.
46. Lance ton monde, puis ouvre **http://localhost:8123** dans ton navigateur (pendant que tu joues).
47. Dans le menu des couches (en haut à droite de la page), coche **Royaumes** et **Lieux**. Chaque royaume est
    dessiné chunk par chunk, à sa couleur ; un clic ouvre sa fiche (souverain, trésor, population, terres…).
    La carte se met à jour toute seule toutes les 20 secondes.
48. Dynmap dessine le terrain à mesure que tu explores. Pour dessiner d'un coup ce qui est déjà exploré :
    `/dynmap fullrender` + le nom de ton monde (il faut les commandes activées). C'est long sur un grand monde moddé.

### Xaero's World Map et la minimap
49. Rien à installer : tes terres et celles des royaumes que tu connais apparaissent sur la carte de Xaero (touche
    **M**) et sur la minimap, chunk par chunk, avec le nom du royaume au survol. Elles suivent l'option « Afficher les
    claims » de Xaero, et ses réglages d'opacité.

### La carte fantasy et les noms
53b. Sur Dynmap, chaque royaume a son **nom écrit en grand** au cœur de ses terres, à l'encre de sa couleur, et
    chaque ville son icône : un **sceau doré** pour une capitale, un point cerclé pour une ville, un point pour un
    village, une tour pour une place forte. De loin, seuls les grands royaumes ; en zoomant, les petits, puis les
    capitales, les villes et les villages.
53c. Pour retirer le cadre sombre que Dynmap pose derrière les noms, le mod écrit sa feuille de style dans
    `dynmap/web/css/override.css` et l'active dans la page de Dynmap (la voie prévue par Dynmap). Un `override.css`
    à toi est complété, jamais écrasé. Réglage `carteWeb.styleFantasy` pour s'en passer.
53d. **Renommer** : bouton **✎** à côté des boutons du menu royal pour ton royaume, **✎** devant chaque ville dans
    l'écran des Villes (la colonie MineColonies change de nom aussi), et l'écran des **Lieux** pour tout le reste
    (les royaumes et villes de l'ordinateur, si tu as l'éditeur). Par commande : `/crusade renommer royaume <nom>`,
    `/crusade renommer ville <nom>` en se tenant dans la ville.

### Zones dessinées d'avance et fusions
53e. Dans l'éditeur de la carte (créatif), crée un royaume et **peins sa zone** avant que personne n'y vive. Pose ensuite
    ton **Trône** dans cette zone : ce royaume dessiné devient **le tien**, avec ses terres peintes et ses villes (le
    nom et la couleur restent ceux que tu choisis à la fondation).
53f. Une **nouvelle colonie** que tu fondes dans la zone de ton royaume (peinte ou revendiquée) s'y rattache d'elle-même
    dans les vingt secondes (réglage `territoire.coloniesRejoignentZone`).
53g. **Fusionner** : dans l'éditeur, choisis une ville d'un royaume de l'ordinateur, règle « Fusionner dans » sur le
    royaume qui l'absorbe (de l'ordinateur ou de joueur), puis OK deux fois : ses villes, ses terres peintes et son
    trésor passent à l'autre.

### Des villes bâties pour de vrai, selon leur civilisation
53h. Chaque royaume de l'ordinateur a un **style** : européen, chinois, japonais, égyptien, romain ou nordique. Dans
    l'éditeur de la carte, choisis une ville, puis **Style** dans la partie royaume (clic : suivant, Maj+clic : retour).
    Les bâtiments viennent des packs installés, lus pendant le jeu : MineColonies (Medieval Oak, Pagoda, Desert Oasis,
    Nordic Spruce…) et Byzantine (romain, Nil, Shogun).
53i. **Bâtir la ville** (deux clics) : la ville s'élève d'un coup, à la taille de sa population (hameau, bourg, ville,
    capitale), avec ses rues, son enceinte quand sa civilisation en bâtissait, et ses habitants. Le plan suit
    l'urbanisme de chaque civilisation :
    - **romaine** : cardo et decumanus qui se croisent au forum, basilique au nord, temple au sud, îlots en damier,
      enceinte carrée à quatre portes ;
    - **chinoise** : palais au centre tourné au sud, cour devant, marché derrière, temple des ancêtres à l'est, avenue
      axiale, ruelles où chaque maison regarde le sud, muraille carrée (Kaogong ji, axe de Pékin) ;
    - **japonaise** : palais au nord, avenue Suzaku jusqu'à la porte du sud, grille de rues, temple de l'Est, marché de
      l'Ouest, sans muraille (Heian-kyō) ;
    - **égyptienne** : enceinte de briques crues, axe processionnel du palais (à l'ouest) au temple (à l'est), rangées
      de maisons (Kahun) ;
    - **nordique** : rempart circulaire de terre, quatre portes, deux rues en croix, quatre maisons en carré autour d'une
      cour dans chaque quartier, basse-cour au sud-est (Trelleborg) ;
    - **européenne** : place du marché au carrefour, hôtel de ville et église sur la place, maisons le long de la
      grand-rue, murs une fois devenue ville.
53i bis. **Depuis la 3.5**, les villes suivent la fiche décidée avec toi (`docs/conception-villes.md`) : cinq tailles,
    les niveaux des plans selon la taille, la palissade des bourgs et la grande muraille du thème pour les villes et
    les capitales (vraies pièces des packs, portes sur les grandes rues, tours), tous les bâtiments d'une ville (marché,
    étals, entrepôt, temple, cimetière, caserne, tours), et un remplissage jusqu'aux murs : maisons, ruelles, placettes
    aux carrefours, mobilier de rue, jardins. **Tes pièces ★** passent avant les autres (bâtiments, décors, murailles).
    Le bourg européen a ses rues qui serpentent, et chaque thème ses pavés mêlés.
53j. **Défaire la ville** : sur la construction de la ville (« Village de … » sur la carte), deux clics. Le terrain revient
    bloc par bloc, coffres compris ; la ville reste sur la carte.
53k. Réglage **villesIA → batirEnApprochant** (coupé par défaut) : une ville de l'ordinateur prend corps quand tu t'en
    approches. Jamais là où des joueurs ont longtemps vécu, ni près d'une colonie. `/crusade civ annuler` les défait.
53l. **Plan d'architecte** (créatif ou éditeur, dans l'onglet du mod) : clic droit vers le ciel pour le catalogue (villes
    entières, chaque pack de style, tes fichiers), clic droit sur un bloc, même à 96 blocs, pour poser (le sol du plan
    au niveau du bloc),
    accroupi pour tourner. Un cadre doré montre où il tombera, un liseré rouge marque l'entrée.
    `/crusade plan annuler` défait la dernière pose.
    Tant que tu tiens l'outil, un **HUD** en haut à gauche dit le plan choisi, sa taille, le côté de l'entrée, le bloc
    visé et sa distance, les gestes, et la barre d'avancement pendant une pose. Dans le **catalogue**, un clic choisit
    un plan et montre à droite sa taille et un **aperçu vu du ciel** (pour une ville entière : son plan, avec sa légende,
    et « Autre tirage » pour une autre variante) ; double-clic ou « Prendre ce plan » pour le prendre, flèches et Entrée
    au clavier.
    **Le fantôme, comme Schematica** : un plan choisi s'affiche en transparence là où il tombera. **R** le tourne,
    **Page↑ / Page↓** le monte ou le descend, **les flèches** le poussent devant, derrière, à gauche, à droite (selon ton
    regard), **G** le fige sur place (tu peux tourner autour) ou le libère, **Début** le remet sous le viseur ; le clic
    droit pose exactement là où il est. Toutes ces touches se changent dans Options › Commandes › CrusadeCraft. Une ville
    entière, trop grande pour un fantôme, se montre par son cadre.
    **Filtres et favoris** : sous la recherche du catalogue, Tout · Bâtis · Murs · Routes · Décors · ★ ; le panneau d'un
    plan dit sa sorte et son rôle (segment, angle, porte, tour, puits, lanterne…) ; « ★ Favori » le range dans
    l'étagère « ★ Mes favoris », commune au monde. L'inventaire complet des pièces est dans `docs/catalogue-plans.md`.
    Une **ville entière** posée à l'outil devient une ville du monde : sur la carte, avec ses habitants. Posée sur les
    terres d'un royaume, elle le rejoint ; sur une terre libre, elle **fonde une nation** de son style, dont elle est
    la capitale. On la défait comme les autres (« Défaire la ville » dans l'éditeur).
    **La faire sienne** : après la pose, le message propose **[Faire ma colonie]** et **[Fonder mon royaume ici]** (aussi
    dans l'éditeur, sur la construction de la ville, et par `/crusade ville colonie` ou `/crusade ville royaume` en s'y
    tenant). La ville devient une vraie colonie MineColonies : chaque bâtiment est enregistré à son niveau, avec son pack
    et son plan, les colons arrivent et y travaillent ; elle rejoint ton royaume, ou l'écran de fondation s'ouvre avec
    elle pour capitale. Elle ne se « défait » plus ensuite : c'est une colonie comme les autres. Pour composer ta propre ville,
    pose les bâtiments un à un depuis les packs : chaque pose se défait.
53m. **Tes propres plans** : dépose tes `.nbt` (bloc de structure) et `.schem` (WorldEdit) dans
    `crusadecraft/plans/` du dossier du jeu (`/crusade plan dossier` donne le chemin). Tes packs Structurize (scans du
    dossier `blueprints/`) apparaissent d'eux-mêmes dans le catalogue.
53n. **Une ville entière en schématique** (3.6). Les villes des fichiers sont aussi en tête de l'étagère **Villes
    entières** du catalogue, sous le nom de leur dossier (Levenzia, Varenhold) (3.7.1). Dolhimluith (Levenzia) et Grunbach (Varenhold) sont livrées avec le mod (3.6.1) : il les range lui-même au démarrage. Pour une autre ville, range-la dans un sous-dossier de `crusadecraft/plans/` — le nom du
    dossier devient son étagère dans le catalogue (`plans/Levenzia/dolhimluith.schem` → « Levenzia »). Sur un serveur,
    c'est le dossier **du serveur** qui compte. Elle se pose telle qu'elle a été bâtie, au bloc près, avec son terrain :
    son sol (la terre de ses bords) tombe au niveau du bloc visé. Elle garde l'orientation de son dessin (**R** la
    tourne). Son fantôme montre le dessus de ses toits et de ses rues. Une fois posée, elle devient une **ville de ton
    royaume**, à son nom, et ses bâtiments sont reconnus d'après leurs portes.
    **Les blocs qu'on n'a pas** (un autre mod, une version plus récente de Minecraft) prennent leur équivalent installé,
    **de même forme** : un escalier reste un escalier tourné pareil, une demi-dalle verticale devient une plaque de Dawn
    of Time, un torchis à colombages un colombage de Domum Ornamentum avec le même motif, le chaume du chaume. Le
    catalogue dit combien de sortes ont été remplacées, et nomme celles qui n'ont aucun équivalent (laissées vides).
    La table est dans le mod ; tu la complètes ou la corriges avec `crusadecraft/equivalences.json` (même format :
    `"me:stucco": ["dawnoftimebuilder:plastered_stone", "minecraft:white_terracotta"]`).
53o. **Bâton d'intendant** (onglet du mod) : donner un but aux bâtiments d'une ville.
    - **Clic droit sur une porte** (ou un mur, de l'intérieur) : le bâtiment est reconnu — on suit son intérieur sous
      le toit, pièce après pièce, étage après étage — et un écran s'ouvre : seize fonctions (maison, mairie, château,
      temple, caserne, tour de garde, boulangerie, taverne, forge, marché, boucherie, ferme, écurie, bibliothèque,
      atelier, entrepôt), celle que ses meubles suggèrent marquée d'une ★ (une enclume et un fourneau : une forge), un
      nom si tu veux, ce qu'on y a trouvé. **Valider** : ses gens arrivent.
    - **Accroupi, deux clics** : une boîte tracée à la main, pour ce que la porte ne trouve pas (une halle ouverte).
    - **Vers le ciel** : l'écran de la ville — ses bâtiments, ses gens, **Reconnaître les bâtiments** (toutes les portes
      d'un coup) et **Accepter les propositions**.
    - Tant que tu le tiens, chaque bâtiment a son cadre de la couleur de sa fonction (gris pâle : pas encore décidé), et
      le HUD dit la ville, le bâtiment visé et ses gens.
    **Ses gens** sont inscrits au registre des lignées, dans la communauté de la ville, et apparaissent en villageois de
    Comes Alive quand tu approches : une ou deux **familles** par maison (elles passent la journée sur la place du
    marché, rentrent le soir) ; à la boulangerie un **boulanger** qui vend pain, biscuits, tartes et gâteaux ; à la
    taverne un **tavernier** (ragoûts, poulet, miel) ; à la forge un **forgeron** (armes, armures, outils) ; des
    **gardes** à la caserne et au château, un **archer** dans la tour, un **prêtre** au temple, des **marchands** au
    marché… Chacun reste à son ouvrage le jour. Un marchand mort est remplacé le lendemain ; les étals se regarnissent
    chaque jour. Une ville fait vivre **80 personnes au plus** (les villageois de Comes Alive pèsent lourd).
    Changer la fonction d'un bâtiment renvoie ses gens ; « Retirer de la ville » l'efface du plan de la ville (les
    pierres restent). Le bâton sert au créatif, à l'éditeur de la carte, et au maître de la ville.
53o bis. **Le panneau de placement** (3.8), comme le bâton de construction de MineColonies : avec un plan choisi,
    **clic droit sur le sol** : le fantôme se fige là et un panneau s'ouvre en bas à droite, le monde visible derrière.
    Ses boutons : **Tourner** (dans les deux sens), **Avancer**, **Reculer**, **Gauche**, **Droite** (selon ton regard),
    **Monter**, **Descendre**, le **pas** (1, 5 ou 10 blocs), puis **Poser ici** ou **Annuler**. Au clavier : flèches ou
    Z Q S D, Espace et Maj (monter, descendre), R (tourner), Entrée (poser), Échap (fermer, le fantôme reste figé ; un
    nouveau clic droit rouvre le panneau). Une ville entière comble aussi le dessous jusqu'au sol (jusqu'à 96 blocs :
    terre, puis pierre) : plus de vide sous elle si tu la poses sur un point haut.
53o ter. **Le bâton d'intendant comme l'outil de scan de MineColonies** (3.8) : **clic gauche** sur un bloc pose le
    premier coin (sans le casser), la boîte suit ton viseur, **clic droit** sur le coin opposé : l'écran de la fonction
    s'ouvre pour ce bâtiment. Accroupi + clic droit vers le ciel efface les coins. Le clic droit sur une porte reste le
    raccourci : le bâtiment se reconnaît tout seul.
53o quater. **Levànzia, Varenhold et les autres nations** (3.9) : une nation est un royaume écrit d'avance, dans un fichier
    de `crusadecraft/nations/` (nom, couleur, tempérament, histoire, culture, souverain, liens, habitants). Il existe dès
    le départ, sans terre. Une ville rangée dans son dossier de plans (`Levenzia`, `Varenhold`) lui revient, où qu'on la
    pose ; une ville déjà posée sous son ancien nom s'y fond sans rien perdre. Sur une **nation de joueur**, celui qui pose
    la première ville prend la couronne : l'ordinateur ne décide plus pour elle, les autres souverains t'écrivent, et tu
    réponds depuis son écran (au Sceptre, ou `/crusade nation`) : histoire, villes, relations, traités, présents, guerre,
    courrier. Les autres nations vivent seules, comme tout royaume de l'ordinateur. Commandes : `/crusade nation liste`,
    `voir <clé>`, `couronne <clé>`, `abdiquer`, `recharger` (après avoir corrigé un fichier), `restaurer` (remet ceux du
    jar, les anciens restent en `.bak`). Une nouvelle version du mod n'écrase jamais tes fichiers.
53o quinquies. **Parler aux habitants** (3.10) : **accroupi, clic droit** sur un habitant : son portrait, son métier, son caractère,
    sa vie, sa famille, ce qu'il pense de toi, et des sujets de conversation (bonjour, métier, ville, royaume, nouvelles,
    famille, ce qu'il pense de toi) et son étal. Ce ne sont pas des réponses d'une intelligence artificielle : plus de
    900 textes écrits d'avance, choisis selon qui parle et qui écoute. Le clic droit ordinaire reste celui de Comes Alive.
    Les personnages écrits dans le fichier de la nation prennent le premier poste de leur fonction, avec leur nom, leur
    vie et leurs propres répliques. Ils portent tous les noms de la culture de leur nation.
53o sexies. **L'œuf d'habitant** (3.10), dans l'onglet créatif : clic droit sur un bloc, choisis la ville et le métier : il naît là,
    inscrit au registre, à un poste s'il y en a un. L'œuf retient le choix ; accroupi, tu le changes. Une ville en reçoit
    80 au plus.
53o septies. **Leur journée** (3.11) : tant qu'un joueur est là, chacun fait son ouvrage, l'outil en main : le fermier moissonne et
    ressème les champs autour de sa ferme, le forgeron va de l'enclume au four, le boulanger porte son pain à la taverne,
    les gardes font leur ronde (certains la nuit), l'archer guette, les habitants vont au marché et paient, on déjeune à
    midi, on boit le soir, les enfants jouent, les anciens ne travaillent plus. Ils parlent : une bulle au-dessus de leur
    tête, jamais plus de quelques-unes à la fois (`repliques = false` dans la configuration pour la couper).
53o octies. **Leurs réserves et leur vie** (3.11), jour après jour, même si personne ne regarde : chaque ville a une bourse et
    des réserves (blé, pain, viande, repas, fer, outils, pierre, bois, cuir, livres), visibles sur l'écran de la ville et
    avec `/crusade ville info`. Ce qui manque à la ville manque aux étals, ce qui est rare coûte plus cher, et ce que tu y
    achètes ou vends fait bouger la bourse. Une partie de la richesse va au royaume en impôt. Un traité de commerce amène
    des convois. On se marie, on a des enfants, un adulte sans métier reprend le poste d'un mort ; une ville qui a faim
    plusieurs jours perd des familles, une ville qui mange bien en attire. Tout ça se raconte : demande « Des nouvelles ? ».
    Un habitant se souvient de ce que tu lui fais : un coup, un meurtre, une vente, une conversation ; ça s'efface lentement.
53o nonies. **Les enseignes** (3.12) : un bloc par fonction (dix-neuf, dont l'école, le moulin et l'hospice, nouveaux), dans l'onglet
    créatif, ou à fabriquer : un panneau et l'objet du métier (panneau + pain pour la boulangerie). Pose-la dans un bâtiment :
    il prend cette fonction et ses gens arrivent, sans bâton ni commande. Retire-la : ils s'en vont. Hors d'une ville, on te
    propose d'en fonder une, et les enseignes déjà posées y sont lues. Une enseigne placée dans une schématique ou une ville
    bâtie à la main est reconnue toute seule quand la ville se pose. Sans mur autour (un étal, un champ), le bâtiment fait
    neuf blocs de côté autour d'elle. École : les enfants y vont le matin, et la ville produit 10 % de plus. Moulin : la même
    farine fait moitié plus de pain. Hospice : une fois sur deux, on y échappe à la mort.
53o decies. **La décoration sert à quelque chose** (3.12), de Minecraft ou de n'importe quel mod (on la reconnaît à son nom) :
    - **S'asseoir** : les mains vides, clic droit sur une chaise, un banc, un tabouret, le siège du conseil ou un escalier à
      l'endroit. Maj pour se lever. Les habitants s'assoient aussi à la taverne, au temple, à midi et le soir.
    - **Dormir** : un lit dans un logis, et la nuit quelqu'un s'y couche.
    - **La cloche** : sonne-la dans une ville, et tout le monde vient pendant une minute.
    - **Le comptoir** : les mains vides, clic droit sur une table ou un comptoir d'un bâtiment où l'on vend : son étal s'ouvre.
    - **Fours, enclumes, rayonnages, foin, tables** : ils font produire jusqu'à deux fois plus dans le bâtiment de leur métier.
    - **Coffres, tonneaux, caisses** : huit places de plus dans les réserves, par meuble, dans toute la ville.
    - **Puits, fontaines, chaudrons** : les habitants y font la corvée d'eau.
    - **Fleurs, lanternes, tapis, bannières, statues** : le **charme** de la ville, de 0 à 100 (écran de la ville). Il
      attire des visiteurs, donc de l'argent, et des familles dans les maisons vides. Il se mesure lentement, en parcourant
      la ville tant qu'un joueur y est. `meublesUtiles = false` dans la configuration coupe tout ça.
53p. **Voir la ville avant de la poser** (3.7) : une ville entière (tes fichiers, Dolhimluith, Grunbach, et les villes
    générées par style) s'affiche **en entier en transparence** là où tu vises, comme au bâton de construction de
    MineColonies : toits, façades et murailles d'abord, puis le terrain, jusqu'à 250 000 blocs. Elle arrive en
    quelques secondes (le HUD dit « N blocs, vise le sol pour la voir » tant qu'elle se prépare). Mêmes touches que pour
    un bâtiment : **R**, **Page↑/↓**, flèches, **G**, **Début**. Vise le sol à moins de 96 blocs.
53q. **Fonder une ville sur ce qui est déjà bâti** (3.7) : ton propre village, un build collé au WorldEdit… Tiens-toi
    au milieu, bâton d'intendant **vers le ciel** : hors d'une ville, il propose de la fonder (son nom ; elle couvre 64
    blocs autour de toi). Ou `/crusade ville fonder <nom>` (`/crusade ville fonder 120 <nom>` pour 120 blocs). Elle
    devient une ville du royaume dont ce sont les terres (sinon une nation nouvelle), et ses bâtiments sont reconnus.
53r. **Les commandes des villes** (3.7), pour la ville et le bâtiment où tu te tiens :
    - `/crusade ville info` : la ville, ses bâtiments, ses gens ; le bâtiment où tu es et qui y vit ;
    - `/crusade ville ecran` : l'écran de la ville ;
    - `/crusade ville reconnaitre`, `/crusade ville accepter` : comme les boutons de l'écran ;
    - `/crusade ville fonction <fonction> [nom]` : donne une fonction au bâtiment où tu es (`maison`, `mairie`,
      `chateau`, `temple`, `caserne`, `tour`, `boulangerie`, `taverne`, `forge`, `marche`, `boucherie`, `ferme`,
      `ecurie`, `bibliotheque`, `atelier`, `entrepot`, ou `aucune`) ;
    - `/crusade ville retirer` : retire de la ville le bâtiment où tu es ;
    - `/crusade ville habitants` : fait venir tout de suite les gens qui manquent ;
    - `/crusade plan recharger` (opérateur) : relit les fichiers de plans et `equivalences.json` après les avoir changés.
    Une maison dont toute la famille est morte accueille une nouvelle famille le lendemain ; quand tu nommes un marché ou
    une mairie, les familles vont désormais passer la journée autour.

### Le panneau d'une ville

Le **bâton d'intendant tourné vers le ciel**, ou `/crusade ville panneau`, ouvre le panneau de la ville où tu es, si elle est à toi
(ou si tu es en créatif, ou éditeur de la carte). Il s'ouvre aussi tout seul quand tu fondes une ville ou qu'une ville finit d'être posée.

- **En direct** : ce qui vient de se passer dans la ville (qui vend quoi à qui, qui livre, qui part, qui arrive), mis à jour toutes les
  deux secondes, et ceux qui sont sur les routes, avec leur avancement. En créatif, le bouton **Voir** t'emmène à côté d'eux.
- **Général** : son nom, le plafond d'habitants, ce qui est permis (mariages, voyages, départs, parole), ses propres listes de noms.
- **Son camp** : la rendre indépendante, lui faire rejoindre un royaume, ou fonder un royaume autour d'elle. Un royaume fondé ainsi
  est une vraie nation, avec son fichier dans `crusadecraft/nations`. « Pour moi » te donne sa couronne, sauf si tu en portes déjà une :
  il reste alors à l'ordinateur, et le message te le dit.
- **Bâtiments**, **Habitants** : la fonction de chaque bâtiment ; la liste des gens, et le bouton qui ouvre l'éditeur de chacun.
- **Apparence** : les tenues et les skins que peuvent porter les gens de la ville, par métier ou pour tout le monde. Ceux qui
  arrivent en reçoivent une ; « Habiller tout le monde » les distribue tout de suite (les personnages écrits à la main gardent la leur).
- **Réserves** : stocks, bourse, commandes en attente, et une rumeur à faire courir.

### L'éditeur d'un habitant

**Le bâton d'intendant en main, clic droit sur un habitant**, ou le bouton **Modifier** de sa fiche, ou le panneau. Comme dans un mod de PNJ :

- **Fiche** : nom, titre, sexe, âge, vie, traits. **Allure** : un skin importé, une tenue, une coiffure, une taille.
- **Stats** : points de vie, vitesse, dégâts, invincible, nom affiché. **Conduite** : sa journée, à son poste ou en ronde ; ses heures de
  travail ; s'il peut voyager, se marier, partir ; muet, normal ou bavard.
- **Métier** : il passe au bâtiment de ce métier, avec ses échanges. **Répliques** : ce qu'il dit, moment par moment. **Échanges** : ce
  qu'il vend et achète, composé avec les objets de ton sac.

### Les skins depuis internet

Dans l'onglet Allure, colle l'une de ces choses et clique sur **Importer** :

- le **pseudo** d'un joueur (`Notch`) ;
- une **page NameMC** (`https://namemc.com/skin/…`) ;
- le **lien direct d'une image** de skin, de n'importe quel site ;
- le **nom d'un fichier** déposé dans le dossier des skins (bouton **Dossier**).

**minecraftskins.com refuse les téléchargements automatiques** (vérifié : réponse 403) ; PlanetMinecraft refuse aussi l'accès automatique
à ses pages. Pour ces sites, enregistre l'image depuis ton navigateur dans le dossier, puis tape son nom. Le skin est envoyé au serveur
et distribué à tous les joueurs : rien à installer chez eux. Un habitant qui porte un skin est dessiné comme un joueur.

### La vie des habitants : échanges, caravanes, courrier

Tant qu'un joueur est à moins de 48 blocs de la ville, **ce qui se compte se voit** :

- **Les achats** : l'acheteur va jusqu'au vendeur, ils s'arrêtent, se parlent, et l'objet passe de main en main contre une pièce. Les
  réserves et les bourses bougent à cet instant-là.
- **Les livraisons et les approvisionnements** : le boulanger remet son pain au tavernier ; l'artisan va chercher ce qu'il lui faut.
- **Les causettes** : deux voisins s'arrêtent pour parler, surtout le soir.

Loin de tout joueur, la ville continue en comptes, une fois par jour.

**Les caravanes** partent toutes seules, jusqu'à quatre fois par jour de jeu : un marchand, ses lamas bâtés et, si la ville a une caserne,
une escorte. Elles emportent le surplus, le vendent là-bas à quelqu'un, et rapportent ce qui manque. Sans traité de commerce avec l'autre
royaume, on leur paie moitié moins ; en guerre, elles n'y vont pas. Sur la route, elles marchent vraiment, et **paraissent à moins de
64 blocs d'un joueur**. Elles sont sur **ta carte du royaume** (un chariot, et le trait de ce qu'il leur reste à faire) et dans l'onglet
En direct. D'autres habitants vont voir du pays. Tuer un marchand fait tomber sa cargaison, et toute sa ville le sait.

**Le courrier** (touche **J**, ou `/crusade courrier`) : un ami donne des nouvelles, un voyageur dit où il part, un marchand rend compte de
ses ventes, celui à qui tu as fait du tort te le dit. Une lettre t'attend si tu n'es pas là.

**Les commandes** : une ville qui manque de quelque chose t'écrit, et paie en émeraudes. Va parler à n'importe lequel de ses habitants
avec ce qu'il faut sur toi : le bouton **Livrer** s'allume.

### Les lignes de commerce : le monde commerce sans toi

Dans le panneau d'une ville, l'onglet **Commerce** ouvre une ligne vers n'importe quelle autre ville : la tienne, celle d'une nation, ou une ville de
l'ordinateur, même si elle n'existe que sur la carte. Tu choisis ce qu'on y envoie, combien, ce qu'on en rapporte, le rythme, l'escorte, et si les caravanes
tracent un chemin au sol. « Selon besoin » : le surplus du moment à l'aller, ce qui manque le plus au retour.

- **Tout marche sans joueur.** Les caravanes partent, marchent, vendent et rentrent, qu'on les voie ou non. Elles suivent leur route en temps réel : si tu
  t'approches à moins de 64 blocs, tu les trouves là où elles en sont.
- **Les prix sont vrais.** Une ville paie plus cher ce dont elle manque. Sans traité de commerce avec son royaume, on n'en tire que la moitié. Les marchands
  libres vont d'eux-mêmes là où ils vendront le mieux. Deux royaumes qui commercent s'apprécient davantage, tant que ça dure.
- **Les villes de l'ordinateur vivent aussi.** Celle qui n'a pas de murs a un *marché* : des réserves, une bourse, des marchands et des gardes, qui ne paraissent
  qu'en voyage. Elle produit ce que sa terre donne, s'épuise du reste, et ouvre seule ses lignes, vers ses voisines ou vers tes villes.
- **Les routes contournent** les lacs et les pentes, et préfèrent les routes qui existent déjà : un réseau se forme tout seul. On les voit sur la carte : en or
  les tiennes, plus discrètes celles de l'ordinateur ; un chariot par caravane en route.
- **Le chemin au sol** : sur une ligne qui le demande, l'herbe et la terre deviennent un chemin sous les pas de la caravane, jamais dans une ville. C'est le seul
  endroit où le commerce change des blocs de ton monde, et il se coupe ligne par ligne, à la création.

### Les gardes se battent

Ceux des casernes, des tours et des châteaux, et ceux des escortes : épée en main (arc pour ceux des tours), ils courent sur les monstres à portée. **Si tu frappes un
habitant, un garde ou un lama de caravane, les gardes de sa ville te tombent dessus** pendant une minute et demie, et te tapent vraiment, jusqu'à te tuer. Mort, tu es quitte. Pareil si ton royaume est en
guerre contre le leur. En créatif, ils t'ignorent. Une ville garde toujours au moins un garde chez elle.

### L'économie du monde

`/crusade economie`, le bouton **Économie** de la carte, ou celui de l'onglet Commerce d'une ville. On n'y voit que ce qu'on connaît : ses terres, et les royaumes découverts.

- **Villes** : leur royaume, leurs habitants, leur bourse, ce qui leur manque. Passe la souris sur une ligne : tout leur stock, et la prospérité d'un marché.
- **Prix** : choisis une marchandise : ce que paie chaque ville pour une charge de caravane, et où elle se vend le mieux. Une ville paie jusqu'au double de ce qu'elle n'a plus, et la moitié de ce qui déborde de ses réserves.
- **Lignes** : les plus rentables d'abord, avec leurs péages. **Routes** : ce qui s'est vendu ces seize derniers jours, et ceux qui marchent en ce moment. **Royaumes** : leur trésor, leurs villes, leur péage et leur douane.

### La guerre et le commerce

- **Une caravane surprise par la guerre fait demi-tour** avec sa charge.
- **Une route qui passe chez l'ennemi est coupée.** Sur la carte, elle devient rouge, et sa ligne attend la paix.
- **Le péage** : une caravane étrangère paie à chaque royaume qu'elle traverse, sauf traité de commerce, de passage ou alliance. **La douane** : le royaume prend sa part sur ce qu'un étranger vend chez lui, sauf traité de commerce. Les deux vont au trésor, et se règlent dans l'onglet Commerce de n'importe laquelle de ses villes. L'ordinateur fixe les siens selon son tempérament.
- **Le blocus** : en guerre, le bouton **Bloquer** de l'écran d'économie (onglet Villes) coupe une ville ennemie du monde, pour 8 pièces par jour, 3 blocus au plus. Plus de grain de la campagne, plus d'importations, plus de caravanes : ses réserves baissent jusqu'à la famine, et chaque jour de famine compte pour toi dans la guerre. L'ordinateur en fait autant, à toi aussi, pour 8 jours au plus. Un siège, une occupation ou une armée à moins de 96 blocs ont le même effet, sans rien coûter. Un anneau rouge le montre sur la carte.
- **Le camp du blocus** : le blocus se voit dans le monde. Dès qu'un joueur approche, des soldats se tiennent en cercle autour de la ville, un sur quatre portant la bannière de son royaume. Ils ne s'en prennent qu'aux joueurs en guerre contre leur royaume et à qui les frappe : un voyageur en paix passe, et les habitants ne risquent rien. **Tue-les tous dans la même journée et le blocus est brisé** ; au matin, ceux qui manquent sont remplacés. Ils ne laissent rien : ni objet, ni expérience.

### Les contrats

Onglet **Contrats** de l'écran d'économie. Chaque jour, les villes proposent du travail, si elles ont de quoi payer. Il faut être à moins de 96 blocs de la ville pour signer ; elle met alors la somme de côté : tu es sûr d'être payé, en émeraudes, si tu tiens parole. Trois contrats à la fois.

- **Porter un pli** : le pli scellé arrive dans ton sac, et le bas de l'écran te dit la distance et la direction. Il suffit d'arriver à moins de 32 blocs de la ville à temps.
- **Escorter une caravane** : elle part dès que tu signes. Il faut marcher à moins de 64 blocs d'elle pendant au moins 60 % du trajet. Sinon, pas de paie, et la ville reprend son argent.
- **Les commandes** : ce que les villes réclament, à apporter sur place : le bouton **Livrer** s'allume quand tu te tiens dans la ville, la marchandise sur toi. Celle qui n'en a plus du tout, ou qui a faim, paie le double.

Tenir parole fait monter ton estime auprès de ses habitants ; renoncer ou échouer la fait baisser.

### Le quai d'une colonie

Une colonie MineColonies d'un de tes royaumes entre dans le commerce : **`/crusade ville panneau`, ou le bâton d'intendant tourné vers le ciel, en te tenant dans la colonie**, ouvre son quai. Toutes les villes la proposent aussi comme destination.

- **Il lui faut un entrepôt bâti.** Ses réserves sont le reflet de ce qu'il contient, moins 32 de chaque marchandise, qu'on lui laisse pour vivre.
- **Une ligne en part ou y arrive pour de vrai** : le fer part de ses rayonnages, le pain y entre. Quand personne n'est là et que son entrepôt n'est pas chargé, ça attend sur le quai, et s'y écrit à ton retour.
- **Les objets enchantés, nommés ou usés ne se vendent jamais.**
- **L'argent** : la bourse du quai est tenue par le trésor de ton royaume, entre 50 et 200 pièces.
- L'ordinateur et les marchands libres ne livrent jamais une colonie d'office : seules les lignes que tu crées la touchent.

### Un marché devient une ville

Un marché de l'ordinateur gagne en prospérité : 2 points par caravane qui y vend ou qui y rentre, 1 ou 2 par jour si sa bourse est pleine, 3 de moins par jour de faim ou de blocus. À 60 (réglable), **il bâtit sa ville quand un joueur s'en approche à moins de 160 blocs** : son chantier s'ouvre, aux bâtiments du style de son royaume. Une fois finie, elle reçoit ses habitants d'elle-même, et reprend les réserves, la bourse et les lignes du marché. Commercer avec une ville de l'ordinateur la fait donc grandir pour de vrai.

Cela change le monde, bloc par bloc : réglage `lesMarchesBatissent` (activé). C'est défaisable depuis l'éditeur de la carte, et jamais là où des joueurs ont longtemps vécu, ni près d'une colonie, ni sur les terres d'un joueur ou dans une nation qu'il dirige.

### La carte de guerre
50. Ouvre la carte du royaume (touche **virgule**) et clique sur **⚔ Guerre**, en bas à droite. Une barre d'outils
    apparaît sous la carte : **Offensive**, **Défense**, **Objectif** (trois pinceaux qui peignent chunk par chunk,
    clic gauche maintenu), **Gomme**, **Flèche** (clique au départ, tire jusqu'à l'arrivée, relâche), **Repère**
    (objectif, ralliement, danger ou note, avec un petit texte) et **Retirer** (clique sur une de tes flèches ou un de
    tes repères). Le bouton **◯** change la taille du pinceau ; **Tout effacer** demande un second clic. Le clic droit
    déplace la carte, la molette zoome.
51. Seul le **souverain** trace le plan de son royaume. Tes **alliés**, ton **suzerain** et tes **vassaux** voient ton
    plan sur leur propre carte (sans pouvoir le toucher), et tu vois les leurs.
52. L'**état de la guerre** se dessine tout seul : les **sièges en cours** (⚔ qui bat, avec la garnison qui tient
    encore), la **ligne de front** en rouge entre ton camp et l'ennemi, les **provinces occupées** rayées à la couleur
    de l'occupant. La Table de guerre ouvre directement la carte de guerre (bouton **Carte**).
53. Sur Dynmap, coche la couche **Carte de guerre** : zones, flèches, repères et sièges y sont aussi, mis à jour
    toutes les 20 secondes. **Attention** : la page Dynmap est la même pour tous ceux qui ont son adresse ; si tu joues
    avec d'autres, ils y voient tes plans. Pour les cacher : `guerre = false` dans la section `carteWeb` des réglages.

### Une carte du vrai monde
54. La carte du royaume montre maintenant le **vrai paysage** : mers (plus sombres au large), montagnes, forêts,
    déserts, neige, relief ombré. Là où tu es passé, ce sont les **vraies couleurs** de la surface ; ailleurs, une
    **estimation** tirée du générateur du monde (sans tes constructions ni les villages).
55. Le bouton de vue passe de **politique** (les royaumes en voile sur le paysage) à **de jure**, puis à **terrain**
    (le paysage seul, frontières en trait).
56. Le paysage se prépare à part, **sans ralentir la partie** : il arrive par morceaux, d'abord grossier puis fin,
    autour de ce que tu regardes. La première fois, compte quelques secondes ; ensuite il est gardé dans
    `saves/<ton monde>/data/crusadecraft_terrain/` (tu peux effacer ce dossier sans rien perdre : il se refait).
57. La molette zoome **en douceur**, et la carte suit la souris quand tu la fais glisser.

### Les villes du royaume (plusieurs colonies)
58. Pour avoir plusieurs colonies MineColonies, il faut d'abord autoriser MineColonies à en donner plusieurs au même
    joueur : réglage `allowinfinitecolonies = true` dans `saves/<ton monde>/serverconfig/minecolonies-server.toml` (c'est un réglage
    de MineColonies, pas de CrusadeCraft).
59. Fonde ta deuxième colonie normalement. Tiens-toi **dedans**, ouvre le menu royal (onglet Royaume) et clique sur
    **Villes**, puis sur **Rattacher « nom de la colonie »**. (La Borne de frontière posée dans la colonie marche aussi.)
60. Dans l'écran **Villes**, chaque ville a trois boutons :
    - le **gouverneur** : « En direct » (tu la gouvernes toi-même) ; chaque clic la confie à la personne suivante de
      ton registre des lignées (Maj + clic : la précédente), jusqu'à revenir à « En direct » ;
    - **Impôts %** et **Levées %** : la part qui revient à la couronne quand la ville a un gouverneur (clic : +10,
      Maj + clic : −10). Le gouverneur garde le reste.
61. Ton rang fixe combien de villes tu tiens **en direct** sans pénalité (2 pour une baronnie, 3 pour un comté, 4 pour
    un duché, 6 pour un royaume, 8 pour un empire). Au-delà, chaque ville de trop rend 30 % d'impôts en moins (son nom
    est en rouge) : confie-la à un gouverneur, ou monte en rang. La capitale reste toujours à toi.
62. Les gouverneurs sont les personnes **vivantes** de ta maison, ou rattachées à ton royaume, dans le registre des
    lignées. Si un gouverneur meurt, sa ville revient en direct. Tu restes le propriétaire MineColonies de chaque
    colonie : le gouverneur la tient pour toi.

### Les gens de la cour
63. Dans le registre des lignées, choisis une personne et clique sur **Fiche** : prénom, âge, jusqu'à **quatre traits**
    de caractère (deux contraires, comme Brave et Lâche, ne vont pas ensemble) et une **biographie** libre.
64. Ceux qui vivent dans le monde (apparus avec Minecraft Comes Alive) ont leur journée : **à la cour** de ton royaume
    le jour (le centre de ta capitale), **chez eux** le soir. Quand ton royaume est **en guerre**, ils se réfugient à la
    cour, jour et nuit. Ils gardent leur propre intelligence : parfois, elle l'emporte.

### Fêtes, deuils et arbre de famille
63b. Dans l'écran de ta **Maison**, le bouton **Banquet** (son prix est écrit dessus) réunit la cour : du prestige,
    conseillers, maires et trois voisins invités contents. Pendant une demi-journée, les gens du registre qui vivent
    dans le monde se rassemblent à la cour (notes de musique au-dessus d'eux) et disent parfois une réplique dans le
    chat aux joueurs tout proches. Pas de banquet en guerre, ni en deuil, ni deux fois dans la semaine (réglable).
63c. À la mort d'un chef de maison, du souverain, de son conjoint ou de son héritier, la cour porte le **deuil** trois
    jours (réglable) ; le premier jour, tout le monde va aux funérailles.
63d. Dans le registre, une maison ouverte a le bouton **Arbre illustré** : toute la famille, génération par
    génération, qu'on fait glisser et qu'on zoome. Clique une personne : **Fiche**, **Apparence** (l'éditeur de
    Comes Alive, si son villageois est à moins de 48 blocs) et **Arbre Comes Alive**.
63e. La maison de ton royaume montre aussi, en bas, les **citoyens MineColonies** que tu as fait entrer dans ta
    maison (écran Maison) : les deux généalogies se voient l'une l'autre.
63f. Les répliques se changent par datapack : `data/<nom>/crusadecraft_repliques/*.json`, avec `moment` (`fete`,
    `deuil`, `guerre`, `cour`), au choix `roles` et `traits`, et `lines` (du texte, ou `{"key": "..."}`).

### Le temps qui passe
65. Toutes les **20 journées** du monde (réglable), chacun vieillit d'un an : les gens du registre, les souverains de
    l'ordinateur et leur conseil. Avant 50 ans, on meurt rarement ; ensuite, la vieillesse gagne ; personne ne passe
    100 ans. **Toi, tu ne vieillis pas.**
66. Quand le chef d'une maison meurt, l'héritier prend sa place selon la nouvelle loi **Succession** (menu royal,
    onglet Lois) : **primogéniture** (l'aîné des enfants, les fils d'abord), **égalitaire** (l'aîné, fille ou fils),
    **ancienneté** (le doyen de la maison) ou **élective** (l'adulte qui a le plus de traits).
67b. Une ville confiée passe à l'**héritier** de son gouverneur mort, aux mêmes conditions, s'il est encore de ta
    maison ou de ton royaume ; sinon elle revient en direct.
67c. Chez l'ordinateur, sous la loi égalitaire, un royaume d'au moins trois villes **se partage** : le cadet emporte les
    villes les plus lointaines et fonde une branche de la même maison. Sous les lois élective et d'ancienneté, une
    succession sur quatre tourne à la **crise** : un prétendant prend un tiers du royaume et lui déclare la guerre.
    Les royaumes que tu as posés à la main restent entiers (réglage `vie.royaumesFaitsMain`).
67. Quand un souverain de l'ordinateur meurt, son successeur monte sur le trône et la nouvelle passe dans les
    chroniques du monde. Pour garder tes personnages du registre en vie : `mortsDuRegistre = false` (section `vie`).

### Les royaumes qui bâtissent (à activer)
68. **Coupé au départ.** Section `civilisations` des réglages, `actif = true` : les royaumes de l'ordinateur posent
    d'eux-mêmes une tour, un camp, un avant-poste, puis un fort (duchés et au-delà), sur leurs propres terres, près de
    leurs villes, en chantier bloc par bloc.
69. Ils ne bâtissent que dans les chunks **déjà chargés**, jamais dans une colonie, seulement sur un sol naturel et
    plat, et à moins de 3 000 blocs du centre du monde (réglable). **Sauvegarde ton monde avant d'activer.**
70. Chaque bloc qu'ils remplacent est noté : `/crusade civ annuler` défait tout, et les blocs d'avant reviennent peu
    à peu. `/crusade civ` dit combien ils en ont posé.

70b. **Villages** : un royaume qui a un camp bâti depuis 10 jours fonde un **village** à 40–80 blocs de lui (un hameau
    de quatre maisons et un puits) : une ville de plus sur la carte, et deux familles au registre des lignées, qui
    apparaissent dans le monde avec Comes Alive. Tous les 8 jours, le village grandit d'un **quartier** de deux maisons
    (hameau, bourg, puis ville au troisième) et gagne des habitants. Deux villages au plus par royaume (réglable).
70c. Fonder un village à moins de 200 blocs des terres d'un autre royaume fait une **dispute** : l'opinion entre eux
    baisse, et la nouvelle passe dans les nouvelles du monde.
70d. `/crusade civ` liste chaque village (rang, quartiers, position). `/crusade civ annuler` défait aussi les villes,
    les quartiers et les habitants qu'ils avaient ajoutés.

### L'armée en chair et en os
71. Lève l'armée à la Table de guerre, puis va près d'un **camp militaire** bâti de ton royaume (bâton de bornage ›
    Camp militaire) et tape `/crusade armee rassembler` : tes levées apparaissent en vrais soldats (Hundred Years
    War), **un pour dix hommes**, vingt-quatre au plus.
72. `/crusade armee suivre` : ils te suivent. `/crusade armee tenir` : ils gardent l'endroit où tu es.
    `/crusade armee demobiliser` : les survivants rentrent chez eux.
73. **Les pertes sont réelles** : un soldat tué, ce sont dix hommes de moins dans ton armée.
73b. Tout se fait aussi depuis l'**écran de guerre** (Table de guerre), une fois l'armée levée : **Rassembler**,
    **Suivre**, **Tenir**, **Formation** (en masse, en ligne sur deux rangs, en colonne par deux, en carré) et
    **Démobiliser**. Par commande : `/crusade armee formation ligne`, `/crusade armee marcher <x> <z>`.
73c. Sur la carte du royaume, mode **⚔ Guerre**, l'outil **Marcher** : clique, et ton armée rassemblée y va dans sa
    formation. Les longues marches se font par étapes ; un soldat dans un chunk qui n'est pas chargé s'arrête.
73d. Ton armée rassemblée a son **pion** sur la carte (écu bordé d'or, son nombre d'hommes, un trait doré jusqu'à son
    but). En guerre, chaque royaume de l'ordinateur lève un **ost** : un pion à sa couleur qui part de sa capitale et
    marche — l'attaquant vers ce qu'il réclame, le défenseur à la rencontre de l'ost adverse. Sur Dynmap, couche
    **Armées**.
73e. Quand ton armée rassemblée arrive à moins de 48 blocs d'un ost ennemi, il **paraît en vrais soldats** et la
    bataille se livre. Tous tombés : l'ost est défait (gros bonus au score de la guerre, fatigue pour l'ennemi) et se
    reforme chez lui trois jours plus tard. Si ton armée s'éloigne, l'ost redevient un pion.

### Le commerce
75. Un **traité de commerce** (proposé ou accepté au Colombier) trace une **route** entre ta capitale et celle de
    l'autre royaume : en pointillés dorés sur la carte du royaume (survole-la pour voir ce qu'elle rapporte), et sur
    Dynmap dans la couche **Routes commerciales**.
76. Chaque jour, elle rapporte de l'or : la part de base d'avant, **plus une pièce tous les 400 blocs** de route (dix
    au plus). Une route lointaine vaut donc davantage. Les traités de commerce entre royaumes de l'ordinateur ont aussi
    leur route.

76b. Chaque royaume vend la **marchandise** de son pays, lue dans le biome de sa capitale (blé, bois, fer, sel, poisson,
    vin, épices, laine, fourrures, pierre). Deux marchandises différentes sur une route : **3 pièces de plus par jour**.
    Survole une route sur la carte pour voir « Blé ⇄ Fer ».
76c. Sur chaque route va et vient une **caravane** : un petit chariot sur la carte et sur Dynmap. Approche-toi à moins
    de 64 blocs de l'endroit où elle passe : elle paraît, un marchand ambulant à son nom et deux lamas, qui suivent la
    route.
76d. Tuer le marchand **pille** la caravane : son or tombe au sol (en pièces **State Marks** si tu as le mod, en or
    sinon), la route ne rapporte plus rien pendant 3 jours (réglable), et les deux royaumes de la route t'en veulent —
    sauf s'ils sont en guerre avec toi. Piller l'ennemi en temps de guerre est donc permis, et coûte cher à sa route.
76e. Avec le mod State Marks, ses pièces de bronze, d'argent et d'or valent 1, 9 et 81 pièces au **Coffre du Trésor**
    (comme pépite, lingot et bloc d'or) : on peut les déposer et les retirer (réglage `economie.marquesEtat`).

### Si le jeu rame
77. `/crusade perf` (opérateur) dit combien de temps chaque partie du mod prend à chaque tick du serveur, et
    `/crusade perf reset` remet les mesures à zéro. Un tick dure 50 ms : le total du mod doit rester bien en dessous.

## 4. Réglages

Tout se règle **en jeu** : menu royal, onglet Royaume, bouton **⚙** tout en haut à droite (ou `/crusade reglages`). Les sections à gauche,
leurs réglages à droite : oui/non d'un clic, nombres avec − et + (Maj : par dix) ou saisis puis ✔, choix qu'on fait
défiler. Survole un nom pour son explication. Réservé aux opérateurs et au propriétaire d'un monde solo ; chaque
changement est vérifié, enregistré et pris en compte aussitôt. Les listes (valeurs de l'or, limites par rang…) se
modifient encore dans le fichier `saves/<ton monde>/serverconfig/crusadecraft-server.toml`, monde fermé. Les sections
principales :

- `fondation`, `personnages` et `conseil` ;
- `economie` et `lois` ;
- `monde` (royaumes IA, et `editeurCarte` : qui peut ouvrir l'éditeur de la carte) ;
- `territoire` (bâton de bornage et constructions en survie, coût d'une case, distance, chantiers, plafond de chunks,
  rayon de `/crusade claim`, titres à l'entrée) ;
- `protection` (protection des terres, terres des IA, vassaux, alliés, opérateurs) ;
- `carteWeb` (Dynmap : fréquence, langue des fiches, royaumes IA, lieux, trésor, conseil, opacité, carte de guerre,
  routes de commerce, armées ; Xaero) ;
- `villes` (limite de domaine par rang, impôts perdus au-delà, parts de la couronne proposées pour un gouverneur) ;
- `pnj` (la journée des gens de la cour, répliques, jours entre deux banquets, jours de deuil) ;
- `vie` (années, jours par an, morts du registre, partages et crises des royaumes de l'ordinateur) ;
- `commerce` (caravanes, leur vitesse, jours sans revenu après un pillage) ;
- `civilisations` (coupé par défaut : constructions de l'ordinateur, fréquence, chance, nombre, zone permise, jours
  avant un village, jours entre deux quartiers, villages au plus) ;
- `diplomatie` ;
- `guerre` (sièges, raids, anti-grief, rang Hostile entre joueurs, troupes Hundred Years War, hommes par soldat et
  soldats au plus pour l'armée en chair et en os, ostes de l'ordinateur et leur vitesse) ;
- `ia` (fréquence des décisions, guerres entre IA et contre toi, répit après la fondation, coalitions) ;
- `intrigues` (événements, factions, complots, révoltes) ;
- `cour` (recherche exigée pour la Lice) ;
- `compatibilite` et `notifications`.

Chaque système peut être coupé.

## 5. Commandes d'administration (opérateur)

| Commande | Effet |
| --- | --- |
| `/crusade liste` | Les royaumes du monde |
| `/crusade info <joueur>` | Le royaume d'un joueur (sans nom : à qui est le chunk où tu te tiens, pour tous) |
| `/crusade colonie` | La colonie sous tes pieds et son royaume |
| `/crusade royaume [joueur]` | Le détail d'un royaume (l'ancien `/crusade info`) |
| `/crusade lieux` | Ouvre l'écran des lieux (pour tous) |
| `/crusade ici` | Où tu te tiens : place proche, région, royaume et suzerains (pour tous) |
| `/crusade claim`, `unclaim`, `map`, `info` | Le territoire (pour tous, voir plus haut) |
| `/crusade armee rassembler`, `suivre`, `tenir`, `demobiliser` | L'armée en chair et en os (pour le souverain) |
| `/crusade armee marcher <x> <z>`, `formation <nom>` | Envoyer l'armée, changer sa formation (pour le souverain) |
| `/crusade civ`, `/crusade civ annuler` | Les constructions de l'ordinateur : combien, et tout défaire |
| `/crusade perf`, `/crusade perf reset` | Le temps pris par chaque partie du mod |
| `/crusade reglages` | L'écran des réglages du monde (opérateurs, propriétaire d'un monde solo) |
| `/crusade plan annuler` | Défaire la dernière pose du plan d'architecte |
| `/crusade ville colonie` · `/crusade ville royaume` | Faire de la ville où l'on se tient sa colonie, ou y fonder son royaume |
| `/crusade plan dossier` | Le dossier où déposer ses fichiers .nbt et .schem |
| `/crusade renommer royaume <nom>`, `renommer ville <nom>` | Renommer son royaume, ou la ville où l'on se tient |
| `/crusade reconcilier` | Revérifie l'état des royaumes |
| `/crusade monde info` | Le monde IA |
| `/crusade monde generer` | Régénère les royaumes IA autour de toi |
| `/crusade monde decouvrir` | Révèle tous les royaumes IA |
| `/crusade dissoudre <royaume>` | Supprime un royaume (irréversible) |

Pour accélérer le temps pendant un test : `/time add 24000` fait passer un jour du monde.

## 6. Limites connues

- Les ostes de l'ordinateur marchent sur la carte, pas dans le monde : ils ne paraissent en vrais soldats que face à
  ton armée rassemblée. Leurs soldats sont des pillards (ou les bandits de Hundred Years War) : ils s'en prennent à
  tous les joueurs proches, pas seulement à l'ennemi.
- Les soldats ne marchent que dans les chunks chargés : loin de toi, ils s'arrêtent et reprennent quand tu reviens.
- Les soldats rassemblés oublient leur ordre au redémarrage du serveur : ils tiennent alors leur place.
- Xaero n'a pas d'API publique pour colorer des terres : CrusadeCraft passe par une petite greffe. Si une future
  version de Xaero la casse, la couche disparaît simplement (le jeu ne plante pas).
- La carte web Dynmap est la même pour tous ceux qui ont son adresse : pas de brouillard, et les plans de guerre y
  sont visibles de tous (la couche se coupe dans les réglages).
- La carte de guerre est un plan : elle ne commande pas les troupes. Les sièges, eux, sont ceux qui se jouent
  vraiment.
- Sur la carte en terrain, l'inexploré est une **estimation** : ni constructions ni structures, et les biomes des mods
  sont colorés par famille (forêt, désert, neige…), donc approximativement. Le Nether n'a pas de carte en terrain.
- Les gens de la cour ont un but de marche, pas une laisse : l'intelligence de Minecraft Comes Alive peut l'emporter.
- Les royaumes de l'ordinateur bâtissent sur plans (tours, camps, forts, villages et quartiers), pas de façon libre :
  il leur faut une parcelle à peu près plate de leurs terres.
- Les caravanes ne marchent dans le monde que près d'un joueur ; loin de tout, elles avancent sur la carte. Après un
  redémarrage, elles repartent de leur première capitale, et un pillage en cours est oublié.
- Une marchandise par royaume, pas de stocks ni de marché : le commerce reste une affaire de revenus.
- Le quai d'une colonie écrit dans son entrepôt au moment où une caravane charge ou décharge, si l'entrepôt est chargé ; sinon, cela attend sur le quai jusqu'au retour d'un joueur. Seul le
  premier entrepôt chargé compte, et il faut que quelqu'un soit assez près pour que son chunk soit chargé.
- Une escorte ne se bat que contre ce qui s'en prend à elle : il n'y a pas de bandits sur les routes (Romain n'en veut pas), le danger vient des monstres et de la guerre.
- Les soldats d'un camp de blocus sont ceux des autres guerres du mod : les bandits de Hundred Years War s'il est là, sinon des pillards et des vindicateurs ; en mode paisible, où les monstres s'effacent, des golems. Il n'y a ni tentes ni feux de camp : le blocus ne pose aucun bloc dans le monde.
- Les routes sont tracées d'après le terrain que le générateur prévoit : elles ne savent rien de ce qu'on a bâti, et un obstacle réel fait franchir un ressaut d'un
  petit saut. Pas de ponts : une rivière trop large se traverse à pied, ou d'un saut.
- Les marchés de l'ordinateur sont des comptes : pas de bâtiments, personne à y voir. Un voyageur qui y arrive se promène sur le siège de la ville.
- Les gardes frappent au corps à corps et tirent à l'arc, sans plus : pas de formation, pas de retraite. Ils ne se battent que près d'un joueur.
- Les échanges face à face ne se jouent que près d'un joueur ; loin, tout passe en comptes. Si l'acheteur n'arrive pas jusqu'au vendeur en
  quarante secondes sous tes yeux, la vente se fait quand même, sans la scène ; sans témoin, il se retrouve à côté de lui.
- Les habitants suivent des règles et des textes écrits d'avance : ils ne pensent pas.
- Un skin importé remplace tout le dessin de Comes Alive sur ce villageois : il n'a plus les vêtements ni les traits de Comes Alive.
- minecraftskins.com et PlanetMinecraft refusent les téléchargements automatiques : passer par le dossier des skins.
- Usage personnel : ce mod n'est pas fait pour être redistribué.
