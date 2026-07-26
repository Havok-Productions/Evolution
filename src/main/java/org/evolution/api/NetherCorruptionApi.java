package org.evolution.api;

import org.bukkit.Location;
import org.bukkit.Material;

public interface NetherCorruptionApi {
    boolean enabled();

    int trackedSourceCount();

    long changedBlockCount();

    boolean registerPortalSourceNear(Location location, int radius);

    boolean isMimicable(Material material);

    boolean isCorrupted(Material material);

    String status();
}
