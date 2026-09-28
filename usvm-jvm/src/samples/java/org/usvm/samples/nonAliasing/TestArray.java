package org.usvm.samples.nonAliasing;

public class TestArray {
    public int checkForAliasingArray(int[] a, int[] b) {
        a[0] = 1;
        b[0] = 2;

        if (a[0] == 2) {
            return 0;
        }
        return 500;
    }

    public int checkForAliasing2DArray(int[][] a, int[][] b) {
        a[0][0] = 1;
        b[0][0] = 2;

        if (a[0][0] == 2) {
            return 0;
        }
        return 500;
    }

    public static class Parent {
        int field;
    }

    public int checkForAliasingArrayCustom(Parent[] a, Parent[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return -1;
        if (a[0] == null || b[0] == null) return -2;
        a[0].field = 1;
        b[0].field = 2;

        if (a[0].field == 2) {
            return 0;
        }
        return 500;
    }

    public int checkForAliasing2DArrayCustom(Parent[][] a, Parent[][] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return -1;
        if (a[0] == null || b[0] == null) return -2;
        if (a[0][0] == null || b[0][0] == null) return -3;
        a[0][0].field = 1;
        b[0][0].field = 2;

        if (a[0][0].field == 2) {
            return 0;
        }
        return 500;
    }

    public int test2DArrayFieldAccess(Parent[][] array, int i, int j) {
        if (array == null || i < 0 || i >= array.length) {
            throw new IllegalArgumentException();
        }
        Parent[] row = array[i];
        if (row == null || j < 0 || j >= row.length) {
            throw new IllegalArgumentException();
        }
        Parent elem = row[j];
        if (elem == null) {
            throw new IllegalArgumentException();
        }

        if (elem.field >= -1) {
            return 1;
        }

        return 500;
    }

    public int test3DArray(int[][][] array, FourInts indices) {
        int elem = array[indices.i][indices.j][indices.x];

        if (elem >= -1) {
            return 1;
        }

        return 500;
    }

    public static class Object {
        public int x;
        public int y;
    }

    public int testMultipleReads(Object[][] grid, int i, int j) {
        if (grid == null || i < 0 || i >= grid.length) return -1;
        if (grid[i] == null || j < 0 || j >= grid[i].length) return -1;
        if (grid[i][j] == null) return -1;

        int val1 = grid[i][j].x;
        int val2 = grid[i][j].y;

        if (j < grid.length && grid[j] != null && j < grid[j].length && grid[j][j] != null) {
            int val3 = grid[j][j].x;
            if (val1 + val2 + val3 == 42) {
                return 100;
            }
        }

        return 200;
    }

    public static class FourInts{
        public int x;
        public int y;
        public int i;
        public int j;
    }

    public void checkForAliasingObjects2DArray(Parent[][] array, FourInts obj){
        Parent a = array[obj.i][obj.j];
        Parent b = array[obj.x][obj.y];
        if (a == null && b == null ) throw new IllegalArgumentException("if both are null then they are equal");

        if (obj.x == obj.i && obj.y == obj.j) {
            assert (a == b);
        }
        else {
            assert (a != b);
        }
    }
}
