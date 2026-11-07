package com.personal.flowreader.share

/**
 * One change to the stored router rule list, applied to the list as it is when the write happens
 * (inside a single DataStore edit). UI code never writes a whole list it read earlier: that would
 * overwrite rules another writer (plugin seeding, tab removal) added in between.
 */
sealed interface RouterRuleEdit {
    fun applyTo(current: List<RouterRule>): List<RouterRule>

    /** Appends [rule] after the existing rules; ignored if its id is already stored. */
    data class Add(val rule: RouterRule) : RouterRuleEdit {
        override fun applyTo(current: List<RouterRule>): List<RouterRule> =
            if (current.any { it.id == rule.id }) current else current + rule
    }

    /** Replaces the stored rule with the same id; ignored if it was deleted meanwhile. */
    data class Replace(val rule: RouterRule) : RouterRuleEdit {
        override fun applyTo(current: List<RouterRule>): List<RouterRule> =
            current.map { if (it.id == rule.id) rule else it }
    }

    data class SetEnabled(val id: String, val enabled: Boolean) : RouterRuleEdit {
        override fun applyTo(current: List<RouterRule>): List<RouterRule> =
            current.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    data class Delete(val ids: Set<String>) : RouterRuleEdit {
        override fun applyTo(current: List<RouterRule>): List<RouterRule> =
            current.filterNot { it.id in ids }
    }

    /**
     * Puts the rules named in [ids] in that order. Stored rules not named (added by another writer
     * after the UI took its snapshot) are kept, before the catch-all URL rule when it is present.
     */
    data class Reorder(val ids: List<String>) : RouterRuleEdit {
        override fun applyTo(current: List<RouterRule>): List<RouterRule> {
            val byId = current.associateBy { it.id }
            val ordered = ids.distinct().mapNotNull(byId::get)
            val named = ordered.map { it.id }.toSet()
            val extras = current.sortedBy { it.order }.filterNot { it.id in named }
            if (extras.isEmpty()) return ordered
            val anyIdx = ordered.indexOfFirst { it.id == RouterRules.URL_ANY_ID }
                .let { if (it < 0) ordered.size else it }
            return ordered.take(anyIdx) + extras + ordered.drop(anyIdx)
        }
    }

    /** Points rules whose landing is the custom tab [tabId] at Files (the tab was removed). */
    data class RedirectTabToFiles(val tabId: String) : RouterRuleEdit {
        override fun applyTo(current: List<RouterRule>): List<RouterRule> =
            current.map {
                if (it.destination.id == tabId) it.copy(destination = RouterLanding.Files) else it
            }
    }

    companion object {
        /** Applies [edit] to [current] (stored order) and renumbers `order` to match list position. */
        fun apply(current: List<RouterRule>, edit: RouterRuleEdit): List<RouterRule> =
            edit.applyTo(current.sortedBy { it.order }).mapIndexed { i, rule -> rule.copy(order = i) }
    }
}
