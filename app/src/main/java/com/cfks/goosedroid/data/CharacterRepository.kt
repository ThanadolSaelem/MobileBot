package com.cfks.goosedroid.data

import android.content.Context
import android.util.Log
import com.cfks.goosedroid.model.AnimationSequence
import com.cfks.goosedroid.model.PhysicsCharacter
import com.cfks.goosedroid.model.SpriteSheetData
import org.json.JSONArray
import org.json.JSONObject

object CharacterRepository {
    private const val PREFS_NAME = "GooseDroidPrefs"
    private const val KEY_CHARACTERS = "saved_physics_characters"

    fun saveCharacters(context: Context, characters: List<PhysicsCharacter>) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            for (char in characters) {
                val charObj = JSONObject()
                charObj.put("id", char.id)
                charObj.put("x", char.x)
                charObj.put("y", char.y)
                
                val spriteObj = JSONObject()
                val sprite = char.spriteSheetData
                spriteObj.put("name", sprite.name)
                spriteObj.put("uri", sprite.uri)
                spriteObj.put("columns", sprite.columns)
                spriteObj.put("rows", sprite.rows)
                
                val movesArr = JSONArray()
                for (move in sprite.moveSets) {
                    val moveObj = JSONObject()
                    moveObj.put("name", move.name)
                    val framesArr = JSONArray()
                    for (f in move.frames) framesArr.put(f)
                    moveObj.put("frames", framesArr)
                    moveObj.put("speedMs", move.speedMs)
                    moveObj.put("uri", move.uri)
                    moveObj.put("columns", move.columns)
                    moveObj.put("rows", move.rows)
                    moveObj.put("description", move.description)
                    moveObj.put("dialogue", move.dialogue)
                    movesArr.put(moveObj)
                }
                spriteObj.put("moveSets", movesArr)
                
                charObj.put("spriteSheetData", spriteObj)
                jsonArray.put(charObj)
            }
            prefs.edit().putString(KEY_CHARACTERS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("CharacterRepository", "Failed to save characters: ${e.message}")
        }
    }

    fun loadCharacters(context: Context): List<PhysicsCharacter> {
        val result = mutableListOf<PhysicsCharacter>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_CHARACTERS, "[]") ?: "[]"
            if (jsonStr == "[]") return result
            
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val charObj = jsonArray.getJSONObject(i)
                val spriteObj = charObj.getJSONObject("spriteSheetData")
                
                val movesArr = spriteObj.getJSONArray("moveSets")
                val moves = mutableListOf<AnimationSequence>()
                for (j in 0 until movesArr.length()) {
                    val moveObj = movesArr.getJSONObject(j)
                    val framesArr = moveObj.getJSONArray("frames")
                    val frames = mutableListOf<Int>()
                    for (k in 0 until framesArr.length()) frames.add(framesArr.getInt(k))
                    
                    moves.add(
                        AnimationSequence(
                            name = moveObj.getString("name"),
                            frames = frames,
                            speedMs = moveObj.getLong("speedMs"),
                            uri = if (moveObj.has("uri") && !moveObj.isNull("uri")) moveObj.getString("uri") else null,
                            columns = moveObj.getInt("columns"),
                            rows = moveObj.getInt("rows"),
                            description = moveObj.optString("description", ""),
                            dialogue = moveObj.optString("dialogue", "")
                        )
                    )
                }
                
                val spriteData = SpriteSheetData(
                    id = if (spriteObj.has("id")) spriteObj.getString("id") else java.util.UUID.randomUUID().toString(),
                    name = spriteObj.getString("name"),
                    uri = if (spriteObj.has("uri") && !spriteObj.isNull("uri")) spriteObj.getString("uri") else null,
                    columns = spriteObj.getInt("columns"),
                    rows = spriteObj.getInt("rows"),
                    moveSets = moves
                )
                
                result.add(
                    PhysicsCharacter(
                        id = charObj.getString("id"),
                        spriteSheetData = spriteData,
                        x = charObj.optDouble("x", 0.0).toFloat(),
                        y = charObj.optDouble("y", 0.0).toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("CharacterRepository", "Failed to load characters: ${e.message}")
        }
        return result
    }
}
