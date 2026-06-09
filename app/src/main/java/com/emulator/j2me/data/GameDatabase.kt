package com.emulator.j2me.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class GameDatabase(context: Context) {
    private val gson = Gson()
    private val dbFile = File(context.filesDir, "games.json")

    fun loadGames(): List<GameModel> {
        if (!dbFile.exists()) return emptyList()
        return try {
            val json = dbFile.readText()
            val type = object : TypeToken<List<GameModel>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveGames(games: List<GameModel>) {
        try {
            val json = gson.toJson(games)
            dbFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addGame(game: GameModel) {
        val games = loadGames().toMutableList()
        games.removeAll { it.id == game.id }
        games.add(game)
        saveGames(games)
    }

    fun removeGame(gameId: String) {
        val games = loadGames().toMutableList()
        games.removeAll { it.id == gameId }
        saveGames(games)
    }

    fun updateGame(game: GameModel) {
        addGame(game)
    }
}
