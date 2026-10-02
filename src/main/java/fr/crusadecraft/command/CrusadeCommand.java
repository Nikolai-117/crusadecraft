package fr.crusadecraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * {@code /crusade} : debug et administration uniquement (permission 2).
 *
 * <p>Le jeu normal passe par les blocs, objets et écrans. Rien ici n'est nécessaire pour jouer.
 */
public final class CrusadeCommand {

    private CrusadeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("crusade")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("liste").executes(CrusadeCommand::list))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("joueur", EntityArgument.player())
                                .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "joueur")))))
                .then(Commands.literal("colonie").executes(CrusadeCommand::colonyHere))
                .then(Commands.literal("reconcilier").executes(ctx -> {
                    RealmService.reconcileAll(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.command.reconciled"), true);
                    return 1;
                }))
                .then(Commands.literal("dissoudre")
                        .then(Commands.argument("royaume", StringArgumentType.greedyString())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        CrusadeSavedData.get(ctx.getSource().getServer()).realms().stream().map(Realm::name), b))
                                .executes(CrusadeCommand::dissolve)))
                .then(Commands.literal("monde")
                        .then(Commands.literal("info").executes(CrusadeCommand::worldInfo))
                        .then(Commands.literal("generer").executes(ctx -> {
                            // Débogage : régénère les royaumes IA autour du joueur (efface les anciens).
                            var player = ctx.getSource().getPlayerOrException();
                            int n = fr.crusadecraft.core.world.WorldService.generate(ctx.getSource().getServer(), player.serverLevel(),
                                    player.getBlockX(), player.getBlockZ());
                            ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.command.world.generated", n), true);
                            return n;
                        }))
                        .then(Commands.literal("decouvrir").executes(ctx -> {
                            int n = fr.crusadecraft.core.world.WorldService.discoverAll(ctx.getSource().getPlayerOrException());
                            ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.command.world.discovered", n), true);
                            return n;
                        }))));
    }

    private static int worldInfo(CommandContext<CommandSourceStack> ctx) {
        var world = CrusadeSavedData.get(ctx.getSource().getServer()).world();
        if (!world.generated()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.command.world.none"), false);
            return 0;
        }
        for (var r : world.realms()) {
            var capital = r.capital();
            ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.command.world.line",
                    fr.crusadecraft.core.world.AiNames.realm(r.rank(), r.place()), Component.translatable(r.personality().key()),
                    r.provinces().size(), capital == null ? "?" : capital.seat().toShortString(), r.discoveredBy().size()), false);
        }
        return world.realms().size();
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var realms = CrusadeSavedData.get(ctx.getSource().getServer()).realms();
        if (realms.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.command.no_realms"), false);
            return 0;
        }
        for (Realm r : realms) {
            ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.command.list_line",
                    r.name(), Component.translatable(r.rank().titleKey()), r.rulerName(),
                    r.capital().toString(), Component.translatable(r.state().key())), false);
        }
        return realms.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        Optional<Realm> realm = RealmService.realmOf(ctx.getSource().getServer(), target.getUUID());
        if (realm.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("crusadecraft.command.no_realm_for", target.getGameProfile().getName()));
            return 0;
        }
        Realm r = realm.get();
        ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.command.info",
                r.name(), Component.translatable(r.rank().titleKey()), r.house(), r.motto(),
                r.capital().toString(), Component.translatable(r.state().key()),
                r.gold(), r.prestige(), r.piety(), r.dread()), false);
        return 1;
    }

    private static int colonyHere(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos pos = player.blockPosition();
        Optional<ColonyRef> ref = MineColoniesBridge.colonyAt(player.level(), pos);
        if (ref.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("crusadecraft.command.no_colony_here",
                    MineColoniesBridge.isReady()));
            return 0;
        }
        ColonyInfo info = MineColoniesBridge.info(ref.get());
        boolean inTownHall = MineColoniesBridge.isInsideTownHall(ref.get(), pos);
        boolean owner = MineColoniesBridge.isOwner(ref.get(), player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.command.colony",
                info.name(), ref.get().toString(), info.townHallLevel(), info.citizens(), info.maxCitizens(),
                String.format(java.util.Locale.ROOT, "%.1f", info.happiness()), info.buildings(), inTownHall, owner), false);
        return 1;
    }

    private static int dissolve(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "royaume");
        var data = CrusadeSavedData.get(ctx.getSource().getServer());
        Optional<Realm> realm = data.realms().stream().filter(r -> r.name().equalsIgnoreCase(name)).findFirst();
        if (realm.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("crusadecraft.command.unknown_realm", name));
            return 0;
        }
        RealmService.dissolve(ctx.getSource().getServer(), realm.get().id());
        ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.command.dissolved", realm.get().name()), true);
        return 1;
    }
}
