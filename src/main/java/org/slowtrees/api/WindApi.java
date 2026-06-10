package org.slowtrees.api;

public interface WindApi {
    boolean enabled();

    long leafParticlesSpawned();

    long leafLitterPlaced();

    WindSnapshot currentPattern();

    String status();
}
