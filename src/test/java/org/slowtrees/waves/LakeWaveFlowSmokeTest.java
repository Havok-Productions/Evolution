package org.slowtrees.waves;

import java.util.ArrayList;
import java.util.List;

public final class LakeWaveFlowSmokeTest {
    private static final int WIDTH = 45;
    private static final int HEIGHT = 35;

    private LakeWaveFlowSmokeTest() {
    }

    public static void main(String[] args) {
        boolean[] known = new boolean[WIDTH * HEIGHT];
        boolean[] lake = new boolean[WIDTH * HEIGHT];
        for (int z = 0; z < HEIGHT; z++) {
            for (int x = 0; x < WIDTH; x++) {
                known[index(x, z)] = true;
                double ellipse = square((x - 21) / 17.0D) + square((z - 17) / 11.0D);
                boolean easternBay = x >= 30 && x <= 39 && z >= 14 && z <= 21
                        && square((x - 31) / 9.0D) + square((z - 17) / 5.0D) <= 1.0D;
                boolean northernCove = x >= 15 && x <= 25 && z >= 5 && z <= 12;
                lake[index(x, z)] = ellipse <= 1.0D || easternBay || northernCove;
            }
        }

        LakeWaveFlowField field = LakeWaveFlowField.build(WIDTH, HEIGHT, known, lake);
        require(field.shoreGuidedComponents() == 1,
                "irregular lake must have one shore-guided component");
        require(field.enclosedComponents() == 1,
                "irregular lake must be one enclosed component");

        int sourceX = -1;
        int sourceZ = -1;
        int sourceDistance = -1;
        int checkedGradients = 0;
        int diagonalGradients = 0;
        int shorelineCells = 0;
        for (int z = 0; z < HEIGHT; z++) {
            for (int x = 0; x < WIDTH; x++) {
                LakeWaveFlowField.Cell cell = field.cell(x, z);
                if (!cell.enclosed()) {
                    continue;
                }
                if (cell.shoreDistance() == 1) {
                    shorelineCells++;
                }
                if (cell.shoreDistance() > sourceDistance) {
                    sourceDistance = cell.shoreDistance();
                    sourceX = x;
                    sourceZ = z;
                }
                if (cell.shoreDistance() > 1) {
                    LakeWaveFlowField.Cell next = field.cell(
                            x + cell.directionX(), z + cell.directionZ());
                    require(next.shoreDistance() < cell.shoreDistance(),
                            "every lake direction must descend toward shore");
                    diagonalGradients += cell.directionX() != 0 && cell.directionZ() != 0 ? 1 : 0;
                    checkedGradients++;
                }
            }
        }
        require(sourceDistance >= 8, "test lake needs a meaningful interior source ridge");
        require(diagonalGradients >= 40,
                "curved shorelines must produce genuine diagonal navigation gradients");
        require(shorelineCells >= 40, "test lake needs several distinct shoreline fronts");

        List<Integer> tracedDistances = new ArrayList<>();
        int x = sourceX;
        int z = sourceZ;
        while (true) {
            LakeWaveFlowField.Cell cell = field.cell(x, z);
            tracedDistances.add(cell.shoreDistance());
            if (cell.shoreDistance() == 1) {
                break;
            }
            x += cell.directionX();
            z += cell.directionZ();
            require(tracedDistances.size() <= sourceDistance,
                    "gradient trace must terminate at the shoreline");
        }

        ShoreBoundWaveField wave = new ShoreBoundWaveField();
        double speed = 2.20D;
        require(peakDistance(wave, sourceDistance, speed, 0L) < 0,
                "lake front must begin submerged on the interior source ridge");
        int[] progress = {18, 38, 62, 82};
        List<Integer> peaks = new ArrayList<>();
        int previous = Integer.MAX_VALUE;
        for (int percentage : progress) {
            long tick = Math.round((sourceDistance * (percentage / 100.0D) / speed) * 20.0D);
            int peak = peakDistance(wave, sourceDistance, speed, tick);
            require(peak > 0 && peak < previous,
                    "each wave frame must move inward instead of restarting at shore");
            peaks.add(peak);
            previous = peak;
        }

        long contactTick = Math.round(((sourceDistance - 1.0D) / speed) * 20.0D);
        int contactingShore = 0;
        for (int zIndex = 0; zIndex < HEIGHT; zIndex++) {
            for (int xIndex = 0; xIndex < WIDTH; xIndex++) {
                LakeWaveFlowField.Cell cell = field.cell(xIndex, zIndex);
                if (cell.enclosed() && cell.shoreDistance() == 1
                        && wave.sample(1, sourceDistance, speed, contactTick).strength() > 0.20D) {
                    contactingShore++;
                }
            }
        }
        require(contactingShore == shorelineCells,
                "the inward distance ring must reach every shoreline of the enclosed lake");

        boolean[] ocean = new boolean[WIDTH * HEIGHT];
        for (int zIndex = 8; zIndex < HEIGHT - 8; zIndex++) {
            for (int xIndex = 0; xIndex < WIDTH; xIndex++) {
                ocean[index(xIndex, zIndex)] = true;
            }
        }
        LakeWaveFlowField openField = LakeWaveFlowField.build(WIDTH, HEIGHT, known, ocean);
        require(openField.enclosedComponents() == 0,
                "water touching the render boundary must not be reported as enclosed");
        require(openField.shoreGuidedComponents() == 1,
                "open water with visible banks must still be shore-guided");
        LakeWaveFlowField.Cell openCenter = openField.cell(WIDTH / 2, HEIGHT / 2);
        require(openCenter.shoreGuided() && !openCenter.enclosed(),
                "render-boundary water must retain the visible shoreline basin");

        boolean[] shorelessWater = new boolean[WIDTH * HEIGHT];
        java.util.Arrays.fill(shorelessWater, true);
        LakeWaveFlowField shorelessField = LakeWaveFlowField.build(
                WIDTH, HEIGHT, known, shorelessWater);
        require(shorelessField.shoreGuidedComponents() == 0,
                "water with no visible shore must remain globally wind-driven");

        int openCoastFronts = activeFrontBands(wave, 100, speed, 0L);
        require(openCoastFronts >= 4,
                "large visible coast must carry several simultaneous inbound fronts");

        System.out.println("Lake wave smoke test passed: enclosed=1 source-distance="
                + sourceDistance + " inward-peaks=" + peaks
                + " gradient-cells=" + checkedGradients
                + " diagonal-gradients=" + diagonalGradients
                + " shoreline-contact=" + contactingShore + "/" + shorelineCells
                + " offshore-birth=true open-coast-guided=true open-coast-fronts="
                + openCoastFronts + " shoreless-wind=true");
    }

    private static int activeFrontBands(ShoreBoundWaveField field, int sourceDistance,
            double speed, long tick) {
        int bands = 0;
        boolean active = false;
        for (int distance = 1; distance <= sourceDistance; distance++) {
            boolean next = field.sample(distance, sourceDistance, speed, tick).strength() > 0.20D;
            if (next && !active) {
                bands++;
            }
            active = next;
        }
        return bands;
    }

    private static int peakDistance(ShoreBoundWaveField field, int sourceDistance,
            double speed, long tick) {
        int bestDistance = -1;
        double bestStrength = 0.0D;
        for (int distance = 1; distance <= sourceDistance; distance++) {
            double strength = field.sample(distance, sourceDistance, speed, tick).strength();
            if (strength > bestStrength) {
                bestStrength = strength;
                bestDistance = distance;
            }
        }
        return bestDistance;
    }

    private static int index(int x, int z) {
        return (z * WIDTH) + x;
    }

    private static double square(double value) {
        return value * value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
