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
import org.usvm.NAReadingIdMap
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
        if (key.index is KBitVec32Value && (key.index.intValue == 1 || key.index.intValue == 16)) {
            println()
        }

//        val y = trimPrefixAndSuffix(x[-3]?.symbol.toString())
        modelEnsureRightInputRef(key.ref)
        val defValue = nonAliasingArrays.values.firstOrNull()!!.read(key.index)
        if (key.ref is UConcreteHeapRef) {
            val valAddress = model.addressesMapping.entries
                .firstOrNull { (_, value) -> value == key.ref }
                ?.key

            val rIdx = rAddressIdx(model, valAddress)
            if (rIdx != null) {
                return nonAliasingArrays[rIdx]?.read(key.index)
                    ?: defValue
            }
            else {
                val x = NAHeapRefMap
                val collections = x.mapValues { (_, v) -> trimPrefixAndSuffix(v.symbol.toString()) }
                val decl = findDecl(model, valAddress).toString()
                val z = extractId(decl)
                val key1 = collections.entries.find { it.value == z }?.key
                return nonAliasingArrays[key1]?.read(key.index)
                    ?: defValue
            }
        }
        else if (key.ref is UNonAliasingHeapRef) {
            return  (nonAliasingArrays[key.ref.id]?.read(key.index)
                ?: defValue
                    )
        }
        return defValue
    }
}
fun extractId(input: String): String {
    return input.trim()
        .removePrefix("(")
        .substringBefore(" ")
}
fun trimPrefixAndSuffix(input: String): String {
    return input.replace(Regex("""^<>@|\[.*]$"""), "")
}

fun findDecl(model: UModelEvaluator<*>, valAddress: UExpr<UAddressSort>?): KDecl<*>? {
    for (decl in model.model.declarations) {
        val interp = model.model.interpretation(decl)
        val x = interp?.default
        val y = if (x is KArrayConst<*,*>) x.value.toString() else x.toString()
        if (y == valAddress.toString()) {
            return decl
        }
    }
    return null
}

fun collectionIdx(model: UModelEvaluator<*>, valAddress: UExpr<UAddressSort>?) {

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