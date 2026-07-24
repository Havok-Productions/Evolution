package org.slowtrees.portal;

import java.util.Set;

record PortalShapePlan(
        PortalPlane plane,
        Set<PortalCell> interior,
        Set<PortalCell> frame
) {
    PortalShapePlan {
        interior = Set.copyOf(interior);
        frame = Set.copyOf(frame);
    }
}
