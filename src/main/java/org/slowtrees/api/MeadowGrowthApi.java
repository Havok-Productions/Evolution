package org.slowtrees.api;

public interface MeadowGrowthApi {
    boolean enabled();

    long grassBlocksSpread();

    long plantsGrown();

    long flowersGrown();

    String status();
}
