package org.evolution.coreparts.hierarchy;

/**
 * A labeled action with exactly one owner.
 */
public interface FeatureActionPhase {
    String owner();

    FeatureActionMode mode();
}
