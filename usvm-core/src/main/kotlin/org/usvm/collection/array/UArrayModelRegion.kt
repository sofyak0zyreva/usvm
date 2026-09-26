package org.usvm.collection.array

import io.ksmt.KAst
import io.ksmt.decl.KDecl
import io.ksmt.decl.KUninterpretedConstDecl
import io.ksmt.expr.KArrayConst
import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KExpr
import io.ksmt.expr.KUninterpretedSortValue
import io.ksmt.solver.model.KFuncInterp
import io.ksmt.solver.model.KFuncInterpVarsFree
import io.ksmt.sort.KSort
import io.ksmt.sort.KUninterpretedSort
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.NAHeapRefMap
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import io.ksmt.utils.uncheckedCast
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
        if (key.index is KBitVec32Value && key.index.intValue == 2) {
            println()
        }
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

class UNonAliasingArrayModelRegion<ArrayType, Sort : USort, USizeSort : USort> internal constructor(
    private val regionId: UArrayRegionId<ArrayType, Sort, USizeSort>,
    private val model: UModelEvaluator<*>,
    private val arrays: Map<UNonAliasingHeapAddress, UNonAliasingArrayCell<USizeSort, Sort>>,
) : UReadOnlyMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {

    private val arraysByAddress: List<Pair<UExpr<UAddressSort>, UNonAliasingArrayCell<USizeSort, Sort>>> by lazy {
        arrays.values.mapNotNull { array -> array.evalArrayRef(model)?.let { it to array } }
    }

    private val defaultValue: UExpr<Sort> by lazy { regionId.sort.accept(model).uncheckedCast() }

    override fun read(key: UArrayIndexLValue<ArrayType, Sort, USizeSort>): UExpr<Sort> {
        modelEnsureRightInputRef(key.ref)
        val array = when (val ref = key.ref) {
            is UNonAliasingHeapRef -> arrays[ref.id]
            is UConcreteHeapRef -> arraysByAddress.firstOrNull { it.first == ref }?.second
            else -> null
        }
        return array?.evalElement(model, key.index) ?: defaultValue
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