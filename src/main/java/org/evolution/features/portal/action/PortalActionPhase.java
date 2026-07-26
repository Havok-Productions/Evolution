package org.evolution.features.portal.action;

import org.evolution.coreparts.hierarchy.FeatureActionMode;
import org.evolution.coreparts.hierarchy.FeatureActionPhase;

public enum PortalActionPhase implements FeatureActionPhase {
    DETECT_FRAME("PortalFrameDetector", FeatureActionMode.GATE),
    CREATE_INTERIOR("PortalCreationExecutor", FeatureActionMode.EXCLUSIVE),
    TRACK_PORTAL("PortalTrackingExecutor", FeatureActionMode.PIPELINE),
    REMOVE_PORTAL("PortalRemovalExecutor", FeatureActionMode.EXCLUSIVE),
    PERSIST_PORTALS("PortalPersistenceExecutor", FeatureActionMode.TERMINAL);

    private final String owner;
    private final FeatureActionMode mode;

    PortalActionPhase(String owner, FeatureActionMode mode) {
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
