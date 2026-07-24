package com.example.foliafunfacts;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class FactPicker {
    private int lastIndex = -1;
    private int sequentialIndex;

    synchronized String pick(List<String> facts, FactOrder order) {
        if (facts.isEmpty()) {
            return null;
        }

        int index;
        if (order == FactOrder.SEQUENTIAL) {
            index = Math.floorMod(sequentialIndex++, facts.size());
        } else if (facts.size() == 1) {
            index = 0;
        } else {
            int candidate = ThreadLocalRandom.current().nextInt(facts.size() - 1);
            index = candidate >= lastIndex ? candidate + 1 : candidate;
        }

        lastIndex = index;
        return facts.get(index);
    }

    synchronized void reset() {
        lastIndex = -1;
        sequentialIndex = 0;
    }
}
