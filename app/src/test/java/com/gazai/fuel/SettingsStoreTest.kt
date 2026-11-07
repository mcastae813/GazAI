package com.gazai.fuel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gazai.fuel.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStoreTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val main get() = ctx.getSharedPreferences("gazai", Context.MODE_PRIVATE)

    @Before
    fun clean() {
        main.edit().clear().commit()
        ctx.getSharedPreferences("gazai_key", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun keyFromVersion03MovesOutOfTheBackedUpFile() {
        main.edit().putString("apiKey", "sk-or-old").putString("currency", "MX$").commit()
        val s = SettingsStore(ctx).load()
        assertEquals("sk-or-old", s.apiKey)
        assertEquals("MX$", s.currency)
        assertFalse(main.contains("apiKey"))
    }

    @Test
    fun savedKeyIsNotWrittenToTheMainFile() {
        val store = SettingsStore(ctx)
        store.save(store.load().copy(apiKey = " sk-or-new "))
        assertEquals("sk-or-new", SettingsStore(ctx).load().apiKey)
        assertFalse(main.contains("apiKey"))
    }
}
