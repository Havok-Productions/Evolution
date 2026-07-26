package org.evolution.coreparts;

import org.bukkit.Location;

interface EvolutionProtection {
    void onLoad();

    void onEnable();

    void reload();

    boolean allows(Location location, String feature);

    String status();
}
