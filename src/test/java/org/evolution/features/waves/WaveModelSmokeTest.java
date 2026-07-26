package org.evolution.features.waves;

public final class WaveModelSmokeTest {
    private WaveModelSmokeTest() {
    }

    public static void main(String[] args) {
        WaveProfile profile = new WaveProfile(0.78D, 1.35D, 44, 2.20D, 0.55D, 0.98D, 168, 0.62D, 0.72D);
        WaveModel model = new WaveModel();

        require(model.layerForStrength(0.19D) == 0, "sub-edge energy must stay flat");
        require(model.layerForStrength(0.20D) == 1, "0.2 edge layer missing");
        require(model.layerForStrength(0.40D) == 2, "0.4 lower-middle layer missing");
        require(model.layerForStrength(0.60D) == 3, "0.6 upper-middle layer missing");
        require(model.layerForStrength(0.80D) == 4, "0.8 crest layer missing");
        require(Math.abs(profile.speed() - 1.35D) < 0.0001D, "speed must be blocks per second");

        WaveProfile lowerCadence = new WaveProfile(0.78D, 1.35D, 44, 0.50D, 0.55D, 0.98D, 168, 0.62D, 0.72D);
        require(model.crestSpacing(profile) < model.crestSpacing(lowerCadence),
                "frequency must shorten distance between oval centers");

        OvalWavePulse submerged = new OvalWavePulse(0.0D, 0.0D, 8.0D, 14.0D, 1.0D, 0.02D, true);
        require(submerged.strengthAt(5.5D, 0.0D) == 0.0D,
                "new wave sources must begin below the visible sea surface");

        OvalWavePulse pulse = new OvalWavePulse(0.0D, 0.0D, 8.0D, 14.0D, 1.0D, 0.25D, true);
        double submergedWake = pulse.strengthAt(0.0D, 0.0D);
        double shoulder = pulse.strengthAt(2.0D, 0.0D);
        double front = pulse.strengthAt(5.5D, 0.0D);
        require(submergedWake == 0.0D, "trailing wake must stay open instead of closing a donut");
        require(shoulder > 0.0D && shoulder < front,
                "the front must rise from a low shoulder toward its leading edge");
        require(pulse.strengthAt(-4.0D, 0.0D) == 0.0D, "wave wake must remain open behind the source");
        require(pulse.strengthAt(7.5D, 0.0D) == 0.0D, "water ahead of the front must remain flat");
        require(pulse.strengthAt(4.8D, 7.0D) > 0.0D,
                "the leading edge must curve backward into a broad crescent");

        OvalWaveSettings settings = OvalWaveSettings.defaults();
        require(Math.abs(settings.heightForLayer(1) - 0.20D) < 0.0001D, "edge height must be 0.2 blocks");
        require(Math.abs(settings.heightForLayer(3) - 0.80D) < 0.0001D, "inner height must be 0.8 blocks");
        require(Math.abs(settings.heightForLayer(4) - 1.80D) < 0.0001D, "crest height must be 1.8 blocks");
        require(settings.heightForStrength(0.25D) < settings.heightForStrength(0.60D),
                "fine terraces must rise with outward field strength");
        WaveHeightStack stack = new WaveHeightStack();
        java.util.List<WaveHeightStack.VisualLayer> crestLayers = stack.layers(1.80D);
        require(crestLayers.size() == 2, "1.8-block crest must use one full and one partial packet layer");
        require(crestLayers.get(0).yOffset() == 0 && crestLayers.get(0).waterLevel() == 0,
                "1.8-block crest base must be full packet water");
        require(crestLayers.get(1).yOffset() == 1 && crestLayers.get(1).waterLevel() > 0,
                "1.8-block crest top must remain partial and below two blocks");

        WaveVisualSmoother smoother = new WaveVisualSmoother();
        int firstTransition = smoother.approach(3, 0, 0.35D);
        int secondTransition = smoother.approach(firstTransition, 0, 0.35D);
        require(firstTransition == 2 && secondTransition == 1,
                "an existing passing crest must approach its spatial height one level per frame");
        require(smoother.approach(1, 7, 0.35D) == 2,
                "fizzling fronts must lower through bounded fluid-level steps");

        WaveFieldMerger merger = new WaveFieldMerger();
        double merged = merger.merge(0.35D, 0.45D, 0.82D);
        require(merged > 0.35D && merged > (0.45D * 0.82D), "overlap must strengthen the joined field");
        require(merged <= 1.0D, "merged field must remain normalized");

        System.out.println("Wave model smoke test passed: submerged origin, open directional front, outward rise, cadence, and smooth union.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}