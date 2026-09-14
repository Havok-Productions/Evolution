package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.evolution.features.treeevolution.constructor.TreeConstructionAttachment;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionPhase;
import org.evolution.features.treeevolution.constructor.TreeConstructionSmokeTag;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;

/**
 * ## Thread-safe XYZ-time stage ownership for live constructor diagnostics.
 *
 * <p>Each tree keeps its own transition counter. A hierarchy change closes
 * the previous stage and opens the next at the same world state, matching the
 * replay's EXIT/ENTER frame pairs without coupling trees across Folia regions.</p>
 */
final class TreeConstructorStageTimeline {
    private final ConcurrentMap<String, StageIdentity> active =
            new ConcurrentHashMap<>();

    Boundary enter(String treeKey, TreeConstructionDecision decision) {
        AtomicReference<Boundary> observed = new AtomicReference<>();
        active.compute(treeKey, (ignored, previous) -> {
            if (previous != null && previous.sameStage(decision)) {
                observed.set(Boundary.unchanged(previous));
                return previous;
            }
            int transition = previous == null
                    ? 1 : previous.transition() + 1;
            StageIdentity entering = StageIdentity.from(
                    decision, transition, false);
            List<Frame> frames = new ArrayList<>(2);
            if (previous != null && !previous.closed()) {
                frames.add(new Frame(previous, FrameBoundary.EXIT));
            }
            frames.add(new Frame(entering, FrameBoundary.ENTER));
            observed.set(new Boundary(true, List.copyOf(frames)));
            return entering;
        });
        return observed.get();
    }

    Boundary complete(
            String treeKey,
            TreeConstructionDecision decision
    ) {
        AtomicReference<Boundary> observed = new AtomicReference<>(
                Boundary.unchanged(null));
        active.computeIfPresent(treeKey, (ignored, current) -> {
            if (current.closed() || !current.sameStage(decision)) {
                return current;
            }
            StageIdentity closed = current.withClosed(true);
            observed.set(new Boundary(
                    true,
                    List.of(new Frame(closed, FrameBoundary.EXIT))));
            return closed;
        });
        return observed.get();
    }

    enum FrameBoundary {
        ENTER,
        EXIT
    }

    record Frame(StageIdentity stage, FrameBoundary boundary) {
    }

    record Boundary(boolean changed, List<Frame> frames) {
        static Boundary unchanged(StageIdentity current) {
            return new Boundary(false, List.of());
        }
    }

    record StageIdentity(
            TreeConstructionSmokeTag smokeTag,
            TreeConstructionPhase phase,
            TreeConstructionSubrule subrule,
            TreeConstructionAttachment attachment,
            int transition,
            boolean closed
    ) {
        static StageIdentity from(
                TreeConstructionDecision decision,
                int transition,
                boolean closed
        ) {
            return new StageIdentity(
                    decision.smokeTag(), decision.phase(),
                    decision.subrule(), decision.attachment(),
                    transition, closed);
        }

        boolean sameStage(TreeConstructionDecision decision) {
            return smokeTag == decision.smokeTag()
                    && subrule == decision.subrule();
        }

        StageIdentity withClosed(boolean value) {
            return new StageIdentity(
                    smokeTag, phase, subrule, attachment,
                    transition, value);
        }
    }
}
