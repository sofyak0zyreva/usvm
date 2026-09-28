package org.usvm.samples.nonAliasing;

public class TestField {
    public static class Container {
        public int value;
    }

    public static class Container2 {
        public Container value;
    }

    public static class Container3 {
        public Container2 value;
    }

    public int checkForAliasing(Container c1, Container c2) {
        c1.value = 1;
        c2.value = 2;

        if (c1.value == 2) {
//          Reachable if c1 == c2 and both are not null
            return 0;
        }
         return 500;
    }

    public int checkForAliasingNestedFields (Container2 c1, Container2 c2) {
        c1.value.value = 1;
        c2.value.value = 2;

        if (c1.value.value == 2) {
//          Reachable if c1 == c2 and both are not null
            return 0;
        }
        return 500;
    }

    public int checkForAliasingParams(Container c1, Container c2) {
        if (c1 == null && c2 == null) return 1;
        if (c1 == c2) {
//          Reachable if c1 == c2 and both are not null
            return 0;
        }
        return 500;
    }

    public static class Node1 {
        public int data;
    }

    public static class SpecialNode extends Node1 {
        public int extra;
    }

    public int checkForAliasingSubtyping(Node1 n1, SpecialNode n2) {
        n1.data = 10;
        n2.data = 20;

        if (n1.data == 20) {
//          Aliasing via subtyping
            return 0;
        }
        return 500;
    }

    public static class Cell {
        public int value;
        public Cell next;
    }

    public int checkForAliasingCyclicRef(Cell head) {
        head.value = 1;
        head.next.value = 2;

        if (head.value == 2) {
//          Cyclic heap aliasing
            return 0;
        }
        return 500;
    }

    public int testArrayElementAliasing(Container[] arr, int i, int j) {
        if (arr == null || i < 0 || j < 0 || i >= arr.length || j >= arr.length) return -1;
        if (arr[i] == null || arr[j] == null) return -2;

        arr[i].value = 100;
        arr[j].value = 200;

        if (arr[i].value == 200 && i != j) {
            return 0; // Reachable only if distinct array slots arr[i] and arr[j] alias the same object
        }
        return 500;
    }

    public int testCrossArrayAliasing(Container[] a1, Container[] a2) {
        if (a1 == null || a2 == null || a1.length == 0 || a2.length == 0) return -1;
        if (a1[0] == null || a2[0] == null) return -2;

        a1[0].value = 10;
        a2[0].value = 20;

        if (a1[0].value == 20) {
            return 0; // Reachable if a1 == a2 OR a1[0] == a2[0]
        }
        return 500; // Expected path in Non-Aliasing mode (a1 != a2 and a1[0] != a2[0])
    }

    public int testNestedFieldsAccess(Container3 c3) {
        if (c3 == null) throw new NullPointerException();
        Container2 c2 = c3.value;
        if (c2 == null) throw new NullPointerException();
        Container c1 = c2.value;
        if (c1 == null) throw new NullPointerException();
        if (c1.value >= -1) {
            return 1;
        }

        return 500;
    }

    public static class Fields {
        public Container fieldA;
        public Container fieldB;
    }

    public void checkForAliasingFieldObjects(Fields f) {
        if (f == null) {
            throw new IllegalArgumentException();
        }

        Container a = f.fieldA;
        Container b = f.fieldB;

        if (a == null && b == null) {
            throw new IllegalArgumentException("if both are null then they are equal");
        }

        assert (a != b);
    }
}

