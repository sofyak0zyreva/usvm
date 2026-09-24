package org.usvm.collection.set.ref

import io.ksmt.cache.hash
import io.ksmt.utils.uncheckedCast
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.collection.set.USetRegionBuilder
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.collection.set.USymbolicSetElementRegion
import org.usvm.collection.set.USymbolicSetKeyInfo
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UTreeUpdates
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.memory.key.UHeapRefRegion
import org.usvm.regions.Region
import org.usvm.regions.emptyRegionTree
import org.usvm.uctx
import java.util.IdentityHashMap

abstract class USymbolicRefSetId<SetType, Element, Reg : Region<Reg>,
        out SetId : USymbolicRefSetId<SetType, Element, Reg, SetId>>(
    val setType: SetType,
    override val sort: UBoolSort
) : USymbolicCollectionId<Element, UBoolSort, SetId> {

    fun setRegionId(): URefSetRegionId<SetType> = URefSetRegionId(setType, sort)
}

class UAllocatedRefSetWithInputElementsId<SetType>(
    val setAddress: UConcreteHeapAddress,
    setType: SetType,
    sort: UBoolSort
) : USymbolicRefSetId<SetType, UHeapRef, UHeapRefRegion, UAllocatedRefSetWithInputElementsId<SetType>>(
    setType, sort
) {
    override fun instantiate(
        collection: USymbolicCollection<UAllocatedRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort>,
        key: UHeapRef,
        composer: UComposer<*, *>?
    ): UExpr<UBoolSort> {
        if (collection.updates.isEmpty()) {
            return key.uctx.falseExpr
        }

        if (composer == null) {
            return key.uctx.mkAllocatedRefSetWithInputElementsReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<UBoolSort>, guard: UBoolExpr) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: UHeapRef) =
        URefSetEntryLValue(key.uctx.mkConcreteHeapRef(setAddress), key, setType)

    override fun keyInfo() = UHeapRefKeyInfo

    override fun emptyRegion(): UAllocatedRefSetWithInputElements<SetType> {
        val updates = UTreeUpdates<UHeapRef, UHeapRefRegion, UBoolSort>(
            updates = emptyRegionTree(),
            UHeapRefKeyInfo
        )
        return USymbolicCollection(this, updates)
    }

    private val regionCache = IdentityHashMap<Any?, Any>()

    fun <R : Region<R>> region(
        collection: USymbolicCollection<UAllocatedRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort>,
        keyInfo: USymbolicCollectionKeyInfo<UHeapRef, R>
    ): R {
        val regionBuilder = USetRegionBuilder(
            baseRegion = keyInfo.bottomRegion(),
            keyInfo = keyInfo,
            topRegion = keyInfo.topRegion()
        )
        return collection.updates.accept(
            regionBuilder,
            regionCache.uncheckedCast()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedRefSetWithInputElementsId<*>

        if (setAddress != other.setAddress) return false
        if (setType != other.setType) return false

        return true
    }

    override fun hashCode(): Int = hash(setAddress, setType)
}

class UAllocatedRefSetWithNonAliasingElementsId<SetType>(
    val setAddress: UConcreteHeapAddress,
    val elementId: UNonAliasingHeapAddress,
    setType: SetType,
    sort: UBoolSort
) : USymbolicRefSetId<SetType, UHeapRef, UHeapRefRegion, UAllocatedRefSetWithNonAliasingElementsId<SetType>>(
    setType, sort
) {
    override fun instantiate(
        collection: USymbolicCollection<UAllocatedRefSetWithNonAliasingElementsId<SetType>, UHeapRef, UBoolSort>,
        key: UHeapRef,
        composer: UComposer<*, *>?
    ): UExpr<UBoolSort> {
        if (collection.updates.isEmpty()) {
            return key.uctx.falseExpr
        }

        if (composer == null) {
            return key.uctx.mkAllocatedRefSetWithNonAliasingElementsReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<UBoolSort>, guard: UBoolExpr) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: UHeapRef) =
        URefSetEntryLValue(key.uctx.mkConcreteHeapRef(setAddress), key, setType)

    override fun keyInfo() = UHeapRefKeyInfo

    override fun emptyRegion(): UAllocatedRefSetWithNonAliasingElements<SetType> {
        val updates = UTreeUpdates<UHeapRef, UHeapRefRegion, UBoolSort>(
            updates = emptyRegionTree(),
            UHeapRefKeyInfo
        )
        return USymbolicCollection(this, updates)
    }

    private val regionCache = IdentityHashMap<Any?, Any>()
    fun <R : Region<R>> region(
        collection: USymbolicCollection<UAllocatedRefSetWithNonAliasingElementsId<SetType>, UHeapRef, UBoolSort>,
        keyInfo: USymbolicCollectionKeyInfo<UHeapRef, R>
    ): R {
        val regionBuilder = USetRegionBuilder(
            baseRegion = keyInfo.bottomRegion(),
            keyInfo = keyInfo,
            topRegion = keyInfo.topRegion()
        )
        return collection.updates.accept(
            regionBuilder,
            regionCache.uncheckedCast()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedRefSetWithNonAliasingElementsId<*>

        if (setAddress != other.setAddress) return false
        if (elementId != other.elementId) return false
        if (setType != other.setType) return false

        return true
    }

    override fun hashCode(): Int = hash(setAddress, elementId, setType)
}


class UInputRefSetWithAllocatedElementsId<SetType>(
    val elementAddress: UConcreteHeapAddress,
    setType: SetType,
    sort: UBoolSort
) : USymbolicRefSetId<SetType, UHeapRef, UHeapRefRegion, UInputRefSetWithAllocatedElementsId<SetType>>(
    setType, sort
) {
    override fun instantiate(
        collection: USymbolicCollection<UInputRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort>,
        key: UHeapRef,
        composer: UComposer<*, *>?
    ): UExpr<UBoolSort> {
        if (collection.updates.isEmpty()) {
            return key.uctx.falseExpr
        }

        if (composer == null) {
            return key.uctx.mkInputRefSetWithAllocatedElementsReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<UBoolSort>, guard: UBoolExpr) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: UHeapRef) =
        URefSetEntryLValue(key, key.uctx.mkConcreteHeapRef(elementAddress), setType)

    override fun keyInfo() = UHeapRefKeyInfo

    override fun emptyRegion(): UInputRefSetWithAllocatedElements<SetType> {
        val updates = UTreeUpdates<UHeapRef, UHeapRefRegion, UBoolSort>(
            updates = emptyRegionTree(),
            UHeapRefKeyInfo
        )
        return USymbolicCollection(this, updates)
    }

    private val regionCache = IdentityHashMap<Any?, Any>()

    fun <R : Region<R>> region(
        collection: USymbolicCollection<UInputRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort>,
        keyInfo: USymbolicCollectionKeyInfo<UHeapRef, R>
    ): R {
        val regionBuilder = USetRegionBuilder(
            baseRegion = keyInfo.bottomRegion(),
            keyInfo = keyInfo,
            topRegion = keyInfo.topRegion()
        )
        return collection.updates.accept(
            regionBuilder,
            regionCache.uncheckedCast()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputRefSetWithAllocatedElementsId<*>

        if (elementAddress != other.elementAddress) return false
        if (setType != other.setType) return false

        return true
    }

    override fun hashCode(): Int = hash(elementAddress, setType)
}

class UNonAliasingRefSetWithAllocatedElementsId<SetType>(
    val setId: UNonAliasingHeapAddress,
    val elementAddress: UConcreteHeapAddress,
    setType: SetType,
    sort: UBoolSort
) : USymbolicRefSetId<SetType, UHeapRef, UHeapRefRegion, UNonAliasingRefSetWithAllocatedElementsId<SetType>>(
    setType, sort
) {
    override fun instantiate(
        collection: USymbolicCollection<UNonAliasingRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort>,
        key: UHeapRef,
        composer: UComposer<*, *>?
    ): UExpr<UBoolSort> {
        if (collection.updates.isEmpty()) {
            return key.uctx.falseExpr
        }

        if (composer == null) {
            return key.uctx.mkNonAliasingRefSetWithAllocatedElementsReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<UBoolSort>, guard: UBoolExpr) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: UHeapRef) =
        URefSetEntryLValue(key, key.uctx.mkConcreteHeapRef(elementAddress), setType)

    override fun keyInfo() = UHeapRefKeyInfo

    override fun emptyRegion(): UNonAliasingRefSetWithAllocatedElements<SetType> {
        val updates = UTreeUpdates<UHeapRef, UHeapRefRegion, UBoolSort>(
            updates = emptyRegionTree(),
            UHeapRefKeyInfo
        )
        return USymbolicCollection(this, updates)
    }

    private val regionCache = IdentityHashMap<Any?, Any>()

    fun <R : Region<R>> region(
        collection: USymbolicCollection<UNonAliasingRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort>,
        keyInfo: USymbolicCollectionKeyInfo<UHeapRef, R>
    ): R {
        val regionBuilder = USetRegionBuilder(
            baseRegion = keyInfo.bottomRegion(),
            keyInfo = keyInfo,
            topRegion = keyInfo.topRegion()
        )
        return collection.updates.accept(
            regionBuilder,
            regionCache.uncheckedCast()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UNonAliasingRefSetWithAllocatedElementsId<*>

        if (elementAddress != other.elementAddress) return false
        if (setId != other.setId) return false
        if (setType != other.setType) return false

        return true
    }

    override fun hashCode(): Int = hash(setId, elementAddress, setType)
}

class UNonAliasingRefSetWithNonAliasingElementsId<SetType>(
    val setId: UNonAliasingHeapAddress,
    val elementId: UNonAliasingHeapAddress,
    setType: SetType,
    sort: UBoolSort
) : USymbolicRefSetId<SetType, USymbolicSetElement<UAddressSort>, USymbolicSetElementRegion<UHeapRefRegion>,
        UNonAliasingRefSetWithNonAliasingElementsId<SetType>>(setType, sort) {
    override fun instantiate(
        collection: USymbolicCollection<UNonAliasingRefSetWithNonAliasingElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>,
        key: USymbolicSetElement<UAddressSort>,
        composer: UComposer<*, *>?
    ): UExpr<UBoolSort> {

        if (composer == null) {
            return sort.uctx.mkNonAliasingRefSetWithNonAliasingElementsReading(collection, key.first, key.second)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: USymbolicSetElement<UAddressSort>, value: UExpr<UBoolSort>, guard: UBoolExpr) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: USymbolicSetElement<UAddressSort>) =
        URefSetEntryLValue(key.first, key.second, setType)

    override fun keyInfo() = USymbolicSetKeyInfo(UHeapRefKeyInfo)
    @Suppress("UNUSED_PARAMETER")
    fun <R : Region<R>> region(
        collection: USymbolicCollection<UNonAliasingRefSetWithNonAliasingElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>,
        keyInfo: USymbolicCollectionKeyInfo<USymbolicSetElement<UAddressSort>, R>
    ): R = keyInfo.topRegion()
    override fun emptyRegion(): UNonAliasingRefSetWithNonAliasingElements<SetType> {
        val updates =
            UTreeUpdates<USymbolicSetElement<UAddressSort>, USymbolicSetElementRegion<UHeapRefRegion>, UBoolSort>(
                updates = emptyRegionTree(),
                keyInfo()
            )
        return USymbolicCollection(this, updates)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UNonAliasingRefSetWithNonAliasingElementsId<*>

        if (elementId != other.elementId) return false
        if (setId != other.setId) return false
        if (setType != other.setType) return false

        return true
    }

    override fun hashCode(): Int = hash(setId, elementId, setType)
}

class UInputRefSetWithInputElementsId<SetType>(
    setType: SetType,
    sort: UBoolSort
) : USymbolicRefSetId<SetType, USymbolicSetElement<UAddressSort>, USymbolicSetElementRegion<UHeapRefRegion>,
        UInputRefSetWithInputElementsId<SetType>>(setType, sort) {

    override fun instantiate(
        collection: USymbolicCollection<UInputRefSetWithInputElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>,
        key: USymbolicSetElement<UAddressSort>,
        composer: UComposer<*, *>?
    ): UExpr<UBoolSort> {
        if (composer == null) {
            return sort.uctx.mkInputRefSetWithInputElementsReading(collection, key.first, key.second)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: USymbolicSetElement<UAddressSort>,
        value: UExpr<UBoolSort>,
        guard: UBoolExpr
    ) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: USymbolicSetElement<UAddressSort>) =
        URefSetEntryLValue(key.first, key.second, setType)

    override fun keyInfo() = USymbolicSetKeyInfo(UHeapRefKeyInfo)

    override fun emptyRegion(): USymbolicCollection<UInputRefSetWithInputElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort> {
        val updates =
            UTreeUpdates<USymbolicSetElement<UAddressSort>, USymbolicSetElementRegion<UHeapRefRegion>, UBoolSort>(
                updates = emptyRegionTree(),
                keyInfo()
            )
        return USymbolicCollection(this, updates)
    }

    @Suppress("UNUSED_PARAMETER")
    fun <R : Region<R>> region(
        collection: USymbolicCollection<UInputRefSetWithInputElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>,
        keyInfo: USymbolicCollectionKeyInfo<USymbolicSetElement<UAddressSort>, R>
    ): R = keyInfo.topRegion()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputRefSetWithInputElementsId<*>

        return setType == other.setType
    }

    override fun hashCode(): Int = hash(setType)
}

//class UNonAliasingRefSetWithInputElementsId<SetType>(
//    val setId: UNonAliasingHeapAddress,
//    setType: SetType,
//    sort: UBoolSort
//) : USymbolicRefSetId<SetType, UHeapRef, UHeapRefRegion, UNonAliasingRefSetWithInputElementsId<SetType>>(
//    setType, sort
//) {
//    private val regionCache = IdentityHashMap<Any?, Any>()
//
//    fun <R : Region<R>> region(
//        collection: USymbolicCollection<UAllocatedRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort>,
//        keyInfo: USymbolicCollectionKeyInfo<UHeapRef, R>
//    ): R {
//        val regionBuilder = USetRegionBuilder(
//            baseRegion = keyInfo.bottomRegion(),
//            keyInfo = keyInfo,
//            topRegion = keyInfo.topRegion()
//        )
//        return collection.updates.accept(
//            regionBuilder,
//            regionCache.uncheckedCast()
//        )
//    }
//
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//
//        other as UAllocatedRefSetWithInputElementsId<*>
//
//        if (setAddress != other.setAddress) return false
//        if (setType != other.setType) return false
//
//        return true
//    }
//
//    override fun hashCode(): Int = hash(setAddress, setType)
//    override fun instantiate(
//        collection: USymbolicCollection<UNonAliasingRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort>,
//        key: UHeapRef,
//        composer: UComposer<*, *>?
//    ): UExpr<UBoolSort> {
//        if (composer == null) {
//            return key.uctx.mkNonAliasingRefSetWithInputElementsReading(collection, key)
//        }
//
//        val memory = composer.memory.toWritableMemory(composer.ownership)
//        collection.applyTo(memory, key, composer)
//        return memory.read(mkLValue(key))
//    }
//
//    private fun mkLValue(key: UHeapRef) =
//        URefSetEntryLValue(key.uctx.mkNonAliasingHeapRef(setId), key, setType)
//
//    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<UBoolSort>, guard: UBoolExpr) {
//        memory.write(mkLValue(key), value, guard)
//    }
//
//    override fun keyInfo(): USymbolicCollectionKeyInfo<UHeapRef, *> = UHeapRefKeyInfo
//
//    override fun emptyRegion(): UNonAliasingRefSetWithInputElements<SetType> {
//        val updates = UTreeUpdates<UHeapRef, UHeapRefRegion, UBoolSort>(
//            updates = emptyRegionTree(),
//            UHeapRefKeyInfo
//        )
//        return USymbolicCollection(this, updates)
//    }
//
//}

//class UInputRefSetWithNonAliasingElementsId<SetType>(
//    val elementId: UNonAliasingHeapAddress,
//    setType: SetType,
//    sort: UBoolSort
//) : USymbolicRefSetId<SetType, UHeapRef, UHeapRefRegion, UInputRefSetWithNonAliasingElementsId<SetType>>(
//    setType, sort
//) {
//    override fun instantiate(
//        collection: USymbolicCollection<UInputRefSetWithNonAliasingElementsId<SetType>, UHeapRef, UBoolSort>,
//        key: UHeapRef,
//        composer: UComposer<*, *>?
//    ): UExpr<UBoolSort> {
//
//        if (composer == null) {
//            return key.uctx.mkInputRefSetWithNonAliasingElementsReading(collection, key)
//        }
//
//        val memory = composer.memory.toWritableMemory(composer.ownership)
//        collection.applyTo(memory, key, composer)
//        return memory.read(mkLValue(key))
//    }
//
//    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<UBoolSort>, guard: UBoolExpr) {
//        memory.write(mkLValue(key), value, guard)
//    }
//
//    private fun mkLValue(key: UHeapRef) =
//        URefSetEntryLValue(key, key.uctx.mkNonAliasingHeapRef(elementId), setType)
//
//    override fun keyInfo() = UHeapRefKeyInfo
//
//    override fun emptyRegion(): UInputRefSetWithNonAliasingElements<SetType> {
//        val updates = UTreeUpdates<UHeapRef, UHeapRefRegion, UBoolSort>(
//            updates = emptyRegionTree(),
//            UHeapRefKeyInfo
//        )
//        return USymbolicCollection(this, updates)
//    }
//
////    private val regionCache = IdentityHashMap<Any?, Any>()
////
////    fun <R : Region<R>> region(
////        collection: USymbolicCollection<UInputRefSetWithNonAliasingElementsId<SetType>, UHeapRef, UBoolSort>,
////        keyInfo: USymbolicCollectionKeyInfo<UHeapRef, R>
////    ): R {
////        val regionBuilder = USetRegionBuilder(
////            baseRegion = keyInfo.bottomRegion(),
////            keyInfo = keyInfo,
////            topRegion = keyInfo.topRegion()
////        )
////        return collection.updates.accept(
////            regionBuilder,
////            regionCache.uncheckedCast()
////        )
////    }
////
////    override fun equals(other: Any?): Boolean {
////        if (this === other) return true
////        if (javaClass != other?.javaClass) return false
////
////        other as UInputRefSetWithNonAliasingElementsId<*>
////
////        if (elementAddress != other.elementAddress) return false
////        if (setType != other.setType) return false
////
////        return true
////    }
////
////    override fun hashCode(): Int = hash(elementAddress, setType)
//}




