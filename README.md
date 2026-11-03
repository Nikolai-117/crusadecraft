# CrusadeCraft

Un Crusader Kings dans Minecraft, branché sur MineColonies. Usage personnel, ne pas redistribuer.

- Minecraft 1.20.1, Forge 47.4.10, Java 17
- Dépendance : MineColonies 1.20.1-1.1.1214 (et Structurize, BlockUI, Domum Ornamentum)
- Documents : `docs/etape-0-rapport.md` (étude), `docs/decisions.md`, `docs/etape-2-plan.md` (phases)

## Construire et tester

Il faut un JDK 17, donné par `JAVA_HOME` (chez Romain : `E:\java\jdk-17.0.20.1+1`). Dans Git Bash :

```bash
export JAVA_HOME="/e/java/jdk-17.0.20.1+1"   # ou le chemin de ton JDK 17
./gradlew build                # compile, gardes d'architecture, tests unitaires → build/libs/
./gradlew runGameTestServer    # tests en monde réel avec MineColonies (monde jetable)
./gradlew runUiShots           # captures des écrans dans run/screenshots/
./gradlew runClient            # client de développement
```

## Installer dans une instance

Copier `build/libs/crusadecraft-<version>.jar` dans le dossier `mods/` de l'instance. Faire d'abord une
copie du monde, et retirer War 'N Taxes et War 'n Nobility s'ils sont présents.

Retirer CrusadeCraft d'un monde ne modifie aucune colonie : ses données vivent à part, dans
`saves/<monde>/data/crusadecraft.dat`.

## Travailler à deux

Le dépôt est privé : on n'y met que notre code. Les mods des autres (leurs jars, leurs plans) n'y sont jamais :
chacun les prend de son côté, et le mod lui-même ne se redistribue pas.

1. Un JDK 17 (par exemple Eclipse Temurin 17), et `JAVA_HOME` qui pointe dessus.
2. Les jars des compatibilités, à télécharger soi-même (CurseForge ou Modrinth) et à poser dans `libs/local/`,
   **avec exactement ces noms** :

   | Fichier dans `libs/local/` | Mod | Obligatoire ? |
   |---|---|---|
   | `minecraft-comes-alive-7.6.16+1.20.1-universal.jar` | Minecraft Comes Alive Reborn 7.6.16 | oui (compile) |
   | `architectury-9.2.14-forge.jar` | Architectury API 9.2.14 | oui (avec MCA) |
   | `Dynmap-3.7-beta-6-forge-1.20.jar` | Dynmap 3.7-beta-6 | oui (compile) |
   | `xaeroworldmap-1.39.9.jar`, `xaerominimap-25.2.6.jar` | Xaero's World Map 1.39.9, Minimap 25.2.6 | oui (compile) |
   | `Byzantine-1.21.1-51.1.jar` | Byzantine (pack de style) | non : `-Pwith_byzantine=false` |
   | `dawnoftimebuilder-1.5.51.jar`, `decorative_blocks-4.1.3.jar` | Dawn of Time Builder, Decorative Blocks | non (blocs des villes importées) |

   MineColonies, Structurize, BlockUI et Domum Ornamentum viennent tout seuls (Gradle les télécharge).
3. `./gradlew build` puis `./gradlew runGameTestServer` : tout doit passer avant de pousser.
4. Une branche par phase (`phase/pNN-…`), un commit par étape, en français ; on fusionne dans `main` quand c'est
   vérifié. Les décisions vont dans `docs/decisions.md`, le mode d'emploi dans `docs/GUIDE.md`.
5. Les villes données par Romain (Dolhimluith, Grunbach) ne sont pas dans le dépôt : il les envoie à part, à
   ranger dans `src/main/resources/crusadecraft_plans/Levenzia/` et `…/Varenhold/` (ignorés par git).

