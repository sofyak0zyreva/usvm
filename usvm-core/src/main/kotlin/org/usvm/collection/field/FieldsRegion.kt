package org.usvm.collection.field

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.UNonAliasingHeapRef
import org.usvm.USort
import org.usvm.collections.immutable.getOrPut
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.foldHeapRefWithStaticAsSymbolic
import org.usvm.memory.guardedWrite
import org.usvm.memory.mapWithStaticAsSymbolic
import org.usvm.sampleUValue
import org.usvm.uctx

data class UFieldLValue<Field, Sort : USort>(override val sort: Sort, val ref: UHeapRef, val field: Field) :
    ULValue<UFieldLValue<Field, Sort>, Sort> {
    override val memoryRegionId: UMemoryRegionId<UFieldLValue<Field, Sort>, Sort> =
        UFieldsRegionId(field, sort)

    override val key: UFieldLValue<Field, Sort>
        get() = this
}

data class UFieldsRegionId<Field, Sort : USort>(val field: Field, override val sort: Sort) :
    UMemoryRegionId<UFieldLValue<Field, Sort>, Sort> {

    override fun emptyRegion(): UMemoryRegion<UFieldLValue<Field, Sort>, Sort> =
        UFieldsMemoryRegion(sort, field)
}

typealias UInputFields<Field, Sort> = USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>

typealias UNonAliasingFields<Field, Sort> = USymbolicCollection<UNonAliasingFieldId<Field, Sort>, UHeapRef, Sort>
interface UFieldsRegion<Field, Sort : USort> : UMemoryRegion<UFieldLValue<Field, Sort>, Sort>

internal class UFieldsMemoryRegion<Field, Sort : USort>(
    private val sort: Sort,
    private val field: Field,
    private val allocatedFields: UPersistentHashMap<UConcreteHeapAddress, UExpr<Sort>> = persistentHashMapOf(),
    private var inputFields: UInputFields<Field, Sort>? = null,
    private var nonAliasingFields: UPersistentHashMap<UNonAliasingHeapAddress, UNonAliasingFields<Field, Sort>> =
        persistentHashMapOf(),
    private var staticFields: UPersistentHashMap<UNonAliasingHeapAddress, UNonAliasingFields<Field, Sort>> =
        persistentHashMapOf(),
) : UFieldsRegion<Field, Sort> {

    private fun updateAllocated(updated: UPersistentHashMap<UConcreteHeapAddress, UExpr<Sort>>) =
        UFieldsMemoryRegion(sort, field, updated, inputFields, nonAliasingFields, staticFields)

    private fun getStaticFields(ref: UFieldLValue<Field, Sort>): UNonAliasingFields<Field, Sort> {
        val mapId = -2
        val (updatedFields, collection) = staticFields.getOrPut(mapId, sort.uctx.defaultOwnership) {
            UNonAliasingFieldId<Field, Sort>(ref.field, ref.sort, mapId).emptyRegion()
        }
        staticFields = updatedFields
        return collection
    }
    private fun updateStatic(updated: UPersistentHashMap<UNonAliasingHeapAddress, UNonAliasingFields<Field, Sort>>) =
        UFieldsMemoryRegion(sort, field, allocatedFields, inputFields, nonAliasingFields, updated)

    private fun getNonAliasingFields(ref: UFieldLValue<Field, Sort>, nonAliasingId: UNonAliasingHeapAddress): UNonAliasingFields<Field, Sort> {
        val (updatedFields, collection) = nonAliasingFields.getOrPut(nonAliasingId, sort.uctx.defaultOwnership) {
            val concrete = staticFields[-2]
            if (concrete != null && concrete.collectionId.field == ref.field && concrete.collectionId.sort == ref.sort) {
                concrete
            } else {
                UNonAliasingFieldId<Field, Sort>(ref.field, ref.sort, nonAliasingId).emptyRegion()
            }
        }
        nonAliasingFields = updatedFields
        return collection
    }
    private fun updateNonAliasing(
        updated: UPersistentHashMap<UNonAliasingHeapAddress, UNonAliasingFields<Field, Sort>>,
    ) =
        UFieldsMemoryRegion(sort, field, allocatedFields, inputFields, updated, staticFields)

    private fun getInputFields(ref: UFieldLValue<Field, Sort>): UInputFields<Field, Sort> {
        if (inputFields == null) {
            inputFields = UInputFieldId(ref.field, ref.sort).emptyRegion()
        }
        return inputFields!!
    }

    private fun updateInput(updated: UInputFields<Field, Sort>) =
        UFieldsMemoryRegion(sort, field, allocatedFields, updated, nonAliasingFields, staticFields)

    override fun read(key: UFieldLValue<Field, Sort>): UExpr<Sort> = key.ref.mapWithStaticAsSymbolic(
        concreteMapper = { concreteRef -> allocatedFields[concreteRef.address] ?: sort.sampleUValue() },
        nonAliasingMapper = { nonAliasingRef ->
            getNonAliasingFields(key, getLocationId(nonAliasingRef)).read(nonAliasingRef)
        },
        symbolicMapper = { symbolicRef -> getInputFields(key).read(symbolicRef) }
    )

    override fun write(
        key: UFieldLValue<Field, Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): UMemoryRegion<UFieldLValue<Field, Sort>, Sort> = foldHeapRefWithStaticAsSymbolic(
        key.ref,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { region, (concreteRef, innerGuard) ->
            val newRegion = region.allocatedFields.guardedWrite(concreteRef.address, value, innerGuard, ownership) {
                sort.sampleUValue()
            }
            region.updateAllocated(newRegion)
        },
        blockOnNonAliasing = { region, (nonAliasingRef, innerGuard) ->
            val id = getLocationId(nonAliasingRef)
            val oldRegion = region.getNonAliasingFields(key, id)
            val newRegion = oldRegion.write(nonAliasingRef, value, innerGuard, ownership)
            val reg = region.updateNonAliasing(nonAliasingFields.put(id, newRegion, ownership))
            val ret = if (nonAliasingRef is UConcreteHeapRef) {
                val oldRegion2 = reg.getStaticFields(key)
                val newRegion2 = oldRegion2.write(nonAliasingRef, value, innerGuard, ownership)
                reg.updateStatic(staticFields.put(-2, newRegion2, ownership))
            } else {
                reg
            }
            ret
        },
        blockOnSymbolic = { region, (symbolicRef, innerGuard) ->
            val oldRegion = region.getInputFields(key)
            val newRegion = oldRegion.write(symbolicRef, value, innerGuard, ownership)
            region.updateInput(newRegion)
        }
    )
}

private fun getLocationId(ref: UHeapRef): Int = when (ref) {
    is UNonAliasingHeapRef -> ref.uctx.nonAliasingLocationOf(ref.id)
    else -> getId(ref)
}

fun getId(ref: UHeapRef): Int {
    val id = when (ref) {
        is UNonAliasingHeapRef -> ref.id
        is UConcreteHeapRef -> ref.id
        else -> throw IllegalStateException("Unsupported reference type: $ref")
    }
    return id
}
