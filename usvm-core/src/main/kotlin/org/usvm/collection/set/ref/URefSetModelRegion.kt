package org.usvm.collection.set.ref

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
import org.usvm.collection.array.rAddressIdx
import org.usvm.collection.field.getId
import org.usvm.collection.set.UNonAliasingSetCollectionDecoder
import org.usvm.collection.set.USetCollectionDecoder
import org.usvm.isFalse
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UMemory1DArray
import org.usvm.model.UMemory2DArray
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.model.modelEnsureRightInputRef

abstract class URefSetModelRegion<SetType>(
    private val regionId: URefSetRegionId<SetType>
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
    inputSetDecoder: USetCollectionDecoder<UAddressSort>
) : URefSetModelRegion<SetType>(regionId) {
    override val inputSet: UMemory2DArray<UAddressSort, UAddressSort, UBoolSort> by lazy {
        inputSetDecoder.decodeCollection(model, assertions)
    }
}

class URefSetEagerModelRegion<SetType>(
    regionId: URefSetRegionId<SetType>, override val inputSet: UMemory2DArray<UAddressSort, UAddressSort, UBoolSort>
) : URefSetModelRegion<SetType>(regionId)

class UNonAliasingRefSetModelRegion<SetType>(
    private val regionId: URefSetRegionId<SetType>,
    private val model: UModelEvaluator<*>,
    assertions: List<KExpr<KBoolSort>>,
    val nonAliasingMapDecoders: Map<UNonAliasingRefSetWithNonAliasingElementsId<SetType>, USetCollectionDecoder<UAddressSort>>
) : UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort>, URefSetReadOnlyRegion<SetType> {
    val refSets: UMemory2DArray<UAddressSort, UAddressSort, UBoolSort> by lazy {
        val x = nonAliasingMapDecoders.values.firstOrNull() !!
        x.decodeCollection(model, assertions)
    }
    val nonAliasingRefSets: Map<UNonAliasingHeapAddress, UMemory2DArray<UAddressSort, UAddressSort, UBoolSort>> by lazy {
        nonAliasingMapDecoders
            .mapKeys { (key, _) -> key.setId }
            .mapValues { (_, decoder) -> decoder.decodeCollection(model, assertions)  }
            .toMutableMap()
        }

    override fun setEntries(ref: UHeapRef): URefSetEntries<SetType> {
        val setRef = modelEnsureConcreteInputRef(ref)
        val result = URefSetEntries<SetType>()
//            ?: throw IllegalStateException("no model set for this NA ref")


        check(refSets.constValue.isFalse) { "Set model is not complete" }
//        nonAliasingRefSets.values.keys.forEach {
//            if (it.first == setRef) {
//                result.add(URefSetEntryLValue(setRef, it.second, regionId.setType))
//            }
//        }
        refSets.values.keys.forEach {
//            result.add(URefSetEntryLValue(setRef, it.second, regionId.setType))
            if (it.first == setRef) {
                result.add(URefSetEntryLValue(setRef, it.second, regionId.setType))
            }
        }
        return result
    }

    override fun read(key: URefSetEntryLValue<SetType>): UExpr<UBoolSort> {
        modelEnsureRightInputRef(key.setRef)
        val defValue = nonAliasingRefSets.values.firstOrNull()!!.read(key.setRef to key.setElement)

        if (key.setRef is UConcreteHeapRef) {
            val valAddress = model.addressesMapping.entries
                .firstOrNull { (_, value) -> value == key.setRef }
                ?.key

            val rIdx = rAddressIdx(model, valAddress)
            return nonAliasingRefSets[rIdx]?.read(key.setElement to key.setElement) ?: defValue
        }
        else if (key.setRef is UNonAliasingHeapRef) {
            return  (nonAliasingRefSets[key.setRef.id]?.read(key.setElement to key.setElement)
                ?: defValue
                    )
        }
        return defValue
    }
}