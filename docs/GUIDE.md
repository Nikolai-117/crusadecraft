# CrusadeCraft 1.0.0 — installer et tester

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

1. Le fichier du mod est `E:\crusadecraft\build\libs\crusadecraft-1.0.0.jar`.
2. Mets-le dans le dossier des mods de l'instance : `D:\MultiMC\instances\1.20.1\.minecraft\mods\`.
   Dans MultiMC, tu peux aussi faire clic droit sur l'instance → *Modifier* → *Mods* → *Ajouter un fichier*.
3. Lance l'instance comme d'habitude.

**Avant de jouer sur un monde qui compte, fais une sauvegarde de ce monde** (copie de son dossier dans
`D:\MultiMC\instances\1.20.1\.minecraft\saves\`). Le plus simple pour un premier essai : **un nouveau monde de
test**.

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

## 4. Réglages

Tout se règle dans `saves/<ton monde>/serverconfig/crusadecraft-server.toml`, à modifier monde fermé. Les
sections principales :

- `fondation`, `personnages` et `conseil` ;
- `economie` et `lois` ;
- `monde` (royaumes IA) ;
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
