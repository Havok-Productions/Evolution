# SlowTrees

SlowTrees is a Folia-first Minecraft plugin that slowly regrows vanilla trees after players cut logs.

## Target

- Server: Folia / Paper API `26.1.2+`
- Java: `25`
- Build: Maven

The plugin depends on Folia's region scheduler and declares `folia-supported: true`.

## Behavior

- Tracks vanilla log breaks.
- Finds the bottom of the connected same-type trunk column.
- Queues one regrowth job per tree base.
- Waits `initial-delay-ticks`, defaulting to 30 minutes at 20 TPS.
- Uses Bukkit/Paper's vanilla tree generator for the configured `TreeType`.
- Places generated blocks gradually in batches.
- Skips work when the chunk area is not loaded, outside configured player distance, or not owned by the current Folia region.
- Never intentionally loads chunks.

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
