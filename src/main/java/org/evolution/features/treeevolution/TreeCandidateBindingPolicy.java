package org.evolution.features.treeevolution;

/**
 * ## Identity rule between discovery candidates and persisted tree DNA.
 */
final class TreeCandidateBindingPolicy {
    private TreeCandidateBindingPolicy() {
    }

    static boolean isCanonical(TreeCandidate candidate, TreeDna dna) {
        return candidate != null
                && dna != null
                && matches(candidate.baseKey(), dna.key());
    }

    static boolean matches(String candidateKey, String dnaKey) {
        return candidateKey != null && candidateKey.equals(dnaKey);
    }
}
