package fr.crusadecraft.core.build.plan;

import fr.crusadecraft.core.build.Blueprints;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Pose les blocs d'un plan (P34), par lots, en notant chaque bloc remplacé dans son journal. Les blocs d'un plan
 * sont posés tels qu'ils ont été relevés, sans que leurs voisins se recalculent : une torche posée avant son mur
 * ne tombe pas, une clôture garde ses raccords.
 */
public final class PlanPlacer {
    /** Fondations : on comble jusqu'à huit blocs sous le plan. */
    public static final int FOUNDATION_DEPTH = 8;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Cases parcourues au plus par bloc changé, dans un tick. */
    static final int STEPS_PER_CHANGE = 40;

    private PlanPlacer() {}

    /**
     * Pose depuis l'étape {@code from} jusqu'à {@code allowed} blocs changés (un bloc déjà en place ne compte pas).
     *
     * @return {@code {étape atteinte, blocs changés}}
     */
    public static int[] advance(ServerLevel level, BlockPos anchor, Rotation rotation, List<Blueprints.Placement> plan, int from,
                                int allowed, BuildJournal journal) {
        int changed = 0, i = from, steps = 0;
        // P38 : une ville apporte des millions de cases d'air déjà vides ; on n'en parcourt qu'un nombre borné par tick.
        int maxSteps = Math.max(allowed, allowed * STEPS_PER_CHANGE);
        while (i < plan.size() && changed < allowed && steps++ < maxSteps) {
            Blueprints.Placement p = plan.get(i);
            BlockPos pos = Blueprints.place(anchor, p, rotation);
            if (level.isOutsideBuildHeight(pos)) {
                i++;
                continue;
            }
            if (p.foundation()) {
                for (int d = 0; d < FOUNDATION_DEPTH; d++) {
                    BlockPos below = pos.below(d);
                    BlockState here = level.getBlockState(below);
                    if (!here.canBeReplaced() && here.getFluidState().isEmpty()) break;
                    if (journal != null) journal.record(level, below, here);
                    level.setBlock(below, p.state(), FLAGS);
                    changed++;
                }
            } else {
                BlockState want = p.state().rotate(rotation);
                BlockState here = level.getBlockState(pos);
                if (!here.equals(want) || p.tag() != null) {
                    if (!(want.isAir() && here.isAir())) {
                        if (journal != null) journal.record(level, pos, here);
                        if (here.hasBlockEntity()) level.removeBlockEntity(pos);
                        level.setBlock(pos, want, FLAGS);
                        if (p.tag() != null) {
                            BlockEntity be = level.getBlockEntity(pos);
                            if (be != null) {
                                var tag = p.tag().copy();
                                tag.putInt("x", pos.getX());
                                tag.putInt("y", pos.getY());
                                tag.putInt("z", pos.getZ());
                                try {
                                    be.load(tag);
                                    be.setChanged();
                                    // Les données de l'entité partent aux joueurs (le bois et le torchis d'un colombage).
                                    level.sendBlockUpdated(pos, want, want, Block.UPDATE_CLIENTS);
                                } catch (RuntimeException ignored) {
                                    // des données d'un autre temps : le bloc reste, nu
                                }
                            }
                        }
                        changed++;
                    }
                }
            }
            i++;
        }
        return new int[]{i, changed};
    }
}
