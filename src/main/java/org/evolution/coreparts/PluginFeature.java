package org.evolution.coreparts;

public interface PluginFeature {
    void onEnable();

    void onDisable();

    void reload();

    String status();
}
