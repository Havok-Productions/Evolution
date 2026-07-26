package org.evolution.features.portal;

import org.bukkit.configuration.file.FileConfiguration;
import org.evolution.coreparts.EvolutionPlugin;

record ShapedPortalConfig(
        boolean enabled,
        int minimumInteriorBlocks,
        int maximumInteriorBlocks,
        int maximumWidth,
        int maximumHeight,
        boolean debugEnabled
) {
    static ShapedPortalConfig load(EvolutionPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new ShapedPortalConfig(
                config.getBoolean("shaped-portals.enabled", true),
                Math.max(6, config.getInt(
                        "shaped-portals.minimum-interior-blocks", 6)),
                Math.max(6, config.getInt(
                        "shaped-portals.maximum-interior-blocks", 400)),
                Math.max(2, config.getInt(
                        "shaped-portals.maximum-width", 21)),
                Math.max(3, config.getInt(
                        "shaped-portals.maximum-height", 21)),
                config.getBoolean("shaped-portals.debug", true)
        );
    }

    String summary() {
        return "enabled=" + enabled
                + ", interior=" + minimumInteriorBlocks + "-"
                + maximumInteriorBlocks
                + ", size=" + maximumWidth + "x" + maximumHeight
                + ", debug=" + debugEnabled;
    }
}
