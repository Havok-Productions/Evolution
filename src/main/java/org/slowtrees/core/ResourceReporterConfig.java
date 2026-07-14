package org.slowtrees.core;

import org.bukkit.configuration.file.FileConfiguration;

public final class ResourceReporterConfig {
    private final boolean enabled;
    private final int recentEvents;
    private final long saveIntervalMillis;
    private final long slowSampleMillis;
    private final boolean traceToArchitectureDebug;
    private final int topTaskLimit;

    private ResourceReporterConfig(
            boolean enabled,
            int recentEvents,
            long saveIntervalMillis,
            long slowSampleMillis,
            boolean traceToArchitectureDebug,
            int topTaskLimit
    ) {
        this.enabled = enabled;
        this.recentEvents = recentEvents;
        this.saveIntervalMillis = saveIntervalMillis;
        this.slowSampleMillis = slowSampleMillis;
        this.traceToArchitectureDebug = traceToArchitectureDebug;
        this.topTaskLimit = topTaskLimit;
    }

    static ResourceReporterConfig load(SlowTreesPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new ResourceReporterConfig(
                config.getBoolean("resource-reporter.enabled", true),
                Math.max(0, config.getInt("resource-reporter.recent-events", 160)),
                Math.max(1000L, config.getLong("resource-reporter.save-interval-millis", 10000L)),
                Math.max(1L, config.getLong("resource-reporter.slow-sample-millis", 10L)),
                config.getBoolean("resource-reporter.trace-to-architecture-debug", true),
                Math.max(5, config.getInt("resource-reporter.top-task-limit", 40))
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

    long slowSampleMillis() {
        return slowSampleMillis;
    }

    boolean traceToArchitectureDebug() {
        return traceToArchitectureDebug;
    }

    int topTaskLimit() {
        return topTaskLimit;
    }
}
