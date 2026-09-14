package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ## Deterministic focus-pool and Folia ownership handoff simulation.
 */
final class TreeFoliaScheduleWideSmoke {
    private TreeFoliaScheduleWideSmoke() {
    }

    static Report run(TreeProductionSmokeSettings settings) {
        List<Work> work = new ArrayList<>();
        int index = 0;
        for (TreeVariant variant : TreeVariant.values()) {
            TreeDna dna = TreeShapeSmokeTest.sampleDna(
                    variant, TreeMaturityStage.MEDIUM, index % 3);
            int units = Math.max(3,
                    TreeShapeSmokeTest.treeBodyPlan(dna).size() / 120);
            work.add(new Work(
                    variant.id(), index % 4, units));
            index++;
        }

        Map<String, Integer> remaining = new LinkedHashMap<>();
        work.forEach(item ->
                remaining.put(item.id(), item.units()));
        List<String> trace = new ArrayList<>();
        trace.add("## Folia-style owned-region handoff and focus rotation.");
        trace.add("cycle,tick,ownedRegion,loaded,attempts,changed,"
                + "remaining,focused");
        int cycle = 0;
        int changed = 0;
        int unloadPauses = 0;
        int regionHandoffs = 0;
        int previousRegion = -1;
        int limit = 10000;

        while (remaining.values().stream()
                .mapToInt(Integer::intValue).sum() > 0
                && cycle < limit) {
            int ownedRegion = (cycle / 3) % 4;
            boolean loaded = cycle % 17 != 8;
            if (previousRegion >= 0
                    && ownedRegion != previousRegion) {
                regionHandoffs++;
            }
            previousRegion = ownedRegion;
            TreeFocusPool pool = new TreeFocusPool();
            for (Work item : work) {
                if (item.region() == ownedRegion
                        && remaining.get(item.id()) > 0) {
                    pool.acquire(item.id());
                }
            }

            int attempts = 0;
            int cycleChanges = 0;
            if (loaded) {
                for (TreeFocusPool.Entry entry
                        : pool.nextRotation()) {
                    if (attempts >= settings.attemptsPerStep()
                            || cycleChanges
                                    >= settings.blocksPerStep()) {
                        break;
                    }
                    attempts++;
                    int left = remaining.get(entry.treeKey());
                    if (left <= 0) {
                        continue;
                    }
                    remaining.put(entry.treeKey(), left - 1);
                    cycleChanges++;
                    changed++;
                    pool.updateProgress(entry.treeKey(), true);
                }
            } else {
                unloadPauses++;
            }
            require(cycleChanges <= settings.blocksPerStep(),
                    "block budget exceeded");
            require(attempts <= settings.attemptsPerStep(),
                    "attempt budget exceeded");
            trace.add(String.join(",",
                    String.valueOf(cycle),
                    String.valueOf(cycle
                            * settings.stepTicks()),
                    String.valueOf(ownedRegion),
                    String.valueOf(loaded),
                    String.valueOf(attempts),
                    String.valueOf(cycleChanges),
                    String.valueOf(remaining.values().stream()
                            .mapToInt(Integer::intValue).sum()),
                    String.valueOf(pool.size())));
            cycle++;
        }
        require(cycle < limit,
                "scheduler did not complete all variant workloads");
        require(unloadPauses > 0,
                "scheduler never exercised an unloaded region");
        require(regionHandoffs > 0,
                "scheduler never exercised a region handoff");
        return new Report(
                cycle,
                changed,
                unloadPauses,
                regionHandoffs,
                cycle * settings.stepTicks(),
                List.copyOf(trace));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Work(String id, int region, int units) {
    }

    record Report(
            int cycles,
            int changedBlocks,
            int unloadPauses,
            int regionHandoffs,
            long simulatedTicks,
            List<String> trace
    ) {
    }
}
