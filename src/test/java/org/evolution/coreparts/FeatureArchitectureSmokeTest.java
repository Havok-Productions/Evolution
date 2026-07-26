package org.evolution.coreparts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class FeatureArchitectureSmokeTest {
    private static final List<String> FEATURE_PACKAGES = List.of(
            "ecology",
            "meadow",
            "nether",
            "portal",
            "puddles",
            "regrowth",
            "treeevolution",
            "waves",
            "wind"
    );

    private FeatureArchitectureSmokeTest() {
    }

    public static void main(String[] args) throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        Path evolutionRoot = sourceRoot.resolve("org/evolution");
        require(Files.isDirectory(evolutionRoot.resolve("api")),
                "Public API package is missing.");
        require(Files.isDirectory(evolutionRoot.resolve("coreparts")),
                "Shared coreparts package is missing.");
        require(Files.isDirectory(evolutionRoot.resolve("features")),
                "Feature package root is missing.");
        require(!Files.exists(sourceRoot.resolve("org/slowtrees")),
                "Legacy org.slowtrees source package still exists.");

        for (String feature : FEATURE_PACKAGES) {
            require(Files.isDirectory(evolutionRoot.resolve("features").resolve(feature)),
                    "Feature package is missing: " + feature);
            require(!Files.exists(evolutionRoot.resolve(feature)),
                    "Legacy top-level feature package still exists: " + feature);
        }
        require(!Files.exists(evolutionRoot.resolve("core")),
                "Legacy core package still exists.");

        verifyPackageDeclarations(sourceRoot);
        verifyFeatureIsolation(evolutionRoot.resolve("features"));

        String pluginYaml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        require(pluginYaml.contains("main: org.evolution.coreparts.EvolutionPlugin"),
                "plugin.yml does not point to the coreparts bootstrap class.");

        System.out.println("Feature architecture smoke test passed: "
                + FEATURE_PACKAGES.size() + " isolated feature packages.");
    }

    private static void verifyPackageDeclarations(Path sourceRoot) throws IOException {
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relativeParent = sourceRoot.relativize(file.getParent())
                        .toString()
                        .replace('\\', '.')
                        .replace('/', '.');
                String expected = "package " + relativeParent + ";";
                String declaration = Files.readAllLines(file).stream()
                        .filter(line -> line.startsWith("package "))
                        .findFirst()
                        .orElse("");
                require(expected.equals(declaration),
                        file + " declares " + declaration + " instead of " + expected);
            }
        }
    }

    private static void verifyFeatureIsolation(Path featureRoot) throws IOException {
        for (String feature : FEATURE_PACKAGES) {
            Path packageRoot = featureRoot.resolve(feature);
            String ownPrefix = "import org.evolution.features." + feature + ".";
            try (Stream<Path> files = Files.walk(packageRoot)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    for (String line : Files.readAllLines(file)) {
                        if (!line.startsWith("import org.evolution.features.")) {
                            continue;
                        }
                        require(line.startsWith(ownPrefix),
                                file + " directly imports another feature: " + line);
                    }
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
