package org.evolution.api.internal;

import java.util.List;
import org.evolution.api.MeadowGrowthApi;
import org.evolution.api.NetherCorruptionApi;
import org.evolution.api.RegrowthApi;
import org.evolution.api.EvolutionApi;
import org.evolution.api.TreeEvolutionApi;
import org.evolution.api.WindApi;
import org.evolution.coreparts.EvolutionPlugin;

public final class EvolutionApiImpl implements EvolutionApi {
    private final EvolutionPlugin plugin;
    private final RegrowthApi regrowth;
    private final MeadowGrowthApi meadow;
    private final NetherCorruptionApi nether;
    private final WindApi wind;
    private final TreeEvolutionApi treeEvolution;

    public EvolutionApiImpl(EvolutionPlugin plugin) {
        this.plugin = plugin;
        this.regrowth = new RegrowthApiImpl(plugin.regrowthFeature());
        this.meadow = new MeadowGrowthApiImpl(plugin.meadowFeature());
        this.nether = new NetherCorruptionApiImpl(plugin.netherFeature());
        this.wind = new WindApiImpl(plugin.windFeature());
        this.treeEvolution = new TreeEvolutionApiImpl(plugin.treeEvolutionFeature());
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
        plugin.reloadEvolution();
    }

    @Override
    public RegrowthApi regrowth() {
        return regrowth;
    }

    @Override
    public MeadowGrowthApi meadow() {
        return meadow;
    }

    @Override
    public NetherCorruptionApi nether() {
        return nether;
    }

    @Override
    public WindApi wind() {
        return wind;
    }

    @Override
    public TreeEvolutionApi treeEvolution() {
        return treeEvolution;
    }
}
