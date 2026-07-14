package org.slowtrees.treeevolution;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slowtrees.core.SlowTreesPlugin;

final class StructurePatternScanner {
    StructureScanResult scanAll(SlowTreesPlugin plugin) {
        File folder = scanFolder(plugin);
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create structure-scan folder.");
            return StructureScanResult.empty();
        }

        File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return isStructureFile(lower) || lower.endsWith(".zip") || lower.endsWith(".jar");
        });
        if (files == null || files.length == 0) {
            save(plugin, List.of(), List.of(), "No .nbt, .schem, .schematic, .zip, or .jar files found in structure-scan.");
            return StructureScanResult.empty();
        }

        List<StructureScanSummary> summaries = new ArrayList<>();
        List<WorldgenScanSummary> worldgenSummaries = new ArrayList<>();
        for (File file : files) {
            try {
                String lower = file.getName().toLowerCase(Locale.ROOT);
                if (isStructureFile(lower)) {
                    summaries.add(scan(file));
                } else if (lower.endsWith(".zip") || lower.endsWith(".jar")) {
                    scanArchive(file, summaries, worldgenSummaries);
                }
            } catch (RuntimeException | IOException ex) {
                plugin.getLogger().warning("Could not scan structure " + file.getName() + ": " + ex.getMessage());
            }
        }
        save(plugin, summaries, worldgenSummaries, "Scanned structures and archive worldgen files for statistical pattern inspiration only.");
        return new StructureScanResult(List.copyOf(summaries), List.copyOf(worldgenSummaries));
    }

    File scanFolder(SlowTreesPlugin plugin) {
        return new File(plugin.getDataFolder(), "structure-scan");
    }

    @SuppressWarnings("unchecked")
    private StructureScanSummary scan(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            return scanNbt(file.getName(), input.readAllBytes());
        }
    }

    private void scanArchive(File file, List<StructureScanSummary> summaries, List<WorldgenScanSummary> worldgenSummaries) throws IOException {
        int jsonFiles = 0;
        int nbtFiles = 0;
        int biomeFiles = 0;
        int configuredFeatureFiles = 0;
        int placedFeatureFiles = 0;
        int treeFeatureFiles = 0;
        int vegetationFeatureFiles = 0;
        int terrainFeatureFiles = 0;
        Map<String, Integer> speciesSignals = new LinkedHashMap<>();
        Map<String, Integer> materialSignals = new LinkedHashMap<>();
        List<WorldgenProfileSuggestion> profileSuggestions = new ArrayList<>();

        try (ZipFile zip = new ZipFile(file)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (isStructureFile(name)) {
                    nbtFiles++;
                    try (InputStream input = zip.getInputStream(entry)) {
                        summaries.add(scanNbt(file.getName() + "!" + entry.getName(), input.readAllBytes()));
                    }
                    continue;
                }
                if (!name.endsWith(".json")) {
                    continue;
                }

                jsonFiles++;
                if (name.contains("/worldgen/biome/")) {
                    biomeFiles++;
                }
                if (name.contains("/worldgen/configured_feature/")) {
                    configuredFeatureFiles++;
                }
                if (name.contains("/worldgen/placed_feature/")) {
                    placedFeatureFiles++;
                }
                if (looksLikeTreeFeature(name)) {
                    treeFeatureFiles++;
                }
                if (looksLikeVegetationFeature(name)) {
                    vegetationFeatureFiles++;
                }
                if (looksLikeTerrainFeature(name)) {
                    terrainFeatureFiles++;
                }

                if (isLikelyWorldgenJson(name)) {
                    String content = readSmallText(zip, entry);
                    recordSignals(name + "\n" + content.toLowerCase(Locale.ROOT), speciesSignals, materialSignals);
                    extractProfileSuggestion(name, content).ifPresent(profileSuggestions::add);
                }
            }
        }

        worldgenSummaries.add(new WorldgenScanSummary(
                file.getName(),
                jsonFiles,
                nbtFiles,
                biomeFiles,
                configuredFeatureFiles,
                placedFeatureFiles,
                treeFeatureFiles,
                vegetationFeatureFiles,
                terrainFeatureFiles,
                speciesSignals,
                materialSignals,
                profileSuggestions
        ));
    }

    @SuppressWarnings("unchecked")
    private StructureScanSummary scanNbt(String sourceName, byte[] source) throws IOException {
        Map<String, Object> root;
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(source)))) {
            root = (Map<String, Object>) readNamedPayload(input);
        } catch (IOException gzipFailure) {
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(source))) {
                root = (Map<String, Object>) readNamedPayload(input);
            }
        }

        ParsedStructure parsed = parseStructure(root);
        List<ScanBlock> blocks = parsed.blocks();

        Bounds bounds = new Bounds();
        Map<String, Integer> materialCounts = new LinkedHashMap<>();
        int logs = 0;
        int leaves = 0;
        int vines = 0;
        int rootLike = 0;
        int canopyBlocks = 0;
        int sideLogBlocks = 0;
        int minLogY = Integer.MAX_VALUE;
        int branchStartY = Integer.MAX_VALUE;
        for (ScanBlock block : blocks) {
            bounds.include(block.x(), block.y(), block.z());
            String material = block.material();
            materialCounts.merge(material, 1, Integer::sum);
            if (isLog(material)) {
                logs++;
                minLogY = Math.min(minLogY, block.y());
                if (Math.abs(block.x()) > 1 || Math.abs(block.z()) > 1) {
                    sideLogBlocks++;
                    branchStartY = Math.min(branchStartY, block.y());
                }
                if (block.y() <= minLogY + 1 && (Math.abs(block.x()) > 0 || Math.abs(block.z()) > 0)) {
                    rootLike++;
                }
            } else if (isLeaf(material)) {
                leaves++;
                canopyBlocks++;
            } else if (material.endsWith("vine")) {
                vines++;
            }
        }

        int widthX = bounds.widthX();
        int height = bounds.height();
        int widthZ = bounds.widthZ();
        int canopyRadius = Math.max(widthX, widthZ) / 2;
        double canopyDensity = canopyRadius <= 0 ? 0.0D : Math.min(1.0D, canopyBlocks / Math.max(1.0D, widthX * Math.max(1, widthZ) * Math.max(1, height / 3.0D)));
        int branchEstimate = Math.max(0, Math.min(12, sideLogBlocks / 3));
        double avgBranchLength = branchEstimate == 0 ? 0.0D : sideLogBlocks / (double) branchEstimate;
        return new StructureScanSummary(
                sourceName,
                parsed.format(),
                speciesGuess(materialCounts),
                blocks.size(),
                logs,
                leaves,
                vines,
                rootLike,
                widthX,
                height,
                widthZ,
                branchEstimate,
                avgBranchLength,
                branchStartY == Integer.MAX_VALUE ? -1 : branchStartY - bounds.minY,
                canopyRadius,
                canopyDensity,
                blocks.isEmpty() ? 0.0D : vines / (double) blocks.size(),
                generatedProfile(height, branchEstimate, avgBranchLength, branchStartY == Integer.MAX_VALUE ? -1 : branchStartY - bounds.minY, canopyRadius, canopyDensity, logs, leaves, vines, rootLike),
                materialCounts
        );
    }

    private void save(SlowTreesPlugin plugin, List<StructureScanSummary> summaries, List<WorldgenScanSummary> worldgenSummaries, String notes) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("notes", "## " + notes + " Exact structure layouts and source JSON are not copied or stored as generated output.");
        yaml.set("scanned-counts.structures", summaries.size());
        yaml.set("scanned-counts.worldgen-archives", worldgenSummaries.size());
        int index = 0;
        for (StructureScanSummary summary : summaries) {
            String path = "structures." + index++;
            yaml.set(path + ".file", summary.fileName());
            yaml.set(path + ".format", summary.format());
            yaml.set(path + ".species-guess", summary.speciesGuess());
            yaml.set(path + ".blocks.total", summary.blockCount());
            yaml.set(path + ".blocks.logs", summary.logBlocks());
            yaml.set(path + ".blocks.leaves", summary.leafBlocks());
            yaml.set(path + ".blocks.vines", summary.vineBlocks());
            yaml.set(path + ".blocks.root-like", summary.rootLikeBlocks());
            yaml.set(path + ".size.x", summary.widthX());
            yaml.set(path + ".size.y", summary.height());
            yaml.set(path + ".size.z", summary.widthZ());
            yaml.set(path + ".estimates.branch-count", summary.branchCountEstimate());
            yaml.set(path + ".estimates.average-branch-length", summary.averageBranchLengthEstimate());
            yaml.set(path + ".estimates.branch-start-height", summary.branchStartHeightEstimate());
            yaml.set(path + ".estimates.canopy-radius", summary.canopyRadiusEstimate());
            yaml.set(path + ".estimates.canopy-density", summary.canopyDensityEstimate());
            yaml.set(path + ".estimates.vine-frequency", summary.vineFrequency());
            yaml.set(path + ".generated-profile", summary.generatedProfile());
            yaml.set(path + ".material-counts", summary.materialCounts());
        }
        int archiveIndex = 0;
        for (WorldgenScanSummary summary : worldgenSummaries) {
            String path = "worldgen-archives." + archiveIndex++;
            yaml.set(path + ".archive", summary.archiveName());
            yaml.set(path + ".files.json", summary.jsonFiles());
            yaml.set(path + ".files.nbt-or-schematic", summary.nbtFiles());
            yaml.set(path + ".files.biomes", summary.biomeFiles());
            yaml.set(path + ".files.configured-features", summary.configuredFeatureFiles());
            yaml.set(path + ".files.placed-features", summary.placedFeatureFiles());
            yaml.set(path + ".signals.tree-features", summary.treeFeatureFiles());
            yaml.set(path + ".signals.vegetation-features", summary.vegetationFeatureFiles());
            yaml.set(path + ".signals.terrain-features", summary.terrainFeatureFiles());
            yaml.set(path + ".signals.species", summary.speciesSignals());
            yaml.set(path + ".signals.materials", summary.materialSignals());
            int suggestionIndex = 0;
            for (WorldgenProfileSuggestion suggestion : summary.profileSuggestions()) {
                String suggestionPath = path + ".profile-suggestions." + suggestionIndex++;
                yaml.set(suggestionPath + ".source", suggestion.sourceFile());
                yaml.set(suggestionPath + ".species", suggestion.species());
                yaml.set(suggestionPath + ".feature-type", suggestion.featureType());
                yaml.set(suggestionPath + ".trunk-placer", suggestion.trunkPlacer());
                yaml.set(suggestionPath + ".foliage-placer", suggestion.foliagePlacer());
                yaml.set(suggestionPath + ".decorators", suggestion.decorators());
                yaml.set(suggestionPath + ".generated-profile", suggestion.generatedProfile());
            }
        }

        File file = new File(plugin.getDataFolder(), "structure-scan-debug.yml");
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save structure-scan-debug.yml: " + ex.getMessage());
        }
    }

    private Object readNamedPayload(DataInputStream input) throws IOException {
        int type = input.readUnsignedByte();
        if (type == 0) {
            return Map.of();
        }
        readString(input);
        return readPayload(input, type);
    }

    private Object readPayload(DataInputStream input, int type) throws IOException {
        return switch (type) {
            case 1 -> input.readByte();
            case 2 -> input.readShort();
            case 3 -> input.readInt();
            case 4 -> input.readLong();
            case 5 -> input.readFloat();
            case 6 -> input.readDouble();
            case 7 -> readByteArray(input);
            case 8 -> readString(input);
            case 9 -> readList(input);
            case 10 -> readCompound(input);
            case 11 -> readIntArray(input);
            case 12 -> readLongArray(input);
            default -> throw new IOException("Unsupported NBT tag " + type);
        };
    }

    private List<Object> readList(DataInputStream input) throws IOException {
        int childType = input.readUnsignedByte();
        int size = input.readInt();
        List<Object> values = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            values.add(readPayload(input, childType));
        }
        return values;
    }

    private Map<String, Object> readCompound(DataInputStream input) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        while (true) {
            int type = input.readUnsignedByte();
            if (type == 0) {
                return values;
            }
            String name = readString(input);
            values.put(name, readPayload(input, type));
        }
    }

    private byte[] readByteArray(DataInputStream input) throws IOException {
        int size = input.readInt();
        byte[] values = new byte[size];
        input.readFully(values);
        return values;
    }

    private List<Integer> readIntArray(DataInputStream input) throws IOException {
        int size = input.readInt();
        List<Integer> values = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            values.add(input.readInt());
        }
        return values;
    }

    private List<Long> readLongArray(DataInputStream input) throws IOException {
        int size = input.readInt();
        List<Long> values = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            values.add(input.readLong());
        }
        return values;
    }

    private String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String readSmallText(ZipFile zip, ZipEntry entry) throws IOException {
        long size = entry.getSize();
        if (size > 128_000L) {
            return "";
        }
        try (InputStream input = zip.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Optional<WorldgenProfileSuggestion> extractProfileSuggestion(String sourceName, String content) {
        if (content == null || content.isBlank() || !sourceName.contains("/worldgen/configured_feature/")) {
            return Optional.empty();
        }
        String lower = (sourceName + "\n" + content).toLowerCase(Locale.ROOT);
        if (!looksLikeTreeFeature(lower) && !lower.contains("trunk_placer") && !lower.contains("foliage_placer")) {
            return Optional.empty();
        }

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(content);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
        if (!parsed.isJsonObject()) {
            return Optional.empty();
        }

        JsonObject root = parsed.getAsJsonObject();
        String featureType = stringValue(root.get("type"), "unknown");
        JsonObject trunkPlacer = findObjectByKey(root, "trunk_placer").orElse(null);
        JsonObject foliagePlacer = findObjectByKey(root, "foliage_placer").orElse(null);
        if (trunkPlacer == null && foliagePlacer == null && !featureType.contains("tree")) {
            return Optional.empty();
        }

        String trunkType = trunkPlacer == null ? "unknown" : stringValue(trunkPlacer.get("type"), "unknown");
        String foliageType = foliagePlacer == null ? "unknown" : stringValue(foliagePlacer.get("type"), "unknown");
        int baseHeight = trunkPlacer == null ? 5 : intProvider(trunkPlacer.get("base_height"), 5);
        int heightRandA = trunkPlacer == null ? 2 : intProvider(trunkPlacer.get("height_rand_a"), 0);
        int heightRandB = trunkPlacer == null ? 1 : intProvider(trunkPlacer.get("height_rand_b"), 0);
        int extraBranchHeight = trunkType.contains("fancy") || trunkType.contains("forking") || trunkType.contains("bending") ? 2 : 0;
        int targetHeightMin = Math.max(3, baseHeight);
        int targetHeightMax = Math.max(targetHeightMin + 1, baseHeight + heightRandA + heightRandB + extraBranchHeight);

        int canopyRadius = Math.max(1, foliagePlacer == null ? 2 : intProvider(foliagePlacer.get("radius"), 2));
        int foliageHeight = Math.max(1, foliagePlacer == null ? 2 : intProvider(foliagePlacer.get("height"), 2));
        int branchBase = branchBaseFor(sourceName, trunkType, foliageType);
        int branchesMin = Math.max(0, branchBase - 1);
        int branchesMax = Math.max(branchesMin, branchBase + Math.max(1, foliageHeight / 2));
        int branchLengthMin = Math.max(1, canopyRadius);
        int branchLengthMax = Math.max(branchLengthMin, canopyRadius + 2 + (trunkType.contains("fancy") ? 1 : 0));

        Map<String, Integer> decorators = collectDecorators(root);
        boolean hasVines = decorators.keySet().stream().anyMatch(key -> key.contains("vine")) || lower.contains("vine");
        boolean hasRoots = decorators.keySet().stream().anyMatch(key -> key.contains("root")) || lower.contains("root");
        boolean hasGroundDetail = lower.contains("moss") || lower.contains("mushroom") || lower.contains("flower") || lower.contains("grass") || lower.contains("fern");
        double canopyDensity = canopyDensityFor(foliageType, sourceName);
        double vineChance = hasVines ? 0.24D : sourceName.contains("jungle") || sourceName.contains("mangrove") ? 0.12D : 0.02D;
        double rootChance = hasRoots ? 0.34D : sourceName.contains("mangrove") ? 0.42D : 0.12D;
        double groundDetailChance = hasGroundDetail ? 0.28D : 0.16D;
        SpeciesGuess speciesGuess = speciesGuessForWorldgen(sourceName, root);
        String species = speciesGuess.species();

        Map<String, Object> generatedProfile = new LinkedHashMap<>();
        generatedProfile.put("target-height-min", targetHeightMin);
        generatedProfile.put("target-height-max", targetHeightMax);
        generatedProfile.put("branches-min", branchesMin);
        generatedProfile.put("branches-max", branchesMax);
        generatedProfile.put("branch-length-min", branchLengthMin);
        generatedProfile.put("branch-length-max", branchLengthMax);
        generatedProfile.put("canopy-radius", canopyRadius);
        generatedProfile.put("canopy-density", round(canopyDensity));
        generatedProfile.put("root-chance", round(rootChance));
        generatedProfile.put("vine-chance", round(vineChance));
        generatedProfile.put("ground-detail-chance", round(groundDetailChance));
        generatedProfile.put("species-source", speciesGuess.source());
        if (!speciesGuess.materialSignals().isEmpty()) {
            generatedProfile.put("species-material-signals", speciesGuess.materialSignals());
        }
        generatedProfile.put("notes", "## Generated from worldgen JSON settings only. Source JSON is not copied.");

        return Optional.of(new WorldgenProfileSuggestion(
                sourceName,
                species,
                featureType,
                trunkType,
                foliageType,
                targetHeightMin,
                targetHeightMax,
                branchesMin,
                branchesMax,
                branchLengthMin,
                branchLengthMax,
                canopyRadius,
                canopyDensity,
                rootChance,
                vineChance,
                groundDetailChance,
                decorators,
                generatedProfile
        ));
    }

    private Optional<JsonObject> findObjectByKey(JsonElement element, String key) {
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement direct = object.get(key);
            if (direct != null && direct.isJsonObject()) {
                return Optional.of(direct.getAsJsonObject());
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                Optional<JsonObject> found = findObjectByKey(entry.getValue(), key);
                if (found.isPresent()) {
                    return found;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                Optional<JsonObject> found = findObjectByKey(child, key);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private Map<String, Integer> collectDecorators(JsonElement root) {
        Map<String, Integer> decorators = new LinkedHashMap<>();
        collectDecoratorTypes(root, decorators);
        return decorators;
    }

    private void collectDecoratorTypes(JsonElement element, Map<String, Integer> decorators) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String type = stringValue(object.get("type"), "");
            if (!type.isBlank() && (type.contains("vine") || type.contains("decorator") || type.contains("beehive") || type.contains("root"))) {
                decorators.merge(type, 1, Integer::sum);
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                collectDecoratorTypes(entry.getValue(), decorators);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectDecoratorTypes(child, decorators);
            }
        }
    }

    private int intProvider(JsonElement element, int fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsInt();
            }
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                for (String key : List.of("value", "base", "min_inclusive", "max_inclusive", "min", "max", "absolute")) {
                    JsonElement child = object.get(key);
                    if (child != null) {
                        return intProvider(child, fallback);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return fallback;
        }
        return fallback;
    }

    private String stringValue(JsonElement element, String fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsString().toLowerCase(Locale.ROOT);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private int branchBaseFor(String sourceName, String trunkType, String foliageType) {
        String text = (sourceName + " " + trunkType + " " + foliageType).toLowerCase(Locale.ROOT);
        if (text.contains("fancy") || text.contains("dark_oak")) {
            return 5;
        }
        if (text.contains("forking") || text.contains("bending") || text.contains("cherry")) {
            return 4;
        }
        if (text.contains("spruce") || text.contains("pine")) {
            return 6;
        }
        if (text.contains("jungle") || text.contains("mangrove")) {
            return 5;
        }
        if (text.contains("birch")) {
            return 2;
        }
        return 3;
    }

    private double canopyDensityFor(String foliageType, String sourceName) {
        String text = (sourceName + " " + foliageType).toLowerCase(Locale.ROOT);
        if (text.contains("fancy") || text.contains("cherry")) {
            return 0.78D;
        }
        if (text.contains("spruce") || text.contains("pine")) {
            return 0.60D;
        }
        if (text.contains("acacia")) {
            return 0.54D;
        }
        if (text.contains("jungle") || text.contains("mangrove")) {
            return 0.72D;
        }
        if (text.contains("birch")) {
            return 0.58D;
        }
        return 0.70D;
    }

    private SpeciesGuess speciesGuessForWorldgen(String sourceName, JsonObject root) {
        String pathGuess = speciesGuessFromPath(sourceName);
        Map<String, Integer> materialSignals = new LinkedHashMap<>();
        collectSpeciesMaterials(root, materialSignals);
        String materialGuess = strongestSpecies(materialSignals);
        if (!pathGuess.equals("unknown")) {
            String source = materialGuess.equals("unknown") || materialGuess.equals(pathGuess)
                    ? "path"
                    : "path-over-material-" + materialGuess;
            return new SpeciesGuess(pathGuess, source, materialSignals);
        }
        if (!materialGuess.equals("unknown")) {
            return new SpeciesGuess(materialGuess, "material", materialSignals);
        }
        return new SpeciesGuess(speciesGuessFromText(sourceName.toLowerCase(Locale.ROOT)), "fallback-text", materialSignals);
    }

    private String speciesGuessFromPath(String sourceName) {
        String normalized = sourceName.toLowerCase(Locale.ROOT)
                .replace('\\', '/')
                .replace(".json", "")
                .replace("fancy_oak", "oak")
                .replace("dark-oak", "dark_oak");
        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            for (String token : segment.split("[^a-z0-9_]+")) {
                if (!token.isBlank()) {
                    segments.add(token);
                }
            }
        }

        for (String species : List.of("dark_oak", "mangrove", "cherry", "jungle", "acacia", "spruce", "birch", "azalea", "oak")) {
            for (String segment : segments) {
                if (segment.equals(species) || segment.startsWith(species + "_") || segment.endsWith("_" + species) || segment.contains("_" + species + "_")) {
                    return species;
                }
            }
        }
        return "unknown";
    }

    private void collectSpeciesMaterials(JsonElement element, Map<String, Integer> materialSignals) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().toLowerCase(Locale.ROOT);
            if (value.startsWith("minecraft:")) {
                addMaterialSpeciesSignal(value, materialSignals);
            }
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                collectSpeciesMaterials(entry.getValue(), materialSignals);
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectSpeciesMaterials(child, materialSignals);
            }
        }
    }

    private void addMaterialSpeciesSignal(String value, Map<String, Integer> materialSignals) {
        for (String species : List.of("dark_oak", "mangrove", "cherry", "jungle", "acacia", "spruce", "birch", "azalea", "oak")) {
            if (!value.contains(species)) {
                continue;
            }
            int weight = 1;
            if (value.endsWith("_log") || value.endsWith("_wood") || value.endsWith("_stem") || value.endsWith("_hyphae")) {
                weight = 8;
            } else if (value.endsWith("_leaves")) {
                weight = 6;
            } else if (value.endsWith("_sapling") || value.endsWith("_propagule")) {
                weight = 4;
            }
            materialSignals.merge(species, weight, Integer::sum);
        }
    }

    private String strongestSpecies(Map<String, Integer> materialSignals) {
        return materialSignals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }

    private String speciesGuessFromText(String text) {
        for (String species : List.of("dark_oak", "fancy_oak", "mangrove", "cherry", "jungle", "acacia", "spruce", "birch", "azalea", "oak")) {
            if (text.contains(species)) {
                return species.equals("fancy_oak") ? "oak" : species;
            }
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    private ParsedStructure parseStructure(Map<String, Object> root) throws IOException {
        if (root.containsKey("palette") && root.containsKey("blocks")) {
            List<Object> palette = (List<Object>) root.getOrDefault("palette", List.of());
            List<Object> rawBlocks = (List<Object>) root.getOrDefault("blocks", List.of());
            Map<Integer, String> paletteNames = new HashMap<>();
            for (int index = 0; index < palette.size(); index++) {
                Map<String, Object> entry = (Map<String, Object>) palette.get(index);
                Object name = entry.get("Name");
                paletteNames.put(index, normalizeMaterialName(name == null ? "unknown" : name.toString()));
            }

            List<ScanBlock> blocks = new ArrayList<>();
            for (Object rawBlock : rawBlocks) {
                Map<String, Object> block = (Map<String, Object>) rawBlock;
                List<Number> pos = (List<Number>) block.getOrDefault("pos", List.of(0, 0, 0));
                int state = asInt(block.get("state"), 0);
                blocks.add(new ScanBlock(
                        pos.get(0).intValue(),
                        pos.get(1).intValue(),
                        pos.get(2).intValue(),
                        paletteNames.getOrDefault(state, "unknown")
                ));
            }
            return new ParsedStructure("minecraft-structure-nbt", blocks);
        }

        if (root.containsKey("Palette") && root.containsKey("BlockData")) {
            Map<String, Object> rawPalette = (Map<String, Object>) root.get("Palette");
            Map<Integer, String> paletteNames = new HashMap<>();
            for (Map.Entry<String, Object> entry : rawPalette.entrySet()) {
                paletteNames.put(asInt(entry.getValue(), 0), normalizeMaterialName(entry.getKey()));
            }

            int width = asInt(root.get("Width"), 0);
            int height = asInt(root.get("Height"), 0);
            int length = asInt(root.get("Length"), 0);
            byte[] blockData = (byte[]) root.get("BlockData");
            List<Integer> states = decodeVarInts(blockData);
            List<ScanBlock> blocks = new ArrayList<>();
            for (int index = 0; index < states.size() && index < width * height * length; index++) {
                String material = paletteNames.getOrDefault(states.get(index), "unknown");
                if (isAir(material)) {
                    continue;
                }
                int x = index % width;
                int z = (index / width) % length;
                int y = index / (width * Math.max(1, length));
                blocks.add(new ScanBlock(x, y, z, material));
            }
            return new ParsedStructure("sponge-schem", blocks);
        }

        if (root.containsKey("Blocks") && root.containsKey("Width") && root.containsKey("Height") && root.containsKey("Length")) {
            int width = asInt(root.get("Width"), 0);
            int height = asInt(root.get("Height"), 0);
            int length = asInt(root.get("Length"), 0);
            byte[] blockIds = (byte[]) root.get("Blocks");
            List<ScanBlock> blocks = new ArrayList<>();
            for (int index = 0; index < blockIds.length && index < width * height * length; index++) {
                String material = legacyMaterialName(Byte.toUnsignedInt(blockIds[index]));
                if (isAir(material)) {
                    continue;
                }
                int x = index % width;
                int z = (index / width) % length;
                int y = index / (width * Math.max(1, length));
                blocks.add(new ScanBlock(x, y, z, material));
            }
            return new ParsedStructure("legacy-schematic", blocks);
        }

        throw new IOException("Unsupported structure NBT format: expected Minecraft structure .nbt, Sponge .schem, or legacy .schematic.");
    }

    private List<Integer> decodeVarInts(byte[] blockData) throws IOException {
        List<Integer> values = new ArrayList<>();
        int value = 0;
        int shift = 0;
        for (byte raw : blockData) {
            int current = raw & 0xFF;
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                values.add(value);
                value = 0;
                shift = 0;
                continue;
            }
            shift += 7;
            if (shift > 35) {
                throw new IOException("Invalid schematic BlockData varint.");
            }
        }
        return values;
    }

    private Map<String, Object> generatedProfile(
            int height,
            int branchEstimate,
            double averageBranchLength,
            int branchStartHeight,
            int canopyRadius,
            double canopyDensity,
            int logs,
            int leaves,
            int vines,
            int roots
    ) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("target-height-min", Math.max(3, height - 2));
        profile.put("target-height-max", Math.max(4, height + 2));
        profile.put("branches-min", Math.max(0, branchEstimate - 1));
        profile.put("branches-max", Math.max(branchEstimate, branchEstimate + 2));
        profile.put("branch-length-min", Math.max(1, (int) Math.floor(averageBranchLength) - 1));
        profile.put("branch-length-max", Math.max(1, (int) Math.ceil(averageBranchLength) + 1));
        profile.put("branch-start-height", branchStartHeight);
        profile.put("canopy-radius", Math.max(1, canopyRadius));
        profile.put("canopy-density", round(canopyDensity));
        profile.put("root-chance", round(logs == 0 ? 0.0D : Math.min(1.0D, roots / (double) Math.max(1, logs))));
        profile.put("vine-chance", round(leaves == 0 ? 0.0D : Math.min(1.0D, vines / (double) Math.max(1, leaves))));
        profile.put("notes", "## Generated from measurements only. It is safe tuning data, not a copied structure layout.");
        return profile;
    }

    private String speciesGuess(Map<String, Integer> materialCounts) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (String material : materialCounts.keySet()) {
            for (String species : List.of("oak", "birch", "spruce", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "azalea")) {
                if (material.contains(species)) {
                    scores.merge(species, materialCounts.get(material), Integer::sum);
                }
            }
        }
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }

    private String legacyMaterialName(int id) {
        return switch (id) {
            case 0 -> "minecraft:air";
            case 17 -> "minecraft:oak_log";
            case 18 -> "minecraft:oak_leaves";
            case 99, 100 -> "minecraft:mushroom_block";
            case 106 -> "minecraft:vine";
            case 161 -> "minecraft:acacia_leaves";
            case 162 -> "minecraft:acacia_log";
            case 175 -> "minecraft:tall_grass";
            default -> "legacy:" + id;
        };
    }

    private String normalizeMaterialName(String material) {
        String normalized = material.toLowerCase(Locale.ROOT);
        int stateStart = normalized.indexOf('[');
        if (stateStart >= 0) {
            normalized = normalized.substring(0, stateStart);
        }
        return normalized;
    }

    private int asInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }

    private boolean isAir(String material) {
        return material.equals("minecraft:air") || material.equals("air") || material.equals("minecraft:void_air") || material.equals("minecraft:cave_air");
    }

    private void recordSignals(String haystack, Map<String, Integer> speciesSignals, Map<String, Integer> materialSignals) {
        for (String species : List.of("oak", "fancy_oak", "birch", "spruce", "pine", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "azalea")) {
            countSignal(haystack, speciesSignals, species);
        }
        for (String material : List.of("log", "leaves", "vine", "moss", "mushroom", "grass", "fern", "flower", "root", "nylium", "netherrack", "basalt", "blackstone", "soul_sand", "soul_soil")) {
            countSignal(haystack, materialSignals, material);
        }
    }

    private void countSignal(String haystack, Map<String, Integer> signals, String token) {
        int count = 0;
        int index = haystack.indexOf(token);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(token, index + token.length());
        }
        if (count > 0) {
            signals.merge(token, count, Integer::sum);
        }
    }

    private boolean isLikelyWorldgenJson(String name) {
        return name.contains("/worldgen/") || name.contains("/tags/block/") || name.contains("/tags/blocks/");
    }

    private boolean looksLikeTreeFeature(String name) {
        return name.contains("tree") || name.contains("oak") || name.contains("birch")
                || name.contains("spruce") || name.contains("jungle") || name.contains("acacia")
                || name.contains("mangrove") || name.contains("cherry") || name.contains("azalea");
    }

    private boolean looksLikeVegetationFeature(String name) {
        return name.contains("vegetation") || name.contains("patch_") || name.contains("flower")
                || name.contains("grass") || name.contains("fern") || name.contains("mushroom");
    }

    private boolean looksLikeTerrainFeature(String name) {
        return name.contains("terrain") || name.contains("carver") || name.contains("ore")
                || name.contains("lake") || name.contains("river") || name.contains("basalt")
                || name.contains("disk") || name.contains("delta");
    }

    private static boolean isStructureFile(String lowerName) {
        return lowerName.endsWith(".nbt") || lowerName.endsWith(".schem") || lowerName.endsWith(".schematic");
    }

    private boolean isLog(String material) {
        return material.endsWith("_log") || material.endsWith("_wood") || material.endsWith(":mushroom_stem") || material.equals("minecraft:mushroom_block");
    }

    private boolean isLeaf(String material) {
        return material.endsWith("_leaves") || material.endsWith(":azalea_leaves") || material.endsWith(":flowering_azalea_leaves");
    }

    private record ParsedStructure(String format, List<ScanBlock> blocks) {
    }

    private record ScanBlock(int x, int y, int z, String material) {
    }

    private record SpeciesGuess(String species, String source, Map<String, Integer> materialSignals) {
    }

    private static final class Bounds {
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        void include(int x, int y, int z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        int widthX() {
            return maxX < minX ? 0 : maxX - minX + 1;
        }

        int height() {
            return maxY < minY ? 0 : maxY - minY + 1;
        }

        int widthZ() {
            return maxZ < minZ ? 0 : maxZ - minZ + 1;
        }
    }
}
