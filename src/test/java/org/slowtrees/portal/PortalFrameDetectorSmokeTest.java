package org.slowtrees.portal;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class PortalFrameDetectorSmokeTest {
    private PortalFrameDetectorSmokeTest() {
    }

    public static void main(String[] args) {
        Set<PortalCell> archInterior = new HashSet<>();
        addRow(archInterior, PortalPlane.X, 0, 1, -1, 1);
        addRow(archInterior, PortalPlane.X, 0, 2, -2, 2);
        addRow(archInterior, PortalPlane.X, 0, 3, -2, 2);
        addRow(archInterior, PortalPlane.X, 0, 4, -1, 1);
        Set<PortalCell> archFrame = frameAround(
                archInterior, PortalPlane.X);

        Optional<PortalShapePlan> arch = detect(
                new PortalCell(0, 1, 0),
                PortalPlane.X,
                archInterior,
                archFrame,
                Set.of());
        require(arch.isPresent(), "an enclosed arch must be accepted");
        require(arch.get().interior().equals(archInterior),
                "the exact irregular interior must be preserved");

        Set<PortalCell> openFrame = new HashSet<>(archFrame);
        openFrame.remove(new PortalCell(0, 5, 0));
        require(detect(
                        new PortalCell(0, 1, 0),
                        PortalPlane.X,
                        archInterior,
                        openFrame,
                        Set.of()).isEmpty(),
                "an open frame must be rejected");

        require(detect(
                        new PortalCell(0, 1, 0),
                        PortalPlane.X,
                        archInterior,
                        archFrame,
                        Set.of(new PortalCell(0, 2, 0))).isEmpty(),
                "a blocked interior must be rejected");

        Set<PortalCell> zInterior = new HashSet<>();
        addRow(zInterior, PortalPlane.Z, 5, 1, -1, 1);
        addRow(zInterior, PortalPlane.Z, 5, 2, -1, 1);
        addRow(zInterior, PortalPlane.Z, 5, 3, -1, 1);
        Set<PortalCell> zFrame = frameAround(
                zInterior, PortalPlane.Z);
        Optional<PortalShapePlan> zPortal = detect(
                new PortalCell(5, 1, 0),
                PortalPlane.Z,
                zInterior,
                zFrame,
                Set.of());
        require(zPortal.isPresent()
                        && zPortal.get().plane() == PortalPlane.Z,
                "the detector must support both vertical axes");

        System.out.println("Portal frame detector smoke test passed: "
                + "arch=true open-rejected=true blocked-rejected=true "
                + "both-axes=true");
    }

    private static Optional<PortalShapePlan> detect(
            PortalCell start,
            PortalPlane plane,
            Set<PortalCell> interior,
            Set<PortalCell> frame,
            Set<PortalCell> blocked
    ) {
        return PortalFrameDetector.detect(
                start,
                plane,
                6,
                400,
                21,
                21,
                cell -> {
                    if (frame.contains(cell)) {
                        return PortalFrameDetector.CellType.FRAME;
                    }
                    if (blocked.contains(cell)) {
                        return PortalFrameDetector.CellType.BLOCKED;
                    }
                    return PortalFrameDetector.CellType.INTERIOR;
                });
    }

    private static void addRow(
            Set<PortalCell> cells,
            PortalPlane plane,
            int fixed,
            int y,
            int minimum,
            int maximum
    ) {
        for (int horizontal = minimum;
                horizontal <= maximum;
                horizontal++) {
            cells.add(plane == PortalPlane.X
                    ? new PortalCell(horizontal, y, fixed)
                    : new PortalCell(fixed, y, horizontal));
        }
    }

    private static Set<PortalCell> frameAround(
            Set<PortalCell> interior,
            PortalPlane plane
    ) {
        Set<PortalCell> frame = new HashSet<>();
        for (PortalCell cell : interior) {
            for (PortalCell neighbor : Set.of(
                    cell.horizontal(-1, plane),
                    cell.horizontal(1, plane),
                    cell.vertical(-1),
                    cell.vertical(1))) {
                if (!interior.contains(neighbor)) {
                    frame.add(neighbor);
                }
            }
        }
        return Set.copyOf(frame);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
