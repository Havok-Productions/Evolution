package org.evolution.features.ecology.action;

import org.evolution.coreparts.hierarchy.FeatureActionMode;
import org.evolution.coreparts.hierarchy.FeatureActionPhase;

public enum EcologyActionPhase implements FeatureActionPhase {
    SAFETY_GATE("EcologySafetyGate", FeatureActionMode.GATE),
    FIND_SURFACE("SurfaceTargetFinder", FeatureActionMode.EXCLUSIVE),
    FIND_TREE("EcologyTreeFinder", FeatureActionMode.EXCLUSIVE),
    RARE_DETAIL("RareDetailExecutor", FeatureActionMode.EXCLUSIVE),
    MICROHABITAT("MicrohabitatExecutor", FeatureActionMode.EXCLUSIVE),
    GROUND_MUTATION("GroundMutationExecutor", FeatureActionMode.EXCLUSIVE),
    PLANT_DETAIL("PlantDetailExecutor", FeatureActionMode.EXCLUSIVE),
    FOREST_FLOOR("ForestFloorExecutor", FeatureActionMode.EXCLUSIVE),
    LEGACY_TREE_SHAPE("LegacyTreeShapeExecutor", FeatureActionMode.EXCLUSIVE);

    private final String owner;
    private final FeatureActionMode mode;

    EcologyActionPhase(String owner, FeatureActionMode mode) {
        this.owner = owner;
        this.mode = mode;
    }

    @Override
    public String owner() {
        return owner;
    }

    @Override
    public FeatureActionMode mode() {
        return mode;
    }
}
