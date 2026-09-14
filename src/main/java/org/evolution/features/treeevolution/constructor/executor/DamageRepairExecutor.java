package org.evolution.features.treeevolution.constructor.executor;

import java.util.Set;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;
import org.evolution.features.treeevolution.constructor.TreeConstructionAttachment;

public final class DamageRepairExecutor implements TreeConstructionExecutor {
    @Override
    public Set<TreeConstructionSubrule> subrules() {
        return Set.of(
                TreeConstructionSubrule.OWNERSHIP_ROLE_RECONCILIATION,
                TreeConstructionSubrule.DISCONNECTED_TARGET_REPAIR,
                TreeConstructionSubrule.INTERRUPTED_DAMAGE_REPAIR);
    }

    @Override
    public Set<TreeConstructionAttachment> attachments() {
        return Set.of(TreeConstructionAttachment.DAMAGE_REPAIR);
    }

    @Override
    public TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return switch (decision.subrule()) {
            case OWNERSHIP_ROLE_RECONCILIATION ->
                    operations.reconcileOwnershipRole();
            case DISCONNECTED_TARGET_REPAIR ->
                    operations.repairDisconnectedTarget();
            case INTERRUPTED_DAMAGE_REPAIR ->
                    operations.repairInterruptedDamage();
            default -> throw new IllegalStateException(
                    "Damage repair executor cannot own "
                            + decision.subrule());
        };
    }
}
