package org.usvm.memory

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.UNonAliasingHeapAddress
import org.usvm.UNonAliasingHeapRef
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.isFalse
import org.usvm.uctx

fun UHeapRef.nonAliasingElementLocation(): UNonAliasingHeapAddress? {
    if (this !is UNonAliasingHeapRef) return null
    val location = uctx.nonAliasingLocationOf(id)
    return if (location == id) null else location
}

fun nonAliasingPairElementLocation(first: UHeapRef, second: UHeapRef): Any? {
    val firstLocation = first.nonAliasingElementLocation()
    val secondLocation = second.nonAliasingElementLocation()
    if (firstLocation == null && secondLocation == null) return null
    return UPairLocation(firstLocation ?: first.objectId(), secondLocation ?: second.objectId())
}

private data class UPairLocation(val first: Int, val second: Int)

private fun UHeapRef.objectId(): Int = when (this) {
    is UConcreteHeapRef -> address
    is UNonAliasingHeapRef -> id
    else -> error("Unexpected ref in a non-aliasing collection: $this")
}

fun sameObjectPath(lhs: UHeapRef, rhs: UHeapRef): UBoolExpr = lhs.uctx.mkNonAliasingKeyEq(lhs, rhs)

fun samePairPath(lhs: Pair<UHeapRef, UHeapRef>, rhs: Pair<UHeapRef, UHeapRef>): UBoolExpr =
    lhs.first.uctx.mkAnd(sameObjectPath(lhs.first, rhs.first), sameObjectPath(lhs.second, rhs.second))

class UElementLocationOp<O, C>(
    val owner: O,
    val guard: UBoolExpr,
    val apply: (C, UBoolExpr, MutabilityOwnership) -> C,
)

class UElementLocation<K, O, C>(
    val members: PersistentMap<K, O> = persistentMapOf(),
    val history: PersistentList<UElementLocationOp<O, C>> = persistentListOf(),
)

class UElementLocations<L, K, O, C>(
    private val samePath: (O, O) -> UBoolExpr,
    private val locations: PersistentMap<L, UElementLocation<K, O, C>> = persistentMapOf(),
) {
    fun materialize(locationId: L, id: K, owner: O, empty: C): Pair<UElementLocations<L, K, O, C>, C> {
        val location = locations[locationId] ?: UElementLocation()
        var collection = empty
        for (op in location.history) {
            val samePath = samePath(op.owner, owner)
            if (samePath.isFalse) continue
            val ctx = op.guard.uctx
            collection = op.apply(collection, ctx.mkAnd(op.guard, samePath), ctx.defaultOwnership)
        }
        val updated = UElementLocation(location.members.put(id, owner), location.history)
        return UElementLocations(samePath, locations.put(locationId, updated)) to collection
    }

    fun apply(
        locationId: L,
        owner: O,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
        op: (C, UBoolExpr, MutabilityOwnership) -> C,
        update: (K, (C) -> C) -> Unit,
    ): UElementLocations<L, K, O, C> {
        val location = locations[locationId] ?: UElementLocation()
        for ((memberId, memberOwner) in location.members) {
            val samePath = samePath(owner, memberOwner)
            if (samePath.isFalse) continue
            val memberGuard = guard.uctx.mkAnd(guard, samePath)
            update(memberId) { op(it, memberGuard, ownership) }
        }
        val updated = UElementLocation(location.members, location.history.add(UElementLocationOp(owner, guard, op)))
        return UElementLocations(samePath, locations.put(locationId, updated))
    }
}

typealias UObjectElementLocations<C> = UElementLocations<UNonAliasingHeapAddress, UNonAliasingHeapAddress, UHeapRef, C>

fun <C> objectElementLocations(): UObjectElementLocations<C> = UElementLocations(::sameObjectPath)

typealias UPairElementLocations<K, C> = UElementLocations<Any, K, Pair<UHeapRef, UHeapRef>, C>

fun <K, C> pairElementLocations(): UPairElementLocations<K, C> = UElementLocations(::samePairPath)
