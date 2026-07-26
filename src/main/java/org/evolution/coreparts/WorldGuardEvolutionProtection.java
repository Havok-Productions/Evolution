package org.evolution.coreparts;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;

final class WorldGuardEvolutionProtection implements EvolutionProtection {
    static final String FLAG_NAME = "evolution";

    private final EvolutionPlugin plugin;
    private final AtomicLong allowedChecks = new AtomicLong();
    private final AtomicLong deniedChecks = new AtomicLong();
    private final AtomicLong failedChecks = new AtomicLong();
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private volatile StateFlag evolutionFlag;
    private volatile RegionQuery query;
    private volatile boolean configuredEnabled = true;
    private volatile boolean debugDenials = true;
    private volatile boolean failOpen = true;
    private volatile boolean active;

    WorldGuardEvolutionProtection(EvolutionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onLoad() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag(FLAG_NAME, true);
            registry.register(flag);
            evolutionFlag = flag;
            plugin.getLogger().info("Registered WorldGuard flag '" + FLAG_NAME + "'.");
        } catch (FlagConflictException conflict) {
            Flag<?> existing = registry.get(FLAG_NAME);
            if (existing instanceof StateFlag stateFlag) {
                evolutionFlag = stateFlag;
                plugin.getLogger().info("Using existing WorldGuard flag '" + FLAG_NAME + "'.");
            } else {
                plugin.getLogger().warning("WorldGuard flag '" + FLAG_NAME
                        + "' exists with an incompatible type; protection integration is disabled.");
            }
        }
    }

    @Override
    public void onEnable() {
        if (!plugin.getServer().getPluginManager()
                .isPluginEnabled("WorldGuard")) {
            active = false;
            plugin.pathDebug().trace(plugin, "worldguard",
                    "integration.skip-disabled-plugin",
                    "## WorldGuard was present during load but did not enable");
            return;
        }
        reload();
        query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        active = configuredEnabled && evolutionFlag != null;
        plugin.pathDebug().trace(plugin, "worldguard", "integration.enable",
                "active=" + active + " flag=" + FLAG_NAME
                        + " default=allow fail-open=" + failOpen
                        + " ## /rg flag <region> evolution deny blocks Evolution changes");
    }

    @Override
    public void reload() {
        configuredEnabled = plugin.getConfig().getBoolean("worldguard.enabled", true);
        debugDenials = plugin.getConfig().getBoolean("worldguard.debug-denials", true);
        failOpen = plugin.getConfig().getBoolean("worldguard.fail-open", true);
        failureLogged.set(false);
        active = configuredEnabled && evolutionFlag != null && query != null;
        plugin.pathDebug().trace(plugin, "worldguard", "config.loaded",
                "enabled=" + configuredEnabled + " flag=" + FLAG_NAME
                        + " default=allow debug-denials=" + debugDenials
                        + " fail-open=" + failOpen);
    }

    @Override
    public boolean allows(Location location, String feature) {
        if (!active || location == null || location.getWorld() == null) {
            return true;
        }
        try {
            boolean allowed = query.testState(
                    BukkitAdapter.adapt(location), null, evolutionFlag);
            if (allowed) {
                allowedChecks.incrementAndGet();
                return true;
            }
            deniedChecks.incrementAndGet();
            if (debugDenials) {
                plugin.pathDebug().traceSampled(plugin, "worldguard",
                        "gate.evolution-deny",
                        "feature=" + safeFeature(feature)
                                + " world=" + location.getWorld().getName()
                                + " block=" + location.getBlockX() + ","
                                + location.getBlockY() + ","
                                + location.getBlockZ()
                                + " ## WorldGuard evolution=deny protected this target");
            }
            return false;
        } catch (RuntimeException failure) {
            failedChecks.incrementAndGet();
            if (failureLogged.compareAndSet(false, true)) {
                plugin.getLogger().warning("WorldGuard evolution query failed; fail-open="
                        + failOpen + " (" + failure.getClass().getSimpleName() + ")");
            }
            plugin.pathDebug().traceSampled(plugin, "worldguard",
                    "gate.query-failed",
                    "feature=" + safeFeature(feature)
                            + " fail-open=" + failOpen
                            + " error=" + failure.getClass().getSimpleName());
            return failOpen;
        }
    }

    @Override
    public String status() {
        return "WorldGuard protection: " + (active ? "active" : "inactive")
                + ", flag=" + FLAG_NAME
                + ", allowed=" + allowedChecks.get()
                + ", denied=" + deniedChecks.get()
                + ", failed=" + failedChecks.get();
    }

    private String safeFeature(String feature) {
        return feature == null || feature.isBlank() ? "unknown" : feature;
    }
}
