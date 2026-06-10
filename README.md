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

## API

Other plugins can read SlowTrees state through `org.slowtrees.api.SlowTreesProvider` while SlowTrees is enabled.

```java
SlowTreesApi api = SlowTreesProvider.get();
int queued = api.regrowth().queuedCount();
boolean tracked = api.nether().registerPortalSourceNear(location, 32);
WindSnapshot wind = api.wind().currentPattern();
```

The first API surface is intentionally small and stable:

- Core: version, status lines, and reload.
- Regrowth: queued, active, and decaying counts.
- Nether corruption: source counts, changed block count, material checks, and existing-portal registration.
- Wind: enabled state, particle/litter counters, and current wind pattern.

## Permissions

- `slowtrees.admin` allows admin commands.

## Build

```bash
mvn clean package
```

The plugin jar will be under `target/`.

## Commit Notes

Project commits should include a `## Notes` section so the work history stays readable.
