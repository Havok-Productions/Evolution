package org.evolution.features.regrowth.action;

import org.evolution.coreparts.hierarchy.FeatureActionMode;
import org.evolution.coreparts.hierarchy.FeatureActionPhase;

public enum RegrowthActionPhase implements FeatureActionPhase {
    VALIDATE("RegrowthValidationGate", FeatureActionMode.GATE),
    QUEUE("RegrowthQueueExecutor", FeatureActionMode.PIPELINE),
    PLAN_STRUCTURE("StructurePlanExecutor", FeatureActionMode.EXCLUSIVE),
    PLACE_BATCH("RegrowthPlacementExecutor", FeatureActionMode.EXCLUSIVE),
    RETRY("RegrowthRetryExecutor", FeatureActionMode.TERMINAL),
    COMPLETE("RegrowthCompletionExecutor", FeatureActionMode.TERMINAL),
    DECAY("PlantDecayExecutor", FeatureActionMode.EXCLUSIVE);

    private final String owner;
    private final FeatureActionMode mode;

    RegrowthActionPhase(String owner, FeatureActionMode mode) {
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
