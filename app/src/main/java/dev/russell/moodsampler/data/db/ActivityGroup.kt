package dev.russell.moodsampler.data.db

/**
 * Fixed grouping for activities, shown as section headers on the capture screen
 * so a long list is quick to scan. Declaration order is display order; [OTHER]
 * is always last and is the fallback for user-added or unrecognised labels.
 */
enum class ActivityGroup(val label: String) {
    WORK("Work & study"),
    CHORES("Chores & errands"),
    SOCIAL("Social"),
    LEISURE("Leisure & media"),
    HEALTH("Health & body"),
    TRANSIT("Getting around"),
    REST("Rest"),
    OTHER("Other");

    companion object {
        private val byName = entries.associateBy { it.name }
        fun parse(key: String?): ActivityGroup = byName[key] ?: OTHER
    }
}

/** The group an activity category belongs to; [ActivityGroup.OTHER] when unset. */
val CategoryEntity.activityGroup: ActivityGroup
    get() = ActivityGroup.parse(group)

/** Active activities bucketed by group, in display order, omitting empty groups. */
fun List<CategoryEntity>.byActivityGroup(): Map<ActivityGroup, List<CategoryEntity>> =
    groupBy { it.activityGroup }.toSortedMap()
