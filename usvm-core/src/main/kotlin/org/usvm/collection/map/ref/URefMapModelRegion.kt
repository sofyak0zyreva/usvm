package org.usvm.collection.map.ref

import org.usvm.UAddressSort
import org.usvm.UExpr
import io.ksmt.utils.uncheckedCast
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.UNonAliasingHeapRef
import org.usvm.USort
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

class UNonAliasingRefMapModelRegion<MapType, ValueSort : USort> internal constructor(
    private val regionId: URefMapRegionId<MapType, ValueSort>,
    private val model: UModelEvaluator<*>,
    private val cells: List<UNonAliasingRefMapCell<ValueSort>>,
) : UReadOnlyMemoryRegion<URefMapEntryLValue<MapType, ValueSort>, ValueSort> {

    private val cellsByIds: Map<Pair<UNonAliasingHeapAddress, UNonAliasingHeapAddress>, UNonAliasingRefMapCell<ValueSort>> =
        cells.associateBy { it.mapAddress to it.keyAddress }

    private val cellRefs: List<Pair<Pair<UExpr<UAddressSort>, UExpr<UAddressSort>>, UNonAliasingRefMapCell<ValueSort>>> by lazy {
        cells.mapNotNull { cell -> cell.evalCellRefs(model)?.let { it to cell } }
    }

    private val defaultValue: UExpr<ValueSort> by lazy { regionId.sort.accept(model).uncheckedCast() }

    override fun read(key: URefMapEntryLValue<MapType, ValueSort>): UExpr<ValueSort> {
        modelEnsureRightInputRef(key.mapRef)
        val mapRef = key.mapRef
        val keyRef = key.mapKey

        val cell = if (mapRef is UNonAliasingHeapRef && keyRef is UNonAliasingHeapRef) {
            cellsByIds[mapRef.id to keyRef.id]
        } else {
            cells.firstOrNull { it.matches(mapRef, keyRef) }
        }

        return cell?.evalCell(model) ?: defaultValue
    }

    private fun UNonAliasingRefMapCell<ValueSort>.matches(mapRef: UHeapRef, keyRef: UHeapRef): Boolean {
        val refs by lazy { cellRefs.firstOrNull { it.second === this }?.first }
        val mapMatches = if (mapRef is UNonAliasingHeapRef) mapRef.id == mapAddress else refs?.first == mapRef
        if (!mapMatches) return false
        return if (keyRef is UNonAliasingHeapRef) keyRef.id == keyAddress else refs?.second == keyRef
    }
}
