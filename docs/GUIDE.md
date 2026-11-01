# CrusadeCraft 3.3.1 — installer et tester

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

1. Le fichier du mod est `E:\crusadecraft\build\libs\crusadecraft-3.3.1.jar`.
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
    Une **ville entière** posée à l'outil devient une ville du monde : sur la carte, avec ses habitants. Posée sur les
    terres d'un royaume, elle le rejoint ; sur une terre libre, elle **fonde une nation** de son style, dont elle est
    la capitale. On la défait comme les autres (« Défaire la ville » dans l'éditeur). Pour composer ta propre ville,
    pose les bâtiments un à un depuis les packs : chaque pose se défait.
53m. **Tes propres plans** : dépose tes `.nbt` (bloc de structure) et `.schem` (WorldEdit) dans
    `crusadecraft/plans/` du dossier du jeu (`/crusade plan dossier` donne le chemin). Tes packs Structurize (scans du
    dossier `blueprints/`) apparaissent d'eux-mêmes dans le catalogue.

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
- Usage personnel : ce mod n'est pas fait pour être redistribué.
