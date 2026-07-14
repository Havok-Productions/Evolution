package org.slowtrees.core;

import java.util.ArrayList;
import java.util.List;
import org.slowtrees.api.SlowTreesApi;
import org.slowtrees.api.SlowTreesProvider;
import org.slowtrees.api.internal.SlowTreesApiImpl;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.slowtrees.ecology.EcologyEvolutionFeature;
import org.slowtrees.meadow.MeadowGrowthFeature;
import org.slowtrees.nether.NetherCorruptionFeature;
import org.slowtrees.regrowth.PlantRegrowthFeature;
import org.slowtrees.treeevolution.TreeEvolutionFeature;
import org.slowtrees.wind.WindFeature;

public final class SlowTreesPlugin extends JavaPlugin {
    private final List<PluginFeature> features = new ArrayList<>();
    private ArchitecturePathDebug architecturePathDebug;
    private ResourceReporter resourceReporter;
    private PlantRegrowthFeature regrowthFeature;
    private MeadowGrowthFeature meadowFeature;
    private EcologyEvolutionFeature ecologyFeature;
    private TreeEvolutionFeature treeEvolutionFeature;
    private NetherCorruptionFeature netherFeature;
    private WindFeature windFeature;
    private SlowTreesApi api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        architecturePathDebug = new ArchitecturePathDebug(this);
        architecturePathDebug.resetForStartup(this);
        resourceReporter = new ResourceReporter(this);
        resourceReporter.resetForStartup(this);
        architecturePathDebug.trace(this, "core", "persistence.save-default-config", "config.yml");
        architecturePathDebug.trace(this, "core", "persistence.save-config", "config.yml defaults merged");
        architecturePathDebug.trace(this, "core", "plugin.enable.start", "default config saved and merged");
        regrowthFeature = new PlantRegrowthFeature(this);
        meadowFeature = new MeadowGrowthFeature(this);
        ecologyFeature = new EcologyEvolutionFeature(this);
        treeEvolutionFeature = new TreeEvolutionFeature(this);
        netherFeature = new NetherCorruptionFeature(this);
        windFeature = new WindFeature(this);
        registerFeature(regrowthFeature);
        registerFeature(meadowFeature);
        registerFeature(ecologyFeature);
        registerFeature(treeEvolutionFeature);
        registerFeature(netherFeature);
        registerFeature(windFeature);
        for (PluginFeature feature : features) {
            architecturePathDebug.trace(this, "core", "feature.enable.start", feature.getClass().getSimpleName());
            try (ResourceReporter.ReportSample sample = resourceReporter().begin("core", "feature.enable")) {
                feature.onEnable();
                sample.changedUnits(1).detail(feature.getClass().getSimpleName());
            }
            architecturePathDebug.trace(this, "core", "feature.enable.done", feature.getClass().getSimpleName());
        }
        api = new SlowTreesApiImpl(this);
        SlowTreesProvider.register(api);
        architecturePathDebug.trace(this, "core", "api.register", "SlowTreesProvider");
        getLogger().info("SlowTrees enabled with " + features.size() + " feature module(s).");
        architecturePathDebug.trace(this, "core", "plugin.enable.done", "features=" + features.size());
    }

    @Override
    public void onDisable() {
        try (ResourceReporter.ReportSample sample = resourceReporter().begin("core", "plugin.disable")) {
            pathDebug().trace(this, "core", "plugin.disable.start", "features=" + features.size());
            if (api != null) {
                SlowTreesProvider.unregister(api);
                pathDebug().trace(this, "core", "api.unregister", "SlowTreesProvider");
                api = null;
            }
            for (PluginFeature feature : features) {
                try (ResourceReporter.ReportSample featureSample = resourceReporter().begin("core", "feature.disable")) {
                    feature.onDisable();
                    featureSample.changedUnits(1).detail(feature.getClass().getSimpleName());
                }
            }
            sample.workUnits(features.size()).detail("features=" + features.size());
            pathDebug().trace(this, "core", "plugin.disable.done", "features disabled");
        } finally {
            resourceReporter().saveNow(this);
            pathDebug().saveNow(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " <reload|status|pending|tree>");
            return true;
        }

        if (treeEvolutionFeature != null && treeEvolutionFeature.handleCommand(sender, label, args)) {
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            try (ResourceReporter.ReportSample sample = resourceReporter().begin("core", "command.reload")) {
                reloadSlowTrees();
                sample.changedUnits(features.size()).detail("features=" + features.size());
            }
            pathDebug().trace(this, "core", "command.reload", "features reloaded");
            sender.sendMessage("SlowTrees config reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("pending")) {
            try (ResourceReporter.ReportSample sample = resourceReporter().begin("core", "command.status")) {
                pathDebug().trace(this, "core", "command.status", "status requested");
                for (String status : featureStatuses()) {
                    sender.sendMessage(status);
                }
                sample.workUnits(features.size()).detail("features=" + features.size());
            }
            resourceReporter().saveNow(this);
            pathDebug().saveNow(this);
            return true;
        }

        sender.sendMessage("Usage: /" + label + " <reload|status|pending|tree>");
        return true;
    }

    private void registerFeature(PluginFeature feature) {
        features.add(feature);
        pathDebug().trace(this, "core", "feature.register", feature.getClass().getSimpleName());
        if (feature instanceof Listener listener) {
            getServer().getPluginManager().registerEvents(listener, this);
            pathDebug().trace(this, "core", "feature.listener", feature.getClass().getSimpleName());
        }
    }

    public ArchitecturePathDebug pathDebug() {
        if (architecturePathDebug == null) {
            architecturePathDebug = new ArchitecturePathDebug(this);
        }
        return architecturePathDebug;
    }

    public ResourceReporter resourceReporter() {
        if (resourceReporter == null) {
            resourceReporter = new ResourceReporter(this);
        }
        return resourceReporter;
    }

    public void reloadSlowTrees() {
        reloadConfig();
        pathDebug().trace(this, "core", "persistence.reload-config", "config.yml");
        pathDebug().reload(this);
        resourceReporter().reload(this);
        features.forEach(PluginFeature::reload);
    }

    public List<String> featureStatuses() {
        return features.stream()
                .map(PluginFeature::status)
                .toList();
    }

    public PlantRegrowthFeature regrowthFeature() {
        return regrowthFeature;
    }

    public MeadowGrowthFeature meadowFeature() {
        return meadowFeature;
    }

    public EcologyEvolutionFeature ecologyFeature() {
        return ecologyFeature;
    }

    public TreeEvolutionFeature treeEvolutionFeature() {
        return treeEvolutionFeature;
    }

    public NetherCorruptionFeature netherFeature() {
        return netherFeature;
    }

    public WindFeature windFeature() {
        return windFeature;
    }
}
