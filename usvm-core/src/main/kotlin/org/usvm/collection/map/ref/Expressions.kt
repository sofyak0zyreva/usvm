package org.usvm.collection.map.ref

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.usvm.UAddressSort
import org.usvm.UCollectionReading
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.asTypedTransformer
import org.usvm.collection.array.makeNonAliasingIdForReading
import org.usvm.collection.map.USymbolicMapKey

class UAllocatedRefMapWithInputKeysReading<MapType, Sort : USort> internal constructor(
    ctx: UContext<*>,
    collection: UAllocatedRefMapWithInputKeys<MapType, Sort>,
    val keyRef: UHeapRef,
) : UCollectionReading<UAllocatedRefMapWithInputKeysId<MapType, Sort>, UHeapRef, Sort>(ctx, collection) {

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<MapType, USort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { keyRef },
        )

    override fun internHashCode(): Int = hash(collection, keyRef)

    override fun print(printer: ExpressionPrinter) {
        printer.append(collection.toString())
        printer.append("[")
        printer.append(keyRef)
        printer.append("]")
    }
}

class UAllocatedRefMapWithNonAliasingKeysReading<MapType, Sort : USort> internal constructor(
    ctx: UContext<*>,
    collection: UAllocatedRefMapWithNonAliasingKeys<MapType, Sort>,
    val keyRef: UHeapRef,
) : UCollectionReading<UAllocatedRefMapWithNonAliasingKeysId<MapType, Sort>, UHeapRef, Sort>(ctx, collection) {

    override val id: UNonAliasingHeapAddress = makeNonAliasingIdForReading(
        ctx,
        Pair(collection.collectionId.mapAddress, collection.collectionId.keyAddress),
        locationKey = Triple(
            ctx.nonAliasingLocationOf(collection.collectionId.mapAddress),
            ctx.nonAliasingLocationOf(collection.collectionId.keyAddress),
            "refMap",
        ),
        collection = collection,
    )

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<MapType, USort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { keyRef },
        )

    override fun internHashCode(): Int = hash(collection, keyRef)

    override fun print(printer: ExpressionPrinter) {
        printer.append(collection.toString())
        printer.append("[")
        printer.append(keyRef)
        printer.append("]")
    }
}

class UInputRefMapWithAllocatedKeysReading<MapType, Sort : USort> internal constructor(
    ctx: UContext<*>,
    collection: UInputRefMapWithAllocatedKeys<MapType, Sort>,
    val mapRef: UHeapRef,
) : UCollectionReading<UInputRefMapWithAllocatedKeysId<MapType, Sort>, UHeapRef, Sort>(ctx, collection) {

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<MapType, USort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { mapRef },
        )

    override fun internHashCode(): Int = hash(collection, mapRef)

    override fun print(printer: ExpressionPrinter) {
        printer.append(collection.toString())
        printer.append("(")
        printer.append(mapRef)
        printer.append(")")
    }
}

class UNonAliasingRefMapWithAllocatedKeysReading<MapType, Sort : USort> internal constructor(
    ctx: UContext<*>,
    collection: UNonAliasingRefMapWithAllocatedKeys<MapType, Sort>,
    val mapRef: UHeapRef,
) : UCollectionReading<UNonAliasingRefMapWithAllocatedKeysId<MapType, Sort>, UHeapRef, Sort>(ctx, collection) {

    override val id: UNonAliasingHeapAddress = makeNonAliasingIdForReading(
        ctx,
        Pair(collection.collectionId.mapAddress, collection.collectionId.keyAddress),
        locationKey = Triple(
            ctx.nonAliasingLocationOf(collection.collectionId.mapAddress),
            ctx.nonAliasingLocationOf(collection.collectionId.keyAddress),
            "refMap",
        ),
        collection = collection,
    )

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<MapType, USort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { mapRef },
        )

    override fun internHashCode(): Int = hash(collection, mapRef)

    override fun print(printer: ExpressionPrinter) {
        printer.append(collection.toString())
        printer.append("(")
        printer.append(mapRef)
        printer.append(")")
    }
}

class UInputRefMapWithInputKeysReading<MapType, Sort : USort> internal constructor(
    ctx: UContext<*>,
    collection: UInputRefMap<MapType, Sort>,
    val mapRef: UHeapRef,
    val keyRef: UHeapRef,
) : UCollectionReading<
    UInputRefMapWithInputKeysId<MapType, Sort>,
    USymbolicMapKey<UAddressSort>,
    Sort
    >(ctx, collection) {
    init {
        require(mapRef !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<MapType, USort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { mapRef },
            { keyRef }
        )

    override fun internHashCode(): Int = hash(collection, mapRef, keyRef)

    override fun print(printer: ExpressionPrinter) {
        printer.append(collection.toString())
        printer.append("(")
        printer.append(mapRef)
        printer.append(")")
        printer.append("[")
        printer.append(keyRef)
        printer.append("]")
    }
}

class UNonAliasingRefMapWithNonAliasingKeysReading<MapType, Sort : USort> internal constructor(
    ctx: UContext<*>,
    collection: UNonAliasingRefMapWithNonAliasingKeys<MapType, Sort>,
    val mapRef: UHeapRef,
    val keyRef: UHeapRef,
) : UCollectionReading<UNonAliasingRefMapWithNonAliasingKeysId<MapType, Sort>, USymbolicMapKey<UAddressSort>, Sort>(
    ctx,
    collection
) {

    override val id: UNonAliasingHeapAddress = makeNonAliasingIdForReading(
        ctx,
        Pair(collection.collectionId.mapAddress, Pair(collection.collectionId.keyAddress, "map")),
        locationKey = Triple(
            ctx.nonAliasingLocationOf(collection.collectionId.mapAddress),
            ctx.nonAliasingLocationOf(collection.collectionId.keyAddress),
            "refMap",
        ),
        collection = collection,
    )

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<MapType, USort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { mapRef },
            { keyRef }
        )

    override fun internHashCode(): Int = hash(collection, mapRef)

    override fun print(printer: ExpressionPrinter) {
        printer.append(collection.toString())
        printer.append("(")
        printer.append(mapRef)
        printer.append(")")
        printer.append("[")
        printer.append(keyRef)
        printer.append("]")
    }
}
