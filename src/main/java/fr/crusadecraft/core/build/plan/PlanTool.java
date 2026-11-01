package fr.crusadecraft.core.build.plan;

import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.item.PlanItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Le plan d'architecte côté serveur (P34) : le catalogue, le choix d'un plan, la rotation, la pose. Tout est
 * revérifié ici : seul un joueur en créatif ou qui a l'éditeur de la carte pose quoi que ce soit.
 */
public final class PlanTool {
    /** L'étagère des villes entières, en tête du catalogue. */
    public static final String TOWNS = "@villes";
    private static final String TOWN_PREFIX = "town:";

    private PlanTool() {}

    public static boolean allowed(ServerPlayer player) {
        return player.isCreative() || WorldEditService.canEdit(player);
    }

    /** « town:CHINOISE:BOURG » : une ville entière d'un style et d'une taille. */
    public static String townRef(NationStyle style, TownSize size) {
        return TOWN_PREFIX + style.name() + ":" + size.name();
    }

    /** Les villes entières qu'on peut poser : chaque style dont un pack est installé, de hameau en capitale. */
    public static List<PlanLibrary.Entry> towns() {
        List<PlanLibrary.Entry> out = new ArrayList<>();
        for (NationStyle style : NationStyle.values()) {
            Optional<String> pack = PlanLibrary.packFor(style);
            if (pack.isEmpty()) continue;
            for (TownSize size : TownSize.values()) {
                if (size == TownSize.QUARTIER) continue;
                out.add(new PlanLibrary.Entry(townRef(style, size), pack.get(), style.name()));
            }
        }
        return out;
    }

    public static List<PlanLibrary.Source> sources() {
        List<PlanLibrary.Source> out = new ArrayList<>();
        out.add(new PlanLibrary.Source(TOWNS, "", towns().size()));
        out.addAll(PlanLibrary.sources());
        return out;
    }

    public static List<PlanLibrary.Entry> entries(String source) {
        return TOWNS.equals(source) ? towns() : PlanLibrary.entries(source);
    }

    /** Choisit ce que l'outil tenu posera. */
    public static Outcome choose(ServerPlayer player, String ref) {
        if (!allowed(player)) return Outcome.no("crusadecraft.plan.denied");
        Optional<ItemStack> held = PlanItem.held(player);
        if (held.isEmpty()) return Outcome.no("crusadecraft.plan.not_held");
        int rotation = PlanItem.choice(held.get()).map(PlanItem.Choice::rotation).orElse(0);
        if (ref.startsWith(TOWN_PREFIX)) {
            String[] parts = ref.substring(TOWN_PREFIX.length()).split(":");
            if (parts.length != 2) return Outcome.no("crusadecraft.plan.unreadable");
            NationStyle style = NationStyle.byName(parts[0], null);
            TownSize size;
            try {
                size = TownSize.valueOf(parts[1]);
            } catch (IllegalArgumentException e) {
                size = null;
            }
            if (style == null || size == null) return Outcome.no("crusadecraft.plan.unreadable");
            long seed = player.getRandom().nextLong();
            Optional<TownPlan> town = TownPlanner.plan(style, size, seed);
            if (town.isEmpty()) return Outcome.no("crusadecraft.town.no_pack");
            Component name = Component.translatable("crusadecraft.plan.town_name", Component.translatable(style.key()), Component.translatable(size.key()));
            PlanItem.setChoice(held.get(), new PlanItem.Choice("", name.getString(), town.get().extent(), 32, town.get().extent(), 0, rotation, true,
                    style.ordinal(), size.ordinal(), seed));
            return Outcome.ok(Component.translatable("crusadecraft.plan.chosen.town", name, town.get().pieces().size()));
        }
        Optional<PlanRef> r = PlanRef.decode(ref);
        if (r.isEmpty()) return Outcome.no("crusadecraft.plan.unreadable");
        Optional<Plan> plan = PlanLibrary.load(r.get(), true);
        if (plan.isEmpty()) return Outcome.no("crusadecraft.plan.unreadable");
        Plan p = plan.get();
        PlanItem.setChoice(held.get(), new PlanItem.Choice(ref, p.name(), p.sizeX(), p.sizeY(), p.sizeZ(), p.groundY(), rotation, false, 0, 0, 0));
        return Outcome.ok(Component.translatable("crusadecraft.plan.chosen", p.name(), p.sizeX(), p.sizeY(), p.sizeZ()));
    }

    public static void rotate(ServerPlayer player, ItemStack stack) {
        PlanItem.rotate(stack);
        int rot = PlanItem.choice(stack).map(PlanItem.Choice::rotation).orElse(0);
        player.displayClientMessage(Component.translatable("crusadecraft.plan.rotated", rot * 90), true);
    }

    /** Pose le plan choisi, son sol au niveau du bloc visé. */
    public static void paste(ServerPlayer player, ItemStack stack, BlockPos ground) {
        if (!allowed(player)) {
            player.displayClientMessage(Component.translatable("crusadecraft.plan.denied"), true);
            return;
        }
        Optional<PlanItem.Choice> c = PlanItem.choice(stack);
        if (c.isEmpty()) {
            player.displayClientMessage(Component.translatable("crusadecraft.plan.tooltip.none"), true);
            return;
        }
        PlanItem.Choice choice = c.get();
        if (choice.town()) {
            Optional<TownPlan> town = TownPlanner.plan(NationStyle.byOrdinal(choice.style()), TownSize.byOrdinal(choice.size()), choice.seed());
            if (town.isEmpty()) {
                player.displayClientMessage(Component.translatable("crusadecraft.town.no_pack"), true);
                return;
            }
            Journals.paste(player, ground, choice.rotation(), null, town.get(), choice.name());
        } else {
            if (PlanRef.decode(choice.ref()).flatMap(r -> PlanLibrary.load(r, true)).isEmpty()) {
                player.displayClientMessage(Component.translatable("crusadecraft.plan.unreadable"), true);
                return;
            }
            Journals.paste(player, ground, choice.rotation(), choice.ref(), null, choice.name());
        }
        player.sendSystemMessage(Component.translatable("crusadecraft.plan.pasting", choice.name()));
    }

    /** Défait la dernière pose du joueur. */
    public static Outcome undoLast(ServerPlayer player) {
        if (!allowed(player)) return Outcome.no("crusadecraft.plan.denied");
        return Journals.undoLast(player).isPresent() ? Outcome.ok(Component.translatable("crusadecraft.plan.undone"))
                : Outcome.no("crusadecraft.plan.nothing_to_undo");
    }
}
