package org.usvm.memory.key

import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.memory.USymbolicCollectionKeyInfo

object UNonAliasingHeapRefKeyInfo : USymbolicCollectionKeyInfo<UHeapRef, UHeapRefRegion> by UHeapRefKeyInfo {
    override fun eqSymbolic(ctx: UContext<*>, key1: UHeapRef, key2: UHeapRef): UBoolExpr =
        ctx.mkNonAliasingKeyEq(key1, key2)
}
