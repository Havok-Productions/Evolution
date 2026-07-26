package org.evolution.features.nether.action;

import org.evolution.coreparts.hierarchy.FeatureActionSubrule;

/**
 * ## NETHER ACTION SUBRULES
 *
 * <p>Source validity owns the right to spread. Target selection and terrain
 * translation cannot run until every source gate has passed.</p>
 */
public enum NetherActionSubrule
        implements FeatureActionSubrule<NetherActionPhase> {
    SOURCE_IS_TRACKED(NetherActionPhase.SOURCE_GATE,
            "TrackedSourceOwnership"),
    SOURCE_WORLD_AVAILABLE(NetherActionPhase.SOURCE_GATE,
            "SourceWorldGate"),
    SOURCE_BOUNDS_LOADED(NetherActionPhase.SOURCE_GATE,
            "LoadedSourceGate"),
    SOURCE_REGION_OWNED(NetherActionPhase.SOURCE_GATE,
            "FoliaSourceOwnershipGate"),
    PORTAL_FINGERPRINT_INTACT(NetherActionPhase.SOURCE_GATE,
            "PortalFingerprintGate"),
    SOURCE_PROTECTION_ALLOWED(NetherActionPhase.SOURCE_GATE,
            "WorldGuardSourceGate"),
    NEARBY_PLAYER_ACTIVE(NetherActionPhase.SOURCE_GATE,
            "PlayerDistanceGate"),
    RETIRE_BROKEN_SOURCE(NetherActionPhase.RETIRE_SOURCE,
            "BrokenSourceRetirement"),
    CONNECTED_FRONTIER_TARGET(NetherActionPhase.SELECT_FRONTIER,
            "ConnectedFrontierSelector"),
    PORTAL_ORIGIN_TARGET(NetherActionPhase.SELECT_NEAR_PORTAL,
            "PortalOriginSelector"),
    BOUNDED_FALLBACK_RADIUS(NetherActionPhase.SELECT_FALLBACK,
            "FallbackRadiusPolicy"),
    DIRECT_MATERIAL_TRANSLATION(NetherActionPhase.MIMIC_TERRAIN,
            "DirectTranslationPolicy"),
    NEIGHBOR_STYLE_TRANSLATION(NetherActionPhase.MIMIC_TERRAIN,
            "NeighborStylePolicy"),
    TARGET_PROTECTION_ALLOWED(NetherActionPhase.MIMIC_TERRAIN,
            "WorldGuardTargetGate"),
    COMMIT_WORLD_CHANGE(NetherActionPhase.COMMIT_FRONTIER,
            "NetherWorldChangeCommit"),
    EXTEND_CONNECTED_FRONTIER(NetherActionPhase.COMMIT_FRONTIER,
            "ConnectedFrontierCommit"),
    SAVE_EXACT_SOURCE_SNAPSHOT(NetherActionPhase.PERSIST_SOURCE,
            "ExactSourcePersistence");

    private final NetherActionPhase phase;
    private final String owner;

    NetherActionSubrule(NetherActionPhase phase, String owner) {
        this.phase = phase;
        this.owner = owner;
    }

    @Override
    public NetherActionPhase phase() {
        return phase;
    }

    @Override
    public String owner() {
        return owner;
    }
}