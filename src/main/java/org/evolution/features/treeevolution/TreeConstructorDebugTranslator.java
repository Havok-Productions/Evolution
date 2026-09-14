package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.evolution.features.treeevolution.constructor.TreeConstructionRuleBook;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionExecutorRegistry;

/**
 * Human-readable dictionary for live constructor and geometry codes.
 *
 * <p>## This class translates production-owned enums and rulebook rows. It
 * does not duplicate routing logic and cannot affect tree construction.</p>
 */
final class TreeConstructorDebugTranslator {
    private TreeConstructorDebugTranslator() {
    }

    static Map<String, Object> translation() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 2);
        root.put("how-to-read", List.of(
                "RULE is the first matching hierarchy rule and its stable priority number.",
                "LAYER says which universal-constructor responsibility currently owns the tree.",
                "SUBRULE and EXECUTOR identify the exact mini-constructor that performed the action.",
                "SMOKE-TAG points to the equivalent simulated lifecycle checkpoint.",
                "AUGMENT identifies the planner geometry that requested a placed block.",
                "RELATIVE-COORDINATE is X,Y,Z measured from the saved tree base.",
                "BLUEPRINT is the approved coordinate map shared by construction, voxel diagnostics, and smoke replay."));
        root.put("blueprint-coordinator", blueprintCoordinator());
        root.put("anomaly-triggers", anomalyTriggers());
        root.put("constructor-rules", constructorRules());
        root.put("formation-augments", formationAugments());
        root.put("block-roles", blockRoles());
        root.put("notes", "## Translation is generated from the live rulebook, executor registry, and planner augments. It is diagnostic only.");
        return root;
    }

    private static Map<String, Object> blueprintCoordinator() {
        return row(
                "authority", "TreeBlueprintCoordinator",
                "input", "species/stage mini-planner coordinate proposals",
                "translation", "TreeCoordinateTranslator world XYZ <-> tree-relative XYZ",
                "validation", "rooted wood, parent-linked branches, covered tips, covered trunk top, augment-role contracts",
                "output", "one sealed TreePlan consumed by live construction, 3D voxel diagnostics, and smoke replay",
                "runtime", "TreeConstructorCore selects exactly one hierarchy action against the sealed blueprint",
                "notes", "## Mini-planners describe species geometry; they cannot independently publish a live target.");
    }

    private static List<Map<String, Object>> anomalyTriggers() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(
                "code", "blocked-constructor",
                "meaning", "The next required action could not run. Check audit-passed and obstructions; this is not automatically a malformed tree."));
        rows.add(row(
                "code", "constructor-stage-boundary",
                "meaning", "A transition checkpoint. final-state=false may temporarily expose one incomplete limb while its planned envelope follows."));
        rows.add(row(
                "code", "coordinate-churn",
                "meaning", "The same coordinate repeatedly alternated between placement and removal and needs ownership or target investigation."));
        rows.add(row(
                "code", "final-deformation",
                "meaning", "Treat final-state=true with audit-passed=false as a completed malformed tree requiring a constructor correction."));
        return List.copyOf(rows);
    }

    private static List<Map<String, Object>> constructorRules() {
        TreeConstructionRuleBook ruleBook = new TreeConstructionRuleBook();
        TreeConstructionExecutorRegistry executors =
                new TreeConstructionExecutorRegistry();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TreeConstructionRuleBook.RuleDiagnostic rule
                : ruleBook.diagnostics()) {
            rows.add(row(
                    "number", String.format("%02d", rule.order()),
                    "code", rule.id().name(),
                    "layer", rule.layer().name(),
                    "phase", rule.subrule().phase().name(),
                    "subrule", rule.subrule().name(),
                    "executor", executors.executorName(rule.subrule()),
                    "smoke-tag", rule.subrule().smokeTag().name(),
                    "behavior", rule.reason(),
                    "blocked-means", rule.auditDetail(),
                    "blocks-completion", rule.blocksCompletion()));
        }
        return List.copyOf(rows);
    }

    private static List<Map<String, Object>> formationAugments() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TreePlacementAugment augment : TreePlacementAugment.values()) {
            rows.add(row(
                    "code", augment.name(),
                    "places", augment.expectedRoleLabel(),
                    "formation", augment.contract()));
        }
        return List.copyOf(rows);
    }

    private static List<Map<String, Object>> blockRoles() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TreeBlockRole role : TreeBlockRole.values()) {
            rows.add(row("code", role.name(), "meaning", switch (role) {
                case TRUNK -> "rooted vertical or load-bearing trunk";
                case BRANCH -> "parent-linked structural limb";
                case CANOPY -> "leaf crown, branch envelope, or tip coverage";
                case ROOT -> "mature ground-connected root structure";
                case VINE -> "post-structure hanging detail";
                case GROUND_DETAIL -> "non-structural ecology around the tree";
                case FALLEN_LOG -> "post-structure fallen wood detail";
                case SAPLING -> "same-species offspring planted after completion";
            }));
        }
        return List.copyOf(rows);
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return java.util.Collections.unmodifiableMap(row);
    }
}
