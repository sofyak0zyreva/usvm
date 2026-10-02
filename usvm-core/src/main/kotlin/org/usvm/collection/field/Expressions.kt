package org.usvm.collection.field

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.usvm.UCollectionReading
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.collection.array.makeNonAliasingIdForReading

class UInputFieldReading<Field, Sort : USort> internal constructor(
    ctx: UContext<*>,
    collection: UInputFields<Field, Sort>,
    val address: UHeapRef,
) : UCollectionReading<UInputFieldId<Field, Sort>, UHeapRef, Sort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { collection }, { address })

    override fun internHashCode(): Int = hash(collection, address)

    override fun print(printer: ExpressionPrinter) {
        printer.append(collection.toString())
        printer.append("[")
        printer.append(address)
        printer.append("]")
    }
}

class UNonAliasingFieldReading<Field, Sort : USort> internal constructor(
    ctx: UContext<*>,
    collection: UNonAliasingFields<Field, Sort>,
    val address: UHeapRef,
) : UCollectionReading<UNonAliasingFieldId<Field, Sort>, UHeapRef, Sort>(ctx, collection) {

    override val id: UNonAliasingHeapAddress =
        makeNonAliasingIdForReading(
            ctx,
            Pair(collection.collectionId.id, Pair(collection.collectionId.field, address)),
            locationKey = Pair(ctx.nonAliasingLocationOf(collection.collectionId.id), collection.collectionId.field),
            collection = collection,
        )

    init {
        require(address !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { collection }, { address })

    override fun internHashCode(): Int = hash(collection, address)

    override fun print(printer: ExpressionPrinter) {
        printer.append(collection.toString())
        printer.append("[")
        printer.append(address)
        printer.append("]")
    }
}
