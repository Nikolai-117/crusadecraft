package fr.crusadecraft.core.nation;

import fr.crusadecraft.core.build.plan.PlanTool;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.network.packet.NationPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** {@code /crusade nation} : voir les nations, prendre ou rendre une couronne, relire les fichiers (P41). */
public final class NationCommands {
    private NationCommands() {}

    /** Ouvre l'écran : la nation dont on est le souverain, ou celle demandée. */
    public static Outcome open(ServerPlayer player, String key) {
        Optional<AiRealm> realm = key == null ? NationService.ruledBy(player.server, player.getUUID()) : NationService.realm(player.server, key);
        if (realm.isEmpty()) return Outcome.no(key == null ? "crusadecraft.nation.none" : "crusadecraft.nation.unknown", key == null ? "" : key);
        NationPackets.send(player, realm.get());
        return Outcome.ok(NationService.title(realm.get()));
    }

    public static List<Component> list(MinecraftServer server) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("crusadecraft.nation.list.title", NationBook.all().size()).withStyle(ChatFormatting.GOLD));
        for (NationDef def : NationBook.all()) {
            Optional<AiRealm> r = NationService.realm(server, def.key());
            Component state = r.isEmpty() ? Component.translatable("crusadecraft.nation.list.gone")
                    : r.get().sovereign() != null ? Component.translatable("crusadecraft.nation.list.ruled", r.get().sovereignName())
                    : Component.translatable("crusadecraft.nation.list.ai", r.get().ruler().name());
            int cities = r.map(x -> x.ownProvinces().size()).orElse(0);
            out.add(Component.translatable("crusadecraft.nation.list.row", def.key(), def.name(), state, cities));
        }
        if (NationBook.all().isEmpty()) out.add(Component.translatable("crusadecraft.nation.list.empty", NationBook.folder().toString()).withStyle(ChatFormatting.GRAY));
        return out;
    }

    public static Outcome crown(ServerPlayer player, String key) {
        if (!PlanTool.allowed(player)) return Outcome.no("crusadecraft.city.denied");
        Optional<AiRealm> realm = NationService.realm(player.server, key);
        if (realm.isEmpty()) return Outcome.no("crusadecraft.nation.unknown", key);
        return NationService.crown(player, realm.get());
    }

    /** Relit le dossier : une correction du fichier se voit tout de suite, une nation nouvelle naît. */
    public static Outcome reload(MinecraftServer server) {
        List<String> errors = NationBook.reload();
        NationService.ensure(server);
        if (!errors.isEmpty()) return Outcome.no("crusadecraft.nation.reload.errors", String.join(" ; ", errors));
        return Outcome.ok(Component.translatable("crusadecraft.nation.reload.done", NationBook.all().size()));
    }

    /** Remet les fichiers du jar (les anciens restent à côté, en .bak). */
    public static Outcome restore(MinecraftServer server) {
        int n = NationBook.restore();
        Outcome out = reload(server);
        return out.success() ? Outcome.ok(Component.translatable("crusadecraft.nation.restore.done", n)) : out;
    }
}
