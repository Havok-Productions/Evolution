# SlowTrees

SlowTrees is a Folia-first Minecraft plugin that slowly regrows vanilla trees and huge mushrooms after players cut them.

## Target

- Server: Folia / Paper API `26.1.2+`
- Java: `25`
- Build: Maven

The plugin depends on Folia's region scheduler and declares `folia-supported: true`.

## Behavior

- Tracks vanilla log breaks.
- Tracks huge mushroom cap and stem breaks.
- Finds the bottom of the connected same-type trunk column.
- Queues one regrowth job per tree base.
- Waits `initial-delay-ticks`, defaulting to 1 second at 20 TPS.
- Uses Bukkit/Paper's vanilla generator for the configured `TreeType`.
- Places generated blocks gradually, defaulting to 1 block every 30 seconds.
- Treats the lowest connected log or mushroom stem as the living anchor; breaking that stump cancels regrowth.
- Skips work when the chunk area is not loaded, outside configured player distance, or not owned by the current Folia region.
- Never intentionally loads chunks.
- Uses built-in red and brown mushroom defaults if an older config does not have `mushroom-types`.

## Commands

- `/slowtrees reload` reloads `config.yml`.
- `/slowtrees pending` shows the number of queued regrowth jobs.

## Permissions

- `slowtrees.admin` allows admin commands.

## Build

```bash
mvn clean package
```

The plugin jar will be under `target/`.

## Commit Notes

Project commits should include a `## Notes` section so the work history stays readable.
