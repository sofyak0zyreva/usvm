package org.usvm.collection.set.ref

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UCollectionReading
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.asTypedTransformer
import org.usvm.collection.array.makeNonAliasingIdForReading
import org.usvm.collection.field.getId
import org.usvm.collection.set.USymbolicSetElement

class UAllocatedRefSetWithInputElementsReading<SetType> internal constructor(
    ctx: UContext<*>,
    collection: UAllocatedRefSetWithInputElements<SetType>,
    val elementRef: UHeapRef,
) : UCollectionReading<UAllocatedRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort>(ctx, collection) {

    override fun accept(transformer: KTransformerBase): KExpr<UBoolSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<SetType, USort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { elementRef },
        )

    override fun internHashCode(): Int = hash(collection, elementRef)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(")
        printer.append(elementRef)
        printer.append(" in ")
        printer.append(collection.toString())
        printer.append(")")
    }
}

class UInputRefSetWithAllocatedElementsReading<SetType> internal constructor(
    ctx: UContext<*>,
    collection: UInputRefSetWithAllocatedElements<SetType>,
    val setRef: UHeapRef,
) : UCollectionReading<UInputRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort>(ctx, collection) {

    override fun accept(transformer: KTransformerBase): KExpr<UBoolSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<SetType, USort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { setRef },
        )

    override fun internHashCode(): Int = hash(collection, setRef)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(")
        printer.append(setRef)
        printer.append(" in ")
        printer.append(collection.toString())
        printer.append(")")
    }
}

class UInputRefSetWithInputElementsReading<SetType> internal constructor(
    ctx: UContext<*>,
    collection: UInputRefSetWithInputElements<SetType>,
    val setRef: UHeapRef,
    val elementRef: UHeapRef,
) : UCollectionReading<
    UInputRefSetWithInputElementsId<SetType>,
    USymbolicSetElement<UAddressSort>,
    UBoolSort
    >(ctx, collection) {
    init {
        require(setRef !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): KExpr<UBoolSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<SetType, USort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { setRef },
            { elementRef }
        )

    override fun internHashCode(): Int = hash(collection, setRef, elementRef)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(")
        printer.append(elementRef)
        printer.append(" in ")
        printer.append(collection.toString())
        printer.append("[")
        printer.append(setRef)
        printer.append("]")
        printer.append(")")
    }
}

class UNonAliasingRefSetWithNonAliasingElementsReading<SetType> internal constructor(
    ctx: UContext<*>,
    collection: UNonAliasingRefSetWithNonAliasingElements<SetType>,
    val setRef: UHeapRef,
    val elementRef: UHeapRef,
) : UCollectionReading<UNonAliasingRefSetWithNonAliasingElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>(
    ctx,
    collection
) {

    override val id: UNonAliasingHeapAddress = makeNonAliasingIdForReading(
        ctx,
        Pair(getId(setRef), Pair(getId(elementRef), "set"))
    )

    override fun accept(transformer: KTransformerBase): KExpr<UBoolSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<SetType, USort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { setRef },
            { elementRef },
        )

    override fun internHashCode(): Int = hash(collection)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(")
        printer.append(setRef)
        printer.append(" in ")
        printer.append(collection.toString())
        printer.append(")")
    }
}

class UAllocatedRefSetWithNonAliasingElementsReading<SetType> internal constructor(
    ctx: UContext<*>,
    collection: UAllocatedRefSetWithNonAliasingElements<SetType>,
    val elementAddress: UHeapRef,
) : UCollectionReading<UAllocatedRefSetWithNonAliasingElementsId<SetType>, UHeapRef, UBoolSort>(ctx, collection) {

    override val id: UNonAliasingHeapAddress = makeNonAliasingIdForReading(
        ctx,
        Pair(collection.collectionId.setAddress, getId(elementAddress))
    )

    override fun accept(transformer: KTransformerBase): KExpr<UBoolSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<SetType, USort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { elementAddress },
        )

    override fun internHashCode(): Int = hash(collection)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(")
        printer.append(elementAddress)
        printer.append(" in ")
        printer.append(collection.toString())
        printer.append(")")
    }
}

class UNonAliasingRefSetWithAllocatedElementsReading<SetType> internal constructor(
    ctx: UContext<*>,
    collection: UNonAliasingRefSetWithAllocatedElements<SetType>,
    val setAddress: UHeapRef,
) : UCollectionReading<UNonAliasingRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort>(ctx, collection) {

    override val id: UNonAliasingHeapAddress = makeNonAliasingIdForReading(
        ctx,
        Pair(getId(setAddress), collection.collectionId.elementAddress)
    )

    override fun accept(transformer: KTransformerBase): KExpr<UBoolSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<SetType, USort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { setAddress },
        )

    override fun internHashCode(): Int = hash(collection)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(")
        printer.append(setAddress)
        printer.append(" in ")
        printer.append(collection.toString())
        printer.append(")")
    }
}
