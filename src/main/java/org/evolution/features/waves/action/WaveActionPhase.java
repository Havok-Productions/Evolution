package org.evolution.features.waves.action;

import org.evolution.coreparts.hierarchy.FeatureActionMode;
import org.evolution.coreparts.hierarchy.FeatureActionPhase;

public enum WaveActionPhase implements FeatureActionPhase {
    SAFETY_GATE("WaveSafetyGate", FeatureActionMode.GATE),
    UPDATE_ENVIRONMENT("WaveEnvironmentExecutor", FeatureActionMode.PIPELINE),
    ADVANCE_FRONTS("TravelingFrontExecutor", FeatureActionMode.PIPELINE),
    COLLECT_VISUALS("WaveVisualCollectionExecutor", FeatureActionMode.PIPELINE),
    MERGE_FRONTS("WaveMergeExecutor", FeatureActionMode.PIPELINE),
    SHORE_RUNUP("ShoreRunupExecutor", FeatureActionMode.PIPELINE),
    RENDER("WaveRenderExecutor", FeatureActionMode.PIPELINE),
    BOAT_RESPONSE("BoatResponseExecutor", FeatureActionMode.PIPELINE);

    private final String owner;
    private final FeatureActionMode mode;

    WaveActionPhase(String owner, FeatureActionMode mode) {
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
