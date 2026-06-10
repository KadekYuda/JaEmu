package com.emulator.j2me.data

import android.content.Context
import com.google.gson.Gson

/** Per-game tunable settings, persisted independently of the game library entry. */
data class GameSettings(
    var targetWidth: Int = 240,
    var targetHeight: Int = 320,
    var scaleMode: String = "FIT",
    var smoothScaling: Boolean = false,
    var keypadOpacity: Float = 0.6f
) {
    companion object {
        fun fromGameModel(game: GameModel) = GameSettings(
            targetWidth = game.targetWidth,
            targetHeight = game.targetHeight,
            scaleMode = game.scaleMode,
            smoothScaling = game.smoothScaling,
            keypadOpacity = game.keypadOpacity
        )
    }
}

/** SharedPreferences-backed store keyed by game ID, serializing [GameSettings] as JSON. */
class GameSettingsStore(context: Context) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(gameId: String): GameSettings? {
        val json = prefs.getString(gameId, null) ?: return null
        return try {
            gson.fromJson(json, GameSettings::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Stored settings for the game, or settings seeded from its legacy [GameModel] fields. */
    fun loadOrDefault(game: GameModel): GameSettings =
        load(game.id) ?: GameSettings.fromGameModel(game)

    fun save(gameId: String, settings: GameSettings) {
        prefs.edit().putString(gameId, gson.toJson(settings)).apply()
    }

    fun remove(gameId: String) {
        prefs.edit().remove(gameId).apply()
    }

    companion object {
        private const val PREFS_NAME = "game_settings"
    }
}
