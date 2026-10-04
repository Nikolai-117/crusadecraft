# CrusadeCraft 1.4.0 — installer et tester

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

1. Le fichier du mod est `E:\crusadecraft\build\libs\crusadecraft-1.4.0.jar`.
2. Mets-le dans le dossier des mods de l'instance : `D:\MultiMC\instances\1.20.1\.minecraft\mods\`.
   Dans MultiMC, tu peux aussi faire clic droit sur l'instance → *Modifier* → *Mods* → *Ajouter un fichier*.
   **Retire l'ancien** (`crusadecraft-1.3.0.jar`, ou une version plus vieille) : deux versions du mod ensemble
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

## 4. Réglages

Tout se règle dans `saves/<ton monde>/serverconfig/crusadecraft-server.toml`, à modifier monde fermé. Les
sections principales :

- `fondation`, `personnages` et `conseil` ;
- `economie` et `lois` ;
- `monde` (royaumes IA, et `editeurCarte` : qui peut ouvrir l'éditeur de la carte) ;
- `territoire` (bâton de bornage et constructions en survie, coût d'une case, distance, chantiers) ;
- `diplomatie` ;
- `guerre` (sièges, raids, anti-grief, rang Hostile entre joueurs, troupes Hundred Years War) ;
- `ia` (fréquence des décisions, guerres entre IA et contre toi, répit après la fondation, coalitions) ;
- `intrigues` (événements, factions, complots, révoltes) ;
- `cour` (recherche exigée pour la Lice) ;
- `compatibilite` et `notifications`.

Chaque système peut être coupé.

## 5. Commandes d'administration (opérateur)

| Commande | Effet |
| --- | --- |
| `/crusade liste` | Les royaumes du monde |
| `/crusade info [joueur]` | Le détail d'un royaume |
| `/crusade colonie` | La colonie sous tes pieds et son royaume |
| `/crusade lieux` | Ouvre l'écran des lieux |
| `/crusade ici` | Où tu te tiens : place proche, région, royaume et suzerains |
| `/crusade reconcilier` | Revérifie l'état des royaumes |
| `/crusade monde info` | Le monde IA |
| `/crusade monde generer` | Régénère les royaumes IA autour de toi |
| `/crusade monde decouvrir` | Révèle tous les royaumes IA |
| `/crusade dissoudre <royaume>` | Supprime un royaume (irréversible) |

Pour accélérer le temps pendant un test : `/time add 24000` fait passer un jour du monde.

## 6. Limites connues

- Les armées IA ne sont pas pilotées par les commandes de Hundred Years War, qui n'a pas d'API publique. Ses
  troupes servent en revanche aux sièges et aux raids.
- Pas d'intégration aux cartes de Xaero, qui n'ont pas d'API pour colorer des territoires. La Table de guerre et
  la carte du mod en tiennent lieu.
- Usage personnel : ce mod n'est pas fait pour être redistribué.
