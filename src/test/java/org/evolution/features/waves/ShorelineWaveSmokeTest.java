package org.evolution.features.waves;

import java.util.Arrays;

public final class ShorelineWaveSmokeTest {
    private ShorelineWaveSmokeTest() {
    }

    public static void main(String[] args) {
        WaveProfile profile = new WaveProfile(
                0.78D, 1.35D, 44, 2.20D, 0.55D, 0.98D, 168, 0.62D, 0.72D);
        OvalWaveSettings settings = OvalWaveSettings.defaults();
        WaveModel model = new WaveModel();
        WaveEnvironmentModel environment = new WaveEnvironmentModel();

        double windwardExposure = environment.coastExposure(1.0D, 0.0D, 1, 0);
        double leewardExposure = environment.coastExposure(1.0D, 0.0D, -1, 0);
        double lateralExposure = environment.coastExposure(1.0D, 0.0D, 0, 1);
        require(windwardExposure > 0.99D, "windward shore exposure missing");
        require(leewardExposure < -0.99D, "leeward shore classification missing");
        require(Math.abs(lateralExposure) < 0.01D, "lateral shore classification missing");

        WaveModel.WaveSample source = strongestSample(model, profile, settings);
        WaveModel.WaveSample sourceBank = model.fetchAdjusted(settings, source, 0, 16, 0.80D);
        WaveModel.WaveSample middle = model.fetchAdjusted(settings, source, 8, 16, 0.80D);
        WaveModel.WaveSample developed = model.fetchAdjusted(settings, source, 16, 16, 0.80D);
        require(!sourceBank.crest(), "open-water wave must begin submerged at its source bank");
        require(sourceBank.crestStrength() < middle.crestStrength()
                        && middle.crestStrength() < developed.crestStrength(),
                "open-water fetch must grow monotonically");

        ShoreBoundWaveField shoreField = new ShoreBoundWaveField();
        require(shoreField.sample(16, 16, profile.speed(), 0L).strength() == 0.0D,
                "each newly emitted shore-bound front must begin submerged offshore");
        int early = peakDistance(shoreField, 16, profile.speed(), 40L);
        int later = peakDistance(shoreField, 16, profile.speed(), 100L);
        require(early > later && later > 0,
                "finite shoreline front must move from offshore toward distance zero");

        long contactTick = 220L;
        WaveModel.WaveSample windward = model.shoreBound(settings, profile,
                1, 16, 2, 0.50D, windwardExposure, 0.20D, 0.80D, contactTick);
        WaveModel.WaveSample leeward = model.shoreBound(settings, profile,
                1, 16, 2, 0.50D, leewardExposure, 0.20D, 0.80D, contactTick);
        WaveModel.WaveSample lateral = model.shoreBound(settings, profile,
                1, 16, 2, 0.50D, lateralExposure, 0.20D, 0.80D, contactTick);
        require(windward.shoreImpact() && leeward.shoreImpact() && lateral.shoreImpact(),
                "every local front must finish at its assigned shoreline");
        require(windward.crestStrength() > leeward.crestStrength(),
                "wind exposure must scale local waves without reversing their direction");

        int[] directionalDistances = new int[8];
        Arrays.fill(directionalDistances, -1);
        directionalDistances[1] = 9; // West is upwind for an eastbound open-water field.
        WaveSurfaceCache.SurfaceColumn lakeColumn = new WaveSurfaceCache.SurfaceColumn(
                0, 63, 0, 4, true, 1, 0, 64, 1, directionalDistances);
        require(lakeColumn.upwindFetch(1.0D, 0.0D, 16) == 8,
                "directional cache must measure water crossed from the upwind bank");
        require(lakeColumn.upwindFetch(-1.0D, 0.0D, 16) == 16,
                "opposite wind must not reuse the wrong shoreline distance");

        System.out.println("Shoreline wave smoke test passed: sources begin submerged offshore, "
                + "finite fronts move inward, and every coast receives a wind-scaled impact.");
    }

    private static int peakDistance(ShoreBoundWaveField field, int sourceDistance,
            double speed, long tick) {
        int bestDistance = -1;
        double bestStrength = 0.0D;
        for (int distance = 0; distance <= sourceDistance; distance++) {
            double strength = field.sample(distance, sourceDistance, speed, tick).strength();
            if (strength > bestStrength) {
                bestStrength = strength;
                bestDistance = distance;
            }
        }
        return bestDistance;
    }

    private static WaveModel.WaveSample strongestSample(WaveModel model, WaveProfile profile,
            OvalWaveSettings settings) {
        WaveModel.WaveSample strongest = model.sample(profile, settings, 0, 0, 0L, 1.0D, 0.0D);
        for (int x = -80; x <= 80; x++) {
            for (int z = -80; z <= 80; z++) {
                WaveModel.WaveSample sample = model.sample(
                        profile, settings, x, z, 0L, 1.0D, 0.0D);
                if (sample.crestStrength() > strongest.crestStrength()) {
                    strongest = sample;
                }
            }
        }
        require(strongest.crestStrength() > 0.90D,
                "test field must contain a developed source crest");
        return strongest;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}