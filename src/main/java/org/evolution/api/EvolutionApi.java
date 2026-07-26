package org.evolution.api;

import java.util.List;

public interface EvolutionApi {
    String version();

    List<String> statusLines();

    void reload();

    RegrowthApi regrowth();

    MeadowGrowthApi meadow();

    NetherCorruptionApi nether();

    WindApi wind();

    TreeEvolutionApi treeEvolution();
}
