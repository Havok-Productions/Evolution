package org.slowtrees.waves;

public final class ShoreWaveSteeringSmokeTest {
    private ShoreWaveSteeringSmokeTest() {
    }

    public static void main(String[] args) {
        ShoreWaveSteering steering = new ShoreWaveSteering();
        ShoreWaveSteering.Steering far = steering.resolve(
                -1.0D, 0.0D, 1.0D, 0.0D, 40, 40);
        require(close(far.directionX(), -1.0D) && close(far.directionZ(), 0.0D),
                "far-water dead zone must preserve the global wind direction exactly");

        double previousDot = -1.01D;
        for (int distance = 39; distance >= 1; distance--) {
            ShoreWaveSteering.Steering turn = steering.resolve(
                    -1.0D, 0.0D, 1.0D, 0.0D, distance, 40);
            double magnitude = Math.sqrt((turn.directionX() * turn.directionX())
                    + (turn.directionZ() * turn.directionZ()));
            require(Math.abs(magnitude - 1.0D) < 0.0001D,
                    "angle steering must never cancel into a static zero vector");
            double shoreDot = turn.directionX();
            require(shoreDot + 0.0001D >= previousDot,
                    "shoreward alignment must increase monotonically with proximity");
            previousDot = shoreDot;
        }
        ShoreWaveSteering.Steering contact = steering.resolve(
                -1.0D, 0.0D, 1.0D, 0.0D, 1, 40);
        require(contact.directionX() > 0.98D,
                "final approach must face the shoreline even against opposite wind");

        OvalWavePulse boomerang = new OvalWavePulse(
                0.0D, 0.0D, 22.0D, 32.0D, 1.0D, 0.55D, true);
        int centerThickness = thickness(boomerang, 0.0D);
        int tipThickness = thickness(boomerang, 25.0D);
        require(centerThickness >= 12,
                "boomerang center must have a deep visible shoulder");
        require(centerThickness >= tipThickness + 5,
                "boomerang middle must be visibly thicker than its tips: center=" + centerThickness + " tip=" + tipThickness);

        WaveProfile profile = new WaveProfile(
                0.78D, 1.35D, 44, 2.20D, 0.55D, 0.98D, 168, 0.62D, 0.72D);
        OvalWaveSettings settings = OvalWaveSettings.defaults();
        WaveModel model = new WaveModel();
        ShoreWaveSteering.Steering near = steering.resolve(
                0.0D, 1.0D, 1.0D, 0.0D, 2, 40);
        double forward = correlation(model, profile, settings, near, 2);
        double backward = correlation(model, profile, settings, near, -2);
        require(forward > backward,
                "the unchanged oval field must correlate as moving toward shore");

        WaveModel.WaveSample strongest = strongest(model, profile, settings, near);
        WaveModel.WaveSample finished = model.shoreAdjusted(
                settings, strongest, 1, 4, 0.50D, 1.0D, 0.0D);
        require(finished.fizzling() && finished.shoreImpact(),
                "the final coast envelope must fizzle and impact at land contact");
        require(finished.crestStrength() < strongest.crestStrength()
                        && finished.crestStrength() > strongest.crestStrength() * 0.60D,
                "coast finish must soften without erasing the crashing oval");

        System.out.println("Shore steering smoke test passed: dead-zone=wind"
                + " contact-direction=" + round(contact.directionX())
                + " center-thickness=" + centerThickness
                + " tip-thickness=" + tipThickness
                + " forward-correlation=" + round(forward)
                + " backward-correlation=" + round(backward));
    }

    private static WaveModel.WaveSample strongest(WaveModel model,
            WaveProfile profile, OvalWaveSettings settings,
            ShoreWaveSteering.Steering direction) {
        WaveModel.WaveSample strongest = model.sample(profile, settings,
                0, 0, 0L, direction.directionX(), direction.directionZ());
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z++) {
                WaveModel.WaveSample candidate = model.sample(profile, settings,
                        x, z, 0L, direction.directionX(), direction.directionZ());
                if (candidate.crestStrength() > strongest.crestStrength()) {
                    strongest = candidate;
                }
            }
        }
        return strongest;
    }

    private static int thickness(OvalWavePulse pulse, double v) {
        int cells = 0;
        for (int u = -30; u <= 30; u++) {
            cells += pulse.strengthAt(u, v) >= 0.20D ? 1 : 0;
        }
        return cells;
    }

    private static double correlation(WaveModel model, WaveProfile profile,
            OvalWaveSettings settings, ShoreWaveSteering.Steering direction,
            int shift) {
        double score = 0.0D;
        for (int x = -50; x <= 50; x++) {
            for (int z = -50; z <= 50; z++) {
                WaveModel.WaveSample first = model.sample(profile, settings,
                        x, z, 0L, direction.directionX(), direction.directionZ());
                WaveModel.WaveSample next = model.sample(profile, settings,
                        x + shift, z, 20L, direction.directionX(), direction.directionZ());
                score += first.crestStrength() * next.crestStrength();
            }
        }
        return score;
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) < 0.0001D;
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
