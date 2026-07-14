package org.slowtrees.core;

import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

public final class RuntimeProfile {
    private RuntimeProfile() {
    }

    public static boolean testingEnabled(FileConfiguration config) {
        String profile = config.getString("runtime-profile", "testing").toLowerCase(Locale.ROOT);
        if (profile.equals("survival") || profile.equals("production")) {
            return false;
        }
        if (profile.equals("testing") || profile.equals("test")) {
            return true;
        }
        return config.getBoolean("testing-mode.enabled", true);
    }

    public static String name(FileConfiguration config) {
        return testingEnabled(config) ? "testing" : "survival";
    }
}
