package org.usvm.collection.array

import io.ksmt.KAst
import io.ksmt.decl.KDecl
import io.ksmt.decl.KUninterpretedConstDecl
import io.ksmt.expr.KExpr
import io.ksmt.expr.KUninterpretedSortValue
import io.ksmt.solver.model.KFuncInterp
import io.ksmt.solver.model.KFuncInterpVarsFree
import io.ksmt.sort.KSort
import io.ksmt.sort.KUninterpretedSort
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UNonAliasingHeapAddress
import org.usvm.UNonAliasingHeapRef
import org.usvm.USort
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.model.modelEnsureRightInputRef
import org.usvm.solver.UCollectionDecoder

abstract class UArrayModelRegion<ArrayType, Sort : USort, USizeSort : USort>(
    private val regionId: UArrayRegionId<ArrayType, Sort, USizeSort>,
) : UReadOnlyMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {
    abstract val inputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex<USizeSort>, Sort>

    override fun read(key: UArrayIndexLValue<ArrayType, Sort, USizeSort>): UExpr<Sort> {
        val ref = modelEnsureConcreteInputRef(key.ref)
        return inputArray.read(ref to key.index)
    }
}

class UArrayLazyModelRegion<ArrayType, Sort : USort, USizeSort : USort>(
    regionId: UArrayRegionId<ArrayType, Sort, USizeSort>,
    private val model: UModelEvaluator<*>,
    private val inputArrayDecoder: UCollectionDecoder<USymbolicArrayIndex<USizeSort>, Sort>
) : UArrayModelRegion<ArrayType, Sort, USizeSort>(regionId) {
    override val inputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex<USizeSort>, Sort> by lazy {
        inputArrayDecoder.decodeCollection(model)
    }
}

class UArrayEagerModelRegion<ArrayType, Sort : USort, USizeSort : USort>(
    regionId: UArrayRegionId<ArrayType, Sort, USizeSort>,
    override val inputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex<USizeSort>, Sort>
) : UArrayModelRegion<ArrayType, Sort, USizeSort>(regionId)

class UNonAliasingArrayModelRegion<ArrayType, Sort : USort, USizeSort : USort>(
    private val regionId: UArrayRegionId<ArrayType, Sort, USizeSort>,
    private val model: UModelEvaluator<*>,
    private val nonAliasingArrayDecoders: Map<UNonAliasingHeapAddress, UCollectionDecoder<UExpr<USizeSort>, Sort>>
) : UReadOnlyMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {
    val nonAliasingArrays: MutableMap<UNonAliasingHeapAddress, UReadOnlyMemoryRegion<UExpr<USizeSort>, Sort>> by lazy {
        nonAliasingArrayDecoders
            .mapValues { (_, decoder) -> decoder.decodeCollection(model) }
            .toMutableMap()
    }

    override fun read(key: UArrayIndexLValue<ArrayType, Sort, USizeSort>): UExpr<Sort> {
        modelEnsureRightInputRef(key.ref)
        val defValue = nonAliasingArrays.values.firstOrNull()!!.read(key.index)
        if (key.ref is UConcreteHeapRef) {
            val valAddress = model.addressesMapping.entries
                .firstOrNull { (_, value) -> value == key.ref }
                ?.key

            val rIdx = rAddressIdx(model, valAddress)
            return nonAliasingArrays[rIdx]?.read(key.index) ?: defValue
        }
        else if (key.ref is UNonAliasingHeapRef) {
            return  (nonAliasingArrays[key.ref.id]?.read(key.index)
                ?: defValue
                    )
        }
        return defValue
    }
}

fun rAddressIdx(model: UModelEvaluator<*>, valAddress: UExpr<UAddressSort>?): Int? {
    val rAddress: KDecl<*>? = run {
        for (decl in model.model.declarations) {
            val interp = model.model.interpretation(decl)
            if (interp.toString() == valAddress.toString()) {
                return@run decl
            }
        }
        null
    }
    rAddress?.let{
        return rAddress.name.substringAfter("r").substringBefore("_Address").toIntOrNull()
    }
    return null
}