package org.usvm.collection.array

import io.ksmt.expr.KUninterpretedSortValue
import io.ksmt.sort.KUninterpretedSort
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

            val strValAddress = valAddress.toString()
            val valAddressIdx = strValAddress.substringAfterLast("!").toIntOrNull()
            val allValues: List<UExpr<Sort>> = nonAliasingArrays.values.map { region ->
                region.read(key.index)
            }
            val interpretations = model.model.toString()
            fun findRegisterNumber(targetAddress: String, dump: String): Int? {
                val cleanTarget = targetAddress.trim()
                val lines = dump.lines().map { it.trim() }

                for (i in lines.indices) {
                    val line = lines[i]

                    // Check if this line is an address definition like "(r1_Address () Address):="
                    if (line.startsWith("(r") && line.contains("_Address")) {
                        // Extract the number X from (rX_Address...
                        val rNum = line.substringAfter("(r").substringBefore("_Address").toIntOrNull()

                        // Look ahead in current or next lines for the target address
                        val lookaheadBlock = lines.subList(i, minOf(i + 3, lines.size)).joinToString(" ")
                        if (lookaheadBlock.contains(cleanTarget)) {
                            return rNum
                        }
                    }
                }
                return null
            }

            val rAddressIdx = findRegisterNumber(strValAddress, interpretations)
            val x = nonAliasingArrays[rAddressIdx]?.read(key.index) ?: defValue
            return x

//            val x =  (nonAliasingArrays[key.ref.address]?.read(key.index)
//                ?: defValue
//                    )
//            return x
        }
        else if (key.ref is UNonAliasingHeapRef) {
            val x =  (nonAliasingArrays[key.ref.id]?.read(key.index)
                ?: defValue
                    )
            return  x
        }
        return defValue
    }
//    val nonAliasingArray: UReadOnlyMemoryRegion<UExpr<USizeSort>, Sort> by lazy {
//        nonAliasingArrayDecoder.decodeCollection(model)
//    }
//    override fun read(key: UArrayIndexLValue<ArrayType, Sort, USizeSort>): UExpr<Sort> {
//        modelEnsureRightInputRef(key.ref)
//        return nonAliasingArray.read(key.index)
//    }
}