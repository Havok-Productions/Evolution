package org.evolution.features.nether;

enum NetherBiomeStyle {
    WASTES("nether-wastes"),
    CRIMSON("crimson"),
    WARPED("warped"),
    SOUL("soul"),
    BASALT("basalt");

    private final String displayName;

    NetherBiomeStyle(String displayName) {
        this.displayName = displayName;
    }

    String displayName() {
        return displayName;
    }
}
