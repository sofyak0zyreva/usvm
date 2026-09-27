package org.usvm.collection.field

import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.sort.KArraySort
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.mkConst
import org.usvm.UAddressSort
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.USort
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.model.UModelEvaluator
import org.usvm.solver.U1DUpdatesTranslator
import org.usvm.solver.UCollectionDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder
import org.usvm.solver.URegionTranslator
import org.usvm.uctx
import java.util.IdentityHashMap

class UFieldRegionDecoder<Field, Sort : USort>(
    private val regionId: UFieldsRegionId<Field, Sort>,
    private val exprTranslator: UExprTranslator<*, *>,
) : URegionDecoder<UFieldLValue<Field, Sort>, Sort> {
    private var inputRegionTranslator: UInputFieldRegionTranslator<Field, Sort>? = null

    private val nonAliasingRegions =
        mutableMapOf<UNonAliasingHeapAddress, UNonAliasingFieldRegionTranslator<Field, Sort>>()

    fun inputFieldRegionTranslator(
        collectionId: UInputFieldId<Field, Sort>,
    ): URegionTranslator<UInputFieldId<Field, Sort>, UHeapRef, Sort> {
        if (inputRegionTranslator == null) {
            inputRegionTranslator = UInputFieldRegionTranslator(collectionId, exprTranslator)
        }
        return inputRegionTranslator!!
    }

    fun nonAliasingFieldRegionTranslator(
        collectionId: UNonAliasingFieldId<Field, Sort>,
    ): URegionTranslator<UNonAliasingFieldId<Field, Sort>, UHeapRef, Sort> =
        nonAliasingRegions.getOrPut(collectionId.id) {
            UNonAliasingFieldRegionTranslator(collectionId, exprTranslator)
        }

    override fun decodeLazyRegion(
        model: UModelEvaluator<*>,
        assertions: List<KExpr<KBoolSort>>,
    ) = inputRegionTranslator?.let {
        UFieldsLazyModelRegion(regionId, model, it)
    } ?: nonAliasingRegions.values.firstOrNull()?.let {
        UNonAliasingFieldsModelRegion(regionId, model, it)
    }
}

private class UInputFieldRegionTranslator<Field, Sort : USort>(
    private val collectionId: UInputFieldId<Field, Sort>,
    exprTranslator: UExprTranslator<*, *>,
) : URegionTranslator<UInputFieldId<Field, Sort>, UHeapRef, Sort>, UCollectionDecoder<UHeapRef, Sort> {
    private val initialValue = with(collectionId.sort.uctx) {
        mkArraySort(addressSort, collectionId.sort).mkConst(collectionId.toString())
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<UAddressSort, Sort>>>()
    private val updatesTranslator = UInputFieldUpdateTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>,
        key: UHeapRef,
    ): KExpr<Sort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: UModelEvaluator<*>,
    ): UReadOnlyMemoryRegion<UHeapRef, Sort> =
        model.evalAndCompleteArray1DMemoryRegion(initialValue.decl)
}

private class UNonAliasingFieldRegionTranslator<Field, Sort : USort>(
    private val collectionId: UNonAliasingFieldId<Field, Sort>,
    exprTranslator: UExprTranslator<*, *>,
) : URegionTranslator<UNonAliasingFieldId<Field, Sort>, UHeapRef, Sort>, UCollectionDecoder<UHeapRef, Sort> {

    private val initialValue = with(collectionId.sort.uctx) {
        mkArraySort(addressSort, collectionId.sort).mkConst(collectionId.toString())
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<UAddressSort, Sort>>>()
    private val updatesTranslator = UInputFieldUpdateTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UNonAliasingFieldId<Field, Sort>, UHeapRef, Sort>,
        key: UHeapRef,
    ): KExpr<Sort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: UModelEvaluator<*>,
    ): UReadOnlyMemoryRegion<UHeapRef, Sort> =
        model.evalAndCompleteArray1DMemoryRegion(initialValue.decl)
}

private class UInputFieldUpdateTranslator<Sort : USort>(
    exprTranslator: UExprTranslator<*, *>,
    initialValue: KExpr<KArraySort<UAddressSort, Sort>>,
) : U1DUpdatesTranslator<UAddressSort, Sort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<UAddressSort, Sort>>,
        update: URangedUpdateNode<*, *, UHeapRef, Sort>,
    ): KExpr<KArraySort<UAddressSort, Sort>> {
        error("Fields has no ranged updates")
    }
}
