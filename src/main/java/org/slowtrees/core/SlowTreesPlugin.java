package org.slowtrees.core;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.slowtrees.nether.NetherCorruptionFeature;
import org.slowtrees.regrowth.PlantRegrowthFeature;
import org.slowtrees.wind.WindFeature;

public final class SlowTreesPlugin extends JavaPlugin {
    private final List<PluginFeature> features = new ArrayList<>();
    private ArchitecturePathDebug architecturePathDebug;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        architecturePathDebug = new ArchitecturePathDebug(this);
        architecturePathDebug.trace(this, "core", "plugin.enable.start", "default config saved and merged");
        registerFeature(new PlantRegrowthFeature(this));
        registerFeature(new NetherCorruptionFeature(this));
        registerFeature(new WindFeature(this));
        for (PluginFeature feature : features) {
            architecturePathDebug.trace(this, "core", "feature.enable.start", feature.getClass().getSimpleName());
            feature.onEnable();
            architecturePathDebug.trace(this, "core", "feature.enable.done", feature.getClass().getSimpleName());
        }
        getLogger().info("SlowTrees enabled with " + features.size() + " feature module(s).");
        architecturePathDebug.trace(this, "core", "plugin.enable.done", "features=" + features.size());
    }

    @Override
    public void onDisable() {
        pathDebug().trace(this, "core", "plugin.disable.start", "features=" + features.size());
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
            reloadConfig();
            pathDebug().reload(this);
            features.forEach(PluginFeature::reload);
            pathDebug().trace(this, "core", "command.reload", "features reloaded");
            sender.sendMessage("SlowTrees config reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("pending")) {
            pathDebug().trace(this, "core", "command.status", "status requested");
            for (PluginFeature feature : features) {
                sender.sendMessage(feature.status());
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
}
