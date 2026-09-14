package org.evolution.features.treeevolution.constructor;

import java.util.EnumSet;
import java.util.Set;

public final class TreeConstructionHierarchySmokeTest {
    private static final TreeConstructionHierarchy HIERARCHY =
            new TreeConstructionHierarchy();
    private static final Set<TreeConstructionSmokeTag> OBSERVED =
            EnumSet.noneOf(TreeConstructionSmokeTag.class);

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
        assertDecision("ownership role reconciliation precedes pruning",
                stateWithOwnershipRoleMismatch(),
                TreeConstructionPhase.REPAIR,
                TreeConstructionSubrule
                        .OWNERSHIP_ROLE_RECONCILIATION,
                false);
        assertDecision("repair precedes normal structure",
                state(true, true, false, true, false,
                        false, false, 0, 0,
                        0.0D, 0.0D, 0.0D, false),
                TreeConstructionPhase.REPAIR,
                TreeConstructionSubrule.INTERRUPTED_DAMAGE_REPAIR, false);
        assertDecision("detached evolved wood precedes shape work",
                stateWithDisconnected(),
                TreeConstructionPhase.PRUNE_RETIRED_CROWN,
                TreeConstructionSubrule
                        .DISCONNECTED_EVOLVED_STRUCTURE,
                false);
        assertDecision("planned detached wood repairs its parent path",
                stateWithDisconnectedTarget(),
                TreeConstructionPhase.REPAIR,
                TreeConstructionSubrule.DISCONNECTED_TARGET_REPAIR,
                false);
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
        assertDecision("unplanned terminal retirement is independently traceable",
                stateWithCanopyIntegrityIssue(1, 0, 0),
                TreeConstructionPhase.BUILD_CANOPY_SHELL,
                TreeConstructionSubrule
                        .UNPLANNED_BARE_TERMINAL_RETIREMENT,
                false);
        assertDecision("stale envelope retirement is independently traceable",
                stateWithCanopyIntegrityIssue(0, 1, 0),
                TreeConstructionPhase.BUILD_CANOPY_SHELL,
                TreeConstructionSubrule.STALE_ENVELOPE_LEAF_RETIREMENT,
                false);
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
        assertDecision("target role conflict precedes canopy fill",
                stateWithConflict(),
                TreeConstructionPhase.PRUNE_RETIRED_CROWN,
                TreeConstructionSubrule.CONFLICTING_EVOLVED_TARGET,
                false);
        assertDecision("obsolete evolved structure precedes completion",
                stateWithObsolete(),
                TreeConstructionPhase.PRUNE_RETIRED_CROWN,
                TreeConstructionSubrule.OBSOLETE_EVOLVED_STRUCTURE, false);
        assertDecision("canopy fill follows branch frame",
                state(true, true, false, false, false,
                        false, false, 0, 0,
                        1.0D, 1.0D, 0.50D, false),
                TreeConstructionPhase.FILL_CANOPY,
                TreeConstructionSubrule.CANOPY_STAGE_TARGET, false);
        assertDecision("ready cleanup interleaves with remaining canopy fill",
                state(true, true, true, false, false,
                        true, true, 0, 0,
                        1.0D, 1.0D, 0.75D, false),
                TreeConstructionPhase.PRUNE_RETIRED_CROWN,
                TreeConstructionSubrule.RETIRED_SOURCE_CROWN, false);
        assertDecision("retired crown continues after targets",
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

        if (!OBSERVED.equals(EnumSet.allOf(
                TreeConstructionSmokeTag.class))) {
            throw new IllegalStateException(
                    "not every production smoke tag has a hierarchy case: "
                            + OBSERVED);
        }

        System.out.println(
                "Tree constructor hierarchy smoke test passed: "
                        + "phase/subrule/smoke-tag ownership and final audit agree.");
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
                ownership, snapshot, transition, false, repair, blocker,
                false, false, false, cleanupReady, false, retired,
                !transition || !retired,
                exposedLogs, uncoveredTips,
                0, 0, uncoveredTips,
                trunk, branch, canopy,
                0.98D, 1.0D, 0.24D, 0.82D, details);
    }

    private static TreeConstructionState stateWithObsolete() {
        return new TreeConstructionState(
                true, true, false, false, false, false,
                false, false, false, true, true, false, true,
                0, 0,
                0, 0, 0,
                1.0D, 1.0D, 0.82D,
                0.98D, 1.0D, 0.24D, 0.82D, false);
    }

    private static TreeConstructionState stateWithCanopyIntegrityIssue(
            int unplannedTerminals,
            int staleEnvelopeLeaves,
            int plannedEnvelopeIssues
    ) {
        int total = unplannedTerminals + staleEnvelopeLeaves
                + plannedEnvelopeIssues;
        return new TreeConstructionState(
                true, true, false, false, false, false,
                false, false, false, false, false, false, true,
                0, total, unplannedTerminals, staleEnvelopeLeaves,
                plannedEnvelopeIssues,
                1.0D, 1.0D, 0.30D,
                0.98D, 1.0D, 0.24D, 0.82D, false);
    }

    private static TreeConstructionState stateWithDisconnected() {
        return new TreeConstructionState(
                true, true, false, false, true, false,
                true, false, false, false, false, false, true,
                0, 0,
                0, 0, 0,
                0.50D, 0.0D, 0.0D,
                0.98D, 1.0D, 0.24D, 0.82D, false);
    }

    private static TreeConstructionState
            stateWithDisconnectedTarget() {
        return new TreeConstructionState(
                true, true, false, false, true, false,
                false, true, false, false, false, false, true,
                0, 0,
                0, 0, 0,
                0.50D, 0.0D, 0.0D,
                0.98D, 1.0D, 0.24D, 0.82D, false);
    }

    private static TreeConstructionState stateWithConflict() {
        return new TreeConstructionState(
                true, true, false, false, false, false,
                false, false, true, false, true, false, true,
                0, 0,
                0, 0, 0,
                1.0D, 1.0D, 0.50D,
                0.98D, 1.0D, 0.24D, 0.82D, false);
    }

    private static TreeConstructionState
            stateWithOwnershipRoleMismatch() {
        return new TreeConstructionState(
                true, true, false, true, false, false,
                false, false, true, true, true, false, true,
                0, 0,
                0, 0, 0,
                1.0D, 1.0D, 1.0D,
                0.98D, 1.0D, 0.24D, 0.82D, false);
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
        if (actual.smokeTag() != expectedSubrule.smokeTag()
                || actual.smokeTag().phase() != expectedPhase) {
            throw new IllegalStateException(
                    name + " did not map to its production smoke tag");
        }
        OBSERVED.add(actual.smokeTag());
        if (!expectedAuditPass
                && actual.finalAudit().firstFailure() != expectedSubrule) {
            throw new IllegalStateException(name
                    + " final audit expected first failure "
                    + expectedSubrule + " but got "
                    + actual.finalAudit().firstFailure());
        }
    }
}
