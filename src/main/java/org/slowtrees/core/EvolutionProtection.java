package org.slowtrees.core;

import org.bukkit.Location;

interface EvolutionProtection {
    void onLoad();

    void onEnable();

    void reload();

    boolean allows(Location location, String feature);

    String status();
}
