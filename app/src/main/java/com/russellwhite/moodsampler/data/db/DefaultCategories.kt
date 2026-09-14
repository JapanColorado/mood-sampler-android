package com.russellwhite.moodsampler.data.db

/**
 * Built-in category labels. Fresh installs seed [DEFAULT_CATEGORIES] on database
 * creation; installs upgrading from schema v1 receive the `V2_ADDED_*` lists
 * through the 1→2 migration (skipping labels the user already added by hand).
 */
object DefaultCategories {
    /** Each default activity declared once with its [ActivityGroup] (schema v3). */
    private val V1_ACTIVITY_GROUPS: List<Pair<String, ActivityGroup>> = listOf(
        "Work" to ActivityGroup.WORK,
        "Chores" to ActivityGroup.CHORES,
        "Meals" to ActivityGroup.HEALTH,
        "Exercise" to ActivityGroup.HEALTH,
        "Socializing" to ActivityGroup.SOCIAL,
        "Screen / leisure" to ActivityGroup.LEISURE,
        "Reading" to ActivityGroup.LEISURE,
        "Commuting" to ActivityGroup.TRANSIT,
        "Resting" to ActivityGroup.REST,
    )

    private val V2_ADDED_ACTIVITY_GROUPS: List<Pair<String, ActivityGroup>> = listOf(
        "Studying" to ActivityGroup.WORK,
        "Cooking" to ActivityGroup.CHORES,
        "Errands / shopping" to ActivityGroup.CHORES,
        "Walking" to ActivityGroup.HEALTH,
        "Gaming" to ActivityGroup.LEISURE,
        "Watching TV / video" to ActivityGroup.LEISURE,
        "Social media" to ActivityGroup.LEISURE,
        "Music" to ActivityGroup.LEISURE,
        "Creative / hobby" to ActivityGroup.LEISURE,
        "Personal care" to ActivityGroup.HEALTH,
        "Napping / sleeping" to ActivityGroup.REST,
        "Caregiving" to ActivityGroup.CHORES,
        "Pet care" to ActivityGroup.CHORES,
        "Meditation / prayer" to ActivityGroup.HEALTH,
        "Phone / messaging" to ActivityGroup.SOCIAL,
        "Waiting" to ActivityGroup.TRANSIT,
        "Medical / appointment" to ActivityGroup.HEALTH,
        "Driving" to ActivityGroup.TRANSIT,
    )

    val V1_ACTIVITIES: List<String> = V1_ACTIVITY_GROUPS.map { it.first }
    val V2_ADDED_ACTIVITIES: List<String> = V2_ADDED_ACTIVITY_GROUPS.map { it.first }

    /** Always kept last in the activity list. */
    const val OTHER_ACTIVITY = "Other"

    val V1_LOCATIONS = listOf(
        "Home", "Work", "Outside", "Transit", "Someone's home", "Public place",
    )

    val V2_ADDED_LOCATIONS = listOf(
        "Gym", "Café / restaurant", "Store", "Car", "School / campus",
        "Park / nature", "Bed", "Doctor / clinic", "Bar", "Library",
    )

    val SOCIAL = listOf(
        "Alone", "Partner", "Family", "Friends", "Coworkers", "Strangers",
    )

    /** Full seed for a fresh install, in display order. */
    val DEFAULT_CATEGORIES: Map<CategoryType, List<String>> = mapOf(
        CategoryType.ACTIVITY to V1_ACTIVITIES + V2_ADDED_ACTIVITIES + OTHER_ACTIVITY,
        CategoryType.LOCATION to V1_LOCATIONS + V2_ADDED_LOCATIONS,
        CategoryType.SOCIAL to SOCIAL,
    )

    /**
     * Group of every default activity, keyed by lower-cased label. Anything not
     * listed — "Other" and user-added labels — falls into [ActivityGroup.OTHER].
     */
    val ACTIVITY_GROUPS: Map<String, ActivityGroup> =
        (V1_ACTIVITY_GROUPS + V2_ADDED_ACTIVITY_GROUPS).associate { (label, group) -> label.lowercase() to group }

    /** Case-insensitive lookup of a default activity label's group. */
    fun groupFor(activityLabel: String): ActivityGroup =
        ACTIVITY_GROUPS[activityLabel.lowercase()] ?: ActivityGroup.OTHER

    /** Labels added in schema v2, by type — what the migration back-fills. */
    val V2_ADDED: Map<CategoryType, List<String>> = mapOf(
        CategoryType.ACTIVITY to V2_ADDED_ACTIVITIES,
        CategoryType.LOCATION to V2_ADDED_LOCATIONS,
    )
}
