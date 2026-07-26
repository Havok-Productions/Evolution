package org.evolution.features.meadow.action;

import org.evolution.coreparts.hierarchy.FeatureActionMode;
import org.evolution.coreparts.hierarchy.FeatureActionPhase;

public enum MeadowActionPhase implements FeatureActionPhase {
    SAFETY_GATE("MeadowSafetyGate", FeatureActionMode.GATE),
    FIND_FRONTIER("GrassFrontierFinder", FeatureActionMode.EXCLUSIVE),
    FIND_RANDOM_SURFACE("MeadowSurfaceFinder", FeatureActionMode.EXCLUSIVE),
    SPREAD_GRASS("GrassSpreadExecutor", FeatureActionMode.EXCLUSIVE),
    GROW_TALL_PLANT("TallPlantExecutor", FeatureActionMode.EXCLUSIVE),
    GROW_SURFACE_PLANT("SurfacePlantExecutor", FeatureActionMode.EXCLUSIVE);

    private final String owner;
    private final FeatureActionMode mode;

    MeadowActionPhase(String owner, FeatureActionMode mode) {
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
