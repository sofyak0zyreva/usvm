package org.usvm.samples.nonAliasing

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

class TestTestField : JavaMethodTestRunner() {

    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testCheckForAliasingI() {
        checkThisAndParamsMutations(
            TestField::checkForAliasing,
            ignoreNumberOfAnalysisResults,
            { _, _, _, _, c1, c2, r -> c1.value == 2 && c2.value == 2 && r == 0 },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun testCheckForAliasingNA() {
        checkDiscoveredPropertiesWithExceptions(
            TestField::checkForAliasing,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r -> r.getOrNull() == 500 },
            { _, c1, _, r -> c1 == null && r.isException<NullPointerException>() },
            { _, _, c2, r -> c2 == null && r.isException<NullPointerException>() },
        )
    }

    @Test
    fun testCheckForAliasingNestedFields() {
        checkDiscoveredPropertiesWithExceptions(
            TestField::checkForAliasingNestedFields,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r -> r.getOrNull() == 500 },
            { _, c1, _, r -> c1 == null && r.isException<NullPointerException>() },
            { _, _, c2, r -> c2 == null && r.isException<NullPointerException>() },
        )
    }

    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testCheckForAliasingParamsI() {
        checkThisAndParamsMutations(
            TestField::checkForAliasingParams,
            ignoreNumberOfAnalysisResults,
            { _, _, _, _, c1, c2, r -> c1 != null && c2 != null && r == 0 },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun testCheckForAliasingParamsNA() {
        checkDiscoveredPropertiesWithExceptions(
            TestField::checkForAliasingParams,
            eq(2),
            { _, c1, c2, r -> c1 == null && c2 == null && r.getOrNull() == 1 },
            { _, c1, c2, r -> c1 != c2 && r.getOrNull() == 500 },
        )
    }

    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testCheckForAliasingSubtypingI() {
        checkThisAndParamsMutations(
            TestField::checkForAliasingSubtyping,
            ignoreNumberOfAnalysisResults,
            { _, _, _, _, n1, n2, r -> n1.data == 20 && n2.data == 20 && r == 0 },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun testCheckForAliasingSubtypingNA() {
        checkDiscoveredPropertiesWithExceptions(
            TestField::checkForAliasingSubtyping,
            eq(3),
            { _, _, _, r -> r.getOrNull() == 500 },
            { _, n1, _, r -> n1 == null && r.isException<NullPointerException>() },
            { _, _, n2, r -> n2 == null && r.isException<NullPointerException>() },
        )
    }

    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testCheckForAliasingCyclicRefI() {
        checkThisAndParamsMutations(
            TestField::checkForAliasingCyclicRef,
            ignoreNumberOfAnalysisResults,
            { _, _, _, head, r -> head.value == 2 && r == 0 },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun testCheckForAliasingCyclicRefNA() {
        checkDiscoveredPropertiesWithExceptions(
            TestField::checkForAliasingCyclicRef,
            eq(3),
            { _, _, r -> r.getOrNull() == 500 },
            { _, head, r -> head == null && r.isException<NullPointerException>() },
            { _, head, r -> head.next == null && r.isException<NullPointerException>() },
        )
    }

    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testArrayElementAliasing() {
        checkDiscoveredPropertiesWithExceptions(
            TestField::testArrayElementAliasing,
            ignoreNumberOfAnalysisResults,
            { _, arr, i, j, r -> arr != null && arr[i] == arr[j] && r.getOrNull() == 0 }
        )
    }

    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testCrossArrayAliasing() {
        checkDiscoveredPropertiesWithExceptions(
            TestField::testCrossArrayAliasing,
            ignoreNumberOfAnalysisResults,
            { _, a1, a2, r -> a1 != null && a2 != null && r.getOrNull() == 0 }
        )
    }

    @Test
    fun testNestedFieldsAccess() {
        checkDiscoveredPropertiesWithExceptions(
            TestField::testNestedFieldsAccess,
            eq(5),
            { _, c, r -> c == null && r.isException<NullPointerException>() },
            { _, c, r -> c.value == null && r.isException<NullPointerException>() },
            { _, c, r -> c.value.value == null && r.isException<NullPointerException>() },
            { _, c, r -> r.getOrNull() == 1 },
            { _, c, r -> r.getOrNull() == 500 },
        )
    }

    @Disabled(
        "For purposes of showing the difference between execution modes: fails in non-aliasing, succeeds in aliasing"
    )
    @Test
    fun testCheckForAliasingFieldObjectsI() {
        checkDiscoveredPropertiesWithExceptions(
            TestField::checkForAliasingFieldObjects,
            ignoreNumberOfAnalysisResults,
            { _, f, r -> f.fieldA != null && f.fieldB != null && r.isException<AssertionError>() },
        )
    }

    @Test
    fun testCheckForAliasingFieldObjectsNA() {
        checkDiscoveredPropertiesWithExceptions(
            TestField::checkForAliasingFieldObjects,
            ignoreNumberOfAnalysisResults,
            { _, f, r -> f.fieldA != null && f.fieldB != null && r.getOrNull() == null },
        )
    }
}
