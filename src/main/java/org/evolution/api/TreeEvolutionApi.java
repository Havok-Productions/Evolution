package org.evolution.api;

public interface TreeEvolutionApi {
    boolean enabled();

    int knownTreeCount();

    long changedBlockCount();
}
