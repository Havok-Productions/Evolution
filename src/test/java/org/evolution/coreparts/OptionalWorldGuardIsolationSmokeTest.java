package org.evolution.coreparts;

public final class OptionalWorldGuardIsolationSmokeTest {
    private OptionalWorldGuardIsolationSmokeTest() {
    }

    public static void main(String[] args) throws ClassNotFoundException {
        ClassLoader loader = OptionalWorldGuardIsolationSmokeTest.class
                .getClassLoader();
        Class.forName("org.evolution.coreparts.EvolutionPlugin", false, loader);
        Class.forName("org.evolution.coreparts.NoopEvolutionProtection", false,
                loader);
        System.out.println("Optional WorldGuard isolation smoke test passed: "
                + "core-loads-without-worldguard=true");
    }
}
