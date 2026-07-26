package org.evolution.features.nether.action;

import org.evolution.coreparts.hierarchy.FeatureActionMode;
import org.evolution.coreparts.hierarchy.FeatureActionPhase;

public enum NetherActionPhase implements FeatureActionPhase {
    SOURCE_GATE("NetherSourceGate", FeatureActionMode.GATE),
    SELECT_FRONTIER("FrontierTargetExecutor", FeatureActionMode.PIPELINE),
    SELECT_NEAR_PORTAL("PortalOriginTargetExecutor", FeatureActionMode.PIPELINE),
    SELECT_FALLBACK("FallbackTargetExecutor", FeatureActionMode.PIPELINE),
    MIMIC_TERRAIN("NetherTerrainExecutor", FeatureActionMode.EXCLUSIVE),
    COMMIT_FRONTIER("FrontierCommitExecutor", FeatureActionMode.PIPELINE),
    RETIRE_SOURCE("BrokenSourceRetirement", FeatureActionMode.TERMINAL),
    PERSIST_SOURCE("NetherSourcePersistence", FeatureActionMode.TERMINAL);

    private final String owner;
    private final FeatureActionMode mode;

    NetherActionPhase(String owner, FeatureActionMode mode) {
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
