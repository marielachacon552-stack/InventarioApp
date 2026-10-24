package com.uth.inventarioapp

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import android.view.View
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import org.hamcrest.Matcher
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Test
import org.junit.runner.RunWith

/** Comprueba el arranque y que la navegación utiliza las vistas XML. No modifica datos. */
@RunWith(AndroidJUnit4::class)
class NavigationInstrumentedTest {
    // Ejecuta el listener de forma s?ncrona; evita retrasos de inyecci?n de toques del emulador.
    private fun click() = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isClickable()
        override fun getDescription() = "Activar el bot?n y esperar a que se actualice la vista"
        override fun perform(controller: UiController, view: View) {
            view.performClick()
            controller.loopMainThreadUntilIdle()
        }
    }
    @Test
    fun opensXmlScreensAndProductForm() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            onView(withId(R.id.total_value)).check(matches(isDisplayed()))
            onView(withId(R.id.nav_products)).perform(click())
            onView(withId(R.id.add_product)).perform(click())
            onView(withId(R.id.input_name)).check(matches(isDisplayed()))
            onView(withText("Cancelar")).perform(click())
            onView(withId(R.id.nav_movements)).perform(click())
            onView(withId(R.id.add_movement)).check(matches(isDisplayed()))
            onView(withId(R.id.nav_summary)).perform(click())
            onView(withId(R.id.total_value)).check(matches(isDisplayed()))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
