package org.evolution.features.treeevolution;

import java.util.HashSet;
import java.util.Set;

/**
 * ## Proves that support follows a complete leaf chain without a distance cap.
 */
public final class TreeLeafConnectivityPolicySmokeTest {
    private TreeLeafConnectivityPolicySmokeTest() {
    }

    public static void main(String[] args) {
        Set<String> leaves = new HashSet<>();
        for (int x = 1; x <= 12; x++) {
            leaves.add(x + ":70:0");
        }
        leaves.add("30:70:0");
        leaves.add("31:70:0");

        Set<String> connected = TreeLeafConnectivityPolicy.connectedLeaves(
                leaves, Set.of("0:70:0"));
        require(connected.size() == 12,
                "all twelve indirectly connected leaves should survive");
        require(!connected.contains("30:70:0")
                        && !connected.contains("31:70:0"),
                "a detached leaf component should be released");
        System.out.println("Tree leaf connectivity smoke test passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
