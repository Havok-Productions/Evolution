package org.evolution.features.treeevolution;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Cell;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Ownership;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSmokeTag;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionResult;

/**
 * ## Coordinates lifecycle scenarios around the production hierarchy.
 */
final class TreeConstructionReplayHarness {
    private final TreeConstructorCore core = new TreeConstructorCore();
    private final TreePlan plan;
    private final List<PlannedTreeBlock> targets;
    private final TreeConstructionReplayInspector inspector;
    private final TreeConstructionReplayRecorder recorder;
    private final TreeConstructionReplayOperations operations;
    private final Set<String> allowedMultiMutationKeys = new HashSet<>();
    private final Set<String> protectedTargetKeys;
    private final String scenario;
    private TreeDna dna;
    private TreeConstructionReplayWorld world;
    private TreeConstructionDecision currentDecision;
    private int steps;
    private boolean paused;
    private boolean restarted;
    private boolean damageInjected;
    private boolean transitionFinalized;
    private int intermediateAudits;
    private final int inheritedDisconnectedWood;
    private final int inheritedOwnershipRoleMismatches;
    private final Map<String, Cell> initialCells;
    private final TreeReplayProgress initialProgress;
    private final TreeSimulatedMinecraftEnvironment environment;

    TreeConstructionReplayHarness(
            String scenario,
            TreeDna dna,
            boolean injectObsoleteStructure
    ) {
        this(scenario, dna, injectObsoleteStructure,
                TreeSimulatedMinecraftEnvironment.permissive(),
                TreeCapturedEnvironmentFixture.empty());
    }

    TreeConstructionReplayHarness(
            String scenario,
            TreeDna dna,
            boolean injectObsoleteStructure,
            TreeSimulatedMinecraftEnvironment environment
    ) {
        this(scenario, dna, injectObsoleteStructure, environment,
                TreeCapturedEnvironmentFixture.empty());
    }

    TreeConstructionReplayHarness(
            String scenario,
            TreeDna dna,
            boolean injectObsoleteStructure,
            TreeSimulatedMinecraftEnvironment environment,
            TreeCapturedEnvironmentFixture capturedEnvironment
    ) {
        this.scenario = scenario;
        this.dna = dna;
        this.environment = environment;
        this.plan = TreeShapeSmokeTest.treeBodyPlan(dna);
        List<PlannedTreeBlock> rawTargets = plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.TRUNK
                        || block.role() == TreeBlockRole.BRANCH
                        || block.role() == TreeBlockRole.CANOPY)
                // ## Live placement skips canopy cells occupied by source wood.
                // Evolved wood is deliberately retained as a canopy target:
                // the hierarchy must explicitly retire that stale ownership
                // before the planned leaf can be placed.
                .filter(block -> block.role() != TreeBlockRole.CANOPY
                        || (!dna.originalShapeLogs().contains(
                                    worldKey(block.key()))))
                .toList();
        this.world = new TreeConstructionReplayWorld(environment);
        TreeConstructionReplaySeeder.seed(
                dna, plan, rawTargets, world, injectObsoleteStructure,
                capturedEnvironment);
        this.protectedTargetKeys = rawTargets.stream()
                .filter(block -> {
                    Cell live = world.cell(block.key());
                    return live != null
                            && live.ownership() == Ownership.NEIGHBOR;
                })
                .map(PlannedTreeBlock::key)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.targets = rawTargets.stream()
                // ## Captured neighboring-tree voxels mirror production's
                // immutable obstacle rule. They remain in the world but are
                // outside this tree's effective completion target.
                .filter(block -> {
                    Cell live = world.cell(block.key());
                    return live == null
                            || live.ownership() != Ownership.NEIGHBOR;
                })
                .toList();
        this.inspector = new TreeConstructionReplayInspector(plan, targets);
        this.recorder = new TreeConstructionReplayRecorder(
                scenario, plan, inspector);
        List<PlannedTreeBlock> canopyTargets = targets.stream()
                .filter(block -> block.role() == TreeBlockRole.CANOPY)
                .toList();
        this.operations = new TreeConstructionReplayOperations(
                scenario, plan, targets, canopyTargets, inspector,
                allowedMultiMutationKeys,
                new TreeConstructionReplayOperations.State() {
                    @Override
                    public TreeDna dna() {
                        return TreeConstructionReplayHarness.this.dna;
                    }

                    @Override
                    public TreeConstructionReplayWorld world() {
                        return TreeConstructionReplayHarness.this.world;
                    }

                    @Override
                    public TreeConstructionDecision decision() {
                        return currentDecision;
                    }

                    @Override
                    public void markTransitionFinalized() {
                        transitionFinalized = true;
                    }
        });
        this.initialCells = world.cells();
        this.initialProgress = progress();
        // ## Current-only captures have already completed their prior
        // transition. They still need to finish the active target, but there
        // is no old source snapshot for FINALIZE_TRANSITION to retire.
        this.transitionFinalized = !dna.hasOriginalShapeSnapshot();
        TreeReplayInvariantAudit.Report inheritedInvariant =
                TreeReplayInvariantAudit.inspect(dna, plan, world);
        this.inheritedDisconnectedWood =
                inheritedInvariant.disconnectedWood();
        this.inheritedOwnershipRoleMismatches =
                inheritedInvariant.ownershipRoleMismatches();
        recorder.capture(
                "00-initial", "initial source and target",
                false, dna, world);
    }

    ReplayResult run() {
        TreeReplayProgress previous = progress();
        int previousRoleMismatches =
                inheritedOwnershipRoleMismatches;
        int actionLimit = Math.max(600, plan.size() * 3 + 300);
        int cycleLimit = actionLimit + 2_000;
        int cycles = 0;
        while (cycles < cycleLimit) {
            TreeSimulatedMinecraftEnvironment.GateDecision schedule =
                    environment.canSchedule(
                            dna.baseX(), dna.baseY(), dna.baseZ());
            if (!schedule.allowed()) {
                recorder.traceEvent(
                        steps,
                        "WORLD_GATE_" + schedule.gate(),
                        schedule.detail(), progress().csv());
                environment.advanceTick();
                cycles++;
                continue;
            }
            if (!paused && steps >= 9) {
                verifyUnloadPause();
                paused = true;
            }
            if (!restarted && steps >= Math.max(18, plan.size() / 4)) {
                verifyRestartDeterminism();
                restarted = true;
            }
            if (!damageInjected
                    && progress().canopy() >= canopyShellTarget()
                    && progress().branch() > 0.0D
                    && dna.unresolvedOriginalShapeLeafCount() == 0
                    && inspector.disconnectedEvolvedBodyBlock(
                            dna, world).isEmpty()) {
                injectPlayerDamage();
                previous = progress();
            }

            TreeConstructionDecision decision = decision();
            currentDecision = decision;
            recorder.beforeAction(steps, decision, dna, world);
            if (decision.subrule()
                    == TreeConstructionSubrule.STAGE_CONTRACT_COMPLETE) {
                TreeConstructionResult result =
                        core.execute(decision, operations);
                recordAction(decision, result, progress());
                break;
            }

            int physicalBefore = world.physicalMutationCount();
            TreeConstructionResult result =
                    core.execute(decision, operations);
            int physicalDelta =
                    world.physicalMutationCount() - physicalBefore;
            require(result.changedUnits() <= 1,
                    "one hierarchy action changed more than one unit");
            require(physicalDelta <= 1,
                    "one hierarchy action physically changed "
                            + physicalDelta + " blocks");
            require(result.progressed(),
                    "constructor stalled scenario=" + scenario
                            + " at " + decision.marker()
                            + " detail=" + result.detail());
            TreeReplayInvariantAudit.Report invariant =
                    TreeReplayInvariantAudit.inspect(
                            dna, plan, world);
            intermediateAudits++;
            require(invariant.passed(
                            inheritedDisconnectedWood,
                            previousRoleMismatches),
                    "intermediate invariant failed scenario="
                            + scenario + " step=" + steps + " "
                            + invariant.summary()
                            + " decision=" + decision.marker()
                            + " result=" + result.detail()
                            + " lastMutation="
                            + world.mutations().stream()
                                    .reduce((first, second) -> second)
                                    .orElse("none"));
            if (decision.subrule()
                    == TreeConstructionSubrule
                            .OWNERSHIP_ROLE_RECONCILIATION) {
                require(invariant.ownershipRoleMismatches()
                                < previousRoleMismatches,
                        "TREE_09 did not reduce ownership role mismatch "
                                + "scenario=" + scenario
                                + " before=" + previousRoleMismatches
                                + " after="
                                + invariant.ownershipRoleMismatches());
            }
            previousRoleMismatches =
                    invariant.ownershipRoleMismatches();

            TreeReplayProgress after = progress();
            boolean conflictReconciliation = decision.subrule()
                    == TreeConstructionSubrule
                            .CONFLICTING_EVOLVED_TARGET;
            if (!conflictReconciliation) {
                // ## Ordinary construction is monotonic. TREE_59 alone may
                // retire a target-looking dependent before its conflicting
                // articulation parent, after which parent-linked construction
                // restores it exactly once.
                require(after.trunk() + 0.000001D >= previous.trunk(),
                        "trunk progress regressed scenario=" + scenario
                                + " decision=" + decision.marker()
                                + " before=" + previous.csv()
                                + " after=" + after.csv());
                require(after.branch() + 0.000001D >= previous.branch(),
                        "branch progress regressed scenario=" + scenario
                                + " decision=" + decision.marker()
                                + " before=" + previous.csv()
                                + " after=" + after.csv());
                require(after.canopy() + 0.000001D >= previous.canopy(),
                        "canopy progress regressed scenario=" + scenario
                                + " decision=" + decision.marker()
                                + " before=" + previous.csv()
                                + " after=" + after.csv());
            }
            recordAction(decision, result, after);
            if (inspector.disconnectedEvolvedBodyBlock(
                    dna, world).isEmpty()) {
                // ## A live capture may already exceed later percentage
                // milestones while carrying inherited detached receipts.
                // Record the milestone only after early safety reconciliation.
                recorder.captureMilestones(
                        dna, world, after, uncoveredBranchTips());
            }
            previous = after;
            steps++;
            environment.advanceTick();
            cycles++;
        }

        require(cycles < cycleLimit,
                "constructor did not converge within " + cycleLimit
                        + " simulated ticks and " + steps + " actions");
        require(steps < actionLimit,
                "constructor exceeded " + actionLimit + " actions");
        recorder.finishActiveStage(steps, dna, world);
        verifyFinalWorld();
        recorder.capture(
                "99-final", "completed live target", true, dna, world);
        TreeLiveVoxelDiff.Report finalDiff =
                TreeLiveVoxelDiff.compare(
                        dna,
                        // ## Repair may add adaptive canopy targets after the
                        // initial list is frozen. Rebuild from the final plan
                        // while preserving the captured neighbor exclusions.
                        plan.orderedBlocks().stream()
                                .filter(block -> !protectedTargetKeys
                                        .contains(block.key()))
                                .toList(),
                        world.cells());
        require(finalDiff.exact(),
                "final coordinate diff did not match target: "
                        + finalDiff.csv());
        return new ReplayResult(
                scenario, dna.species(), dna.maturityStage(), steps,
                plan.size(), world.physicalMutationCount(),
                recorder.trace(), recorder.snapshots(),
                recorder.voxelSnapshots(), recorder.visualReports(),
                recorder.smokeHits(),
                recorder.stageFrames(),
                initialCells, world.mutationTimeline(),
                initialProgress, progress(),
                intermediateAudits, finalDiff,
                environment.report());
    }

    private TreeConstructionDecision decision() {
        reconcileSourceLeafLedger();
        TreeReplayProgress progress = progress();
        TreeGrowthQueuePolicy.Completion completion =
                new TreeGrowthQueuePolicy.Completion(
                        liveHeight(),
                        TreeSpeciesStageStyle.visibleHeight(dna),
                        progress.trunkPlaced(), progress.trunkTotal(),
                        progress.branchPlaced(), progress.branchTotal(),
                        progress.canopyPlaced(), progress.canopyTotal());
        TreeCandidate candidate = new TreeCandidate(
                null, dna.baseX(), dna.baseY(), dna.baseZ(),
                dna.baseY() + TreeSpeciesStageStyle.visibleHeight(dna) - 1,
                liveHeight(), dna.species(),
                progress.trunkPlaced() + progress.branchPlaced(),
                progress.canopyPlaced(), Set.copyOf(world.sortedKeys()), true);
        // ## Preserve the live persisted intent at replay entry. The old
        // harness replaced current-only trees with CANOPY and transitions with
        // CLEANUP, hiding the exact REPAIR path reported by live DNA.
        TreeGrowthIntent intent = dna.damageCount() > 0
                ? TreeGrowthIntent.REPAIR : dna.currentIntent();
        int exposedLogs = exposedUpperLogs();
        TreeConstructionReplayInspector.BranchIntegrity branchIntegrity =
                inspector.branchIntegrity(dna, world, true);
        boolean targetVoxelsComplete = progress.trunk() >= 0.999D
                && progress.branch() >= 0.999D
                && progress.canopy() >= 0.999D;
        if (intent == TreeGrowthIntent.REPAIR
                && targetVoxelsComplete) {
            // ## Mirror production's stale-damage normalization. A repair
            // counter with no missing target is state drift, not a constructor
            // action, and must not conceal the following cleanup smoke tag.
            dna.clearDamage();
            dna.setCurrentIntent(dna.hasOriginalShapeSnapshot()
                    ? TreeGrowthIntent.CLEANUP
                    : TreeGrowthIntent.CANOPY);
            intent = dna.currentIntent();
        }
        TreeConstructionSnapshot snapshot =
                TreeConstructionSnapshot.capture(
                        candidate, dna, completion,
                        TreeGrowthQueuePolicy.stageBudget(dna), intent,
                        new TreeConstructionSnapshot.Facts(
                                exposedLogs,
                                branchIntegrity.totalProblems(),
                                branchIntegrity.unplannedBareTerminals(),
                                branchIntegrity.staleEnvelopeLeaves(),
                                branchIntegrity
                                        .uncoveredPlannedEnvelopes(),
                                inspector.readyTransitionBlocker(
                                        dna, world).isPresent(),
                                inspector.ownershipRoleRepair(
                                        dna, world).isPresent(),
                                inspector.disconnectedObsoleteBodyRemaining(
                                        dna, world),
                                inspector.disconnectedPlannedBodyRemaining(
                                        dna, world),
                                inspector.conflictSafeRetirement(
                                        dna, world).isPresent(),
                                progress.canopy()
                                        >= TreeCanopyTransitionPolicy
                                                .minimumReplacementCanopy(dna),
                                inspector.obsoleteEvolvedTreeBlock(
                                        dna, world).isPresent(),
                                inspector.retiredSourceLeaf(
                                        world).isPresent(),
                                dna.unresolvedOriginalShapeLeafCount() == 0));
        return core.decide(snapshot);
    }

    private void reconcileSourceLeafLedger() {
        if (!dna.hasOriginalShapeSnapshot()) {
            return;
        }
        for (String sourceKey : dna.originalShapeLeaves()) {
            if (dna.retiredOriginalShapeLeaves().contains(sourceKey)
                    || dna.countsAsEvolvedLeaf(sourceKey)) {
                continue;
            }
            String coordinateKey = coordinateKey(sourceKey);
            Cell live = world.cell(coordinateKey);
            if (live == null
                    || live.material() != dna.species().leafMaterial()) {
                // ## Mirror the live transition reconciler: a persisted
                // source receipt whose voxel is already absent must retire
                // before the hierarchy asks an executor to prune it again.
                dna.markOriginalShapeLeafRetired(sourceKey);
                continue;
            }
            PlannedTreeBlock planned =
                    plan.blocksByKey().get(coordinateKey);
            if (planned != null
                    && planned.role() == TreeBlockRole.CANOPY
                    && planned.material() == live.material()) {
                // ## A source leaf already occupying its current target is
                // adopted as evolved evidence without changing the world.
                dna.markEvolvedLeaf(sourceKey);
            }
        }
    }

    private static String coordinateKey(String worldKey) {
        String[] parts = worldKey.split(":");
        int offset = parts.length - 3;
        return parts[offset] + ":" + parts[offset + 1]
                + ":" + parts[offset + 2];
    }

    private void injectPlayerDamage() {
        PlannedTreeBlock damaged = targets.stream()
                .filter(block -> block.role() == TreeBlockRole.CANOPY)
                .filter(block ->
                        inspector.targetSatisfied(dna, world, block))
                .filter(block -> world.cell(block.key()).ownership()
                        == Ownership.EVOLVED)
                .findFirst()
                .orElse(null);
        if (damaged == null) {
            return;
        }
        require(world.remove(damaged.key(), "simulated-player-damage"),
                "could not inject player damage");
        allowedMultiMutationKeys.add(damaged.key());
        dna.markDamaged(0L);
        recorder.traceEvent(
                steps, "PLAYER_DAMAGE", damaged.key(), progress().csv());
        damageInjected = true;
    }

    private void verifyUnloadPause() {
        String before = world.fingerprint();
        TreeConstructionDecision next = decision();
        for (int skipped = 0; skipped < 3; skipped++) {
            require(world.fingerprint().equals(before),
                    "unloaded replay changed the virtual world");
        }
        recorder.traceEvent(
                steps, "UNLOADED_PAUSE", next.marker(), progress().csv());
    }

    private void verifyRestartDeterminism() {
        String beforeWorld = world.fingerprint();
        String beforeDecision = decision().marker();
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("tree");
        dna.writeTo(section);
        TreeDna restored = TreeDna.from(
                yaml.getConfigurationSection("tree"));
        require(restored != null, "DNA restart restore returned null");
        dna = restored;
        world = world.copy();
        require(world.fingerprint().equals(beforeWorld),
                "virtual world changed across restart");
        require(decision().marker().equals(beforeDecision),
                "next hierarchy decision changed across restart");
        recorder.capture(
                "50-restart", "persisted and resumed without reroll",
                false, dna, world);
        recorder.traceEvent(
                steps, "RESTART", beforeDecision, progress().csv());
    }

    private void verifyFinalWorld() {
        TreeReplayProgress finalProgress = progress();
        require(finalProgress.trunk() == 1.0D,
                "final trunk does not match target");
        require(finalProgress.branch() == 1.0D,
                "final branches do not match target");
        require(finalProgress.canopy() == 1.0D,
                "final canopy does not match target");
        require(exposedUpperLogs() == 0,
                "final tree has exposed upper support");
        require(uncoveredBranchTips() == 0,
                "final tree has an uncovered branch tip");
        require(dna.unresolvedOriginalShapeLeafCount() == 0,
                "final tree retained unresolved source leaves");
        require(!dna.hasOriginalShapeSnapshot(),
                "final tree retained its transition snapshot");
        require(transitionFinalized,
                "constructor reached completion without finalizing transition");
        require(inspector.obsoleteEvolvedTreeBlock(
                        dna, world).isEmpty(),
                "final tree retained plugin-owned blocks outside the target");
        TreeReplayInvariantAudit.Report finalInvariant =
                TreeReplayInvariantAudit.inspect(dna, plan, world);
        require(finalInvariant.passed(),
                "final invariant retained inherited defects: "
                        + finalInvariant.summary());
        require(world.cells().values().stream()
                        .noneMatch(cell -> cell.ownership() == Ownership.SOURCE
                                && cell.material().name()
                                        .endsWith("_LEAVES")),
                "source foliage remained after transition completion");
        // ## The fourth dimension is the ordered mutation timeline. A replay
        // must reconstruct the final XYZ volume before its renders are trusted.
        world.assertTimelineReconstructs(initialCells);
        world.assertNoCoordinateChurn(allowedMultiMutationKeys);
        for (PlannedTreeBlock target : targets) {
            require(inspector.targetSatisfied(dna, world, target),
                    "final actual world differs from projected target at "
                            + target.key());
        }
    }

    private TreeReplayProgress progress() {
        return inspector.progress(dna, world);
    }

    private int liveHeight() {
        return inspector.liveHeight(dna, world);
    }

    private int exposedUpperLogs() {
        return inspector.exposedUpperLogs(dna, world);
    }

    private int uncoveredBranchTips() {
        return inspector.uncoveredBranchTips(dna, world);
    }

    private void recordAction(
            TreeConstructionDecision decision,
            TreeConstructionResult result,
            TreeReplayProgress progress
    ) {
        // ## Keep the compact first-hit view for smoke coverage. ENTER/EXIT
        // stage frames separately preserve repeated visits to the same tag.
        recorder.captureSmokeStageIfAbsent(
                decision, dna, world);
        recorder.traceAction(
                steps, decision, core.executorName(decision), result,
                progress, dna.unresolvedOriginalShapeLeafCount());
    }

    private double canopyShellTarget() {
        return switch (dna.maturityStage()) {
            case SMALL -> 0.18D;
            case MEDIUM -> 0.24D;
            case MATURE -> 0.30D;
            case ANCIENT -> 0.32D;
        };
    }

    private String worldKey(String coordinateKey) {
        return dna.worldId() + ":" + coordinateKey;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    record ReplayResult(
            String scenario,
            TreeSpecies species,
            TreeMaturityStage stage,
            int steps,
            int plannedBlocks,
            int physicalMutations,
            List<String> trace,
            Map<String, String> snapshots,
            Map<String, Map<String, Cell>> voxelSnapshots,
            Map<String, TreeVisualQualityAudit.Report> visualReports,
            Map<TreeConstructionSmokeTag, Integer> smokeHits,
            List<TreeReplayStageFrame> stageFrames,
            Map<String, Cell> initialCells,
            List<TreeConstructionReplayWorld.Mutation> mutationTimeline,
            TreeReplayProgress initialProgress,
            TreeReplayProgress finalProgress,
            int intermediateAudits,
            TreeLiveVoxelDiff.Report finalDiff,
            TreeSimulatedMinecraftEnvironment.Report environmentReport
    ) {
    }
}
