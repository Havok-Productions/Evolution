/**
 * ## TREE EVOLUTION OWNERSHIP MAP
 *
 * <p>{@code TreeEvolutionFeature} is the Folia-facing coordinator. It owns
 * events, player scheduling, work budgets, and the handoff into the constructor
 * hierarchy. New algorithms should not be added there when one of the owners
 * below matches the responsibility.</p>
 *
 * <ul>
 *   <li>{@code TreeDna}: one tree's identity and mutable evolution state</li>
 *   <li>{@code TreeDnaFactory}: deterministic creation from profiles/samples</li>
 *   <li>{@code TreeDnaShapeRules}: invariant and geometry normalization</li>
 *   <li>{@code TreeDnaCodec}: backward-compatible YAML fields</li>
 *   <li>{@code TreeDnaRepository}: tree-DNA/offspring receipts and async persistence</li>
 *   <li>{@code TreeDnaLifecycleService}: retention, migration, and cache invalidation</li>
 *   <li>{@code TreeCandidateDiscoveryService}: bounded Folia-safe tree traversal</li>
 *   <li>{@code TreeConstructionRuntime}: phase/subrule execution adapter</li>
 *   <li>{@code TreePlanAuditService}: plans, completion projection, and terminal audits</li>
 *   <li>{@code TreeMaturityService}: source snapshots and stage transitions</li>
 *   <li>{@code TreePlacementService}: dependencies, protection gates, and world placement</li>
 *   <li>{@code TreeCanopyRepairService}: exposed-log and branch-envelope repairs</li>
 *   <li>{@code TreeTransitionService}: atomic blockers and source-crown retirement</li>
 *   <li>{@code TreeReproductionService}: seedling search, ownership, placement, and gradual handoff</li>
 *   <li>{@code TreeProfileScanService}: optional structure analysis and profile samples</li>
 *   <li>{@code TreeEvolutionPlanner}/{@code TreeShapeEngine}: immutable target geometry</li>
 *   <li>{@code TreeGrowthIntentPolicy}: ordered growth intent selection</li>
 *   <li>{@code TreeGroundDetailPolicy}: biome/terrain detail substitutions</li>
 *   <li>{@code constructor}: exclusive phase and subrule hierarchy</li>
 *   <li>{@code constructor.executor}: exactly one executor per construction phase</li>
 * </ul>
 *
 * <p>Debug paths retain their existing {@code [SCHED]}, {@code [STATE]},
 * {@code [GATE]}, {@code [ACTION]}, {@code [TRACE]}, and {@code [MAP]}
 * categories. Extracted services use the same feature key so the architecture
 * trail remains chronological across class boundaries.</p>
 */
package org.evolution.features.treeevolution;
