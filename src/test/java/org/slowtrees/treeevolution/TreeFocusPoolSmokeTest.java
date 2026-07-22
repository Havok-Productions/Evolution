package org.slowtrees.treeevolution;

import java.util.List;

public final class TreeFocusPoolSmokeTest {
    private TreeFocusPoolSmokeTest() {
    }

    public static void main(String[] args) {
        TreeFocusPool pool = new TreeFocusPool();
        for (int index = 0; index < TreeFocusPool.CAPACITY; index++) {
            require(pool.acquire("tree-" + index), "each available slot must accept one tree");
        }
        require(!pool.acquire("tree-overflow"), "the pool must stay bounded");
        require(!pool.acquire("tree-0"), "the same tree must not occupy two slots");

        List<TreeFocusPool.Entry> first = pool.nextRotation();
        List<TreeFocusPool.Entry> second = pool.nextRotation();
        require(first.size() == TreeFocusPool.CAPACITY, "all active trees must be scheduled");
        require(!first.get(0).treeKey().equals(second.get(0).treeKey()),
                "successive passes must rotate the first candidate");

        int noProgress = 0;
        for (int pass = 0; pass < TreeFocusPolicy.MAX_NO_PROGRESS_PASSES; pass++) {
            noProgress = pool.updateProgress("tree-0", false);
        }
        require(TreeFocusPolicy.shouldYield(noProgress), "blocked slots must eventually yield");
        require(pool.release("tree-0"), "a yielded tree must leave its slot");
        require(pool.acquire("tree-replacement"), "a released slot must accept another tree");

        System.out.println("Tree focus pool smoke test passed: capacity="
                + TreeFocusPool.CAPACITY + " rotation=true replacement=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
