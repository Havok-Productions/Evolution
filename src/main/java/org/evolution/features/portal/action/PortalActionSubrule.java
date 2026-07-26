package org.evolution.features.portal.action;

import org.evolution.coreparts.hierarchy.FeatureActionSubrule;

/** ## Nested contracts for custom portal creation and teardown. */
public enum PortalActionSubrule
        implements FeatureActionSubrule<PortalActionPhase> {
    ENCLOSED_FRAME_FOUND(PortalActionPhase.DETECT_FRAME,
            "EnclosedFrameContract"),
    PROTECTION_ALLOWED(PortalActionPhase.DETECT_FRAME,
            "PortalProtectionGate"),
    INTERIOR_COMMITTED(PortalActionPhase.CREATE_INTERIOR,
            "PortalInteriorCommit"),
    EXACT_FOOTPRINT_INDEXED(PortalActionPhase.TRACK_PORTAL,
            "PortalFootprintIndex"),
    NETHER_SOURCE_RETIRED(PortalActionPhase.REMOVE_PORTAL,
            "NetherSourceRetirement"),
    FOOTPRINT_UNINDEXED(PortalActionPhase.REMOVE_PORTAL,
            "PortalFootprintRemoval"),
    INTERIOR_CLEARED(PortalActionPhase.REMOVE_PORTAL,
            "PortalInteriorRemoval"),
    PORTAL_STATE_SAVED(PortalActionPhase.PERSIST_PORTALS,
            "PortalStatePersistence");

    private final PortalActionPhase phase;
    private final String owner;

    PortalActionSubrule(PortalActionPhase phase, String owner) {
        this.phase = phase;
        this.owner = owner;
    }

    @Override
    public PortalActionPhase phase() {
        return phase;
    }

    @Override
    public String owner() {
        return owner;
    }
}