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
 *   <li>{@code TreeSourcePattern}: immutable pre-evolution source measurements</li>
 *   <li>{@code TreeVariantClassifier}: source geometry to persistent species subtype</li>
 *   <li>{@code TreeVariantPolicy}: subtype architecture floors, caps, and stage expression</li>
 *   <li>{@code TreeDnaRepository}: tree-DNA/offspring receipts and async persistence</li>
 *   <li>{@code TreeDnaLifecycleService}: retention, migration, and cache invalidation</li>
 *   <li>{@code TreeCandidateDiscoveryService}: bounded Folia-safe tree traversal</li>
 *   <li>{@code TreeBlueprintCoordinator}: sole authority that assembles, translates, validates, and seals target XYZ</li>
 *   <li>{@code TreeCoordinateTranslator}: shared tree-relative/world coordinate vocabulary shared by every planner contribution</li>
 *   <li>{@code TreeConstructionRuntime}: snapshot, rule selection, and execution orchestration only</li>
 *   <li>{@code TreeConstructorPreflight}: persistent logical normalization before rule selection</li>
 *   <li>{@code TreeCanopyIntegrityOperations}: exact exposed-support, stale-leaf, terminal, and envelope actions</li>
 *   <li>{@code TreeTransitionConstructionOperations}: exact ledger, bridge, cleanup, and finalization actions</li>
 *   <li>{@code TreeConstructorStageTimeline}: per-tree live EXIT/ENTER voxel chronology</li>
 *   <li>{@code TreePlanAuditService}: plans, completion projection, and terminal audits</li>
 *   <li>{@code TreeMaturityService}: source snapshots and stage transitions</li>
 *   <li>{@code TreePlacementService}: dependencies, protection gates, and world placement</li>
 *   <li>{@code TreeCanopyRepairService}: exposed-log and branch-envelope repairs</li>
 *   <li>{@code TreeTransitionService}: atomic blockers and source-crown retirement</li>
 *   <li>{@code TreeReproductionService}: seedling search, ownership, placement, and gradual handoff</li>
 *   <li>{@code TreeProfileScanService}: optional structure analysis and profile samples</li>
 *   <li>{@code TreeEvolutionPlanner}/{@code TreeShapeEngine}: immutable target geometry</li>
 *   <li>{@code TreePlacementAugment}: coordinate-level planner provenance and expected block-role contract</li>
 *   <li>{@code TreeGrowthIntentPolicy}: ordered growth intent selection</li>
 *   <li>{@code TreeGroundDetailPolicy}: biome/terrain detail substitutions</li>
 *   <li>{@code constructor}: exclusive phase and subrule hierarchy</li>
 *   <li>{@code constructor.executor}: exactly one validated executor attachment per construction subrule</li>
 * </ul>
 *
 * <p>Debug paths retain their existing {@code [SCHED]}, {@code [STATE]},
 * {@code [GATE]}, {@code [ACTION]}, {@code [TRACE]}, and {@code [MAP]}
 * categories. Extracted services use the same feature key so the architecture
 * trail remains chronological across class boundaries.</p>
 *
 * <p>## Shape diagnosis follows one joined identity chain:
 * target coordinate, planner augment, expected role, branch parent path,
 * constructor smoke tag, then live action. A strange voxel can therefore be
 * assigned to either its geometry recipe or runtime constructor route without
 * inferring ownership from appearance.</p>
 */
package org.evolution.features.treeevolution;
