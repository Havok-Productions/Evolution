package org.evolution.features.treeevolution.constructor;

public final class TreeConstructionHierarchySmokeTest {
    private static final TreeConstructionHierarchy HIERARCHY =
            new TreeConstructionHierarchy();

    private TreeConstructionHierarchySmokeTest() {
    }

    public static void main(String[] args) {
        // ## This table proves both levels of the constructor hierarchy.
        assertDecision("ownership outranks every action",
                state(false, false, true, true, true,
                        true, true, 1, 1,
                        0.0D, 0.0D, 0.0D, false),
                TreeConstructionPhase.WAIT_FOR_OWNERSHIP,
                TreeConstructionSubrule.ROOTED_TREE_OWNERSHIP, false);
        assertDecision("source snapshot precedes transition",
                state(true, false, true, false, true,
                        true, true, 0, 0,
                        1.0D, 1.0D, 1.0D, false),
                TreeConstructionPhase.WAIT_FOR_SOURCE_SNAPSHOT,
                TreeConstructionSubrule.IMMUTABLE_SOURCE_SNAPSHOT, false);
        assertDecision("repair precedes normal structure",
                state(true, true, false, true, false,
                        false, false, 0, 0,
                        0.0D, 0.0D, 0.0D, false),
                TreeConstructionPhase.REPAIR,
                TreeConstructionSubrule.INTERRUPTED_DAMAGE_REPAIR, false);
        assertDecision("ready source blocker is replaced atomically",
                state(true, true, true, false, true,
                        false, false, 0, 0,
                        0.0D, 0.0D, 0.0D, false),
                TreeConstructionPhase.REPLACE_TRANSITION_BLOCKER,
                TreeConstructionSubrule.READY_SOURCE_LEAF_BLOCKER, false);
        assertDecision("trunk support precedes crown and branches",
                state(true, true, false, false, false,
                        false, false, 0, 0,
                        0.50D, 0.0D, 0.0D, false),
                TreeConstructionPhase.BUILD_SUPPORT,
                TreeConstructionSubrule.SUPPORT_STAGE_TARGET, false);
        assertDecision("exposed upper wood has its own canopy subrule",
                state(true, true, false, false, false,
                        false, false, 1, 0,
                        1.0D, 0.0D, 0.0D, false),
                TreeConstructionPhase.BUILD_CANOPY_SHELL,
                TreeConstructionSubrule.COVER_EXPOSED_SUPPORT, false);
        assertDecision("owned branch envelope is independently traceable",
                state(true, true, false, false, false,
                        false, false, 0, 1,
                        1.0D, 0.0D, 0.30D, false),
                TreeConstructionPhase.BUILD_CANOPY_SHELL,
                TreeConstructionSubrule.OWNED_BRANCH_ENVELOPE, false);
        assertDecision("minimum crown shell is a separate canopy subrule",
                state(true, true, false, false, false,
                        false, false, 0, 0,
                        1.0D, 0.0D, 0.10D, false),
                TreeConstructionPhase.BUILD_CANOPY_SHELL,
                TreeConstructionSubrule.MINIMUM_CROWN_SHELL, false);
        assertDecision("parent-linked branch frame follows shell",
                state(true, true, false, false, false,
                        false, false, 0, 0,
                        1.0D, 0.40D, 0.30D, false),
                TreeConstructionPhase.BUILD_BRANCH_FRAME,
                TreeConstructionSubrule.PARENT_LINKED_BRANCH_FRAME, false);
        assertDecision("canopy fill follows branch frame",
                state(true, true, false, false, false,
                        false, false, 0, 0,
                        1.0D, 1.0D, 0.50D, false),
                TreeConstructionPhase.FILL_CANOPY,
                TreeConstructionSubrule.CANOPY_STAGE_TARGET, false);
        assertDecision("retired crown prunes only after targets",
                state(true, true, true, false, false,
                        true, true, 0, 0,
                        1.0D, 1.0D, 1.0D, false),
                TreeConstructionPhase.PRUNE_RETIRED_CROWN,
                TreeConstructionSubrule.RETIRED_SOURCE_CROWN, false);
        assertDecision("transition finalizes only after final audit passes",
                state(true, true, true, false, false,
                        true, false, 0, 0,
                        1.0D, 1.0D, 1.0D, false),
                TreeConstructionPhase.FINALIZE_TRANSITION,
                TreeConstructionSubrule.TRANSITION_CONTRACT_COMPLETE, true);
        assertDecision("details wait for structural audit",
                state(true, true, false, false, false,
                        false, false, 0, 0,
                        1.0D, 1.0D, 1.0D, true),
                TreeConstructionPhase.BUILD_DETAILS,
                TreeConstructionSubrule.POST_STRUCTURE_DETAIL, true);
        assertDecision("satisfied stage is independently audited",
                state(true, true, false, false, false,
                        false, false, 0, 0,
                        1.0D, 1.0D, 1.0D, false),
                TreeConstructionPhase.COMPLETE,
                TreeConstructionSubrule.STAGE_CONTRACT_COMPLETE, true);

        System.out.println(
                "Tree constructor hierarchy smoke test passed: "
                        + "phase/subrule ownership and final audit agree.");
    }

    private static TreeConstructionState state(
            boolean ownership,
            boolean snapshot,
            boolean transition,
            boolean repair,
            boolean blocker,
            boolean cleanupReady,
            boolean retired,
            int exposedLogs,
            int uncoveredTips,
            double trunk,
            double branch,
            double canopy,
            boolean details
    ) {
        return new TreeConstructionState(
                ownership, snapshot, transition, repair, blocker,
                cleanupReady, retired, exposedLogs, uncoveredTips,
                trunk, branch, canopy,
                0.98D, 1.0D, 0.24D, 0.82D, details);
    }

    private static void assertDecision(
            String name,
            TreeConstructionState state,
            TreeConstructionPhase expectedPhase,
            TreeConstructionSubrule expectedSubrule,
            boolean expectedAuditPass
    ) {
        TreeConstructionDecision actual = HIERARCHY.decide(state);
        if (actual.phase() != expectedPhase
                || actual.subrule() != expectedSubrule
                || actual.finalAudit().passed() != expectedAuditPass) {
            throw new IllegalStateException(name
                    + " expected " + expectedPhase + "/" + expectedSubrule
                    + " audit=" + expectedAuditPass
                    + " but got " + actual.marker()
                    + " " + actual.finalAudit().marker()
                    + " because " + actual.reason());
        }
        if (actual.attachment() != expectedSubrule.attachment()) {
            throw new IllegalStateException(
                    name + " attached the subrule to the wrong executor");
        }
        if (!expectedAuditPass
                && actual.finalAudit().firstFailure() != expectedSubrule) {
            throw new IllegalStateException(name
                    + " final audit expected first failure "
                    + expectedSubrule + " but got "
                    + actual.finalAudit().firstFailure());
        }
    }
}