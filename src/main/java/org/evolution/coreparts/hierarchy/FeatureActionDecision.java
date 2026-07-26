package org.evolution.coreparts.hierarchy;

public record FeatureActionDecision(
        String feature,
        String phase,
        String subrule,
        String owner,
        FeatureActionMode mode,
        int order,
        String reason
) {
    public String marker() {
        String nested = subrule == null || subrule.isBlank()
                ? ""
                : "[" + subrule + "]";
        return "[HIERARCHY][" + feature + "][" + phase + "]" + nested
                + "[" + owner + "][" + mode + "]";
    }

    public String resourceTask() {
        String task = "hierarchy."
                + phase.toLowerCase(java.util.Locale.ROOT);
        return subrule == null || subrule.isBlank()
                ? task
                : task + "." + subrule.toLowerCase(java.util.Locale.ROOT);
    }
}