package org.usvm.solver

import io.ksmt.decl.KDecl
import io.ksmt.expr.KApp
import io.ksmt.expr.KExpr
import io.ksmt.sort.KArraySort
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.mkConst
import io.ksmt.utils.uncheckedCast
import org.usvm.*
import org.usvm.collection.array.UAllocatedArrayReading
import org.usvm.collection.array.UArrayRegionDecoder
import org.usvm.collection.array.UArrayRegionId
import org.usvm.collection.array.UInputArrayReading
import org.usvm.collection.array.UNonAliasingArrayReading
import org.usvm.collection.array.USymbolicArrayId
import org.usvm.collection.array.length.UArrayLengthRegionDecoder
import org.usvm.collection.array.length.UArrayLengthsRegionId
import org.usvm.collection.array.length.UInputArrayLengthReading
import org.usvm.collection.array.length.USymbolicArrayLengthId
import org.usvm.collection.field.UFieldRegionDecoder
import org.usvm.collection.field.UFieldsRegionId
import org.usvm.collection.field.UInputFieldReading
import org.usvm.collection.field.UNonAliasingFieldReading
import org.usvm.collection.field.USymbolicFieldId
import org.usvm.collection.map.length.UInputMapLengthReading
import org.usvm.collection.map.length.UMapLengthRegionDecoder
import org.usvm.collection.map.length.UMapLengthRegionId
import org.usvm.collection.map.length.USymbolicMapLengthId
import org.usvm.collection.map.primitive.UAllocatedMapReading
import org.usvm.collection.map.primitive.UInputMapReading
import org.usvm.collection.map.primitive.UMapRegionDecoder
import org.usvm.collection.map.primitive.UMapRegionId
import org.usvm.collection.map.primitive.UNonAliasingMapReading
import org.usvm.collection.map.primitive.USymbolicMapId
import org.usvm.collection.map.ref.UAllocatedRefMapWithInputKeysReading
import org.usvm.collection.map.ref.UAllocatedRefMapWithNonAliasingKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithAllocatedKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithInputKeysReading
import org.usvm.collection.map.ref.UNonAliasingRefMapWithAllocatedKeysReading
import org.usvm.collection.map.ref.UNonAliasingRefMapWithNonAliasingKeysReading
import org.usvm.collection.map.ref.URefMapRegionDecoder
import org.usvm.collection.map.ref.URefMapRegionId
import org.usvm.collection.map.ref.USymbolicRefMapId
import org.usvm.collection.set.primitive.UAllocatedSetReading
import org.usvm.collection.set.primitive.UInputSetReading
import org.usvm.collection.set.primitive.UNonAliasingSetReading
import org.usvm.collection.set.primitive.USetRegionDecoder
import org.usvm.collection.set.primitive.USymbolicSetId
import org.usvm.collection.set.ref.UAllocatedRefSetWithInputElementsReading
import org.usvm.collection.set.ref.UAllocatedRefSetWithNonAliasingElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithAllocatedElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithInputElementsReading
import org.usvm.collection.set.ref.UNonAliasingRefSetWithAllocatedElementsReading
import org.usvm.collection.set.ref.UNonAliasingRefSetWithNonAliasingElementsReading
import org.usvm.collection.set.ref.URefSetRegionDecoder
import org.usvm.collection.set.ref.USymbolicRefSetId
import org.usvm.memory.UMemoryRegionId
import org.usvm.regions.Region
import java.util.concurrent.ConcurrentHashMap

/**
 * Translates custom [UExpr] to a [KExpr]. Region readings are translated via [URegionTranslator]s.
 * Base version cache everything, but doesn't track translated expressions like register readings, mock symbols, etc.
 *
 * To show semantics of the translator, we use [KExpr] as return values, though [UExpr] is a typealias for it.
 */
open class UExprTranslator<Type, USizeSort : USort>(
    override val ctx: UContext<USizeSort>,
) : UExprTransformer<Type, USizeSort>(ctx) {
    open fun <Sort : USort> translate(expr: UExpr<Sort>): KExpr<Sort> = apply(expr)

    private val nonAliasingAxiomsByTerm = hashMapOf<KExpr<*>, MutableList<UBoolExpr>>()

    fun nonAliasingAxiomsFor(assertions: Collection<KExpr<KBoolSort>>): List<UBoolExpr> {
        if (nonAliasingAxiomsByTerm.isEmpty()) return emptyList()

        val axioms = linkedSetOf<UBoolExpr>()
        val visited = hashSetOf<KExpr<*>>()
        val stack = ArrayDeque<KExpr<*>>(assertions)
        while (stack.isNotEmpty()) {
            val expr = stack.removeLast()
            if (!visited.add(expr)) continue
            nonAliasingAxiomsByTerm[expr]?.forEach { axiom ->
                if (axioms.add(axiom)) stack.addLast(axiom)
            }
            if (expr is KApp<*, *>) stack.addAll(expr.args)
        }
        return axioms.toList()
    }

    private val naOriginIndexDecl by lazy {
        ctx.mkFuncDecl("na_origin_index", ctx.sizeSort, listOf(ctx.addressSort))
    }

    private val naOriginArrayDecl by lazy {
        ctx.mkFuncDecl("na_origin_array", ctx.bv32Sort, listOf(ctx.addressSort))
    }

    private val naOriginOwnerDecl by lazy {
        ctx.mkFuncDecl("na_origin_owner", ctx.addressSort, listOf(ctx.addressSort))
    }

    fun addNonAliasingArrayElementAxiom(
        arrayId: Int,
        baseArray: KExpr<KArraySort<USizeSort, UAddressSort>>,
        index: KExpr<USizeSort>,
        owner: KExpr<UAddressSort>? = null,
        trigger: KExpr<*>? = null,
    ) = with(ctx) {
        val elem = mkArraySelect(baseArray, index)
        val isNull = mkEqNoSimplify(elem, translate(nullRef))
        val sameIndex = mkEq(mkApp(naOriginIndexDecl, listOf(elem)), index)
        val sameArray = mkEq(mkApp(naOriginArrayDecl, listOf(elem)), mkBv(arrayId))
        val sameOwner = owner?.let { mkEq(mkApp(naOriginOwnerDecl, listOf(elem)), it) } ?: trueExpr
        val axiom = mkOr(isNull, mkAnd(sameIndex, sameArray, sameOwner))
        nonAliasingAxiomsByTerm.getOrPut(elem) { mutableListOf() }.add(axiom)
        if (trigger != null && trigger != elem) {
            nonAliasingAxiomsByTerm.getOrPut(trigger) { mutableListOf() }.add(axiom)
        }
    }

    override fun <Sort : USort> transform(expr: URegisterReading<Sort>): KExpr<Sort> {
        val registerConst = expr.sort.mkConst("r${expr.idx}_${expr.sort}")
        return registerConst
    }

    override fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): KExpr<Sort> {
        val const = expr.sort.mkConst("m${expr.method}_${expr.callIndex}_${expr.sort}")
        return const
    }

    override fun <Sort : USort> transform(
        expr: UTrackedSymbol<Sort>,
    ): UExpr<Sort> = expr.sort.mkConst(expr.name)

    override fun transform(expr: UNullRef): KExpr<UAddressSort> {
        val const = expr.sort.mkConst("null")
        return const
    }

    override fun transform(expr: UConcreteHeapRef): KExpr<UAddressSort> {
        require(isStaticHeapRef(expr)) { "Unexpected ref: $expr" }

        return ctx.mkUninterpretedSortValue(ctx.addressSort, expr.address)
    }

    override fun transform(expr: UNonAliasingHeapRef): UExpr<UAddressSort> {
        val symbol = checkNotNull(expr.symbol) { "Non-aliasing ref ${expr.id} has no symbol" }

        return transformExprAfterTransformed(expr, symbol) { it }
    }

    private val _declToIsExpr = mutableMapOf<KDecl<UBoolSort>, UIsExpr<Type>>()
    val declToIsExpr: Map<KDecl<UBoolSort>, UIsExpr<Type>> get() = _declToIsExpr

    override fun transform(expr: UIsSubtypeExpr<Type>): KExpr<KBoolSort> {
        require(expr.ref is USymbolicHeapRef) { "Unexpected ref: ${expr.ref}" }

        val const = expr.sort.mkConst("isSubtype#${_declToIsExpr.size}")
        // we need to track declarations to pass them to the type solver in the DPLL(T) procedure
        _declToIsExpr[const.decl] = expr
        return const
    }

    override fun transform(expr: UIsSupertypeExpr<Type>): KExpr<KBoolSort> {
        require(expr.ref is USymbolicHeapRef) { "Unexpected ref: ${expr.ref}" }

        val const = expr.sort.mkConst("isSupertype#${_declToIsExpr.size}")
        // we need to track declarations to pass them to the type solver in the DPLL(T) procedure
        _declToIsExpr[const.decl] = expr
        return const
    }

    override fun transform(expr: UInputArrayLengthReading<Type, USizeSort>): KExpr<USizeSort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            val translator = arrayLengthRegionDecoder(expr.collection.collectionId)
                .inputArrayLengthRegionTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, address)
        }

    override fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort, USizeSort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.address, expr.index) { address, index ->
            val translator = arrayRegionDecoder(expr.collection.collectionId)
                .inputArrayRegionTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, address to index)
        }

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort, USizeSort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.index) { index ->
            val translator = arrayRegionDecoder(expr.collection.collectionId)
                .allocatedArrayRegionTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, index)
        }

    override fun <Field, Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            val translator = fieldsRegionDecoder(expr.collection.collectionId)
                .inputFieldRegionTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, address)
        }

    override fun <Field, Sort : USort> transform(expr: UNonAliasingFieldReading<Field, Sort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            val translator = fieldsRegionDecoder(expr.collection.collectionId)
                .nonAliasingFieldRegionTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, address)
        }

    override fun <Sort : USort> transform(expr: UNonAliasingArrayReading<Type, Sort, USizeSort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.index) { index ->
            val translator = arrayRegionDecoder(expr.collection.collectionId)
                .nonAliasingArrayRegionTranslator(expr.id, expr.collection.collectionId)
            translator.translateReading(expr.collection, index)
        }

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedMapReading<Type, KeySort, Sort, Reg>,
    ): KExpr<Sort> = transformExprAfterTransformed(expr, expr.key) { key ->
        val translator = mapRegionDecoder(expr.collection.collectionId)
            .allocatedMapTranslator(expr.collection.collectionId)
        translator.translateReading(expr.collection, key)
    }

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UNonAliasingMapReading<Type, KeySort, Sort, Reg>,
    ): KExpr<Sort> = transformExprAfterTransformed(expr, expr.key) { key ->
        val translator = mapRegionDecoder(expr.collection.collectionId)
            .nonAliasingMapTranslator(expr.collection.collectionId)
        translator.translateReading(expr.collection, key)
    }

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UInputMapReading<Type, KeySort, Sort, Reg>,
    ): KExpr<Sort> = transformExprAfterTransformed(expr, expr.address, expr.key) { address, key ->
        val translator = mapRegionDecoder(expr.collection.collectionId)
            .inputMapTranslator(expr.collection.collectionId)
        translator.translateReading(expr.collection, address to key)
    }

    override fun <Sort : USort> transform(
        expr: UAllocatedRefMapWithInputKeysReading<Type, Sort>,
    ): UExpr<Sort> = transformExprAfterTransformed(expr, expr.keyRef) { keyRef ->
        val translator = refMapRegionDecoder(expr.collection.collectionId)
            .allocatedRefMapWithInputKeysTranslator(expr.collection.collectionId)
        translator.translateReading(expr.collection, keyRef)
    }

    override fun <Sort : USort> transform(
        expr: UAllocatedRefMapWithNonAliasingKeysReading<Type, Sort>,
    ): UExpr<Sort> = transformExprAfterTransformed(expr, expr.keyRef) { keyRef ->
        val translator = refMapRegionDecoder(expr.collection.collectionId)
            .allocatedRefMapWithNonAliasingKeysTranslator(expr.collection.collectionId)
        translator.translateReading(expr.collection, keyRef)
    }

    override fun <Sort : USort> transform(
        expr: UInputRefMapWithAllocatedKeysReading<Type, Sort>,
    ): UExpr<Sort> = transformExprAfterTransformed(expr, expr.mapRef) { mapRef ->
        val translator = refMapRegionDecoder(expr.collection.collectionId)
            .inputRefMapWithAllocatedKeysTranslator(expr.collection.collectionId)
        translator.translateReading(expr.collection, mapRef)
    }

    override fun <Sort : USort> transform(
        expr: UNonAliasingRefMapWithAllocatedKeysReading<Type, Sort>,
    ): UExpr<Sort> = transformExprAfterTransformed(expr, expr.mapRef) { mapRef ->
        val translator = refMapRegionDecoder(expr.collection.collectionId)
            .nonAliasingRefMapWithAllocatedKeysTranslator(expr.collection.collectionId)
        translator.translateReading(expr.collection, mapRef)
    }

    override fun <Sort : USort> transform(
        expr: UInputRefMapWithInputKeysReading<Type, Sort>,
    ): UExpr<Sort> = transformExprAfterTransformed(expr, expr.mapRef, expr.keyRef) { mapRef, keyRef ->
        val translator = refMapRegionDecoder(expr.collection.collectionId)
            .inputRefMapTranslator(expr.collection.collectionId)
        translator.translateReading(expr.collection, mapRef to keyRef)
    }

    override fun <Sort : USort> transform(
        expr: UNonAliasingRefMapWithNonAliasingKeysReading<Type, Sort>,
    ): UExpr<Sort> = transformExprAfterTransformed(expr, expr.mapRef, expr.keyRef) { mapRef, keyRef ->
        val translator = refMapRegionDecoder(expr.collection.collectionId)
            .nonAliasingRefMapWithNonAliasingKeysTranslator(expr.collection.collectionId)
        translator.translateReading(expr.collection, mapRef to keyRef)
    }

    override fun transform(expr: UInputMapLengthReading<Type, USizeSort>): KExpr<USizeSort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            val translator = mapLengthRegionDecoder(expr.collection.collectionId)
                .inputMapLengthRegionTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, address)
        }

    override fun <ElemSort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedSetReading<Type, ElemSort, Reg>,
    ): UBoolExpr = transformExprAfterTransformed(expr, expr.element) { element ->
        val translator = setRegionDecoder(expr.collection.collectionId)
            .allocatedSetTranslator(expr.collection.collectionId)
        translator.translateReading(expr.collection, element)
    }

    override fun <ElemSort : USort, Reg : Region<Reg>> transform(
        expr: UNonAliasingSetReading<Type, ElemSort, Reg>,
    ): UBoolExpr = transformExprAfterTransformed(expr, expr.element) { element ->
        val translator = setRegionDecoder(expr.collection.collectionId)
            .nonAliasingSetTranslator(expr.collection.collectionId)
        translator.translateReading(expr.collection, element)
    }


    override fun <ElemSort : USort, Reg : Region<Reg>> transform(
        expr: UInputSetReading<Type, ElemSort, Reg>,
    ): UBoolExpr = transformExprAfterTransformed(expr, expr.address, expr.element) { address, element ->
        val translator = setRegionDecoder(expr.collection.collectionId)
            .inputSetTranslator(expr.collection.collectionId)
        translator.translateReading(expr.collection, address to element)
    }

    override fun transform(expr: UAllocatedRefSetWithInputElementsReading<Type>): UBoolExpr =
        transformExprAfterTransformed(expr, expr.elementRef) { element ->
            val translator = refSetRegionDecoder(expr.collection.collectionId)
                .allocatedRefSetWithInputElementsTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, element)
        }

    override fun transform(expr: UInputRefSetWithAllocatedElementsReading<Type>): UBoolExpr =
        transformExprAfterTransformed(expr, expr.setRef) { setRef ->
            val translator = refSetRegionDecoder(expr.collection.collectionId)
                .inputRefSetWithAllocatedElementsTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, setRef)
        }

    override fun transform(expr: UInputRefSetWithInputElementsReading<Type>): UBoolExpr =
        transformExprAfterTransformed(expr, expr.setRef, expr.elementRef) { setRef, element ->
            val translator = refSetRegionDecoder(expr.collection.collectionId)
                .inputRefSetTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, setRef to element)
        }

    override fun transform(expr: UNonAliasingRefSetWithAllocatedElementsReading<Type>): UBoolExpr =
        transformExprAfterTransformed(expr, expr.setAddress) { setRef ->
            val translator = refSetRegionDecoder(expr.collection.collectionId)
                .nonAliasingRefSetWithAllocatedElementsTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, setRef)
        }

    override fun transform(expr: UAllocatedRefSetWithNonAliasingElementsReading<Type>): UBoolExpr =
        transformExprAfterTransformed(expr, expr.elementAddress) { element ->
            val translator = refSetRegionDecoder(expr.collection.collectionId)
                .allocatedRefSetWithNonAliasingElementsTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, element)
        }

    override fun transform(expr: UNonAliasingRefSetWithNonAliasingElementsReading<Type>): UBoolExpr =
        transformExprAfterTransformed(expr, expr.setRef, expr.elementRef) { setRef, elemRef ->
            val translator = refSetRegionDecoder(expr.collection.collectionId)
                .nonAliasingRefSetWithNonAliasingElementsTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, setRef to elemRef)
        }




    fun <Field, Sort : USort, FieldId : USymbolicFieldId<Field, *, Sort, FieldId>> fieldsRegionDecoder(
        fieldId: FieldId,
    ): UFieldRegionDecoder<Field, Sort> {
        val fieldRegionId = UFieldsRegionId(fieldId.field, fieldId.sort)
        return getOrPutRegionDecoder(fieldRegionId) {
            UFieldRegionDecoder(fieldRegionId, this)
        }
    }

    fun <ArrayType, Sort : USort, ArrayId : USymbolicArrayId<ArrayType, *, Sort, ArrayId>> arrayRegionDecoder(
        arrayId: ArrayId,
    ): UArrayRegionDecoder<ArrayType, Sort, USizeSort> {
        val arrayRegionId = UArrayRegionId<ArrayType, Sort, USizeSort>(arrayId.arrayType, arrayId.sort)
        return getOrPutRegionDecoder(arrayRegionId) {
            UArrayRegionDecoder(arrayRegionId, this)
        }
    }

    fun <ArrayType, ArrayLenId : USymbolicArrayLengthId<*, ArrayType, ArrayLenId, USizeSort>> arrayLengthRegionDecoder(
        arrayLengthId: ArrayLenId,
    ): UArrayLengthRegionDecoder<ArrayType, USizeSort> {
        val arrayRegionId = UArrayLengthsRegionId(arrayLengthId.sort, arrayLengthId.arrayType)
        return getOrPutRegionDecoder(arrayRegionId) {
            UArrayLengthRegionDecoder(arrayRegionId, this)
        }
    }

    fun <MapType, Sort : USort, MapId : USymbolicRefMapId<MapType, *, Sort, *, MapId>> refMapRegionDecoder(
        refMapId: MapId,
    ): URefMapRegionDecoder<MapType, Sort> {
        val symbolicRefMapRegionId = URefMapRegionId(refMapId.sort, refMapId.mapType)
        return getOrPutRegionDecoder(symbolicRefMapRegionId) {
            URefMapRegionDecoder(symbolicRefMapRegionId, this)
        }
    }

    fun <
        MapType,
        KeySort : USort,
        ValueSort : USort,
        Reg : Region<Reg>,
        MapId : USymbolicMapId<MapType, *, KeySort, ValueSort, Reg, *, MapId>,
        > mapRegionDecoder(
        mapId: MapId,
    ): UMapRegionDecoder<MapType, KeySort, ValueSort, Reg> {
        val symbolicMapRegionId = UMapRegionId(mapId.keySort, mapId.sort, mapId.mapType, mapId.keyInfo)
        return getOrPutRegionDecoder(symbolicMapRegionId) {
            UMapRegionDecoder(symbolicMapRegionId, this)
        }
    }

    fun <MapType, MapLengthId : USymbolicMapLengthId<UHeapRef, MapType, MapLengthId, USizeSort>> mapLengthRegionDecoder(
        mapLengthId: MapLengthId,
    ): UMapLengthRegionDecoder<MapType, USizeSort> {
        val symbolicMapLengthRegionId = UMapLengthRegionId(mapLengthId.sort, mapLengthId.mapType)
        return getOrPutRegionDecoder(symbolicMapLengthRegionId) {
            UMapLengthRegionDecoder(symbolicMapLengthRegionId, this)
        }
    }

    fun <SetType, SetId : USymbolicRefSetId<SetType, *, *, SetId>> refSetRegionDecoder(
        refSetId: SetId,
    ): URefSetRegionDecoder<SetType> {
        val symbolicRefSetRegionId = refSetId.setRegionId()
        return getOrPutRegionDecoder(symbolicRefSetRegionId) {
            URefSetRegionDecoder(symbolicRefSetRegionId, this)
        }
    }

    fun <
        SetType,
        KeySort : USort,
        Reg : Region<Reg>,
        SetId : USymbolicSetId<SetType, KeySort, *, Reg, *, SetId>,
        > setRegionDecoder(
        setId: SetId,
    ): USetRegionDecoder<SetType, KeySort, Reg> {
        val symbolicSetRegionId = setId.setRegionId()
        return getOrPutRegionDecoder(symbolicSetRegionId) {
            USetRegionDecoder(symbolicSetRegionId, this)
        }
    }

    val regionIdToDecoder: MutableMap<UMemoryRegionId<*, *>, URegionDecoder<*, *>> = ConcurrentHashMap()

    inline fun <reified D : URegionDecoder<*, *>> getOrPutRegionDecoder(
        regionId: UMemoryRegionId<*, *>,
        buildDecoder: () -> D,
    ): D = regionIdToDecoder.getOrPut(regionId) {
        buildDecoder()
    }.uncheckedCast()
}
