package org.usvm.collection.set.ref

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.collection.set.USetCollectionDecoder
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.isFalse
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UMemory2DArray
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef

abstract class URefSetModelRegion<SetType>(
    private val regionId: URefSetRegionId<SetType>,
) : UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort>, URefSetReadOnlyRegion<SetType> {
    abstract val inputSet: UMemory2DArray<UAddressSort, UAddressSort, UBoolSort>

    override fun read(key: URefSetEntryLValue<SetType>): UBoolExpr {
        val setRef = modelEnsureConcreteInputRef(key.setRef)
        return inputSet.read(setRef to key.setElement)
    }

    override fun setEntries(ref: UHeapRef): URefSetEntries<SetType> {
        val setRef = modelEnsureConcreteInputRef(ref)

        check(inputSet.constValue.isFalse) { "Set model is not complete" }

        val result = URefSetEntries<SetType>()
        inputSet.values.keys.forEach {
            if (it.first == setRef) {
                result.add(URefSetEntryLValue(setRef, it.second, regionId.setType))
            }
        }

        return result
    }
}

class URefSetLazyModelRegion<SetType>(
    regionId: URefSetRegionId<SetType>,
    model: UModelEvaluator<*>,
    assertions: List<KExpr<KBoolSort>>,
    inputSetDecoder: USetCollectionDecoder<UAddressSort>,
) : URefSetModelRegion<SetType>(regionId) {
    override val inputSet: UMemory2DArray<UAddressSort, UAddressSort, UBoolSort> by lazy {
        inputSetDecoder.decodeCollection(model, assertions)
    }
}

class URefSetEagerModelRegion<SetType>(
    regionId: URefSetRegionId<SetType>,
    override val inputSet: UMemory2DArray<UAddressSort, UAddressSort, UBoolSort>,
) : URefSetModelRegion<SetType>(regionId)

class UNonAliasingRefSetModelRegion<SetType>(
    regionId: URefSetRegionId<SetType>,
    private val model: UModelEvaluator<*>,
    private val assertions: List<KExpr<KBoolSort>>,
    private val cells: Collection<USetCollectionDecoder<UAddressSort>>,
) : URefSetModelRegion<SetType>(regionId) {
    override val inputSet: UMemory2DArray<UAddressSort, UAddressSort, UBoolSort> by lazy {
        var entries = persistentHashMapOf<Pair<UExpr<UAddressSort>, UExpr<UAddressSort>>, UBoolExpr>()
        cells.forEach { cell ->
            cell.decodeCollection(model, assertions).values.forEach { (key, value) ->
                entries = entries.put(key, value, model.ctx.defaultOwnership)
            }
        }
        UMemory2DArray(entries, constValue = model.ctx.falseExpr)
    }
}
