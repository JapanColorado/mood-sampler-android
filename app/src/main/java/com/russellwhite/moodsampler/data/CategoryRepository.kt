package com.russellwhite.moodsampler.data

import androidx.room.withTransaction
import com.russellwhite.moodsampler.data.db.ActivityGroup
import com.russellwhite.moodsampler.data.db.AppDatabase
import com.russellwhite.moodsampler.data.db.CategoryEntity
import com.russellwhite.moodsampler.data.db.CategoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Active and archived categories of one type, each in display order. */
data class CategoryGroup(
    val active: List<CategoryEntity> = emptyList(),
    val archived: List<CategoryEntity> = emptyList(),
)

/** Editing operations on the category lists. Categories are archived, never deleted. */
class CategoryRepository(private val db: AppDatabase) {

    private val dao get() = db.categoryDao()

    val groups: Flow<Map<CategoryType, CategoryGroup>> = dao.allFlow().map { all ->
        all.groupBy { it.type }.mapValues { (_, list) ->
            val sorted = list.sortedWith(ORDER)
            CategoryGroup(active = sorted.filter { !it.isArchived }, archived = sorted.filter { it.isArchived })
        }
    }

    /** Sample counts per category id (activities via the join table). */
    val usage: Flow<Map<Long, Int>> = db.sampleDao().categoryUsageFlow().map { rows ->
        rows.groupBy { it.categoryId }.mapValues { (_, r) -> r.sumOf { it.n } }
    }

    suspend fun add(type: CategoryType, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        val group = if (type == CategoryType.ACTIVITY) ActivityGroup.OTHER.name else null
        dao.insert(CategoryEntity(type = type, label = trimmed, sortOrder = dao.maxSortOrder(type) + 1, group = group))
    }

    /**
     * The id of the [type] category labelled [label] (case-insensitive), adding it
     * when there is none and restoring it when archived. Null for a blank label.
     */
    suspend fun findOrAdd(type: CategoryType, label: String): Long? {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return null
        return db.withTransaction {
            val existing = dao.byType(type).firstOrNull { it.label.equals(trimmed, ignoreCase = true) }
            when {
                existing == null -> {
                    val group = if (type == CategoryType.ACTIVITY) ActivityGroup.OTHER.name else null
                    dao.insert(CategoryEntity(type = type, label = trimmed, sortOrder = dao.maxSortOrder(type) + 1, group = group))
                }
                existing.isArchived -> {
                    dao.update(existing.copy(isArchived = false))
                    renumber(type)
                    existing.id
                }
                else -> existing.id
            }
        }
    }

    /** Move an activity to another [ActivityGroup]; no-op for other types. */
    suspend fun setGroup(category: CategoryEntity, group: ActivityGroup) {
        if (category.type != CategoryType.ACTIVITY) return
        dao.update(category.copy(group = group.name))
    }

    suspend fun rename(category: CategoryEntity, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        dao.update(category.copy(label = trimmed))
    }

    suspend fun setArchived(category: CategoryEntity, archived: Boolean) {
        db.withTransaction {
            dao.update(category.copy(isArchived = archived))
            renumber(category.type)  // a restored item goes to the end of the active list
        }
    }

    /** Move an active category one slot up (delta = -1) or down (+1) within its type. */
    suspend fun move(category: CategoryEntity, delta: Int) {
        db.withTransaction {
            val active = dao.byType(category.type).filter { !it.isArchived }.sortedWith(ORDER).toMutableList()
            val from = active.indexOfFirst { it.id == category.id }
            val to = from + delta
            if (from < 0 || to !in active.indices) return@withTransaction
            active.add(to, active.removeAt(from))
            dao.updateAll(active.mapIndexed { i, c -> c.copy(sortOrder = i + 1) })
        }
    }

    /** Active items 1..n in their current order, archived after them. */
    private suspend fun renumber(type: CategoryType) {
        val all = dao.byType(type).sortedWith(ORDER)
        val active = all.filter { !it.isArchived }
        val archived = all.filter { it.isArchived }
        dao.updateAll(
            active.mapIndexed { i, c -> c.copy(sortOrder = i + 1) } +
                archived.mapIndexed { i, c -> c.copy(sortOrder = active.size + i + 1) }
        )
    }

    private companion object {
        val ORDER = compareBy<CategoryEntity>({ it.sortOrder }, { it.id })
    }
}
