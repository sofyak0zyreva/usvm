package org.usvm.collection.set.primitive

import io.ksmt.decl.KFuncDecl
import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UNonAliasingHeapAddress
import org.usvm.USort
import org.usvm.collection.set.UAllocatedSetUpdatesTranslator
import org.usvm.collection.set.UInputSetUpdatesTranslator
import org.usvm.collection.set.UNonAliasingSetCollectionDecoder
import org.usvm.collection.set.UNonAliasingSetUpdatesTranslator
import org.usvm.collection.set.USetCollectionDecoder
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.model.UMemory1DArray
import org.usvm.model.UModelEvaluator
import org.usvm.regions.Region
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder
import org.usvm.solver.URegionTranslator
import org.usvm.uctx
import java.util.IdentityHashMap

class USetRegionDecoder<SetType, ElementSort : USort, Reg : Region<Reg>>(
    private val regionId: USetRegionId<SetType, ElementSort, Reg>,
    private val exprTranslator: UExprTranslator<*, *>,
) : URegionDecoder<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort> {
    private val allocatedRegionTranslator =
        mutableMapOf<UAllocatedSetId<SetType, ElementSort, Reg>, UAllocatedSetTranslator<SetType, ElementSort, Reg>>()

    private val nonAliasingRegionTranslators =
        mutableMapOf<UNonAliasingSetId<SetType, ElementSort, Reg>, UNonAliasingSetTranslator<SetType, ElementSort, Reg>>()

    private var inputRegionTranslator: UInputSetTranslator<SetType, ElementSort, Reg>? = null

    fun allocatedSetTranslator(
        collectionId: UAllocatedSetId<SetType, ElementSort, Reg>,
    ): URegionTranslator<UAllocatedSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort> =
        allocatedRegionTranslator.getOrPut(collectionId) {
            UAllocatedSetTranslator(exprTranslator)
        }

    fun nonAliasingSetTranslator(
        collectionId: UNonAliasingSetId<SetType, ElementSort, Reg>,
    ): URegionTranslator<UNonAliasingSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort> =
        nonAliasingRegionTranslators.getOrPut(collectionId) {
            UNonAliasingSetTranslator(collectionId, exprTranslator)
        }

    fun inputSetTranslator(
        collectionId: UInputSetId<SetType, ElementSort, Reg>,
    ): URegionTranslator<UInputSetId<SetType, ElementSort, Reg>, USymbolicSetElement<ElementSort>, UBoolSort> {
        if (inputRegionTranslator == null) {
            inputRegionTranslator = UInputSetTranslator(collectionId, exprTranslator)
        }
        return inputRegionTranslator!!
    }

    override fun decodeLazyRegion(
        model: UModelEvaluator<*>,
        assertions: List<KExpr<KBoolSort>>,
    ): UReadOnlyMemoryRegion<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort>? =
        inputRegionTranslator?.let { USetLazyModelRegion(regionId, model, assertions, it) }
            ?: if (nonAliasingRegionTranslators.isNotEmpty()) {
                UNonAliasingSetModelRegion(regionId, model, assertions, nonAliasingRegionTranslators.values)
            } else {
                null
            }
}

private class UAllocatedSetTranslator<SetType, ElementSort : USort, Reg : Region<Reg>>(
    private val exprTranslator: UExprTranslator<*, *>,
) : URegionTranslator<UAllocatedSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort> {
    override fun translateReading(
        region: USymbolicCollection<UAllocatedSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort>,
        key: UExpr<ElementSort>,
    ): KExpr<UBoolSort> {
        val updatesTranslator = UAllocatedSetUpdatesTranslator(exprTranslator, key)
        return region.updates.accept(updatesTranslator, IdentityHashMap())
    }
}

internal interface UNonAliasingSetCollection<ElementSort : USort> {
    val id: UNonAliasingHeapAddress

    fun decodeElements(model: UModelEvaluator<*>, assertions: List<KExpr<KBoolSort>>): UMemory1DArray<ElementSort, UBoolSort>

    fun evalSetRef(model: UModelEvaluator<*>): UExpr<UAddressSort>?
}

private class UNonAliasingSetTranslator<SetType, ElementSort : USort, Reg : Region<Reg>>(
    collectionId: UNonAliasingSetId<SetType, ElementSort, Reg>,
    private val exprTranslator: UExprTranslator<*, *>,
) : URegionTranslator<UNonAliasingSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort>,
    UNonAliasingSetCollectionDecoder<ElementSort>(),
    UNonAliasingSetCollection<ElementSort> {

    override val id: UNonAliasingHeapAddress = collectionId.id

    override fun decodeElements(
        model: UModelEvaluator<*>,
        assertions: List<KExpr<KBoolSort>>,
    ): UMemory1DArray<ElementSort, UBoolSort> = decodeCollection(model, assertions)

    override fun evalSetRef(model: UModelEvaluator<*>): UExpr<UAddressSort>? {
        val ref = exprTranslator.ctx.nonAliasingHeapRefs[id] ?: return null
        return model.evalAndComplete(exprTranslator.translate(ref))
    }
    override fun translateReading(
        region: USymbolicCollection<UNonAliasingSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort>,
        key: UExpr<ElementSort>,
    ): KExpr<UBoolSort> {
        val updatesTranslator = UNonAliasingSetUpdatesTranslator(exprTranslator, inputFunction, key)
        return region.updates.accept(updatesTranslator, IdentityHashMap())
    }

    override val inputFunction: KFuncDecl<KBoolSort> =
        with(collectionId.sort.uctx) {
            mkFuncDecl(collectionId.toString(), boolSort, listOf(collectionId.elementSort))
        }
}

private class UInputSetTranslator<SetType, ElementSort : USort, Reg : Region<Reg>>(
    collectionId: UInputSetId<SetType, ElementSort, Reg>,
    private val exprTranslator: UExprTranslator<*, *>,
) : URegionTranslator<UInputSetId<SetType, ElementSort, Reg>, USymbolicSetElement<ElementSort>, UBoolSort>,
    USetCollectionDecoder<ElementSort>() {
    override val inputFunction = with(collectionId.sort.uctx) {
        mkFuncDecl(collectionId.toString(), boolSort, listOf(addressSort, collectionId.elementSort))
    }

    override fun translateReading(
        region:
        USymbolicCollection<UInputSetId<SetType, ElementSort, Reg>, USymbolicSetElement<ElementSort>, UBoolSort>,
        key: USymbolicSetElement<ElementSort>,
    ): KExpr<UBoolSort> {
        val updatesTranslator = UInputSetUpdatesTranslator(exprTranslator, inputFunction, key)
        return region.updates.accept(updatesTranslator, IdentityHashMap())
    }
}
