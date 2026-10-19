package com.dimgym.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataLayerTest {
    @Test
    fun sharedExerciseWeightShowsOnBothDays() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DimGymDb::class.java)
            .allowMainThreadQueries().build()
        val dao = db.dao()
        val a = dao.save(Day(name = "Legs A", position = 0))
        val b = dao.save(Day(name = "Legs B", position = 1))

        val id = dao.exerciseId("Leg curl")
        assertEquals(id, dao.exerciseId("  leg CURL ")) // same exercise, case/space-insensitive

        dao.save(DayExercise(dayId = a, exerciseId = id, position = 0, sets = 3, reps = 12))
        dao.save(DayExercise(dayId = b, exerciseId = id, position = 0, sets = 4, reps = 8)) // different sets x reps
        dao.update(dao.find("Leg curl")!!.copy(weight = 30.0))

        assertEquals(30.0, dao.exercisesOf(a).first().single().weight)
        assertEquals(30.0, dao.exercisesOf(b).first().single().weight)
        db.close()
    }
}