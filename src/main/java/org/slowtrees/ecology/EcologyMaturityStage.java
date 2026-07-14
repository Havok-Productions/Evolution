package org.slowtrees.ecology;

enum EcologyMaturityStage {
    SPARSE,
    ESTABLISHED,
    DENSE,
    MATURE,
    OLD_GROWTH;

    boolean atLeast(EcologyMaturityStage other) {
        return ordinal() >= other.ordinal();
    }
}
