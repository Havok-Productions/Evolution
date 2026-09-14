package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares the live structural voxels with the constructor's immutable target.
 *
 * <p>## Shape quality and target completion answer different questions. A tree
 * may have a reasonable silhouette while still retaining an old log or missing
 * part of its crown. Keeping this comparison independent makes that drift
 * visible in deformation bundles without teaching the constructor to trust its
 * own plan.</p>
 */
final class TreeTargetConformanceAudit {
    private static final int SAMPLE_LIMIT = 24;

    Report inspect(
            Collection<TreeVoxelSnapshotRenderer.Voxel> current,
            Collection<TreeVoxelSnapshotRenderer.Voxel> target,
            Set<String> blockedTargetCoordinates
    ) {
        Map<String, TreeVoxelSnapshotRenderer.Voxel> currentByKey =
                structuralByKey(current);
        Map<String, TreeVoxelSnapshotRenderer.Voxel> targetByKey =
                structuralByKey(target);
        List<String> missing = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        List<String> extra = new ArrayList<>();
        List<String> wrongMaterial = new ArrayList<>();

        for (Map.Entry<String, TreeVoxelSnapshotRenderer.Voxel> entry
                : targetByKey.entrySet()) {
            TreeVoxelSnapshotRenderer.Voxel live = currentByKey.get(
                    entry.getKey());
            if (live == null) {
                if (blockedTargetCoordinates.contains(entry.getKey())) {
                    blocked.add(encoded(entry.getValue()));
                } else {
                    missing.add(encoded(entry.getValue()));
                }
                continue;
            }
            TreeVoxelSnapshotRenderer.Voxel expected = entry.getValue();
            if (!expected.material().equals(live.material())
                    || roleFamily(expected.role())
                            != roleFamily(live.role())) {
                wrongMaterial.add(entry.getKey() + " expected="
                        + expected.role() + "/" + expected.material()
                        + " current=" + live.role() + "/"
                        + live.material());
            }
        }
        for (Map.Entry<String, TreeVoxelSnapshotRenderer.Voxel> entry
                : currentByKey.entrySet()) {
            if (!targetByKey.containsKey(entry.getKey())) {
                extra.add(encoded(entry.getValue()));
            }
        }

        return new Report(
                missing.isEmpty() && extra.isEmpty()
                        && wrongMaterial.isEmpty(),
                missing.size(), extra.size(), wrongMaterial.size(),
                blocked.size(), sample(missing), sample(extra),
                sample(wrongMaterial), sample(blocked));
    }

    private Map<String, TreeVoxelSnapshotRenderer.Voxel> structuralByKey(
            Collection<TreeVoxelSnapshotRenderer.Voxel> voxels
    ) {
        Map<String, TreeVoxelSnapshotRenderer.Voxel> byKey =
                new LinkedHashMap<>();
        for (TreeVoxelSnapshotRenderer.Voxel voxel : voxels) {
            if (roleFamily(voxel.role()) == RoleFamily.DETAIL) {
                continue;
            }
            byKey.put(key(voxel), voxel);
        }
        return byKey;
    }

    private static RoleFamily roleFamily(TreeBlockRole role) {
        return switch (role) {
            case TRUNK, BRANCH, ROOT -> RoleFamily.WOOD;
            case CANOPY -> RoleFamily.CANOPY;
            default -> RoleFamily.DETAIL;
        };
    }

    private static String key(TreeVoxelSnapshotRenderer.Voxel voxel) {
        return voxel.x() + ":" + voxel.y() + ":" + voxel.z();
    }

    private static String encoded(TreeVoxelSnapshotRenderer.Voxel voxel) {
        return key(voxel) + " " + voxel.role() + " " + voxel.material();
    }

    private static List<String> sample(List<String> values) {
        return List.copyOf(values.subList(
                0, Math.min(SAMPLE_LIMIT, values.size())));
    }

    record Report(
            boolean passed,
            int missingTarget,
            int extraCurrent,
            int wrongMaterialOrRole,
            int blockedTarget,
            List<String> missingTargetSample,
            List<String> extraCurrentSample,
            List<String> wrongMaterialOrRoleSample,
            List<String> blockedTargetSample
    ) {
        Map<String, Object> asMap() {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("passed", passed);
            output.put("missing-target", missingTarget);
            output.put("extra-current", extraCurrent);
            output.put("wrong-material-or-role", wrongMaterialOrRole);
            output.put("blocked-target", blockedTarget);
            output.put("missing-target-sample", missingTargetSample);
            output.put("extra-current-sample", extraCurrentSample);
            output.put("wrong-material-or-role-sample",
                    wrongMaterialOrRoleSample);
            output.put("blocked-target-sample", blockedTargetSample);
            return Map.copyOf(output);
        }
    }

    private enum RoleFamily {
        WOOD,
        CANOPY,
        DETAIL
    }
}
