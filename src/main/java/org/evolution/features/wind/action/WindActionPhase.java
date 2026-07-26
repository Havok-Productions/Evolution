package org.evolution.features.wind.action;

import org.evolution.coreparts.hierarchy.FeatureActionMode;
import org.evolution.coreparts.hierarchy.FeatureActionPhase;

public enum WindActionPhase implements FeatureActionPhase {
    SAFETY_GATE("WindSafetyGate", FeatureActionMode.GATE),
    UPDATE_PATTERN("WindPatternExecutor", FeatureActionMode.PIPELINE),
    FIND_CANOPY("CanopySearchExecutor", FeatureActionMode.PIPELINE),
    SPAWN_PARTICLES("LeafParticleExecutor", FeatureActionMode.PIPELINE),
    SETTLE_LITTER("LeafLitterExecutor", FeatureActionMode.EXCLUSIVE),
    STACK_LITTER("LeafLitterStackExecutor", FeatureActionMode.EXCLUSIVE);

    private final String owner;
    private final FeatureActionMode mode;

    WindActionPhase(String owner, FeatureActionMode mode) {
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
