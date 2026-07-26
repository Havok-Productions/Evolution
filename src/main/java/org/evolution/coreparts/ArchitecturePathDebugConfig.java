package org.evolution.coreparts;

import org.bukkit.configuration.file.FileConfiguration;

public final class ArchitecturePathDebugConfig {
    private final boolean enabled;
    private final int recentEvents;
    private final long saveIntervalMillis;

    private ArchitecturePathDebugConfig(boolean enabled, int recentEvents, long saveIntervalMillis) {
        this.enabled = enabled;
        this.recentEvents = recentEvents;
        this.saveIntervalMillis = saveIntervalMillis;
    }

    static ArchitecturePathDebugConfig load(EvolutionPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new ArchitecturePathDebugConfig(
                config.getBoolean("architecture-pathfinding.debug.enabled", true),
                Math.max(0, config.getInt("architecture-pathfinding.debug.recent-events", 160)),
                Math.max(1000L, config.getLong("architecture-pathfinding.debug.save-interval-millis", 10000L))
        );
    }

    boolean enabled() {
        return enabled;
    }

    int recentEvents() {
        return recentEvents;
    }

    long saveIntervalMillis() {
        return saveIntervalMillis;
    }
}
