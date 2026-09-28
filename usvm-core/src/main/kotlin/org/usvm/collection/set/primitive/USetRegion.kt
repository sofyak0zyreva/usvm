package org.usvm.collection.set.primitive

import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.USort
import org.usvm.collection.field.getId
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.collection.set.USymbolicSetElementsCollector
import org.usvm.collection.set.USymbolicSetEntries
import org.usvm.collections.immutable.getOrPut
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.foldHeapRef2
import org.usvm.memory.foldHeapRefWithStaticAsSymbolic
import org.usvm.memory.mapWithStaticAsSymbolic
import org.usvm.regions.Region
import org.usvm.uctx

data class USetEntryLValue<SetType, ElementSort : USort, Reg : Region<Reg>>(
    val elementSort: ElementSort,
    val setRef: UHeapRef,
    val setElement: UExpr<ElementSort>,
    val setType: SetType,
    val elementInfo: USymbolicCollectionKeyInfo<UExpr<ElementSort>, Reg>,
) : ULValue<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort> {
    override val sort: UBoolSort
        get() = elementSort.uctx.boolSort

    override val memoryRegionId: UMemoryRegionId<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort>
        get() = USetRegionId(elementSort, setType, elementInfo)

    override val key: USetEntryLValue<SetType, ElementSort, Reg>
        get() = this
}

data class USetRegionId<SetType, ElementSort : USort, Reg : Region<Reg>>(
    val elementSort: ElementSort,
    val setType: SetType,
    val elementInfo: USymbolicCollectionKeyInfo<UExpr<ElementSort>, Reg>,
) : UMemoryRegionId<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort> {
    override val sort: UBoolSort
        get() = elementSort.uctx.boolSort

    override fun emptyRegion(): USetRegion<SetType, ElementSort, Reg> =
        USetMemoryRegion(setType, elementSort, elementInfo)
}

typealias UAllocatedSet<SetType, ElementSort, Reg> =
    USymbolicCollection<UAllocatedSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort>

typealias UNonAliasingSet<SetType, ElementSort, Reg> =
    USymbolicCollection<UNonAliasingSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort>

typealias UInputSet<SetType, ElementSort, Reg> =
    USymbolicCollection<UInputSetId<SetType, ElementSort, Reg>, USymbolicSetElement<ElementSort>, UBoolSort>

typealias UPrimitiveSetEntries<SetType, ElementSort, Reg> = USymbolicSetEntries<USetEntryLValue<SetType, ElementSort, Reg>>

interface USetReadOnlyRegion<SetType, ElementSort : USort, Reg : Region<Reg>> :
    UReadOnlyMemoryRegion<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort> {
    fun setEntries(ref: UHeapRef): UPrimitiveSetEntries<SetType, ElementSort, Reg>
}

interface USetRegion<SetType, ElementSort : USort, Reg : Region<Reg>> :
    USetReadOnlyRegion<SetType, ElementSort, Reg>,
    UMemoryRegion<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort> {

    fun allocatedSetElements(address: UConcreteHeapAddress): UAllocatedSet<SetType, ElementSort, Reg>
    fun nonAliasingSetElements(address: UConcreteHeapAddress): UNonAliasingSet<SetType, ElementSort, Reg>

    fun inputSetElements(): UInputSet<SetType, ElementSort, Reg>

    fun union(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): USetRegion<SetType, ElementSort, Reg>
}

internal class USetMemoryRegion<SetType, ElementSort : USort, Reg : Region<Reg>>(
    private val setType: SetType,
    private val elementSort: ElementSort,
    private val elementInfo: USymbolicCollectionKeyInfo<UExpr<ElementSort>, Reg>,
    private var allocatedSets:
    UPersistentHashMap<UAllocatedSetId<SetType, ElementSort, Reg>, UAllocatedSet<SetType, ElementSort, Reg>> = persistentHashMapOf(),
    private var nonAliasingSets:
    UPersistentHashMap<UNonAliasingSetId<SetType, ElementSort, Reg>, UNonAliasingSet<SetType, ElementSort, Reg>> = persistentHashMapOf(),
    private var inputSet: UInputSet<SetType, ElementSort, Reg>? = null,
) : USetRegion<SetType, ElementSort, Reg> {

    private val defaultOwnership = elementSort.uctx.defaultOwnership
    init {
        check(elementSort != elementSort.uctx.addressSort) {
            "Ref set must be used to handle sets with ref elements"
        }
    }

    override fun allocatedSetElements(address: UConcreteHeapAddress): UAllocatedSet<SetType, ElementSort, Reg> =
        getAllocatedSet(UAllocatedSetId(address, elementSort, setType, elementInfo))

    private fun getAllocatedSet(
        id: UAllocatedSetId<SetType, ElementSort, Reg>,
    ): UAllocatedSet<SetType, ElementSort, Reg> {
        val (updatesSets, collection) = allocatedSets.getOrPut(id, defaultOwnership) { id.emptyRegion() }
        allocatedSets = updatesSets
        return collection
    }

    private fun updateAllocatedSet(
        id: UAllocatedSetId<SetType, ElementSort, Reg>,
        updated: UAllocatedSet<SetType, ElementSort, Reg>,
        ownership: MutabilityOwnership,
    ) = USetMemoryRegion(
        setType,
        elementSort,
        elementInfo,
        allocatedSets.put(id, updated, ownership),
        nonAliasingSets,
        inputSet
    )

    override fun nonAliasingSetElements(address: UNonAliasingHeapAddress): UNonAliasingSet<SetType, ElementSort, Reg> =
        getNonAliasingSet(UNonAliasingSetId(elementSort, setType, elementInfo, address))

    private fun getNonAliasingSet(
        id: UNonAliasingSetId<SetType, ElementSort, Reg>,
    ): UNonAliasingSet<SetType, ElementSort, Reg> {
        val (updatesSets, collection) = nonAliasingSets.getOrPut(id, defaultOwnership) { id.emptyRegion() }
        nonAliasingSets = updatesSets
        return collection
    }

    private fun updateNonAliasingSet(
        id: UNonAliasingSetId<SetType, ElementSort, Reg>,
        updated: UNonAliasingSet<SetType, ElementSort, Reg>,
        ownership: MutabilityOwnership,
    ) = USetMemoryRegion(
        setType,
        elementSort,
        elementInfo,
        allocatedSets,
        nonAliasingSets.put(id, updated, ownership),
        inputSet
    )

    override fun inputSetElements(): UInputSet<SetType, ElementSort, Reg> {
        if (inputSet == null) {
            inputSet = UInputSetId(elementSort, setType, elementInfo).emptyRegion()
        }
        return inputSet!!
    }

    private fun updateInputSet(updated: UInputSet<SetType, ElementSort, Reg>) =
        USetMemoryRegion(setType, elementSort, elementInfo, allocatedSets, nonAliasingSets, updated)

    override fun read(key: USetEntryLValue<SetType, ElementSort, Reg>): UExpr<UBoolSort> =
        key.setRef.mapWithStaticAsSymbolic(
            concreteMapper = { concreteRef -> allocatedSetElements(concreteRef.address).read(key.setElement) },
            nonAliasingMapper = { nonAliasingRef ->
                nonAliasingSetElements(
                    getId(nonAliasingRef)
                ).read(key.setElement)
            },
            symbolicMapper = { symbolicRef -> inputSetElements().read(symbolicRef to key.setElement) }
        )

    override fun write(
        key: USetEntryLValue<SetType, ElementSort, Reg>,
        value: UExpr<UBoolSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
    ) = foldHeapRefWithStaticAsSymbolic(
        ref = key.setRef,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { region, (concreteRef, guard) ->
            val id = UAllocatedSetId(concreteRef.address, elementSort, setType, elementInfo)
            val newCollection = region.getAllocatedSet(id)
                .write(key.setElement, value, guard, ownership)
            region.updateAllocatedSet(id, newCollection, ownership)
        },
        blockOnSymbolic = { region, (symbolicRef, guard) ->
            val newCollection = region.inputSetElements()
                .write(symbolicRef to key.setElement, value, guard, ownership)
            region.updateInputSet(newCollection)
        },
        blockOnNonAliasing = { region, (nonAliasingRef, guard) ->
            val id = UNonAliasingSetId(elementSort, setType, elementInfo, getId(nonAliasingRef))
            val newCollection = region.getNonAliasingSet(id)
                .write(key.setElement, value, guard, ownership)
            region.updateNonAliasingSet(id, newCollection, ownership)
        }
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
            val srcId = UAllocatedSetId(srcConcrete.address, elementSort, setType, elementInfo)
            val srcCollection = region.getAllocatedSet(srcId)

            val dstId = UAllocatedSetId(dstConcrete.address, elementSort, setType, elementInfo)
            val dstCollection = region.getAllocatedSet(dstId)

            val adapter = UAllocatedToAllocatedSymbolicSetUnionAdapter(srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateAllocatedSet(dstId, updated, ownership)
        },
        blockOnConcrete0NonAliasing1 = { region, srcConcrete, dstNonAliasing, guard ->
            val srcId = UAllocatedSetId(srcConcrete.address, elementSort, setType, elementInfo)
            val srcCollection = region.getAllocatedSet(srcId)

            val dstId = UNonAliasingSetId(elementSort, setType, elementInfo, getId(dstNonAliasing))
            val dstCollection = region.getNonAliasingSet(dstId)

            val adapter = UAllocatedToNonAliasingSymbolicSetUnionAdapter(dstNonAliasing, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateNonAliasingSet(dstId, updated, ownership)
        },
        blockOnConcrete0Symbolic1 = { region, srcConcrete, dstSymbolic, guard ->
            val srcId = UAllocatedSetId(srcConcrete.address, elementSort, setType, elementInfo)
            val srcCollection = region.getAllocatedSet(srcId)

            val dstCollection = region.inputSetElements()

            val adapter = UAllocatedToInputSymbolicSetUnionAdapter(dstSymbolic, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateInputSet(updated)
        },
        blockOnNonAliasing0Concrete1 = { region, srcNonAliasing, dstConcrete, guard ->
            val srcId = UNonAliasingSetId(elementSort, setType, elementInfo, getId(srcNonAliasing))
            val srcCollection = region.getNonAliasingSet(srcId)

            val dstId = UAllocatedSetId(dstConcrete.address, elementSort, setType, elementInfo)
            val dstCollection = region.getAllocatedSet(dstId)

            val adapter = UNonAliasingToAllocatedSymbolicSetUnionAdapter(srcNonAliasing, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateAllocatedSet(dstId, updated, ownership)
        },
        blockOnNonAliasing0NonAliasing1 = { region, srcNonAliasing, dstNonAliasing, guard ->
            val srcId = UNonAliasingSetId(elementSort, setType, elementInfo, getId(srcNonAliasing))
            val srcCollection = region.getNonAliasingSet(srcId)

            val dstId = UNonAliasingSetId(elementSort, setType, elementInfo, getId(dstNonAliasing))
            val dstCollection = region.getNonAliasingSet(dstId)

            val adapter =
                UNonAliasingToNonAliasingSymbolicSetUnionAdapter(srcNonAliasing, dstNonAliasing, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateNonAliasingSet(dstId, updated, ownership)
        },
        blockOnSymbolic0Concrete1 = { region, srcSymbolic, dstConcrete, guard ->
            val srcCollection = region.inputSetElements()

            val dstId = UAllocatedSetId(dstConcrete.address, elementSort, setType, elementInfo)
            val dstCollection = region.getAllocatedSet(dstId)

            val adapter = UInputToAllocatedSymbolicSetUnionAdapter(srcSymbolic, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateAllocatedSet(dstId, updated, ownership)
        },
        blockOnSymbolic0Symbolic1 = { region, srcSymbolic, dstSymbolic, guard ->
            val srcCollection = region.inputSetElements()
            val dstCollection = region.inputSetElements()

            val adapter = UInputToInputSymbolicSetUnionAdapter(srcSymbolic, dstSymbolic, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateInputSet(updated)
        },
        blockOnNonAliasing0Symbolic1 = { region, srcNonAliasing, dstSymbolic, guard ->
            val srcId = UNonAliasingSetId(elementSort, setType, elementInfo, getId(srcNonAliasing))
            val srcCollection = region.getNonAliasingSet(srcId)

            val dstCollection = region.inputSetElements()

            val adapter = UNonAliasingToInputSymbolicSetUnionAdapter(srcNonAliasing, dstSymbolic, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateInputSet(updated)
        },
        blockOnSymbolic0NonAliasing1 = { region, srcSymbolic, dstNonAliasing, guard ->
            val srcCollection = region.inputSetElements()

            val dstId = UNonAliasingSetId(elementSort, setType, elementInfo, getId(dstNonAliasing))
            val dstCollection = region.getNonAliasingSet(dstId)

            val adapter = UInputToNonAliasingSymbolicSetUnionAdapter(srcSymbolic, dstNonAliasing, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateNonAliasingSet(dstId, updated, ownership)
        },
    )

    override fun setEntries(ref: UHeapRef): UPrimitiveSetEntries<SetType, ElementSort, Reg> =
        foldHeapRefWithStaticAsSymbolic(
            ref = ref,
            initial = UPrimitiveSetEntries(),
            initialGuard = ref.uctx.trueExpr,
            blockOnConcrete = { entries, (concreteRef, _) ->
                val setElements = allocatedSetElements(concreteRef.address)
                val elements = USymbolicSetElementsCollector.collect(setElements.updates)
                elements.elements.forEach { elem ->
                    entries.add(USetEntryLValue(elementSort, concreteRef, elem, setType, elementInfo))
                }

                if (elements.isInput) {
                    entries.markAsInput()
                }

                entries
            },
            blockOnSymbolic = { entries, (symbolicRef, _) ->
                val setElements = inputSetElements()
                val elements = USymbolicSetElementsCollector.collect(setElements.updates)
                elements.elements.forEach { elem ->
                    entries.add(USetEntryLValue(elementSort, symbolicRef, elem.second, setType, elementInfo))
                }

                entries.markAsInput()

                entries
            },
            blockOnNonAliasing = { entries, (nonAliasingRef, _) ->
                val setElements = nonAliasingSetElements(getId(nonAliasingRef))
                val elements = USymbolicSetElementsCollector.collect(setElements.updates)
                elements.elements.forEach { elem ->
                    entries.add(USetEntryLValue(elementSort, nonAliasingRef, elem, setType, elementInfo))
                }

                entries.markAsInput()

                entries
            }
        )
}
