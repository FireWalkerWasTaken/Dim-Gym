package com.dimgym

import com.dimgym.data.Day
import com.dimgym.data.DayExercise
import com.dimgym.data.Exercise
import com.dimgym.data.PlanDao
import com.dimgym.data.Settings
import org.json.JSONArray
import org.json.JSONObject

/** Backup = one readable JSON file with the whole plan, weights, notes and settings. */
object Backup {
    suspend fun export(dao: PlanDao): String {
        val s = dao.settingsNow() ?: Settings()
        return JSONObject()
            .put("app", "DimGym").put("version", 1)
            .put(
                "settings",
                JSONObject().put("unit", s.unit).put("theme", s.theme)
                    .put("reminderMinutes", s.reminderMinutes ?: JSONObject.NULL).put("cyclePosition", s.cyclePosition),
            )
            .put(
                "exercises",
                JSONArray(dao.allExercises().map {
                    JSONObject().put("id", it.id).put("name", it.name).put("weight", it.weight ?: JSONObject.NULL).put("notes", it.notes)
                }),
            )
            .put(
                "days",
                JSONArray(dao.allDays().map {
                    JSONObject().put("id", it.id).put("name", it.name).put("position", it.position).put("isRest", it.isRest)
                }),
            )
            .put(
                "dayExercises",
                JSONArray(dao.allDayExercises().map {
                    JSONObject().put("id", it.id).put("dayId", it.dayId).put("exerciseId", it.exerciseId)
                        .put("position", it.position).put("sets", it.sets).put("reps", it.reps)
                }),
            )
            .toString(2)
    }

    /** Replaces all data with the backup. Throws if the text is not a valid backup, and then nothing is changed. */
    suspend fun restore(dao: PlanDao, text: String): Int? {
        val o = JSONObject(text)
        require(o.optString("app") == "DimGym" && o.getInt("version") == 1)
        val s = o.getJSONObject("settings")
        val settings = Settings(
            unit = s.getString("unit"), theme = s.getString("theme"),
            reminderMinutes = if (s.isNull("reminderMinutes")) null else s.getInt("reminderMinutes"),
            cyclePosition = s.getInt("cyclePosition"),
        )
        val exercises = o.getJSONArray("exercises").objects().map {
            Exercise(it.getLong("id"), it.getString("name"), if (it.isNull("weight")) null else it.getDouble("weight"), it.getString("notes"))
        }
        val days = o.getJSONArray("days").objects().map {
            Day(it.getLong("id"), it.getString("name"), it.getInt("position"), it.getBoolean("isRest"))
        }
        val links = o.getJSONArray("dayExercises").objects().map {
            DayExercise(it.getLong("id"), it.getLong("dayId"), it.getLong("exerciseId"), it.getInt("position"), it.getInt("sets"), it.getInt("reps"))
        }
        dao.restore(settings, exercises, days, links)
        return settings.reminderMinutes
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
}