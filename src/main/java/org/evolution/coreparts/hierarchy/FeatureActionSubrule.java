package org.evolution.coreparts.hierarchy;

/**
 * ## NESTED FEATURE ACTION RULE
 *
 * <p>Complex feature phases can expose smaller owned contracts without
 * creating a second competing action pipeline.</p>
 */
public interface FeatureActionSubrule<
        P extends Enum<P> & FeatureActionPhase> {
    P phase();

    String owner();
}