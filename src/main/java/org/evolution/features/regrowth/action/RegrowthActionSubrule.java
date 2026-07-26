package org.evolution.features.regrowth.action;

import org.evolution.coreparts.hierarchy.FeatureActionSubrule;

/** ## Nested ownership and retry contracts for gradual plant regrowth. */
public enum RegrowthActionSubrule
        implements FeatureActionSubrule<RegrowthActionPhase> {
    BREAK_SNAPSHOT_QUEUED(RegrowthActionPhase.QUEUE,
            "BreakSnapshotQueue"),
    FEATURE_AND_WORLD_ALLOWED(RegrowthActionPhase.VALIDATE,
            "RegrowthFeatureWorldGate"),
    SINGLE_ACTIVE_OWNER(RegrowthActionPhase.VALIDATE,
            "ActiveRegrowthOwnership"),
    REGION_WORK_ALLOWED(RegrowthActionPhase.VALIDATE,
            "RegrowthRegionGate"),
    ANCHOR_INTACT(RegrowthActionPhase.VALIDATE,
            "RegrowthAnchorGate"),
    VANILLA_STRUCTURE_CAPTURED(RegrowthActionPhase.PLAN_STRUCTURE,
            "VanillaStructureCapture"),
    SAFE_BLOCKS_FILTERED(RegrowthActionPhase.PLAN_STRUCTURE,
            "RegrowthPlacementFilter"),
    CURRENT_ACTIVE_OWNER(RegrowthActionPhase.PLACE_BATCH,
            "CurrentActiveRegrowthGate"),
    COOLDOWN_EXPIRED(RegrowthActionPhase.PLACE_BATCH,
            "RegrowthCooldownGate"),
    SAFE_BATCH_COMMITTED(RegrowthActionPhase.PLACE_BATCH,
            "RegrowthBatchCommit"),
    BOUNDED_RETRY(RegrowthActionPhase.RETRY,
            "RegrowthRetryPolicy"),
    ACTIVE_OWNER_RELEASED(RegrowthActionPhase.COMPLETE,
            "RegrowthCompletionRelease"),
    INTERRUPTED_TREE_DECAY(RegrowthActionPhase.DECAY,
            "InterruptedTreeDecay");

    private final RegrowthActionPhase phase;
    private final String owner;

    RegrowthActionSubrule(RegrowthActionPhase phase, String owner) {
        this.phase = phase;
        this.owner = owner;
    }

    @Override
    public RegrowthActionPhase phase() {
        return phase;
    }

    @Override
    public String owner() {
        return owner;
    }
}