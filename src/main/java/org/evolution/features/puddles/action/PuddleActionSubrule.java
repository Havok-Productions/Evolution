package org.evolution.features.puddles.action;

import org.evolution.coreparts.hierarchy.FeatureActionSubrule;

/** ## Nested weather-state contracts for puddle lifecycle ownership. */
public enum PuddleActionSubrule
        implements FeatureActionSubrule<PuddleActionPhase> {
    FEATURE_WORLD_ALLOWED(PuddleActionPhase.WEATHER_GATE,
            "PuddleFeatureWorldGate"),
    PRECIPITATION_ALLOWED(PuddleActionPhase.WEATHER_GATE,
            "BiomePrecipitationGate"),
    RAIN_REJECTED_RETIRED(PuddleActionPhase.RETIRE_INVALID,
            "RainRejectedRetirement"),
    DISTANT_PUDDLES_RETIRED(PuddleActionPhase.RETIRE_INVALID,
            "DistanceRetirement"),
    RAIN_SEED_AND_EXPAND(PuddleActionPhase.GROW,
            "RainGrowthContract"),
    DRY_GRACE_EXPIRED(PuddleActionPhase.DRY,
            "PuddleDryGraceGate"),
    PLAYER_VISIBLE_SET(PuddleActionPhase.RENDER,
            "PuddleVisibilityContract");

    private final PuddleActionPhase phase;
    private final String owner;

    PuddleActionSubrule(PuddleActionPhase phase, String owner) {
        this.phase = phase;
        this.owner = owner;
    }

    @Override
    public PuddleActionPhase phase() {
        return phase;
    }

    @Override
    public String owner() {
        return owner;
    }
}