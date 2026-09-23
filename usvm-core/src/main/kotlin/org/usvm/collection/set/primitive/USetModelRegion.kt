package org.usvm.collection.set.primitive

import io.ksmt.decl.KDecl
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
import org.usvm.collection.array.rAddressIdx
import org.usvm.collection.field.getId
import org.usvm.collection.map.primitive.UMapEntryLValue
import org.usvm.collection.map.primitive.UMapRegionId
import org.usvm.collection.set.UNonAliasingSetCollectionDecoder
import org.usvm.collection.set.USetCollectionDecoder
import org.usvm.isFalse
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UMemory1DArray
import org.usvm.model.UMemory2DArray
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.model.modelEnsureRightInputRef
import org.usvm.regions.Region
import org.usvm.solver.UCollectionDecoder

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

class UNonAliasingSetModelRegion<SetType, ElementSort : USort, Reg : Region<Reg>>(
    private val regionId: USetRegionId<SetType, ElementSort, Reg>,
    private val model: UModelEvaluator<*>,
    assertions: List<KExpr<KBoolSort>>,
    private val nonAliasingMapDecoders:  Map<UNonAliasingSetId<SetType, ElementSort, Reg>, UNonAliasingSetCollectionDecoder<ElementSort>>
) : UReadOnlyMemoryRegion<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort>,
    USetReadOnlyRegion<SetType, ElementSort, Reg> {
        val refSets: UMemory1DArray<ElementSort, UBoolSort> by lazy {
            val x = nonAliasingMapDecoders.values.firstOrNull() !!
            x.decodeCollection(model, assertions)
        }
        val nonAliasingSets: Map<UNonAliasingHeapAddress, UMemory1DArray<ElementSort, UBoolSort>> by lazy {
            nonAliasingMapDecoders
                .mapKeys { (key, _) -> key.id }
                .mapValues { (_, decoder) -> decoder.decodeCollection(model, assertions)  }
                .toMutableMap()
        }

    override fun setEntries(ref: UHeapRef): UPrimitiveSetEntries<SetType, ElementSort, Reg> = with(regionId) {
        val setRef = modelEnsureConcreteInputRef(ref)
        val result = UPrimitiveSetEntries<SetType, ElementSort, Reg>()
        val set = nonAliasingSets[getId(setRef)] ?: return result
//        throw IllegalStateException("no model set for this NA ref")

        check(set.constValue.isFalse) { "Set model is not complete" }

        set.values.keys.forEach {
            result.add(USetEntryLValue(elementSort, setRef, it, setType, elementInfo))
        }

        return result
    }

    override fun read(key: USetEntryLValue<SetType, ElementSort, Reg>): UExpr<UBoolSort> {
        modelEnsureRightInputRef(key.setRef)
        val defValue = nonAliasingSets.values.firstOrNull()!!.read(key.setElement)

        if (key.setRef is UConcreteHeapRef) {
            val valAddress = model.addressesMapping.entries
                .firstOrNull { (_, value) -> value == key.setRef }
                ?.key

            val rIdx = rAddressIdx(model, valAddress)
            return nonAliasingSets[rIdx]?.read(key.setElement) ?: defValue
        }
        else if (key.setRef is UNonAliasingHeapRef) {
            return  (nonAliasingSets[key.setRef.id]?.read(key.setElement)
                ?: defValue
                    )
        }
        return defValue
    }
}
