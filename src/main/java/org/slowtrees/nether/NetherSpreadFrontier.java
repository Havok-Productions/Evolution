package org.slowtrees.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

final class NetherSpreadFrontier {
    private final ConcurrentLinkedDeque<Point> points = new ConcurrentLinkedDeque<>();
    private final AtomicInteger size = new AtomicInteger();

    NetherSpreadFrontier(PortalSource source) {
        add(source.centerX(), source.centerY(), source.centerZ(), 1);
    }

    void add(int x, int y, int z, int maxSize) {
        points.addLast(new Point(x, y, z));
        int currentSize = size.incrementAndGet();
        while (currentSize > maxSize) {
            if (points.pollFirst() == null) {
                size.set(0);
                return;
            }
            currentSize = size.decrementAndGet();
        }
    }

    Point randomPoint(Random random) {
        List<Point> snapshot = new ArrayList<>(points);
        if (snapshot.isEmpty()) {
            return null;
        }
        return snapshot.get(random.nextInt(snapshot.size()));
    }

    int size() {
        return size.get();
    }

    record Point(int x, int y, int z) {
    }
}
