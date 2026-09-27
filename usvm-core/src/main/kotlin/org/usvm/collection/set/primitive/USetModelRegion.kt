package org.usvm.collection.set.primitive

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.UNonAliasingHeapRef
import org.usvm.USort
import org.usvm.collection.set.USetCollectionDecoder
import org.usvm.isFalse
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UMemory1DArray
import org.usvm.model.UMemory2DArray
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.model.modelEnsureRightInputRef
import org.usvm.regions.Region

abstract class USetModelRegion<SetType, ElementSort : USort, Reg : Region<Reg>>(
    private val regionId: USetRegionId<SetType, ElementSort, Reg>
) : UReadOnlyMemoryRegion<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort>,
    USetReadOnlyRegion<SetType, ElementSort, Reg> {
    abstract val inputSet: UMemory2DArray<UAddressSort, ElementSort, UBoolSort>

    override fun read(key: USetEntryLValue<SetType, ElementSort, Reg>): UBoolExpr {
        val setRef = modelEnsureConcreteInputRef(key.setRef)
        return inputSet.read(setRef to key.setElement)
    }

    override fun setEntries(ref: UHeapRef): UPrimitiveSetEntries<SetType, ElementSort, Reg> = with(regionId) {
        val setRef = modelEnsureConcreteInputRef(ref)

        check(inputSet.constValue.isFalse) { "Set model is not complete" }

        val result = UPrimitiveSetEntries<SetType, ElementSort, Reg>()
        inputSet.values.keys.forEach {
            if (it.first == setRef) {
                result.add(USetEntryLValue(elementSort, setRef, it.second, setType, elementInfo))
            }
        }

        return result
    }
}

class USetLazyModelRegion<SetType, ElementSort : USort, Reg : Region<Reg>>(
    regionId: USetRegionId<SetType, ElementSort, Reg>,
    model: UModelEvaluator<*>,
    assertions: List<KExpr<KBoolSort>>,
    inputSetDecoder: USetCollectionDecoder<ElementSort>
) : USetModelRegion<SetType, ElementSort, Reg>(regionId) {
    override val inputSet: UMemory2DArray<UAddressSort, ElementSort, UBoolSort> by lazy {
        inputSetDecoder.decodeCollection(model, assertions)
    }
}

class USetEagerModelRegion<SetType, ElementSort : USort, Reg : Region<Reg>>(
    regionId: USetRegionId<SetType, ElementSort, Reg>,
    override val inputSet: UMemory2DArray<UAddressSort, ElementSort, UBoolSort>
) : USetModelRegion<SetType, ElementSort, Reg>(regionId)

class UNonAliasingSetModelRegion<SetType, ElementSort : USort, Reg : Region<Reg>> internal constructor(
    private val regionId: USetRegionId<SetType, ElementSort, Reg>,
    private val model: UModelEvaluator<*>,
    private val assertions: List<KExpr<KBoolSort>>,
    sets: Collection<UNonAliasingSetCollection<ElementSort>>,
) : UReadOnlyMemoryRegion<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort>,
    USetReadOnlyRegion<SetType, ElementSort, Reg> {

    private val setsById = sets.associateBy { it.id }

    private val decodedSets = mutableMapOf<UNonAliasingHeapAddress, UMemory1DArray<ElementSort, UBoolSort>>()

    private val idsByAddress: List<Pair<UExpr<UAddressSort>, UNonAliasingHeapAddress>> by lazy {
        sets.mapNotNull { set -> set.evalSetRef(model)?.let { it to set.id } }
    }

    private fun idOf(ref: UHeapRef): UNonAliasingHeapAddress? = when (ref) {
        is UNonAliasingHeapRef -> ref.id
        is UConcreteHeapRef -> idsByAddress.firstOrNull { it.first == ref }?.second
        else -> null
    }

    private fun elementsOf(id: UNonAliasingHeapAddress): UMemory1DArray<ElementSort, UBoolSort>? {
        val set = setsById[id] ?: return null
        return decodedSets.getOrPut(id) { set.decodeElements(model, assertions) }
    }

    override fun setEntries(ref: UHeapRef): UPrimitiveSetEntries<SetType, ElementSort, Reg> = with(regionId) {
        modelEnsureRightInputRef(ref)
        val result = UPrimitiveSetEntries<SetType, ElementSort, Reg>()
        val elements = idOf(ref)?.let { elementsOf(it) } ?: return result

        check(elements.constValue.isFalse) { "Set model is not complete" }

        elements.values.keys.forEach {
            result.add(USetEntryLValue(elementSort, ref, it, setType, elementInfo))
        }
        return result
    }

    override fun read(key: USetEntryLValue<SetType, ElementSort, Reg>): UExpr<UBoolSort> {
        modelEnsureRightInputRef(key.setRef)
        val elements = idOf(key.setRef)?.let { elementsOf(it) } ?: return model.ctx.falseExpr
        return elements.read(key.setElement)
    }
}
