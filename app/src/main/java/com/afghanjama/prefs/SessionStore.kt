package com.afghanjama.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore("session")

class SessionStore(private val ctx: Context) {
    private val KEY_LOGGED_IN = booleanPreferencesKey("logged_in")

    val loggedIn: Flow<Boolean> = ctx.ds.data.map { it[KEY_LOGGED_IN] ?: false }

    suspend fun setLoggedIn(value: Boolean) {
        ctx.ds.edit { it[KEY_LOGGED_IN] = value }
    }
}
