package fr.crusadecraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
                // Pour tous les joueurs (P16) : le territoire, comme dans Factions.
                .then(Commands.literal("claim")
                        .executes(ctx -> say(ctx, fr.crusadecraft.core.world.ClaimService.claim(ctx.getSource().getPlayerOrException(), 0)))
                        .then(Commands.argument("rayon", IntegerArgumentType.integer(0, 10))
                                .executes(ctx -> say(ctx, fr.crusadecraft.core.world.ClaimService.claim(ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "rayon"))))))
                .then(Commands.literal("unclaim")
                        .executes(ctx -> say(ctx, fr.crusadecraft.core.world.ClaimService.unclaim(ctx.getSource().getPlayerOrException(), 0)))
                        .then(Commands.argument("rayon", IntegerArgumentType.integer(0, 10))
                                .executes(ctx -> say(ctx, fr.crusadecraft.core.world.ClaimService.unclaim(ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "rayon"))))))
                .then(Commands.literal("map").executes(ctx -> lines(ctx, fr.crusadecraft.core.world.ClaimService.map(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("info")
                        .executes(ctx -> lines(ctx, fr.crusadecraft.core.world.ClaimService.info(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("joueur", EntityArgument.player()).requires(src -> src.hasPermission(2))
                                .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "joueur")))))
                // Administration : opérateurs seulement.
                .then(Commands.literal("royaume").requires(src -> src.hasPermission(2))
                        .executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("joueur", EntityArgument.player())
                                .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "joueur")))))
                .then(Commands.literal("liste").requires(src -> src.hasPermission(2)).executes(CrusadeCommand::list))
                .then(Commands.literal("colonie").requires(src -> src.hasPermission(2)).executes(CrusadeCommand::colonyHere))
                .then(Commands.literal("lieux").executes(ctx -> {
                    fr.crusadecraft.network.packet.LieuPackets.send(ctx.getSource().getPlayerOrException(), null, true, null, true);
                    return 1;
                }))
                .then(Commands.literal("ici").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    Component chain = fr.crusadecraft.core.place.PlaceService.chainAt(ctx.getSource().getServer(),
                            player.level().dimension().location(), player.getX(), player.getZ());
                    ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.lieu.here", chain), false);
                    return 1;
                }))
                .then(Commands.literal("lignees").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    fr.crusadecraft.network.packet.LineagePackets.send(ctx.getSource().getPlayerOrException(), null, true);
                    return 1;
                }))
                .then(Commands.literal("reconcilier").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    RealmService.reconcileAll(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.command.reconciled"), true);
                    return 1;
                }))
                .then(Commands.literal("dissoudre").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("royaume", StringArgumentType.greedyString())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        CrusadeSavedData.get(ctx.getSource().getServer()).realms().stream().map(Realm::name), b))
                                .executes(CrusadeCommand::dissolve)))
                // Les levées en chair et en os (P23), pour le souverain.
                .then(Commands.literal("armee")
                        .then(Commands.literal("rassembler").executes(ctx -> say(ctx,
                                fr.crusadecraft.core.war.ArmyService.muster(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("suivre").executes(ctx -> say(ctx, fr.crusadecraft.core.war.ArmyService.order(
                                ctx.getSource().getPlayerOrException(), fr.crusadecraft.core.war.ArmyService.Order.FOLLOW))))
                        .then(Commands.literal("tenir").executes(ctx -> say(ctx, fr.crusadecraft.core.war.ArmyService.order(
                                ctx.getSource().getPlayerOrException(), fr.crusadecraft.core.war.ArmyService.Order.HOLD))))
                        .then(Commands.literal("demobiliser").executes(ctx -> say(ctx,
                                fr.crusadecraft.core.war.ArmyService.dismiss(ctx.getSource().getPlayerOrException()))))
                        // P27 : marcher vers un point, en formation.
                        .then(Commands.literal("marcher")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> say(ctx, fr.crusadecraft.core.war.ArmyService.march(ctx.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "z")))))))
                        .then(Commands.literal("formation")
                                .then(Commands.argument("nom", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(fr.crusadecraft.core.war.Formation.values())
                                                .map(f -> f.name().toLowerCase(java.util.Locale.ROOT)), b))
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "nom").toUpperCase(java.util.Locale.ROOT);
                                            for (fr.crusadecraft.core.war.Formation f : fr.crusadecraft.core.war.Formation.values()) {
                                                if (f.name().equals(name)) return say(ctx, fr.crusadecraft.core.war.ArmyService.formation(
                                                        ctx.getSource().getPlayerOrException(), f));
                                            }
                                            ctx.getSource().sendFailure(Component.translatable("crusadecraft.army.formation.unknown"));
                                            return 0;
                                        }))))
                // Ce que coûte chaque système sur le serveur (P24).
                // Renommer son royaume, ou la ville où l'on se tient (P32).
                .then(Commands.literal("renommer")
                        .then(Commands.literal("royaume").then(Commands.argument("nom", StringArgumentType.greedyString())
                                .executes(ctx -> say(ctx, fr.crusadecraft.core.realm.NameService.renameRealm(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "nom"))))))
                        .then(Commands.literal("ville").then(Commands.argument("nom", StringArgumentType.greedyString())
                                .executes(ctx -> say(ctx, fr.crusadecraft.core.realm.NameService.renameColonyHere(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "nom")))))))
                // P34 : le plan d'architecte — défaire sa dernière pose, voir où déposer ses fichiers.
                .then(Commands.literal("plan")
                        .then(Commands.literal("annuler").executes(ctx -> say(ctx, fr.crusadecraft.core.build.plan.PlanTool.undoLast(
                                ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("dossier").executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.plan.folder",
                                    fr.crusadecraft.core.build.plan.PlanLibrary.folderForDisplay()), false);
                            return 1;
                        })))
                // L'écran des réglages du monde (P30) ; il dit lui-même s'il est réservé.
                .then(Commands.literal("reglages").executes(ctx -> {
                    fr.crusadecraft.network.packet.SettingsPackets.send(ctx.getSource().getPlayerOrException(), true);
                    return 1;
                }))
                .then(Commands.literal("perf").requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            var lines = fr.crusadecraft.core.Perf.report();
                            long ticks = fr.crusadecraft.core.Perf.ticks();
                            ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.perf.header", ticks, ticks / 20), false);
                            if (lines.isEmpty()) ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.perf.none"), false);
                            double total = 0;
                            for (var l : lines) {
                                total += l.msPerTick();
                                String perTick = String.format(java.util.Locale.ROOT, "%.3f", l.msPerTick());
                                String max = String.format(java.util.Locale.ROOT, "%.2f", l.maxMs());
                                ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.perf.line", l.system(), perTick, max, l.calls()), false);
                            }
                            String sum = String.format(java.util.Locale.ROOT, "%.3f", total);
                            ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.perf.total", sum), false);
                            return lines.size();
                        })
                        .then(Commands.literal("reset").executes(ctx -> {
                            fr.crusadecraft.core.Perf.reset();
                            ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.perf.reset"), false);
                            return 1;
                        })))
                // Les civilisations autonomes (P22) : où elles en sont, et tout défaire.
                .then(Commands.literal("civ").requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            var server = ctx.getSource().getServer();
                            long auto = CrusadeSavedData.get(server).world().sites().stream().filter(fr.crusadecraft.core.build.Site::auto).count();
                            boolean on = fr.crusadecraft.config.CrusadeConfig.SERVER.civActive.get();
                            ctx.getSource().sendSuccess(() -> Component.translatable(on ? "crusadecraft.civ.info.on" : "crusadecraft.civ.info.off", auto), false);
                            // P28 : chaque village de l'ordinateur, son rang et ses quartiers.
                            for (Component line : fr.crusadecraft.core.ai.VillageService.report(server)) ctx.getSource().sendSuccess(() -> line, false);
                            return (int) auto;
                        })
                        .then(Commands.literal("annuler").executes(ctx -> {
                            int n = fr.crusadecraft.core.build.SiteService.undoAuto(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.translatable("crusadecraft.civ.undone", n), true);
                            return n;
                        })))
                .then(Commands.literal("monde").requires(src -> src.hasPermission(2))
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

    private static int say(CommandContext<CommandSourceStack> ctx, fr.crusadecraft.core.council.CouncilActions.Outcome out) {
        if (out.success()) ctx.getSource().sendSuccess(out::message, false);
        else ctx.getSource().sendFailure(out.message());
        return out.success() ? 1 : 0;
    }

    private static int lines(CommandContext<CommandSourceStack> ctx, java.util.List<Component> lines) {
        for (Component line : lines) ctx.getSource().sendSuccess(() -> line, false);
        return lines.size();
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
