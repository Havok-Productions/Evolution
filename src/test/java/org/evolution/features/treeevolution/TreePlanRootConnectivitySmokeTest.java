package org.evolution.features.treeevolution;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * ## Checks exact stump-rooted wood, not merely radius-near silhouettes.
 */
public final class TreePlanRootConnectivitySmokeTest {
    private TreePlanRootConnectivitySmokeTest() {
    }

    public static void main(String[] args) {
        List<TreeDna> fixtures = args.length == 0
                ? Arrays.stream(TreeSpecies.values())
                        .map(species -> TreeShapeSmokeTest.sampleDna(
                                species, TreeMaturityStage.MEDIUM, 0))
                        .toList()
                : TreeCapturedFixtureStore.load(Path.of(args[0])).stream()
                        .map(TreeCapturedFixtureStore.Fixture::dna)
                        .toList();
        for (TreeDna source : fixtures) {
            TreeDna dna = new TreeDnaNormalizer().normalize(
                    source, source.maturityStage()).dna();
            TreePlan plan = TreeShapeSmokeTest.treeBodyPlan(dna);
            TreePlanRootConnectivityPolicy.Report report =
                    TreePlanRootConnectivityPolicy.inspect(
                            dna, plan.orderedBlocks());
            if (!report.connected()) {
                throw new IllegalStateException(
                        dna.species().id() + "/" + dna.variant().id()
                                + " " + report.marker());
            }
        }
        System.out.println("Plan root connectivity smoke passed: fixtures="
                + fixtures.size());
    }
}
