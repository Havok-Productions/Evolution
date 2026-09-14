package org.evolution.features.waves;

final class WaveWaterBodyPolicy {
    private static final int CHANNEL_MAXIMUM_SPAN = 40;
    private static final int ENCLOSED_CHANNEL_MAXIMUM_DEPTH = 21;
    private static final int OPEN_COAST_MINIMUM_DEPTH = 48;
    private static final int OPEN_COAST_MINIMUM_SPAN = 64;

    private WaveWaterBodyPolicy() {
    }

    static Classification classify(LakeWaveFlowField.Cell cell, int passageSpan) {
        if (cell.shoreGuided() && cell.enclosed()
                && cell.sourceDistance() > ENCLOSED_CHANNEL_MAXIMUM_DEPTH) {
            return new Classification(Body.ENCLOSED_LAKE, passageSpan);
        }
        if (cell.shoreGuided() && passageSpan > 0
                && passageSpan <= CHANNEL_MAXIMUM_SPAN) {
            return new Classification(Body.CHANNEL, passageSpan);
        }
        if (cell.shoreGuided() && cell.enclosed()) {
            return new Classification(Body.ENCLOSED_LAKE, passageSpan);
        }
        if (cell.shoreGuided()
                && cell.sourceDistance() >= OPEN_COAST_MINIMUM_DEPTH
                && (passageSpan < 0 || passageSpan >= OPEN_COAST_MINIMUM_SPAN)) {
            return new Classification(Body.OPEN_COAST, passageSpan);
        }
        if (!cell.shoreGuided()) {
            return new Classification(Body.OPEN_WATER, passageSpan);
        }
        return new Classification(Body.CONFINED_WATER, passageSpan);
    }

    enum Body {
        CHANNEL,
        ENCLOSED_LAKE,
        CONFINED_WATER,
        OPEN_WATER,
        OPEN_COAST
    }

    record Classification(Body body, int passageSpan) {
        TravelingWaveFront.Kind permittedKind(TravelingWaveFront.Kind requested) {
            // ## Giant waves are coastal events. A lake may be wide without having
            // the fetch and shoreline scale needed to support an ocean-sized front.
            if (requested == TravelingWaveFront.Kind.GIANT
                    && body != Body.OPEN_COAST) {
                return TravelingWaveFront.Kind.STANDARD;
            }
            return requested;
        }

        boolean channelLocked() {
            return body == Body.CHANNEL;
        }

        boolean broadWater() {
            return body == Body.OPEN_COAST || body == Body.OPEN_WATER;
        }

        boolean openCoast() {
            return body == Body.OPEN_COAST;
        }

        double fitHalfWidth(double proposed) {
            return switch (body) {
                case CHANNEL -> Math.min(proposed,
                        Math.max(3.5D, Math.min(9.0D, passageSpan * 0.22D)));
                case ENCLOSED_LAKE -> Math.min(proposed, 15.0D);
                case CONFINED_WATER -> Math.min(proposed, 18.0D);
                case OPEN_WATER, OPEN_COAST -> proposed;
            };
        }

        double fitHalfLength(double proposed) {
            return switch (body) {
                case CHANNEL -> Math.min(proposed,
                        Math.max(4.0D, Math.min(10.0D, passageSpan * 0.28D)));
                case ENCLOSED_LAKE -> Math.min(proposed, 13.0D);
                case CONFINED_WATER -> Math.min(proposed, 15.0D);
                case OPEN_WATER, OPEN_COAST -> proposed;
            };
        }
    }
}
