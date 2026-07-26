package org.evolution.coreparts.hierarchy;

import java.util.EnumSet;
import java.util.Set;
import org.evolution.features.ecology.action.EcologyActionPhase;
import org.evolution.features.meadow.action.MeadowActionPhase;
import org.evolution.features.nether.action.NetherActionPhase;
import org.evolution.features.nether.action.NetherActionSubrule;
import org.evolution.features.portal.action.PortalActionPhase;
import org.evolution.features.portal.action.PortalActionSubrule;
import org.evolution.features.puddles.action.PuddleActionPhase;
import org.evolution.features.puddles.action.PuddleActionSubrule;
import org.evolution.features.regrowth.action.RegrowthActionPhase;
import org.evolution.features.regrowth.action.RegrowthActionSubrule;
import org.evolution.features.waves.action.WaveActionPhase;
import org.evolution.features.waves.action.WaveActionSubrule;
import org.evolution.features.wind.action.WindActionPhase;

public final class FeatureActionHierarchySmokeTest {
    private FeatureActionHierarchySmokeTest() {
    }

    public static void main(String[] args) {
        verify("ecology", EcologyActionPhase.class);
        verify("meadow", MeadowActionPhase.class);
        verify("nether", NetherActionPhase.class);
        verify("shaped-portals", PortalActionPhase.class);
        verify("puddles", PuddleActionPhase.class);
        verify("regrowth", RegrowthActionPhase.class);
        verify("waves", WaveActionPhase.class);
        verify("wind", WindActionPhase.class);

        int nested = 0;
        nested += verifySubrules("nether", NetherActionPhase.class,
                NetherActionSubrule.class);
        nested += verifySubrules("shaped-portals", PortalActionPhase.class,
                PortalActionSubrule.class);
        nested += verifySubrules("puddles", PuddleActionPhase.class,
                PuddleActionSubrule.class);
        nested += verifySubrules("regrowth", RegrowthActionPhase.class,
                RegrowthActionSubrule.class);
        // ## Waves currently need one nested viewer-policy contract. It is tested
        // without requiring unrelated simulation phases to invent empty subrules.
        nested += verifySingleSubrule("waves", WaveActionPhase.class,
                WaveActionSubrule.PER_PLAYER_COAST_AREA_DISTRIBUTION);

        System.out.println(
                "Feature action hierarchy smoke test passed: "
                        + "8 feature hierarchies and " + nested
                        + " nested lifecycle subrules are ordered and owned.");
    }

    private static <P extends Enum<P> & FeatureActionPhase> void verify(
            String feature,
            Class<P> phaseType) {
        FeatureActionHierarchy<P> hierarchy =
                FeatureActionHierarchy.of(feature, phaseType);
        P[] phases = phaseType.getEnumConstants();
        require(hierarchy.phaseCount() == phases.length,
                feature + " hierarchy omitted a phase.");

        for (int index = 0; index < phases.length; index++) {
            P phase = phases[index];
            FeatureActionDecision decision =
                    hierarchy.decide(phase, "smoke-test");
            require(decision.order() == index,
                    feature + " phase order changed for " + phase.name());
            require(decision.owner().equals(phase.owner()),
                    feature + " owner mismatch for " + phase.name());
            require(decision.mode() == phase.mode(),
                    feature + " mode mismatch for " + phase.name());
            require(decision.marker().contains(
                            "[" + phase.name() + "]"),
                    feature + " marker omitted " + phase.name());
        }
    }

    private static <
            P extends Enum<P> & FeatureActionPhase,
            S extends Enum<S> & FeatureActionSubrule<P>> int verifySubrules(
                    String feature,
                    Class<P> phaseType,
                    Class<S> subruleType) {
        FeatureActionHierarchy<P> hierarchy =
                FeatureActionHierarchy.of(feature, phaseType);
        Set<P> covered = EnumSet.noneOf(phaseType);
        S[] subrules = subruleType.getEnumConstants();
        for (S subrule : subrules) {
            P phase = subrule.phase();
            covered.add(phase);
            FeatureActionDecision decision = hierarchy.decide(
                    phase, subrule, "nested-smoke-test");
            require(decision.subrule().equals(subrule.name()),
                    feature + " omitted nested rule " + subrule.name());
            require(decision.owner().equals(subrule.owner()),
                    feature + " nested owner mismatch for "
                            + subrule.name());
            require(decision.marker().contains(
                            "[" + subrule.name() + "]"),
                    feature + " marker omitted nested rule "
                            + subrule.name());

            P wrongPhase = firstOtherPhase(phaseType, phase);
            if (wrongPhase != null) {
                boolean rejected = false;
                try {
                    hierarchy.decide(wrongPhase, subrule,
                            "wrong-parent-smoke-test");
                } catch (IllegalArgumentException expected) {
                    rejected = true;
                }
                require(rejected,
                        feature + " accepted " + subrule.name()
                                + " under wrong phase " + wrongPhase);
            }
        }
        require(covered.size() == phaseType.getEnumConstants().length,
                feature + " nested hierarchy omitted a parent phase: "
                        + covered);
        return subrules.length;
    }

    private static <
            P extends Enum<P> & FeatureActionPhase,
            S extends Enum<S> & FeatureActionSubrule<P>> int verifySingleSubrule(
                    String feature,
                    Class<P> phaseType,
                    S subrule) {
        FeatureActionHierarchy<P> hierarchy =
                FeatureActionHierarchy.of(feature, phaseType);
        FeatureActionDecision decision = hierarchy.decide(
                subrule.phase(), subrule, "single-nested-smoke-test");
        require(decision.subrule().equals(subrule.name()),
                feature + " omitted nested rule " + subrule.name());
        require(decision.owner().equals(subrule.owner()),
                feature + " nested owner mismatch for " + subrule.name());
        return 1;
    }
    private static <P extends Enum<P>> P firstOtherPhase(
            Class<P> phaseType,
            P expected) {
        for (P phase : phaseType.getEnumConstants()) {
            if (phase != expected) {
                return phase;
            }
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}