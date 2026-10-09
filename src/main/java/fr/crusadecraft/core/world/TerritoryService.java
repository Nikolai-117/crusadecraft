package fr.crusadecraft.core.world;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.item.BornageStaffItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le bornage au bâton (P14) : poser des coins, valider un tracé, peindre en marchant, annuler. Les frontières sont
 * celles de l'éditeur de la carte ({@link TerritoryMap}) ; l'éditeur peut tout, un joueur en survie étend son propre
 * royaume selon {@link TerritoryRules}, contre de l'or.
 *
 * <p>Les annulations sont gardées le temps de la session (les dix derniers tracés de chaque joueur) ; l'or payé est
 * rendu à l'annulation.
 */
public final class TerritoryService {
    private static final int UNDO_KEPT = 10;
    /** Un coin cliqué à moins de trois blocs du premier ferme le polygone. */
    private static final double CLOSE_DISTANCE = 3.0;
    /** Les cases peintes en marchant forment un seul tracé tant qu'on ne s'arrête pas plus de cinq secondes. */
    private static final long WALK_GROUP_MS = 5000;

    /** Un tracé appliqué : ce qu'il y avait avant, pour l'annuler, et l'or payé, pour le rendre. */
    private static final class Stroke {
        final ResourceLocation dim;
        final List<Long> keys = new ArrayList<>();
        final List<UUID> before = new ArrayList<>();
        final UUID payer;
        long paid;
        long lastTouch;
        final boolean walk;

        Stroke(ResourceLocation dim, UUID payer, boolean walk) {
            this.dim = dim;
            this.payer = payer;
            this.walk = walk;
        }
    }

    private static final Map<UUID, Deque<Stroke>> UNDO = new HashMap<>();
    private static final Map<UUID, Long> LAST_CELL = new HashMap<>();

    private TerritoryService() {}

    public static void onServerStopped() {
        UNDO.clear();
        LAST_CELL.clear();
    }

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    // ------------------------------------------------------------------ le bâton

    /** Un clic droit du bâton sur un bloc. */
    public static void onStaffUse(ServerPlayer player, ItemStack stack, BlockPos pos, Direction face, boolean sneaking) {
        BornageStaffItem.Mode mode = BornageStaffItem.mode(stack);
        long cell = TerritoryMap.key(TerritoryMap.cellOf(pos.getX()), TerritoryMap.cellOf(pos.getZ()));
        if (sneaking && mode != BornageStaffItem.Mode.CONSTRUIRE && mode != BornageStaffItem.Mode.LIEU) {
            // Accroupi : cette case redevient terre libre.
            say(player, apply(player, null, BornageStaffItem.NEUTRAL, new long[]{cell}, false));
            return;
        }
        switch (mode) {
            case POLYGONE, RECTANGLE -> {
                List<BlockPos> points = new ArrayList<>(BornageStaffItem.points(stack));
                boolean closes = mode == BornageStaffItem.Mode.POLYGONE && points.size() >= 3
                        && Math.hypot(points.get(0).getX() - pos.getX(), points.get(0).getZ() - pos.getZ()) <= CLOSE_DISTANCE;
                if (!closes) points.add(pos.immutable());
                BornageStaffItem.setPoints(stack, points);
                if (closes || (mode == BornageStaffItem.Mode.RECTANGLE && points.size() >= 2)) {
                    validate(player, stack);
                } else {
                    player.displayClientMessage(preview(player, stack), true);
                }
            }
            case MARCHE -> say(player, paintTarget(player, stack, new long[]{cell}, true));
            case CONSTRUIRE -> say(player, SiteService.placeFromStaff(player, stack, pos, player.getDirection()));
            // Un lieu saint, une ruine, une région : on le nomme dans un formulaire, puis le serveur le pose ici.
            case LIEU -> fr.crusadecraft.network.CrusadeNetwork.toPlayer(player,
                    new fr.crusadecraft.network.packet.LieuPackets.OpenForm(BornageStaffItem.lieu(stack).ordinal(), pos.above()));
        }
    }

    private static void say(ServerPlayer player, Outcome out) {
        player.displayClientMessage(out.message(), true);
    }

    /** Les cases du tracé en cours (coins posés) ; vide s'il est trop grand ou incomplet. */
    static long[] strokeCells(ItemStack stack) {
        List<BlockPos> pts = BornageStaffItem.points(stack);
        List<int[]> xz = new ArrayList<>();
        for (BlockPos p : pts) xz.add(new int[]{p.getX(), p.getZ()});
        if (BornageStaffItem.mode(stack) == BornageStaffItem.Mode.RECTANGLE) {
            return xz.size() >= 2 ? TerritoryRules.rectangle(xz.get(0)[0], xz.get(0)[1], xz.get(1)[0], xz.get(1)[1]) : null;
        }
        return xz.size() >= 3 ? TerritoryRules.polygon(xz) : null;
    }

    /** Valide le tracé en cours : ses cases reviennent à qui le bâton désigne. */
    public static Outcome validate(ServerPlayer player, ItemStack stack) {
        long[] cells = strokeCells(stack);
        if (cells == null) {
            Outcome out = Outcome.no(BornageStaffItem.points(stack).size() < 2 ? "crusadecraft.bornage.need_points" : "crusadecraft.bornage.too_big");
            say(player, out);
            return out;
        }
        Outcome out = paintTarget(player, stack, cells, false);
        if (out.success()) BornageStaffItem.setPoints(stack, List.of());
        say(player, out);
        return out;
    }

    private static Outcome paintTarget(ServerPlayer player, ItemStack stack, long[] cells, boolean walk) {
        return apply(player, BornageStaffItem.owner(stack).orElse(null), BornageStaffItem.special(stack), cells, walk);
    }

    /** Ce que donnerait le tracé en cours, pour la barre d'action. */
    public static Component preview(ServerPlayer player, ItemStack stack) {
        List<BlockPos> pts = BornageStaffItem.points(stack);
        long[] cells = strokeCells(stack);
        if (cells == null) return Component.translatable("crusadecraft.bornage.points", pts.size());
        if (WorldEditService.canEdit(player) || !BornageStaffItem.special(stack).isEmpty()) {
            return Component.translatable("crusadecraft.bornage.preview", pts.size(), cells.length);
        }
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) return Component.translatable("crusadecraft.bornage.preview", pts.size(), cells.length);
        TerritoryRules.Verdict v = verdict(player, realm.get(), cells);
        return Component.translatable("crusadecraft.bornage.preview.survival", pts.size(), v.cells().length, v.cost(), v.taken() + v.far());
    }

    private static TerritoryRules.Verdict verdict(ServerPlayer player, Realm realm, long[] cells) {
        ResourceLocation dim = player.level().dimension().location();
        Territory.Snapshot snap = Territory.snapshot(player.server, dim);
        return TerritoryRules.survival(realm.id(), cells, snap::ownerOfCell, anchors(player.server, realm, dim),
                CrusadeConfig.SERVER.claimMaxDistance.get(), CrusadeConfig.SERVER.claimCellCost.get(), CrusadeConfig.SERVER.claimMaxCells.get());
    }

    /** Les centres d'où un royaume peut étendre ses terres : ses colonies, ses postes de garde, ses constructions bâties. */
    public static List<double[]> anchors(MinecraftServer server, Realm realm, ResourceLocation dim) {
        List<double[]> out = new ArrayList<>();
        for (ColonyRef c : realm.allColonies()) {
            if (!c.dimension().equals(dim)) continue;
            if (MineColoniesBridge.isReady()) MineColoniesBridge.center(c).ifPresent(b -> out.add(new double[]{b.getX() + 0.5, b.getZ() + 0.5}));
            for (MineColoniesBridge.GuardPost g : Territory.guardPosts(c)) out.add(new double[]{g.pos().getX() + 0.5, g.pos().getZ() + 0.5});
        }
        for (Site s : data(server).world().sites()) {
            if (s.built() && realm.id().equals(s.owner()) && s.dimension().equals(dim)) out.add(new double[]{s.anchor().getX() + 0.5, s.anchor().getZ() + 0.5});
        }
        return out;
    }

    // ------------------------------------------------------------------ appliquer

    /**
     * Peint ces cases. {@code owner} : le royaume ; {@code special} : {@link BornageStaffItem#NEUTRAL} (terre libre) ou
     * {@link BornageStaffItem#ERASE} (retour aux cercles). L'éditeur peut tout ; en survie, on n'étend que son royaume.
     */
    public static Outcome apply(ServerPlayer player, UUID owner, String special, long[] cells, boolean walk) {
        MinecraftServer server = player.server;
        CrusadeSavedData data = data(server);
        ResourceLocation dim = player.level().dimension().location();
        TerritoryMap borders = data.world().borders();
        boolean editor = WorldEditService.canEdit(player);
        boolean neutral = BornageStaffItem.NEUTRAL.equals(special), erase = BornageStaffItem.ERASE.equals(special);
        if (!neutral && !erase && owner == null) return Outcome.no("crusadecraft.bornage.no_owner");
        if (cells.length > TerritoryRules.MAX_CELLS) return Outcome.no("crusadecraft.bornage.too_big");
        long[] chosen = cells;
        long cost = 0;
        Realm payer = null;
        if (!editor) {
            if (!CrusadeConfig.SERVER.survivalClaims.get()) return Outcome.no("crusadecraft.bornage.survival_off");
            Optional<Realm> realm = RealmService.realmOf(server, player.getUUID());
            if (realm.isEmpty()) return Outcome.no("crusadecraft.realm.none");
            Territory.Snapshot snap = Territory.snapshot(server, dim);
            if (neutral || erase) {
                // Rendre ses propres terres : seulement celles qui sont à vous (et, pour la gomme, peintes à votre nom).
                List<Long> mine = new ArrayList<>();
                for (long k : cells) {
                    boolean own = erase ? realm.get().id().equals(borders.cells(dim).get(k)) : snap.ownerOfCell(k).map(realm.get().id()::equals).orElse(false);
                    if (own) mine.add(k);
                }
                if (mine.isEmpty()) return Outcome.no("crusadecraft.bornage.not_yours");
                chosen = mine.stream().mapToLong(Long::longValue).toArray();
            } else {
                if (!owner.equals(realm.get().id())) return Outcome.no("crusadecraft.bornage.only_yours");
                TerritoryRules.Verdict v = TerritoryRules.survival(realm.get().id(), cells, snap::ownerOfCell, anchors(server, realm.get(), dim),
                        CrusadeConfig.SERVER.claimMaxDistance.get(), CrusadeConfig.SERVER.claimCellCost.get(), CrusadeConfig.SERVER.claimMaxCells.get());
                if (!v.ok()) return Outcome.no(v.refusal(), CrusadeConfig.SERVER.claimMaxCells.get());
                // Le plafond de chunks revendiqués, selon le rang (les terres autour des villes ne comptent pas).
                int cap = claimCap(realm.get());
                int already = borders.countOf(realm.get().id());
                int fresh = 0;
                for (long k : v.cells()) if (!realm.get().id().equals(borders.cells(dim).get(k))) fresh++;
                if (already + fresh > cap) return Outcome.no("crusadecraft.claim.cap", already, cap);
                if (!TreasuryService.spend(server, realm.get(), LedgerCategory.TERRITOIRE, v.cost())) {
                    return Outcome.no("crusadecraft.bornage.no_gold", v.cost());
                }
                chosen = v.cells();
                cost = v.cost();
                payer = realm.get();
            }
        } else if (!neutral && !erase && data.world().realm(owner).isEmpty() && data.realm(owner).isEmpty()) {
            return Outcome.no("crusadecraft.mapedit.realm.unknown");
        }
        UUID value = neutral ? TerritoryMap.NEUTRAL : erase ? null : owner;
        Stroke stroke = stroke(player, dim, payer == null ? null : payer.id(), walk);
        var current = borders.cells(dim);
        int changed = 0;
        for (long k : chosen) {
            UUID before = current.get(k);
            if (value == null ? before == null : value.equals(before)) continue;
            stroke.keys.add(k);
            stroke.before.add(before);
            changed++;
        }
        borders.paint(dim, chosen, value);
        stroke.paid += cost;
        stroke.lastTouch = System.currentTimeMillis();
        if (changed > 0 || cost > 0) data.setDirty();
        if (changed == 0) return Outcome.no("crusadecraft.bornage.nothing");
        Component who = neutral ? Component.translatable("crusadecraft.mapedit.brush.neutral") : erase
                ? Component.translatable("crusadecraft.mapedit.brush.erase") : DiplomacyService.name(server, owner);
        return cost > 0 ? Outcome.ok(Component.translatable("crusadecraft.bornage.done.paid", changed, who, cost))
                : Outcome.ok(Component.translatable("crusadecraft.bornage.done", changed, who));
    }

    /** Les chunks qu'un royaume peut revendiquer à la main, selon son rang (réglage {@code plafondChunks}). */
    public static int claimCap(Realm realm) {
        java.util.List<? extends Integer> caps = CrusadeConfig.SERVER.claimCaps.get();
        if (caps.isEmpty()) return Integer.MAX_VALUE;
        return caps.get(Math.min(caps.size() - 1, realm.rank().ordinal()));
    }

    /** Le tracé où inscrire ces cases : un nouveau, ou celui de la marche en cours. */
    private static Stroke stroke(ServerPlayer player, ResourceLocation dim, UUID payer, boolean walk) {
        Deque<Stroke> stack = UNDO.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        Stroke last = stack.peekFirst();
        if (walk && last != null && last.walk && last.dim.equals(dim) && System.currentTimeMillis() - last.lastTouch < WALK_GROUP_MS) return last;
        Stroke s = new Stroke(dim, payer, walk);
        stack.addFirst(s);
        while (stack.size() > UNDO_KEPT) stack.removeLast();
        return s;
    }

    /** Annule le dernier tracé de ce joueur (et rend l'or payé). */
    public static Outcome undo(ServerPlayer player) {
        Deque<Stroke> stack = UNDO.get(player.getUUID());
        Stroke s = stack == null ? null : stack.pollFirst();
        while (s != null && s.keys.isEmpty() && s.paid == 0) s = stack.pollFirst();
        if (s == null) return Outcome.no("crusadecraft.bornage.undo.none");
        TerritoryMap borders = data(player.server).world().borders();
        for (int i = s.keys.size() - 1; i >= 0; i--) borders.paint(s.dim, new long[]{s.keys.get(i)}, s.before.get(i));
        Stroke undone = s;
        if (undone.paid > 0 && undone.payer != null) {
            data(player.server).realm(undone.payer).ifPresent(r -> TreasuryService.credit(player.server, r, LedgerCategory.TERRITOIRE, undone.paid));
        }
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.bornage.undo.done", undone.keys.size()));
    }

    // ------------------------------------------------------------------ en marchant

    /** Deux fois par seconde : le bâton en mode « en marchant » peint la case où l'on entre (accroupi : la rend libre). */
    public static void walkTick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<ItemStack> staff = BornageStaffItem.held(player);
            if (staff.isEmpty() || BornageStaffItem.mode(staff.get()) != BornageStaffItem.Mode.MARCHE || player.isSpectator()) {
                LAST_CELL.remove(player.getUUID());
                continue;
            }
            long cell = TerritoryMap.key(TerritoryMap.cellOf(player.getX()), TerritoryMap.cellOf(player.getZ()));
            Long last = LAST_CELL.put(player.getUUID(), cell);
            if (last != null && last == cell) continue;
            Outcome out = player.isShiftKeyDown() ? apply(player, null, BornageStaffItem.NEUTRAL, new long[]{cell}, true)
                    : paintTarget(player, staff.get(), new long[]{cell}, true);
            player.displayClientMessage(out.message(), true);
        }
    }
}
