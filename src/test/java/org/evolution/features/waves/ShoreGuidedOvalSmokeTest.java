package org.evolution.features.waves;

public final class ShoreGuidedOvalSmokeTest {
    private static final int SOURCE_DISTANCE = 100;
    private static final int MIN_Z = -60;
    private static final int MAX_Z = 60;

    private ShoreGuidedOvalSmokeTest() {
    }

    public static void main(String[] args) {
        WaveProfile profile = new WaveProfile(
                0.78D, 1.35D, 44, 2.20D, 0.55D, 0.98D, 168, 0.62D, 0.72D);
        OvalWaveSettings settings = OvalWaveSettings.defaults();
        WaveModel model = new WaveModel();

        Frame first = frame(model, profile, settings, 0L);
        Frame next = frame(model, profile, settings, 20L);
        Frame later = frame(model, profile, settings, 80L);
        require(first.crests > 900 && next.crests > 900 && later.crests > 900,
                "guided oval water must remain broadly populated across time");
        require(first.merged > 80,
                "guided oval fronts must retain overlap and soft merging");
        require(first.maximumTangentRun >= 12,
                "guided fronts must remain wide across their tangent");
        require(first.maximumForwardRun >= 8,
                "guided fronts must retain a deep shoulder instead of becoming 1-3 block lines");
        require(first.layer1 > 0 && first.layer2 > 0
                        && first.layer3 > 0 && first.layer4 > 0,
                "guided ovals must retain layered edge, shoulder, inner, and crest heights");
        require(first.expanding > 0 && first.fizzling > 0,
                "guided field must contain both growing and finishing oval stages");

        for (int z = MIN_Z; z <= MAX_Z; z++) {
            require(!sample(model, profile, settings, 0, z, 0L).crest(),
                    "offshore source boundary must remain submerged");
        }

        int bestShift = bestForwardShift(first.strength, next.strength);
        require(bestShift > 0,
                "guided oval correlation must move toward shore, not away from it");

        boolean reachedShore = false;
        for (long tick = 0L; tick <= 900L && !reachedShore; tick += 10L) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                if (sample(model, profile, settings,
                        SOURCE_DISTANCE - 1, z, tick).shoreImpact()) {
                    reachedShore = true;
                    break;
                }
            }
        }
        require(reachedShore,
                "at least one coherent oval must survive its journey and contact shore");

        System.out.println("Shore-guided oval smoke test passed: crests=" + first.crests
                + "/" + next.crests + "/" + later.crests
                + " merged=" + first.merged
                + " tangent-run=" + first.maximumTangentRun
                + " forward-thickness=" + first.maximumForwardRun
                + " best-forward-shift=" + bestShift
                + " layered=true offshore-submerged=true shore-contact=true");
    }

    private static Frame frame(WaveModel model, WaveProfile profile,
            OvalWaveSettings settings, long tick) {
        int width = SOURCE_DISTANCE;
        int depth = (MAX_Z - MIN_Z) + 1;
        double[][] strength = new double[width][depth];
        int crests = 0;
        int merged = 0;
        int expanding = 0;
        int fizzling = 0;
        int layer1 = 0;
        int layer2 = 0;
        int layer3 = 0;
        int layer4 = 0;
        int maximumTangentRun = 0;
        int maximumForwardRun = 0;
        int[] forwardRuns = new int[depth];
        for (int x = 0; x < SOURCE_DISTANCE; x++) {
            int tangentRun = 0;
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                WaveModel.WaveSample sample = sample(model, profile, settings, x, z, tick);
                strength[x][z - MIN_Z] = sample.crestStrength();
                int zIndex = z - MIN_Z;
                if (!sample.crest()) {
                    tangentRun = 0;
                    forwardRuns[zIndex] = 0;
                    continue;
                }
                crests++;
                merged += sample.overlaps() >= 2 ? 1 : 0;
                expanding += sample.fizzling() ? 0 : 1;
                fizzling += sample.fizzling() ? 1 : 0;
                layer1 += sample.layer() == 1 ? 1 : 0;
                layer2 += sample.layer() == 2 ? 1 : 0;
                layer3 += sample.layer() == 3 ? 1 : 0;
                layer4 += sample.layer() == 4 ? 1 : 0;
                tangentRun++;
                maximumTangentRun = Math.max(maximumTangentRun, tangentRun);
                forwardRuns[zIndex]++;
                maximumForwardRun = Math.max(maximumForwardRun, forwardRuns[zIndex]);
            }
        }
        return new Frame(strength, crests, merged, expanding, fizzling,
                layer1, layer2, layer3, layer4,
                maximumTangentRun, maximumForwardRun);
    }

    private static WaveModel.WaveSample sample(WaveModel model, WaveProfile profile,
            OvalWaveSettings settings, int travelledFromSource, int z, long tick) {
        int shoreDistance = SOURCE_DISTANCE - travelledFromSource;
        return model.shoreGuidedOval(settings, profile,
                travelledFromSource, z, shoreDistance, SOURCE_DISTANCE,
                4, Double.POSITIVE_INFINITY, 1.0D, 0.20D,
                0.80D, 1.0D, 0.0D, tick);
    }

    private static int bestForwardShift(double[][] first, double[][] next) {
        int bestShift = 0;
        double bestScore = -1.0D;
        for (int shift = -5; shift <= 5; shift++) {
            double score = 0.0D;
            for (int x = 5; x < SOURCE_DISTANCE - 5; x++) {
                int nextX = x + shift;
                if (nextX < 0 || nextX >= SOURCE_DISTANCE) {
                    continue;
                }
                for (int z = 0; z < first[x].length; z++) {
                    score += first[x][z] * next[nextX][z];
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestShift = shift;
            }
        }
        return bestShift;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Frame(double[][] strength, int crests, int merged,
            int expanding, int fizzling, int layer1, int layer2,
            int layer3, int layer4, int maximumTangentRun,
            int maximumForwardRun) {
    }
}
