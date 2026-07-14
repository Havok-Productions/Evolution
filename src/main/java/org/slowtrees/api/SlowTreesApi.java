package org.slowtrees.api;

import java.util.List;

public interface SlowTreesApi {
    String version();

    List<String> statusLines();

    void reload();

    RegrowthApi regrowth();

    MeadowGrowthApi meadow();

    NetherCorruptionApi nether();

    WindApi wind();

    TreeEvolutionApi treeEvolution();
}
