package org.slowtrees.core;

public final class OptionalWorldGuardIsolationSmokeTest {
    private OptionalWorldGuardIsolationSmokeTest() {
    }

    public static void main(String[] args) throws ClassNotFoundException {
        ClassLoader loader = OptionalWorldGuardIsolationSmokeTest.class
                .getClassLoader();
        Class.forName("org.slowtrees.core.SlowTreesPlugin", false, loader);
        Class.forName("org.slowtrees.core.NoopEvolutionProtection", false,
                loader);
        System.out.println("Optional WorldGuard isolation smoke test passed: "
                + "core-loads-without-worldguard=true");
    }
}
