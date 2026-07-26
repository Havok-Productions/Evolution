package org.evolution.features.treeevolution;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Guards the file-level runtime hierarchy against collapsing back into the
 * Folia/event facade.
 */
public final class TreeRuntimeOwnershipSmokeTest {
    private TreeRuntimeOwnershipSmokeTest() {
    }

    public static void main(String[] args) {
        Set<Class<?>> facadeOwners = fieldTypes(TreeEvolutionFeature.class);
        require(facadeOwners.contains(TreeConstructionRuntime.class),
                "feature facade must delegate constructor execution");
        require(facadeOwners.contains(TreePlanAuditService.class),
                "feature facade must delegate plan/audit ownership");
        require(facadeOwners.contains(TreeMaturityService.class),
                "feature facade must delegate maturity ownership");
        require(facadeOwners.contains(TreePlacementService.class),
                "feature facade must delegate placement ownership");
        require(facadeOwners.contains(TreeCanopyRepairService.class),
                "feature facade must delegate canopy-repair ownership");
        require(facadeOwners.contains(TreeTransitionService.class),
                "feature facade must delegate transition ownership");
        require(!facadeOwners.contains(TreeConstructorCore.class),
                "feature facade must not own constructor phase execution");
        require(!facadeOwners.contains(TreeShapeEngine.class),
                "feature facade must not own live target selection");

        Set<Class<?>> runtimeOwners = fieldTypes(TreeConstructionRuntime.class);
        require(runtimeOwners.contains(TreeConstructorCore.class),
                "runtime must own the exclusive constructor hierarchy");
        require(runtimeOwners.contains(TreePlanAuditService.class),
                "runtime must use shared plan/audit ownership");
        require(runtimeOwners.contains(TreeMaturityService.class),
                "runtime must use shared maturity ownership");
        require(runtimeOwners.contains(TreePlacementService.class),
                "runtime must use shared placement ownership");
        require(runtimeOwners.contains(TreeCanopyRepairService.class),
                "runtime must use shared canopy-repair ownership");
        require(runtimeOwners.contains(TreeTransitionService.class),
                "runtime must use shared transition ownership");
        require(runtimeOwners.contains(TreeReproductionService.class),
                "runtime must use shared reproduction ownership");

        System.out.println(
                "Tree runtime ownership smoke test passed: facade, hierarchy, "
                        + "audit, maturity, transition, placement, canopy repair, and reproduction are split.");
    }

    private static Set<Class<?>> fieldTypes(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getType)
                .collect(Collectors.toSet());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
