package org.evolution.features.ecology;

import java.util.Locale;
import org.bukkit.block.Biome;

enum BiomeEcologyPath {
    TEMPERATE("plains -> meadow -> shrub meadow -> young woodland -> mixed forest"),
    BIRCH("birch forest -> tall birch grove -> mature birch woodland -> old birch forest"),
    DARK_WOODLAND("dark forest -> dense dark forest -> ancient dark woodland"),
    CHERRY("cherry grove -> dense cherry grove -> old cherry grove"),
    TROPICAL("jungle -> dense jungle -> ancient jungle"),
    COLD_CONIFER("taiga -> dense spruce taiga -> old-growth taiga"),
    WETLAND("swamp/river -> wet bank -> wet woodland -> swamp thicket"),
    DRY("desert/savanna/badlands -> dry scrub pockets -> hardy woodland pockets"),
    COASTAL("beach/shore -> dune grass -> coastal scrub -> sparse coastal grove"),
    ALPINE("windswept/grove/peaks -> sparse alpine woodland -> rugged old-growth pockets"),
    FUNGAL("mushroom fields -> richer mycelium colony -> giant mushroom grove");

    private final String progressionNote;

    BiomeEcologyPath(String progressionNote) {
        this.progressionNote = progressionNote;
    }

    String progressionNote() {
        return progressionNote;
    }

    static BiomeEcologyPath from(Biome biome) {
        String key = biome.getKey().getKey().toUpperCase(Locale.ROOT);
        if (key.contains("MUSHROOM")) {
            return FUNGAL;
        }
        if (key.contains("JUNGLE") || key.contains("BAMBOO")) {
            return TROPICAL;
        }
        if (key.contains("MANGROVE") || key.contains("SWAMP") || key.contains("RIVER")) {
            return WETLAND;
        }
        if (key.contains("TAIGA") || key.contains("SNOWY_TAIGA") || key.contains("OLD_GROWTH_PINE") || key.contains("OLD_GROWTH_SPRUCE")) {
            return COLD_CONIFER;
        }
        if (key.contains("DESERT") || key.contains("SAVANNA") || key.contains("BADLANDS")) {
            return DRY;
        }
        if (key.contains("BEACH") || key.contains("SHORE") || key.contains("OCEAN")) {
            return COASTAL;
        }
        if (key.contains("WINDSWEPT") || key.contains("GROVE") || key.contains("PEAK") || key.contains("SLOPE")) {
            return ALPINE;
        }
        if (key.contains("DARK_FOREST")) {
            return DARK_WOODLAND;
        }
        if (key.contains("BIRCH")) {
            return BIRCH;
        }
        if (key.contains("CHERRY")) {
            return CHERRY;
        }
        return TEMPERATE;
    }
}
