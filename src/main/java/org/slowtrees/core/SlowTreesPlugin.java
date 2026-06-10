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
import org.slowtrees.meadow.MeadowGrowthFeature;
import org.slowtrees.nether.NetherCorruptionFeature;
import org.slowtrees.regrowth.PlantRegrowthFeature;
import org.slowtrees.wind.WindFeature;

public final class SlowTreesPlugin extends JavaPlugin {
    private final List<PluginFeature> features = new ArrayList<>();
    private ArchitecturePathDebug architecturePathDebug;
    private PlantRegrowthFeature regrowthFeature;
    private MeadowGrowthFeature meadowFeature;
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
        architecturePathDebug.trace(this, "core", "persistence.save-default-config", "config.yml");
        architecturePathDebug.trace(this, "core", "persistence.save-config", "config.yml defaults merged");
        architecturePathDebug.trace(this, "core", "plugin.enable.start", "default config saved and merged");
        regrowthFeature = new PlantRegrowthFeature(this);
        meadowFeature = new MeadowGrowthFeature(this);
        netherFeature = new NetherCorruptionFeature(this);
        windFeature = new WindFeature(this);
        registerFeature(regrowthFeature);
        registerFeature(meadowFeature);
        registerFeature(netherFeature);
        registerFeature(windFeature);
        for (PluginFeature feature : features) {
            architecturePathDebug.trace(this, "core", "feature.enable.start", feature.getClass().getSimpleName());
            feature.onEnable();
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
        pathDebug().trace(this, "core", "plugin.disable.start", "features=" + features.size());
        if (api != null) {
            SlowTreesProvider.unregister(api);
            pathDebug().trace(this, "core", "api.unregister", "SlowTreesProvider");
            api = null;
        }
        features.forEach(PluginFeature::onDisable);
        pathDebug().trace(this, "core", "plugin.disable.done", "features disabled");
        pathDebug().saveNow(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " <reload|status|pending>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadSlowTrees();
            pathDebug().trace(this, "core", "command.reload", "features reloaded");
            sender.sendMessage("SlowTrees config reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("pending")) {
            pathDebug().trace(this, "core", "command.status", "status requested");
            for (String status : featureStatuses()) {
                sender.sendMessage(status);
            }
            pathDebug().saveNow(this);
            return true;
        }

        sender.sendMessage("Usage: /" + label + " <reload|status|pending>");
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

    public void reloadSlowTrees() {
        reloadConfig();
        pathDebug().trace(this, "core", "persistence.reload-config", "config.yml");
        pathDebug().reload(this);
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

    public NetherCorruptionFeature netherFeature() {
        return netherFeature;
    }

    public WindFeature windFeature() {
        return windFeature;
    }
}
