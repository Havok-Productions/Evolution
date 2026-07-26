package org.evolution.api;

public interface MeadowGrowthApi {
    boolean enabled();

    long grassBlocksSpread();

    long plantsGrown();

    long flowersGrown();

    String status();
}
