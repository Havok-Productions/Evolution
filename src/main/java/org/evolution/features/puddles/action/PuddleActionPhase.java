package org.evolution.features.puddles.action;

import org.evolution.coreparts.hierarchy.FeatureActionMode;
import org.evolution.coreparts.hierarchy.FeatureActionPhase;

public enum PuddleActionPhase implements FeatureActionPhase {
    WEATHER_GATE("PuddleWeatherGate", FeatureActionMode.GATE),
    GROW("PuddleGrowthExecutor", FeatureActionMode.EXCLUSIVE),
    DRY("PuddleDryingExecutor", FeatureActionMode.EXCLUSIVE),
    RETIRE_INVALID("PuddleRetirementExecutor", FeatureActionMode.PIPELINE),
    RENDER("PuddleRenderExecutor", FeatureActionMode.PIPELINE);

    private final String owner;
    private final FeatureActionMode mode;

    PuddleActionPhase(String owner, FeatureActionMode mode) {
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
