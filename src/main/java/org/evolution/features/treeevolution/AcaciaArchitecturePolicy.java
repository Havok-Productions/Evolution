package org.evolution.features.treeevolution;

/**
 * Owns the acacia silhouette contract shared by branch and canopy planning.
 *
 * <p>## Keeping these limits in one policy prevents an oversized branch budget
 * from fighting a compact umbrella canopy and producing disconnected shelves.</p>
 */
final class AcaciaArchitecturePolicy {
    private AcaciaArchitecturePolicy() {
    }

    static int forkCount(TreeDna dna) {
        int stageForks = switch (dna.maturityStage()) {
            case SMALL -> 2;
            case MEDIUM -> 3;
            case MATURE -> 4;
            case ANCIENT -> 5;
        };
        return switch (dna.variant()) {
            case ACACIA_SINGLE_FORK -> Math.min(2, stageForks);
            case ACACIA_MULTI_FORK -> stageForks;
            case ACACIA_WINDSWEPT -> Math.max(1, stageForks - 1);
            default -> stageForks;
        };
    }

    static int branchLength(TreeDna dna, int branchIndex) {
        int base = switch (dna.maturityStage()) {
            case SMALL -> 2;
            case MEDIUM -> 3;
            case MATURE -> 4;
            case ANCIENT -> 5;
        };
        if (dna.variant() == TreeVariant.ACACIA_WINDSWEPT) {
            base++;
        } else if (dna.variant()
                == TreeVariant.ACACIA_SINGLE_FORK) {
            base = Math.max(2, base - 1);
        }
        return base + (branchIndex % 2);
    }

    static int maximumBranchLength(TreeDna dna) {
        return branchLength(dna, 1);
    }

    static int bendAfter(int length) {
        return Math.max(1, length - 2);
    }

    static boolean risesAt(int step, int length) {
        // ## A single late lift creates a readable fork without a staircase arm.
        return step == Math.max(2, length - 1);
    }

    static int centerLobeRadius(TreeDna dna) {
        int radius = switch (dna.maturityStage()) {
            case SMALL -> 3;
            case MEDIUM -> 4;
            case MATURE, ANCIENT -> 4;
        };
        return dna.variant() == TreeVariant.ACACIA_MULTI_FORK
                ? radius + 1 : radius;
    }

    static int branchLobeRadius(TreeDna dna) {
        int radius = switch (dna.maturityStage()) {
            case SMALL -> 2;
            case MEDIUM, MATURE -> 3;
            case ANCIENT -> 4;
        };
        if (dna.variant() == TreeVariant.ACACIA_SINGLE_FORK
                && dna.maturityStage().ordinal()
                        >= TreeMaturityStage.MEDIUM.ordinal()) {
            // ## A single fork has only two crown anchors; each needs enough
            // umbrella mass to read as an acacia instead of a bare pole.
            radius++;
        }
        return dna.variant() == TreeVariant.ACACIA_MULTI_FORK
                ? Math.min(4, radius + 1) : radius;
    }

    static int minimumTipLeafContacts() {
        return 4;
    }
}
