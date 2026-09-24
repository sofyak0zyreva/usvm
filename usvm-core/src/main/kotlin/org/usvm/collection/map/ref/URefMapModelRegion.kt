package org.usvm.collection.map.ref

import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UNonAliasingHeapAddress
import org.usvm.UNonAliasingHeapRef
import org.usvm.USort
import org.usvm.collection.array.rAddressIdx
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.model.modelEnsureRightInputRef
import org.usvm.solver.UCollectionDecoder

abstract class URefMapModelRegion<MapType, ValueSort : USort>(
    private val regionId: URefMapRegionId<MapType, ValueSort>
) : UReadOnlyMemoryRegion<URefMapEntryLValue<MapType, ValueSort>, ValueSort> {
    abstract val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort>

    override fun read(key: URefMapEntryLValue<MapType, ValueSort>): UExpr<ValueSort> {
        val mapRef = modelEnsureConcreteInputRef(key.mapRef)
        return inputMap.read(mapRef to key.mapKey)
    }
}

class URefMapLazyModelRegion<MapType, ValueSort : USort>(
    regionId: URefMapRegionId<MapType, ValueSort>,
    private val model: UModelEvaluator<*>,
    private val inputMapDecoder: UCollectionDecoder<USymbolicMapKey<UAddressSort>, ValueSort>
) : URefMapModelRegion<MapType, ValueSort>(regionId) {
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort> by lazy {
        inputMapDecoder.decodeCollection(model)
    }
}

class URefMapEagerModelRegion<MapType, ValueSort : USort>(
    regionId: URefMapRegionId<MapType, ValueSort>,
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort>
) : URefMapModelRegion<MapType, ValueSort>(regionId)

class UNonAliasingRefMapModelRegion<MapType, ValueSort : USort>(
    private val regionId: URefMapRegionId<MapType, ValueSort>,
    private val model: UModelEvaluator<*>,
    private val nonAliasingMapDecoders:  Map<UNonAliasingHeapAddress, UCollectionDecoder<USymbolicMapKey<UAddressSort>, ValueSort>>
) : UReadOnlyMemoryRegion<URefMapEntryLValue<MapType, ValueSort>, ValueSort> {

    val nonAliasingMaps: MutableMap<UNonAliasingHeapAddress, UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort>> by lazy {
        nonAliasingMapDecoders
            .mapValues { (_, decoder) -> decoder.decodeCollection(model)  }
            .toMutableMap()
    }

    override fun read(key: URefMapEntryLValue<MapType, ValueSort>): UExpr<ValueSort> {
        modelEnsureRightInputRef(key.mapRef)
        val defValue = nonAliasingMaps.values.firstOrNull()!!.read(key.mapRef to key.mapKey)
        if (key.mapRef is UConcreteHeapRef) {
            val valAddress = model.addressesMapping.entries
                .firstOrNull { (_, value) -> value == key.mapRef }
                ?.key

            val rIdx = rAddressIdx(model, valAddress)
            return nonAliasingMaps[rIdx]?.read(key.mapRef to key.mapKey) ?: defValue
        }
        else if (key.mapRef is UNonAliasingHeapRef) {
            return  (nonAliasingMaps[key.mapRef.id]?.read(key.mapRef to key.mapKey)
                ?: defValue
                    )
        }
        return defValue
    }

}