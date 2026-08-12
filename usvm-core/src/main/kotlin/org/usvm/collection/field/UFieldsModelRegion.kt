package org.usvm.collection.field

import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.model.modelEnsureRightInputRef
import org.usvm.solver.UCollectionDecoder

abstract class UFieldsModelRegion<Field, Sort : USort>(
    private val regionId: UFieldsRegionId<Field, Sort>,
) : UReadOnlyMemoryRegion<UFieldLValue<Field, Sort>, Sort> {
    abstract val inputFields: UReadOnlyMemoryRegion<UHeapRef, Sort>

    override fun read(key: UFieldLValue<Field, Sort>): UExpr<Sort> {
        val ref = modelEnsureConcreteInputRef(key.ref)
        return inputFields.read(ref)
    }
}

class UFieldsLazyModelRegion<Field, Sort : USort>(
    regionId: UFieldsRegionId<Field, Sort>,
    private val model: UModelEvaluator<*>,
    private val inputFieldsDecoder: UCollectionDecoder<UHeapRef, Sort>
) : UFieldsModelRegion<Field, Sort>(regionId) {
    override val inputFields: UReadOnlyMemoryRegion<UHeapRef, Sort> by lazy {
        inputFieldsDecoder.decodeCollection(model)
    }
}

class UFieldsEagerModelRegion<Field, Sort : USort>(
    regionId: UFieldsRegionId<Field, Sort>,
    override val inputFields: UReadOnlyMemoryRegion<UHeapRef, Sort>
) : UFieldsModelRegion<Field, Sort>(regionId)

class UNonAliasingFieldsModelRegion<Field, Sort : USort>(
    private val regionId: UFieldsRegionId<Field, Sort>,
    private val model: UModelEvaluator<*>,
    private val nonAliasingFieldsDecoder: UCollectionDecoder<UHeapRef, Sort>
): UReadOnlyMemoryRegion<UFieldLValue<Field, Sort>, Sort> {
    val nonAliasingFields: UReadOnlyMemoryRegion<UHeapRef, Sort> by lazy {
    nonAliasingFieldsDecoder.decodeCollection(model)
    }
    override fun read(key: UFieldLValue<Field, Sort>): UExpr<Sort> {
        modelEnsureRightInputRef(key.ref)
        return nonAliasingFields.read(key.ref)
    }
}