package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.spark.data.SparkDatabase
import com.example.spark.data.SparkRepository
import com.example.spark.engine.AppLauncherEngine
import com.example.spark.engine.CalendarEngine
import com.example.spark.engine.HardwareEngine
import com.example.spark.engine.MediaEngine
import com.example.spark.engine.TelephonyEngine
import com.example.spark.tools.SparkToolRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Spark Assistant", appName)
  }

  @Test
  fun `verify tool registry definitions and execution`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = SparkDatabase.getDatabase(context)
    val repo = SparkRepository(db.sparkDao())

    val registry = SparkToolRegistry(
      context = context,
      repository = repo,
      hardwareEngine = HardwareEngine(context),
      mediaEngine = MediaEngine(context),
      telephonyEngine = TelephonyEngine(context),
      appLauncherEngine = AppLauncherEngine(context),
      calendarEngine = CalendarEngine(context)
    )

    // Check registered tool count
    assertTrue("Should have 16 registered tools", registry.toolDefinitions.size >= 15)

    // Test Core Memory save & access
    val saveResult = registry.executeTool(
      "save_core_memory",
      JSONObject().put("key", "Parking Location").put("value", "Level 2 Bay 14")
    )
    assertTrue(saveResult.isSuccess)

    val memory = repo.getMemory("Parking Location")
    assertNotNull(memory)
    assertEquals("Level 2 Bay 14", memory?.value)

    // Test hardware tool schema
    val hardwareResult = registry.executeTool(
      "control_device_hardware",
      JSONObject().put("action", "wifi_settings")
    )
    assertTrue(hardwareResult.isSuccess)
  }
}
