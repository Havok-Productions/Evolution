package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.BlockFace;

record TreeBranchPlan(
        int id,
        BlockFace direction,
        int anchorX,
        int anchorY,
        int anchorZ,
        List<BranchSegment> segments
) {
    TreeBranchPlan {
        segments = List.copyOf(segments);
    }

    BranchTip tip() {
        if (segments.isEmpty()) {
            return new BranchTip(anchorX, anchorY, anchorZ, id);
        }
        BranchSegment segment = segments.get(segments.size() - 1);
        return new BranchTip(segment.x(), segment.y(), segment.z(), id);
    }

    static Builder builder(int id, BlockFace direction, int anchorX, int anchorY, int anchorZ) {
        return new Builder(id, direction, anchorX, anchorY, anchorZ);
    }

    record BranchSegment(
            int branchId,
            int step,
            int x,
            int y,
            int z,
            int parentX,
            int parentY,
            int parentZ,
            boolean tip
    ) {
    }

    record BranchTip(int x, int y, int z, int branchId) {
    }

    static final class Builder {
        private final int id;
        private final BlockFace direction;
        private final int anchorX;
        private final int anchorY;
        private final int anchorZ;
        private final List<BranchSegment> segments = new ArrayList<>();

        private Builder(int id, BlockFace direction, int anchorX, int anchorY, int anchorZ) {
            this.id = id;
            this.direction = direction;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
        }

        void add(int step, int x, int y, int z, int parentX, int parentY, int parentZ) {
            segments.add(new BranchSegment(id, step, x, y, z, parentX, parentY, parentZ, false));
        }

        TreeBranchPlan build() {
            List<BranchSegment> finalized = new ArrayList<>(segments.size());
            for (int index = 0; index < segments.size(); index++) {
                BranchSegment segment = segments.get(index);
                finalized.add(new BranchSegment(
                        segment.branchId(),
                        segment.step(),
                        segment.x(),
                        segment.y(),
                        segment.z(),
                        segment.parentX(),
                        segment.parentY(),
                        segment.parentZ(),
                        index == segments.size() - 1
                ));
            }
            return new TreeBranchPlan(id, direction, anchorX, anchorY, anchorZ, finalized);
        }
    }
}
