package org.slowtrees.api.internal;

import java.util.List;
import org.slowtrees.api.NetherCorruptionApi;
import org.slowtrees.api.RegrowthApi;
import org.slowtrees.api.SlowTreesApi;
import org.slowtrees.api.WindApi;
import org.slowtrees.core.SlowTreesPlugin;

public final class SlowTreesApiImpl implements SlowTreesApi {
    private final SlowTreesPlugin plugin;
    private final RegrowthApi regrowth;
    private final NetherCorruptionApi nether;
    private final WindApi wind;

    public SlowTreesApiImpl(SlowTreesPlugin plugin) {
        this.plugin = plugin;
        this.regrowth = new RegrowthApiImpl(plugin.regrowthFeature());
        this.nether = new NetherCorruptionApiImpl(plugin.netherFeature());
        this.wind = new WindApiImpl(plugin.windFeature());
    }

    @Override
    public String version() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public List<String> statusLines() {
        return plugin.featureStatuses();
    }

    @Override
    public void reload() {
        plugin.reloadSlowTrees();
    }

    @Override
    public RegrowthApi regrowth() {
        return regrowth;
    }

    @Override
    public NetherCorruptionApi nether() {
        return nether;
    }

    @Override
    public WindApi wind() {
        return wind;
    }
}
