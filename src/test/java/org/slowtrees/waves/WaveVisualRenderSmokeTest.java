package org.slowtrees.waves;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class WaveVisualRenderSmokeTest {
    private static final int RADIUS = 100;
    private static final int SIZE = (RADIUS * 2) + 1;
    private static final int SCALE = 3;
    private static final long[] TICKS = {0L, 20L, 40L};
    private static final Color[] LAYERS = {
            new Color(17, 63, 87),
            new Color(72, 153, 179),
            new Color(107, 188, 207),
            new Color(158, 220, 229),
            new Color(224, 247, 247)
    };

    private WaveVisualRenderSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        WaveProfile profile = new WaveProfile(
                0.78D, 1.35D, 44, 2.20D, 0.55D, 0.98D, 168, 0.62D, 0.72D);
        OvalWaveSettings settings = OvalWaveSettings.defaults();
        WaveModel model = new WaveModel();
        int framePixels = SIZE * SCALE;
        BufferedImage image = new BufferedImage(
                framePixels * TICKS.length, framePixels * 2,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int frame = 0; frame < TICKS.length; frame++) {
                drawOriginal(graphics, model, profile, settings,
                        frame * framePixels, 0, TICKS[frame]);
                drawGuided(graphics, model, profile, settings,
                        frame * framePixels, framePixels, TICKS[frame]);
            }
        } finally {
            graphics.dispose();
        }
        File output = new File("target/wave-visual-comparison.png");
        ImageIO.write(image, "png", output);
        System.out.println("Wave visual comparison written to " + output.getAbsolutePath()
                + " (top=original oval, bottom=shore-guided oval; frames=0/20/40 ticks)");
    }

    private static void drawOriginal(Graphics2D graphics, WaveModel model,
            WaveProfile profile, OvalWaveSettings settings,
            int offsetX, int offsetY, long tick) {
        for (int px = 0; px < SIZE; px++) {
            int x = px - RADIUS;
            for (int pz = 0; pz < SIZE; pz++) {
                int z = pz - RADIUS;
                WaveModel.WaveSample sample = model.sample(
                        profile, settings, x, z, tick, 1.0D, 0.0D);
                drawCell(graphics, offsetX, offsetY, px, pz, LAYERS[sample.layer()]);
            }
        }
    }

    private static void drawGuided(Graphics2D graphics, WaveModel model,
            WaveProfile profile, OvalWaveSettings settings,
            int offsetX, int offsetY, long tick) {
        for (int px = 0; px < SIZE; px++) {
            int worldX = px - RADIUS;
            for (int pz = 0; pz < SIZE; pz++) {
                int worldZ = pz - RADIUS;
                if (worldX > 0) {
                    drawCell(graphics, offsetX, offsetY, px, pz,
                            new Color(70, 79, 73));
                    continue;
                }
                int travelledFromSource = worldX + RADIUS;
                int shoreDistance = RADIUS - travelledFromSource;
                WaveModel.WaveSample sample = model.shoreGuidedOval(
                        settings, profile, worldX, worldZ,
                        shoreDistance, RADIUS, 4,
                        Double.POSITIVE_INFINITY, 1.0D, 0.20D,
                        0.80D, 1.0D, 0.0D, tick);
                drawCell(graphics, offsetX, offsetY, px, pz, LAYERS[sample.layer()]);
            }
        }
        graphics.setColor(new Color(245, 238, 200));
        graphics.fillRect(offsetX + (RADIUS * SCALE), offsetY, SCALE, SIZE * SCALE);
    }

    private static void drawCell(Graphics2D graphics, int offsetX, int offsetY,
            int x, int z, Color color) {
        graphics.setColor(color);
        graphics.fillRect(offsetX + (x * SCALE), offsetY + (z * SCALE), SCALE, SCALE);
    }
}
