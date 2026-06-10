package org.slowtrees.api.internal;

import org.slowtrees.api.WindApi;
import org.slowtrees.api.WindSnapshot;
import org.slowtrees.wind.WindFeature;

final class WindApiImpl implements WindApi {
    private final WindFeature feature;

    WindApiImpl(WindFeature feature) {
        this.feature = feature;
    }

    @Override
    public boolean enabled() {
        return feature.enabled();
    }

    @Override
    public long leafParticlesSpawned() {
        return feature.leafParticlesSpawned();
    }

    @Override
    public long leafLitterPlaced() {
        return feature.leafLitterPlaced();
    }

    @Override
    public WindSnapshot currentPattern() {
        return new WindSnapshot(feature.currentWindX(), feature.currentWindZ(), feature.currentWindStrength());
    }

    @Override
    public String status() {
        return feature.status();
    }
}
