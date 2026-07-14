package org.slowtrees.treeevolution;

public enum TreeMaturityStage {
    SMALL,
    MEDIUM,
    MATURE,
    ANCIENT;

    public TreeMaturityStage next() {
        return switch (this) {
            case SMALL -> MEDIUM;
            case MEDIUM -> MATURE;
            case MATURE, ANCIENT -> ANCIENT;
        };
    }
}
