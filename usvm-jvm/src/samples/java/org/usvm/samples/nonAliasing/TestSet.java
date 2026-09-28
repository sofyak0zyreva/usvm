package org.usvm.samples.nonAliasing;

public class TestSet {
    public static class Element {
        public int id;
    }
    public int testHashSetAliasingRef(TestRefSet<Element> s1, TestRefSet<Element> s2) {
        if (s1 == null && s2 == null) return 1;
        if (s1 == s2) {
            return 0; // Reachable if m1 == m2 or if both are not null
        }
        return 500;
    }

    public int testHashSetAliasingPrimitive(TestRefSet<Integer> s1, TestRefSet<Integer> s2) {
        if (s1 == null && s2 == null) return 1;
        if (s1 == s2) {
            return 0; // Reachable if m1 == m2 and both are not null
        }
        return 500;
    }
}
