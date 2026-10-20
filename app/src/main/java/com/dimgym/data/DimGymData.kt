package com.dimgym.data // change to your own package name

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One record per exercise name, so weight and notes are shared by every day that uses it. */
@Entity(indices = [Index("name", unique = true)])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val weight: Double? = null, // null = no weight (dips, etc.); dumbbells = per dumbbell
    val notes: String = "",
)

/** A step in the rolling cycle ("Push A", "Rest"...). */
@Entity
data class Day(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int, // order in the cycle
    val isRest: Boolean = false,
)

/** An exercise inside a day. Sets x reps live here, so they can differ per day. */
@Entity(
    foreignKeys = [
        ForeignKey(Day::class, ["id"], ["dayId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Exercise::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("dayId"), Index("exerciseId")],
)
data class DayExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val exerciseId: Long,
    val position: Int,
    val sets: Int,
    val reps: Int,
)

/** Single row (id = 1), created with the database. */
@Entity
data class Settings(
    @PrimaryKey val id: Int = 1,
    val unit: String = "kg", // "kg" | "lb"
    val theme: String = "dark", // "dark" | "light"
    val reminderMinutes: Int? = null, // minutes after midnight, null = reminder off
    val cyclePosition: Int = 0, // index of today's workout in the cycle (0 = first day)
    val lastDoneAt: Long? = null, // when Done was last tapped (epoch millis); used to skip the reminder
)

/** What the Today screen shows per card. */
data class ExerciseRow(
    val dayExerciseId: Long,
    val exerciseId: Long,
    val name: String,
    val weight: Double?,
    val notes: String,
    val sets: Int,
    val reps: Int,
)

@Dao
abstract class PlanDao {
    @Query("SELECT * FROM Day ORDER BY position") abstract fun days(): Flow<List<Day>>

    @Query("SELECT * FROM Day ORDER BY position LIMIT 1 OFFSET (SELECT cyclePosition FROM Settings WHERE id = 1) % MAX((SELECT COUNT(*) FROM Day), 1)")
    abstract fun today(): Flow<Day?>

    @Query(
        """SELECT de.id AS dayExerciseId, de.exerciseId, e.name, e.weight, e.notes, de.sets, de.reps
           FROM DayExercise de JOIN Exercise e ON e.id = de.exerciseId
           WHERE de.dayId = :dayId ORDER BY de.position"""
    )
    abstract fun exercisesOf(dayId: Long): Flow<List<ExerciseRow>>

    @Query("SELECT name FROM Exercise ORDER BY name") abstract fun exerciseNames(): Flow<List<String>>

    @Query("SELECT * FROM Settings WHERE id = 1") abstract fun settings(): Flow<Settings>

    @Upsert abstract suspend fun save(day: Day): Long
    @Upsert abstract suspend fun save(de: DayExercise)
    @Upsert abstract suspend fun save(s: Settings)
    @Update abstract suspend fun update(e: Exercise) // weight + notes edits
    @Delete abstract suspend fun delete(day: Day)
    @Delete abstract suspend fun delete(de: DayExercise)

    @Query("SELECT COUNT(*) FROM Day") abstract suspend fun dayCount(): Int
    @Query("SELECT cyclePosition FROM Settings WHERE id = 1") abstract suspend fun cycle(): Int
    @Query("UPDATE Settings SET cyclePosition = :i WHERE id = 1") abstract suspend fun setCycle(i: Int)
    @Query("UPDATE Settings SET lastDoneAt = :t WHERE id = 1") abstract suspend fun setDoneAt(t: Long)

    @Query("UPDATE DayExercise SET position = :pos WHERE id = :id") abstract suspend fun updatePosition(id: Long, pos: Int)

    @Transaction
    open suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { i, id -> updatePosition(id, i) }
    }

    /** Done: move to the next workout, wrapping around at the end of the cycle, and remember when. */
    @Transaction
    open suspend fun advance() {
        val n = dayCount()
        if (n > 0) setCycle((cycle() % n + 1) % n)
        setDoneAt(System.currentTimeMillis())
    }

    // --- backup / restore ---
    @Query("SELECT * FROM Exercise") abstract suspend fun allExercises(): List<Exercise>
    @Query("SELECT * FROM Day") abstract suspend fun allDays(): List<Day>
    @Query("SELECT * FROM DayExercise") abstract suspend fun allDayExercises(): List<DayExercise>
    @Query("SELECT * FROM Settings WHERE id = 1") abstract suspend fun settingsNow(): Settings?
    @Query("DELETE FROM Day") abstract suspend fun clearDays()
    @Query("DELETE FROM Exercise") abstract suspend fun clearExercises()
    @Insert abstract suspend fun addExercises(list: List<Exercise>)
    @Insert abstract suspend fun addDays(list: List<Day>)
    @Insert abstract suspend fun addDayExercises(list: List<DayExercise>)

    /** Replaces everything with the backup's data, all or nothing. */
    @Transaction
    open suspend fun restore(s: Settings, ex: List<Exercise>, days: List<Day>, links: List<DayExercise>) {
        clearDays(); clearExercises()
        addExercises(ex); addDays(days); addDayExercises(links)
        save(s)
    }

    @Query("SELECT * FROM Exercise WHERE name = :name") abstract suspend fun find(name: String): Exercise?
    @Insert abstract suspend fun insert(e: Exercise): Long

    /** Typing a name that already exists reuses that exercise (case-insensitive), so its weight stays shared. */
    @Transaction
    open suspend fun exerciseId(name: String): Long {
        val n = name.trim()
        return find(n)?.id ?: insert(Exercise(name = n))
    }
}

/** v1 -> v2: remember when Done was last tapped. Existing data is kept. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Settings ADD COLUMN lastDoneAt INTEGER")
    }
}

@Database(
    entities = [Day::class, Exercise::class, DayExercise::class, Settings::class],
    version = 2,
    exportSchema = false,
)
abstract class DimGymDb : RoomDatabase() {
    abstract fun dao(): PlanDao

    companion object {
        @Volatile private var inst: DimGymDb? = null

        fun get(ctx: Context): DimGymDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, DimGymDb::class.java, "dimgym.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("INSERT INTO Settings (id, unit, theme, reminderMinutes, cyclePosition) VALUES (1, 'kg', 'dark', NULL, 0)")
                    }
                })
                .build().also { inst = it }
        }
    }
}