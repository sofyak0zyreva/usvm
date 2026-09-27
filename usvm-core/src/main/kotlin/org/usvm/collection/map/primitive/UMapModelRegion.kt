package org.usvm.collection.map.primitive

import io.ksmt.utils.uncheckedCast
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UNonAliasingHeapAddress
import org.usvm.UNonAliasingHeapRef
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.model.modelEnsureRightInputRef
import org.usvm.regions.Region
import org.usvm.solver.UCollectionDecoder

abstract class UMapModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    private val regionId: UMapRegionId<MapType, KeySort, ValueSort, Reg>,
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
    private val inputMapDecoder: UCollectionDecoder<USymbolicMapKey<KeySort>, ValueSort>,
) : UMapModelRegion<MapType, KeySort, ValueSort, Reg>(regionId) {
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort> by lazy {
        inputMapDecoder.decodeCollection(model)
    }
}

class UMapEagerModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    regionId: UMapRegionId<MapType, KeySort, ValueSort, Reg>,
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>,
) : UMapModelRegion<MapType, KeySort, ValueSort, Reg>(regionId)

class UNonAliasingMapModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> internal constructor(
    private val regionId: UMapRegionId<MapType, KeySort, ValueSort, Reg>,
    private val model: UModelEvaluator<*>,
    private val maps: Map<UNonAliasingHeapAddress, UNonAliasingMapCollection<KeySort, ValueSort>>,
) : UReadOnlyMemoryRegion<UMapEntryLValue<MapType, KeySort, ValueSort, Reg>, ValueSort> {

    private val decodedMaps = mutableMapOf<UNonAliasingHeapAddress, UReadOnlyMemoryRegion<UExpr<KeySort>, ValueSort>>()

    private val idsByAddress: List<Pair<UExpr<UAddressSort>, UNonAliasingHeapAddress>> by lazy {
        maps.values.mapNotNull { map -> map.evalMapRef(model)?.let { it to map.id } }
    }

    private val defaultValue: UExpr<ValueSort> by lazy { regionId.sort.accept(model).uncheckedCast() }

    override fun read(key: UMapEntryLValue<MapType, KeySort, ValueSort, Reg>): UExpr<ValueSort> {
        modelEnsureRightInputRef(key.mapRef)
        val id = when (val ref = key.mapRef) {
            is UNonAliasingHeapRef -> ref.id
            is UConcreteHeapRef -> idsByAddress.firstOrNull { it.first == ref }?.second
            else -> null
        }
        val map = id?.let { maps[it] } ?: return defaultValue
        val decoded = decodedMaps.getOrPut(map.id) { map.decodeCollection(model) }
        return decoded.read(key.mapKey)
    }
}
