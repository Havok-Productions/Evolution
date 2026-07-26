/**
 * ## CONSTRUCTOR EXECUTOR OWNERSHIP
 *
 * <p>Each construction phase is attached to exactly one executor:</p>
 *
 * <ul>
 *   <li>{@code OwnershipGateExecutor}: ownership and source snapshot gates</li>
 *   <li>{@code DamageRepairExecutor}: interrupted or damaged trees</li>
 *   <li>{@code TransitionReconciler}: atomic blockers and retired leaves</li>
 *   <li>{@code TrunkConstructionExecutor}: trunk/support structure</li>
 *   <li>{@code CanopyConstructionExecutor}: canopy shell and canopy fill</li>
 *   <li>{@code BranchConstructionExecutor}: parent-linked branch frame</li>
 *   <li>{@code DetailConstructionExecutor}: seedlings and natural details</li>
 *   <li>{@code StageFinalizer}: transition closure and maturity handoff</li>
 * </ul>
 *
 * <p>The registry rejects duplicate or missing phase owners. Executors invoke
 * shared world mechanics only through {@code TreeConstructionOperations}.</p>
 */
package org.evolution.features.treeevolution.constructor.executor;
