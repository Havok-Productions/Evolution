package org.evolution.features.nether;

import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PortalSourceLifecycleSmokeTest {
    private PortalSourceLifecycleSmokeTest() {
    }

    public static void main(String[] args) {
        UUID worldId = UUID.nameUUIDFromBytes(
                "portal-source-lifecycle-smoke".getBytes());
        List<PortalSource.Cell> cells = List.of(
                new PortalSource.Cell(10, 64, 20),
                new PortalSource.Cell(10, 65, 20),
                new PortalSource.Cell(11, 64, 20),
                new PortalSource.Cell(11, 65, 20),
                new PortalSource.Cell(12, 65, 20));
        PortalSource source = PortalSource.fromCells(worldId, cells);

        require(source.isExactSnapshot(), "New source must be exact.");
        require(source.portalCellCount() == cells.size(),
                "Exact source omitted portal cells.");
        require(source.containsPortalCell(12, 65, 20),
                "Irregular portal tip was not retained.");
        require(!source.containsPortalCell(12, 64, 20),
                "Bounding-box air was treated as portal.");
        require(source.touchesPortalFrame(9, 64, 20),
                "Face-adjacent frame was not recognized.");
        require(!source.touchesPortalFrame(9, 63, 20),
                "Diagonal non-frame cell was recognized.");
        require(source.isIntact(cells::contains),
                "Complete fingerprint should pass.");
        require(!source.isIntact(cell -> !cell.equals(cells.get(2))),
                "One missing portal cell must break the source contract.");

        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("source");
        source.writeTo(section);
        PortalSource restored = PortalSource.from(section);
        require(restored.matchesFingerprint(source),
                "Persisted fingerprint changed on reload.");
        require(restored.isExactSnapshot(),
                "Reloaded source lost exact ownership.");

        ConfigurationSection legacy = yaml.createSection("legacy");
        legacy.set("world-id", worldId.toString());
        legacy.set("min-x", 1);
        legacy.set("min-y", 2);
        legacy.set("min-z", 3);
        legacy.set("max-x", 2);
        legacy.set("max-y", 3);
        legacy.set("max-z", 3);
        PortalSource migrated = PortalSource.from(legacy);
        require(!migrated.isExactSnapshot(),
                "Legacy bounds must stay marked conservative.");
        require(migrated.portalCellCount() == 4,
                "Legacy migration produced the wrong footprint.");

        System.out.println(
                "Portal source lifecycle smoke test passed: exact irregular "
                        + "fingerprints persist, one missing cell invalidates, "
                        + "and legacy bounds migrate conservatively.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}