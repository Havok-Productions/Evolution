package org.slowtrees.waves;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class TravelingWaveRegistry {
    private static final int SHORE_STEERING_DISTANCE = 48;
    private static final long MERGE_COOLDOWN_TICKS = 40L;
    private static final long SPAWN_REPLENISH_TICKS = 20L;
    private static final long FRONT_FADE_IN_TICKS = 20L;
    private static final int SOURCE_CELL_SIZE = 192;
    private static final int SOURCE_SEARCH_RADIUS = 80;
    private static final int FRONTS_PER_SOURCE = 4;
    private static final int SOURCE_CACHE_LIMIT = 512;
    private static final long SOURCE_RETENTION_MILLIS = 600_000L;
    private static final int PASSAGE_PROBE_REACH = 40;
    private static final int SMALL_ENCLOSED_WATER_SPAN = 40;
    private static final int SMALL_ENCLOSED_MAX_SOURCE_DISTANCE = 21;
    private static final int SMALL_WATER_MIN_START_DISTANCE = 4;
    private static final int SMALL_WATER_MAX_START_DISTANCE = 12;
    private static final double OPEN_WATER_MAX_FAN_ANGLE = Math.toRadians(18.0D);
    private static final int SHORE_ANGLE_PROBE_RADIUS = 18;
    private static final int SHORE_ANGLE_PROBE_STEP = 3;
    private static final int SHORE_ANGLE_MINIMUM_SAMPLES = 12;
    private static final double SHORE_ANGLE_MINIMUM_KNOWN_RATIO = 0.72D;
    private static final double SHORE_ANGLE_MAXIMUM_COHERENCE = 0.78D;
    private static final int[][] PASSAGE_AXES = {
            {1, 0}, {0, 1}, {1, 1}, {1, -1}
    };
    private final Map<SourceKey, SourceFronts> bySource = new ConcurrentHashMap<>();
    private final Map<UUID, List<TravelingWaveFront>> visibleByPlayer = new ConcurrentHashMap<>();

    Update update(UUID playerId, UUID worldId, int playerX, int playerZ, long tick,
            WaveProfile profile, OvalWaveSettings settings, double windX, double windZ,
            int renderRadius, int simulationRadius,
            WaveLakeFlowCache.Snapshot topology) {
        double moved = 0.0D;
        int shoreGuided = 0;
        int impacts = 0;
        int mergedFronts = 0;
        int activeSources = 0;
        List<Long> spawnedIds = new ArrayList<>();
        List<Long> expiredIds = new ArrayList<>();
        List<Long> distanceCulledIds = new ArrayList<>();
        List<Long> shoreFizzleIds = new ArrayList<>();
        List<Long> distanceFizzleIds = new ArrayList<>();
        List<String> mergeTransitions = new ArrayList<>();
        List<String> steeringTransitions = new ArrayList<>();
        List<TravelingWaveFront> visible = new ArrayList<>();
        Set<String> anchors = new LinkedHashSet<>();

        for (SourceCoordinate coordinate : sourceCoordinatesNear(
                worldId, playerX, playerZ, simulationRadius)) {
            SourceKey key = new SourceKey(worldId, coordinate.cellX(), coordinate.cellZ());
            SourceFronts state = bySource.computeIfAbsent(key, SourceFronts::new);
            SourceUpdate sourceUpdate;
            synchronized (state) {
                state.lastViewedMillis = System.currentTimeMillis();
                if (!state.resolved && !resolveSource(state, topology)) {
                    continue;
                }
                sourceUpdate = advanceSource(state, tick, profile, settings,
                        windX, windZ, topology);
                activeSources++;
                if (anchors.size() < 8) {
                    anchors.add(state.sourceX + "," + state.sourceZ);
                }
                for (TravelingWaveFront front : state.fronts) {
                    double reach = simulationRadius * 1.55D
                            + Math.max(front.boundsX(), front.boundsZ());
                    if (distance(front.x(), front.z(), playerX, playerZ) <= reach) {
                        visible.add(front);
                    }
                }
            }
            moved += sourceUpdate.movedBlocks();
            shoreGuided += sourceUpdate.shoreGuidedFronts();
            impacts += sourceUpdate.shoreImpacts();
            mergedFronts += sourceUpdate.mergedFronts();
            spawnedIds.addAll(sourceUpdate.lifecycle().spawnedIds());
            expiredIds.addAll(sourceUpdate.lifecycle().expiredIds());
            shoreFizzleIds.addAll(sourceUpdate.lifecycle().shoreFizzleIds());
            distanceFizzleIds.addAll(sourceUpdate.lifecycle().distanceFizzleIds());
            mergeTransitions.addAll(sourceUpdate.lifecycle().mergeTransitions());
            steeringTransitions.addAll(sourceUpdate.lifecycle().steeringTransitions());
        }

        List<TravelingWaveFront> snapshot = Collections.unmodifiableList(new ArrayList<>(visible));
        if (snapshot.isEmpty()) {
            visibleByPlayer.remove(playerId);
        } else {
            visibleByPlayer.put(playerId, snapshot);
        }
        trimDormantSources();

        Lifecycle lifecycle = new Lifecycle(spawnedIds, expiredIds, distanceCulledIds,
                shoreFizzleIds, distanceFizzleIds, mergeTransitions, steeringTransitions,
                0L, 0L, false);
        SourceSummary sources = new SourceSummary(
                activeSources, snapshot.size(), SOURCE_CELL_SIZE, new ArrayList<>(anchors));
        return new Update(snapshot, moved, shoreGuided, impacts, mergedFronts,
                lifecycle, directionSummary(snapshot), sources);
    }

    private SourceUpdate advanceSource(SourceFronts state, long tick,
            WaveProfile profile, OvalWaveSettings settings, double windX, double windZ,
            WaveLakeFlowCache.Snapshot topology) {
        boolean initialPopulation = state.fronts.isEmpty() && state.spawnSequence == 1L;
        double moved = 0.0D;
        int shoreGuided = 0;
        int impacts = 0;
        List<Long> spawnedIds = new ArrayList<>();
        List<Long> expiredIds = new ArrayList<>();
        List<Long> distanceCulledIds = new ArrayList<>();
        List<Long> shoreFizzleIds = new ArrayList<>();
        List<Long> distanceFizzleIds = new ArrayList<>();
        List<String> mergeTransitions = new ArrayList<>();
        List<String> steeringTransitions = new ArrayList<>();

        Iterator<TravelingWaveFront> iterator = state.fronts.iterator();
        while (iterator.hasNext()) {
            TravelingWaveFront front = iterator.next();
            if (front.expired(tick)) {
                expiredIds.add(front.id());
                iterator.remove();
                continue;
            }
            int frontX = (int) Math.round(front.x());
            int frontZ = (int) Math.round(front.z());
            if (!topology.isKnown(frontX, frontZ)) {
                continue;
            }
            LakeWaveFlowField.Cell cell = topology.cell(frontX, frontZ);
            if (cell.shoreGuided() && front.beginPassageProbe(tick)) {
                PassageMeasurement passage = localPassage(topology, frontX, frontZ);
                int waterSpan = passage.span();
                int nearbyFronts = nearbyFrontCount(state.fronts, front, waterSpan);
                if (waterSpan > 0
                        && front.lockNarrowPassage(waterSpan, nearbyFronts)) {
                    steeringTransitions.add("[STEER][TIGHT-WATER] id=" + front.id()
                            + " kind=" + front.kind()
                            + " action=compact-and-lock"
                            + " local-bank-span=" + waterSpan
                            + " nearby-fronts=" + nearbyFronts
                            + " target-half-width=" + rounded(front.passageHalfWidth()));
                }
                if (isSmallEnclosedWater(cell, passage)) {
                    TravelingWaveFront.Direction channel = resolveChannelCourse(
                            state, passage, windX, windZ);
                    if (front.lockChannelCourse(channel.x(), channel.z())) {
                        steeringTransitions.add(channelLockMarker(front, passage,
                                topology, frontX, frontZ));
                    }
                }
            }
            boolean detectedShore = !front.channelCourseLocked()
                    && cell.shoreGuided()
                    && cell.shoreDistance() <= SHORE_STEERING_DISTANCE
                    && (cell.directionX() != 0 || cell.directionZ() != 0);
            if (detectedShore) {
                front.acquireShoreTarget(cell.directionX(), cell.directionZ(),
                        cell.shoreDistance());
            }
            boolean channelLocked = front.channelCourseLocked();
            boolean guided = !channelLocked && front.hasShoreTarget();
            TravelingWaveFront.Direction baseCourse = channelLocked
                    ? front.channelCourse()
                    : guided ? front.lockedShoreDirection()
                            : new TravelingWaveFront.Direction(windX, windZ);
            int courseDistance = guided
                    ? (int) Math.round(Math.min(SHORE_STEERING_DISTANCE,
                            front.lockedShoreDistance()))
                    : SHORE_STEERING_DISTANCE;
            TravelingWaveFront.Direction course = channelLocked
                    ? baseCourse
                    : front.courseDirection(
                            baseCourse.x(), baseCourse.z(), courseDistance, guided);
            TravelingWaveFront.Motion motion = front.prepareMotion(
                    tick, profile.speed(), course.x(), course.z(), guided);
            if (motion.steeringSignal() != TravelingWaveFront.SteeringSignal.NONE) {
                steeringTransitions.add(steeringMarker(front, motion));
                if (motion.steeringSignal() == TravelingWaveFront.SteeringSignal.SHORE_REACHED
                        && !front.fizzling()) {
                    front.beginShoreFizzle(tick);
                    shoreFizzleIds.add(front.id());
                    impacts++;
                    shoreGuided++;
                    continue;
                }
            }
            int nextX = (int) Math.round(motion.nextX());
            int nextZ = (int) Math.round(motion.nextZ());
            if (!topology.isKnown(nextX, nextZ)) {
                continue;
            }

            if (!front.fizzling() && topology.isWater(nextX, nextZ)) {
                front.commitMotion(motion);
                moved += motion.distance();
            } else if (!front.fizzling()) {
                front.beginShoreFizzle(tick);
                shoreFizzleIds.add(front.id());
                impacts++;
            }
            if (!front.fizzling() && detectedShore && cell.shoreDistance() <= 2) {
                front.beginShoreFizzle(tick);
                shoreFizzleIds.add(front.id());
                impacts++;
            } else if (!front.fizzling() && front.travelled() >= profile.travelDistance()) {
                front.beginDistanceFizzle(tick);
                distanceFizzleIds.add(front.id());
            }
            shoreGuided += front.shoreGuided() ? 1 : 0;
        }

        int mergedFronts = mergeCollidingFronts(
                state, tick, topology, mergeTransitions);
        if (mergedFronts > 0) {
            state.nextSpawnTick = Math.max(
                    state.nextSpawnTick, tick + SPAWN_REPLENISH_TICKS);
        }
        int spawnLimit = initialPopulation ? FRONTS_PER_SOURCE
                : tick >= state.nextSpawnTick ? 1 : 0;
        int attempts = 0;
        while (state.fronts.size() < FRONTS_PER_SOURCE
                && spawnedIds.size() < spawnLimit && attempts++ < 8) {
            TravelingWaveFront spawned = spawn(state, tick, profile, settings,
                    windX, windZ, topology, true, steeringTransitions);
            if (spawned == null) {
                break;
            }
            state.fronts.add(spawned);
            spawnedIds.add(spawned.id());
        }
        if (!initialPopulation && !spawnedIds.isEmpty()) {
            state.nextSpawnTick = tick + SPAWN_REPLENISH_TICKS;
        }

        Lifecycle lifecycle = new Lifecycle(spawnedIds, expiredIds, distanceCulledIds,
                shoreFizzleIds, distanceFizzleIds, mergeTransitions, steeringTransitions,
                Math.max(0L, state.nextMergeTick - tick),
                Math.max(0L, state.nextSpawnTick - tick),
                state.fronts.size() < FRONTS_PER_SOURCE);
        return new SourceUpdate(moved, shoreGuided, impacts, mergedFronts, lifecycle);
    }
    FrontSample sample(UUID playerId, double x, double z, long tick) {
        List<TravelingWaveFront> fronts = visibleByPlayer.get(playerId);
        if (fronts == null || fronts.isEmpty()) {
            return new FrontSample(0.0D, false, 0);
        }
        double combined = 0.0D;
        double dominant = 0.0D;
        boolean dominantFizzling = false;
        int contributors = 0;
        for (TravelingWaveFront front : fronts) {
            double strength = front.strengthAt(x, z, tick);
            if (strength <= 0.001D) {
                continue;
            }
            contributors++;
            combined = 1.0D - ((1.0D - combined) * (1.0D - strength));
            if (strength > dominant) {
                dominant = strength;
                dominantFizzling = front.fizzling();
            }
        }
        return new FrontSample(combined, dominantFizzling, contributors);
    }

    void remove(UUID playerId) {
        visibleByPlayer.remove(playerId);
    }

    void clear() {
        bySource.clear();
        visibleByPlayer.clear();
    }
    static List<SourceCoordinate> sourceCoordinatesNear(
            UUID worldId, int viewerX, int viewerZ, int simulationRadius) {
        int sourceReach = Math.max(SOURCE_CELL_SIZE / 2,
                simulationRadius + SOURCE_SEARCH_RADIUS);
        int minimumCellX = Math.floorDiv(viewerX - sourceReach + (SOURCE_CELL_SIZE / 2), SOURCE_CELL_SIZE);
        int maximumCellX = Math.floorDiv(viewerX + sourceReach + (SOURCE_CELL_SIZE / 2), SOURCE_CELL_SIZE);
        int minimumCellZ = Math.floorDiv(viewerZ - sourceReach + (SOURCE_CELL_SIZE / 2), SOURCE_CELL_SIZE);
        int maximumCellZ = Math.floorDiv(viewerZ + sourceReach + (SOURCE_CELL_SIZE / 2), SOURCE_CELL_SIZE);
        List<SourceCoordinate> coordinates = new ArrayList<>();
        for (int cellX = minimumCellX; cellX <= maximumCellX; cellX++) {
            int centerX = cellX * SOURCE_CELL_SIZE;
            for (int cellZ = minimumCellZ; cellZ <= maximumCellZ; cellZ++) {
                int centerZ = cellZ * SOURCE_CELL_SIZE;
                if (distanceStatic(centerX, centerZ, viewerX, viewerZ) > sourceReach) {
                    continue;
                }
                coordinates.add(new SourceCoordinate(worldId, cellX, cellZ, centerX, centerZ));
            }
        }
        return Collections.unmodifiableList(coordinates);
    }

    private boolean resolveSource(SourceFronts state,
            WaveLakeFlowCache.Snapshot topology) {
        int centerX = state.key.centerX();
        int centerZ = state.key.centerZ();
        for (int attempt = 0; attempt < 128; attempt++) {
            double angle = hash01(state.seed, attempt * 2L) * Math.PI * 2.0D;
            double radial = attempt == 0 ? 0.0D
                    : Math.sqrt(hash01(state.seed, (attempt * 2L) + 1L));
            int x = centerX + (int) Math.round(
                    Math.cos(angle) * SOURCE_SEARCH_RADIUS * radial);
            int z = centerZ + (int) Math.round(
                    Math.sin(angle) * SOURCE_SEARCH_RADIUS * radial);
            if (!topology.isKnown(x, z)) {
                // ## Candidate order is deterministic. Waiting for an earlier UNKNOWN
                // prevents a player's partial topology from choosing a different source.
                return false;
            }
            if (!topology.isWater(x, z)) {
                continue;
            }
            LakeWaveFlowField.Cell cell = topology.cell(x, z);
            if (cell.shoreGuided() && cell.shoreDistance() < 6) {
                int localSpan = localWaterSpan(topology, x, z);
                boolean smallEnclosedWater = localSpan > 0
                        && localSpan <= SMALL_ENCLOSED_WATER_SPAN
                        && cell.sourceDistance() > 0
                        && cell.sourceDistance() <= SMALL_ENCLOSED_MAX_SOURCE_DISTANCE;
                if (!smallEnclosedWater || cell.shoreDistance() < 3) {
                    continue;
                }
            }
            state.sourceX = x;
            state.sourceZ = z;
            state.resolved = true;
            return true;
        }
        return false;
    }

    private TravelingWaveFront spawn(SourceFronts state,
            long tick, WaveProfile profile, OvalWaveSettings settings,
            double windX, double windZ,
            WaveLakeFlowCache.Snapshot topology, boolean fadeIn,
            List<String> sourceTransitions) {
        Candidate best = null;
        long sequence = state.spawnSequence;
        long waveSeed = state.seed ^ sequence;
        TravelingWaveFront.Kind kind = kindFor(sequence);
        double minimumSeparation = profile.wavelength() * switch (kind) {
            case CROSSING -> 0.30D;
            case GIANT -> 0.55D;
            case STANDARD, MERGED -> 0.42D;
        };
        int searchRadius = Math.max(12, Math.min(36, profile.wavelength()));
        for (int attempt = 0; attempt < 96; attempt++) {
            double angle = hash01(waveSeed, attempt * 2L) * Math.PI * 2.0D;
            double radial = attempt == 0 ? 0.0D
                    : 0.12D + (hash01(waveSeed, (attempt * 2L) + 1L) * 0.88D);
            int x = state.sourceX
                    + (int) Math.round(Math.cos(angle) * searchRadius * radial);
            int z = state.sourceZ
                    + (int) Math.round(Math.sin(angle) * searchRadius * radial);
            if (!topology.isKnown(x, z) || !topology.isWater(x, z)
                    || tooClose(state.fronts, x, z, minimumSeparation)) {
                continue;
            }
            LakeWaveFlowField.Cell cell = topology.cell(x, z);
            double shoreScore = cell.shoreGuided()
                    ? 80.0D - Math.abs(30.0D - cell.shoreDistance())
                    : 20.0D;
            double sourceScore = 12.0D * (1.0D - radial);
            double score = shoreScore + sourceScore + hash01(state.seed, attempt + sequence) * 4.0D;
            if (best == null || score > best.score()) {
                best = new Candidate(x, z, cell, score);
            }
        }
        if (best == null) {
            return null;
        }
        best = adjustSmallWaterStart(best, topology, state.fronts,
                minimumSeparation, sourceTransitions);

        double shapeSeed = hash01(waveSeed, 901L);
        double widthMinimum = Math.max(0.42D, settings.widthMinScale());
        double widthMaximum = Math.max(widthMinimum + 0.08D, settings.widthMaxScale());
        double lengthMinimum = Math.max(0.38D, settings.lengthMinScale());
        double lengthMaximum = Math.max(lengthMinimum + 0.10D, settings.lengthMaxScale());
        double halfWidth = profile.wavelength()
                * lerp(widthMinimum, widthMaximum, shapeSeed);
        double halfLength = Math.max(7.0D,
                (profile.wavelength() / profile.frequency())
                        * lerp(lengthMinimum, lengthMaximum, hash01(waveSeed, 902L)));
        if (kind == TravelingWaveFront.Kind.GIANT) {
            halfWidth *= 1.35D;
            halfLength *= 1.20D;
        } else if (kind == TravelingWaveFront.Kind.CROSSING) {
            halfWidth *= 1.08D;
        }
        double targetHalfWidth = halfWidth;
        double targetHalfLength = halfLength;
        double energy = Math.max(0.72D, Math.min(1.0D,
                profile.amplitude() * (0.90D + (hash01(waveSeed, 903L) * 0.16D))));
        if (kind == TravelingWaveFront.Kind.GIANT) {
            energy = Math.max(0.92D, energy);
        }
        PassageMeasurement spawnPassage = localPassage(
                topology, best.x(), best.z());
        boolean channelLocked = isSmallEnclosedWater(best.cell(), spawnPassage);
        boolean largeOpenWater = !channelLocked
                && isLargeOpenWater(best.cell(), spawnPassage);
        ShoreAngleDecision shoreAngles = largeOpenWater
                ? shoreAngleDecision(topology, best.x(), best.z())
                : ShoreAngleDecision.notApplicable();
        boolean openWaterFan = largeOpenWater && shoreAngles.requiresFan();
        if (openWaterFan) {
            double startScale = switch (kind) {
                case CROSSING -> 0.44D;
                case GIANT -> 0.48D;
                case STANDARD, MERGED -> 0.50D;
            };
            halfWidth *= startScale;
            halfLength *= Math.min(0.62D, startScale + 0.12D);
        }
        TravelingWaveFront.Direction channelCourse = channelLocked
                ? resolveChannelCourse(state, spawnPassage, windX, windZ)
                : new TravelingWaveFront.Direction(windX, windZ);
        boolean guided = !channelLocked && best.cell().shoreGuided()
                && best.cell().shoreDistance() <= SHORE_STEERING_DISTANCE
                && (best.cell().directionX() != 0 || best.cell().directionZ() != 0);
        double baseHeadingX = channelLocked ? channelCourse.x()
                : guided ? best.cell().directionX() : windX;
        double baseHeadingZ = channelLocked ? channelCourse.z()
                : guided ? best.cell().directionZ() : windZ;
        double courseBias = courseBias(kind, waveSeed);
        if (openWaterFan) {
            courseBias = Math.max(-OPEN_WATER_MAX_FAN_ANGLE,
                    Math.min(OPEN_WATER_MAX_FAN_ANGLE, courseBias));
        }
        double baseAngle = Math.atan2(baseHeadingZ, baseHeadingX);
        double headingX = Math.cos(baseAngle + courseBias);
        double headingZ = Math.sin(baseAngle + courseBias);
        TravelingWaveFront front = new TravelingWaveFront(
                state.nextId++, kind, courseBias,
                best.x(), best.z(), headingX, headingZ,
                halfLength, halfWidth, energy, tick, false,
                fadeIn ? FRONT_FADE_IN_TICKS : 0L);
        if (openWaterFan) {
            double expansionDistance = Math.max(32.0D,
                    Math.min(56.0D, profile.wavelength() * 1.25D));
            front.beginOpenWaterExpansion(
                    targetHalfLength, targetHalfWidth, expansionDistance);
            sourceTransitions.add("[SOURCE][OPEN-WATER-FAN] id=" + front.id()
                    + " kind=" + front.kind()
                    + " reason=multiple-shore-angles"
                    + " direction-samples=" + shoreAngles.directionSamples()
                    + " direction-sectors=" + shoreAngles.directionSectors()
                    + " coherence=" + rounded(shoreAngles.coherence())
                    + " angle-degrees="
                    + rounded(Math.toDegrees(courseBias))
                    + " start-half-width=" + rounded(front.halfWidth())
                    + " target-half-width=" + rounded(targetHalfWidth)
                    + " expansion-distance=" + rounded(expansionDistance));
        } else if (largeOpenWater) {
            sourceTransitions.add("[SOURCE][BROAD-WAVE] id=" + front.id()
                    + " kind=" + front.kind()
                    + " reason=" + shoreAngles.reason()
                    + " known-ratio=" + rounded(shoreAngles.knownRatio())
                    + " direction-samples=" + shoreAngles.directionSamples()
                    + " direction-sectors=" + shoreAngles.directionSectors()
                    + " coherence=" + rounded(shoreAngles.coherence())
                    + " half-width=" + rounded(front.halfWidth()));
        }
        state.spawnSequence++;
        if (channelLocked) {
            front.lockChannelCourse(channelCourse.x(), channelCourse.z());
            sourceTransitions.add(channelLockMarker(front, spawnPassage,
                    topology, best.x(), best.z()));
        } else if (guided) {
            front.acquireShoreTarget(best.cell().directionX(), best.cell().directionZ(),
                    best.cell().shoreDistance());
        }
        return front;
    }
    private Candidate adjustSmallWaterStart(Candidate original,
            WaveLakeFlowCache.Snapshot topology,
            List<TravelingWaveFront> existingFronts, double minimumSeparation,
            List<String> transitions) {
        int localSpan = localWaterSpan(topology, original.x(), original.z());
        if (localSpan < 1 || localSpan > SMALL_ENCLOSED_WATER_SPAN
                || !original.cell().shoreGuided()
                || original.cell().sourceDistance() < 1
                || original.cell().sourceDistance() > SMALL_ENCLOSED_MAX_SOURCE_DISTANCE) {
            return original;
        }
        int desiredDistance = Math.max(SMALL_WATER_MIN_START_DISTANCE,
                Math.min(SMALL_WATER_MAX_START_DISTANCE,
                        (int) Math.round(localSpan * 0.30D)));
        if (original.cell().shoreDistance() <= desiredDistance) {
            return original;
        }

        int x = original.x();
        int z = original.z();
        LakeWaveFlowField.Cell cell = original.cell();
        int startDistance = cell.shoreDistance();
        double compactSeparation = Math.max(4.0D, minimumSeparation * 0.35D);
        while (cell.shoreDistance() > desiredDistance
                && (cell.directionX() != 0 || cell.directionZ() != 0)) {
            int nextX = x + cell.directionX();
            int nextZ = z + cell.directionZ();
            if (!topology.isKnown(nextX, nextZ) || !topology.isWater(nextX, nextZ)
                    || tooClose(existingFronts, nextX, nextZ, compactSeparation)) {
                break;
            }
            LakeWaveFlowField.Cell next = topology.cell(nextX, nextZ);
            if (!next.shoreGuided()
                    || next.shoreDistance() >= cell.shoreDistance()) {
                break;
            }
            x = nextX;
            z = nextZ;
            cell = next;
        }
        if (x == original.x() && z == original.z()) {
            return original;
        }
        transitions.add("[SOURCE][ENCLOSED-WATER] action=shoreward-start"
                + " bank-span=" + localSpan
                + " start-distance=" + startDistance
                + " adjusted-distance=" + cell.shoreDistance()
                + " from=" + original.x() + "," + original.z()
                + " to=" + x + "," + z);
        return new Candidate(x, z, cell, original.score());
    }
    private int nearbyFrontCount(List<TravelingWaveFront> fronts,
            TravelingWaveFront subject, int waterSpan) {
        if (waterSpan <= 0) {
            return 1;
        }
        int nearby = 0;
        for (TravelingWaveFront candidate : fronts) {
            double overlapReach = Math.max(waterSpan * 2.0D,
                    Math.min(48.0D,
                            (subject.halfWidth() + candidate.halfWidth()) * 0.65D));
            if (distance(subject.x(), subject.z(), candidate.x(), candidate.z())
                    <= overlapReach) {
                nearby++;
            }
        }
        return Math.max(1, nearby);
    }
    int localWaterSpan(WaveLakeFlowCache.Snapshot topology,
            int centerX, int centerZ) {
        return localPassage(topology, centerX, centerZ).span();
    }

    private PassageMeasurement localPassage(
            WaveLakeFlowCache.Snapshot topology, int centerX, int centerZ) {
        PassageMeasurement narrowest = PassageMeasurement.open();
        for (int[] axis : PASSAGE_AXES) {
            int forward = bankDistance(topology, centerX, centerZ, axis[0], axis[1]);
            int backward = bankDistance(topology, centerX, centerZ, -axis[0], -axis[1]);
            if (forward < 0 || backward < 0) {
                continue;
            }
            double scale = axis[0] != 0 && axis[1] != 0 ? Math.sqrt(2.0D) : 1.0D;
            int span = (int) Math.ceil((forward + backward - 1) * scale);
            if (narrowest.span() < 0 || span < narrowest.span()) {
                double channelX = -axis[1];
                double channelZ = axis[0];
                double magnitude = Math.hypot(channelX, channelZ);
                narrowest = new PassageMeasurement(span,
                        channelX / magnitude, channelZ / magnitude);
            }
        }
        return narrowest;
    }

    private boolean isLargeOpenWater(LakeWaveFlowField.Cell cell,
            PassageMeasurement passage) {
        if (!cell.shoreGuided()) {
            return true;
        }
        return cell.sourceDistance() > SMALL_ENCLOSED_MAX_SOURCE_DISTANCE
                || passage.span() < 0
                || passage.span() > SMALL_ENCLOSED_WATER_SPAN;
    }
    // ## A broad front is the default. It only divides into smaller angled fronts
    // when a well-known local water field contains materially different coast paths.
    ShoreAngleDecision shoreAngleDecision(WaveLakeFlowCache.Snapshot topology,
            int centerX, int centerZ) {
        int inspected = 0;
        int known = 0;
        int directionSamples = 0;
        int[] sectors = new int[8];
        double sumX = 0.0D;
        double sumZ = 0.0D;
        for (int dz = -SHORE_ANGLE_PROBE_RADIUS;
                dz <= SHORE_ANGLE_PROBE_RADIUS; dz += SHORE_ANGLE_PROBE_STEP) {
            for (int dx = -SHORE_ANGLE_PROBE_RADIUS;
                    dx <= SHORE_ANGLE_PROBE_RADIUS; dx += SHORE_ANGLE_PROBE_STEP) {
                if ((dx * dx) + (dz * dz)
                        > SHORE_ANGLE_PROBE_RADIUS * SHORE_ANGLE_PROBE_RADIUS) {
                    continue;
                }
                inspected++;
                int x = centerX + dx;
                int z = centerZ + dz;
                if (!topology.isKnown(x, z)) {
                    continue;
                }
                known++;
                if (!topology.isWater(x, z)) {
                    continue;
                }
                LakeWaveFlowField.Cell cell = topology.cell(x, z);
                if (!cell.shoreGuided()
                        || (cell.directionX() == 0 && cell.directionZ() == 0)) {
                    continue;
                }
                double magnitude = Math.hypot(cell.directionX(), cell.directionZ());
                double directionX = cell.directionX() / magnitude;
                double directionZ = cell.directionZ() / magnitude;
                sumX += directionX;
                sumZ += directionZ;
                directionSamples++;
                double angle = Math.atan2(directionZ, directionX);
                int sector = Math.floorMod((int) Math.floor(
                        (angle + (Math.PI / 8.0D)) / (Math.PI / 4.0D)), 8);
                sectors[sector]++;
            }
        }

        double knownRatio = inspected == 0 ? 0.0D : known / (double) inspected;
        int representedSectors = 0;
        int meaningfulMinimum = Math.max(3,
                (int) Math.ceil(directionSamples * 0.12D));
        for (int count : sectors) {
            representedSectors += count >= meaningfulMinimum ? 1 : 0;
        }
        double coherence = directionSamples == 0 ? 1.0D
                : Math.hypot(sumX, sumZ) / directionSamples;
        if (knownRatio < SHORE_ANGLE_MINIMUM_KNOWN_RATIO
                || directionSamples < SHORE_ANGLE_MINIMUM_SAMPLES) {
            return new ShoreAngleDecision(false, "insufficient-topology",
                    knownRatio, directionSamples, representedSectors, coherence);
        }
        if (representedSectors < 2 || coherence > SHORE_ANGLE_MAXIMUM_COHERENCE) {
            return new ShoreAngleDecision(false, "coherent-single-direction",
                    knownRatio, directionSamples, representedSectors, coherence);
        }
        return new ShoreAngleDecision(true, "multiple-shore-angles",
                knownRatio, directionSamples, representedSectors, coherence);
    }

    private boolean isSmallEnclosedWater(LakeWaveFlowField.Cell cell,
            PassageMeasurement passage) {
        return cell.shoreGuided()
                && passage.span() > 0
                && passage.span() <= SMALL_ENCLOSED_WATER_SPAN
                && cell.sourceDistance() > 0
                && cell.sourceDistance() <= SMALL_ENCLOSED_MAX_SOURCE_DISTANCE;
    }
    private TravelingWaveFront.Direction resolveChannelCourse(
            SourceFronts state, PassageMeasurement passage,
            double windX, double windZ) {
        if (!state.channelCourseResolved) {
            double projection = (windX * passage.channelX())
                    + (windZ * passage.channelZ());
            double sign = Math.abs(projection) >= 0.15D
                    ? Math.copySign(1.0D, projection)
                    : (state.seed & 1L) == 0L ? 1.0D : -1.0D;
            state.channelCourseX = passage.channelX() * sign;
            state.channelCourseZ = passage.channelZ() * sign;
            state.channelCourseResolved = true;
        }
        return new TravelingWaveFront.Direction(
                state.channelCourseX, state.channelCourseZ);
    }

    private String channelLockMarker(TravelingWaveFront front,
            PassageMeasurement passage, WaveLakeFlowCache.Snapshot topology,
            int x, int z) {
        TravelingWaveFront.Direction course = front.channelCourse();
        return "[STEER][CHANNEL-LOCK] id=" + front.id()
                + " bank-span=" + passage.span()
                + " pointer-variants=" + localPointerVariants(topology, x, z)
                + " heading=" + rounded(course.x()) + "," + rounded(course.z());
    }

    private int localPointerVariants(WaveLakeFlowCache.Snapshot topology,
            int centerX, int centerZ) {
        Set<Integer> variants = new LinkedHashSet<>();
        int[][] offsets = {
                {0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3},
                {3, 3}, {3, -3}, {-3, 3}, {-3, -3}
        };
        for (int[] offset : offsets) {
            int x = centerX + offset[0];
            int z = centerZ + offset[1];
            if (!topology.isKnown(x, z) || !topology.isWater(x, z)) {
                continue;
            }
            LakeWaveFlowField.Cell cell = topology.cell(x, z);
            if (cell.shoreGuided()
                    && (cell.directionX() != 0 || cell.directionZ() != 0)) {
                variants.add(((cell.directionX() + 1) * 3)
                        + cell.directionZ() + 1);
            }
        }
        return variants.size();
    }

    private int bankDistance(WaveLakeFlowCache.Snapshot topology,
            int centerX, int centerZ, int stepX, int stepZ) {
        for (int distance = 1; distance <= PASSAGE_PROBE_REACH; distance++) {
            int x = centerX + (stepX * distance);
            int z = centerZ + (stepZ * distance);
            if (!topology.isKnown(x, z)) {
                return -1;
            }
            if (!topology.isWater(x, z)) {
                return distance;
            }
        }
        return -1;
    }
    private String steeringMarker(TravelingWaveFront front,
            TravelingWaveFront.Motion motion) {
        String tag = motion.steeringSignal()
                == TravelingWaveFront.SteeringSignal.SHORE_REACHED
                ? "[STEER][OVERSHOOT-PREVENTED]"
                : "[STEER][REVERSE][ORBIT-PREVENTED]";
        return tag + " id=" + front.id()
                + " kind=" + front.kind()
                + " distance=" + rounded(motion.shoreDistance())
                + " alignment=" + rounded(motion.shoreAlignment())
                + " at=" + rounded(front.x()) + "," + rounded(front.z())
                + " target=" + rounded(front.shoreTargetX()) + ","
                + rounded(front.shoreTargetZ());
    }

    private double rounded(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
    private int mergeCollidingFronts(SourceFronts state, long tick,
            WaveLakeFlowCache.Snapshot topology, List<String> transitions) {
        if (tick < state.nextMergeTick) {
            return 0;
        }
        for (int firstIndex = 0; firstIndex < state.fronts.size(); firstIndex++) {
            TravelingWaveFront first = state.fronts.get(firstIndex);
            if (!first.mergeEligible(tick)) {
                continue;
            }
            for (int secondIndex = firstIndex + 1;
                    secondIndex < state.fronts.size(); secondIndex++) {
                TravelingWaveFront second = state.fronts.get(secondIndex);
                if (!second.mergeEligible(tick) || !crossingAngle(first, second)) {
                    continue;
                }
                Collision collision = findCollision(first, second, tick, topology);
                if (collision == null) {
                    continue;
                }
                TravelingWaveFront combined = mergeFronts(
                        state.nextId++, first, second, collision, tick, topology);
                state.fronts.remove(secondIndex);
                state.fronts.remove(firstIndex);
                state.fronts.add(combined);
                transitions.add(first.id() + "+" + second.id() + ">" + combined.id());
                state.nextMergeTick = tick + MERGE_COOLDOWN_TICKS;
                return 1;
            }
        }
        return 0;
    }

    private boolean crossingAngle(TravelingWaveFront first, TravelingWaveFront second) {
        double dot = (first.headingX() * second.headingX())
                + (first.headingZ() * second.headingZ());
        // ## Nearly parallel fronts layer naturally; nearly opposing fronts break.
        // Different destination coasts remain separate instead of merging and reversing.
        return dot < 0.94D && dot > -0.85D
                && shoreTargetsCompatible(first, second);
    }

    private boolean shoreTargetsCompatible(TravelingWaveFront first,
            TravelingWaveFront second) {
        if (!first.hasShoreTarget() || !second.hasShoreTarget()) {
            return true;
        }
        double targetSeparation = distance(first.shoreTargetX(), first.shoreTargetZ(),
                second.shoreTargetX(), second.shoreTargetZ());
        double sameCoastReach = Math.max(16.0D,
                Math.min(48.0D, (first.halfWidth() + second.halfWidth()) * 0.75D));
        if (targetSeparation <= sameCoastReach) {
            return true;
        }
        TravelingWaveFront.Direction firstDirection = first.lockedShoreDirection();
        TravelingWaveFront.Direction secondDirection = second.lockedShoreDirection();
        double directionDot = (firstDirection.x() * secondDirection.x())
                + (firstDirection.z() * secondDirection.z());
        return directionDot >= 0.75D;
    }

    private Collision findCollision(TravelingWaveFront first,
            TravelingWaveFront second, long tick, WaveLakeFlowCache.Snapshot topology) {
        double maximumCenterDistance = first.halfWidth() + second.halfWidth()
                + first.halfLength() + second.halfLength();
        if (distance(first.x(), first.z(), second.x(), second.z()) > maximumCenterDistance) {
            return null;
        }
        int minX = (int) Math.floor(Math.max(first.x() - first.boundsX(),
                second.x() - second.boundsX()));
        int maxX = (int) Math.ceil(Math.min(first.x() + first.boundsX(),
                second.x() + second.boundsX()));
        int minZ = (int) Math.floor(Math.max(first.z() - first.boundsZ(),
                second.z() - second.boundsZ()));
        int maxZ = (int) Math.ceil(Math.min(first.z() + first.boundsZ(),
                second.z() + second.boundsZ()));
        if (minX > maxX || minZ > maxZ) {
            return null;
        }
        Collision strongest = null;
        double strongestOverlap = 0.0D;
        for (int x = minX; x <= maxX; x += 2) {
            for (int z = minZ; z <= maxZ; z += 2) {
                if (!topology.isWater(x, z)) {
                    continue;
                }
                double firstStrength = first.strengthAt(x + 0.5D, z + 0.5D, tick);
                if (firstStrength < 0.12D) {
                    continue;
                }
                double secondStrength = second.strengthAt(x + 0.5D, z + 0.5D, tick);
                double overlap = firstStrength * secondStrength;
                if (secondStrength >= 0.12D && overlap > strongestOverlap) {
                    strongestOverlap = overlap;
                    strongest = new Collision(x + 0.5D, z + 0.5D, overlap);
                }
            }
        }
        return strongest;
    }

    private TravelingWaveFront preferredShoreTarget(TravelingWaveFront first,
            TravelingWaveFront second, Collision collision) {
        double combinedX = first.headingX() + second.headingX();
        double combinedZ = first.headingZ() + second.headingZ();
        double magnitude = Math.hypot(combinedX, combinedZ);
        if (magnitude <= 0.001D) {
            double firstDistance = distance(collision.x(), collision.z(),
                    first.shoreTargetX(), first.shoreTargetZ());
            double secondDistance = distance(collision.x(), collision.z(),
                    second.shoreTargetX(), second.shoreTargetZ());
            return firstDistance <= secondDistance ? first : second;
        }
        combinedX /= magnitude;
        combinedZ /= magnitude;
        double firstScore = targetScore(first, collision, combinedX, combinedZ);
        double secondScore = targetScore(second, collision, combinedX, combinedZ);
        return firstScore >= secondScore ? first : second;
    }

    private double targetScore(TravelingWaveFront front, Collision collision,
            double headingX, double headingZ) {
        double dx = front.shoreTargetX() - collision.x();
        double dz = front.shoreTargetZ() - collision.z();
        double distance = Math.hypot(dx, dz);
        if (distance <= 0.001D) {
            return Double.POSITIVE_INFINITY;
        }
        double alignment = ((dx / distance) * headingX) + ((dz / distance) * headingZ);
        return (alignment * 4.0D) - (distance * 0.01D);
    }
    private TravelingWaveFront mergeFronts(long id, TravelingWaveFront first,
            TravelingWaveFront second, Collision collision, long tick,
            WaveLakeFlowCache.Snapshot topology) {
        boolean hasTarget = first.hasShoreTarget() || second.hasShoreTarget();
        double targetX = 0.0D;
        double targetZ = 0.0D;
        if (first.hasShoreTarget() && second.hasShoreTarget()) {
            TravelingWaveFront preferred = preferredShoreTarget(first, second, collision);
            targetX = preferred.shoreTargetX();
            targetZ = preferred.shoreTargetZ();
        } else if (first.hasShoreTarget()) {
            targetX = first.shoreTargetX();
            targetZ = first.shoreTargetZ();
        } else if (second.hasShoreTarget()) {
            targetX = second.shoreTargetX();
            targetZ = second.shoreTargetZ();
        }

        double headingX = hasTarget ? targetX - collision.x()
                : first.headingX() + second.headingX();
        double headingZ = hasTarget ? targetZ - collision.z()
                : first.headingZ() + second.headingZ();
        double magnitude = Math.hypot(headingX, headingZ);
        if (magnitude <= 0.001D) {
            headingX = first.headingX();
            headingZ = first.headingZ();
            magnitude = 1.0D;
        }
        headingX /= magnitude;
        headingZ /= magnitude;

        double mergedHalfWidth = Math.min(64.0D,
                Math.max(first.halfWidth(), second.halfWidth())
                        + (Math.min(first.halfWidth(), second.halfWidth()) * 0.45D));
        double mergedHalfLength = Math.max(first.halfLength(), second.halfLength()) * 1.10D;
        TravelingWaveFront.Kind mergedKind = first.kind() == TravelingWaveFront.Kind.GIANT
                || second.kind() == TravelingWaveFront.Kind.GIANT
                ? TravelingWaveFront.Kind.GIANT : TravelingWaveFront.Kind.MERGED;
        // The new crescent's leading ridge is centered on the strongest overlap.
        double centerX = first.x();
        double centerZ = first.z();
        double[] offsets = {0.58D, 0.42D, 0.26D, 0.10D, 0.0D};
        for (double offset : offsets) {
            double candidateX = collision.x() - (headingX * mergedHalfLength * offset);
            double candidateZ = collision.z() - (headingZ * mergedHalfLength * offset);
            int candidateBlockX = (int) Math.round(candidateX);
            int candidateBlockZ = (int) Math.round(candidateZ);
            if (topology.isWater(candidateBlockX, candidateBlockZ)) {
                centerX = candidateBlockX;
                centerZ = candidateBlockZ;
                break;
            }
        }
        TravelingWaveFront combined = new TravelingWaveFront(id, mergedKind, 0.0D,
                centerX, centerZ, headingX, headingZ,
                mergedHalfLength, mergedHalfWidth, 0.98D, tick, true,
                FRONT_FADE_IN_TICKS);
        if (hasTarget) {
            combined.inheritShoreTarget(targetX, targetZ);
        }
        return combined;
    }

    private void trimDormantSources() {
        if (bySource.size() <= SOURCE_CACHE_LIMIT) {
            return;
        }
        long cutoff = System.currentTimeMillis() - SOURCE_RETENTION_MILLIS;
        bySource.entrySet().removeIf(entry ->
                entry.getValue().lastViewedMillis < cutoff);
    }

    private static double distanceStatic(
            double x1, double z1, double x2, double z2) {
        return Math.hypot(x1 - x2, z1 - z2);
    }

    private static long sourceSeed(SourceKey key) {
        long value = key.worldId().getMostSignificantBits()
                ^ Long.rotateLeft(key.worldId().getLeastSignificantBits(), 17)
                ^ ((long) key.cellX() * 0x9E3779B97F4A7C15L)
                ^ ((long) key.cellZ() * 0xC2B2AE3D27D4EB4FL);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
    private TravelingWaveFront.Kind kindFor(long sequence) {
        if (sequence % 6L == 0L) {
            return TravelingWaveFront.Kind.GIANT;
        }
        if (sequence % 3L == 0L) {
            return TravelingWaveFront.Kind.CROSSING;
        }
        return TravelingWaveFront.Kind.STANDARD;
    }

    private double courseBias(TravelingWaveFront.Kind kind, long sequence) {
        double variation = hash01(sequence, 904L);
        return switch (kind) {
            case CROSSING -> {
                double sign = ((sequence / 3L) & 1L) == 0L ? 1.0D : -1.0D;
                yield Math.toRadians(sign * (24.0D + (variation * 14.0D)));
            }
            case GIANT -> Math.toRadians((variation * 14.0D) - 7.0D);
            case STANDARD -> Math.toRadians((variation * 24.0D) - 12.0D);
            case MERGED -> 0.0D;
        };
    }

    private boolean tooClose(List<TravelingWaveFront> fronts, int x, int z, double minimum) {
        for (TravelingWaveFront front : fronts) {
            if (distance(front.x(), front.z(), x, z) < minimum) {
                return true;
            }
        }
        return false;
    }

    private double lerp(double start, double end, double amount) {
        double clamped = Math.max(0.0D, Math.min(1.0D, amount));
        return start + ((end - start) * clamped);
    }

    private double distance(double x1, double z1, double x2, double z2) {
        return Math.hypot(x1 - x2, z1 - z2);
    }

    private DirectionSummary directionSummary(List<TravelingWaveFront> fronts) {
        int[] sectors = new int[8];
        for (TravelingWaveFront front : fronts) {
            double angle = Math.atan2(front.headingZ(), front.headingX());
            int sector = Math.floorMod(
                    (int) Math.floor((angle + (Math.PI / 8.0D)) / (Math.PI / 4.0D)), 8);
            sectors[sector]++;
        }
        // ## Minecraft coordinates use +X for east and +Z for south.
        return new DirectionSummary(
                sectors[6], sectors[7], sectors[0], sectors[1],
                sectors[2], sectors[3], sectors[4], sectors[5]);
    }

    private double hash01(long first, long second) {
        long value = (first * 0x9E3779B97F4A7C15L) ^ (second * 0xC2B2AE3D27D4EB4FL);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    record Update(List<TravelingWaveFront> fronts, double movedBlocks,
            int shoreGuidedFronts, int shoreImpacts, int mergedFronts,
            Lifecycle lifecycle, DirectionSummary directions,
            SourceSummary sources) {
    }

    record Lifecycle(List<Long> spawnedIds, List<Long> expiredIds,
            List<Long> distanceCulledIds, List<Long> shoreFizzleIds,
            List<Long> distanceFizzleIds, List<String> mergeTransitions,
            List<String> steeringTransitions, long mergeCooldownTicks,
            long spawnCooldownTicks, boolean spawnDeferred) {
        Lifecycle {
            spawnedIds = List.copyOf(spawnedIds);
            expiredIds = List.copyOf(expiredIds);
            distanceCulledIds = List.copyOf(distanceCulledIds);
            shoreFizzleIds = List.copyOf(shoreFizzleIds);
            distanceFizzleIds = List.copyOf(distanceFizzleIds);
            mergeTransitions = List.copyOf(mergeTransitions);
            steeringTransitions = List.copyOf(steeringTransitions);
        }

        boolean hasEvents() {
            return !spawnedIds.isEmpty() || !expiredIds.isEmpty()
                    || !distanceCulledIds.isEmpty() || !shoreFizzleIds.isEmpty()
                    || !distanceFizzleIds.isEmpty() || !mergeTransitions.isEmpty()
                    || !steeringTransitions.isEmpty();
        }

        String summary() {
            return "born=" + spawnedIds + " expired=" + expiredIds
                    + " viewer-distance-hidden=" + distanceCulledIds
                    + " shore-fizzle=" + shoreFizzleIds
                    + " distance-fizzle=" + distanceFizzleIds
                    + " merges=" + mergeTransitions
                    + " steering=" + steeringTransitions
                    + " merge-cooldown=" + mergeCooldownTicks
                    + " spawn-cooldown=" + spawnCooldownTicks
                    + " spawn-deferred=" + spawnDeferred;
        }
    }

    record DirectionSummary(int north, int northEast, int east, int southEast,
            int south, int southWest, int west, int northWest) {
        int cardinal() {
            return north + east + south + west;
        }

        int diagonal() {
            return northEast + southEast + southWest + northWest;
        }

        String summary() {
            return "N=" + north + " NE=" + northEast + " E=" + east
                    + " SE=" + southEast + " S=" + south + " SW=" + southWest
                    + " W=" + west + " NW=" + northWest;
        }
    }

    record FrontSample(double strength, boolean fizzling, int contributors) {
    }

    private record Collision(double x, double z, double strength) {
    }

    private record PassageMeasurement(int span,
            double channelX, double channelZ) {
        private static PassageMeasurement open() {
            return new PassageMeasurement(-1, 0.0D, 0.0D);
        }
    }

    record ShoreAngleDecision(boolean requiresFan, String reason,
            double knownRatio, int directionSamples, int directionSectors,
            double coherence) {
        private static ShoreAngleDecision notApplicable() {
            return new ShoreAngleDecision(false, "not-applicable",
                    1.0D, 0, 0, 1.0D);
        }
    }

    private record Candidate(int x, int z, LakeWaveFlowField.Cell cell, double score) {
    }

    record SourceSummary(int activeSources, int visibleFronts,
            int sourceCellSize, List<String> anchors) {
        SourceSummary {
            anchors = List.copyOf(anchors);
        }

        String summary() {
            return "static-sources=" + activeSources
                    + " visible-fronts=" + visibleFronts
                    + " grid=" + sourceCellSize
                    + " anchors=" + anchors;
        }
    }

    record SourceCoordinate(UUID worldId, int cellX, int cellZ,
            int centerX, int centerZ) {
    }

    private record SourceUpdate(double movedBlocks, int shoreGuidedFronts,
            int shoreImpacts, int mergedFronts, Lifecycle lifecycle) {
    }

    private record SourceKey(UUID worldId, int cellX, int cellZ) {
        int centerX() {
            return cellX * SOURCE_CELL_SIZE;
        }

        int centerZ() {
            return cellZ * SOURCE_CELL_SIZE;
        }
    }

    private static final class SourceFronts {
        private final SourceKey key;
        private final long seed;
        private final List<TravelingWaveFront> fronts = new ArrayList<>();
        private long nextId;
        private long spawnSequence = 1L;
        private long nextMergeTick;
        private long nextSpawnTick;
        private volatile long lastViewedMillis = System.currentTimeMillis();
        private boolean resolved;
        private int sourceX;
        private int sourceZ;
        private boolean channelCourseResolved;
        private double channelCourseX;
        private double channelCourseZ;

        private SourceFronts(SourceKey key) {
            this.key = key;
            this.seed = sourceSeed(key);
            this.nextId = ((seed & 0x001FFFFFFFFFFFFFL) << 8) + 1L;
        }
    }}
