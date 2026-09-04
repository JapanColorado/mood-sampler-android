package com.russellwhite.moodsampler.data.db

/**
 * Built-in category labels. Fresh installs seed [DEFAULT_CATEGORIES] on database
 * creation; installs upgrading from schema v1 receive the `V2_ADDED_*` lists
 * through the 1→2 migration (skipping labels the user already added by hand).
 */
object DefaultCategories {
    val V1_ACTIVITIES = listOf(
        "Work", "Chores", "Meals", "Exercise", "Socializing",
        "Screen / leisure", "Reading", "Commuting", "Resting",
    )

    val V2_ADDED_ACTIVITIES = listOf(
        "Studying", "Cooking", "Errands / shopping", "Walking", "Gaming",
        "Watching TV / video", "Social media", "Music", "Creative / hobby",
        "Personal care", "Napping / sleeping", "Caregiving", "Pet care",
        "Meditation / prayer", "Phone / messaging", "Waiting",
        "Medical / appointment", "Driving",
    )

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

    /** Labels added in schema v2, by type — what the migration back-fills. */
    val V2_ADDED: Map<CategoryType, List<String>> = mapOf(
        CategoryType.ACTIVITY to V2_ADDED_ACTIVITIES,
        CategoryType.LOCATION to V2_ADDED_LOCATIONS,
    )
}
