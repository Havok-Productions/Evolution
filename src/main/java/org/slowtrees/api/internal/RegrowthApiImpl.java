package org.slowtrees.api.internal;

import org.slowtrees.api.RegrowthApi;
import org.slowtrees.regrowth.PlantRegrowthFeature;

final class RegrowthApiImpl implements RegrowthApi {
    private final PlantRegrowthFeature feature;

    RegrowthApiImpl(PlantRegrowthFeature feature) {
        this.feature = feature;
    }

    @Override
    public int queuedCount() {
        return feature.queuedRegrowthCount();
    }

    @Override
    public int activeCount() {
        return feature.activeRegrowthCount();
    }

    @Override
    public int decayingCount() {
        return feature.activeDecayCount();
    }

    @Override
    public String status() {
        return feature.status();
    }
}
