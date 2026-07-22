package org.slowtrees.waves;

public final class WaveAsciiVisualTest {
    private static final char[] LAYERS = {' ', '.', 'o', 'O', '#'};

    private WaveAsciiVisualTest() {
    }

    public static void main(String[] args) {
        WaveProfile profile = new WaveProfile(
                0.78D, 1.35D, 44, 2.20D, 0.55D, 0.98D, 168, 0.62D, 0.72D);
        OvalWaveSettings settings = OvalWaveSettings.defaults();
        WaveModel model = new WaveModel();
        System.out.println("ORIGINAL OVAL (left) | SHORE-DIRECTED OVAL (right), tick=20");
        for (int z = -60; z <= 60; z += 3) {
            StringBuilder original = new StringBuilder();
            StringBuilder guided = new StringBuilder();
            for (int x = -100; x <= 0; x += 2) {
                original.append(LAYERS[maxOriginalLayer(
                        model, profile, settings, x, z, 20L)]);
                guided.append(LAYERS[maxGuidedLayer(
                        model, profile, settings, x, z, 20L)]);
            }
            System.out.println(original + " | " + guided);
        }
    }

    private static int maxOriginalLayer(WaveModel model, WaveProfile profile,
            OvalWaveSettings settings, int x, int z, long tick) {
        int layer = 0;
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                layer = Math.max(layer, model.sample(profile, settings,
                        x + dx, z + dz, tick, 1.0D, 0.0D).layer());
            }
        }
        return layer;
    }

    private static int maxGuidedLayer(WaveModel model, WaveProfile profile,
            OvalWaveSettings settings, int x, int z, long tick) {
        int layer = 0;
        int travelledFromSource = x + 100;
        int shoreDistance = 100 - travelledFromSource;
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                layer = Math.max(layer, model.shoreGuidedOval(
                        settings, profile, x + dx, z + dz,
                        shoreDistance, 100, 4, Double.POSITIVE_INFINITY,
                        1.0D, 0.20D, 0.80D, 1.0D, 0.0D, tick).layer());
            }
        }
        return layer;
    }
}
