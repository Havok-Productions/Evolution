package org.slowtrees.waves;

public final class ShoreRunupPolicySmokeTest {
    private ShoreRunupPolicySmokeTest() {
    }

    public static void main(String[] args) {
        ShoreRunupPolicy policy = new ShoreRunupPolicy();

        require(!policy.hasArrived(4, true),
                "an offshore fizzling sample must not start land run-up");
        require(!policy.hasArrived(1, false),
                "a traveling front must not place water on land before impact");
        require(policy.hasArrived(1, true),
                "the final water edge must admit a fizzling shore impact");

        require(policy.maximumReachableGroundY(63, 1.80D) == 64,
                "a 1.8-block crest may reach only one block above water");
        require(policy.canReachGround(63, 64, 1.80D),
                "a tall crest should wash onto a one-block shore");
        require(!policy.canReachGround(63, 71, 2.0D),
                "a two-block crest must not climb an eight-block land mass");
        require(policy.canReachGround(63, 63, 0.20D)
                        && !policy.canReachGround(63, 64, 0.20D),
                "a low ripple may wet level shore but cannot climb");

        System.out.println("Shore run-up policy smoke test passed: arrival and absolute crest-height gates hold.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
