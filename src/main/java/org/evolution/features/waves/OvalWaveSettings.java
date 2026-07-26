package org.evolution.features.waves;

import org.bukkit.configuration.ConfigurationSection;

record OvalWaveSettings(
        double laneSpacingScale,
        double travelSpacingScale,
        double widthMinScale,
        double widthMaxScale,
        double lengthMinScale,
        double lengthMaxScale,
        double lifecycleSeconds,
        double mergeSoftness,
        double shoreCompression,
        double shoreBoost,
        double shallowWaterBoost,
        double edgeHeight,
        double shoulderHeight,
        double innerHeight,
        double crestHeight,
        double heightStep
) {
    static OvalWaveSettings defaults() {
        return new OvalWaveSettings(0.80D, 1.25D, 0.16D, 0.78D, 0.20D, 0.98D,
                22.0D, 1.00D, 0.22D, 0.28D, 0.12D,
                0.20D, 0.50D, 0.80D, 1.80D, 0.20D);
    }

    static OvalWaveSettings load(ConfigurationSection section) {
        OvalWaveSettings fallback = defaults();
        if (section == null) {
            return fallback;
        }
        return new OvalWaveSettings(
                positive(section.getDouble("lane-spacing-scale", fallback.laneSpacingScale()), fallback.laneSpacingScale()),
                positive(section.getDouble("travel-spacing-scale", fallback.travelSpacingScale()), fallback.travelSpacingScale()),
                positive(section.getDouble("width-min-scale", fallback.widthMinScale()), fallback.widthMinScale()),
                positive(section.getDouble("width-max-scale", fallback.widthMaxScale()), fallback.widthMaxScale()),
                positive(section.getDouble("length-min-scale", fallback.lengthMinScale()), fallback.lengthMinScale()),
                positive(section.getDouble("length-max-scale", fallback.lengthMaxScale()), fallback.lengthMaxScale()),
                positive(section.getDouble("lifecycle-seconds", fallback.lifecycleSeconds()), fallback.lifecycleSeconds()),
                clamp(section.getDouble("merge-softness", fallback.mergeSoftness()), 0.10D, 1.0D),
                clamp(section.getDouble("shore-compression", fallback.shoreCompression()), 0.0D, 0.75D),
                clamp(section.getDouble("shore-boost", fallback.shoreBoost()), 0.0D, 1.0D),
                clamp(section.getDouble("shallow-water-boost", fallback.shallowWaterBoost()), 0.0D, 1.0D),
                clamp(section.getDouble("height-bands.edge", fallback.edgeHeight()), 0.10D, 2.0D),
                clamp(section.getDouble("height-bands.shoulder", fallback.shoulderHeight()), 0.10D, 2.0D),
                clamp(section.getDouble("height-bands.inner", fallback.innerHeight()), 0.10D, 2.0D),
                clamp(section.getDouble("height-bands.crest", fallback.crestHeight()), 0.10D, 2.0D),
                clamp(section.getDouble("height-bands.step", fallback.heightStep()), 0.05D, 0.50D)
        ).normalized();
    }

    private OvalWaveSettings normalized() {
        return new OvalWaveSettings(laneSpacingScale, travelSpacingScale,
                Math.min(widthMinScale, widthMaxScale), Math.max(widthMinScale, widthMaxScale),
                Math.min(lengthMinScale, lengthMaxScale), Math.max(lengthMinScale, lengthMaxScale),
                lifecycleSeconds, mergeSoftness, shoreCompression, shoreBoost, shallowWaterBoost,
                Math.min(edgeHeight, shoulderHeight),
                Math.max(edgeHeight, Math.min(shoulderHeight, innerHeight)),
                Math.max(shoulderHeight, Math.min(innerHeight, crestHeight)),
                Math.max(innerHeight, crestHeight), heightStep);
    }

    double heightForLayer(int layer) {
        return switch (layer) {
            case 1 -> edgeHeight;
            case 2 -> shoulderHeight;
            case 3 -> innerHeight;
            case 4 -> crestHeight;
            default -> 0.0D;
        };
    }

    int layerForHeight(double height) {
        if (height <= 0.01D) {
            return 0;
        }
        if (height <= edgeHeight + 0.01D) {
            return 1;
        }
        if (height <= shoulderHeight + 0.01D) {
            return 2;
        }
        if (height <= innerHeight + 0.01D) {
            return 3;
        }
        return 4;
    }

    double heightForStrength(double strength) {
        double normalized = Math.max(0.0D, Math.min(1.0D, strength));
        double height;
        if (normalized <= 0.35D) {
            height = edgeHeight;
        } else if (normalized <= 0.55D) {
            height = lerp(edgeHeight, shoulderHeight, (normalized - 0.35D) / 0.20D);
        } else if (normalized <= 0.72D) {
            height = lerp(shoulderHeight, innerHeight, (normalized - 0.55D) / 0.17D);
        } else {
            height = lerp(innerHeight, crestHeight, (normalized - 0.72D) / 0.28D);
        }
        // ## Packet water already provides eighth-block visual levels. Capping the
        // extra terrace quantization at 0.10 prevents visible vertical snapping.
        double effectiveStep = Math.min(heightStep, 0.10D);
        double stepped = Math.round(height / effectiveStep) * effectiveStep;
        return Math.max(edgeHeight, Math.min(crestHeight, stepped));
    }

    String summary() {
        return "oval-lane=" + laneSpacingScale + ", oval-travel=" + travelSpacingScale
                + ", oval-width=" + widthMinScale + ".." + widthMaxScale
                + ", oval-length=" + lengthMinScale + ".." + lengthMaxScale
                + ", lifecycle-seconds=" + lifecycleSeconds + ", merge=" + mergeSoftness
                + ", heights=" + edgeHeight + "/" + shoulderHeight + "/" + innerHeight + "/" + crestHeight
                + ", height-step=" + heightStep;
    }

    private double lerp(double start, double end, double amount) {
        return start + ((end - start) * Math.max(0.0D, Math.min(1.0D, amount)));
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    private static double clamp(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
}
