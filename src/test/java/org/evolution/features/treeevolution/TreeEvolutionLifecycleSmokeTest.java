package org.evolution.features.treeevolution;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionHierarchy;
import org.evolution.features.treeevolution.constructor.TreeConstructionPhase;
import org.evolution.features.treeevolution.constructor.TreeConstructionState;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;

/**
 * ## Integrated production-aware tree lifecycle regression.
 */
public final class TreeEvolutionLifecycleSmokeTest {
    private static final int VARIANTS_PER_SHAPE = 3;
    private static final Path OUT = Path.of(
            "target", "tree-evolution-smoke");
    private static final TreeConstructionHierarchy HIERARCHY =
            new TreeConstructionHierarchy();
    private static final TreeShapeEngine SHAPE_ENGINE =
            new TreeShapeEngine();

    private TreeEvolutionLifecycleSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        TreeMaturityStage productionMaximum = productionMaximumStage();
        List<String> trace = new ArrayList<>();
        trace.add("## Integrated lifecycle trace generated from real plans, DNA ownership, persistence, and constructor contracts.");
        trace.add("species,stage,variant,step,phase,subrule,audit,auditFirstFailure,wood,leaves,branches,floatingWood,unanchoredSegments,coveredTips,topCovered,prunedBranches,normalEnough");

        int scenarios = 0;
        int productionScenarios = 0;
        int futureAdvisories = 0;
        List<String> advisoryDetails = new ArrayList<>();
        for (TreeSpecies species : TreeSpecies.values()) {
            for (TreeMaturityStage stage : TreeMaturityStage.values()) {
                for (int variant = 0;
                        variant < VARIANTS_PER_SHAPE; variant++) {
                    TreeDna dna = TreeShapeSmokeTest.sampleDna(
                            species, stage, variant);
                    TreePlan plan = TreeShapeSmokeTest.treeBodyPlan(dna);
                    TreeShapeEngine.ShapeReport shape =
                            SHAPE_ENGINE.analyze(plan, dna);
                    boolean production = stage.ordinal()
                            <= productionMaximum.ordinal();
                    verifyPlanContract(dna, plan, shape, production);
                    verifyOwnershipLifecycle(dna, plan);
                    appendHierarchyLifecycle(
                            trace, dna, plan, shape, variant);
                    scenarios++;
                    if (production) {
                        productionScenarios++;
                    } else if (!shape.normalEnough()) {
                        futureAdvisories++;
                        advisoryDetails.add(
                                species.id() + "/" + stage
                                        + "/variant=" + variant
                                        + "/floating=" + shape.floatingWood()
                                        + "/unanchored="
                                        + shape.unanchoredBranchSegments()
                                        + "/covered="
                                        + shape.coveredBranchTips() + "/"
                                        + shape.branchTips()
                                        + "/top=" + shape.topCovered());
                    }
                }
            }
        }

        Files.writeString(
                OUT.resolve("lifecycle-trace.csv"),
                String.join(System.lineSeparator(), trace)
                        + System.lineSeparator());
        String summary = "## Evolution integrated tree smoke summary"
                + System.lineSeparator()
                + "production-maximum-stage=" + productionMaximum
                + System.lineSeparator()
                + "species=" + TreeSpecies.values().length
                + System.lineSeparator()
                + "stages=" + TreeMaturityStage.values().length
                + System.lineSeparator()
                + "variants-per-shape=" + VARIANTS_PER_SHAPE
                + System.lineSeparator()
                + "scenarios=" + scenarios
                + System.lineSeparator()
                + "production-scenarios=" + productionScenarios
                + System.lineSeparator()
                + "future-stage-advisories=" + futureAdvisories
                + System.lineSeparator()
                + "future-stage-advisory-details="
                + (advisoryDetails.isEmpty()
                        ? "none" : String.join(";", advisoryDetails))
                + System.lineSeparator()
                + "lifecycle-steps=" + (trace.size() - 2)
                + System.lineSeparator()
                + "result=PASS"
                + System.lineSeparator();
        Files.writeString(OUT.resolve("summary.txt"), summary);
        System.out.print(summary);
        System.out.println("trace="
                + OUT.resolve("lifecycle-trace.csv").toAbsolutePath());
    }

    private static void verifyPlanContract(
            TreeDna dna,
            TreePlan plan,
            TreeShapeEngine.ShapeReport shape,
            boolean production
    ) {
        require(shape.wood() > 0 && shape.leaves() > 0,
                label(dna) + " produced an empty structural role");
        require(shape.branchTips() > 0,
                label(dna) + " lost all branch-tip variation");
        require(shape.floatingWood() == 0,
                label(dna) + " contains floating wood");
        require(shape.coveredBranchTips() == shape.branchTips(),
                label(dna) + " has uncovered planned branch tips");
        require(shape.topCovered(),
                label(dna) + " leaves its upper support exposed");
        require(plan.prunedBranchCount() >= 0,
                label(dna) + " returned an invalid prune count");
        if (production) {
            require(shape.normalEnough(),
                    label(dna) + " reachable production target is not normal"
                            + " unanchored="
                            + shape.unanchoredBranchSegments()
                            + " leafWood=" + shape.leafWoodRatio());
            require(shape.unanchoredBranchSegments() == 0,
                    label(dna)
                            + " reachable production target has unsupported branch segments");
        }
    }

    private static void verifyOwnershipLifecycle(
            TreeDna dna, TreePlan plan) {
        PlannedTreeBlock sourceLog = plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.TRUNK)
                .findFirst()
                .orElseThrow();
        PlannedTreeBlock sourceLeaf = plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.CANOPY)
                .findFirst()
                .orElseThrow();
        PlannedTreeBlock evolvedWood = plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.BRANCH)
                .findFirst()
                .orElse(sourceLog);
        String logKey = worldKey(dna, sourceLog);
        String leafKey = worldKey(dna, sourceLeaf);
        String evolvedWoodKey = worldKey(dna, evolvedWood);
        String retiredLeafKey = dna.worldId() + ":"
                + (dna.baseX() + 19) + ":" + (dna.baseY() + 2)
                + ":" + (dna.baseZ() + 19);

        dna.captureOriginalShape(
                List.of(logKey), List.of(leafKey, retiredLeafKey));
        require(dna.requiresEvolvedLeafOwnership(),
                label(dna) + " did not activate ownership audit");
        require(!dna.countsAsEvolvedLeaf(leafKey),
                label(dna) + " accepted an untouched source leaf");
        require(dna.markEvolvedLeaf(leafKey)
                        && dna.countsAsEvolvedLeaf(leafKey),
                label(dna) + " failed to reform its source leaf");
        require(dna.markEvolvedBlock(
                        evolvedWoodKey, evolvedWood.role()),
                label(dna) + " failed to record evolved wood");
        require(dna.markOriginalShapeLeafRetired(retiredLeafKey),
                label(dna) + " failed to retire a source leaf");
        require(!dna.markOriginalShapeLeafRetired(retiredLeafKey),
                label(dna) + " retired the same source leaf twice");

        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("tree");
        dna.writeTo(section);
        TreeDna restored = TreeDna.from(
                yaml.getConfigurationSection("tree"));
        require(restored != null,
                label(dna) + " failed DNA reload");
        require(restored.countsAsEvolvedLeaf(leafKey),
                label(dna) + " lost evolved leaf ownership on reload");
        require(restored.evolvedLogCount() > 0,
                label(dna) + " lost evolved wood ownership on reload");
        require(restored.retiredOriginalShapeLeaves()
                        .contains(retiredLeafKey),
                label(dna) + " lost retired-leaf history on reload");

        restored.completeStageTransition();
        require(!restored.hasOriginalShapeSnapshot(),
                label(dna) + " retained source snapshot after finalization");
        require(restored.countsAsEvolvedLeaf(leafKey),
                label(dna) + " lost completed-stage audit evidence");
        restored.captureOriginalShape(
                List.of(logKey), List.of(leafKey));
        require(!restored.countsAsEvolvedLeaf(leafKey),
                label(dna) + " failed to reset ownership for the next stage");
    }

    private static void appendHierarchyLifecycle(
            List<String> trace,
            TreeDna dna,
            TreePlan plan,
            TreeShapeEngine.ShapeReport shape,
            int variant
    ) {
        double shell = canopyShellTarget(dna.maturityStage());
        for (LifecycleStep step : lifecycle(shell)) {
            TreeConstructionDecision decision =
                    HIERARCHY.decide(step.state());
            require(decision.phase() == step.phase(),
                    label(dna) + " " + step.name()
                            + " selected " + decision.phase()
                            + " instead of " + step.phase());
            require(decision.subrule() == step.subrule(),
                    label(dna) + " " + step.name()
                            + " selected " + decision.subrule()
                            + " instead of " + step.subrule());
            require(decision.attachment()
                            == step.subrule().attachment(),
                    label(dna) + " " + step.name()
                            + " escaped its phase owner");
            require(decision.finalAudit().passed()
                            == step.auditPass(),
                    label(dna) + " " + step.name()
                            + " final audit disagreed with routing");
            if (!step.auditPass()) {
                require(decision.finalAudit().firstFailure()
                                == step.subrule(),
                        label(dna) + " " + step.name()
                                + " audit first failure was "
                                + decision.finalAudit().firstFailure());
            }
            trace.add(String.join(",",
                    dna.species().id(),
                    dna.maturityStage().name(),
                    String.valueOf(variant),
                    step.name(),
                    decision.phase().name(),
                    decision.subrule().name(),
                    decision.finalAudit().passed() ? "PASS" : "BLOCKED",
                    String.valueOf(
                            decision.finalAudit().firstFailure()),
                    String.valueOf(shape.wood()),
                    String.valueOf(shape.leaves()),
                    String.valueOf(shape.branches()),
                    String.valueOf(shape.floatingWood()),
                    String.valueOf(shape.unanchoredBranchSegments()),
                    shape.coveredBranchTips() + "/" + shape.branchTips(),
                    String.valueOf(shape.topCovered()),
                    String.valueOf(plan.prunedBranchCount()),
                    String.valueOf(shape.normalEnough())));
        }
    }

    private static List<LifecycleStep> lifecycle(double shell) {
        return List.of(
                step("ownership", state(false, false, true,
                        false, false, false, false, 0, 0,
                        0.0D, 0.0D, 0.0D, shell, false),
                        TreeConstructionPhase.WAIT_FOR_OWNERSHIP,
                        TreeConstructionSubrule.ROOTED_TREE_OWNERSHIP,
                        false),
                step("source-snapshot", state(true, false, true,
                        false, false, false, false, 0, 0,
                        0.0D, 0.0D, 0.0D, shell, false),
                        TreeConstructionPhase.WAIT_FOR_SOURCE_SNAPSHOT,
                        TreeConstructionSubrule.IMMUTABLE_SOURCE_SNAPSHOT,
                        false),
                step("repair", state(true, true, true,
                        true, false, false, false, 0, 0,
                        0.0D, 0.0D, 0.0D, shell, false),
                        TreeConstructionPhase.REPAIR,
                        TreeConstructionSubrule.INTERRUPTED_DAMAGE_REPAIR,
                        false),
                step("transition-blocker", state(true, true, true,
                        false, true, false, false, 0, 0,
                        0.0D, 0.0D, 0.0D, shell, false),
                        TreeConstructionPhase.REPLACE_TRANSITION_BLOCKER,
                        TreeConstructionSubrule.READY_SOURCE_LEAF_BLOCKER,
                        false),
                step("support", state(true, true, true,
                        false, false, false, false, 0, 0,
                        0.50D, 0.0D, 0.0D, shell, false),
                        TreeConstructionPhase.BUILD_SUPPORT,
                        TreeConstructionSubrule.SUPPORT_STAGE_TARGET,
                        false),
                step("exposed-support", state(true, true, true,
                        false, false, false, false, 1, 0,
                        1.0D, 0.0D, shell, shell, false),
                        TreeConstructionPhase.BUILD_CANOPY_SHELL,
                        TreeConstructionSubrule.COVER_EXPOSED_SUPPORT,
                        false),
                step("minimum-shell", state(true, true, true,
                        false, false, false, false, 0, 0,
                        1.0D, 0.0D, 0.0D, shell, false),
                        TreeConstructionPhase.BUILD_CANOPY_SHELL,
                        TreeConstructionSubrule.MINIMUM_CROWN_SHELL,
                        false),
                step("branch-frame", state(true, true, true,
                        false, false, false, false, 0, 0,
                        1.0D, 0.50D, shell, shell, false),
                        TreeConstructionPhase.BUILD_BRANCH_FRAME,
                        TreeConstructionSubrule.PARENT_LINKED_BRANCH_FRAME,
                        false),
                step("owned-envelope", state(true, true, true,
                        false, false, false, false, 0, 1,
                        1.0D, 1.0D, shell, shell, false),
                        TreeConstructionPhase.BUILD_CANOPY_SHELL,
                        TreeConstructionSubrule.OWNED_BRANCH_ENVELOPE,
                        false),
                step("canopy-fill", state(true, true, true,
                        false, false, false, false, 0, 0,
                        1.0D, 1.0D, Math.max(shell, 0.50D),
                        shell, false),
                        TreeConstructionPhase.FILL_CANOPY,
                        TreeConstructionSubrule.CANOPY_STAGE_TARGET,
                        false),
                step("retired-crown", state(true, true, true,
                        false, false, true, true, 0, 0,
                        1.0D, 1.0D, 1.0D, shell, false),
                        TreeConstructionPhase.PRUNE_RETIRED_CROWN,
                        TreeConstructionSubrule.RETIRED_SOURCE_CROWN,
                        false),
                step("finalize", state(true, true, true,
                        false, false, true, false, 0, 0,
                        1.0D, 1.0D, 1.0D, shell, false),
                        TreeConstructionPhase.FINALIZE_TRANSITION,
                        TreeConstructionSubrule.TRANSITION_CONTRACT_COMPLETE,
                        true),
                step("details", state(true, true, false,
                        false, false, false, false, 0, 0,
                        1.0D, 1.0D, 1.0D, shell, true),
                        TreeConstructionPhase.BUILD_DETAILS,
                        TreeConstructionSubrule.POST_STRUCTURE_DETAIL,
                        true),
                step("complete", state(true, true, false,
                        false, false, false, false, 0, 0,
                        1.0D, 1.0D, 1.0D, shell, false),
                        TreeConstructionPhase.COMPLETE,
                        TreeConstructionSubrule.STAGE_CONTRACT_COMPLETE,
                        true));
    }

    private static TreeConstructionState state(
            boolean ownership,
            boolean snapshot,
            boolean transition,
            boolean repair,
            boolean blocker,
            boolean cleanupReady,
            boolean retired,
            int exposed,
            int uncovered,
            double trunk,
            double branch,
            double canopy,
            double shell,
            boolean details
    ) {
        return new TreeConstructionState(
                ownership, snapshot, transition, repair, blocker,
                cleanupReady, retired, exposed, uncovered,
                trunk, branch, canopy,
                1.0D, 1.0D, shell, 1.0D, details);
    }

    private static LifecycleStep step(
            String name,
            TreeConstructionState state,
            TreeConstructionPhase phase,
            TreeConstructionSubrule subrule,
            boolean auditPass
    ) {
        return new LifecycleStep(
                name, state, phase, subrule, auditPass);
    }

    private static TreeMaturityStage productionMaximumStage() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/config.yml"));
        String configured = config.getString(
                "tree-evolution.maximum-stage", "MEDIUM");
        try {
            return TreeMaturityStage.valueOf(
                    configured.trim().toUpperCase(
                            java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TreeMaturityStage.MEDIUM;
        }
    }

    private static double canopyShellTarget(TreeMaturityStage stage) {
        return switch (stage) {
            case SMALL -> 0.18D;
            case MEDIUM -> 0.24D;
            case MATURE -> 0.30D;
            case ANCIENT -> 0.32D;
        };
    }

    private static String worldKey(
            TreeDna dna, PlannedTreeBlock block) {
        return dna.worldId() + ":" + block.x() + ":" + block.y()
                + ":" + block.z();
    }

    private static String label(TreeDna dna) {
        return dna.species().id() + "/" + dna.maturityStage()
                + "/seed=" + dna.seed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record LifecycleStep(
            String name,
            TreeConstructionState state,
            TreeConstructionPhase phase,
            TreeConstructionSubrule subrule,
            boolean auditPass
    ) {
    }
}