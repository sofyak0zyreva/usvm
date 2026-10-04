package org.usvm.samples.nonAliasing

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

class TestTestArray : JavaMethodTestRunner() {
    @Test
    fun testCheckForAliasingArray() {
        checkDiscoveredPropertiesWithExceptions(
            TestArray::checkForAliasingArray,
            eq(5),
            { _, _, _, r -> r.getOrNull() == 500 },
            { _, a1, _, r -> a1 == null && r.isException<NullPointerException>() },
            { _, _, a2, r -> a2 == null && r.isException<NullPointerException>() },
            { _, a1, _, r -> a1 != null && a1.isEmpty() && r.isException<IndexOutOfBoundsException>() },
            { _, _, a2, r -> a2 != null && a2.isEmpty() && r.isException<IndexOutOfBoundsException>() },
        )
    }

    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testCheckForAliasingArrayCustom() {
        checkDiscoveredPropertiesWithExceptions(
            TestArray::checkForAliasingArrayCustom,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r -> r.getOrNull() == 0 },
        )
    }

    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testCheckForAliasing2DArrayCustom() {
        checkDiscoveredPropertiesWithExceptions(
            TestArray::checkForAliasing2DArrayCustom,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r -> r.getOrNull() == 0 },
        )
    }

    @Test
    fun testCheckForAliasing2DArray() {
        checkDiscoveredPropertiesWithExceptions(
            TestArray::checkForAliasing2DArray,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r -> r.getOrNull() == 500 },
            { _, a1, _, r -> a1 == null && r.isException<NullPointerException>() },
            { _, _, a2, r -> a2 == null && r.isException<NullPointerException>() },
            { _, a1, _, r -> a1 != null && a1.isEmpty() && r.isException<IndexOutOfBoundsException>() },
            { _, _, a2, r -> a2 != null && a2.isEmpty() && r.isException<IndexOutOfBoundsException>() },
        )
    }

    @Test
    fun test2DArrayFieldAccess() {
        checkDiscoveredPropertiesWithExceptions(
            TestArray::test2DArrayFieldAccess,
            ignoreNumberOfAnalysisResults,
            { _, array, i, j, r -> array == null && r.isException<IllegalArgumentException>() },
            { _, array, i, j, r -> array != null && (i < 0 || i >= array.size) && r.isException<IllegalArgumentException>() },
            { _, array, i, j, r -> array != null && array[i] == null && r.isException<IllegalArgumentException>() },
            { _, array, i, j, r -> array != null && array[i] != null && array[i][j] == null && r.isException<IllegalArgumentException>() },
            { _, array, i, j, r -> array != null && array[i] != null && array[i][j] != null && r.getOrNull() == 1 },
            { _, array, i, j, r -> array != null && array[i] != null && array[i][j] != null && r.getOrNull() == 500 }
        )
    }

    @Test
    fun test3DArray() {
        checkDiscoveredPropertiesWithExceptions(
            TestArray::test3DArray,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r -> r.getOrNull() == 1 },
            { _, _, _, r -> r.getOrNull() == 500 },
        )
    }

    @Test
    fun testMultipleReads() {
        checkDiscoveredPropertiesWithExceptions(
            TestArray::testMultipleReads,
            ignoreNumberOfAnalysisResults,
            { _, grid, i, j, r -> grid[i][j] != null && grid[j][j] != null && r.getOrNull() == 100 },
            { _, _, _, _, r -> r.getOrNull() == 200 },
        )
    }

    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testCheckForAliasingObjects2DArrayI() {
        checkDiscoveredPropertiesWithExceptions(
            TestArray::checkForAliasingObjects2DArray,
            ignoreNumberOfAnalysisResults,
            { _, a, obj, r -> (obj.x != obj.i || obj.y != obj.j) && a[obj.x][obj.y] == a[obj.i][obj.j] && r.isException<AssertionError>() },
        )
    }

    @Test
    fun testCheckForAliasingObjects2DArrayNA() {
        checkDiscoveredPropertiesWithExceptions(
            TestArray::checkForAliasingObjects2DArray,
            ignoreNumberOfAnalysisResults,
            { _, a, obj, r -> obj.x == obj.i && obj.y == obj.j && a[obj.x][obj.y] == a[obj.i][obj.j] && r.getOrNull() == null },
            { _, a, obj, r -> a[obj.x][obj.y] != a[obj.i][obj.j] && r.getOrNull() == null }
        )
    }

    @Test
    fun testWritePropagation() {
        checkDiscoveredPropertiesWithExceptions(
            TestArray::testWritePropagation,
            ignoreNumberOfAnalysisResults,
            { _, a, m, n, r -> a[m] == a[n] && r.getOrNull() == null }
        )
    }
}
