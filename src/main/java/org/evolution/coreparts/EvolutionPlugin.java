package org.evolution.coreparts;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.evolution.api.EvolutionApi;
import org.evolution.api.EvolutionProvider;
import org.evolution.api.internal.EvolutionApiImpl;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.evolution.features.ecology.EcologyEvolutionFeature;
import org.evolution.features.meadow.MeadowGrowthFeature;
import org.evolution.features.nether.NetherCorruptionFeature;
import org.evolution.features.puddles.PuddleFeature;
import org.evolution.features.portal.ShapedPortalFeature;
import org.evolution.features.regrowth.PlantRegrowthFeature;
import org.evolution.features.treeevolution.TreeEvolutionFeature;
import org.evolution.features.waves.WaveFeature;
import org.evolution.features.wind.WindFeature;

public final class EvolutionPlugin extends JavaPlugin {
    private FeatureRegistry featureRegistry;
    private ArchitecturePathDebug architecturePathDebug;
    private ResourceReporter resourceReporter;
    private PlantRegrowthFeature regrowthFeature;
    private MeadowGrowthFeature meadowFeature;
    private EcologyEvolutionFeature ecologyFeature;
    private TreeEvolutionFeature treeEvolutionFeature;
    private NetherCorruptionFeature netherFeature;
    private ShapedPortalFeature shapedPortalFeature;
    private WindFeature windFeature;
    private PuddleFeature puddleFeature;
    private WaveFeature waveFeature;
    private EvolutionApi api;
    private EvolutionProtection evolutionProtection =
            new NoopEvolutionProtection("WorldGuard not detected");

    @Override
    public void onLoad() {
        if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }
        try {
            evolutionProtection = new WorldGuardEvolutionProtection(this);
            evolutionProtection.onLoad();
        } catch (LinkageError | RuntimeException failure) {
            evolutionProtection = new NoopEvolutionProtection(
                    "WorldGuard integration unavailable");
            getLogger().warning("Could not register WorldGuard evolution flag: "
                    + failure.getClass().getSimpleName());
        }
    }

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
        enableEvolutionProtection();
        featureRegistry = new FeatureRegistry(this);
        regrowthFeature = featureRegistry.register("plant-regrowth",
                new PlantRegrowthFeature(this));
        meadowFeature = featureRegistry.register("meadow-growth",
                new MeadowGrowthFeature(this));
        ecologyFeature = featureRegistry.register("ecology-evolution",
                new EcologyEvolutionFeature(this));
        treeEvolutionFeature = featureRegistry.register("tree-evolution",
                new TreeEvolutionFeature(this));
        netherFeature = featureRegistry.register("nether-corruption",
                new NetherCorruptionFeature(this));
        shapedPortalFeature = featureRegistry.register("shaped-portals",
                new ShapedPortalFeature(this));
        windFeature = featureRegistry.register("wind",
                new WindFeature(this));
        puddleFeature = featureRegistry.register("puddles",
                new PuddleFeature(this));
        waveFeature = featureRegistry.register("waves",
                new WaveFeature(this));
        featureRegistry.enableAll();
        api = new EvolutionApiImpl(this);
        EvolutionProvider.register(api);
        architecturePathDebug.trace(this, "core", "api.register", "EvolutionProvider");
        getLogger().info("Evolution enabled with " + featureRegistry.size() + " feature module(s).");
        architecturePathDebug.trace(this, "core", "plugin.enable.done",
                "features=" + featureRegistry.size() + " architecture=coreparts+features");
    }

    @Override
    public void onDisable() {
        int featureCount = featureRegistry == null ? 0 : featureRegistry.size();
        try (ResourceReporter.ReportSample sample = resourceReporter().begin("core", "plugin.disable")) {
            pathDebug().trace(this, "core", "plugin.disable.start", "features=" + featureCount);
            if (api != null) {
                EvolutionProvider.unregister(api);
                pathDebug().trace(this, "core", "api.unregister", "EvolutionProvider");
                api = null;
            }
            if (featureRegistry != null) {
                featureRegistry.disableAll();
            }
            sample.workUnits(featureCount).detail("features=" + featureCount);
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
                reloadEvolution();
                int featureCount = featureRegistry == null ? 0 : featureRegistry.size();
                sample.changedUnits(featureCount).detail("features=" + featureCount);
            }
            pathDebug().trace(this, "core", "command.reload", "features reloaded");
            sender.sendMessage("Evolution config reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("pending")) {
            try (ResourceReporter.ReportSample sample = resourceReporter().begin("core", "command.status")) {
                pathDebug().trace(this, "core", "command.status", "status requested");
                for (String status : featureStatuses()) {
                    sender.sendMessage(status);
                }
                int featureCount = featureRegistry == null ? 0 : featureRegistry.size();
                sample.workUnits(featureCount).detail("features=" + featureCount);
            }
            resourceReporter().saveNow(this);
            pathDebug().saveNow(this);
            return true;
        }

        sender.sendMessage("Usage: /" + label + " <reload|status|pending|tree>");
        return true;
    }

    private void migrateLegacyDataFolder() {
        File current = getDataFolder();
        File parent = current.getParentFile();
        if (parent == null || current.exists()) {
            return;
        }
        File legacy = new File(parent, "SlowTrees");
        if (!legacy.isDirectory()) {
            return;
        }
        if (legacy.renameTo(current)) {
            getLogger().info("Migrated legacy SlowTrees data folder to Evolution.");
        } else {
            getLogger().warning("Could not migrate plugins/SlowTrees to plugins/Evolution. Existing Evolution config will be used.");
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

    public void reloadEvolution() {
        reloadConfig();
        pathDebug().trace(this, "core", "persistence.reload-config", "config.yml");
        pathDebug().reload(this);
        resourceReporter().reload(this);
        try {
            evolutionProtection.reload();
        } catch (LinkageError | RuntimeException failure) {
            pathDebug().trace(this, "worldguard",
                    "integration.reload-failed",
                    failure.getClass().getSimpleName()
                            + " ## Evolution remains active without region integration");
            evolutionProtection = new NoopEvolutionProtection(
                    "WorldGuard reload failed");
        }
        if (featureRegistry != null) {
            featureRegistry.reloadAll();
        }
    }

    public List<String> featureStatuses() {
        List<String> statuses = new ArrayList<>(featureRegistry == null
                ? List.of()
                : featureRegistry.statuses());
        statuses.add(evolutionProtection.status());
        return List.copyOf(statuses);
    }

    public boolean canEvolveAt(Location location, String feature) {
        return evolutionProtection.allows(location, feature);
    }

    private void enableEvolutionProtection() {
        try {
            evolutionProtection.onEnable();
        } catch (LinkageError | RuntimeException failure) {
            pathDebug().trace(this, "worldguard",
                    "integration.enable-failed",
                    failure.getClass().getSimpleName()
                            + " ## Evolution remains active without region integration");
            getLogger().warning("WorldGuard integration could not be enabled: "
                    + failure.getClass().getSimpleName());
            evolutionProtection = new NoopEvolutionProtection(
                    "WorldGuard enable failed");
        }
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

    public ShapedPortalFeature shapedPortalFeature() {
        return shapedPortalFeature;
    }

    public WindFeature windFeature() {
        return windFeature;
    }

    public PuddleFeature puddleFeature() {
        return puddleFeature;
    }

    public WaveFeature waveFeature() {
        return waveFeature;
    }
}
