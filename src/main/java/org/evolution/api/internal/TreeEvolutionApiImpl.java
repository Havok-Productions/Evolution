package org.evolution.api.internal;

import org.evolution.api.TreeEvolutionApi;
import org.evolution.features.treeevolution.TreeEvolutionFeature;

public final class TreeEvolutionApiImpl implements TreeEvolutionApi {
    private final TreeEvolutionFeature feature;

    public TreeEvolutionApiImpl(TreeEvolutionFeature feature) {
        this.feature = feature;
    }

    @Override
    public boolean enabled() {
        return feature.enabled();
    }

    @Override
    public int knownTreeCount() {
        return feature.knownTreeCount();
    }

    @Override
    public long changedBlockCount() {
        return feature.changedBlockCount();
    }
}
