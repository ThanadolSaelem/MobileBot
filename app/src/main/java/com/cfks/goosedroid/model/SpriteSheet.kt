package com.cfks.goosedroid.model

data class AnimationSequence(
    var name: String = "IDLE",
    var frames: List<Int> = listOf(0), // Frame indices in the sprite sheet grid
    var speedMs: Long = 120L,
    var uri: String? = null,
    var columns: Int = 1,
    var rows: Int = 1,
    var description: String = "", // Purpose / condition / trigger context for LLM
    var dialogue: String = "" // Optional speech/dialogue line when this action triggers
)

data class SpriteSheetData(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String = "New Character",
    var uri: String? = null,
    var columns: Int = 1,
    var rows: Int = 1,
    val moveSets: MutableList<AnimationSequence> = mutableListOf()
)

data class PhysicsCharacter(
    val id: String,
    val spriteSheetData: SpriteSheetData,
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var isDragging: Boolean = false,
    var currentMovesetName: String? = null
)

object MovesetMatcher {
    fun hasIdleMoveset(moveSets: List<AnimationSequence>): Boolean {
        if (moveSets.isEmpty()) return true
        return moveSets.any {
            it.name.contains("idle", true) || it.name.contains("stand", true) ||
                    it.name.contains("rest", true) || it.name.contains("stay", true) ||
                    it.name.contains("still", true) || it.name.contains("หยุด", true) ||
                    it.name.contains("นิ่ง", true) || it.name.contains("ยืน", true) ||
                    it.name.contains("พัก", true)
        }
    }

    fun hasWalkMoveset(moveSets: List<AnimationSequence>): Boolean {
        if (moveSets.isEmpty()) return true
        return moveSets.any {
            it.name.contains("walk", true) || it.name.contains("move", true) ||
                    it.name.contains("patrol", true) || it.name.contains("เดิน", true) ||
                    it.name.contains("ก้าว", true)
        }
    }

    fun hasRunMoveset(moveSets: List<AnimationSequence>): Boolean {
        return moveSets.any {
            it.name.contains("run", true) || it.name.contains("dash", true) ||
                    it.name.contains("sprint", true) || it.name.contains("fast", true) ||
                    it.name.contains("วิ่ง", true)
        }
    }

    fun hasJumpMoveset(moveSets: List<AnimationSequence>): Boolean {
        return moveSets.any {
            it.name.contains("jump", true) || it.name.contains("air", true) ||
                    it.name.contains("fall", true) || it.name.contains("fly", true) ||
                    it.name.contains("กระโดด", true) || it.name.contains("ลอย", true) ||
                    it.name.contains("บิน", true)
        }
    }

    fun getAvailableAutonomousBehaviors(moveSets: List<AnimationSequence>): List<String> {
        if (moveSets.isEmpty()) return listOf("IDLE", "WALK")

        val behaviors = mutableListOf<String>()
        if (hasIdleMoveset(moveSets)) behaviors.add("IDLE")
        if (hasWalkMoveset(moveSets)) behaviors.add("WALK")
        if (hasRunMoveset(moveSets)) behaviors.add("RUN")
        if (hasJumpMoveset(moveSets)) behaviors.add("JUMP")

        // Include any custom named movesets (e.g. "Attack", "Dance", "Sleep", "Taunt")
        val customMoves = moveSets.filter { move ->
            val n = move.name.lowercase()
            !n.contains("idle") && !n.contains("stand") && !n.contains("walk") &&
                    !n.contains("run") && !n.contains("jump") && !n.contains("drag") &&
                    !n.contains("held") && !n.contains("fall") && !n.contains("fly") &&
                    !n.contains("air") && !n.contains("dash") && !n.contains("sprint") &&
                    !n.contains("เดิน") && !n.contains("วิ่ง") && !n.contains("กระโดด") &&
                    !n.contains("ยืน") && !n.contains("หยุด") && !n.contains("นิ่ง")
        }
        for (cm in customMoves) {
            if (!behaviors.contains(cm.name)) {
                behaviors.add(cm.name)
            }
        }

        if (behaviors.isEmpty()) {
            return listOf(moveSets.first().name)
        }
        return behaviors
    }

    fun selectBestMoveset(
        moveSets: List<AnimationSequence>,
        explicitName: String?,
        vx: Float,
        vy: Float,
        isAirborne: Boolean,
        isDragging: Boolean,
        facingLeft: Boolean
    ): AnimationSequence? {
        if (moveSets.isEmpty()) return null

        // 1. Explicit directive from LLM / User (e.g. "Attack", "Dance", or named custom moveset)
        if (!explicitName.isNullOrBlank()) {
            val isGenericState = explicitName.equals("IDLE", true) ||
                    explicitName.equals("WALK", true) ||
                    explicitName.equals("RUN", true) ||
                    explicitName.equals("JUMP", true)

            // If not generic or if an exact match exists, try exact or partial match
            val exact = moveSets.firstOrNull { it.name.equals(explicitName, ignoreCase = true) }
            if (exact != null) return exact

            if (!isGenericState) {
                val partial = moveSets.firstOrNull { it.name.contains(explicitName, ignoreCase = true) }
                if (partial != null) return partial
            }
        }

        // 2. User Touch / Drag
        if (isDragging) {
            val dragMove = moveSets.firstOrNull {
                it.name.contains("drag", true) || it.name.contains("held", true) ||
                        it.name.contains("hold", true) || it.name.contains("grab", true) ||
                        it.name.contains("จับ", true) || it.name.contains("ลาก", true)
            }
            if (dragMove != null) return dragMove
        }

        // 3. Airborne / Jumping / Falling (vy < -1.2f or vy > 2.0f or isAirborne or explicit "JUMP")
        if (isAirborne || vy < -1.2f || vy > 2.0f || explicitName.equals("JUMP", true)) {
            val jumpMoves = moveSets.filter {
                it.name.contains("jump", true) || it.name.contains("air", true) ||
                        it.name.contains("fall", true) || it.name.contains("fly", true) ||
                        it.name.contains("กระโดด", true) || it.name.contains("ลอย", true)
            }
            if (jumpMoves.isNotEmpty()) {
                return selectDirectional(jumpMoves, vx, vy, facingLeft)
            }
            // If character has no jump animation, use walking/moving legs instead of frozen idle
            val walkMoves = moveSets.filter {
                it.name.contains("walk", true) || it.name.contains("run", true) ||
                        it.name.contains("move", true) || it.name.contains("เดิน", true) ||
                        it.name.contains("วิ่ง", true)
            }
            if (walkMoves.isNotEmpty()) {
                return selectDirectional(walkMoves, vx, vy, facingLeft)
            }
        }

        // 4. Running (Speed >= 2.4f or explicit "RUN")
        if (kotlin.math.abs(vx) >= 2.4f || kotlin.math.abs(vy) >= 2.4f || explicitName.equals("RUN", true)) {
            val runMoves = moveSets.filter {
                it.name.contains("run", true) || it.name.contains("dash", true) ||
                        it.name.contains("sprint", true) || it.name.contains("fast", true) ||
                        it.name.contains("วิ่ง", true)
            }
            if (runMoves.isNotEmpty()) {
                return selectDirectional(runMoves, vx, vy, facingLeft)
            }
            // Fallback to walk if no run moveset
            val walkMoves = moveSets.filter {
                it.name.contains("walk", true) || it.name.contains("move", true) ||
                        it.name.contains("patrol", true) || it.name.contains("เดิน", true)
            }
            if (walkMoves.isNotEmpty()) {
                return selectDirectional(walkMoves, vx, vy, facingLeft)
            }
        }

        // 5. Walking (Moving horizontally: abs(vx) > 0.1f or explicit "WALK")
        if (kotlin.math.abs(vx) > 0.1f || kotlin.math.abs(vy) > 0.1f || explicitName.equals("WALK", true)) {
            val walkMoves = moveSets.filter {
                it.name.contains("walk", true) || it.name.contains("move", true) ||
                        it.name.contains("patrol", true) || it.name.contains("run", true) ||
                        it.name.contains("เดิน", true) || it.name.contains("ก้าว", true)
            }
            if (walkMoves.isNotEmpty()) {
                return selectDirectional(walkMoves, vx, vy, facingLeft)
            }
            // Fallback to any non-idle moveset
            val nonIdle = moveSets.filter {
                !it.name.contains("idle", true) && !it.name.contains("stand", true) &&
                        !it.name.contains("หยุด", true) && !it.name.contains("นิ่ง", true)
            }
            if (nonIdle.isNotEmpty()) {
                return selectDirectional(nonIdle, vx, vy, facingLeft)
            }
        }

        // 6. Stationary IDLE (Standing still: vx == 0, vy == 0, not airborne)
        val idleMoves = moveSets.filter {
            it.name.contains("idle", true) || it.name.contains("stand", true) ||
                    it.name.contains("rest", true) || it.name.contains("stay", true) ||
                    it.name.contains("still", true) || it.name.contains("หยุด", true) ||
                    it.name.contains("นิ่ง", true) || it.name.contains("ยืน", true) ||
                    it.name.contains("พัก", true)
        }
        if (idleMoves.isNotEmpty()) {
            return selectDirectional(idleMoves, vx, vy, facingLeft)
        }

        // Default: First available moveset
        return moveSets.firstOrNull()
    }

    private fun selectDirectional(candidates: List<AnimationSequence>, vx: Float, vy: Float, facingLeft: Boolean): AnimationSequence {
        if (candidates.size == 1) return candidates[0]

        val isMovingLeft = vx < -0.1f
        val isMovingRight = vx > 0.1f
        val isMovingUp = vy < -0.1f
        val isMovingDown = vy > 0.1f

        // Diagonal
        if (isMovingUp && isMovingLeft) {
            val upLeft = candidates.firstOrNull {
                it.name.contains("upleft", true) || it.name.contains("up-left", true) || it.name.contains("topleft", true)
            }
            if (upLeft != null) return upLeft
        }
        if (isMovingUp && isMovingRight) {
            val upRight = candidates.firstOrNull {
                it.name.contains("upright", true) || it.name.contains("up-right", true) || it.name.contains("topright", true)
            }
            if (upRight != null) return upRight
        }
        if (isMovingDown && isMovingLeft) {
            val downLeft = candidates.firstOrNull {
                it.name.contains("downleft", true) || it.name.contains("down-left", true) || it.name.contains("bottomleft", true)
            }
            if (downLeft != null) return downLeft
        }
        if (isMovingDown && isMovingRight) {
            val downRight = candidates.firstOrNull {
                it.name.contains("downright", true) || it.name.contains("down-right", true) || it.name.contains("bottomright", true)
            }
            if (downRight != null) return downRight
        }

        // 4-way direction priorities if no diagonal or not moving diagonally
        if (isMovingUp) {
            val up = candidates.firstOrNull {
                it.name.contains("up", true) || it.name.contains("back", true) || it.name.contains("top", true) || it.name.contains("หลัง", true)
            }
            if (up != null) return up
        }
        
        if (isMovingDown) {
            val down = candidates.firstOrNull {
                it.name.contains("down", true) || it.name.contains("front", true) || it.name.contains("bottom", true) || it.name.contains("หน้า", true)
            }
            if (down != null) return down
        }

        // Left / Right default matching
        if (facingLeft || isMovingLeft) {
            val left = candidates.firstOrNull {
                it.name.contains("left", true) || it.name.contains("side", true) ||
                        it.name.contains("ซ้าย", true) || it.name.contains("ข้าง", true)
            }
            if (left != null) return left
        } else if (isMovingRight || !facingLeft) {
            val right = candidates.firstOrNull {
                it.name.contains("right", true) || it.name.contains("side", true) ||
                        it.name.contains("ขวา", true) || it.name.contains("ข้าง", true)
            }
            if (right != null) return right
        }

        // Check front / default fallback
        val front = candidates.firstOrNull {
            it.name.contains("front", true) || it.name.contains("down", true) ||
                    it.name.contains("หน้า", true)
        }
        return front ?: candidates.first()
    }
}

