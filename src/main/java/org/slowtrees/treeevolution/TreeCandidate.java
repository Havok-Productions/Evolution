package org.slowtrees.treeevolution;

import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

record TreeCandidate(
        World world,
        int baseX,
        int baseY,
        int baseZ,
        int topY,
        int height,
        TreeSpecies species,
        int connectedLogs,
        int connectedLeaves,
        Set<String> naturalKeys
) {
    Block baseBlock() {
        return world.getBlockAt(baseX, baseY, baseZ);
    }

    Location baseLocation() {
        return new Location(world, baseX, baseY, baseZ);
    }

    String baseKey() {
        return world.getUID() + ":" + baseX + ":" + baseY + ":" + baseZ;
    }
}
