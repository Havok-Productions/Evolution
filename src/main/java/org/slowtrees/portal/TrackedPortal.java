package org.slowtrees.portal;

import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;

record TrackedPortal(
        String id,
        UUID worldId,
        String worldName,
        PortalPlane plane,
        Set<PortalCell> interior,
        Set<PortalCell> frame
) {
    TrackedPortal {
        interior = Set.copyOf(interior);
        frame = Set.copyOf(frame);
    }

    World world() {
        World world = Bukkit.getWorld(worldId);
        return world != null ? world : Bukkit.getWorld(worldName);
    }
}
