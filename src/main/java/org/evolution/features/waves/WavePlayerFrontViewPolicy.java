package org.evolution.features.waves;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ## Applies the final stable wave limit across one player's complete view.
 */
final class WavePlayerFrontViewPolicy {
    private WavePlayerFrontViewPolicy() {
    }

    static Selection select(
            List<TravelingWaveFront> candidates,
            List<TravelingWaveFront> previousVisible,
            int playerX,
            int playerZ,
            int maximumVisible
    ) {
        int limit = Math.max(1, maximumVisible);
        Set<Long> candidateIds = new HashSet<>();
        candidates.forEach(front -> candidateIds.add(front.id()));

        List<TravelingWaveFront> selected = new ArrayList<>(limit);
        previousVisible.stream()
                .filter(front -> candidateIds.contains(front.id()))
                .sorted(Comparator.comparingLong(TravelingWaveFront::id))
                .limit(limit)
                .forEach(selected::add);

        Set<Long> selectedIds = new HashSet<>();
        selected.forEach(front -> selectedIds.add(front.id()));
        candidates.stream()
                .filter(front -> !selectedIds.contains(front.id()))
                .sorted(Comparator
                        .comparingDouble((TravelingWaveFront front) ->
                                distanceSquared(front, playerX, playerZ))
                        .thenComparingLong(TravelingWaveFront::id))
                .limit(Math.max(0, limit - selected.size()))
                .forEach(selected::add);

        return new Selection(selected,
                Math.max(0, candidates.size() - selected.size()));
    }

    private static double distanceSquared(
            TravelingWaveFront front,
            int playerX,
            int playerZ
    ) {
        double dx = front.x() - playerX;
        double dz = front.z() - playerZ;
        return (dx * dx) + (dz * dz);
    }

    record Selection(List<TravelingWaveFront> fronts, int suppressed) {
        Selection {
            fronts = List.copyOf(fronts);
        }
    }
}
