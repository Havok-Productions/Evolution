# SlowTrees

SlowTrees is a Folia-first Minecraft plugin that slowly regrows vanilla trees and huge mushrooms after players cut them, and lets active Nether portals slowly leak Nether-like terrain into the Overworld.

## Target

- Server: Folia / Paper API `26.1.2+`
- Java: `25`
- Build: Maven

The plugin depends on Folia's region scheduler and declares `folia-supported: true`.

## Plant Regrowth

- Tracks vanilla log breaks.
- Tracks huge mushroom cap and stem breaks.
- Finds the bottom of the connected same-type trunk column.
- Queues one regrowth job per tree base.
- Waits `initial-delay-ticks`, defaulting to 1 second at 20 TPS.
- Uses Bukkit/Paper's vanilla generator for the configured `TreeType`.
- Places generated blocks gradually, defaulting to 1 block every 30 seconds.
- Resets the growth cooldown when players mine actively regrown blocks, then heals those blocks before continuing.
- Uses world health mode by default with a `growth-speed-multiplier` of `0.15x`.
- Treats the lowest connected log or mushroom stem as the living anchor; breaking that stump cancels regrowth.
- Skips work when the chunk area is not loaded, outside configured player distance, or not owned by the current Folia region.
- Never intentionally loads chunks.
- Uses built-in red and brown mushroom defaults if an older config does not have `mushroom-types`.

## Nether Corruption

- Tracks newly created and player-used Nether portal clusters in Overworld worlds.
- Also scans around nearby players so existing portals can become sources.
- Slowly changes nearby natural terrain while the portal still exists.
- Stops spreading when the portal is gone, while existing corruption remains.
- Turns water into lava as corruption reaches it.
- Leaves blocks alone when there is no clear Nether equivalent.
- Keeps Nether mimic rules in code instead of exposing replacement maps.
- Skips work when target chunks are unloaded, outside configured player distance, or outside the current Folia region.

## Meadow Growth

- Lets dirt-like natural ground become grass when it is near existing grass or meadow plants.
- Lets grass blocks grow short grass, ferns, tall grass, large ferns, and biome-aware flowers.
- Forms small flower clusters by biasing new flowers toward nearby flowers and stable biome patches.
- Uses biome-aware flower choices for plains, forests, swamps, cherry groves, and meadows.
- Can replace leaf litter so ground plants can reclaim the surface.
- Avoids caves, liquids, player blocks, unloaded chunks, distant players, and non-owned Folia regions.
- Uses world health mode by default, so meadow spread slows with the global natural growth multiplier.

## Tree Evolution

- Observes natural vanilla trees near loaded, player-visible areas.
- Stores seeded per-tree DNA in `tree-evolution.yml`.
- Evolves trees one block at a time into taller trunks, branches, fuller canopy, roots, vines, and subtle ground detail.
- Uses species profiles for oak, birch, spruce, jungle, acacia, dark oak, mangrove, and cherry.
- Respects the stump rule: when the stump is removed, that tree DNA stops upward evolution.
- Damage stalls a tree temporarily and adds dynamic slowdown that recovers as healthy growth succeeds.
- Has a fast `tree-evolution.testing` mode for watching growth/profile behavior immediately during testing.
- Roots are disabled by default with `tree-evolution.roots.enabled`; scanned samples still influence height, branch start, branch length, and uneven canopy shape.
- Tree DNA now includes personality, rarity, age, generation, parent lineage, trunk thickness, lean, and sample inspiration, all written into the existing tree evolution debug files.
- Mature and ancient trees can slowly seed nearby saplings when surface/light/spacing checks pass, allowing forests to thicken naturally.
- Avoids unloaded chunks, non-owned Folia regions, player blocks, liquids, caves, and unnatural placement targets.
- Writes `tree-evolution-trace.debug.yml` and `tree-evolution-map.debug.yml`.
- Creates `structure-scan/` for optional `.nbt`, `.schem`, `.schematic`, `.zip`, or `.jar` analysis and writes `structure-scan-debug.yml`; these scans measure structure/worldgen signals, generate profile suggestions from tree configured-feature JSON, include species-source evidence, and do not copy exact layouts or source JSON.
- Writes `tree-profile-samples.yml` from scanned worldgen suggestions; new tree DNA can pick one of those samples and stores the chosen sample id/source for debugging.
- Auto-scans `structure-scan/` on startup when `tree-evolution.debug.auto-scan-on-startup` is enabled.

## Wind

- Represents wind through drifting leaves near tree canopies.
- Uses visible default tuning so leaf motion should be noticeable near trees.
- Changes wind direction and strength slowly over time.
- Places subtle leaf litter downwind in uneven clusters.
- Requires surface-only natural ground, air above the target, and sky or strong surface light.
- Avoids player blocks, liquids, caves, unloaded chunks, distant players, and non-owned Folia regions.
- Rain keeps leaves closer to trees, while storms push them farther and show stronger gusts.

## Commands

- `/slowtrees reload` reloads `config.yml`.
- `/slowtrees pending` shows the number of queued regrowth jobs.
- `/slowtrees tree debug` dumps nearby tree DNA and target-plan details.
- `/slowtrees tree step` forces one nearby tree evolution pass.
- `/slowtrees tree preview` writes a nearby tree target-plan preview to debug files.
- `/slowtrees tree scan` scans optional `.nbt`, `.schem`, `.schematic`, `.zip`, or `.jar` files in `structure-scan/`.

## API

Other plugins can read SlowTrees state through `org.slowtrees.api.SlowTreesProvider` while SlowTrees is enabled.

```java
SlowTreesApi api = SlowTreesProvider.get();
int queued = api.regrowth().queuedCount();
long grass = api.meadow().grassBlocksSpread();
boolean tracked = api.nether().registerPortalSourceNear(location, 32);
WindSnapshot wind = api.wind().currentPattern();
long evolved = api.treeEvolution().changedBlockCount();
```

The first API surface is intentionally small and stable:

- Core: version, status lines, and reload.
- Regrowth: queued, active, and decaying counts.
- Meadow growth: grass, plant, and flower growth counters.
- Nether corruption: source counts, changed block count, material checks, and existing-portal registration.
- Wind: enabled state, particle/litter counters, and current wind pattern.
- Tree evolution: enabled state, known tree count, and changed block count.

## Permissions

- `slowtrees.admin` allows admin commands.

## Build

```bash
mvn clean package
```

The plugin jar will be under `target/`.

## Commit Notes

Project commits should include a `## Notes` section so the work history stays readable.
