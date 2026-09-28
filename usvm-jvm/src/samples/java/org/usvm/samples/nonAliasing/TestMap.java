package org.usvm.samples.nonAliasing;

public class TestMap {
    public static class Item {
        public int id;
    }

    public int testHashMapAliasingRef(org.usvm.samples.approximations.TestMap<Item, Integer> m1, org.usvm.samples.approximations.TestMap<Item, Integer> m2) {
        if (m1 == null && m2 == null) return 1;
        if (m1 == m2) {
            return 0; // Reachable if m1 == m2 or if both are not null
        }
        return 500;
    }

    public int testHashMapAliasing(org.usvm.samples.approximations.TestMap<Integer, Integer> m1, org.usvm.samples.approximations.TestMap<Integer, Integer> m2) {
        if (m1 == null && m2 == null) return 1;
        if (m1 == m2) {
            return 0; // Reachable if m1 == m2 or if both are not null
        }
        return 500;
    }

    public static int testMapFieldAccess(org.usvm.samples.approximations.TestMap<String, Item> map) {
        if (map.size() < 10) {
            return 0;
        }

        if (!map.containsKey("abc")) {
            return 1;
        }

        Item value = map.get("abc");
        if (value.id != 5) {
            return 2;
        }

        return 500;
    }

    public static int checkForAliasingObjectsMap(org.usvm.samples.approximations.TestMap<String, Item> map) {
        if (map.size() < 10) return 0;
        String s1 = "abc";
        if (!map.containsKey(s1)) return 1;
        Item a = map.get(s1);
        String s2 = "cba";
        if (!map.containsKey(s2)) return 2;
        Item b = map.get(s2);
        if (a == null && b == null) throw new IllegalArgumentException();
        assert (a != b);
        s2 = "abc";
        b = map.get(s2);
        assert (a == b);
        return 3;
    }
}
