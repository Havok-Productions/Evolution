package org.evolution.api;

public interface WindApi {
    boolean enabled();

    long leafParticlesSpawned();

    long leafLitterPlaced();

    WindSnapshot currentPattern();

    String status();
}
