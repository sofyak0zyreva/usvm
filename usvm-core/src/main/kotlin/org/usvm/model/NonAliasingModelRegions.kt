package org.usvm.model

import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.UNonAliasingHeapRef
import org.usvm.USort
import org.usvm.isAllocated
import org.usvm.isStaticHeapRef
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.solver.UExprTranslator

class UNonAliasingRoots(
    private val model: UModelEvaluator<*>,
    private val exprTranslator: UExprTranslator<*, *>,
    private val ids: Collection<UNonAliasingHeapAddress>,
) {
    private val addresses: Set<UExpr<UAddressSort>> by lazy {
        ids.mapNotNullTo(mutableSetOf()) { id ->
            exprTranslator.ctx.nonAliasingHeapRefs[id]?.let { model.evalAndComplete(exprTranslator.translate(it)) }
        }
    }

    fun isRoot(ref: UHeapRef): Boolean = when (ref) {
        is UNonAliasingHeapRef -> true
        is UConcreteHeapRef -> isStaticHeapRef(ref) || ref in addresses
        else -> false
    }

    fun isRootOrAllocated(ref: UHeapRef): Boolean =
        (ref is UConcreteHeapRef && ref.address.isAllocated) || isRoot(ref)
}

class UNonAliasingOrInputModelRegion<Key, Sort : USort>(
    private val nonAliasing: UReadOnlyMemoryRegion<Key, Sort>,
    private val input: UReadOnlyMemoryRegion<Key, Sort>,
    private val isNonAliasing: (Key) -> Boolean,
) : UReadOnlyMemoryRegion<Key, Sort> {
    override fun read(key: Key): UExpr<Sort> =
        if (isNonAliasing(key)) nonAliasing.read(key) else input.read(key)
}
