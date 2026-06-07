package org.slowtrees.core;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.slowtrees.nether.NetherCorruptionFeature;
import org.slowtrees.regrowth.PlantRegrowthFeature;

public final class SlowTreesPlugin extends JavaPlugin {
    private final List<PluginFeature> features = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registerFeature(new PlantRegrowthFeature(this));
        registerFeature(new NetherCorruptionFeature(this));
        features.forEach(PluginFeature::onEnable);
        getLogger().info("SlowTrees enabled with " + features.size() + " feature module(s).");
    }

    @Override
    public void onDisable() {
        features.forEach(PluginFeature::onDisable);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " <reload|status|pending>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            features.forEach(PluginFeature::reload);
            sender.sendMessage("SlowTrees config reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("pending")) {
            for (PluginFeature feature : features) {
                sender.sendMessage(feature.status());
            }
            return true;
        }

        sender.sendMessage("Usage: /" + label + " <reload|status|pending>");
        return true;
    }

    private void registerFeature(PluginFeature feature) {
        features.add(feature);
        if (feature instanceof Listener listener) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }
}
