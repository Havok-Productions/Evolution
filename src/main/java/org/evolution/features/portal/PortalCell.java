package org.evolution.features.portal;

record PortalCell(int x, int y, int z) {
    PortalCell horizontal(int amount, PortalPlane plane) {
        return plane == PortalPlane.X
                ? new PortalCell(x + amount, y, z)
                : new PortalCell(x, y, z + amount);
    }

    PortalCell vertical(int amount) {
        return new PortalCell(x, y + amount, z);
    }

    int horizontalCoordinate(PortalPlane plane) {
        return plane == PortalPlane.X ? x : z;
    }

    String encoded() {
        return x + "," + y + "," + z;
    }

    static PortalCell decode(String encoded) {
        String[] parts = encoded.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected x,y,z portal cell.");
        }
        return new PortalCell(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
    }
}
