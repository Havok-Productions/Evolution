package org.evolution.coreparts.hierarchy;

import org.evolution.coreparts.EvolutionPlugin;

/**
 * Writes the same hierarchy selection to architecture and resource debug.
 */
public final class FeatureHierarchyTrace {
    private FeatureHierarchyTrace() {
    }

    public static <P extends Enum<P> & FeatureActionPhase>
            FeatureActionDecision record(
                    EvolutionPlugin plugin,
                    FeatureActionHierarchy<P> hierarchy,
                    P phase,
                    String reason) {
        FeatureActionDecision decision = hierarchy.decide(phase, reason);
        record(plugin, decision, false);
        return decision;
    }

    public static <
            P extends Enum<P> & FeatureActionPhase,
            S extends Enum<S> & FeatureActionSubrule<P>>
            FeatureActionDecision record(
                    EvolutionPlugin plugin,
                    FeatureActionHierarchy<P> hierarchy,
                    P phase,
                    S subrule,
                    String reason) {
        FeatureActionDecision decision =
                hierarchy.decide(phase, subrule, reason);
        record(plugin, decision, true);
        return decision;
    }

    private static void record(
            EvolutionPlugin plugin,
            FeatureActionDecision decision,
            boolean nested) {
        String path = "hierarchy." + decision.phase().toLowerCase(
                java.util.Locale.ROOT);
        if (nested) {
            path += "." + decision.subrule().toLowerCase(
                    java.util.Locale.ROOT);
        }
        plugin.pathDebug().traceSampled(
                plugin,
                decision.feature(),
                path,
                decision.marker() + " order=" + decision.order()
                        + " reason=" + decision.reason());
        plugin.resourceReporter().count(
                plugin,
                decision.feature(),
                decision.resourceTask(),
                1L,
                0L,
                "owner=" + decision.owner()
                        + " mode=" + decision.mode()
                        + " reason=" + decision.reason());
    }
}