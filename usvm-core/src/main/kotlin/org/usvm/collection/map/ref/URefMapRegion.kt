package org.usvm.collection.map.ref

import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.USort
import org.usvm.collection.field.getId
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.collection.set.ref.UAllocatedRefSetWithNonAliasingElements
import org.usvm.collection.set.ref.UNonAliasingRefSetWithNonAliasingElements
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.collection.set.ref.URefSetRegion
import org.usvm.collections.immutable.getOrPut
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UPairElementLocations
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.foldHeapRef2
import org.usvm.memory.foldHeapRefWithStaticAsSymbolic
import org.usvm.memory.guardedWrite
import org.usvm.memory.mapWithStaticAsSymbolic
import org.usvm.memory.nonAliasingPairElementLocation
import org.usvm.memory.pairElementLocations
import org.usvm.sampleUValue
import org.usvm.uctx

data class URefMapEntryLValue<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    val mapRef: UHeapRef,
    val mapKey: UHeapRef,
    val mapType: MapType,
) : ULValue<URefMapEntryLValue<MapType, ValueSort>, ValueSort> {
    override val memoryRegionId: UMemoryRegionId<URefMapEntryLValue<MapType, ValueSort>, ValueSort> =
        URefMapRegionId(sort, mapType)

    override val key: URefMapEntryLValue<MapType, ValueSort>
        get() = this
}

data class URefMapRegionId<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    val mapType: MapType,
) : UMemoryRegionId<URefMapEntryLValue<MapType, ValueSort>, ValueSort> {
    override fun emptyRegion(): UMemoryRegion<URefMapEntryLValue<MapType, ValueSort>, ValueSort> =
        URefMapMemoryRegion(sort, mapType)
}

interface URefMapRegion<MapType, ValueSort : USort> :
    UMemoryRegion<URefMapEntryLValue<MapType, ValueSort>, ValueSort> {
    fun merge(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        mapType: MapType,
        sort: ValueSort,
        keySet: URefSetRegion<MapType>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): URefMapRegion<MapType, ValueSort>
}

typealias UAllocatedRefMapWithInputKeys<MapType, ValueSort> =
    USymbolicCollection<UAllocatedRefMapWithInputKeysId<MapType, ValueSort>, UHeapRef, ValueSort>

typealias UAllocatedRefMapWithNonAliasingKeys<MapType, ValueSort> =
    USymbolicCollection<UAllocatedRefMapWithNonAliasingKeysId<MapType, ValueSort>, UHeapRef, ValueSort>

typealias UInputRefMapWithAllocatedKeys<MapType, ValueSort> =
    USymbolicCollection<UInputRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort>

typealias UNonAliasingRefMapWithAllocatedKeys<MapType, ValueSort> =
    USymbolicCollection<UNonAliasingRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort>

typealias UInputRefMap<MapType, ValueSort> =
    USymbolicCollection<UInputRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort>

typealias UNonAliasingRefMapWithNonAliasingKeys<MapType, ValueSort> =
    USymbolicCollection<UNonAliasingRefMapWithNonAliasingKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort>


internal data class UAllocatedRefMapWithAllocatedKeysId(
    val mapAddress: UConcreteHeapAddress,
    val keyAddress: UConcreteHeapAddress,
)

internal data class URefMapElementLocations<MapType, ValueSort : USort>(
    val allocatedMapNonAliasingKeys: UPairElementLocations<
        UAllocatedRefMapWithNonAliasingKeysId<MapType, ValueSort>,
        UAllocatedRefMapWithNonAliasingKeys<MapType, ValueSort>,
        > = pairElementLocations(),
    val nonAliasingMapAllocatedKeys: UPairElementLocations<
        UNonAliasingRefMapWithAllocatedKeysId<MapType, ValueSort>,
        UNonAliasingRefMapWithAllocatedKeys<MapType, ValueSort>,
        > = pairElementLocations(),
    val nonAliasingMapNonAliasingKeys: UPairElementLocations<
        UNonAliasingRefMapWithNonAliasingKeysId<MapType, ValueSort>,
        UNonAliasingRefMapWithNonAliasingKeys<MapType, ValueSort>,
        > = pairElementLocations(),
)

internal class URefMapMemoryRegion<MapType, ValueSort : USort>(
    private val valueSort: ValueSort,
    private val mapType: MapType,
    private var allocatedMapWithAllocatedKeys: UPersistentHashMap<UAllocatedRefMapWithAllocatedKeysId, UExpr<ValueSort>> =
        persistentHashMapOf(),
    private var inputMapWithAllocatedKeys:
    UPersistentHashMap<UInputRefMapWithAllocatedKeysId<MapType, ValueSort>, UInputRefMapWithAllocatedKeys<MapType, ValueSort>> = persistentHashMapOf(),
    private var allocatedMapWithInputKeys:
    UPersistentHashMap<UAllocatedRefMapWithInputKeysId<MapType, ValueSort>, UAllocatedRefMapWithInputKeys<MapType, ValueSort>> = persistentHashMapOf(),
    private var inputMapWithInputKeys: UInputRefMap<MapType, ValueSort>? = null,
    private var allocatedMapWithNonAliasingKeys:
    UPersistentHashMap<UAllocatedRefMapWithNonAliasingKeysId<MapType, ValueSort>, UAllocatedRefMapWithNonAliasingKeys<MapType, ValueSort>> = persistentHashMapOf(),
    private var nonAliasingMapWithAllocatedKeys:
    UPersistentHashMap<UNonAliasingRefMapWithAllocatedKeysId<MapType, ValueSort>, UNonAliasingRefMapWithAllocatedKeys<MapType, ValueSort>> = persistentHashMapOf(),
    private var nonAliasingMapWithNonAliasingKeys:
    UPersistentHashMap<UNonAliasingRefMapWithNonAliasingKeysId<MapType, ValueSort>, UNonAliasingRefMapWithNonAliasingKeys<MapType, ValueSort>> = persistentHashMapOf(),
    private var elementLocations: URefMapElementLocations<MapType, ValueSort> = URefMapElementLocations(),
) : URefMapRegion<MapType, ValueSort> {

    private val defaultOwnership = valueSort.uctx.defaultOwnership

    private fun copy(
        allocatedMapWithAllocatedKeys: UPersistentHashMap<UAllocatedRefMapWithAllocatedKeysId, UExpr<ValueSort>> =
            this.allocatedMapWithAllocatedKeys,
        inputMapWithAllocatedKeys:
        UPersistentHashMap<UInputRefMapWithAllocatedKeysId<MapType, ValueSort>, UInputRefMapWithAllocatedKeys<MapType, ValueSort>> =
            this.inputMapWithAllocatedKeys,
        allocatedMapWithInputKeys:
        UPersistentHashMap<UAllocatedRefMapWithInputKeysId<MapType, ValueSort>, UAllocatedRefMapWithInputKeys<MapType, ValueSort>> =
            this.allocatedMapWithInputKeys,
        inputMapWithInputKeys: UInputRefMap<MapType, ValueSort>? = this.inputMapWithInputKeys,
        allocatedMapWithNonAliasingKeys:
        UPersistentHashMap<UAllocatedRefMapWithNonAliasingKeysId<MapType, ValueSort>, UAllocatedRefMapWithNonAliasingKeys<MapType, ValueSort>> =
            this.allocatedMapWithNonAliasingKeys,
        nonAliasingMapWithAllocatedKeys:
        UPersistentHashMap<UNonAliasingRefMapWithAllocatedKeysId<MapType, ValueSort>, UNonAliasingRefMapWithAllocatedKeys<MapType, ValueSort>> =
            this.nonAliasingMapWithAllocatedKeys,
        nonAliasingMapWithNonAliasingKeys:
        UPersistentHashMap<UNonAliasingRefMapWithNonAliasingKeysId<MapType, ValueSort>, UNonAliasingRefMapWithNonAliasingKeys<MapType, ValueSort>> =
            this.nonAliasingMapWithNonAliasingKeys,
        elementLocations: URefMapElementLocations<MapType, ValueSort> = this.elementLocations,
    ) = URefMapMemoryRegion(
        valueSort,
        mapType,
        allocatedMapWithAllocatedKeys,
        inputMapWithAllocatedKeys,
        allocatedMapWithInputKeys,
        inputMapWithInputKeys,
        allocatedMapWithNonAliasingKeys,
        nonAliasingMapWithAllocatedKeys,
        nonAliasingMapWithNonAliasingKeys,
        elementLocations,
    )

    private fun nonAliasingRef(id: UNonAliasingHeapAddress): UHeapRef? = valueSort.uctx.nonAliasingHeapRefs[id]

    private fun concreteRef(address: UConcreteHeapAddress): UHeapRef = valueSort.uctx.mkConcreteHeapRef(address)

    private fun <Id, C> materializePair(
        collections: UPersistentHashMap<Id, C>,
        locations: UPairElementLocations<Id, C>,
        id: Id,
        mapRef: UHeapRef,
        keyRef: UHeapRef,
        empty: () -> C,
    ): Triple<UPersistentHashMap<Id, C>, UPairElementLocations<Id, C>, C> {
        collections[id]?.let { return Triple(collections, locations, it) }
        val locationId = nonAliasingPairElementLocation(mapRef, keyRef)
        val (newLocations, collection) = if (locationId == null) {
            locations to empty()
        } else {
            locations.materialize(locationId, id, mapRef to keyRef, empty())
        }
        return Triple(collections.put(id, collection, defaultOwnership), newLocations, collection)
    }

    private fun <Id, C> applyToPair(
        collections: UPersistentHashMap<Id, C>,
        locations: UPairElementLocations<Id, C>,
        id: Id,
        mapRef: UHeapRef,
        keyRef: UHeapRef,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
        empty: () -> C,
        op: (C, UBoolExpr, MutabilityOwnership) -> C,
    ): Pair<UPersistentHashMap<Id, C>, UPairElementLocations<Id, C>> {
        val (materialized, materializedLocations, collection) =
            materializePair(collections, locations, id, mapRef, keyRef, empty)
        val locationId = nonAliasingPairElementLocation(mapRef, keyRef)
            ?: return materialized.put(id, op(collection, guard, ownership), ownership) to materializedLocations

        var result = materialized
        val newLocations = materializedLocations.apply(
            locationId,
            mapRef to keyRef,
            guard,
            ownership,
            op
        ) { memberId, f ->
            result[memberId]?.let { result = result.put(memberId, f(it), ownership) }
        }
        return result to newLocations
    }

    private fun applyToAllocatedMapWithNonAliasingKeys(
        id: UAllocatedRefMapWithNonAliasingKeysId<MapType, ValueSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
        op: (UAllocatedRefMapWithNonAliasingKeys<MapType, ValueSort>, UBoolExpr, MutabilityOwnership) ->
        UAllocatedRefMapWithNonAliasingKeys<MapType, ValueSort>,
    ): URefMapMemoryRegion<MapType, ValueSort> {
        val keyRef = nonAliasingRef(id.keyAddress)
            ?: return updateAllocatedMapWithNonAliasingKeys(
                id, op(getAllocatedMapWithNonAliasingKeys(id), guard, ownership), ownership
            )
        val (collections, locations) = applyToPair(
            allocatedMapWithNonAliasingKeys, elementLocations.allocatedMapNonAliasingKeys,
            id, concreteRef(id.mapAddress), keyRef, guard, ownership, { id.emptyRegion() }, op
        )
        return copy(
            allocatedMapWithNonAliasingKeys = collections,
            elementLocations = elementLocations.copy(allocatedMapNonAliasingKeys = locations)
        )
    }

    private fun applyToNonAliasingMapWithAllocatedKeys(
        id: UNonAliasingRefMapWithAllocatedKeysId<MapType, ValueSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
        op: (UNonAliasingRefMapWithAllocatedKeys<MapType, ValueSort>, UBoolExpr, MutabilityOwnership) ->
        UNonAliasingRefMapWithAllocatedKeys<MapType, ValueSort>,
    ): URefMapMemoryRegion<MapType, ValueSort> {
        val (collections, locations) = applyToPair(
            nonAliasingMapWithAllocatedKeys, elementLocations.nonAliasingMapAllocatedKeys,
            id, id.mapRef, concreteRef(id.keyAddress), guard, ownership, { id.emptyRegion() }, op
        )
        return copy(
            nonAliasingMapWithAllocatedKeys = collections,
            elementLocations = elementLocations.copy(nonAliasingMapAllocatedKeys = locations)
        )
    }

    private fun applyToNonAliasingMapWithNonAliasingKeys(
        id: UNonAliasingRefMapWithNonAliasingKeysId<MapType, ValueSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
        op: (UNonAliasingRefMapWithNonAliasingKeys<MapType, ValueSort>, UBoolExpr, MutabilityOwnership) ->
        UNonAliasingRefMapWithNonAliasingKeys<MapType, ValueSort>,
    ): URefMapMemoryRegion<MapType, ValueSort> {
        val mapRef = nonAliasingRef(id.mapAddress)
        val keyRef = nonAliasingRef(id.keyAddress)
        if (mapRef == null || keyRef == null) {
            return updateNonAliasingMapWithNonAliasingKeys(
                id,
                op(getNonAliasingMapWithNonAliasingKeys(id), guard, ownership),
                ownership
            )
        }
        val (collections, locations) = applyToPair(
            nonAliasingMapWithNonAliasingKeys, elementLocations.nonAliasingMapNonAliasingKeys,
            id, mapRef, keyRef, guard, ownership, { id.emptyRegion() }, op
        )
        return copy(
            nonAliasingMapWithNonAliasingKeys = collections,
            elementLocations = elementLocations.copy(nonAliasingMapNonAliasingKeys = locations)
        )
    }

    private fun updateAllocatedMapWithAllocatedKeys(
        updated: UPersistentHashMap<UAllocatedRefMapWithAllocatedKeysId, UExpr<ValueSort>>,
    ) = copy(allocatedMapWithAllocatedKeys = updated)

    private fun inputMapWithAllocatedKeyId(keyAddress: UConcreteHeapAddress) =
        UInputRefMapWithAllocatedKeysId(valueSort, mapType, keyAddress)

    private fun getInputMapWithAllocatedKeys(
        id: UInputRefMapWithAllocatedKeysId<MapType, ValueSort>,
    ): UInputRefMapWithAllocatedKeys<MapType, ValueSort> {
        val (updatedMap, collection) = inputMapWithAllocatedKeys.getOrPut(id, defaultOwnership) { id.emptyRegion() }
        inputMapWithAllocatedKeys = updatedMap
        return collection
    }

    private fun updateInputMapWithAllocatedKeys(
        id: UInputRefMapWithAllocatedKeysId<MapType, ValueSort>,
        updatedMap: UInputRefMapWithAllocatedKeys<MapType, ValueSort>,
        ownership: MutabilityOwnership,
    ) = copy(inputMapWithAllocatedKeys = inputMapWithAllocatedKeys.put(id, updatedMap, ownership))

    private fun nonAliasingMapWithAllocatedKeyId(mapRef: UHeapRef, keyAddress: UConcreteHeapAddress) =
        UNonAliasingRefMapWithAllocatedKeysId(valueSort, mapType, mapRef, keyAddress)

    private fun getNonAliasingMapWithAllocatedKeys(
        id: UNonAliasingRefMapWithAllocatedKeysId<MapType, ValueSort>,
    ): UNonAliasingRefMapWithAllocatedKeys<MapType, ValueSort> {
        val (collections, locations, collection) = materializePair(
            nonAliasingMapWithAllocatedKeys,
            elementLocations.nonAliasingMapAllocatedKeys,
            id,
            id.mapRef,
            concreteRef(id.keyAddress)
        ) { id.emptyRegion() }
        nonAliasingMapWithAllocatedKeys = collections
        elementLocations = elementLocations.copy(nonAliasingMapAllocatedKeys = locations)
        return collection
    }

    private fun updateNonAliasingMapWithAllocatedKeys(
        id: UNonAliasingRefMapWithAllocatedKeysId<MapType, ValueSort>,
        updatedMap: UNonAliasingRefMapWithAllocatedKeys<MapType, ValueSort>,
        ownership: MutabilityOwnership,
    ) = copy(nonAliasingMapWithAllocatedKeys = nonAliasingMapWithAllocatedKeys.put(id, updatedMap, ownership))

    private fun allocatedMapWithInputKeyId(mapAddress: UConcreteHeapAddress) =
        UAllocatedRefMapWithInputKeysId(valueSort, mapType, mapAddress)

    private fun getAllocatedMapWithInputKeys(
        id: UAllocatedRefMapWithInputKeysId<MapType, ValueSort>,
    ): UAllocatedRefMapWithInputKeys<MapType, ValueSort> {
        val (updatedMap, collection) = allocatedMapWithInputKeys.getOrPut(id, defaultOwnership) { id.emptyRegion() }
        allocatedMapWithInputKeys = updatedMap
        return collection
    }

    private fun updateAllocatedMapWithInputKeys(
        id: UAllocatedRefMapWithInputKeysId<MapType, ValueSort>,
        updatedMap: UAllocatedRefMapWithInputKeys<MapType, ValueSort>,
        ownership: MutabilityOwnership,
    ) = copy(allocatedMapWithInputKeys = allocatedMapWithInputKeys.put(id, updatedMap, ownership))

    private fun allocatedMapWithNonAliasingKeyId(mapAddress: UConcreteHeapAddress, keyAddress: UNonAliasingHeapAddress) =
        UAllocatedRefMapWithNonAliasingKeysId(valueSort, mapType, mapAddress, keyAddress)

    private fun getAllocatedMapWithNonAliasingKeys(
        id: UAllocatedRefMapWithNonAliasingKeysId<MapType, ValueSort>,
    ): UAllocatedRefMapWithNonAliasingKeys<MapType, ValueSort> {
        val keyRef = nonAliasingRef(id.keyAddress)
            ?: return allocatedMapWithNonAliasingKeys.getOrPut(id, defaultOwnership) { id.emptyRegion() }
                .let { (updated, collection) ->
                    allocatedMapWithNonAliasingKeys = updated
                    collection
                }
        val (collections, locations, collection) = materializePair(
            allocatedMapWithNonAliasingKeys,
            elementLocations.allocatedMapNonAliasingKeys,
            id,
            concreteRef(id.mapAddress),
            keyRef
        ) { id.emptyRegion() }
        allocatedMapWithNonAliasingKeys = collections
        elementLocations = elementLocations.copy(allocatedMapNonAliasingKeys = locations)
        return collection
    }

    private fun updateAllocatedMapWithNonAliasingKeys(
        id: UAllocatedRefMapWithNonAliasingKeysId<MapType, ValueSort>,
        updatedMap: UAllocatedRefMapWithNonAliasingKeys<MapType, ValueSort>,
        ownership: MutabilityOwnership,
    ) = copy(allocatedMapWithNonAliasingKeys = allocatedMapWithNonAliasingKeys.put(id, updatedMap, ownership))


    private fun getInputMapWithInputKeys(): UInputRefMap<MapType, ValueSort> {
        if (inputMapWithInputKeys == null) {
            inputMapWithInputKeys = UInputRefMapWithInputKeysId(
                valueSort, mapType
            ).emptyRegion()
        }
        return inputMapWithInputKeys!!
    }

    private fun updateInputMapWithInputKeys(updatedMap: UInputRefMap<MapType, ValueSort>) =
        copy(inputMapWithInputKeys = updatedMap)

    private fun nonAliasingMapWithNonAliasingKeyId(mapAddress: UNonAliasingHeapAddress, keyAddress: UNonAliasingHeapAddress) =
        UNonAliasingRefMapWithNonAliasingKeysId(valueSort, mapType, mapAddress, keyAddress)

    private fun getNonAliasingMapWithNonAliasingKeys(
        id: UNonAliasingRefMapWithNonAliasingKeysId<MapType, ValueSort>,
    ): UNonAliasingRefMapWithNonAliasingKeys<MapType, ValueSort> {
        val mapRef = nonAliasingRef(id.mapAddress)
        val keyRef = nonAliasingRef(id.keyAddress)
        if (mapRef == null || keyRef == null) {
            val (updated, collection) = nonAliasingMapWithNonAliasingKeys.getOrPut(
                id,
                defaultOwnership
            ) { id.emptyRegion() }
            nonAliasingMapWithNonAliasingKeys = updated
            return collection
        }
        val (collections, locations, collection) = materializePair(
            nonAliasingMapWithNonAliasingKeys,
            elementLocations.nonAliasingMapNonAliasingKeys,
            id,
            mapRef,
            keyRef
        ) { id.emptyRegion() }
        nonAliasingMapWithNonAliasingKeys = collections
        elementLocations = elementLocations.copy(nonAliasingMapNonAliasingKeys = locations)
        return collection
    }

    private fun updateNonAliasingMapWithNonAliasingKeys(
        id: UNonAliasingRefMapWithNonAliasingKeysId<MapType, ValueSort>,
        updatedMap: UNonAliasingRefMapWithNonAliasingKeys<MapType, ValueSort>,
        ownership: MutabilityOwnership,
    ) = copy(nonAliasingMapWithNonAliasingKeys = nonAliasingMapWithNonAliasingKeys.put(id, updatedMap, ownership))

    override fun read(key: URefMapEntryLValue<MapType, ValueSort>): UExpr<ValueSort> =
        key.mapRef.mapWithStaticAsSymbolic(
            concreteMapper = { concreteRef ->
                key.mapKey.mapWithStaticAsSymbolic(
                    concreteMapper = { concreteKey ->
                        val id = UAllocatedRefMapWithAllocatedKeysId(concreteRef.address, concreteKey.address)
                        allocatedMapWithAllocatedKeys[id] ?: valueSort.sampleUValue()
                    },
                    symbolicMapper = { symbolicKey ->
                        val id = allocatedMapWithInputKeyId(concreteRef.address)
                        getAllocatedMapWithInputKeys(id).read(symbolicKey)
                    },
                    nonAliasingMapper = { nonAliasingKey ->
                        val id = allocatedMapWithNonAliasingKeyId(concreteRef.address, getId(nonAliasingKey))
                        getAllocatedMapWithNonAliasingKeys(id).read(nonAliasingKey)
                    },
                    ignoreNullRefs = false
                )
            },
            symbolicMapper = { symbolicRef ->
                key.mapKey.mapWithStaticAsSymbolic(
                    concreteMapper = { concreteKey ->
                        val id = inputMapWithAllocatedKeyId(concreteKey.address)
                        getInputMapWithAllocatedKeys(id).read(symbolicRef)
                    },
                    symbolicMapper = { symbolicKey ->
                        getInputMapWithInputKeys().read(symbolicRef to symbolicKey)
                    },
                    nonAliasingMapper = { nonAliasingKey ->
                        throw IllegalStateException("NA and input together")
                    },
                    ignoreNullRefs = false
                )
            },
            nonAliasingMapper = { nonAliasingRef ->
                key.mapKey.mapWithStaticAsSymbolic(
                    concreteMapper = { concreteKey ->
                        val id = nonAliasingMapWithAllocatedKeyId(nonAliasingRef, concreteKey.address)
                        getNonAliasingMapWithAllocatedKeys(id).read(nonAliasingRef)
                    },
                    symbolicMapper = { symbolicKey ->
                        throw IllegalStateException("NA and input together")
                    },
                    nonAliasingMapper = { nonAliasingKey ->
                        val id = nonAliasingMapWithNonAliasingKeyId(getId(nonAliasingRef), getId(nonAliasingKey))
                        getNonAliasingMapWithNonAliasingKeys(id).read(nonAliasingRef to nonAliasingKey)
                    },
                    ignoreNullRefs = false
                )
            }
        )

    override fun write(
        key: URefMapEntryLValue<MapType, ValueSort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
    ) = foldHeapRefWithStaticAsSymbolic(
        ref = key.mapRef,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { mapRegion, (concreteMapRef, mapGuard) ->
            foldHeapRefWithStaticAsSymbolic(
                ref = key.mapKey,
                initial = mapRegion,
                initialGuard = mapGuard,
                ignoreNullRefs = false,
                blockOnConcrete = { region, (concreteKeyRef, guard) ->
                    val id = UAllocatedRefMapWithAllocatedKeysId(concreteMapRef.address, concreteKeyRef.address)
                    val newMap = region.allocatedMapWithAllocatedKeys.guardedWrite(id, value, guard, ownership) {
                        valueSort.sampleUValue()
                    }
                    region.updateAllocatedMapWithAllocatedKeys(newMap)
                },
                blockOnSymbolic = { region, (symbolicKeyRef, guard) ->
                    val id = allocatedMapWithInputKeyId(concreteMapRef.address)
                    val newMap = region.getAllocatedMapWithInputKeys(id)
                        .write(symbolicKeyRef, value, guard, ownership)
                    region.updateAllocatedMapWithInputKeys(id, newMap, ownership)
                },
                blockOnNonAliasing = { region, (nonAliasingKeyRef, guard) ->
                    val id = allocatedMapWithNonAliasingKeyId(concreteMapRef.address, getId(nonAliasingKeyRef))
                    region.applyToAllocatedMapWithNonAliasingKeys(id, guard, ownership) { map, opGuard, opOwnership ->
                        map.write(nonAliasingKeyRef, value, opGuard, opOwnership)
                    }
                }
            )
        },
        blockOnSymbolic = { mapRegion, (symbolicMapRef, mapGuard) ->
            foldHeapRefWithStaticAsSymbolic(
                ref = key.mapKey,
                initial = mapRegion,
                initialGuard = mapGuard,
                ignoreNullRefs = false,
                blockOnConcrete = { region, (concreteKeyRef, guard) ->
                    val id = inputMapWithAllocatedKeyId(concreteKeyRef.address)
                    val newMap = region.getInputMapWithAllocatedKeys(id)
                        .write(symbolicMapRef, value, guard, ownership)
                    region.updateInputMapWithAllocatedKeys(id, newMap, ownership)
                },
                blockOnSymbolic = { region, (symbolicKeyRef, guard) ->
                    val newMap = region.getInputMapWithInputKeys()
                        .write(symbolicMapRef to symbolicKeyRef, value, guard, ownership)
                    region.updateInputMapWithInputKeys(newMap)
                },
                blockOnNonAliasing = { region, (symbolicKeyRef, guard) ->
                    throw IllegalStateException("NA and input together")
                }
            )
        },
        blockOnNonAliasing = { mapRegion, (nonAliasingMapRef, mapGuard) ->
            foldHeapRefWithStaticAsSymbolic(
                ref = key.mapKey,
                initial = mapRegion,
                initialGuard = mapGuard,
                ignoreNullRefs = false,
                blockOnConcrete = { region, (concreteKeyRef, guard) ->
                    val id = nonAliasingMapWithAllocatedKeyId(nonAliasingMapRef, concreteKeyRef.address)
                    region.applyToNonAliasingMapWithAllocatedKeys(id, guard, ownership) { map, opGuard, opOwnership ->
                        map.write(nonAliasingMapRef, value, opGuard, opOwnership)
                    }
                },
                blockOnSymbolic = { region, (symbolicKeyRef, guard) ->
                    throw IllegalStateException("NA and input together")
                },
                blockOnNonAliasing = { region, (nonAliasingKeyRef, guard) ->
                    val id = nonAliasingMapWithNonAliasingKeyId(getId(nonAliasingMapRef), getId(nonAliasingKeyRef))
                    region.applyToNonAliasingMapWithNonAliasingKeys(id, guard, ownership) { map, opGuard, opOwnership ->
                        map.write(nonAliasingMapRef to nonAliasingKeyRef, value, opGuard, opOwnership)
                    }
                }
            )
        }
    )

    /**
     * Merge maps with ref keys.
     *
     * Note 1: there are no concrete keys in input maps.
     * Therefore, we can enumerate all possible concrete keys.
     *
     * Note 2: concrete keys can't intersect with symbolic ones.
     *
     * Merge:
     * 1. Merge src symbolic keys into dst symbolic keys using `merge update node`.
     * 2. Merge src concrete keys into dst concrete keys.
     *  2.1 enumerate all concrete keys using map writes.
     *  2.2 write keys into dst with `map.write` operation.
     * */
    override fun merge(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        mapType: MapType,
        sort: ValueSort,
        keySet: URefSetRegion<MapType>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ) = foldHeapRef2(
        ref0 = srcRef,
        ref1 = dstRef,
        initial = this,
        initialGuard = operationGuard,
        blockOnConcrete0Concrete1 = { region, srcConcrete, dstConcrete, guard ->
            val initialAllocatedMapState = region.allocatedMapWithAllocatedKeys
            val updatedAllocatedMap = region.mergeAllocatedMapAllocatedKeys(
                initial = initialAllocatedMapState,
                srcMapRef = srcConcrete,
                guard = guard,
                keySet = keySet,
                read = { initialAllocatedMapState[it] ?: valueSort.sampleUValue() },
                mkDstKeyId = { UAllocatedRefMapWithAllocatedKeysId(dstConcrete.address, it) },
                write = { result, dstKeyId, value, g ->
                    result.guardedWrite(dstKeyId, value, g, ownership) { valueSort.sampleUValue() }
                }
            )
            val updatedRegion = region.updateAllocatedMapWithAllocatedKeys(updatedAllocatedMap)

            val srcKeys = keySet.allocatedSetWithInputElements(srcConcrete.address)
            val srcInputKeysId = updatedRegion.allocatedMapWithInputKeyId(srcConcrete.address)
            val srcInputKeysCollection = updatedRegion.getAllocatedMapWithInputKeys(srcInputKeysId)

            val dstInputKeysId = updatedRegion.allocatedMapWithInputKeyId(dstConcrete.address)
            val dstInputKeysCollection = updatedRegion.getAllocatedMapWithInputKeys(dstInputKeysId)

            val adapter = UAllocatedToAllocatedSymbolicRefMapMergeAdapter(srcKeys)
            val updatedDstCollection = dstInputKeysCollection.copyRange(srcInputKeysCollection, adapter, guard)
            val updatedRegion2 = updatedRegion.updateAllocatedMapWithInputKeys(
                dstInputKeysId,
                updatedDstCollection,
                ownership
            )

            updatedRegion2.mergeAllocatedMapNonAliasingKeys(
                initial = updatedRegion2,
                srcMapRef = srcConcrete,
                guard = guard,
                getSrcKeys = { keyRef ->
                    keySet.allocatedSetWithNonAliasingElements(getId(srcConcrete), getId(keyRef))
                },
                read = { keyId ->
                    updatedRegion2.getAllocatedMapWithNonAliasingKeys(keyId)
                },
                mkDstKeyId = { allocatedMapWithNonAliasingKeyId(dstConcrete.address, it) },
                write = { result, dstKeyId, srcNonAliasingKeysCollection, srcKeys2, g ->
                    val adapter2 = UAllocatedToAllocatedNARefMapMergeAdapter(srcKeys2)
                    result.applyToAllocatedMapWithNonAliasingKeys(dstKeyId, guard, ownership) { dst, opGuard, _ ->
                        dst.copyRange(srcNonAliasingKeysCollection, adapter2, opGuard)
                    }
                }
            )
        },
        blockOnConcrete0Symbolic1 = { region, srcConcrete, dstSymbolic, guard ->
            val initialAllocatedMapState = region.allocatedMapWithAllocatedKeys
            val updatedRegion = region.mergeAllocatedMapAllocatedKeys(
                initial = region,
                srcMapRef = srcConcrete,
                guard = guard,
                keySet = keySet,
                read = { initialAllocatedMapState[it] ?: valueSort.sampleUValue() },
                mkDstKeyId = { inputMapWithAllocatedKeyId(it) },
                write = { result, dstKeyId, value, g ->
                    val newMap = result.getInputMapWithAllocatedKeys(dstKeyId)
                        .write(dstSymbolic, value, g, ownership)
                    result.updateInputMapWithAllocatedKeys(dstKeyId, newMap, ownership)
                }
            )

            val srcKeys = keySet.allocatedSetWithInputElements(srcConcrete.address)
            val srcInputKeysId = updatedRegion.allocatedMapWithInputKeyId(srcConcrete.address)
            val srcInputKeysCollection = updatedRegion.getAllocatedMapWithInputKeys(srcInputKeysId)

            val dstInputKeysCollection = updatedRegion.getInputMapWithInputKeys()

            val adapter = UAllocatedToInputSymbolicRefMapMergeAdapter(dstSymbolic, srcKeys)
            val updatedDstCollection = dstInputKeysCollection.copyRange(srcInputKeysCollection, adapter, guard)
            updatedRegion.updateInputMapWithInputKeys(updatedDstCollection)
        },
        blockOnConcrete0NonAliasing1 = { region, srcConcrete, dstNonAliasing, guard ->
            val initialAllocatedMapState = region.allocatedMapWithAllocatedKeys
            val updatedRegion = region.mergeAllocatedMapAllocatedKeys(
                initial = region,
                srcMapRef = srcConcrete,
                guard = guard,
                keySet = keySet,
                read = { initialAllocatedMapState[it] ?: valueSort.sampleUValue() },
                mkDstKeyId = { nonAliasingMapWithAllocatedKeyId(dstNonAliasing, it) },
                write = { result, dstKeyId, value, g ->
                    result.applyToNonAliasingMapWithAllocatedKeys(dstKeyId, g, ownership) { map, opGuard, opOwnership ->
                        map.write(dstNonAliasing, value, opGuard, opOwnership)
                    }
                }
            )
            updatedRegion.mergeAllocatedMapNonAliasingKeys(
                initial = updatedRegion,
                srcMapRef = srcConcrete,
                guard = guard,
                getSrcKeys = { keyRef ->
                    keySet.allocatedSetWithNonAliasingElements(getId(srcConcrete), getId(keyRef))
                },
                read = { keyId ->
                    updatedRegion.getAllocatedMapWithNonAliasingKeys(keyId)
                },
                mkDstKeyId = { nonAliasingMapWithNonAliasingKeyId(getId(dstNonAliasing), it) },
                write = { result, dstKeyId, srcNonAliasingKeysCollection, srcKeys, g ->
                    val adapter = UAllocatedToNonAliasingSymbolicRefMapMergeAdapter(dstNonAliasing, srcKeys)
                    result.applyToNonAliasingMapWithNonAliasingKeys(dstKeyId, guard, ownership) { dst, opGuard, _ ->
                        dst.copyRange(srcNonAliasingKeysCollection, adapter, opGuard)
                    }
                }
            )
        },
        blockOnSymbolic0Concrete1 = { region, srcSymbolic, dstConcrete, guard ->
            val updatedAllocatedMap = region.mergeInputMapAllocatedKeys(
                initial = region.allocatedMapWithAllocatedKeys,
                srcMapRef = srcSymbolic,
                guard = guard,
                keySet = keySet,
                read = { region.getInputMapWithAllocatedKeys(it).read(srcSymbolic) },
                mkDstKeyId = { UAllocatedRefMapWithAllocatedKeysId(dstConcrete.address, it) },
                write = { result, dstKeyId, value, g ->
                    result.guardedWrite(dstKeyId, value, g, ownership) { sort.sampleUValue() }
                }
            )
            val updatedRegion = region.updateAllocatedMapWithAllocatedKeys(updatedAllocatedMap)

            val srcKeys = keySet.inputSetWithInputElements()
            val srcInputKeysCollection = updatedRegion.getInputMapWithInputKeys()

            val dstInputKeysId = updatedRegion.allocatedMapWithInputKeyId(dstConcrete.address)
            val dstInputKeysCollection = updatedRegion.getAllocatedMapWithInputKeys(dstInputKeysId)

            val adapter = UInputToAllocatedSymbolicRefMapMergeAdapter(srcSymbolic, srcKeys)
            val updatedDstCollection = dstInputKeysCollection.copyRange(srcInputKeysCollection, adapter, guard)
            updatedRegion.updateAllocatedMapWithInputKeys(dstInputKeysId, updatedDstCollection, ownership)
        },
        blockOnNonAliasing0Concrete1 = { region, srcNonAliasing, dstConcrete, guard ->
            val updatedAllocatedMap = region.mergeNonAliasingMapAllocatedKeys(
                initial = region.allocatedMapWithAllocatedKeys,
                srcMapRef = srcNonAliasing,
                guard = guard,
                keySet = keySet,
                read = { region.getNonAliasingMapWithAllocatedKeys(it).read(srcNonAliasing) },
                mkDstKeyId = { UAllocatedRefMapWithAllocatedKeysId(dstConcrete.address, it) },
                write = { result, dstKeyId, value, g ->
                    result.guardedWrite(dstKeyId, value, g, ownership) { sort.sampleUValue() }
                }
            )
            val updatedRegion = region.updateAllocatedMapWithAllocatedKeys(updatedAllocatedMap)
            updatedRegion.mergeNonAliasingMapNonAliasingKeys(
                initial = updatedRegion,
                srcMapRef = srcNonAliasing,
                guard = guard,
                getSrcKeys = { keyRef ->
                    keySet.nonAliasingSetWithNonAliasingElements(getId(srcNonAliasing), getId(keyRef))
                },
                read = { keyId ->
                    updatedRegion.getNonAliasingMapWithNonAliasingKeys(keyId)
                },
                mkDstKeyId = { allocatedMapWithNonAliasingKeyId(dstConcrete.address, it) },
                write = { result, dstKeyId, srcNonAliasingKeysCollection, srcKeys, g ->
                    val adapter = UNonAliasingToAllocatedSymbolicRefMapMergeAdapter(srcNonAliasing, srcKeys)
                    result.applyToAllocatedMapWithNonAliasingKeys(dstKeyId, guard, ownership) { dst, opGuard, _ ->
                        dst.copyRange(srcNonAliasingKeysCollection, adapter, opGuard)
                    }
                }
            )
        },
        blockOnNonAliasing0NonAliasing1 = { region, srcNonAliasing, dstNonAliasing, guard ->
            val updatedRegion = region.mergeNonAliasingMapAllocatedKeys(
                initial = region,
                srcMapRef = srcNonAliasing,
                guard = guard,
                keySet = keySet,
                read = { region.getNonAliasingMapWithAllocatedKeys(it).read(srcNonAliasing) },
                mkDstKeyId = { nonAliasingMapWithAllocatedKeyId(dstNonAliasing, it) },
                write = { result, dstKeyId, value, g ->
                    result.applyToNonAliasingMapWithAllocatedKeys(dstKeyId, g, ownership) { map, opGuard, opOwnership ->
                        map.write(dstNonAliasing, value, opGuard, opOwnership)
                    }
                }
            )
            updatedRegion.mergeNonAliasingMapNonAliasingKeys(
                initial = updatedRegion,
                srcMapRef = srcNonAliasing,
                guard = guard,
                getSrcKeys = { keyRef ->
                    keySet.nonAliasingSetWithNonAliasingElements(getId(srcNonAliasing), getId(keyRef))
                },
                read = { keyId ->
                    updatedRegion.getNonAliasingMapWithNonAliasingKeys(keyId)
                },
                mkDstKeyId = { nonAliasingMapWithNonAliasingKeyId(getId(dstNonAliasing), it) },
                write = { result, dstKeyId, srcNonAliasingKeysCollection, srcKeys, g ->
                    val adapter =
                        UNonAliasingToNonAliasingSymbolicRefMapMergeAdapter(srcNonAliasing, dstNonAliasing, srcKeys)
                    result.applyToNonAliasingMapWithNonAliasingKeys(dstKeyId, guard, ownership) { dst, opGuard, _ ->
                        dst.copyRange(srcNonAliasingKeysCollection, adapter, opGuard)
                    }
                }
            )
        },
        blockOnSymbolic0Symbolic1 = { region, srcSymbolic, dstSymbolic, guard ->
            val updatedRegion = region.mergeInputMapAllocatedKeys(
                initial = region,
                srcMapRef = srcSymbolic,
                guard = guard,
                keySet = keySet,
                read = { region.getInputMapWithAllocatedKeys(it).read(srcSymbolic) },
                mkDstKeyId = { inputMapWithAllocatedKeyId(it) },
                write = { result, dstKeyId, value, g ->
                    val newMap = result.getInputMapWithAllocatedKeys(dstKeyId)
                        .write(dstSymbolic, value, g, ownership)
                    result.updateInputMapWithAllocatedKeys(dstKeyId, newMap, ownership)
                }
            )
            val srcKeys = keySet.inputSetWithInputElements()
            val srcInputKeysCollection = updatedRegion.getInputMapWithInputKeys()

            val dstInputKeysCollection = updatedRegion.getInputMapWithInputKeys()

            val adapter = UInputToInputSymbolicRefMapMergeAdapter(srcSymbolic, dstSymbolic, srcKeys)
            val updatedDstCollection = dstInputKeysCollection.copyRange(srcInputKeysCollection, adapter, guard)
            updatedRegion.updateInputMapWithInputKeys(updatedDstCollection)
        },
    )

    private inline fun <R, DstKeyId> mergeInputMapAllocatedKeys(
        initial: R,
        srcMapRef: UHeapRef,
        guard: UBoolExpr,
        keySet: URefSetRegion<MapType>,
        read: (UInputRefMapWithAllocatedKeysId<MapType, ValueSort>) -> UExpr<ValueSort>,
        mkDstKeyId: (UConcreteHeapAddress) -> DstKeyId,
        write: (R, DstKeyId, UExpr<ValueSort>, UBoolExpr) -> R,
    ) = mergeAllocatedKeys(
        initial,
        inputMapWithAllocatedKeys.keys.toList(),
        guard,
        keySet,
        srcMapRef,
        { it.keyAddress },
        read,
        mkDstKeyId,
        write
    )

    private inline fun <R, DstKeyId> mergeNonAliasingMapAllocatedKeys(
        initial: R,
        srcMapRef: UHeapRef,
        guard: UBoolExpr,
        keySet: URefSetRegion<MapType>,
        read: (UNonAliasingRefMapWithAllocatedKeysId<MapType, ValueSort>) -> UExpr<ValueSort>,
        mkDstKeyId: (UNonAliasingHeapAddress) -> DstKeyId,
        write: (R, DstKeyId, UExpr<ValueSort>, UBoolExpr) -> R,
    ) = mergeAllocatedKeys(
        initial,
        nonAliasingMapWithAllocatedKeys.keys.toList(),
        guard,
        keySet,
        srcMapRef,
        { it.keyAddress },
        read,
        mkDstKeyId,
        write
    )

    private inline fun <R, DstKeyId> mergeNonAliasingMapNonAliasingKeys(
        initial: R,
        srcMapRef: UHeapRef,
        guard: UBoolExpr,
        getSrcKeys: (UHeapRef) -> UNonAliasingRefSetWithNonAliasingElements<MapType>,
        read: (
            UNonAliasingRefMapWithNonAliasingKeysId<MapType, ValueSort>,
        ) -> UNonAliasingRefMapWithNonAliasingKeys<MapType, ValueSort>,
        mkDstKeyId: (UNonAliasingHeapAddress) -> DstKeyId,
        write: (
            R,
            DstKeyId,
            UNonAliasingRefMapWithNonAliasingKeys<MapType, ValueSort>,
            UNonAliasingRefSetWithNonAliasingElements<MapType>,
            UBoolExpr,
        ) -> R,
    ) = mergeNonAliasingKeys(
        initial,
        nonAliasingMapWithNonAliasingKeys.keys.toList(),
        guard,
        { it.keyAddress },
        getSrcKeys,
        read,
        mkDstKeyId,
        write
    )

    private inline fun <R, DstKeyId> mergeAllocatedMapAllocatedKeys(
        initial: R,
        srcMapRef: UConcreteHeapRef,
        guard: UBoolExpr,
        keySet: URefSetRegion<MapType>,
        read: (UAllocatedRefMapWithAllocatedKeysId) -> UExpr<ValueSort>,
        mkDstKeyId: (UConcreteHeapAddress) -> DstKeyId,
        write: (R, DstKeyId, UExpr<ValueSort>, UBoolExpr) -> R,
    ) = mergeAllocatedKeys(
        initial,
        allocatedMapWithAllocatedKeys.keys.filterTo(mutableListOf()) { it.mapAddress == srcMapRef.address },
        guard,
        keySet,
        srcMapRef,
        { it.keyAddress },
        read,
        mkDstKeyId,
        write
    )

    private inline fun <R, DstKeyId> mergeAllocatedMapNonAliasingKeys(
        initial: R,
        srcMapRef: UConcreteHeapRef,
        guard: UBoolExpr,
        getSrcKeys: (UHeapRef) -> UAllocatedRefSetWithNonAliasingElements<MapType>,
        read: (
            UAllocatedRefMapWithNonAliasingKeysId<MapType, ValueSort>,
        ) -> UAllocatedRefMapWithNonAliasingKeys<MapType, ValueSort>,
        mkDstKeyId: (UNonAliasingHeapAddress) -> DstKeyId,
        write: (
            R,
            DstKeyId,
            UAllocatedRefMapWithNonAliasingKeys<MapType, ValueSort>,
            UAllocatedRefSetWithNonAliasingElements<MapType>,
            UBoolExpr,
        ) -> R,
    ) = mergeNonAliasingKeys(
        initial,
        allocatedMapWithNonAliasingKeys.keys.filterTo(mutableListOf()) { it.mapAddress == srcMapRef.address },
        guard,
        { it.keyAddress },
        getSrcKeys,
        read,
        mkDstKeyId,
        write
    )

    private inline fun <R, SrcKeyId, DstKeyId> mergeAllocatedKeys(
        initial: R,
        keys: List<SrcKeyId>,
        guard: UBoolExpr,
        keySet: URefSetRegion<MapType>,
        srcMapRef: UHeapRef,
        srcKeyConcreteAddress: (SrcKeyId) -> UConcreteHeapAddress,
        read: (SrcKeyId) -> UExpr<ValueSort>,
        mkDstKeyId: (UConcreteHeapAddress) -> DstKeyId,
        write: (R, DstKeyId, UExpr<ValueSort>, UBoolExpr) -> R,
    ): R = keys.fold(initial) { result, srcKeyId ->
        val srcKeyAddress = srcKeyConcreteAddress(srcKeyId)
        val srcValue = read(srcKeyId)

        val keyRef = guard.uctx.mkConcreteHeapRef(srcKeyAddress)
        val srcContains = keySet.read(URefSetEntryLValue(srcMapRef, keyRef, mapType))
        val mergedGuard = guard.uctx.mkAnd(srcContains, guard)

        write(result, mkDstKeyId(srcKeyAddress), srcValue, mergedGuard)
    }

    private inline fun <R, SrcKeyId, DstKeyId, SrcCollection, Keys> mergeNonAliasingKeys(
        initial: R,
        keys: List<SrcKeyId>,
        guard: UBoolExpr,
        srcKeyConcreteAddress: (SrcKeyId) -> UConcreteHeapAddress,
        getSrcKeys: (UHeapRef) -> Keys,
        read: (SrcKeyId) -> SrcCollection,
        mkDstKeyId: (UConcreteHeapAddress) -> DstKeyId,
        write: (R, DstKeyId, SrcCollection, Keys, UBoolExpr) -> R,
    ): R = keys.fold(initial) { result, srcKeyId ->
        val srcKeyAddress = srcKeyConcreteAddress(srcKeyId)

        val keyRef = guard.uctx.mkConcreteHeapRef(srcKeyAddress)
        val srcKeys = getSrcKeys(keyRef)
        val srcCollection = read(srcKeyId)
        val x = write(result, mkDstKeyId(srcKeyAddress), srcCollection, srcKeys, guard)
        x
    }
}
