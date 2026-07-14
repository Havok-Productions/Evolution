package org.slowtrees.api.internal;

import org.slowtrees.api.TreeEvolutionApi;
import org.slowtrees.treeevolution.TreeEvolutionFeature;

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
