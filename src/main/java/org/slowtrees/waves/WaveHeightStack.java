package org.slowtrees.waves;

import java.util.ArrayList;
import java.util.List;

final class WaveHeightStack {
    List<VisualLayer> layers(double requestedHeight) {
        double height = Math.max(0.0D, Math.min(2.0D, requestedHeight));
        List<VisualLayer> layers = new ArrayList<>(2);
        int wholeBlocks = (int) Math.floor(height);
        for (int offset = 0; offset < wholeBlocks; offset++) {
            layers.add(new VisualLayer(offset, 0));
        }
        double fraction = height - wholeBlocks;
        if (fraction > 0.01D) {
            int level = Math.max(0, Math.min(7, 7 - (int) Math.round(fraction * 7.0D)));
            layers.add(new VisualLayer(wholeBlocks, level));
        }
        return List.copyOf(layers);
    }

    record VisualLayer(int yOffset, int waterLevel) {
    }
}
