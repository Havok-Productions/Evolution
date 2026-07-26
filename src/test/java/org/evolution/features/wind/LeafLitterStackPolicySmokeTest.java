package org.evolution.features.wind;

public final class LeafLitterStackPolicySmokeTest {
    private LeafLitterStackPolicySmokeTest() {
    }

    public static void main(String[] args) {
        assertAmount(2, LeafLitterStackPolicy.nextSegmentAmount(1, 4));
        assertAmount(4, LeafLitterStackPolicy.nextSegmentAmount(3, 4));
        assertAmount(4, LeafLitterStackPolicy.nextSegmentAmount(4, 4));
        System.out.println("Leaf litter stack policy smoke test passed");
    }

    private static void assertAmount(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(
                    "Expected segment amount " + expected + ", got " + actual);
        }
    }
}
