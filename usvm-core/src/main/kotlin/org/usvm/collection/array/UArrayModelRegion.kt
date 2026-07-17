package org.usvm.collection.array

import org.usvm.UExpr
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
    private val nonAliasingArrayDecoder: UCollectionDecoder<UExpr<USizeSort>, Sort>
) : UReadOnlyMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {
    val nonAliasingArray: UReadOnlyMemoryRegion<UExpr<USizeSort>, Sort> by lazy {
        nonAliasingArrayDecoder.decodeCollection(model)
    }
    override fun read(key: UArrayIndexLValue<ArrayType, Sort, USizeSort>): UExpr<Sort> {
        modelEnsureRightInputRef(key.ref)
        return nonAliasingArray.read(key.index)
    }
}