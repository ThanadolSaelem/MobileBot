package com.cfks.goosedroid.brain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * CharacterRegistry - จัดการและจัดเก็บรายชื่อตัวละครที่ active อยู่บน Overlay
 * รับประกันว่าชื่อของตัวละครทุกตัวไม่ซ้ำกัน (สูงสุด 10 ตัว)
 * เพื่อใช้เป็น Context ในการระบุตัวตนและสื่อสารกับ LLM แยกกัน
 */
object CharacterRegistry {
    const val MAX_OVERLAY_UNITS = 10

    private val _activeOverlayUnits = MutableStateFlow<List<CharacterUnitInfo>>(emptyList())
    val activeOverlayUnits: StateFlow<List<CharacterUnitInfo>> = _activeOverlayUnits.asStateFlow()

    // Context / Memory แยกตามชื่อตัวละครแต่ละตัว
    private val characterHistories = mutableMapOf<String, MutableList<String>>()
    private val spriteDataMap = mutableMapOf<String, com.cfks.goosedroid.model.SpriteSheetData>()

    fun registerCharacterData(id: String, data: com.cfks.goosedroid.model.SpriteSheetData) {
        spriteDataMap[id] = data
    }

    fun getCharacterData(id: String): com.cfks.goosedroid.model.SpriteSheetData? = spriteDataMap[id]

    fun getActiveCount(): Int = _activeOverlayUnits.value.size

    fun isMaxLimitReached(): Boolean = getActiveCount() >= MAX_OVERLAY_UNITS

    /**
     * ตรวจสอบว่าชื่อนี้กำลังถูกใช้งานอยู่ในระบบหรือไม่ (Case-insensitive)
     */
    fun isNameTaken(name: String, excludeId: String? = null): Boolean {
        val clean = name.trim().lowercase()
        return _activeOverlayUnits.value.any { it.name.trim().lowercase() == clean && it.id != excludeId }
    }

    /**
     * ตรวจสอบและสร้างชื่อเฉพาะ (Unique Name) โดยไม่มีทางซ้ำกับตัวละครที่กำลังรันอยู่บนหน้าจอ
     */
    fun resolveUniqueName(baseName: String): String {
        val activeNames = _activeOverlayUnits.value.map { it.name.trim().lowercase() }.toSet()
        val cleanBase = baseName.trim().ifEmpty { "UNIT" }

        if (!activeNames.contains(cleanBase.lowercase())) {
            return cleanBase
        }

        // หา index ถัดไป เช่น GOOSE-02, GOOSE-03
        var index = 2
        while (activeNames.contains("${cleanBase.lowercase()}-$index") || activeNames.contains("${cleanBase.lowercase()} #$index")) {
            index++
        }
        return "$cleanBase-$index"
    }

    fun registerUnit(
        id: String,
        uniqueName: String,
        spriteUri: String?,
        cols: Int,
        rows: Int,
        moveSets: List<com.cfks.goosedroid.model.AnimationSequence> = emptyList()
    ) {
        _activeOverlayUnits.update { list ->
            list.filter { it.id != id } + CharacterUnitInfo(
                id = id,
                name = uniqueName,
                spriteUri = spriteUri,
                columns = cols,
                rows = rows,
                moveSets = moveSets
            )
        }
        if (!characterHistories.containsKey(uniqueName)) {
            val movesSummary = if (moveSets.isNotEmpty()) {
                "ท่าทาง: " + moveSets.joinToString(", ") { 
                    val desc = if (it.description.isNotBlank()) " (${it.description})" else ""
                    "${it.name}$desc"
                }
            } else "พร้อมปฏิบัติหน้าที่"
            characterHistories[uniqueName] = mutableListOf("UNIT_INITIALIZED", movesSummary)
        }
    }

    fun getUnitInfo(name: String): CharacterUnitInfo? {
        return _activeOverlayUnits.value.find { it.name.trim().equals(name.trim(), ignoreCase = true) }
    }

    fun unregisterUnit(id: String) {
        val target = _activeOverlayUnits.value.find { it.id == id }
        _activeOverlayUnits.update { list ->
            list.filter { it.id != id }
        }
        // Retain character history even when unregistered so it survives recalls between Overlay and Playground
    }

    fun getActiveNames(): List<String> = _activeOverlayUnits.value.map { it.name }

    fun addInteractionLog(characterName: String, log: String) {
        val history = characterHistories.getOrPut(characterName) { mutableListOf() }
        if (history.size > 20) {
            history.removeAt(0)
        }
        history.add(log)
    }

    fun getInteractionContext(characterName: String): String {
        val history = characterHistories[characterName] ?: return "สถานะ: พร้อมปฏิบัติหน้าที่"
        return history.takeLast(5).joinToString(" | ")
    }

    // Characters that were recalled from overlay and should be restored to playground
    private val _recalledCharacters = MutableStateFlow<List<com.cfks.goosedroid.model.PhysicsCharacter>>(emptyList())
    val recalledCharacters: StateFlow<List<com.cfks.goosedroid.model.PhysicsCharacter>> = _recalledCharacters.asStateFlow()

    fun recallCharacterToPlayground(character: com.cfks.goosedroid.model.PhysicsCharacter) {
        _recalledCharacters.update { it + character }
    }

    fun clearRecalled() {
        _recalledCharacters.value = emptyList()
    }

    fun clearAll() {
        _activeOverlayUnits.value = emptyList()
        // Retain characterHistories so they can be resumed when spawning back from Playground
    }
}

data class CharacterUnitInfo(
    val id: String,
    val name: String,
    val spriteUri: String?,
    val columns: Int,
    val rows: Int,
    val moveSets: List<com.cfks.goosedroid.model.AnimationSequence> = emptyList(),
    val spawnedTimestamp: Long = System.currentTimeMillis()
)
