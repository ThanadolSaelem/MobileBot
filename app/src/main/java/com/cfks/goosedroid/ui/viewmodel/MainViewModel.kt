package com.cfks.goosedroid.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cfks.goosedroid.model.AnimationSequence
import com.cfks.goosedroid.model.PhysicsCharacter
import com.cfks.goosedroid.model.SpriteSheetData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val context = application.applicationContext
    
    private val _physicsCharacters = MutableStateFlow<List<PhysicsCharacter>>(emptyList())
    val physicsCharacters: StateFlow<List<PhysicsCharacter>> = _physicsCharacters.asStateFlow()

    private val _hudMessage = MutableStateFlow<String?>(null)
    val hudMessage: StateFlow<String?> = _hudMessage.asStateFlow()

    private val _customWallpaperUri = MutableStateFlow<String?>(null)
    val customWallpaperUri: StateFlow<String?> = _customWallpaperUri.asStateFlow()

    init {
        // Load persisted characters initially
        val loaded = com.cfks.goosedroid.data.CharacterRepository.loadCharacters(context)
        _physicsCharacters.value = loaded

        viewModelScope.launch {
            com.cfks.goosedroid.brain.CharacterRegistry.recalledCharacters.collect { recalledList ->
                if (recalledList.isNotEmpty()) {
                    _physicsCharacters.update { current ->
                        val newOnes = recalledList.filter { rec -> current.none { it.id == rec.id } }
                        val updated = current + newOnes
                        com.cfks.goosedroid.data.CharacterRepository.saveCharacters(context, updated)
                        updated
                    }
                    com.cfks.goosedroid.brain.CharacterRegistry.clearRecalled()
                    if (recalledList.size == 1) {
                        showHud("UNIT RECALLED: ${recalledList.first().spriteSheetData.name.uppercase()}")
                    } else {
                        showHud("${recalledList.size} UNITS RECALLED")
                    }
                }
            }
        }
    }

    fun showHud(message: String) {
        viewModelScope.launch {
            _hudMessage.value = message
            delay(3000)
            if (_hudMessage.value == message) {
                _hudMessage.value = null
            }
        }
    }

    fun setCustomWallpaper(uri: String?) {
        _customWallpaperUri.value = uri
        if (uri != null) {
            showHud("WALLPAPER UPDATED")
        } else {
            showHud("RESET TO DEFAULT GRID")
        }
    }

    fun spawnCharacter(character: PhysicsCharacter) {
        _physicsCharacters.update { 
            // If editing existing, replace it
            val list = if (it.any { c -> c.id == character.id }) {
                it.map { c -> if (c.id == character.id) character else c }
            } else {
                it + character
            }
            list
        }
        
        // Update Master DB
        val masterList = com.cfks.goosedroid.data.CharacterRepository.loadCharacters(context).toMutableList()
        val index = masterList.indexOfFirst { it.id == character.id }
        if (index != -1) {
            masterList[index] = character
        } else {
            masterList.add(character)
        }
        com.cfks.goosedroid.data.CharacterRepository.saveCharacters(context, masterList)
        
        showHud("NEW UNIT DEPLOYED: ${character.spriteSheetData.name.uppercase()}")
    }

    fun updateCharacterPhysics(
        id: String,
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
        isDragging: Boolean,
        currentMovesetName: String? = null
    ) {
        _physicsCharacters.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(
                        x = x,
                        y = y,
                        vx = vx,
                        vy = vy,
                        isDragging = isDragging,
                        currentMovesetName = currentMovesetName ?: it.currentMovesetName
                    )
                } else it
            }
        }
    }

    fun removeCharacter(id: String) {
        val target = _physicsCharacters.value.find { it.id == id }
        _physicsCharacters.update { list -> 
            val updated = list.filter { it.id != id }
            updated
        }
        
        // Remove from Master DB
        val masterList = com.cfks.goosedroid.data.CharacterRepository.loadCharacters(context).filter { it.id != id }
        com.cfks.goosedroid.data.CharacterRepository.saveCharacters(context, masterList)
        
        if (target != null) {
            // Unregister from registry as well, effectively deleting from database
            com.cfks.goosedroid.brain.CharacterRegistry.unregisterUnit(id)
            showHud("UNIT DECOMMISSIONED: ${target.spriteSheetData.name.uppercase()}")
        }
    }

    fun warpCharacter(id: String): PhysicsCharacter? {
        val target = _physicsCharacters.value.find { it.id == id }
        if (target != null) {
            _physicsCharacters.update { list -> 
                val updated = list.filter { it.id != id }
                updated
            }
            showHud("WARP INITIATED: ${target.spriteSheetData.name.uppercase()}")
        }
        return target
    }

    fun getCharacterById(id: String): PhysicsCharacter? {
        val masterList = com.cfks.goosedroid.data.CharacterRepository.loadCharacters(context)
        return masterList.find { it.id == id }
    }
    fun moveCharacterBy(id: String, dx: Float, dy: Float) {
        _physicsCharacters.update { list ->
            list.map { if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it }
        }
    }
}

