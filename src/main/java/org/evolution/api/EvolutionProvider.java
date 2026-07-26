package org.evolution.api;

import java.util.Optional;

public final class EvolutionProvider {
    private static volatile EvolutionApi api;

    private EvolutionProvider() {
    }

    public static EvolutionApi get() {
        EvolutionApi current = api;
        if (current == null) {
            throw new IllegalStateException("Evolution API is not available.");
        }
        return current;
    }

    public static Optional<EvolutionApi> current() {
        return Optional.ofNullable(api);
    }

    public static boolean available() {
        return api != null;
    }

    public static void register(EvolutionApi api) {
        EvolutionProvider.api = api;
    }

    public static void unregister(EvolutionApi api) {
        if (EvolutionProvider.api == api) {
            EvolutionProvider.api = null;
        }
    }
}
