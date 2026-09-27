package org.usvm.collection.field

import io.ksmt.cache.hash
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.USort
import org.usvm.memory.UFlatUpdates
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.uctx

interface USymbolicFieldId<Field, Key, Sort : USort, out FieldId : USymbolicFieldId<Field, Key, Sort, FieldId>> :
    USymbolicCollectionId<Key, Sort, FieldId> {
    val field: Field
}

/**
 * An id for a collection storing the specific [field].
 */
class UInputFieldId<Field, Sort : USort> internal constructor(
    override val field: Field,
    override val sort: Sort,
) : USymbolicFieldId<Field, UHeapRef, Sort, UInputFieldId<Field, Sort>> {

    override fun instantiate(
        collection: USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>,
        key: UHeapRef,
        composer: UComposer<*, *>?,
    ): UExpr<Sort> {
        if (composer == null) {
            return key.uctx.mkInputFieldReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<Sort>, guard: UBoolExpr) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: UHeapRef) = UFieldLValue(sort, key, field)

    override fun keyInfo() = UHeapRefKeyInfo

    override fun emptyRegion(): USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort> =
        USymbolicCollection(this, UFlatUpdates(keyInfo()))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputFieldId<*, *>

        if (field != other.field) return false
        if (sort != other.sort) return false

        return true
    }

    override fun hashCode(): Int = hash(field, sort)

    override fun toString(): String = "inputField<$field>()"
}

class UNonAliasingFieldId<Field, Sort : USort> internal constructor(
    override val field: Field,
    override val sort: Sort,
    val id: UNonAliasingHeapAddress,
) : USymbolicFieldId<Field, UHeapRef, Sort, UNonAliasingFieldId<Field, Sort>> {

    override fun instantiate(
        collection: USymbolicCollection<UNonAliasingFieldId<Field, Sort>, UHeapRef, Sort>,
        key: UHeapRef,
        composer: UComposer<*, *>?,
    ): UExpr<Sort> {
        if (composer == null) {
            return key.uctx.mkNonAliasingFieldReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<Sort>, guard: UBoolExpr) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: UHeapRef) = UFieldLValue(sort, key, field)

    override fun keyInfo() = UHeapRefKeyInfo

    override fun emptyRegion(): USymbolicCollection<UNonAliasingFieldId<Field, Sort>, UHeapRef, Sort> =
        USymbolicCollection(this, UFlatUpdates(keyInfo()))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UNonAliasingFieldId<*, *>

        if (field != other.field) return false
        if (sort != other.sort) return false

        return true
    }

    override fun hashCode(): Int = hash(field, sort)

    override fun toString(): String = "nonAliasingField<$field>()"
}
