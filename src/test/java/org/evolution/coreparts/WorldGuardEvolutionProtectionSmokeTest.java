package org.evolution.coreparts;

import com.sk89q.worldguard.protection.flags.StateFlag;
import java.util.List;

public final class WorldGuardEvolutionProtectionSmokeTest {
    private WorldGuardEvolutionProtectionSmokeTest() {
    }

    public static void main(String[] args) {
        StateFlag flag = new StateFlag(
                WorldGuardEvolutionProtection.FLAG_NAME, true);

        require("evolution".equals(flag.getName()),
                "the public region flag must be named evolution");
        require(flag.getDefault() == StateFlag.State.ALLOW,
                "regions without an explicit flag must evolve normally");
        require(StateFlag.combine(List.of(
                        StateFlag.State.ALLOW,
                        StateFlag.State.DENY)) == StateFlag.State.DENY,
                "a deny at the effective priority must win over allow");

        EvolutionProtection fallback =
                new NoopEvolutionProtection("smoke-test");
        require(fallback.allows(null, "tree-evolution"),
                "missing WorldGuard must remain a fail-open optional integration");

        System.out.println("WorldGuard evolution protection smoke test passed: "
                + "flag=evolution default=allow deny-wins=true optional=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
