package org.slowtrees.api;

import java.util.List;

public interface SlowTreesApi {
    String version();

    List<String> statusLines();

    void reload();

    RegrowthApi regrowth();

    NetherCorruptionApi nether();

    WindApi wind();
}
