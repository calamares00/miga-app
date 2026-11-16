package org.calamares.miga.data.cook

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.calamares.miga.ui.list.TestMigaApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Several named timers at once, each with a system alarm that fires when it ends. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class)
class CookTimersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))

    @After
    fun clear() = CookTimers.timers.value.forEach { CookTimers.cancel(context, it.id) }

    @Test
    fun `timers run side by side, each with its alarm`() {
        val rice = CookTimers.start(context, "Rice", 600, now = 1_000L)
        val oven = CookTimers.start(context, "Step 3", 90, stepIndex = 2, now = 1_000L)
        assertEquals(listOf(rice, oven), CookTimers.timers.value)
        assertEquals(600, rice.secondsLeft(1_000L))
        assertEquals(1, oven.secondsLeft(90_000L))
        assertEquals(0, oven.secondsLeft(91_000L))
        assertEquals(2, alarms.scheduledAlarms.size)
        assertTrue(alarms.scheduledAlarms.any { it.triggerAtTime == 601_000L })
    }

    @Test
    fun `cancelling one keeps the others and removes its alarm`() {
        val first = CookTimers.start(context, "One", 60)
        CookTimers.start(context, "Two", 120)
        CookTimers.cancel(context, first.id)
        assertEquals(listOf("Two"), CookTimers.timers.value.map { it.name })
        assertEquals(1, alarms.scheduledAlarms.size)
    }

    @Test
    fun `the alarm posts a notification with the timer's name`() {
        val manager = shadowOf(context.getSystemService(android.app.NotificationManager::class.java))
        assertNull(manager.allNotifications.firstOrNull())
        CookTimers.notifyFinished(context, 3, "Rice")
        val notification = manager.allNotifications.single()
        assertNotNull(notification)
        assertTrue(notification.extras.getString("android.title").orEmpty().contains("Rice"))
    }
}
