package org.usvm.collection.array

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.USort
import org.usvm.collection.field.getId
import org.usvm.collections.immutable.getOrPut
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.foldHeapRef2
import org.usvm.memory.foldHeapRefWithStaticAsSymbolic
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.memory.mapWithStaticAsSymbolic
import org.usvm.uctx

data class UArrayIndexLValue<ArrayType, Sort : USort, USizeSort : USort>(
    override val sort: Sort,
    val ref: UHeapRef,
    val index: UExpr<USizeSort>,
    val arrayType: ArrayType,
) : ULValue<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {

    override val memoryRegionId: UMemoryRegionId<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> =
        UArrayRegionId(arrayType, sort)

    override val key: UArrayIndexLValue<ArrayType, Sort, USizeSort>
        get() = this
}

data class UArrayRegionId<ArrayType, Sort : USort, USizeSort : USort>(val arrayType: ArrayType, override val sort: Sort) :
    UMemoryRegionId<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {

    override fun emptyRegion(): UMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> =
        UArrayMemoryRegion()
}

typealias UAllocatedArray<ArrayType, Sort, USizeSort> = USymbolicCollection<UAllocatedArrayId<ArrayType, Sort, USizeSort>, UExpr<USizeSort>, Sort>
typealias UNonAliasingArray<ArrayType, Sort, USizeSort> = USymbolicCollection<UNonAliasingArrayId<ArrayType, Sort, USizeSort>, UExpr<USizeSort>, Sort>
typealias UInputArray<ArrayType, Sort, USizeSort> = USymbolicCollection<UInputArrayId<ArrayType, Sort, USizeSort>, USymbolicArrayIndex<USizeSort>, Sort>

interface UArrayRegion<ArrayType, Sort : USort, USizeSort : USort> : UMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {
    fun memcpy(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        fromSrcIdx: UExpr<USizeSort>,
        fromDstIdx: UExpr<USizeSort>,
        toDstIdx: UExpr<USizeSort>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): UArrayRegion<ArrayType, Sort, USizeSort>

    fun initializeAllocatedArray(
        address: UConcreteHeapAddress,
        arrayType: ArrayType,
        sort: Sort,
        content: List<UExpr<Sort>>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): UArrayRegion<ArrayType, Sort, USizeSort>
}

internal class UArrayMemoryRegion<ArrayType, Sort : USort, USizeSort : USort>(
    private var allocatedArrays: UPersistentHashMap<UConcreteHeapAddress, UAllocatedArray<ArrayType, Sort, USizeSort>> =
        persistentHashMapOf(),
    private var inputArray: UInputArray<ArrayType, Sort, USizeSort>? = null,
    private var nonAliasingArrays:
    UPersistentHashMap<UNonAliasingHeapAddress, UNonAliasingArray<ArrayType, Sort, USizeSort>> = persistentHashMapOf(),
    private var staticArrays: UPersistentHashMap<UNonAliasingHeapAddress, UNonAliasingArray<ArrayType, Sort, USizeSort>> =
        persistentHashMapOf(),
) : UArrayRegion<ArrayType, Sort, USizeSort> {

    private fun getAllocatedArray(
        arrayType: ArrayType,
        sort: Sort,
        address: UConcreteHeapAddress,
    ): UAllocatedArray<ArrayType, Sort, USizeSort> {
        val (updatedArrays, collection) = allocatedArrays.getOrPut(address, sort.uctx.defaultOwnership) {
            UAllocatedArrayId<_, _, USizeSort>(arrayType, sort, address).emptyRegion()
        }
        allocatedArrays = updatedArrays
        return collection
    }

    private fun updateAllocatedArray(
        ref: UConcreteHeapAddress,
        updated: UAllocatedArray<ArrayType, Sort, USizeSort>,
        ownership: MutabilityOwnership,
    ) = UArrayMemoryRegion(allocatedArrays.put(ref, updated, ownership), inputArray, nonAliasingArrays, staticArrays)

    private fun getStaticArray(
        arrayType: ArrayType,
        sort: Sort,
    ): UNonAliasingArray<ArrayType, Sort, USizeSort> {
        val mapId = -2
        val (updatedArrays, collection) = staticArrays.getOrPut(mapId, sort.uctx.defaultOwnership) {
            UNonAliasingArrayId<_, _, USizeSort>(arrayType, sort, mapId).emptyRegion()
        }
        staticArrays = updatedArrays
        return collection
    }

    private fun updateStatic(
        updated: UNonAliasingArray<ArrayType, Sort, USizeSort>,
        ownership: MutabilityOwnership,
    ) = UArrayMemoryRegion(allocatedArrays, inputArray, nonAliasingArrays, staticArrays.put(-2, updated, ownership))

    private fun getNonAliasingArray(
        arrayType: ArrayType,
        sort: Sort,
        id: UNonAliasingHeapAddress,
    ): UNonAliasingArray<ArrayType, Sort, USizeSort> {
        val (updatedArrays, collection) = nonAliasingArrays.getOrPut(id, sort.uctx.defaultOwnership) {
            UNonAliasingArrayId<_, _, USizeSort>(arrayType, sort, id).emptyRegion()
        }
        nonAliasingArrays = updatedArrays
        return collection
    }
    private fun updateNonAliasingArray(
        ref: UNonAliasingHeapAddress,
        updated: UNonAliasingArray<ArrayType, Sort, USizeSort>,
        ownership: MutabilityOwnership,
    ) = UArrayMemoryRegion(allocatedArrays, inputArray, nonAliasingArrays.put(ref, updated, ownership), staticArrays)

    private fun getInputArray(arrayType: ArrayType, sort: Sort): UInputArray<ArrayType, Sort, USizeSort> {
        if (inputArray == null) {
            inputArray = UInputArrayId<_, _, USizeSort>(arrayType, sort).emptyRegion()
        }
        return inputArray!!
    }

    private fun updateInput(updated: UInputArray<ArrayType, Sort, USizeSort>) =
        UArrayMemoryRegion(allocatedArrays, updated, nonAliasingArrays, staticArrays)

    override fun read(key: UArrayIndexLValue<ArrayType, Sort, USizeSort>): UExpr<Sort> {
        val x = key.ref.mapWithStaticAsSymbolic(
            concreteMapper = { concreteRef ->
                getAllocatedArray(
                    key.arrayType,
                    key.sort,
                    concreteRef.address
                ).read(key.index)
            },
            nonAliasingMapper = { nonAliasingRef ->
                getNonAliasingArray(
                    key.arrayType,
                    key.sort,
                    getId(nonAliasingRef)
                ).read(key.index)
            },
            symbolicMapper = { symbolicRef -> getInputArray(key.arrayType, key.sort).read(symbolicRef to key.index) }
        )
        return x
    }

    override fun write(
        key: UArrayIndexLValue<ArrayType, Sort, USizeSort>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): UMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> = foldHeapRefWithStaticAsSymbolic(
        key.ref,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { region, (concreteRef, innerGuard) ->
            val oldRegion = region.getAllocatedArray(key.arrayType, key.sort, concreteRef.address)
            val newRegion = oldRegion.write(key.index, value, innerGuard, ownership)
            region.updateAllocatedArray(concreteRef.address, newRegion, ownership)
        },
        blockOnNonAliasing = { region, (nonAliasingRef, innerGuard) ->
            val id = getId(nonAliasingRef)
            val oldRegion = region.getNonAliasingArray(key.arrayType, key.sort, id)
            val newRegion = oldRegion.write(key.index, value, innerGuard, ownership)
            val reg = region.updateNonAliasingArray(id, newRegion, ownership)
            val ret = if (nonAliasingRef is UConcreteHeapRef) {
                val oldRegion2 = reg.getStaticArray(key.arrayType, key.sort,)
                val newRegion2 = oldRegion2.write(key.index, value, innerGuard, ownership)
                reg.updateStatic(newRegion2, ownership)
            } else {
                reg
            }
            ret
        },
        blockOnSymbolic = { region, (symbolicRef, innerGuard) ->
            val oldRegion = region.getInputArray(key.arrayType, key.sort)
            val newRegion = oldRegion.write(symbolicRef to key.index, value, innerGuard, ownership)
            region.updateInput(newRegion)
        }
    )

    override fun memcpy(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        fromSrcIdx: UExpr<USizeSort>,
        fromDstIdx: UExpr<USizeSort>,
        toDstIdx: UExpr<USizeSort>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ) = foldHeapRef2(
        ref0 = srcRef,
        ref1 = dstRef,
        initial = this,
        initialGuard = operationGuard,
        blockOnConcrete0Concrete1 = { region, srcConcrete, dstConcrete, guard ->
            val srcCollection = region.getAllocatedArray(type, elementSort, srcConcrete.address)
            val dstCollection = region.getAllocatedArray(type, elementSort, dstConcrete.address)
            val adapter = USymbolicArrayAllocatedToAllocatedCopyAdapter(
                fromSrcIdx,
                fromDstIdx,
                toDstIdx,
                USizeExprKeyInfo()
            )
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateAllocatedArray(dstConcrete.address, newDstCollection, ownership)
        },
        blockOnConcrete0NonAliasing1 = { region, srcConcrete, dstNonAliasing, guard ->
            val id = getId(dstNonAliasing)
            val srcCollection = region.getAllocatedArray(type, elementSort, srcConcrete.address)
            val dstCollection = region.getNonAliasingArray(type, elementSort, id)
            val adapter = USymbolicArrayAllocatedToNonAliasingCopyAdapter(
                fromSrcIdx,
                fromDstIdx,
                toDstIdx,
                USizeExprKeyInfo()
            )
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateNonAliasingArray(id, newDstCollection, ownership)
        },
        blockOnConcrete0Symbolic1 = { region, srcConcrete, dstSymbolic, guard ->
            val srcCollection = region.getAllocatedArray(type, elementSort, srcConcrete.address)
            val dstCollection = region.getInputArray(type, elementSort)
            val adapter = USymbolicArrayAllocatedToInputCopyAdapter(
                fromSrcIdx,
                dstSymbolic to fromDstIdx,
                dstSymbolic to toDstIdx,
                USymbolicArrayIndexKeyInfo()
            )
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateInput(newDstCollection)
        },
        blockOnNonAliasing0Concrete1 = { region, srcNonAliasing, dstConcrete, guard ->
            val id = getId(srcNonAliasing)
            val srcCollection = region.getNonAliasingArray(type, elementSort, id)
            val dstCollection = region.getAllocatedArray(type, elementSort, dstConcrete.address)
            val adapter = USymbolicArrayNonAliasingToAllocatedCopyAdapter(
                fromSrcIdx,
                fromDstIdx,
                toDstIdx,
                USizeExprKeyInfo()
            )
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateAllocatedArray(dstConcrete.address, newDstCollection, ownership)
        },
        blockOnNonAliasing0NonAliasing1 = { region, srcNonAliasing, dstNonAliasing, guard ->
            val srcId = getId(srcNonAliasing)
            val dstId = getId(dstNonAliasing)
            val srcCollection = region.getNonAliasingArray(type, elementSort, srcId)
            val dstCollection = region.getNonAliasingArray(type, elementSort, dstId)
            val adapter = USymbolicArrayNonAliasingToNonAliasingCopyAdapter(
                fromSrcIdx,
                fromDstIdx,
                toDstIdx,
                USizeExprKeyInfo()
            )
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateNonAliasingArray(dstId, newDstCollection, ownership)
        },
        blockOnSymbolic0Concrete1 = { region, srcSymbolic, dstConcrete, guard ->
            val srcCollection = region.getInputArray(type, elementSort)
            val dstCollection = region.getAllocatedArray(type, elementSort, dstConcrete.address)
            val adapter = USymbolicArrayInputToAllocatedCopyAdapter(
                srcSymbolic to fromSrcIdx,
                fromDstIdx,
                toDstIdx,
                USizeExprKeyInfo()
            )
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateAllocatedArray(dstConcrete.address, newDstCollection, ownership)
        },
        blockOnSymbolic0Symbolic1 = { region, srcSymbolic, dstSymbolic, guard ->
            val srcCollection = region.getInputArray(type, elementSort)
            val dstCollection = region.getInputArray(type, elementSort)
            val adapter = USymbolicArrayInputToInputCopyAdapter(
                srcSymbolic to fromSrcIdx,
                dstSymbolic to fromDstIdx,
                dstSymbolic to toDstIdx,
                USymbolicArrayIndexKeyInfo()
            )
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateInput(newDstCollection)
        }
    )

    override fun initializeAllocatedArray(
        address: UConcreteHeapAddress,
        arrayType: ArrayType,
        sort: Sort,
        content: List<UExpr<Sort>>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): UArrayMemoryRegion<ArrayType, Sort, USizeSort> {
        val arrayId = UAllocatedArrayId<_, _, USizeSort>(arrayType, sort, address)
        val newCollection = arrayId.initializedArray(content, operationGuard)
        return UArrayMemoryRegion(allocatedArrays.put(address, newCollection, ownership), inputArray)
    }
}
