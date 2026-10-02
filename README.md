# CrusadeCraft

Un Crusader Kings dans Minecraft, branché sur MineColonies. Usage personnel, ne pas redistribuer.

- Minecraft 1.20.1, Forge 47.4.10, Java 17
- Dépendance : MineColonies 1.20.1-1.1.1214 (et Structurize, BlockUI, Domum Ornamentum)
- Documents : `docs/etape-0-rapport.md` (étude), `docs/decisions.md`, `docs/etape-2-plan.md` (phases)

## Construire et tester

Le JDK 17 du projet est dans `E:\java\` (voir `gradle.properties`). Dans Git Bash :

```bash
export JAVA_HOME="/e/java/jdk-17.0.20.1+1"
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
