package com.personal.flowreader.share

/**
 * [rules] with the unlocked rules arranged as [movableIds]. The rule list only drags unlocked rows,
 * so [movableIds] never holds the protected Default rule; it is carried over with its stored fields
 * and stays last. Unlocked rules missing from [movableIds] keep their relative order after the
 * listed ones; unknown ids are ignored.
 */
fun ParseRules.reordered(rules: List<ParseRule>, movableIds: List<String>): List<ParseRule> {
    val (protectedRules, movable) = rules.partition(::isProtected)
    val byId = movable.associateBy { it.id }
    val listed = movableIds.distinct().mapNotNull(byId::get)
    val listedIds = listed.mapTo(HashSet()) { it.id }
    return listed + movable.filterNot { it.id in listedIds } + protectedRules
}
