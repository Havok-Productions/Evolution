package org.evolution.coreparts.hierarchy;

import java.util.EnumMap;
import java.util.Map;

/**
 * ## SHARED FEATURE ACTION HIERARCHY
 *
 * <p>Every enum phase supplies one owner and one execution mode. Construction
 * fails immediately when a phase is unlabeled, keeping ownership explicit as
 * features grow.</p>
 */
public final class FeatureActionHierarchy<P extends Enum<P>
        & FeatureActionPhase> {
    private final String feature;
    private final Map<P, Attachment> attachments;

    private FeatureActionHierarchy(
            String feature,
            Class<P> phaseType
    ) {
        if (feature == null || feature.isBlank()) {
            throw new IllegalArgumentException("Feature id must not be blank.");
        }
        this.feature = feature;
        EnumMap<P, Attachment> mapped = new EnumMap<>(phaseType);
        P[] phases = phaseType.getEnumConstants();
        for (int order = 0; order < phases.length; order++) {
            P phase = phases[order];
            if (phase.owner() == null || phase.owner().isBlank()) {
                throw new IllegalStateException(
                        feature + " phase has no owner: " + phase.name());
            }
            if (phase.mode() == null) {
                throw new IllegalStateException(
                        feature + " phase has no mode: " + phase.name());
            }
            Attachment previous = mapped.putIfAbsent(
                    phase, new Attachment(
                            phase.owner(), phase.mode(), order));
            if (previous != null) {
                throw new IllegalStateException(
                        feature + " phase has duplicate ownership: "
                                + phase.name());
            }
        }
        attachments = Map.copyOf(mapped);
    }

    public static <P extends Enum<P> & FeatureActionPhase>
            FeatureActionHierarchy<P> of(
                    String feature,
                    Class<P> phaseType) {
        return new FeatureActionHierarchy<>(feature, phaseType);
    }

    public FeatureActionDecision decide(P phase, String reason) {
        Attachment attachment = attachments.get(phase);
        if (attachment == null) {
            throw new IllegalStateException(
                    feature + " has no owner for phase " + phase);
        }
        return new FeatureActionDecision(
                feature,
                phase.name(),
                null,
                attachment.owner(),
                attachment.mode(),
                attachment.order(),
                reason == null ? "unspecified" : reason);
    }

    public <S extends Enum<S> & FeatureActionSubrule<P>>
            FeatureActionDecision decide(
                    P phase,
                    S subrule,
                    String reason) {
        if (subrule == null) {
            throw new IllegalArgumentException(
                    feature + " subrule must not be null.");
        }
        if (subrule.phase() != phase) {
            throw new IllegalArgumentException(
                    feature + " subrule " + subrule.name()
                            + " belongs to " + subrule.phase()
                            + ", not " + phase);
        }
        if (subrule.owner() == null || subrule.owner().isBlank()) {
            throw new IllegalStateException(
                    feature + " subrule has no owner: " + subrule.name());
        }

        Attachment attachment = attachments.get(phase);
        if (attachment == null) {
            throw new IllegalStateException(
                    feature + " has no owner for phase " + phase);
        }
        return new FeatureActionDecision(
                feature,
                phase.name(),
                subrule.name(),
                subrule.owner(),
                attachment.mode(),
                attachment.order(),
                reason == null ? "unspecified" : reason);
    }

    public int phaseCount() {
        return attachments.size();
    }

    private record Attachment(
            String owner,
            FeatureActionMode mode,
            int order
    ) {
    }
}
