package org.slowtrees.core;

public interface PluginFeature {
    void onEnable();

    void onDisable();

    void reload();

    String status();
}
