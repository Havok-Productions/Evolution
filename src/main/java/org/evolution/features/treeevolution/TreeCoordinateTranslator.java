package org.evolution.features.treeevolution;

/**
 * ## Single coordinate vocabulary for one tree blueprint.
 *
 * <p>Planners currently describe blocks with world coordinates because their
 * species geometry is anchored by {@link TreeDna}. This translator immediately
 * converts every proposal to a tree-local coordinate and back to one canonical
 * world coordinate. Construction, voxel diagnostics, and conflict reporting
 * therefore agree on the same origin and XYZ interpretation.</p>
 */
final class TreeCoordinateTranslator {
    private final int originX;
    private final int originY;
    private final int originZ;

    TreeCoordinateTranslator(TreeDna dna) {
        originX = dna.baseX();
        originY = dna.baseY();
        originZ = dna.baseZ();
    }

    PlannedTreeBlock canonicalize(PlannedTreeBlock proposed) {
        Coordinate block = relative(
                proposed.x(), proposed.y(), proposed.z());
        Coordinate parent = relative(
                proposed.parentX(), proposed.parentY(),
                proposed.parentZ());
        return proposed.at(
                worldX(block.x()), worldY(block.y()), worldZ(block.z()),
                worldX(parent.x()), worldY(parent.y()), worldZ(parent.z()));
    }

    Coordinate relative(PlannedTreeBlock block) {
        return relative(block.x(), block.y(), block.z());
    }

    String relativeKey(PlannedTreeBlock block) {
        return relative(block).key();
    }

    String originLabel() {
        return originX + "," + originY + "," + originZ;
    }

    boolean roundTrips(PlannedTreeBlock block) {
        Coordinate relative = relative(block);
        return worldX(relative.x()) == block.x()
                && worldY(relative.y()) == block.y()
                && worldZ(relative.z()) == block.z();
    }

    private Coordinate relative(int x, int y, int z) {
        return new Coordinate(
                Math.subtractExact(x, originX),
                Math.subtractExact(y, originY),
                Math.subtractExact(z, originZ));
    }

    private int worldX(int relativeX) {
        return Math.addExact(originX, relativeX);
    }

    private int worldY(int relativeY) {
        return Math.addExact(originY, relativeY);
    }

    private int worldZ(int relativeZ) {
        return Math.addExact(originZ, relativeZ);
    }

    record Coordinate(int x, int y, int z) {
        String key() {
            return x + ":" + y + ":" + z;
        }
    }
}
