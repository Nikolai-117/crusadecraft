package fr.crusadecraft.core.treasury;

import java.util.List;

/**
 * Barèmes par défaut, en or par jour du monde. Les proportions suivent War 'N Taxes (l'Hôtel de ville et
 * l'Université rapportent le plus) ; l'échelle est réduite à une journée et à l'or du Coffre du Trésor.
 */
public final class EconomyDefaults {

    public static final List<String> TAX_RATES = List.of(
            "townhall=3:1",
            "university=2:1",
            "mysticalsite=2:1",
            "hospital=1.5:0.75",
            "tavern=1.5:0.75",
            "blacksmith=1.5:0.75",
            "enchanter=1.5:0.75",
            "smeltery=1.5:0.75",
            "library=1:0.75",
            "school=1:0.75",
            "warehouse=1:0.5",
            "residence=0.5:0.25",
            "postbox=0:0",
            "stash=0:0",
            "barracks=0:0",
            "barrackstower=0:0",
            "guardtower=0:0",
            "archery=0:0",
            "combatacademy=0:0",
            "gatehouse=0:0");

    public static final List<String> UPKEEP_RATES = List.of(
            "barracks=2:1",
            "barrackstower=1:0.5",
            "guardtower=1:0.5",
            "archery=1.5:0.5",
            "combatacademy=1.5:0.5",
            "gatehouse=0.5:0.5");

    private EconomyDefaults() {}
}
