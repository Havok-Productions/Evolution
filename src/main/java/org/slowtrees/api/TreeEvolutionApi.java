package org.slowtrees.api;

public interface TreeEvolutionApi {
    boolean enabled();

    int knownTreeCount();

    long changedBlockCount();
}
