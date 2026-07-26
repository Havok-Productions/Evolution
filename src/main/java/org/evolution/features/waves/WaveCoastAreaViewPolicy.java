package org.evolution.features.waves;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ## Selects a stable, evenly distributed set of incoming fronts for one player.
 *
 * <p>The shared wave simulation remains independent of viewers. This policy only
 * limits the packet-visible set, grouping a long shoreline into reasonable coast
 * areas so one busy coast cannot fill a player's entire view.
 */
final class WaveCoastAreaViewPolicy {
    static final int COAST_AREA_SIZE = 64;

    private WaveCoastAreaViewPolicy() {
    }

    static Selection select(
            List<TravelingWaveFront> candidates,
            List<TravelingWaveFront> previousVisible,
            int maximumPerCoastArea
    ) {
        int limit = Math.max(1, maximumPerCoastArea);
        Set<Long> previousIds = new HashSet<>();
        for (TravelingWaveFront front : previousVisible) {
            previousIds.add(front.id());
        }

        List<TravelingWaveFront> unrestricted = new ArrayList<>();
        Map<CoastArea, List<TravelingWaveFront>> grouped = new HashMap<>();
        for (TravelingWaveFront front : candidates) {
            if (!front.hasShoreTarget()) {
                // ## Open-water fronts have not selected a coast and therefore do
                // not compete for a shoreline area's incoming-wave allowance.
                unrestricted.add(front);
                continue;
            }
            grouped.computeIfAbsent(coastAreaFor(front), ignored -> new ArrayList<>())
                    .add(front);
        }

        List<Map.Entry<CoastArea, List<TravelingWaveFront>>> areas =
                new ArrayList<>(grouped.entrySet());
        areas.sort(Map.Entry.comparingByKey());
        List<TravelingWaveFront> selected = new ArrayList<>(unrestricted);
        Map<CoastArea, AreaDistribution> distributions = new LinkedHashMap<>();
        int limitedAreas = 0;
        int suppressed = 0;
        for (Map.Entry<CoastArea, List<TravelingWaveFront>> entry : areas) {
            List<TravelingWaveFront> areaCandidates = entry.getValue();
            List<TravelingWaveFront> areaSelected =
                    selectStableSpread(areaCandidates, previousIds, limit);
            selected.addAll(areaSelected);
            int areaSuppressed = Math.max(0,
                    areaCandidates.size() - areaSelected.size());
            if (areaSuppressed > 0) {
                limitedAreas++;
                suppressed += areaSuppressed;
            }
            distributions.put(entry.getKey(), new AreaDistribution(
                    areaCandidates.size(), areaSelected.size(), areaSuppressed,
                    areaSelected.stream().map(TravelingWaveFront::id).toList()));
        }
        selected.sort(Comparator.comparingLong(TravelingWaveFront::id));
        return new Selection(
                selected, grouped.size(), limitedAreas, suppressed, distributions);
    }

    private static List<TravelingWaveFront> selectStableSpread(
            List<TravelingWaveFront> candidates,
            Set<Long> previousIds,
            int limit
    ) {
        List<TravelingWaveFront> retained = candidates.stream()
                .filter(front -> previousIds.contains(front.id()))
                .sorted(Comparator.comparingLong(TravelingWaveFront::id))
                .toList();
        List<TravelingWaveFront> fresh = candidates.stream()
                .filter(front -> !previousIds.contains(front.id()))
                .sorted(Comparator.comparingLong(TravelingWaveFront::id))
                .toList();
        List<TravelingWaveFront> selected = new ArrayList<>(limit);

        // ## Keep prior choices first to prevent whole fronts popping as a player
        // moves. Within each pool, farthest-point selection spreads shore impacts.
        fillFarthest(retained, selected, limit);
        fillFarthest(fresh, selected, limit);
        return selected;
    }

    private static void fillFarthest(
            List<TravelingWaveFront> pool,
            List<TravelingWaveFront> selected,
            int limit
    ) {
        List<TravelingWaveFront> remaining = new ArrayList<>(pool);
        while (selected.size() < limit && !remaining.isEmpty()) {
            TravelingWaveFront next = selected.isEmpty()
                    ? remaining.stream()
                            .min(Comparator.comparingLong(
                                    TravelingWaveFront::id))
                            .orElseThrow()
                    : remaining.stream()
                            .max(Comparator
                                    .comparingDouble((TravelingWaveFront front) ->
                                            minimumTargetDistanceSquared(
                                                    front, selected))
                                    .thenComparingLong((TravelingWaveFront front) -> -front.id()))
                            .orElseThrow();
            selected.add(next);
            remaining.remove(next);
        }
    }

    private static double minimumTargetDistanceSquared(
            TravelingWaveFront candidate,
            List<TravelingWaveFront> selected
    ) {
        double minimum = Double.POSITIVE_INFINITY;
        for (TravelingWaveFront existing : selected) {
            double dx = candidate.shoreTargetX() - existing.shoreTargetX();
            double dz = candidate.shoreTargetZ() - existing.shoreTargetZ();
            minimum = Math.min(minimum, (dx * dx) + (dz * dz));
        }
        return minimum;
    }

    static CoastArea coastAreaFor(TravelingWaveFront front) {
        return new CoastArea(
                Math.floorDiv((int) Math.round(front.shoreTargetX()),
                        COAST_AREA_SIZE),
                Math.floorDiv((int) Math.round(front.shoreTargetZ()),
                        COAST_AREA_SIZE));
    }

    record Selection(
            List<TravelingWaveFront> fronts,
            int coastAreas,
            int limitedAreas,
            int suppressedFronts,
            Map<CoastArea, AreaDistribution> distributions
    ) {
        Selection {
            fronts = List.copyOf(fronts);
            distributions = Map.copyOf(distributions);
        }
    }

    record AreaDistribution(
            int candidates,
            int selected,
            int suppressed,
            List<Long> selectedIds
    ) {
        AreaDistribution {
            selectedIds = List.copyOf(selectedIds);
        }
    }

    record CoastArea(int cellX, int cellZ)
            implements Comparable<CoastArea> {
        @Override
        public int compareTo(CoastArea other) {
            int x = Integer.compare(cellX, other.cellX);
            return x != 0 ? x : Integer.compare(cellZ, other.cellZ);
        }
    }
}
