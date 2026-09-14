/**
 * ## TREE CONSTRUCTOR HIERARCHY
 *
 * <p>The constructor hierarchy does not generate species geometry. Species
 * planners contribute geometry to {@code TreeBlueprintCoordinator}, which
 * translates and approves one sealed target. Runtime construction then follows
 * a one-way ownership path:</p>
 *
 * <ol>
 *   <li>preflight captures one coherent live/plan snapshot</li>
 *   <li>{@code TreeConstructionRuleBook} selects one ordered subrule</li>
 *   <li>that subrule names one phase, attachment, and smoke tag</li>
 *   <li>one mini-constructor executes one exact operation</li>
 *   <li>the cycle stops and the next tick observes state again</li>
 * </ol>
 *
 * <p>Subrules cover rooted ownership, immutable source snapshots, repair,
 * support targets, exposed support cover, unplanned-terminal retirement,
 * stale-envelope retirement, evolved branch envelopes, crown shell,
 * parent-linked branch frame, canopy fill, retired-crown pruning,
 * finalization, and details. Every subrule declares its parent phase and sole
 * executor attachment, so overlapping ownership is rejected by the decision
 * model.</p>
 *
 * <p>{@code TreeConstructionFinalAudit} reads the same immutable rulebook as
 * action routing. It does not carry a duplicate conditional hierarchy that
 * can drift. Every decision carries its first audit failure, while
 * finalization and completion are allowed only with an audit pass. Existing
 * architecture pathfinding and tree-evolution diagnostics therefore identify
 * both the phase and exact smaller rule involved in a future problem.</p>
 *
 * <p>## {@code TreeConstructionSmokeTag} is the stable bridge between this
 * production hierarchy and simulated smoke stages. Every subrule owns exactly
 * one tag, and the same marker is written by live diagnostics and test replay
 * artifacts so the two paths can be compared directly.</p>
 *
 * <p>## {@code TreePlacementAugment} belongs to the parent planner package
 * and labels which geometry rule requested each coordinate. It is deliberately
 * separate from {@code TreeConstructionSmokeTag}: the former explains what
 * target was designed, while the latter explains which ordered constructor
 * action realized it. Live and simulated traces write both labels together.</p>
 *
 * <p>{@code TreeConstructorCore} in the parent package converts live
 * TreeEvolution state into this package's immutable decision model. The
 * {@code executor} package gives every subrule exactly one file-level owner.
 * {@code TreeConstructionRuntime} is the only hierarchy adapter. Preflight,
 * canopy integrity, and transition operations have focused owners in the
 * parent package. Runtime delegates to one of those owners and then ends the
 * cycle; it cannot route from one mini-constructor into another.</p>
 */
package org.evolution.features.treeevolution.constructor;
