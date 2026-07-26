package org.evolution.api.internal;

import org.bukkit.Location;
import org.bukkit.Material;
import org.evolution.api.NetherCorruptionApi;
import org.evolution.features.nether.NetherCorruptionFeature;

final class NetherCorruptionApiImpl implements NetherCorruptionApi {
    private final NetherCorruptionFeature feature;

    NetherCorruptionApiImpl(NetherCorruptionFeature feature) {
        this.feature = feature;
    }

    @Override
    public boolean enabled() {
        return feature.enabled();
    }

    @Override
    public int trackedSourceCount() {
        return feature.trackedSourceCount();
    }

    @Override
    public long changedBlockCount() {
        return feature.changedBlockCount();
    }

    @Override
    public boolean registerPortalSourceNear(Location location, int radius) {
        return feature.registerPortalSourceNear(location, radius);
    }

    @Override
    public boolean isMimicable(Material material) {
        return feature.isMimicable(material);
    }

    @Override
    public boolean isCorrupted(Material material) {
        return feature.isCorruptionMaterial(material);
    }

    @Override
    public String status() {
        return feature.status();
    }
}
