package org.slowtrees.waves;

final class WaveModel {
    private static final double SEAM_CANDIDATE_STRENGTH = 0.12D;
    private final OvalWaveField field = new OvalWaveField();
    private final WaveEnvironmentModel environment = new WaveEnvironmentModel();
    private final ShoreBoundWaveField shoreBoundField = new ShoreBoundWaveField();
    private final ShoreWaveResponse shoreResponse = new ShoreWaveResponse();

    WaveSample sample(WaveProfile profile, int x, int z, long tick) {
        return sample(profile, OvalWaveSettings.defaults(), x, z, tick);
    }

    WaveSample sample(WaveProfile profile, OvalWaveSettings settings, int x, int z, long tick) {
        return sample(profile, settings, x, z, tick, 0.86D, 0.50D);
    }

    WaveSample sample(WaveProfile profile, OvalWaveSettings settings, int x, int z, long tick,
            double windX, double windZ) {
        WaveSample center = directionalRaw(profile, settings, x, z, tick, windX, windZ);
        if (!isSeamCandidate(center)) {
            return center;
        }
        WaveSample west = directionalRaw(profile, settings, x - 1, z, tick, windX, windZ);
        if (!west.crest()) {
            return center;
        }
        WaveSample east = directionalRaw(profile, settings, x + 1, z, tick, windX, windZ);
        if (!east.crest()) {
            return center;
        }
        WaveSample north = directionalRaw(profile, settings, x, z - 1, tick, windX, windZ);
        if (!north.crest()) {
            return center;
        }
        WaveSample south = directionalRaw(profile, settings, x, z + 1, tick, windX, windZ);
        return south.crest() ? closeSingleCellSeam(settings, center, west, east, north, south) : center;
    }

    WaveSample fetchAdjusted(OvalWaveSettings settings, WaveSample sample,
            int fetchDistance, int maximumFetch, double windStrength) {
        double growth = environment.fetchGrowth(fetchDistance, maximumFetch, windStrength);
        return fromCrestStrength(settings, sample.crestStrength() * growth,
                sample.travelProgress(), sample.fizzling(), sample.overlaps(),
                sample.activePulses(), false);
    }

    WaveSample shoreBound(OvalWaveSettings settings, WaveProfile profile,
            int shoreDistance, int sourceDistance, int waterDepth, double shoreHeightCap,
            double exposure, double minimumFacing, double windStrength, long tick) {
        ShoreBoundWaveField.FieldSample fieldSample = shoreBoundField.sample(
                shoreDistance, sourceDistance, profile.speed(), tick);
        double facing = environment.coastResponse(exposure, minimumFacing);
        double developedWind = environment.fetchGrowth(sourceDistance, sourceDistance, windStrength);
        // ## Every local front remains coast-bound. Windward exposure makes it larger;
        // side and leeward shores still receive a smaller inward-moving lap.
        double localScale = (0.62D + (0.38D * facing)) * developedWind;
        WaveSample sample = fromStrength(settings, fieldSample.strength() * localScale,
                fieldSample.progress(), fieldSample.fizzling(), 1, 1, false);
        return resolveShoreSample(settings, sample, shoreDistance,
                waterDepth, shoreHeightCap, facing);
    }

    WaveSample shoreGuidedOval(OvalWaveSettings settings, WaveProfile profile,
            int x, int z, int shoreDistance, int sourceDistance,
            int waterDepth, double shoreHeightCap, double exposure,
            double minimumFacing, double windStrength,
            double shoreDirectionX, double shoreDirectionZ, long tick) {
        int source = Math.max(4, sourceDistance);
        OvalWaveField.FieldSample fieldSample = field.sampleShoreGuided(
                profile, settings, x, z, shoreDirectionX, shoreDirectionZ,
                shoreDistance, source, tick);
        double facing = environment.coastResponse(exposure, minimumFacing);
        double developedWind = environment.fetchGrowth(source, source, windStrength);
        // ## Wind changes scale, not destination. The oval itself advances through
        // the coast-distance coordinate and therefore follows the shoreline basin.
        double localScale = (0.72D + (0.28D * facing)) * developedWind;
        WaveSample sample = fromStrength(settings, fieldSample.strength() * localScale,
                fieldSample.progress(), fieldSample.fizzling(),
                fieldSample.overlaps(), fieldSample.activePulses(), false);
        return resolveShoreSample(settings, sample, shoreDistance,
                waterDepth, shoreHeightCap, facing);
    }

    private WaveSample resolveShoreSample(OvalWaveSettings settings, WaveSample sample,
            int shoreDistance, int waterDepth, double shoreHeightCap, double facing) {
        if (!sample.crest()) {
            return sample;
        }
        double coastInfluence = 0.55D + (0.45D * facing);
        ShoreWaveResponse.Impact impact = shoreResponse.resolve(
                sample.crestStrength(), shoreDistance, waterDepth, settings, coastInfluence);
        double capTarget = Math.min(sample.height(), Math.max(0.0D, shoreHeightCap));
        double capProximity = 1.0D - Math.min(1.0D,
                Math.max(0, shoreDistance - 1) / 7.0D);
        double height = lerp(sample.height(), capTarget, smoothStep(capProximity));
        int layer = settings.layerForHeight(height);
        boolean contact = shoreDistance <= 2;
        return new WaveSample(height, impact.pressure(), sample.crestStrength(), layer,
                waterLevelFor(height), layer > 0, sample.envelope(), sample.travelProgress(),
                sample.fizzling(), sample.overlaps(), sample.activePulses(), contact);
    }

    WaveSample shoreAdjusted(OvalWaveSettings settings, WaveSample sample,
            int shoreDistance, int waterDepth, double shoreHeightCap,
            double exposure, double minimumFacing) {
        if (!sample.crest() || shoreDistance < 0) {
            return sample;
        }
        double response = environment.coastResponse(exposure, minimumFacing);
        if (response <= 0.0D) {
            return sample;
        }
        boolean coastFinish = shoreDistance <= 6;
        double finishScale = coastFinish
                ? 0.72D + (0.28D * Math.max(0, shoreDistance) / 6.0D)
                : 1.0D;
        WaveSample incoming = finishScale < 0.999D
                ? fromCrestStrength(settings, sample.crestStrength() * finishScale,
                        sample.travelProgress(), true, sample.overlaps(),
                        sample.activePulses(), false)
                : sample;
        ShoreWaveResponse.Impact impact = shoreResponse.resolve(
                incoming.crestStrength(), shoreDistance, waterDepth, settings, response);
        // ## Only the final six water blocks lower and finish the incoming oval.
        // Open-water geometry, width, merger, and travel remain untouched.
        double capTarget = Math.min(incoming.height(), Math.max(0.0D, shoreHeightCap));
        double capProximity = 1.0D - Math.min(1.0D,
                Math.max(0, shoreDistance - 1) / 7.0D);
        double height = lerp(incoming.height(), capTarget, smoothStep(capProximity) * response);
        int layer = settings.layerForHeight(height);
        return new WaveSample(height, impact.pressure(), incoming.crestStrength(), layer,
                waterLevelFor(height), layer > 0, incoming.envelope(), incoming.travelProgress(),
                incoming.fizzling(), incoming.overlaps(), incoming.activePulses(), impact.impactsShore());
    }


    private WaveSample directionalRaw(WaveProfile profile, OvalWaveSettings settings,
            int x, int z, long tick, double directionX, double directionZ) {
        return fromField(settings, field.sample(profile, settings, x, z, tick, directionX, directionZ));
    }

    private boolean isSeamCandidate(WaveSample sample) {
        return !sample.crest() && sample.crestStrength() >= SEAM_CANDIDATE_STRENGTH;
    }

    private WaveSample closeSingleCellSeam(OvalWaveSettings settings, WaveSample center,
            WaveSample west, WaveSample east, WaveSample north, WaveSample south) {
        // ## Morphological closure is deliberately one-cell and four-sided. It cannot
        // bridge ordinary calm-water gaps or close the open trailing side of a front.
        double neighborFloor = Math.min(Math.min(west.crestStrength(), east.crestStrength()),
                Math.min(north.crestStrength(), south.crestStrength()));
        double strength = Math.max(0.20D, Math.min(0.30D, neighborFloor));
        return fromCrestStrength(settings, strength, center.travelProgress(), center.fizzling(),
                center.overlaps(), center.activePulses(), false);
    }

    private WaveSample fromField(OvalWaveSettings settings, OvalWaveField.FieldSample fieldSample) {
        return fromStrength(settings, fieldSample.strength(), fieldSample.progress(), fieldSample.fizzling(),
                fieldSample.overlaps(), fieldSample.activePulses(), false);
    }

    private WaveSample fromStrength(OvalWaveSettings settings, double strength, double progress,
            boolean fizzling, int overlaps, int activePulses, boolean shoreImpact) {
        // ## The square-root display curve preserves a thin front shoulder while zero-strength
        // water stays flat. Directional geometry keeps the trailing wake submerged and open.
        double crestStrength = Math.sqrt(Math.max(0.0D, Math.min(1.0D, strength)));
        return fromCrestStrength(settings, crestStrength, progress, fizzling, overlaps, activePulses, shoreImpact);
    }

    WaveSample travelingFront(OvalWaveSettings settings, double strength,
            boolean fizzling, int overlaps, int activeFronts) {
        return fromStrength(settings, strength, 0.55D, fizzling,
                overlaps, activeFronts, false);
    }

    private WaveSample fromCrestStrength(OvalWaveSettings settings, double strength, double progress,
            boolean fizzling, int overlaps, int activePulses, boolean shoreImpact) {
        double crestStrength = Math.max(0.0D, Math.min(1.0D, strength));
        int layer = layerForStrength(crestStrength);
        boolean crest = layer > 0;
        // ## A submerged source has no packet height until its front crosses the visible threshold.
        double height = crest ? settings.heightForStrength(crestStrength) : 0.0D;
        return new WaveSample(height, crestStrength, crestStrength, layer, waterLevelFor(height), crest,
                crestStrength, progress, fizzling, overlaps, activePulses, shoreImpact);
    }

    double crestSpacing(WaveProfile profile) {
        return Math.max(4.0D, profile.wavelength() / profile.frequency());
    }

    int layerForStrength(double strength) {
        if (strength >= 0.80D) {
            return 4;
        }
        if (strength >= 0.60D) {
            return 3;
        }
        if (strength >= 0.40D) {
            return 2;
        }
        return strength >= 0.20D ? 1 : 0;
    }

    private int waterLevelFor(double height) {
        double visibleFraction = Math.max(0.0D, Math.min(1.0D, height));
        return Math.max(0, Math.min(7, 7 - (int) Math.round(visibleFraction * 7.0D)));
    }

    private double lerp(double start, double end, double amount) {
        double clamped = Math.max(0.0D, Math.min(1.0D, amount));
        return start + ((end - start) * clamped);
    }

    private double smoothStep(double value) {
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        return clamped * clamped * (3.0D - (2.0D * clamped));
    }

    record WaveSample(double height, double energy, double crestStrength, int layer, int waterLevel,
            boolean crest, double envelope, double travelProgress, boolean fizzling,
            int overlaps, int activePulses, boolean shoreImpact) {
    }
}