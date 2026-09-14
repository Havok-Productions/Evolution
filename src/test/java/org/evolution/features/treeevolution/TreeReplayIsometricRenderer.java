package org.evolution.features.treeevolution;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Cell;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Ownership;

/**
 * ## Produces readable isometric replay renders from actual virtual-world cells.
 */
final class TreeReplayIsometricRenderer {
    private static final int HALF_WIDTH = 7;
    private static final int HALF_DEPTH = 4;
    private static final int BLOCK_HEIGHT = 8;
    private static final int MARGIN = 34;
    private static final int HEADER = 62;

    private TreeReplayIsometricRenderer() {
    }

    static List<Path> renderAll(
            Path directory,
            String baseName,
            String title,
            TreeDna dna,
            Map<String, Cell> cells,
            TreeVisualQualityAudit.Report audit
    ) throws IOException {
        List<Path> rendered = new ArrayList<>();
        for (View view : View.values()) {
            Path target = directory.resolve(
                    baseName + "-" + view.id() + ".png");
            render(target, title + " / " + view.label(),
                    dna, cells, audit, view);
            rendered.add(target);
        }
        return List.copyOf(rendered);
    }

    static void render(
            Path target,
            String title,
            TreeDna dna,
            Map<String, Cell> cells,
            TreeVisualQualityAudit.Report audit
    ) throws IOException {
        render(target, title, dna, cells, audit, View.NORTH_EAST);
    }

    private static void render(
            Path target,
            String title,
            TreeDna dna,
            Map<String, Cell> cells,
            TreeVisualQualityAudit.Report audit,
            View view
    ) throws IOException {
        List<RenderCell> renderCells = new ArrayList<>();
        for (Map.Entry<String, Cell> entry : cells.entrySet()) {
            Coordinate coordinate = coordinate(entry.getKey());
            renderCells.add(new RenderCell(
                    coordinate.x(), coordinate.y(), coordinate.z(),
                    entry.getValue()));
        }
        // ## Painter order must use camera-rotated depth. Sorting by world
        // x+z only rendered the opposite two views as overlapping stripes.
        renderCells.sort(Comparator
                .comparingInt((RenderCell cell) ->
                        projectedDepth(cell, dna, view))
                .thenComparingInt(RenderCell::y)
                .thenComparingInt(cell ->
                        rotate(cell, dna, view).x()));

        Bounds bounds = projectedBounds(renderCells, dna, view);
        int width = Math.max(520, bounds.width() + MARGIN * 2);
        int height = Math.max(420,
                bounds.height() + MARGIN * 2 + HEADER);
        BufferedImage image = new BufferedImage(
                width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(248, 250, 247));
        graphics.fillRect(0, 0, width, height);

        int originX = MARGIN - bounds.minX();
        int originY = HEADER + MARGIN - bounds.minY();
        for (RenderCell cell : renderCells) {
            drawBlock(
                    graphics,
                    originX + projectX(cell, dna, view),
                    originY + projectY(cell, dna, view),
                    cell.cell());
        }

        graphics.setColor(new Color(28, 34, 30));
        graphics.setFont(new Font("SansSerif", Font.BOLD, 17));
        graphics.drawString(title, 18, 24);
        graphics.setFont(new Font("Monospaced", Font.PLAIN, 12));
        graphics.drawString(
                dna.species().id() + " " + dna.maturityStage()
                        + " | " + audit.metrics(),
                18, 45);
        if (!audit.passed()) {
            graphics.setColor(new Color(165, 34, 34));
            graphics.drawString(
                    "VISUAL AUDIT: " + audit.failureSummary(),
                    18, height - 18);
        }
        graphics.dispose();

        Files.createDirectories(target.toAbsolutePath().getParent());
        ImageIO.write(image, "png", target.toFile());
    }

    private static Bounds projectedBounds(
            List<RenderCell> cells, TreeDna dna, View view) {
        if (cells.isEmpty()) {
            return new Bounds(0, 1, 0, 1);
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (RenderCell cell : cells) {
            int x = projectX(cell, dna, view);
            int y = projectY(cell, dna, view);
            minX = Math.min(minX, x - HALF_WIDTH);
            maxX = Math.max(maxX, x + HALF_WIDTH);
            minY = Math.min(minY, y - HALF_DEPTH);
            maxY = Math.max(maxY, y + BLOCK_HEIGHT + HALF_DEPTH);
        }
        return new Bounds(minX, maxX, minY, maxY);
    }

    private static int projectX(
            RenderCell cell, TreeDna dna, View view) {
        Rotated rotated = rotate(cell, dna, view);
        int x = rotated.x();
        int z = rotated.z();
        return (x - z) * HALF_WIDTH;
    }

    private static int projectY(
            RenderCell cell, TreeDna dna, View view) {
        Rotated rotated = rotate(cell, dna, view);
        int x = rotated.x();
        int y = cell.y() - dna.baseY();
        int z = rotated.z();
        return (x + z) * HALF_DEPTH - y * BLOCK_HEIGHT;
    }

    private static int projectedDepth(
            RenderCell cell, TreeDna dna, View view) {
        Rotated rotated = rotate(cell, dna, view);
        return rotated.x() + rotated.z();
    }

    private static Rotated rotate(
            RenderCell cell, TreeDna dna, View view) {
        int x = cell.x() - dna.baseX();
        int z = cell.z() - dna.baseZ();
        return switch (view) {
            case NORTH_EAST -> new Rotated(x, z);
            case SOUTH_EAST -> new Rotated(-z, x);
            case SOUTH_WEST -> new Rotated(-x, -z);
            case NORTH_WEST -> new Rotated(z, -x);
        };
    }

    private static void drawBlock(
            Graphics2D graphics, int x, int y, Cell cell) {
        Color top = topColor(cell);
        Color left = shade(top, 0.82D);
        Color right = shade(top, 0.68D);
        Polygon topFace = new Polygon(
                new int[]{x, x + HALF_WIDTH, x, x - HALF_WIDTH},
                new int[]{y - HALF_DEPTH, y, y + HALF_DEPTH, y},
                4);
        Polygon leftFace = new Polygon(
                new int[]{x - HALF_WIDTH, x, x, x - HALF_WIDTH},
                new int[]{y, y + HALF_DEPTH, y + HALF_DEPTH + BLOCK_HEIGHT,
                        y + BLOCK_HEIGHT},
                4);
        Polygon rightFace = new Polygon(
                new int[]{x, x + HALF_WIDTH, x + HALF_WIDTH, x},
                new int[]{y + HALF_DEPTH, y, y + BLOCK_HEIGHT,
                        y + HALF_DEPTH + BLOCK_HEIGHT},
                4);
        graphics.setColor(left);
        graphics.fillPolygon(leftFace);
        graphics.setColor(right);
        graphics.fillPolygon(rightFace);
        graphics.setColor(top);
        graphics.fillPolygon(topFace);
        graphics.setColor(new Color(45, 55, 48, 125));
        graphics.drawPolygon(topFace);
    }

    private static Color topColor(Cell cell) {
        if (cell.ownership() == Ownership.NEIGHBOR) {
            return new Color(124, 132, 126);
        }
        Color base = switch (cell.role()) {
            case CANOPY -> new Color(74, 151, 74);
            case TRUNK -> new Color(143, 102, 60);
            case BRANCH -> new Color(126, 86, 49);
            default -> new Color(130, 136, 132);
        };
        return cell.ownership() == Ownership.SOURCE
                ? blend(base, Color.WHITE, 0.24D)
                : base;
    }

    private static Color shade(Color color, double scale) {
        return new Color(
                clamp((int) Math.round(color.getRed() * scale)),
                clamp((int) Math.round(color.getGreen() * scale)),
                clamp((int) Math.round(color.getBlue() * scale)),
                color.getAlpha());
    }

    private static Color blend(
            Color first, Color second, double amount) {
        double inverse = 1.0D - amount;
        return new Color(
                clamp((int) Math.round(
                        first.getRed() * inverse
                                + second.getRed() * amount)),
                clamp((int) Math.round(
                        first.getGreen() * inverse
                                + second.getGreen() * amount)),
                clamp((int) Math.round(
                        first.getBlue() * inverse
                                + second.getBlue() * amount)));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static Coordinate coordinate(String value) {
        String[] parts = value.split(":");
        int offset = parts.length - 3;
        return new Coordinate(
                Integer.parseInt(parts[offset]),
                Integer.parseInt(parts[offset + 1]),
                Integer.parseInt(parts[offset + 2]));
    }

    private record Coordinate(int x, int y, int z) {
    }

    private record RenderCell(int x, int y, int z, Cell cell) {
    }

    private record Rotated(int x, int z) {
    }

    private enum View {
        NORTH_EAST("ne", "north-east"),
        SOUTH_EAST("se", "south-east"),
        SOUTH_WEST("sw", "south-west"),
        NORTH_WEST("nw", "north-west");

        private final String id;
        private final String label;

        View(String id, String label) {
            this.id = id;
            this.label = label;
        }

        String id() {
            return id;
        }

        String label() {
            return label;
        }
    }

    private record Bounds(int minX, int maxX, int minY, int maxY) {
        int width() {
            return maxX - minX + 1;
        }

        int height() {
            return maxY - minY + 1;
        }
    }
}
