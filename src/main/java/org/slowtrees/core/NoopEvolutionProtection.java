package org.slowtrees.core;

import org.bukkit.Location;

final class NoopEvolutionProtection implements EvolutionProtection {
    private final String reason;

    NoopEvolutionProtection(String reason) {
        this.reason = reason;
    }

    @Override
    public void onLoad() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void reload() {
    }

    @Override
    public boolean allows(Location location, String feature) {
        return true;
    }

    @Override
    public String status() {
        return "WorldGuard protection: inactive (" + reason + ")";
    }
}
