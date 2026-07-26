package org.evolution.coreparts;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.event.Listener;

final class FeatureRegistry {
    private final EvolutionPlugin plugin;
    private final List<RegisteredFeature> features = new ArrayList<>();
    private final Set<String> featureIds = new HashSet<>();

    FeatureRegistry(EvolutionPlugin plugin) {
        this.plugin = plugin;
    }

    <T extends PluginFeature> T register(String id, T feature) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Feature id must not be blank.");
        }
        if (!featureIds.add(id)) {
            throw new IllegalArgumentException("Duplicate feature id: " + id);
        }

        features.add(new RegisteredFeature(id, feature));
        String detail = describe(id, feature);
        plugin.pathDebug().trace(plugin, "core", "feature.register", detail);
        plugin.pathDebug().trace(plugin, "core", "architecture.feature-boundary",
                detail + " ## owned by features/" + packageSection(feature));
        if (feature instanceof Listener listener) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            plugin.pathDebug().trace(plugin, "core", "feature.listener", detail);
        }
        return feature;
    }

    void enableAll() {
        for (RegisteredFeature registered : features) {
            String detail = describe(registered.id(), registered.feature());
            plugin.pathDebug().trace(plugin, "core", "feature.enable.start", detail);
            try (ResourceReporter.ReportSample sample =
                    plugin.resourceReporter().begin("core", "feature.enable." + registered.id())) {
                registered.feature().onEnable();
                sample.changedUnits(1).detail(detail);
            }
            plugin.pathDebug().trace(plugin, "core", "feature.enable.done", detail);
        }
    }

    void disableAll() {
        for (RegisteredFeature registered : features) {
            String detail = describe(registered.id(), registered.feature());
            plugin.pathDebug().trace(plugin, "core", "feature.disable.start", detail);
            try (ResourceReporter.ReportSample sample =
                    plugin.resourceReporter().begin("core", "feature.disable." + registered.id())) {
                registered.feature().onDisable();
                sample.changedUnits(1).detail(detail);
            }
            plugin.pathDebug().trace(plugin, "core", "feature.disable.done", detail);
        }
    }

    void reloadAll() {
        for (RegisteredFeature registered : features) {
            String detail = describe(registered.id(), registered.feature());
            plugin.pathDebug().trace(plugin, "core", "feature.reload.start", detail);
            try (ResourceReporter.ReportSample sample =
                    plugin.resourceReporter().begin("core", "feature.reload." + registered.id())) {
                registered.feature().reload();
                sample.changedUnits(1).detail(detail);
            }
            plugin.pathDebug().trace(plugin, "core", "feature.reload.done", detail);
        }
    }

    List<String> statuses() {
        return features.stream()
                .map(registered -> registered.feature().status())
                .toList();
    }

    int size() {
        return features.size();
    }

    private String describe(String id, PluginFeature feature) {
        return "id=" + id
                + " class=" + feature.getClass().getSimpleName()
                + " package=" + feature.getClass().getPackageName();
    }

    private String packageSection(PluginFeature feature) {
        String packageName = feature.getClass().getPackageName();
        int separator = packageName.lastIndexOf('.');
        return separator < 0 ? packageName : packageName.substring(separator + 1);
    }

    private record RegisteredFeature(String id, PluginFeature feature) {
    }
}
