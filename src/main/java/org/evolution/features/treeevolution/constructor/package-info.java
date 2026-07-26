/**
 * ## TREE CONSTRUCTOR HIERARCHY
 *
 * <p>The constructor hierarchy does not generate species geometry. It decides
 * which existing planner owns the next live action through two levels:</p>
 *
 * <ol>
 *   <li>one exclusive top-level phase</li>
 *   <li>one ordered, named subrule inside that phase</li>
 * </ol>
 *
 * <p>Subrules cover rooted ownership, immutable source snapshots, repair,
 * support targets, exposed support cover, evolved branch envelopes, crown
 * shell, parent-linked branch frame, canopy fill, retired-crown pruning,
 * finalization, and details. Every subrule declares its parent phase and sole
 * executor attachment, so overlapping ownership is rejected by the decision
 * model.</p>
 *
 * <p>{@code TreeConstructionFinalAudit} independently rechecks the complete
 * structural contract. Every decision carries its first audit failure, while
 * finalization and completion are allowed only with an audit pass. Existing
 * architecture pathfinding and tree-evolution diagnostics therefore identify
 * both the phase and exact smaller rule involved in a future problem.</p>
 *
 * <p>{@code TreeConstructorCore} in the parent package converts live
 * TreeEvolution state into this package's immutable decision model. The
 * {@code executor} package gives every phase exactly one file-level owner.
 * {@code TreeConstructionRuntime} is the only hierarchy adapter. It delegates
 * audit, maturity, transition, placement, and reproduction actions to their
 * named services, then executes only the selected attachment.</p>
 */
package org.evolution.features.treeevolution.constructor;