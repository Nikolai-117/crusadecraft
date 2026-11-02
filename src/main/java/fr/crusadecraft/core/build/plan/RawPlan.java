package fr.crusadecraft.core.build.plan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Un plan tel qu'il est lu (P34), avant toute traduction : les blocs de la boîte, avec l'identifiant de chacun (pour
 * les règles de {@link PlanRules}) et les données de son entité. Les cases absentes ne sont pas posées.
 *
 * @param groundY  la couche qui se pose au niveau du sol (celle de l'herbe), comptée depuis le bas du plan
 * @param replaced P38 : sortes de blocs qu'on n'a pas, remplacées par leur équivalent ({@link BlockEquivalents})
 * @param unknown  P38 : sortes de blocs sans équivalent, laissées de côté
 */
public record RawPlan(String name, int sizeX, int sizeY, int sizeZ, int groundY, List<Cell> cells, int replaced, List<String> unknown) {

    public RawPlan(String name, int sizeX, int sizeY, int sizeZ, int groundY, List<Cell> cells) {
        this(name, sizeX, sizeY, sizeZ, groundY, cells, 0, List.of());
    }

    /** Un bloc du plan, en coordonnées du plan (0 à taille − 1). */
    public record Cell(int x, int y, int z, BlockState state, String id, CompoundTag tag) {}
}
