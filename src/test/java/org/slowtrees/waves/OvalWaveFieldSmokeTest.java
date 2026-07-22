package org.slowtrees.waves;

public final class OvalWaveFieldSmokeTest {
    private static final int RADIUS = 100;

    private OvalWaveFieldSmokeTest() {
    }

    public static void main(String[] args) {
        WaveProfile profile = new WaveProfile(0.78D, 1.35D, 44, 2.20D, 0.55D, 0.98D, 168, 0.62D, 0.72D);
        OvalWaveSettings settings = OvalWaveSettings.defaults();
        WaveModel model = new WaveModel();
        boolean[][] first = new boolean[(RADIUS * 2) + 1][(RADIUS * 2) + 1];
        int crests = 0;
        int merged = 0;
        int expanding = 0;
        int closing = 0;
        int changed = 0;
        int shortFrameChanged = 0;
        WaveModel.WaveSample shoreCandidate = null;

        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                WaveModel.WaveSample now = model.sample(profile, settings, x, z, 0L);
                WaveModel.WaveSample nextFrame = model.sample(profile, settings, x, z, 6L);
                WaveModel.WaveSample later = model.sample(profile, settings, x, z, 40L);
                first[x + RADIUS][z + RADIUS] = now.crest();
                if (now.crest()) {
                    crests++;
                    merged += now.overlaps() >= 2 ? 1 : 0;
                    expanding += now.fizzling() ? 0 : 1;
                    closing += now.fizzling() ? 1 : 0;
                    if (shoreCandidate == null && now.crestStrength() < 0.95D) {
                        shoreCandidate = now;
                    }
                }
                if (now.layer() != nextFrame.layer()) {
                    shortFrameChanged++;
                }
                if (now.layer() != later.layer()) {
                    changed++;
                }
            }
        }

        int enclosedHoles = enclosedSingleCellHoles(first);
        System.out.println("Oval metrics before assertions: crests=" + crests + " merged=" + merged
                + " moving=" + changed + " short-frame-moving=" + shortFrameChanged
                + " expanding=" + expanding
                + " closing=" + closing + " enclosed-holes=" + enclosedHoles);
        require(crests > 500, "oval field must produce broad visible water coverage");
        require(crests < 20000, "100-block field must preserve calm water between front groups");
        require(merged > 250, "nearby directional fronts must overlap and merge");
        require(expanding > 0 && closing > 0, "frame must contain expanding and closing pulses");
        require(changed > 100, "oval field must visibly travel within two seconds");
        require(shortFrameChanged > 100,
                "front must make visible partial progress within one six-tick frame");
        require(enclosedHoles == 0,
                "terraced oval union must not contain single-cell cheese holes: " + enclosedHoles);
        require(shoreCandidate != null, "test requires a visible shore candidate");

        WaveModel.WaveSample shore = model.shoreAdjusted(settings, shoreCandidate, 1, 2, 0.50D, 1.0D, 0.20D);
        require(shore.shoreImpact(), "near-shore pulse must be marked as an impact");
        require(shore.energy() >= shoreCandidate.energy(), "shallow shore must strengthen impact pressure");
        require(shore.height() <= shoreCandidate.height(),
                "shore impact must never exceed the incoming front height");
        require(shore.height() <= 0.50D + 0.0001D,
                "final approach must not rise above the detected shoreline elevation");

        System.out.println("Oval wave smoke test passed: crests=" + crests + " merged=" + merged
                + " moving=" + changed + " short-frame-moving=" + shortFrameChanged
                + " expanding=" + expanding
                + " closing=" + closing + " enclosed-holes=" + enclosedHoles);
    }

    private static int enclosedSingleCellHoles(boolean[][] field) {
        int holes = 0;
        for (int x = 1; x < field.length - 1; x++) {
            for (int z = 1; z < field[x].length - 1; z++) {
                if (!field[x][z] && field[x - 1][z] && field[x + 1][z]
                        && field[x][z - 1] && field[x][z + 1]) {
                    holes++;
                }
            }
        }
        return holes;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}