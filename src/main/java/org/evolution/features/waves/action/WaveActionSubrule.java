package org.evolution.features.waves.action;

import org.evolution.coreparts.hierarchy.FeatureActionSubrule;

/** ## Nested ownership contracts for wave simulation and player presentation. */
public enum WaveActionSubrule
        implements FeatureActionSubrule<WaveActionPhase> {
    PER_PLAYER_COAST_AREA_DISTRIBUTION(
            WaveActionPhase.COLLECT_VISUALS,
            "WaveCoastAreaViewPolicy");

    private final WaveActionPhase phase;
    private final String owner;

    WaveActionSubrule(WaveActionPhase phase, String owner) {
        this.phase = phase;
        this.owner = owner;
    }

    @Override
    public WaveActionPhase phase() {
        return phase;
    }

    @Override
    public String owner() {
        return owner;
    }
}
