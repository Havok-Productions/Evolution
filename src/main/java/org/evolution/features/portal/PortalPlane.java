package org.evolution.features.portal;

import org.bukkit.Axis;

enum PortalPlane {
    X,
    Z;

    Axis axis() {
        return this == X ? Axis.X : Axis.Z;
    }
}
