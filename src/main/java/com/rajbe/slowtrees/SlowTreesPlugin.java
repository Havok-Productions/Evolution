package com.rajbe.slowtrees;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class SlowTreesPlugin extends JavaPlugin {
    private SlowTreesConfig slowTreesConfig;
    private TreeRegrowthService regrowthService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.slowTreesConfig = SlowTreesConfig.load(this);
        this.regrowthService = new TreeRegrowthService(this, slowTreesConfig);
        getServer().getPluginManager().registerEvents(regrowthService, this);
        regrowthService.loadQueuedTrees();
        getLogger().info("SlowTrees enabled with " + regrowthService.pendingCount() + " queued tree(s).");
    }

    @Override
    public void onDisable() {
        if (regrowthService != null) {
            regrowthService.saveQueuedTrees();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " <reload|pending>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            this.slowTreesConfig = SlowTreesConfig.load(this);
            this.regrowthService.updateConfig(slowTreesConfig);
            sender.sendMessage("SlowTrees config reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("pending")) {
            sender.sendMessage("SlowTrees has " + regrowthService.pendingCount() + " queued tree(s).");
            return true;
        }

        sender.sendMessage("Usage: /" + label + " <reload|pending>");
        return true;
    }
}
