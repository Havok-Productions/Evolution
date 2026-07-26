package org.evolution.features.portal;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

final class PortalFrameDetector {
    private PortalFrameDetector() {
    }

    static Optional<PortalShapePlan> detect(
            PortalCell start,
            PortalPlane plane,
            int minimumInteriorBlocks,
            int maximumInteriorBlocks,
            int maximumWidth,
            int maximumHeight,
            CellLookup lookup
    ) {
        Queue<PortalCell> pending = new ArrayDeque<>();
        Set<PortalCell> visited = new HashSet<>();
        Set<PortalCell> interior = new HashSet<>();
        Set<PortalCell> frame = new HashSet<>();
        pending.add(start);

        while (!pending.isEmpty()) {
            PortalCell cell = pending.remove();
            if (!visited.add(cell)) {
                continue;
            }
            if (Math.abs(cell.horizontalCoordinate(plane)
                    - start.horizontalCoordinate(plane)) > maximumWidth
                    || Math.abs(cell.y() - start.y()) > maximumHeight) {
                return Optional.empty();
            }

            CellType type = lookup.typeAt(cell);
            if (type == CellType.FRAME) {
                frame.add(cell);
                continue;
            }
            if (type != CellType.INTERIOR) {
                return Optional.empty();
            }

            interior.add(cell);
            if (interior.size() > maximumInteriorBlocks) {
                return Optional.empty();
            }
            pending.add(cell.horizontal(-1, plane));
            pending.add(cell.horizontal(1, plane));
            pending.add(cell.vertical(-1));
            pending.add(cell.vertical(1));
        }

        if (interior.size() < minimumInteriorBlocks || frame.isEmpty()) {
            return Optional.empty();
        }

        int minHorizontal = Integer.MAX_VALUE;
        int maxHorizontal = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (PortalCell cell : interior) {
            int horizontal = cell.horizontalCoordinate(plane);
            minHorizontal = Math.min(minHorizontal, horizontal);
            maxHorizontal = Math.max(maxHorizontal, horizontal);
            minY = Math.min(minY, cell.y());
            maxY = Math.max(maxY, cell.y());
        }
        int width = maxHorizontal - minHorizontal + 1;
        int height = maxY - minY + 1;
        if (width < 2 || height < 3
                || width > maximumWidth || height > maximumHeight) {
            return Optional.empty();
        }

        return Optional.of(new PortalShapePlan(plane, interior, frame));
    }

    enum CellType {
        FRAME,
        INTERIOR,
        BLOCKED,
        UNAVAILABLE
    }

    @FunctionalInterface
    interface CellLookup {
        CellType typeAt(PortalCell cell);
    }
}
