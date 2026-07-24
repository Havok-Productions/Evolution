package org.slowtrees.treeevolution;

import java.util.HashSet;
import java.util.List;

public final class TreeSeedlingSearchPolicySmokeTest {
    private TreeSeedlingSearchPolicySmokeTest() {
    }

    public static void main(String[] args) {
        List<TreeSeedlingSearchPolicy.Offset> offsets =
                TreeSeedlingSearchPolicy.sampleRing(4, 12, 32, 91L);
        require(offsets.size() == 32,
                "expanded search must return the configured useful attempts");
        require(new HashSet<>(offsets).size() == offsets.size(),
                "expanded search must not retry duplicate coordinates");
        for (TreeSeedlingSearchPolicy.Offset offset : offsets) {
            int distanceSquared =
                    (offset.x() * offset.x()) + (offset.z() * offset.z());
            require(distanceSquared >= 16 && distanceSquared <= 144,
                    "every sampled coordinate must be inside the valid ring");
        }
        require(offsets.equals(
                        TreeSeedlingSearchPolicy.sampleRing(4, 12, 32, 91L)),
                "search order must remain deterministic for diagnostics");

        System.out.println("Tree seedling search policy smoke test passed: "
                + "attempts=32 unique=true ring=4..12");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
