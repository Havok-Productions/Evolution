package org.evolution.features.treeevolution;

/**
 * ## Proves every species and stage crosses the same approved blueprint gate.
 */
public final class TreeBlueprintCoordinatorSmokeTest {
    private TreeBlueprintCoordinatorSmokeTest() {
    }

    public static void main(String[] args) {
        TreeEvolutionPlanner planner = new TreeEvolutionPlanner();
        int checked = 0;
        for (TreeVariant variant : TreeVariant.values()) {
            for (TreeMaturityStage stage : TreeMaturityStage.values()) {
                for (int seedVariation = 0;
                        seedVariation < 3;
                        seedVariation++) {
                    TreeDna dna = TreeShapeSmokeTest.sampleDna(
                            variant, stage, seedVariation);
                    TreePlan plan = planner.plan(dna, null, false);
                    TreeBlueprintValidation validation =
                            plan.blueprintValidation();
                    require(plan.sealed(),
                            label(dna) + " was not sealed");
                    require(validation.approved(),
                            label(dna) + " " + validation.marker());
                    require(validation.blocks() == plan.size(),
                            label(dna) + " block count drifted");
                    require(validation.plannedWood()
                                    == validation.rootedWood(),
                            label(dna) + " has disconnected target wood");
                    require(validation.branchTips()
                                    == validation.coveredBranchTips(),
                            label(dna) + " has an uncovered branch tip");
                    for (PlannedTreeBlock block : plan.orderedBlocks()) {
                        require(plan.coordinateTranslator().roundTrips(block),
                                label(dna) + " coordinate did not round-trip: "
                                        + block.key());
                    }
                    checked++;
                }
            }
        }
        System.out.println("Tree blueprint coordinator smoke passed: "
                + checked + " variant/stage/seed plans approved.");
    }

    private static String label(TreeDna dna) {
        return dna.species().id() + "/" + dna.variant().id()
                + "/" + dna.maturityStage();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
