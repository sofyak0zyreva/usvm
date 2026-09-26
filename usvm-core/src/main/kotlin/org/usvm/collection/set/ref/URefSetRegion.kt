package org.usvm.collection.set.ref

import org.usvm.NAHeapRefMap
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapAddress
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.UNonAliasingHeapRef
import org.usvm.collection.field.getId
import org.usvm.collection.set.USymbolicSetEntries
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.collection.set.USymbolicSetElementsCollector
import org.usvm.collection.set.primitive.UNonAliasingSet
import org.usvm.collections.immutable.getOrPut
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.foldHeapRef2
import org.usvm.memory.foldHeapRefWithStaticAsSymbolic
import org.usvm.memory.guardedWrite
import org.usvm.memory.mapWithStaticAsSymbolic
import org.usvm.uctx

data class URefSetEntryLValue<SetType>(
    val setRef: UHeapRef,
    val setElement: UHeapRef,
    val setType: SetType
) : ULValue<URefSetEntryLValue<SetType>, UBoolSort> {
    override val sort: UBoolSort
        get() = setRef.uctx.boolSort

    override val memoryRegionId: UMemoryRegionId<URefSetEntryLValue<SetType>, UBoolSort>
        get() = URefSetRegionId(setType, sort)

    override val key: URefSetEntryLValue<SetType>
        get() = this
}

data class URefSetRegionId<SetType>(
    val setType: SetType,
    override val sort: UBoolSort
) : UMemoryRegionId<URefSetEntryLValue<SetType>, UBoolSort> {
    override fun emptyRegion(): UMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort> =
        URefSetMemoryRegion(setType, sort)
}

internal data class UAllocatedRefSetWithAllocatedElementId(
    val setAddress: UConcreteHeapAddress,
    val elementAddress: UConcreteHeapAddress
)

//internal data class UAllocatedRefSetWithNonAliasingElementId(
//    val setAddress: UConcreteHeapAddress,
//    val elementId: UNonAliasingHeapAddress
//)
//
//internal data class UNonAliasingRefSetWithAllocatedElementId(
//    val setId: UNonAliasingHeapAddress,
//    val elementAddress: UConcreteHeapAddress
//)
//
//internal data class UNonAliasingRefSetWithNonAliasingElementId(
//    val setId: UNonAliasingHeapAddress,
//    val elementId: UNonAliasingHeapAddress
//)

typealias UAllocatedRefSetWithInputElements<SetType> =
        USymbolicCollection<UAllocatedRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort>

typealias UInputRefSetWithAllocatedElements<SetType> =
        USymbolicCollection<UInputRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort>

typealias UInputRefSetWithInputElements<SetType> =
        USymbolicCollection<UInputRefSetWithInputElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>

//typealias UNonAliasingRefSetWithInputElements<SetType> =
//        USymbolicCollection<UNonAliasingRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort>

typealias UNonAliasingRefSetWithAllocatedElements<SetType> =
        USymbolicCollection<UNonAliasingRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort>
//
//typealias UInputRefSetWithNonAliasingElements<SetType> =
//        USymbolicCollection<UInputRefSetWithNonAliasingElementsId<SetType>, UHeapRef, UBoolSort>
////
typealias UAllocatedRefSetWithNonAliasingElements<SetType> =
        USymbolicCollection<UAllocatedRefSetWithNonAliasingElementsId<SetType>, UHeapRef, UBoolSort>

typealias UNonAliasingRefSetWithNonAliasingElements<SetType> =
        USymbolicCollection<UNonAliasingRefSetWithNonAliasingElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>

typealias URefSetEntries<SetType> = USymbolicSetEntries<URefSetEntryLValue<SetType>>

interface URefSetReadOnlyRegion<SetType> :
    UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort> {
    fun setEntries(ref: UHeapRef): URefSetEntries<SetType>
}

interface URefSetRegion<SetType> :
    URefSetReadOnlyRegion<SetType>,
    UMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort> {

    fun allocatedSetWithInputElements(setRef: UConcreteHeapAddress): UAllocatedRefSetWithInputElements<SetType>

    fun allocatedSetWithNonAliasingElements(setRef: UConcreteHeapAddress, setElement: UNonAliasingHeapAddress): UAllocatedRefSetWithNonAliasingElements<SetType>
    fun inputSetWithInputElements(): UInputRefSetWithInputElements<SetType>
    fun nonAliasingSetWithNonAliasingElements(setRef: UNonAliasingHeapAddress, setElement: UNonAliasingHeapAddress):  UNonAliasingRefSetWithNonAliasingElements<SetType>

    fun union(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): URefSetRegion<SetType>
}

internal class URefSetMemoryRegion<SetType>(
    private val setType: SetType,
    private val sort: UBoolSort,
    private var allocatedSetWithAllocatedElements: UPersistentHashMap<UAllocatedRefSetWithAllocatedElementId, UBoolExpr> = persistentHashMapOf(),
    private var allocatedSetWithInputElements: UPersistentHashMap<UAllocatedRefSetWithInputElementsId<SetType>, UAllocatedRefSetWithInputElements<SetType>> = persistentHashMapOf(),
    private var inputSetWithAllocatedElements: UPersistentHashMap<UInputRefSetWithAllocatedElementsId<SetType>, UInputRefSetWithAllocatedElements<SetType>> = persistentHashMapOf(),
    private var inputSetWithInputElements: UInputRefSetWithInputElements<SetType>? = null,
    private var allocatedSetWithNonAliasingElements: UPersistentHashMap<UAllocatedRefSetWithNonAliasingElementsId<SetType>, UAllocatedRefSetWithNonAliasingElements<SetType>> = persistentHashMapOf(),
    private var nonAliasingSetWithAllocatedElements: UPersistentHashMap<UNonAliasingRefSetWithAllocatedElementsId<SetType>, UNonAliasingRefSetWithAllocatedElements<SetType>> = persistentHashMapOf(),
    private var nonAliasingSetWithNonAliasingElements: UPersistentHashMap<UNonAliasingRefSetWithNonAliasingElementsId<SetType>, UNonAliasingRefSetWithNonAliasingElements<SetType>> = persistentHashMapOf(),
//    private var nonAliasingSetWithInputElements: UPersistentHashMap<UNonAliasingRefSetWithInputElementsId<SetType>, UNonAliasingRefSetWithInputElements<SetType>> = persistentHashMapOf(),
//    private var inputSetWithNonAliasingElements: UPersistentHashMap<UInputRefSetWithNonAliasingElementsId<SetType>, UInputRefSetWithNonAliasingElements<SetType>> = persistentHashMapOf(),
//    private var allocatedSetWithNonAliasingElements: UPersistentHashMap<UAllocatedRefSetWithNonAliasingElementsId<SetType>, UAllocatedRefSetWithNonAliasingElements<SetType>> = persistentHashMapOf(),
//    private var nonAliasingSetWithAllocatedElements: UPersistentHashMap<UNonAliasingRefSetWithAllocatedElementsId<SetType>, UNonAliasingRefSetWithAllocatedElements<SetType>> = persistentHashMapOf(),
//    private var nonAliasingSetWithNonAliasingElements: UPersistentHashMap<UNonAliasingRefSetWithNonAliasingElementsId<SetType>, UNonAliasingRefSetWithNonAliasingElements<SetType>> = persistentHashMapOf(),

    ) : URefSetRegion<SetType> {

    private val defaultOwnership = sort.uctx.defaultOwnership

    private fun updateAllocatedSetWithAllocatedElements(
        updated: UPersistentHashMap<UAllocatedRefSetWithAllocatedElementId, UBoolExpr>
    ) = URefSetMemoryRegion(
        setType, sort,
        updated,
        allocatedSetWithInputElements,
        inputSetWithAllocatedElements,
        inputSetWithInputElements,
        allocatedSetWithNonAliasingElements,
        nonAliasingSetWithAllocatedElements,
        nonAliasingSetWithNonAliasingElements,
//        nonAliasingSetWithInputElements,
//        inputSetWithNonAliasingElements
    )

    private fun allocatedSetWithNonAliasingElementsId(setAddress: UConcreteHeapAddress, elementId: UNonAliasingHeapAddress) =
        UAllocatedRefSetWithNonAliasingElementsId(setAddress, elementId, setType, sort)

    private fun getAllocatedSetWithNonAliasingElements(
        id: UAllocatedRefSetWithNonAliasingElementsId<SetType>,
    ): UAllocatedRefSetWithNonAliasingElements<SetType> {
        val (updatedSet, collection) = allocatedSetWithNonAliasingElements.getOrPut(id, defaultOwnership) { id.emptyRegion() }
        allocatedSetWithNonAliasingElements = updatedSet
        return collection
    }

    override fun allocatedSetWithNonAliasingElements(setRef: UConcreteHeapAddress, setElement: UNonAliasingHeapAddress) =
        getAllocatedSetWithNonAliasingElements(allocatedSetWithNonAliasingElementsId(setRef, setElement))

    private fun updateAllocatedSetWithNonAliasingElements(
        id: UAllocatedRefSetWithNonAliasingElementsId<SetType>,
        updatedSet: UAllocatedRefSetWithNonAliasingElements<SetType>,
        ownership: MutabilityOwnership,
    ) = URefSetMemoryRegion(
        setType, sort,
        allocatedSetWithAllocatedElements,
        allocatedSetWithInputElements,
        inputSetWithAllocatedElements,
        inputSetWithInputElements,
        allocatedSetWithNonAliasingElements.put(id, updatedSet, ownership),
        nonAliasingSetWithAllocatedElements,
        nonAliasingSetWithNonAliasingElements,
//        nonAliasingSetWithInputElements,
//        inputSetWithNonAliasingElements
    )

    private fun allocatedSetWithInputElementsId(setAddress: UConcreteHeapAddress) =
        UAllocatedRefSetWithInputElementsId(setAddress, setType, sort)

    private fun getAllocatedSetWithInputElements(
        id: UAllocatedRefSetWithInputElementsId<SetType>,
    ): UAllocatedRefSetWithInputElements<SetType> {
        val (updatedSet, collection) = allocatedSetWithInputElements.getOrPut(id, defaultOwnership) { id.emptyRegion() }
        allocatedSetWithInputElements = updatedSet
        return collection
    }

    override fun allocatedSetWithInputElements(setRef: UConcreteHeapAddress) =
        getAllocatedSetWithInputElements(allocatedSetWithInputElementsId(setRef))



    private fun updateAllocatedSetWithInputElements(
        id: UAllocatedRefSetWithInputElementsId<SetType>,
        updatedSet: UAllocatedRefSetWithInputElements<SetType>,
        ownership: MutabilityOwnership,
    ) = URefSetMemoryRegion(
        setType, sort,
        allocatedSetWithAllocatedElements,
        allocatedSetWithInputElements.put(id, updatedSet, ownership),
        inputSetWithAllocatedElements,
        inputSetWithInputElements,
        allocatedSetWithNonAliasingElements,
        nonAliasingSetWithAllocatedElements,
        nonAliasingSetWithNonAliasingElements,
//        nonAliasingSetWithInputElements,
//        inputSetWithNonAliasingElements
    )

//    private fun nonAliasingSetWithInputElementsId(setId: UNonAliasingHeapAddress) =
//        UNonAliasingRefSetWithInputElementsId(setId, setType, sort)
//
//    private fun getNonAliasingSetWithInputElements(
//        id: UNonAliasingRefSetWithInputElementsId<SetType>,
//    ): UNonAliasingRefSetWithInputElements<SetType> {
//        val (updatedSet, collection) = nonAliasingSetWithInputElements.getOrPut(id, defaultOwnership) { id.emptyRegion() }
//        nonAliasingSetWithInputElements = updatedSet
//        return collection
//    }
//
//    fun nonAliasingSetWithInputElements(setRef: UConcreteHeapAddress) =
//        getNonAliasingSetWithInputElements(nonAliasingSetWithInputElementsId(setRef))
//
//    private fun updateNonAliasingSetWithInputElements(
//        id: UNonAliasingRefSetWithInputElementsId<SetType>,
//        updatedSet: UNonAliasingRefSetWithInputElements<SetType>,
//        ownership: MutabilityOwnership,
//    ) = URefSetMemoryRegion(
//        setType, sort,
//        allocatedSetWithAllocatedElements,
//        allocatedSetWithInputElements,
//        inputSetWithAllocatedElements,
//        inputSetWithInputElements,
//        allocatedSetWithNonAliasingElements,
//        nonAliasingSetWithAllocatedElements,
//        nonAliasingSetWithNonAliasingElements,
//        nonAliasingSetWithInputElements.put(id, updatedSet, ownership),
//        inputSetWithNonAliasingElements
//    )
    private fun nonAliasingSetWithAllocatedElementsId(setId: UNonAliasingHeapAddress, elementAddress: UConcreteHeapAddress) =
        UNonAliasingRefSetWithAllocatedElementsId(setId, elementAddress, setType, sort)

    private fun getNonAliasingSetWithAllocatedElements(
        id: UNonAliasingRefSetWithAllocatedElementsId<SetType>
    ): UNonAliasingRefSetWithAllocatedElements<SetType> {
        val (updatedMap, collection) = nonAliasingSetWithAllocatedElements.getOrPut(id, defaultOwnership) { id.emptyRegion() }
        nonAliasingSetWithAllocatedElements = updatedMap
        return collection
    }

    private fun updateNonAliasingSetWithAllocatedElements(
        id: UNonAliasingRefSetWithAllocatedElementsId<SetType>,
        updatedSet: UNonAliasingRefSetWithAllocatedElements<SetType>,
        ownership: MutabilityOwnership,
    ) = URefSetMemoryRegion(
        setType, sort,
        allocatedSetWithAllocatedElements,
        allocatedSetWithInputElements,
        inputSetWithAllocatedElements,
        inputSetWithInputElements,
        allocatedSetWithNonAliasingElements,
        nonAliasingSetWithAllocatedElements.put(id, updatedSet, ownership),
        nonAliasingSetWithNonAliasingElements,
//        nonAliasingSetWithInputElements,
//        inputSetWithNonAliasingElements
    )

    private fun inputSetWithAllocatedElementsId(elementAddress: UConcreteHeapAddress) =
        UInputRefSetWithAllocatedElementsId(elementAddress, setType, sort)

    private fun getInputSetWithAllocatedElements(
        id: UInputRefSetWithAllocatedElementsId<SetType>
    ): UInputRefSetWithAllocatedElements<SetType> {
        val (updatedMap, collection) = inputSetWithAllocatedElements.getOrPut(id, defaultOwnership) { id.emptyRegion() }
        inputSetWithAllocatedElements = updatedMap
        return collection
    }

    private fun updateInputSetWithAllocatedElements(
        id: UInputRefSetWithAllocatedElementsId<SetType>,
        updatedSet: UInputRefSetWithAllocatedElements<SetType>,
        ownership: MutabilityOwnership,
    ) = URefSetMemoryRegion(
        setType, sort,
        allocatedSetWithAllocatedElements,
        allocatedSetWithInputElements,
        inputSetWithAllocatedElements.put(id, updatedSet, ownership),
        inputSetWithInputElements,
        allocatedSetWithNonAliasingElements,
        nonAliasingSetWithAllocatedElements,
        nonAliasingSetWithNonAliasingElements,
//        nonAliasingSetWithInputElements,
//        inputSetWithNonAliasingElements
    )

//    private fun inputSetWithNonAliasingElementsId(elementId: UNonAliasingHeapAddress) =
//        UInputRefSetWithNonAliasingElementsId(elementId, setType, sort)
//
//    private fun getInputSetWithNonAliasingElements(
//        id: UInputRefSetWithNonAliasingElementsId<SetType>
//    ): UInputRefSetWithNonAliasingElements<SetType> {
//        val (updatedMap, collection) = inputSetWithNonAliasingElements.getOrPut(id, defaultOwnership) { id.emptyRegion() }
//        inputSetWithNonAliasingElements = updatedMap
//        return collection
//    }
//
//    private fun updateInputSetWithNonAliasingElements(
//        id: UInputRefSetWithNonAliasingElementsId<SetType>,
//        updatedSet: UInputRefSetWithNonAliasingElements<SetType>,
//        ownership: MutabilityOwnership,
//    ) = URefSetMemoryRegion(
//        setType, sort,
//        allocatedSetWithAllocatedElements,
//        allocatedSetWithInputElements,
//        inputSetWithAllocatedElements,
//        inputSetWithInputElements,
//        allocatedSetWithNonAliasingElements,
//        nonAliasingSetWithAllocatedElements,
//        nonAliasingSetWithNonAliasingElements,
//        nonAliasingSetWithInputElements,
//        inputSetWithNonAliasingElements.put(id, updatedSet, ownership)
//    )

    override fun nonAliasingSetWithNonAliasingElements(setRef: UNonAliasingHeapAddress, setElement: UNonAliasingHeapAddress) =
        getNonAliasingSetWithNonAliasingElements(nonAliasingSetWithNonAliasingElementsId(setRef, setElement))
    private fun nonAliasingSetWithNonAliasingElementsId(setId: UNonAliasingHeapAddress, elementId: UNonAliasingHeapAddress) =
        UNonAliasingRefSetWithNonAliasingElementsId(setId, elementId, setType, sort)

    private fun getNonAliasingSetWithNonAliasingElements(
        id: UNonAliasingRefSetWithNonAliasingElementsId<SetType>
    ): UNonAliasingRefSetWithNonAliasingElements<SetType> {
        val (updatedMap, collection) = nonAliasingSetWithNonAliasingElements.getOrPut(id, defaultOwnership) { id.emptyRegion() }
        nonAliasingSetWithNonAliasingElements = updatedMap
        return collection
    }

    private fun updateNonAliasingSetWithNonAliasingElements(
        id: UNonAliasingRefSetWithNonAliasingElementsId<SetType>,
        updatedSet: UNonAliasingRefSetWithNonAliasingElements<SetType>,
        ownership: MutabilityOwnership,
    ) = URefSetMemoryRegion(
        setType, sort,
        allocatedSetWithAllocatedElements,
        allocatedSetWithInputElements,
        inputSetWithAllocatedElements,
        inputSetWithInputElements,
        allocatedSetWithNonAliasingElements,
        nonAliasingSetWithAllocatedElements,
        nonAliasingSetWithNonAliasingElements.put(id, updatedSet, ownership),
//        nonAliasingSetWithInputElements,
//        inputSetWithNonAliasingElements
    )

    override fun inputSetWithInputElements(): UInputRefSetWithInputElements<SetType> {
        if (inputSetWithInputElements == null)
            inputSetWithInputElements = UInputRefSetWithInputElementsId(setType, sort).emptyRegion()
        return inputSetWithInputElements!!
    }

    private fun updateInputSetWithInputElements(updatedSet: UInputRefSetWithInputElements<SetType>) =
        URefSetMemoryRegion(
            setType, sort,
            allocatedSetWithAllocatedElements,
            allocatedSetWithInputElements,
            inputSetWithAllocatedElements,
            updatedSet,
            allocatedSetWithNonAliasingElements,
            nonAliasingSetWithAllocatedElements,
            nonAliasingSetWithNonAliasingElements,
//            nonAliasingSetWithInputElements,
//            inputSetWithNonAliasingElements
        )

    override fun read(key: URefSetEntryLValue<SetType>): UBoolExpr =
        key.setRef.mapWithStaticAsSymbolic(
            // all these cases needs serious rewrites maybe (copy-paste from above)
            { concreteRef ->
                key.setElement.mapWithStaticAsSymbolic(
                    { concreteElem ->
                        val id = UAllocatedRefSetWithAllocatedElementId(concreteRef.address, concreteElem.address)
                        allocatedSetWithAllocatedElements[id] ?: sort.uctx.falseExpr
                    },
                    { nonAliasingElem ->
                        val id = allocatedSetWithNonAliasingElementsId(concreteRef.address, getId(nonAliasingElem))
                        getAllocatedSetWithNonAliasingElements(id).read(nonAliasingElem)
                    },
                    { symbolicElem ->
                        val id = allocatedSetWithInputElementsId(concreteRef.address)
                        getAllocatedSetWithInputElements(id).read(symbolicElem)
                    },
                    ignoreNullRefs = false
                )
            },
            { nonAliasingRef ->
                key.setElement.mapWithStaticAsSymbolic(
                    { concreteElem ->
                        val id = nonAliasingSetWithAllocatedElementsId(getId(nonAliasingRef), concreteElem.address)
                        getNonAliasingSetWithAllocatedElements(id).read(nonAliasingRef)
                    },
                    { nonAliasingElem ->
                        // ?
                        val id = nonAliasingSetWithNonAliasingElementsId(getId(nonAliasingRef), getId(nonAliasingElem))
                        getNonAliasingSetWithNonAliasingElements(id).read(nonAliasingRef to nonAliasingElem)
                    },
                    { symbolicElem ->
                        throw IllegalStateException("NA and input together")
//                        val id = nonAliasingSetWithInputElementsId(getId(nonAliasingRef))
//                        getNonAliasingSetWithInputElements(id).read(symbolicElem)
                    },
                    ignoreNullRefs = false
                )
            },
            { symbolicRef ->
                key.setElement.mapWithStaticAsSymbolic(
                    { concreteElem ->
                        val id = inputSetWithAllocatedElementsId(concreteElem.address)
                        getInputSetWithAllocatedElements(id).read(symbolicRef)
                    },
                    { nonAliasingElem ->
                        throw IllegalStateException("NA and input together")
//                        val id = inputSetWithNonAliasingElementsId(getId(nonAliasingElem))
//                        getInputSetWithNonAliasingElements(id).read(symbolicRef)
                    },
                    { symbolicElem ->
                        inputSetWithInputElements().read(symbolicRef to symbolicElem)
                    },
                    ignoreNullRefs = false
                )
            },
        )

    override fun write(
        key: URefSetEntryLValue<SetType>,
        value: UBoolExpr,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
    ) = foldHeapRefWithStaticAsSymbolic(
        ref = key.setRef,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { setRegion, (concreteSetRef, setGuard) ->
            foldHeapRefWithStaticAsSymbolic(
                ref = key.setElement,
                initial = setRegion,
                initialGuard = setGuard,
                ignoreNullRefs = false,
                blockOnConcrete = { region, (concreteElemRef, guard) ->
                    val id = UAllocatedRefSetWithAllocatedElementId(concreteSetRef.address, concreteElemRef.address)
                    val newMap = region.allocatedSetWithAllocatedElements.guardedWrite(id, value, guard, ownership) {
                        sort.uctx.falseExpr
                    }
                    region.updateAllocatedSetWithAllocatedElements(newMap)
                },
                blockOnSymbolic = { region, (symbolicElemRef, guard) ->
                    val id = allocatedSetWithInputElementsId(concreteSetRef.address)
                    val newMap = region.getAllocatedSetWithInputElements(id)
                        .write(symbolicElemRef, value, guard, ownership)
                    region.updateAllocatedSetWithInputElements(id, newMap, ownership)
                },
                blockOnNonAliasing = { region, (nonAliasingElemRef, guard) ->
                    val id = allocatedSetWithNonAliasingElementsId(concreteSetRef.address, getId(nonAliasingElemRef))
                    val newMap = region.getAllocatedSetWithNonAliasingElements(id)
                        .write(nonAliasingElemRef, value, guard, ownership)
                    region.updateAllocatedSetWithNonAliasingElements(id, newMap, ownership)
                }
            )
        },
        blockOnSymbolic = { setRegion, (symbolicSetRef, setGuard) ->
            foldHeapRefWithStaticAsSymbolic(
                ref = key.setElement,
                initial = setRegion,
                initialGuard = setGuard,
                ignoreNullRefs = false,
                blockOnConcrete = { region, (concreteElemRef, guard) ->
                    val id = inputSetWithAllocatedElementsId(concreteElemRef.address)
                    val newMap = region.getInputSetWithAllocatedElements(id)
                        .write(symbolicSetRef, value, guard, ownership)
                    region.updateInputSetWithAllocatedElements(id, newMap, ownership)
                },
                blockOnSymbolic = { region, (symbolicElemRef, guard) ->
                    val newMap = region.inputSetWithInputElements()
                        .write(symbolicSetRef to symbolicElemRef, value, guard, ownership)
                    region.updateInputSetWithInputElements(newMap)
                },
                blockOnNonAliasing = { region, (nonAliasingElemRef, guard) ->
                    throw IllegalStateException("NA and input together")
//                    val id = inputSetWithNonAliasingElementsId(getId(nonAliasingElemRef))
//                    val newMap = region.getInputSetWithNonAliasingElements(id)
//                        .write(symbolicSetRef, value, guard, ownership)
//                    region.updateInputSetWithNonAliasingElements(id, newMap, ownership)
                }
            )
        },
        blockOnNonAliasing = { setRegion, (nonAliasingSetRef, setGuard) ->
            foldHeapRefWithStaticAsSymbolic(
                ref = key.setElement,
                initial = setRegion,
                initialGuard = setGuard,
                ignoreNullRefs = false,
                blockOnConcrete = { region, (concreteElemRef, guard) ->
                    val id = nonAliasingSetWithAllocatedElementsId(getId(nonAliasingSetRef), concreteElemRef.address)
                    val newMap = region.getNonAliasingSetWithAllocatedElements(id)
                        .write(nonAliasingSetRef, value, guard, ownership)
                    region.updateNonAliasingSetWithAllocatedElements(id, newMap, ownership)
                },
                blockOnSymbolic = { region, (symbolicElemRef, guard) ->
                    throw IllegalStateException("NA and input together")
//                    val id = nonAliasingSetWithInputElementsId(getId(nonAliasingSetRef))
//                    val newMap = region.getNonAliasingSetWithInputElements(id)
//                        .write(symbolicElemRef, value, guard, ownership)
//                    region.updateNonAliasingSetWithInputElements(id, newMap, ownership)
                },
                blockOnNonAliasing = { region, (nonAliasingElemRef, guard) ->
                    val id = nonAliasingSetWithNonAliasingElementsId(getId(nonAliasingSetRef), getId(nonAliasingElemRef))
                    val newMap = region.getNonAliasingSetWithNonAliasingElements(id)
                        .write(nonAliasingSetRef to nonAliasingElemRef, value, guard, ownership)
                    region.updateNonAliasingSetWithNonAliasingElements(id, newMap, ownership)
                }
            )
        },
    )

    override fun union(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ) = foldHeapRef2(
        ref0 = srcRef,
        ref1 = dstRef,
        initial = this,
        initialGuard = operationGuard,
        blockOnConcrete0Concrete1 = { region, srcConcrete, dstConcrete, guard ->
            val initialAllocatedSetState = region.allocatedSetWithAllocatedElements
            val updatedAllocatedSet = region.unionAllocatedSetAllocatedElements(
                initial = initialAllocatedSetState,
                srcAddress = srcConcrete.address,
                guard = guard,
                read = { initialAllocatedSetState[it] ?: sort.uctx.falseExpr },
                mkDstKeyId = { UAllocatedRefSetWithAllocatedElementId(dstConcrete.address, it) },
                write = { result, dstKeyId, value, g ->
                    result.guardedWrite(dstKeyId, value, g, ownership) { sort.uctx.falseExpr }
                }
            )
            val updatedRegion = region.updateAllocatedSetWithAllocatedElements(updatedAllocatedSet)

            val srcId = allocatedSetWithInputElementsId(srcConcrete.address)
            val srcCollection = updatedRegion.getAllocatedSetWithInputElements(srcId)

            val dstId = allocatedSetWithInputElementsId(dstConcrete.address)
            val dstCollection = updatedRegion.getAllocatedSetWithInputElements(dstId)

            val adapter = UAllocatedToAllocatedSymbolicRefSetUnionAdapter(srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            val updatedRegion2 = updatedRegion.updateAllocatedSetWithInputElements(dstId, updated, ownership)

            updatedRegion2.unionAllocatedSetNonAliasingElements(
                initial = updatedRegion2,
                srcSetRef = srcConcrete,
                read = { id -> region.getAllocatedSetWithNonAliasingElements(id) },
                mkDstKeyId = { allocatedSetWithNonAliasingElementsId(srcConcrete.address, it) },
                write = { result, dstKeyId, srcCollection2 ->
                    val dstCollection2 = result.getAllocatedSetWithNonAliasingElements(dstKeyId)
                    val adapter2 = UAllocatedToAllocatedNARefSetUnionAdapter(srcCollection2)
                    val updated2 = dstCollection2.copyRange(srcCollection2, adapter2, guard)
                    result.updateAllocatedSetWithNonAliasingElements(dstKeyId, updated2, ownership)
                }
            )

        },
        blockOnSymbolic0Concrete1 = { region, srcSymbolic, dstConcrete, guard ->
            val updatedAllocatedSet = region.unionInputSetAllocatedElements(
                initial = region.allocatedSetWithAllocatedElements,
                guard = guard,
                read = { region.getInputSetWithAllocatedElements(it).read(srcSymbolic) },
                mkDstKeyId = { UAllocatedRefSetWithAllocatedElementId(dstConcrete.address, it) },
                write = { result, dstKeyId, value, g ->
                    result.guardedWrite(dstKeyId, value, g, ownership) { sort.uctx.falseExpr }
                }
            )
            val updatedRegion = region.updateAllocatedSetWithAllocatedElements(updatedAllocatedSet)

            val srcCollection = updatedRegion.inputSetWithInputElements()

            val dstId = allocatedSetWithInputElementsId(dstConcrete.address)
            val dstCollection = updatedRegion.getAllocatedSetWithInputElements(dstId)

            val adapter = UInputToAllocatedSymbolicRefSetUnionAdapter(srcSymbolic, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            updatedRegion.updateAllocatedSetWithInputElements(dstId, updated, ownership)
        },
        blockOnNonAliasing0Concrete1 = {region, srcNonAliasing, dstConcrete, guard ->
            val updatedAllocatedSet = region.unionNonAliasingSetAllocatedElements(
                initial = region.allocatedSetWithAllocatedElements,
                srcSetRef = srcNonAliasing,
                guard = guard,
                read = { region.getNonAliasingSetWithAllocatedElements(it).read(srcNonAliasing) },
                mkDstKeyId = { UAllocatedRefSetWithAllocatedElementId(dstConcrete.address, it) },
                write = { result, dstKeyId, value, g ->
                    result.guardedWrite(dstKeyId, value, g, ownership) { sort.uctx.falseExpr }
                }
            )
            val updatedRegion = region.updateAllocatedSetWithAllocatedElements(updatedAllocatedSet)
            updatedRegion.unionNonAliasingSetNonAliasingElements(
                initial = updatedRegion,
                srcSetRef = srcNonAliasing,
                read = { id -> updatedRegion.getNonAliasingSetWithNonAliasingElements(id) },
                mkDstKeyId = { allocatedSetWithNonAliasingElementsId(dstConcrete.address, it) },
                write = { result, dstKeyId, srcCollection ->
                    val dstCollection = result.getAllocatedSetWithNonAliasingElements(dstKeyId)
                    val adapter = UNonAliasingToAllocatedSymbolicRefSetUnionAdapter(srcNonAliasing, srcCollection)
                    val updated = dstCollection.copyRange(srcCollection, adapter, guard)
                    result.updateAllocatedSetWithNonAliasingElements(dstKeyId, updated, ownership)
                }
            )
        },
        blockOnConcrete0Symbolic1 = { region, srcConcrete, dstSymbolic, guard ->
            val initialAllocatedSetState = region.allocatedSetWithAllocatedElements
            val updatedRegion = region.unionAllocatedSetAllocatedElements(
                initial = region, srcAddress = srcConcrete.address, guard = guard,
                read = { initialAllocatedSetState[it] ?: sort.uctx.falseExpr },
                mkDstKeyId = { inputSetWithAllocatedElementsId(it) },
                write = { result, dstKeyId, value, g ->
                    val newMap = result.getInputSetWithAllocatedElements(dstKeyId)
                        .write(dstSymbolic, value, g, ownership)
                    result.updateInputSetWithAllocatedElements(dstKeyId, newMap, ownership)
                }
            )

            val srcId = allocatedSetWithInputElementsId(srcConcrete.address)
            val srcCollection = updatedRegion.getAllocatedSetWithInputElements(srcId)

            val dstCollection = updatedRegion.inputSetWithInputElements()

            val adapter = UAllocatedToInputSymbolicRefSetUnionAdapter(dstSymbolic, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            updatedRegion.updateInputSetWithInputElements(updated)
        },
        blockOnConcrete0NonAliasing1 = { region, srcConcrete, dstNonAliasing, guard ->
            val initialAllocatedSetState = region.allocatedSetWithAllocatedElements
            val updatedRegion = region.unionAllocatedSetAllocatedElements(
                initial = region,
                srcAddress = srcConcrete.address,
                guard = guard,
                read = { initialAllocatedSetState[it] ?: sort.uctx.falseExpr },
                mkDstKeyId = { nonAliasingSetWithAllocatedElementsId(getId(dstNonAliasing), it) },
                write = { result, dstKeyId, value, g ->
                    val newMap = result.getNonAliasingSetWithAllocatedElements(dstKeyId)
                        .write(dstNonAliasing, value, g, ownership)
                    result.updateNonAliasingSetWithAllocatedElements(dstKeyId, newMap, ownership)
                }
            )
            updatedRegion.unionAllocatedSetNonAliasingElements(
                initial = updatedRegion,
                srcSetRef = srcConcrete,
                read = { id -> updatedRegion.getAllocatedSetWithNonAliasingElements(id) },
                mkDstKeyId = { nonAliasingSetWithNonAliasingElementsId(getId(dstNonAliasing), it) },
                write = { result, dstKeyId, srcCollection ->
                    val dstCollection = result.getNonAliasingSetWithNonAliasingElements(dstKeyId)
                    val adapter = UAllocatedToNonAliasingSymbolicRefSetUnionAdapter(dstNonAliasing, srcCollection)
                    val updated = dstCollection.copyRange(srcCollection, adapter, guard)
                    result.updateNonAliasingSetWithNonAliasingElements(dstKeyId, updated, ownership)
                }
            )
        },
        blockOnNonAliasing0NonAliasing1 = {region, srcNonAliasing, dstNonAliasing, guard ->
            val updatedRegion = region.unionNonAliasingSetAllocatedElements(
                initial = region,
                srcSetRef = srcNonAliasing,
                guard = guard,
                read = { region.getNonAliasingSetWithAllocatedElements(it).read(srcNonAliasing) },
                mkDstKeyId = { nonAliasingSetWithAllocatedElementsId(getId(dstNonAliasing), it) },
                write = { result, dstKeyId, value, g ->
                    val newMap = region.getNonAliasingSetWithAllocatedElements(dstKeyId)
                        .write(dstNonAliasing, value, g, ownership)
                    result.updateNonAliasingSetWithAllocatedElements(dstKeyId, newMap, ownership)
                }
            )
            updatedRegion.unionNonAliasingSetNonAliasingElements(
                initial = updatedRegion,
                srcSetRef = srcNonAliasing,
                read = { id -> region.getNonAliasingSetWithNonAliasingElements(id) },
                mkDstKeyId = { nonAliasingSetWithNonAliasingElementsId(getId(dstNonAliasing), it) },
                write = { result, dstKeyId, srcCollection ->
                    val dstCollection = updatedRegion.getNonAliasingSetWithNonAliasingElements(dstKeyId)
                    val adapter = UNonAliasingToNonAliasingSymbolicRefSetUnionAdapter(srcNonAliasing, dstNonAliasing, srcCollection)
                    val updated = dstCollection.copyRange(srcCollection, adapter, guard)
                    result.updateNonAliasingSetWithNonAliasingElements(dstKeyId, updated, ownership)
                }
            )
        },
        blockOnSymbolic0Symbolic1 = { region, srcSymbolic, dstSymbolic, guard ->
            val updatedRegion = region.unionInputSetAllocatedElements(
                initial = region, guard = guard,
                read = { region.getInputSetWithAllocatedElements(it).read(srcSymbolic) },
                mkDstKeyId = { inputSetWithAllocatedElementsId(it) },
                write = { result, dstKeyId, value, g ->
                    val newMap = result.getInputSetWithAllocatedElements(dstKeyId)
                        .write(dstSymbolic, value, g, ownership)
                    result.updateInputSetWithAllocatedElements(dstKeyId, newMap, ownership)
                }
            )
            val srcCollection = updatedRegion.inputSetWithInputElements()
            val dstCollection = updatedRegion.inputSetWithInputElements()

            val adapter = UInputToInputSymbolicRefSetUnionAdapter(srcSymbolic, dstSymbolic, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            updatedRegion.updateInputSetWithInputElements(updated)
        }
    )

    private inline fun <R, DstKeyId> unionInputSetAllocatedElements(
        initial: R,
        guard: UBoolExpr,
        read: (UInputRefSetWithAllocatedElementsId<SetType>) -> UBoolExpr,
        mkDstKeyId: (UConcreteHeapAddress) -> DstKeyId,
        write: (R, DstKeyId, UBoolExpr, UBoolExpr) -> R
    ) = unionAllocatedElements(
        initial,
        inputSetWithAllocatedElements.keys.toList(),
        guard,
        read,
        { mkDstKeyId(it.elementAddress) },
        write
    )

    private inline fun <R, DstKeyId> unionAllocatedSetAllocatedElements(
        initial: R,
        srcAddress: UConcreteHeapAddress,
        guard: UBoolExpr,
        read: (UAllocatedRefSetWithAllocatedElementId) -> UBoolExpr,
        mkDstKeyId: (UConcreteHeapAddress) -> DstKeyId,
        write: (R, DstKeyId, UBoolExpr, UBoolExpr) -> R
    ) = unionAllocatedElements(
        initial,
        allocatedSetWithAllocatedElements.keys.filterTo(mutableListOf()) { it.setAddress == srcAddress },
        guard,
        read,
        { mkDstKeyId(it.elementAddress) },
        write
    )

    private inline fun <R, DstKeyId> unionNonAliasingSetAllocatedElements(
        initial: R,
        srcSetRef: UHeapRef,
        guard: UBoolExpr,
        read: (UNonAliasingRefSetWithAllocatedElementsId<SetType>) -> UBoolExpr,
        mkDstKeyId: (UNonAliasingHeapAddress) -> DstKeyId,
        write: (R, DstKeyId, UBoolExpr, UBoolExpr) -> R
    ) = unionAllocatedElements(
        initial,
        nonAliasingSetWithAllocatedElements.keys.filterTo(mutableListOf()) { it.setId == getId(srcSetRef) },
        guard,
        read,
        { mkDstKeyId(it.elementAddress) },
        write
    )

    private inline fun <R, DstKeyId> unionAllocatedSetNonAliasingElements(
        initial: R,
        srcSetRef: UHeapRef,
        read: (UAllocatedRefSetWithNonAliasingElementsId<SetType>) -> UAllocatedRefSetWithNonAliasingElements<SetType>,
        mkDstKeyId: (UNonAliasingHeapAddress) -> DstKeyId,
        write: (R, DstKeyId,  UAllocatedRefSetWithNonAliasingElements<SetType>) -> R
    ) = unionNonAliasingElements(
        initial,
        allocatedSetWithNonAliasingElements.keys.filterTo(mutableListOf()) { it.setAddress == getId(srcSetRef) },
        read,
        { mkDstKeyId(it.elementId) },
        write
    )

    private inline fun <R, DstKeyId> unionNonAliasingSetNonAliasingElements(
        initial: R,
        srcSetRef: UHeapRef,
        read: (UNonAliasingRefSetWithNonAliasingElementsId<SetType>) -> UNonAliasingRefSetWithNonAliasingElements<SetType>,
        mkDstKeyId: (UNonAliasingHeapAddress) -> DstKeyId,
        write: (R, DstKeyId, UNonAliasingRefSetWithNonAliasingElements<SetType>) -> R
    ) = unionNonAliasingElements(
        initial,
        nonAliasingSetWithNonAliasingElements.keys.filterTo(mutableListOf()) { it.setId == getId(srcSetRef) },
        read,
        { mkDstKeyId(it.elementId) },
        write
    )

    private inline fun <R, SrcKeyId, DstKeyId> unionAllocatedElements(
        initial: R,
        keys: List<SrcKeyId>,
        guard: UBoolExpr,
        read: (SrcKeyId) -> UBoolExpr,
        mkDstKeyId: (SrcKeyId) -> DstKeyId,
        write: (R, DstKeyId, UBoolExpr, UBoolExpr) -> R
    ): R = keys.fold(initial) { result, srcKeyId ->
        val srcContains = read(srcKeyId)

        val mergedGuard = guard.uctx.mkAnd(srcContains, guard)
        write(result, mkDstKeyId(srcKeyId), guard.uctx.trueExpr, mergedGuard)
    }

    private inline fun <R, SrcKeyId, DstKeyId, SrcCollection> unionNonAliasingElements(
        initial: R,
        keys: List<SrcKeyId>,
        read: (SrcKeyId) -> SrcCollection,
        mkDstKeyId: (SrcKeyId) -> DstKeyId,
        write: (R, DstKeyId, SrcCollection) -> R
    ): R = keys.fold(initial) { result, srcKeyId ->
        val srcCollection = read(srcKeyId)
        write(result, mkDstKeyId(srcKeyId), srcCollection)
    }

    override fun setEntries(ref: UHeapRef): URefSetEntries<SetType> =
        foldHeapRefWithStaticAsSymbolic(
            ref = ref,
            initial = URefSetEntries(),
            initialGuard = ref.uctx.trueExpr,
            blockOnConcrete = { entries, (concreteRef, _) ->
                allocatedSetWithAllocatedElements.keys.forEach { entry ->
                    if (entry.setAddress == concreteRef.address) {
                        val elem = ref.uctx.mkConcreteHeapRef(entry.elementAddress)
                        entries.add(URefSetEntryLValue(concreteRef, elem, setType))
                    }
                }

                val elementsId = allocatedSetWithInputElementsId(concreteRef.address)
                val elements = USymbolicSetElementsCollector.collect(
                    getAllocatedSetWithInputElements(elementsId).updates
                )
                elements.elements.forEach { elem ->
                    entries.add(URefSetEntryLValue(concreteRef, elem, setType))
                }

                if (elements.isInput) {
                    entries.markAsInput()
                }

                allocatedSetWithNonAliasingElements.keys.forEach { entry ->
                    if (entry.setAddress == concreteRef.address) {
                        val elem = NAHeapRefMap[entry.elementId] ?: ref.uctx.mkConcreteHeapRef(entry.elementId)
                        entries.add(URefSetEntryLValue(concreteRef, elem, setType))
                    }
                }

                entries
            },
            blockOnSymbolic = { entries, (symbolicRef, _) ->
                inputSetWithAllocatedElements.keys.forEach { entry ->
                    val elem = ref.uctx.mkConcreteHeapRef(entry.elementAddress)
                    entries.add(URefSetEntryLValue(symbolicRef, elem, setType))
                }

                val elements = USymbolicSetElementsCollector.collect(inputSetWithInputElements().updates)
                elements.elements.forEach { entry ->
                    entries.add(URefSetEntryLValue(symbolicRef, entry.second, setType))
                }

                entries.markAsInput()

                entries
            },
            blockOnNonAliasing = { entries, (nonAliasingRef, _) ->
                val setId = getId(nonAliasingRef)
                nonAliasingSetWithAllocatedElements.keys.forEach { entry ->
                    if (entry.setId == setId) {
                        val elem = ref.uctx.mkConcreteHeapRef(entry.elementAddress)
                        entries.add(URefSetEntryLValue(nonAliasingRef, elem, setType))
                    }
                }
                nonAliasingSetWithNonAliasingElements.keys.forEach { entry ->
                    if (entry.setId == setId) {
                        val elem = NAHeapRefMap[entry.elementId] ?: ref.uctx.mkConcreteHeapRef(entry.elementId)
                        entries.add(URefSetEntryLValue(nonAliasingRef, elem, setType))
                    }
                }

                entries.markAsInput()

                entries
            }
        )
}
