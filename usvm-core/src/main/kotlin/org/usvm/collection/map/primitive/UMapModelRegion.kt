package org.usvm.collection.map.primitive

import io.ksmt.decl.KDecl
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
import org.usvm.regions.Region

abstract class UMapModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    private val regionId: UMapRegionId<MapType, KeySort, ValueSort, Reg>
) : UReadOnlyMemoryRegion<UMapEntryLValue<MapType, KeySort, ValueSort, Reg>, ValueSort> {
    abstract val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>

    override fun read(key: UMapEntryLValue<MapType, KeySort, ValueSort, Reg>): UExpr<ValueSort> {
        val mapRef = modelEnsureConcreteInputRef(key.mapRef)
        return inputMap.read(mapRef to key.mapKey)
    }
}

class UMapLazyModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    regionId: UMapRegionId<MapType, KeySort, ValueSort, Reg>,
    private val model: UModelEvaluator<*>,
    private val inputMapDecoder: UCollectionDecoder<USymbolicMapKey<KeySort>, ValueSort>
) : UMapModelRegion<MapType, KeySort, ValueSort, Reg>(regionId) {
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort> by lazy {
        inputMapDecoder.decodeCollection(model)
    }
}

class UMapEagerModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    regionId: UMapRegionId<MapType, KeySort, ValueSort, Reg>,
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>
) : UMapModelRegion<MapType, KeySort, ValueSort, Reg>(regionId)

class UNonAliasingMapModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    regionId: UMapRegionId<MapType, KeySort, ValueSort, Reg>,
    private val model: UModelEvaluator<*>,
    private val nonAliasingMapDecoders:  Map<UNonAliasingHeapAddress, UCollectionDecoder<UExpr<KeySort>, ValueSort>>
) : UReadOnlyMemoryRegion<UMapEntryLValue<MapType, KeySort, ValueSort, Reg>, ValueSort> {

    val nonAliasingMaps: MutableMap<UNonAliasingHeapAddress, UReadOnlyMemoryRegion<UExpr<KeySort>, ValueSort>> by lazy {
        nonAliasingMapDecoders
            .mapValues { (_, decoder) -> decoder.decodeCollection(model)  }
            .toMutableMap()
    }
    override fun read(key: UMapEntryLValue<MapType, KeySort, ValueSort, Reg>): UExpr<ValueSort> {
        modelEnsureRightInputRef(key.mapRef)
        val defValue = nonAliasingMaps.values.firstOrNull()!!.read(key.mapKey)
        if (key.mapRef is UConcreteHeapRef) {
            val valAddress = model.addressesMapping.entries
                .firstOrNull { (_, value) -> value == key.mapRef }
                ?.key

            val rIdx = rAddressIdx(model, valAddress)
            return nonAliasingMaps[rIdx]?.read(key.mapKey) ?: defValue
        }
        else if (key.mapRef is UNonAliasingHeapRef) {
            return  (nonAliasingMaps[key.mapRef.id]?.read(key.mapKey)
                ?: defValue
                    )
        }
        return defValue
    }

}