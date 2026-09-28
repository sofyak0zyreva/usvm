package org.usvm.samples.nonAliasing

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TestTestRefSet : JavaMethodTestRunner() {
    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testComputeI() {
        checkDiscoveredPropertiesWithExceptions(
            TestSet::testHashSetAliasingRef,
            ignoreNumberOfAnalysisResults,
            { _, s1, s2, r -> s1 != null && s2 != null && r.getOrNull() == 0 },
        )
    }

    @Test
    fun testComputeNA() {
        checkDiscoveredPropertiesWithExceptions(
            TestSet::testHashSetAliasingRef,
            eq(2),
            { _, s1, s2, r -> s1 == null && s2 == null && r.getOrNull() == 1 },
            { _, s1, s2, r -> s1 != s2 && r.getOrNull() == 500 },
        )
    }

    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testCompute2I() {
        checkDiscoveredPropertiesWithExceptions(
            TestSet::testHashSetAliasingPrimitive,
            ignoreNumberOfAnalysisResults,
            { _, s1, s2, r -> s1 != null && s2 != null && r.getOrNull() == 0 },
        )
    }

    @Test
    fun testCompute2NA() {
        checkDiscoveredPropertiesWithExceptions(
            TestSet::testHashSetAliasingPrimitive,
            ignoreNumberOfAnalysisResults,
            { _, s1, s2, r -> s1 == null && s2 == null && r.getOrNull() == 1 },
            { _, s1, s2, r -> s1 != s2 && r.getOrNull() == 500 },
        )
    }
}
