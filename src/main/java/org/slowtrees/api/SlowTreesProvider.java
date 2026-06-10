package org.slowtrees.api;

import java.util.Optional;

public final class SlowTreesProvider {
    private static volatile SlowTreesApi api;

    private SlowTreesProvider() {
    }

    public static SlowTreesApi get() {
        SlowTreesApi current = api;
        if (current == null) {
            throw new IllegalStateException("SlowTrees API is not available.");
        }
        return current;
    }

    public static Optional<SlowTreesApi> current() {
        return Optional.ofNullable(api);
    }

    public static boolean available() {
        return api != null;
    }

    public static void register(SlowTreesApi api) {
        SlowTreesProvider.api = api;
    }

    public static void unregister(SlowTreesApi api) {
        if (SlowTreesProvider.api == api) {
            SlowTreesProvider.api = null;
        }
    }
}
