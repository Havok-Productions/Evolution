package org.evolution.features.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

final class NetherSpreadFrontier {
    private final ConcurrentLinkedDeque<Point> points = new ConcurrentLinkedDeque<>();
    private final Set<Point> uniquePoints = ConcurrentHashMap.newKeySet();
    private final AtomicInteger size = new AtomicInteger();

    NetherSpreadFrontier(PortalSource source) {
        add(source.centerX(), source.centerY(), source.centerZ(), 1);
    }

    void add(int x, int y, int z, int maxSize) {
        Point point = new Point(x, y, z);
        if (!uniquePoints.add(point)) {
            return;
        }

        points.addLast(point);
        int currentSize = size.incrementAndGet();
        while (currentSize > maxSize) {
            Point removed = points.pollFirst();
            if (removed == null) {
                size.set(0);
                uniquePoints.clear();
                return;
            }
            uniquePoints.remove(removed);
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

    int maxHorizontalDistanceFrom(int x, int z) {
        int maxDistanceSquared = 0;
        for (Point point : points) {
            int dx = point.x() - x;
            int dz = point.z() - z;
            maxDistanceSquared = Math.max(maxDistanceSquared, (dx * dx) + (dz * dz));
        }
        return (int) Math.ceil(Math.sqrt(maxDistanceSquared));
    }

    record Point(int x, int y, int z) {
    }
}
