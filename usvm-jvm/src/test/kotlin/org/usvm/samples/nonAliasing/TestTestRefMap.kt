package org.usvm.samples.nonAliasing

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.approximations.ApproximationsTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

class TestTestRefMap : ApproximationsTestRunner() {
    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testComputeI() {
        checkDiscoveredPropertiesWithExceptions(
            TestMap::testHashMapAliasingRef,
            ignoreNumberOfAnalysisResults,
            { _, m1, m2, r -> m1 != null && m2 != null && r.getOrNull() == 0 },
        )
    }

    @Test
    fun testComputeNA() {
        checkDiscoveredPropertiesWithExceptions(
            TestMap::testHashMapAliasingRef,
            eq(2),
            { _, m1, m2, r -> m1 == null && m2 == null && r.getOrNull() == 1 },
            { _, m1, m2, r -> m1 != m2 && r.getOrNull() == 500 },
        )
    }

    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testCompute2I() {
        checkDiscoveredPropertiesWithExceptions(
            TestMap::testHashMapAliasing,
            ignoreNumberOfAnalysisResults,
            { _, m1, m2, r -> m1 != null && m2 != null && r.getOrNull() == 0 },
        )
    }

    @Test
    fun testCompute2NA() {
        checkDiscoveredPropertiesWithExceptions(
            TestMap::testHashMapAliasing,
            eq(2),
            { _, m1, m2, r -> m1 == null && m2 == null && r.getOrNull() == 1 },
            { _, m1, m2, r -> m1 != m2 && r.getOrNull() == 500 },
        )
    }

    @Test
    fun testMapFieldAccess() {
        checkDiscoveredProperties(
            TestMap::testMapFieldAccess,
            ignoreNumberOfAnalysisResults,
            { map, r -> map.size() < 10 && r == 0 },
            { map, r -> !map.containsKey("abc") && r == 1 },
            { map, r -> map.size() < 10 && r == 0 },
            { map, r -> map.get("abc").id != 5 && r == 2 },
            { map, r -> map.get("abc").id == 5 && r == 500 },
        )
    }

    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testCheckForAliasingObjectsMapI() {
        checkDiscoveredPropertiesWithExceptions(
            TestMap::checkForAliasingObjectsMap,
            ignoreNumberOfAnalysisResults,
            { map, r -> map.get("abc") == map.get("cba") && r.isException<AssertionError>() },
        )
    }

    @Test
    fun testCheckForAliasingObjectsMapNA() {
        checkDiscoveredPropertiesWithExceptions(
            TestMap::checkForAliasingObjectsMap,
            ignoreNumberOfAnalysisResults,
            { map, r -> map.size() < 10 && r.getOrNull() == 0 },
            { map, r -> !map.containsKey("abc") && r.getOrNull() == 1 },
            { map, r -> !map.containsKey("cba") && r.getOrNull() == 2 },
            { map, r -> map.get("abc") == null && map.get("cba") == null && r.isException<IllegalArgumentException>() },
            { map, r -> map.get("abc") != map.get("cba") && r.getOrNull() == 3 },
        )
    }
}
