package org.evolution.features.waves;

final class TravelingWaveFront {
    private static final double MAX_TURN_RADIANS_PER_SECOND = Math.toRadians(24.0D);
    private static final double MAX_GUIDED_BIAS_RADIANS = Math.toRadians(12.0D);
    private static final double SHORE_CONTACT_DISTANCE = 2.5D;
    private static final int SHORE_STEERING_DISTANCE = 48;
    private static final long SHORE_FIZZLE_TICKS = 34L;
    private static final long DISTANCE_FIZZLE_TICKS = 50L;

    private final long id;
    private final Kind kind;
    private final double courseBiasRadians;
    private double halfLength;
    private double halfWidth;
    private final double baseEnergy;
    private final long createdTick;
    private final boolean mergeResult;
    private final long fadeInTicks;
    private double x;
    private double z;
    private double headingX;
    private double headingZ;
    private double travelled;
    private long lastTick;
    private long fizzleStartedTick = -1L;
    private long fizzleDurationTicks = SHORE_FIZZLE_TICKS;
    private boolean shoreGuided;
    private boolean shoreTargetLocked;
    private double shoreTargetX;
    private double shoreTargetZ;
    private boolean narrowPassageLocked;
    private double passageHalfLength;
    private double passageHalfWidth;
    private int passageCrowding = 1;
    private long lastPassageProbeSlot = Long.MIN_VALUE;
    private boolean channelCourseLocked;
    private double channelCourseX;
    private double channelCourseZ;
    private boolean openWaterFan;
    private boolean openWaterExpanding;
    private double openWaterStartHalfLength;
    private double openWaterStartHalfWidth;
    private double openWaterTargetHalfLength;
    private double openWaterTargetHalfWidth;
    private double openWaterExpansionDistance;
    private double openWaterExpansionStartTravelled;

    TravelingWaveFront(long id, double x, double z, double headingX, double headingZ,
            double halfLength, double halfWidth, double baseEnergy, long tick) {
        this(id, Kind.STANDARD, 0.0D, x, z, headingX, headingZ,
                halfLength, halfWidth, baseEnergy, tick);
    }

    TravelingWaveFront(long id, Kind kind, double courseBiasRadians,
            double x, double z, double headingX, double headingZ,
            double halfLength, double halfWidth, double baseEnergy, long tick) {
        this(id, kind, courseBiasRadians, x, z, headingX, headingZ,
                halfLength, halfWidth, baseEnergy, tick, false, 0L);
    }

    TravelingWaveFront(long id, Kind kind, double courseBiasRadians,
            double x, double z, double headingX, double headingZ,
            double halfLength, double halfWidth, double baseEnergy, long tick,
            boolean mergeResult, long fadeInTicks) {
        this.id = id;
        this.kind = kind == null ? Kind.STANDARD : kind;
        this.courseBiasRadians = courseBiasRadians;
        this.x = x;
        this.z = z;
        double magnitude = Math.hypot(headingX, headingZ);
        this.headingX = magnitude > 0.001D ? headingX / magnitude : 1.0D;
        this.headingZ = magnitude > 0.001D ? headingZ / magnitude : 0.0D;
        this.halfLength = Math.max(4.0D, halfLength);
        this.halfWidth = Math.max(8.0D, halfWidth);
        this.baseEnergy = clamp(baseEnergy);
        this.createdTick = tick;
        this.mergeResult = mergeResult;
        this.fadeInTicks = Math.max(0L, fadeInTicks);
        this.lastTick = tick;
    }

    void acquireShoreTarget(double directionX, double directionZ, int shoreDistance) {
        if (shoreTargetLocked || shoreDistance < 1) {
            return;
        }
        double magnitude = Math.hypot(directionX, directionZ);
        if (magnitude <= 0.001D) {
            return;
        }
        double distance = Math.max(2.0D, shoreDistance);
        shoreTargetX = x + ((directionX / magnitude) * distance);
        shoreTargetZ = z + ((directionZ / magnitude) * distance);
        shoreTargetLocked = true;
    }

    void inheritShoreTarget(double targetX, double targetZ) {
        shoreTargetX = targetX;
        shoreTargetZ = targetZ;
        shoreTargetLocked = true;
    }

    void beginOpenWaterExpansion(double targetHalfLength,
            double targetHalfWidth, double expansionDistance) {
        if (narrowPassageLocked || fizzling()) {
            return;
        }
        openWaterFan = true;
        openWaterStartHalfLength = halfLength;
        openWaterStartHalfWidth = halfWidth;
        openWaterTargetHalfLength = Math.max(halfLength, targetHalfLength);
        openWaterTargetHalfWidth = Math.max(halfWidth, targetHalfWidth);
        openWaterExpansionDistance = Math.max(8.0D, expansionDistance);
        openWaterExpansionStartTravelled = travelled;
        openWaterExpanding = openWaterTargetHalfLength > halfLength + 0.01D
                || openWaterTargetHalfWidth > halfWidth + 0.01D;
    }

    private void updateOpenWaterExpansion() {
        if (!openWaterExpanding) {
            return;
        }
        double progress = clamp((travelled - openWaterExpansionStartTravelled)
                / openWaterExpansionDistance);
        double eased = smoothStep(progress);
        halfLength = openWaterStartHalfLength
                + ((openWaterTargetHalfLength - openWaterStartHalfLength) * eased);
        halfWidth = openWaterStartHalfWidth
                + ((openWaterTargetHalfWidth - openWaterStartHalfWidth) * eased);
        if (progress >= 1.0D) {
            openWaterExpanding = false;
        }
    }
    boolean lockChannelCourse(double directionX, double directionZ) {
        if (channelCourseLocked || fizzling()) {
            return false;
        }
        double magnitude = Math.hypot(directionX, directionZ);
        if (magnitude <= 0.001D) {
            return false;
        }
        channelCourseX = directionX / magnitude;
        channelCourseZ = directionZ / magnitude;
        channelCourseLocked = true;
        shoreTargetLocked = false;
        shoreGuided = false;
        headingX = channelCourseX;
        headingZ = channelCourseZ;
        return true;
    }

    Direction channelCourse() {
        return channelCourseLocked
                ? new Direction(channelCourseX, channelCourseZ)
                : new Direction(headingX, headingZ);
    }
    boolean beginPassageProbe(long tick) {
        long slot = Math.floorDiv(tick, 20L);
        if (slot == lastPassageProbeSlot) {
            return false;
        }
        lastPassageProbeSlot = slot;
        return !narrowPassageLocked;
    }
    boolean lockNarrowPassage(int waterSpan) {
        return lockNarrowPassage(waterSpan, 1);
    }

    boolean lockNarrowPassage(int waterSpan, int nearbyFronts) {
        if (narrowPassageLocked || fizzling() || waterSpan < 4) {
            return false;
        }
        passageCrowding = Math.max(1, nearbyFronts);
        double crowdScale = switch (Math.min(4, passageCrowding)) {
            case 4 -> 0.40D;
            case 3 -> 0.50D;
            case 2 -> 0.65D;
            default -> 1.0D;
        };
        double minimumWidth = passageCrowding >= 3 ? 3.0D : 4.0D;
        double fittingHalfWidth = Math.max(minimumWidth,
                waterSpan * 0.72D * crowdScale);
        if (halfWidth <= fittingHalfWidth * 1.20D) {
            return false;
        }
        // ## Narrow water chooses one stable compact state. Crowded fronts become
        // much smaller, then keep that footprint and course instead of twirling.
        narrowPassageLocked = true;
        openWaterExpanding = false;
        passageHalfWidth = Math.min(halfWidth, fittingHalfWidth);
        passageHalfLength = Math.min(halfLength,
                Math.max(3.5D, waterSpan * 0.90D * Math.sqrt(crowdScale)));
        return true;
    }

    private void easePassageShape(long elapsedTicks) {
        if (!narrowPassageLocked || elapsedTicks <= 0L) {
            return;
        }
        double amount = Math.min(1.0D, elapsedTicks / 20.0D);
        halfWidth += (passageHalfWidth - halfWidth) * amount;
        halfLength += (passageHalfLength - halfLength) * amount;
    }

    Direction lockedShoreDirection() {
        if (!shoreTargetLocked) {
            return new Direction(headingX, headingZ);
        }
        double dx = shoreTargetX - x;
        double dz = shoreTargetZ - z;
        double magnitude = Math.hypot(dx, dz);
        return magnitude > 0.001D
                ? new Direction(dx / magnitude, dz / magnitude)
                : new Direction(headingX, headingZ);
    }

    double lockedShoreDistance() {
        return shoreTargetLocked ? Math.hypot(shoreTargetX - x, shoreTargetZ - z)
                : Double.POSITIVE_INFINITY;
    }

    Direction courseDirection(double baseX, double baseZ, int shoreDistance, boolean guided) {
        double magnitude = Math.hypot(baseX, baseZ);
        double normalizedX = magnitude > 0.001D ? baseX / magnitude : headingX;
        double normalizedZ = magnitude > 0.001D ? baseZ / magnitude : headingZ;
        double retainedBias = guided
                ? smoothStep((shoreDistance - 8.0D) / (SHORE_STEERING_DISTANCE - 8.0D))
                : 1.0D;
        double effectiveBias = guided
                ? Math.max(-MAX_GUIDED_BIAS_RADIANS,
                        Math.min(MAX_GUIDED_BIAS_RADIANS, courseBiasRadians))
                : courseBiasRadians;
        double angle = Math.atan2(normalizedZ, normalizedX)
                + (effectiveBias * retainedBias);
        return new Direction(Math.cos(angle), Math.sin(angle));
    }

    Motion prepareMotion(long tick, double speed, double targetX, double targetZ,
            boolean guided) {
        long elapsedTicks = Math.max(0L, Math.min(40L, tick - lastTick));
        lastTick = tick;
        shoreGuided = guided;
        easePassageShape(elapsedTicks);
        if (fizzling() || elapsedTicks == 0L) {
            return new Motion(x, z, 0.0D, SteeringSignal.NONE,
                    lockedShoreDistance(), 1.0D);
        }
        turnToward(targetX, targetZ, MAX_TURN_RADIANS_PER_SECOND * elapsedTicks / 20.0D);
        double distance = Math.max(0.0D, speed) * elapsedTicks / 20.0D;
        if (!guided || !shoreTargetLocked) {
            return new Motion(x + (headingX * distance), z + (headingZ * distance),
                    distance, SteeringSignal.NONE, Double.POSITIVE_INFINITY, 1.0D);
        }

        double shoreDistance = lockedShoreDistance();
        Direction shoreDirection = lockedShoreDirection();
        double alignment = (headingX * shoreDirection.x()) + (headingZ * shoreDirection.z());
        if (shoreDistance <= SHORE_CONTACT_DISTANCE) {
            return new Motion(x, z, 0.0D, SteeringSignal.SHORE_REACHED,
                    shoreDistance, alignment);
        }

        double nextX = x + (headingX * distance);
        double nextZ = z + (headingZ * distance);
        double nextDistance = Math.hypot(shoreTargetX - nextX, shoreTargetZ - nextZ);
        if (nextDistance > shoreDistance + 0.001D) {
            // ## A shore target is a destination, not an orbit center. If a curved
            // approach would move away, straighten this step instead of circling back.
            headingX = shoreDirection.x();
            headingZ = shoreDirection.z();
            double safeDistance = Math.min(distance,
                    Math.max(0.0D, shoreDistance - SHORE_CONTACT_DISTANCE));
            return new Motion(x + (headingX * safeDistance), z + (headingZ * safeDistance),
                    safeDistance, SteeringSignal.REVERSE_CORRECTED,
                    shoreDistance, alignment);
        }
        return new Motion(nextX, nextZ, distance, SteeringSignal.NONE,
                shoreDistance, alignment);
    }

    void commitMotion(Motion motion) {
        x = motion.nextX();
        z = motion.nextZ();
        travelled += motion.distance();
        updateOpenWaterExpansion();
    }

    void beginShoreFizzle(long tick) {
        beginFizzle(tick, SHORE_FIZZLE_TICKS);
    }

    void beginDistanceFizzle(long tick) {
        beginFizzle(tick, DISTANCE_FIZZLE_TICKS);
    }

    boolean expired(long tick) {
        return fizzling() && tick - fizzleStartedTick >= fizzleDurationTicks;
    }

    double strengthAt(double worldX, double worldZ, long tick) {
        double dx = worldX - x;
        double dz = worldZ - z;
        double forward = (dx * headingX) + (dz * headingZ);
        double lateral = (dx * -headingZ) + (dz * headingX);
        double energy = currentEnergy(tick);
        OvalWavePulse crest = new OvalWavePulse(0.0D, 0.0D,
                halfLength, halfWidth, energy, 0.55D, !fizzling());
        double strength = crest.strengthAt(forward, lateral);
        if (kind != Kind.GIANT || energy <= 0.0D) {
            return strength;
        }

        // ## Giant fronts are a two-ridge wave train. The lower trailing shelf adds
        // depth and a second collision surface without closing the main boomerang wake.
        OvalWavePulse trailingShelf = new OvalWavePulse(
                -halfLength * 0.88D, 0.0D,
                halfLength * 0.72D, halfWidth * 0.90D,
                energy * 0.52D, 0.55D, !fizzling());
        double trailing = trailingShelf.strengthAt(forward, lateral);
        return 1.0D - ((1.0D - strength) * (1.0D - trailing));
    }

    private void turnToward(double targetX, double targetZ, double maximumTurn) {
        double magnitude = Math.hypot(targetX, targetZ);
        if (magnitude <= 0.001D) {
            return;
        }
        double targetAngle = Math.atan2(targetZ / magnitude, targetX / magnitude);
        double currentAngle = Math.atan2(headingZ, headingX);
        double delta = normalizeAngle(targetAngle - currentAngle);
        double nextAngle = currentAngle + Math.max(-maximumTurn, Math.min(maximumTurn, delta));
        headingX = Math.cos(nextAngle);
        headingZ = Math.sin(nextAngle);
    }

    private void beginFizzle(long tick, long durationTicks) {
        if (!fizzling()) {
            fizzleStartedTick = tick;
            fizzleDurationTicks = durationTicks;
        }
    }

    private double currentEnergy(long tick) {
        double energy = baseEnergy;
        if (fadeInTicks > 0L) {
            double arrival = Math.max(0.0D, Math.min(1.0D,
                    (double) (tick - createdTick) / fadeInTicks));
            energy *= smoothStep(arrival);
        }
        if (!fizzling()) {
            return energy;
        }
        double progress = Math.max(0.0D, Math.min(1.0D,
                (double) (tick - fizzleStartedTick) / Math.max(1L, fizzleDurationTicks)));
        // ## New fronts rise while their predecessors settle, preventing a merge
        // from replacing thousands of packet-water columns in one frame.
        return energy * (1.0D - smoothStep(progress));
    }

    private double normalizeAngle(double angle) {
        double normalized = angle;
        while (normalized > Math.PI) {
            normalized -= Math.PI * 2.0D;
        }
        while (normalized < -Math.PI) {
            normalized += Math.PI * 2.0D;
        }
        return normalized;
    }

    private double smoothStep(double value) {
        double clamped = clamp(value);
        return clamped * clamped * (3.0D - (2.0D * clamped));
    }

    private double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    long id() { return id; }
    Kind kind() { return kind; }
    double x() { return x; }
    double z() { return z; }
    double headingX() { return headingX; }
    double headingZ() { return headingZ; }
    double halfLength() { return halfLength; }
    double halfWidth() { return halfWidth; }
    boolean narrowPassageLocked() { return narrowPassageLocked; }
    boolean channelCourseLocked() { return channelCourseLocked; }
    boolean openWaterFan() { return openWaterFan; }
    boolean openWaterExpanding() { return openWaterExpanding; }
    double openWaterTargetHalfWidth() { return openWaterTargetHalfWidth; }
    double passageHalfWidth() { return passageHalfWidth; }
    int passageCrowding() { return passageCrowding; }
    double travelled() { return travelled; }
    boolean shoreGuided() { return shoreGuided; }
    boolean fizzling() { return fizzleStartedTick >= 0L; }
    boolean hasShoreTarget() { return shoreTargetLocked; }
    double shoreTargetX() { return shoreTargetX; }
    double shoreTargetZ() { return shoreTargetZ; }
    boolean mergeEligible(long tick) {
        return !mergeResult && !fizzling() && tick - createdTick >= 40L;
    }
    int boundsX() {
        double longitudinal = halfLength * (kind == Kind.GIANT ? 1.65D : 1.05D);
        return (int) Math.ceil((Math.abs(headingX) * longitudinal)
                + (Math.abs(headingZ) * halfWidth) + 2.0D);
    }

    int boundsZ() {
        double longitudinal = halfLength * (kind == Kind.GIANT ? 1.65D : 1.05D);
        return (int) Math.ceil((Math.abs(headingZ) * longitudinal)
                + (Math.abs(headingX) * halfWidth) + 2.0D);
    }

    enum Kind {
        STANDARD,
        CROSSING,
        GIANT,
        MERGED
    }

    record Direction(double x, double z) {
    }

    enum SteeringSignal {
        NONE,
        REVERSE_CORRECTED,
        SHORE_REACHED
    }

    record Motion(double nextX, double nextZ, double distance,
            SteeringSignal steeringSignal, double shoreDistance,
            double shoreAlignment) {
    }
}
