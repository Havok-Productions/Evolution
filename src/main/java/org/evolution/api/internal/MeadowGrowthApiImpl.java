package org.evolution.api.internal;

import org.evolution.api.MeadowGrowthApi;
import org.evolution.features.meadow.MeadowGrowthFeature;

final class MeadowGrowthApiImpl implements MeadowGrowthApi {
    private final MeadowGrowthFeature feature;

    MeadowGrowthApiImpl(MeadowGrowthFeature feature) {
        this.feature = feature;
    }

    @Override
    public boolean enabled() {
        return feature.enabled();
    }

    @Override
    public long grassBlocksSpread() {
        return feature.grassBlocksSpread();
    }

    @Override
    public long plantsGrown() {
        return feature.plantsGrown();
    }

    @Override
    public long flowersGrown() {
        return feature.flowersGrown();
    }

    @Override
    public String status() {
        return feature.status();
    }
}
