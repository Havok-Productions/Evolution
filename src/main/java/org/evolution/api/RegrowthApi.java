package org.evolution.api;

public interface RegrowthApi {
    int queuedCount();

    int activeCount();

    int decayingCount();

    String status();
}
