package com.cfks.goosedroid.GooseDesktop;

import com.cfks.goosedroid.SamEngine.*;

/**
 * Advanced skeletal rig for the goose.
 * Handles body parts, expressions, animations, and poses.
 */
public class GooseRig {

    // ============== BONE STRUCTURE ==============

    /**
     * Represents a single bone/joint in the skeleton.
     */
    public static class Bone {
        public Vector2 position;
        public float rotation;
        public float length;
        public float radius;

        public Bone(float radius, float length) {
            this.radius = radius;
            this.length = length;
            this.position = Vector2.zero;
            this.rotation = 0f;
        }

        public Vector2 getEndPoint(Vector2 direction) {
            return Vector2.add(position, Vector2.multiply(direction, length));
        }
    }

    // ============== EXPRESSION SYSTEM ==============

    /**
     * Facial expressions for emotional states.
     */
    public enum Expression {
        NEUTRAL,    // Normal eyes
        HAPPY,      // Eyes curved up (^_^)
        SAD,        // Eyes droopy
        SLEEPY,     // Eyes half closed
        SURPRISED,  // Eyes wide
        ANGRY,      // Eyes narrowed, angled
        LOVE        // Heart eyes effect
    }

    // ============== POSE SYSTEM ==============

    /**
     * Predefined body poses.
     */
    public enum Pose {
        NORMAL,     // Standard standing
        ALERT,      // Neck extended, attentive
        RELAXED,    // Neck down, casual
        SLEEPING,   // Curled up, eyes closed
        EATING,     // Neck down, pecking
        EXCITED,    // Bouncy, energetic
        SCARED      // Cowering, neck retracted
    }

    // ============== BONES ==============

    public final Bone underbody;
    public final Bone body;
    public final Bone neck;
    public final Bone head1;
    public final Bone head2;
    public final Bone beak;
    public final Bone leftEye;
    public final Bone rightEye;
    public final Bone leftWing;
    public final Bone rightWing;
    public final Bone tail;

    // ============== COMPUTED POSITIONS ==============

    public Vector2 underbodyCenter;
    public Vector2 bodyCenter;
    public Vector2 neckBase;
    public Vector2 neckHeadPoint;
    public Vector2 head1EndPoint;
    public Vector2 head2EndPoint;
    public Vector2 beakEnd;
    public Vector2 leftEyePos;
    public Vector2 rightEyePos;
    public Vector2 leftWingPos;
    public Vector2 rightWingPos;
    public Vector2 tailPos;
    public Vector2 tailEnd;

    // Wing animation state
    private float wingFlapPhase = 0f;
    private float wingFlapSpeed = 0f;  // 0 = idle, higher = faster flapping
    private float tailWagPhase = 0f;

    // ============== ANIMATION STATE ==============

    private float neckLerpPercent = 0f;
    private float breathPhase = 0f;
    private float blinkTimer = 0f;
    private float blinkDuration = 0f;
    private boolean isBlinking = false;
    private float expressionBlend = 0f;
    private float poseBlend = 0f;

    // Current and target states
    private Expression currentExpression = Expression.NEUTRAL;
    private Expression targetExpression = Expression.NEUTRAL;
    private Pose currentPose = Pose.NORMAL;
    private Pose targetPose = Pose.NORMAL;

    // ============== SQUASH & STRETCH ==============

    private float squashAmount = 0f;       // Current squash (0 = normal, 1 = max squash)
    private float targetSquash = 0f;       // Target squash amount
    private float stretchAmount = 0f;      // Current stretch
    private float targetStretch = 0f;      // Target stretch amount
    private float squashRecoverySpeed = 5f; // How fast to recover from squash

    // ============== SECONDARY MOTION ==============

    private Vector2 tailLag = new Vector2(0, 0);      // Accumulated tail lag
    private Vector2 wingLag = new Vector2(0, 0);      // Accumulated wing lag
    private float bellyWobble = 0f;                    // Belly wobble amount
    private float bellyWobbleVelocity = 0f;            // Belly wobble velocity
    private Vector2 lastPosition = new Vector2(0, 0); // For calculating velocity
    private Vector2 velocity = new Vector2(0, 0);     // Current movement velocity

    // ============== EXPRESSION DETAILS ==============

    private float beakOpenAmount = 0f;     // 0 = closed, 1 = fully open
    private float targetBeakOpen = 0f;     // Target beak opening
    private float browCurve = 0f;          // -1 = sad curve, 0 = neutral, 1 = happy curve
    private float targetBrowCurve = 0f;    // Target brow curve
    private float tearAmount = 0f;         // Tear effect for SAD expression

    // ============== SCALE ==============

    private float scale = 1.0f;
    private float targetScale = 1.0f;

    // ============== CONSTANTS ==============

    private static final float BREATH_SPEED = 2.5f;
    private static final float BREATH_AMPLITUDE = 0.8f;
    private static final float BLINK_INTERVAL_MIN = 2f;
    private static final float BLINK_INTERVAL_MAX = 5f;
    private static final float BLINK_DURATION = 0.15f;
    private static final float EXPRESSION_BLEND_SPEED = 5f;
    private static final float POSE_BLEND_SPEED = 3f;
    private static final float SCALE_BLEND_SPEED = 2f;

    // Squash/Stretch constants
    private static final float SQUASH_RECOVERY_SPEED = 8f;
    private static final float MAX_SQUASH = 0.2f;        // Maximum squash deformation
    private static final float MAX_STRETCH = 0.15f;      // Maximum stretch deformation

    // Secondary motion constants
    private static final float TAIL_LAG_FACTOR = 0.15f;   // How much tail lags behind
    private static final float TAIL_RECOVERY_SPEED = 5f;  // How fast tail catches up
    private static final float WING_LAG_FACTOR = 0.1f;    // How much wings lag
    private static final float WING_RECOVERY_SPEED = 7f;  // How fast wings catch up
    private static final float BELLY_WOBBLE_DAMPING = 3f; // Belly wobble friction
    private static final float BELLY_WOBBLE_SPRING = 15f; // Belly wobble springiness

    // Expression animation constants
    private static final float BEAK_ANIMATION_SPEED = 8f;
    private static final float BROW_ANIMATION_SPEED = 4f;

    // ============== CONSTRUCTOR ==============

    public GooseRig() {
        // Initialize bones with default sizes
        underbody = new Bone(15, 7);
        body = new Bone(22, 11);
        neck = new Bone(13, 20);
        head1 = new Bone(15, 3);
        head2 = new Bone(10, 5);
        beak = new Bone(4, 3);
        leftEye = new Bone(2, 0);
        rightEye = new Bone(2, 0);
        leftWing = new Bone(8, 18);
        rightWing = new Bone(8, 18);
        tail = new Bone(6, 12);

        // Initialize blink timer
        resetBlinkTimer();
    }

    // ============== UPDATE ==============

    /**
     * Update rig with full animation support.
     */
    public void update(Vector2 position, float direction, float deltaTime) {
        // Calculate velocity for secondary motion
        velocity = Vector2.multiply(Vector2.subtract(position, lastPosition), 1f / Math.max(deltaTime, 0.001f));
        lastPosition = new Vector2(position.x, position.y);

        // Update animations
        updateBreathing(deltaTime);
        updateBlinking(deltaTime);
        updateExpressionBlend(deltaTime);
        updatePoseBlend(deltaTime);
        updateScaleBlend(deltaTime);

        // Update new animation systems
        updateSquashStretch(deltaTime);
        updateSecondaryMotion(deltaTime);
        updateExpressionDetails(deltaTime);

        // Apply pose modifications
        float poseNeckMod = getPoseNeckModifier();
        float poseHeightMod = getPoseHeightModifier();

        // Apply squash/stretch to scale
        float squashScaleX = 1f + squashAmount * 0.15f - stretchAmount * 0.1f;
        float squashScaleY = 1f - squashAmount * 0.15f + stretchAmount * 0.1f;

        // Calculate base vectors with squash/stretch applied
        Vector2 basePos = new Vector2(position.x, position.y);
        Vector2 scaleVec = new Vector2(1.3f * scale * squashScaleX, 0.4f * scale * squashScaleY);

        Vector2 forward = Vector2.GetFromAngleDegrees(direction);
        forward = Vector2.multiply(forward, scaleVec);
        Vector2 right = Vector2.GetFromAngleDegrees(direction + 90f);
        right = Vector2.multiply(right, scaleVec);

        Vector2 up = new Vector2(0f, -1f);

        // Apply breathing offset
        float breathOffset = (float) Math.sin(breathPhase) * BREATH_AMPLITUDE * scale;

        // Calculate body positions with belly wobble
        float bodyElevation = (14f + breathOffset) * scale * poseHeightMod;
        float underbodyElevation = (9f + breathOffset * 0.5f + bellyWobble) * scale * poseHeightMod;

        underbodyCenter = Vector2.add(basePos, Vector2.multiply(up, underbodyElevation));
        bodyCenter = Vector2.add(basePos, Vector2.multiply(up, bodyElevation));

        // Calculate neck with pose modification
        float neckHeight = SamMath.Lerp(20f, 10f, neckLerpPercent) * scale * poseNeckMod;
        float neckExtend = SamMath.Lerp(3f, 16f, neckLerpPercent) * scale;

        neckBase = Vector2.add(bodyCenter, Vector2.multiply(forward, 15f * scale));
        neckHeadPoint = Vector2.add(
            Vector2.add(neckBase, Vector2.multiply(forward, neckExtend)),
            Vector2.multiply(up, neckHeight)
        );

        // Calculate head positions
        head1EndPoint = Vector2.subtract(
            Vector2.add(neckHeadPoint, Vector2.multiply(forward, 3f * scale)),
            Vector2.multiply(up, 1f * scale)
        );
        head2EndPoint = Vector2.add(head1EndPoint, Vector2.multiply(forward, 5f * scale));

        // Calculate beak
        beakEnd = Vector2.add(head2EndPoint, Vector2.multiply(forward, 3f * scale));

        // Calculate eye positions with expression offset
        Vector2 eyeOffset = getExpressionEyeOffset();
        float eyeSpacing = 5f * scale;
        float eyeForward = 5f * scale;
        float eyeUp = 3f * scale;

        Vector2 eyeBase = Vector2.add(
            Vector2.add(neckHeadPoint, Vector2.multiply(up, eyeUp)),
            Vector2.multiply(forward, eyeForward)
        );

        leftEyePos = Vector2.add(
            Vector2.subtract(eyeBase, Vector2.multiply(right, eyeSpacing)),
            eyeOffset
        );
        rightEyePos = Vector2.add(
            Vector2.add(eyeBase, Vector2.multiply(right, eyeSpacing)),
            eyeOffset
        );

        // Calculate wing positions
        updateWings(deltaTime);
        float wingAngle = getWingAngle();
        Vector2 wingOffset = Vector2.multiply(up, 12f * scale);

        // Left wing - on the left side of the body
        leftWingPos = Vector2.add(
            Vector2.subtract(bodyCenter, Vector2.multiply(right, 8f * scale)),
            wingOffset
        );

        // Right wing - on the right side of the body
        rightWingPos = Vector2.add(
            Vector2.add(bodyCenter, Vector2.multiply(right, 8f * scale)),
            wingOffset
        );

        // Calculate tail position (behind the body) with secondary motion lag
        updateTail(deltaTime);
        Vector2 tailOffset = Vector2.multiply(forward, -15f * scale);
        tailPos = Vector2.add(bodyCenter, tailOffset);

        float tailAngle = getTailWagAngle();
        Vector2 tailDirection = Vector2.add(
            Vector2.multiply(forward, -1f),
            Vector2.multiply(right, tailAngle * 0.3f)
        );

        // Apply tail lag for secondary motion
        Vector2 tailLagOffset = Vector2.multiply(tailLag, scale * 0.5f);
        tailEnd = Vector2.add(
            Vector2.add(tailPos, Vector2.multiply(Vector2.Normalize(tailDirection), 12f * scale)),
            tailLagOffset
        );

        // Update bone positions
        underbody.position = underbodyCenter;
        body.position = bodyCenter;
        neck.position = neckBase;
        head1.position = neckHeadPoint;
        head2.position = head1EndPoint;
        beak.position = head2EndPoint;
        leftEye.position = leftEyePos;
        rightEye.position = rightEyePos;
        leftWing.position = leftWingPos;
        rightWing.position = rightWingPos;
        tail.position = tailPos;
    }

    // ============== WING & TAIL ANIMATION ==============

    private void updateWings(float deltaTime) {
        // Wing flapping animation
        if (wingFlapSpeed > 0) {
            wingFlapPhase += wingFlapSpeed * deltaTime;
            if (wingFlapPhase > Math.PI * 2) {
                wingFlapPhase -= (float)(Math.PI * 2);
            }
        } else {
            // Gentle idle wing movement synced with breathing
            wingFlapPhase = breathPhase * 0.3f;
        }
    }

    private void updateTail(float deltaTime) {
        // Tail wagging - faster when happy/excited
        float wagSpeed = 2f;
        if (currentPose == Pose.EXCITED) wagSpeed = 6f;
        else if (currentExpression == Expression.HAPPY) wagSpeed = 4f;

        tailWagPhase += wagSpeed * deltaTime;
        if (tailWagPhase > Math.PI * 2) {
            tailWagPhase -= (float)(Math.PI * 2);
        }
    }

    public float getWingAngle() {
        if (wingFlapSpeed > 0) {
            // Active flapping
            return (float)Math.sin(wingFlapPhase) * 30f;
        } else {
            // Subtle breathing movement
            return (float)Math.sin(breathPhase) * 5f;
        }
    }

    public float getTailWagAngle() {
        float amplitude = 0.3f;
        if (currentPose == Pose.EXCITED) amplitude = 0.8f;
        else if (currentExpression == Expression.HAPPY) amplitude = 0.5f;
        return (float)Math.sin(tailWagPhase) * amplitude;
    }

    public void setWingFlapSpeed(float speed) {
        this.wingFlapSpeed = Math.max(0, Math.min(20f, speed));
    }

    public float getWingFlapPhase() {
        return wingFlapPhase;
    }

    /**
     * Simple update without delta time (backwards compatible).
     */
    public void update(Vector2 position, float direction) {
        update(position, direction, Time.deltaTime);
    }

    // ============== ANIMATION UPDATES ==============

    private void updateBreathing(float deltaTime) {
        breathPhase += BREATH_SPEED * deltaTime;
        if (breathPhase > Math.PI * 2) {
            breathPhase -= (float) (Math.PI * 2);
        }
    }

    private void updateBlinking(float deltaTime) {
        blinkTimer -= deltaTime;

        if (blinkTimer <= 0 && !isBlinking) {
            // Start blink
            isBlinking = true;
            blinkDuration = BLINK_DURATION;
        }

        if (isBlinking) {
            blinkDuration -= deltaTime;
            if (blinkDuration <= 0) {
                isBlinking = false;
                resetBlinkTimer();
            }
        }
    }

    private void resetBlinkTimer() {
        blinkTimer = SamMath.RandomRange(BLINK_INTERVAL_MIN, BLINK_INTERVAL_MAX);
    }

    private void updateExpressionBlend(float deltaTime) {
        if (currentExpression != targetExpression) {
            expressionBlend += EXPRESSION_BLEND_SPEED * deltaTime;
            if (expressionBlend >= 1f) {
                expressionBlend = 0f;
                currentExpression = targetExpression;
            }
        }
    }

    private void updatePoseBlend(float deltaTime) {
        if (currentPose != targetPose) {
            poseBlend += POSE_BLEND_SPEED * deltaTime;
            if (poseBlend >= 1f) {
                poseBlend = 0f;
                currentPose = targetPose;
            }
        }
    }

    private void updateScaleBlend(float deltaTime) {
        if (Math.abs(scale - targetScale) > 0.01f) {
            scale = SamMath.Lerp(scale, targetScale, SCALE_BLEND_SPEED * deltaTime);
        } else {
            scale = targetScale;
        }
    }

    // ============== POSE MODIFIERS ==============

    private float getPoseNeckModifier() {
        float baseMod = getPoseNeckModifierForPose(currentPose);
        if (currentPose != targetPose) {
            float targetMod = getPoseNeckModifierForPose(targetPose);
            return SamMath.Lerp(baseMod, targetMod, poseBlend);
        }
        return baseMod;
    }

    private float getPoseNeckModifierForPose(Pose pose) {
        switch (pose) {
            case ALERT:     return 1.3f;
            case RELAXED:   return 0.7f;
            case SLEEPING:  return 0.4f;
            case EATING:    return 0.5f;
            case EXCITED:   return 1.1f;
            case SCARED:    return 0.6f;
            default:        return 1.0f;
        }
    }

    private float getPoseHeightModifier() {
        float baseMod = getPoseHeightModifierForPose(currentPose);
        if (currentPose != targetPose) {
            float targetMod = getPoseHeightModifierForPose(targetPose);
            return SamMath.Lerp(baseMod, targetMod, poseBlend);
        }
        return baseMod;
    }

    private float getPoseHeightModifierForPose(Pose pose) {
        switch (pose) {
            case ALERT:     return 1.1f;
            case RELAXED:   return 0.9f;
            case SLEEPING:  return 0.6f;
            case EATING:    return 0.85f;
            case EXCITED:   return 1.05f + (float) Math.sin(breathPhase * 3) * 0.05f;
            case SCARED:    return 0.75f;
            default:        return 1.0f;
        }
    }

    // ============== EXPRESSION MODIFIERS ==============

    private Vector2 getExpressionEyeOffset() {
        Vector2 baseOffset = getExpressionEyeOffsetForExpression(currentExpression);
        if (currentExpression != targetExpression) {
            Vector2 targetOffset = getExpressionEyeOffsetForExpression(targetExpression);
            return Vector2.Lerp(baseOffset, targetOffset, expressionBlend);
        }
        return baseOffset;
    }

    private Vector2 getExpressionEyeOffsetForExpression(Expression expr) {
        switch (expr) {
            case HAPPY:     return new Vector2(0, -1f * scale);
            case SAD:       return new Vector2(0, 1.5f * scale);
            case SLEEPY:    return new Vector2(0, 0.5f * scale);
            case SURPRISED: return new Vector2(0, -1.5f * scale);
            case ANGRY:     return new Vector2(0, 0.5f * scale);
            case LOVE:      return new Vector2(0, 0);
            default:        return Vector2.zero;
        }
    }

    // ============== PUBLIC GETTERS ==============

    public float getNeckLerpPercent() {
        return neckLerpPercent;
    }

    public void setNeckLerpPercent(float percent) {
        this.neckLerpPercent = percent;
    }

    public boolean isBlinking() {
        return isBlinking;
    }

    public float getBlinkAmount() {
        if (!isBlinking) return 0f;
        // 0 to 1 to 0 over blink duration
        float progress = 1f - (blinkDuration / BLINK_DURATION);
        return (float) Math.sin(progress * Math.PI);
    }

    public Expression getCurrentExpression() {
        return currentExpression;
    }

    public Pose getCurrentPose() {
        return currentPose;
    }

    public float getScale() {
        return scale;
    }

    public float getBreathPhase() {
        return breathPhase;
    }

    // ============== PUBLIC SETTERS ==============

    /**
     * Set facial expression with smooth transition.
     */
    public void setExpression(Expression expression) {
        if (expression != targetExpression) {
            targetExpression = expression;
            expressionBlend = 0f;
        }
    }

    /**
     * Set body pose with smooth transition.
     */
    public void setPose(Pose pose) {
        if (pose != targetPose) {
            targetPose = pose;
            poseBlend = 0f;
        }
    }

    /**
     * Set scale with smooth transition.
     */
    public void setScale(float newScale) {
        targetScale = Math.max(0.5f, Math.min(2.0f, newScale));
    }

    /**
     * Set scale immediately without transition.
     */
    public void setScaleImmediate(float newScale) {
        scale = Math.max(0.5f, Math.min(2.0f, newScale));
        targetScale = scale;
    }

    /**
     * Force immediate expression change.
     */
    public void setExpressionImmediate(Expression expression) {
        currentExpression = expression;
        targetExpression = expression;
        expressionBlend = 0f;
    }

    /**
     * Force immediate pose change.
     */
    public void setPoseImmediate(Pose pose) {
        currentPose = pose;
        targetPose = pose;
        poseBlend = 0f;
    }

    // ============== UTILITY METHODS ==============

    /**
     * Get eye radius considering blink state.
     */
    public float getEffectiveEyeRadius() {
        float baseRadius = leftEye.radius * scale;
        if (isBlinking) {
            return baseRadius * (1f - getBlinkAmount() * 0.8f);
        }
        // Sleepy expression also affects eye size
        if (currentExpression == Expression.SLEEPY) {
            return baseRadius * 0.6f;
        }
        return baseRadius;
    }

    /**
     * Get eye vertical scale for expressions.
     */
    public float getEyeVerticalScale() {
        if (isBlinking) {
            return 1f - getBlinkAmount() * 0.9f;
        }
        switch (currentExpression) {
            case HAPPY:     return 0.5f;  // Curved up eyes
            case SAD:       return 1.2f;  // Droopy
            case SLEEPY:    return 0.3f;  // Half closed
            case SURPRISED: return 1.4f;  // Wide
            case ANGRY:     return 0.6f;  // Narrowed
            default:        return 1.0f;
        }
    }

    /**
     * Check if love expression is active (for heart eyes).
     */
    public boolean hasHeartEyes() {
        return currentExpression == Expression.LOVE || targetExpression == Expression.LOVE;
    }

    /**
     * Get body scale from breathing.
     */
    public float getBreathBodyScale() {
        return 1f + (float) Math.sin(breathPhase) * 0.02f;
    }

    // ============== SQUASH & STRETCH ==============

    /**
     * Update squash and stretch deformation.
     */
    private void updateSquashStretch(float deltaTime) {
        // Smoothly interpolate towards target
        squashAmount = SamMath.Lerp(squashAmount, targetSquash, SQUASH_RECOVERY_SPEED * deltaTime);
        stretchAmount = SamMath.Lerp(stretchAmount, targetStretch, SQUASH_RECOVERY_SPEED * deltaTime);

        // Auto-recover to normal
        targetSquash = SamMath.Lerp(targetSquash, 0f, squashRecoverySpeed * deltaTime);
        targetStretch = SamMath.Lerp(targetStretch, 0f, squashRecoverySpeed * deltaTime);

        // Clamp values
        squashAmount = SamMath.Clamp(squashAmount, 0f, MAX_SQUASH);
        stretchAmount = SamMath.Clamp(stretchAmount, 0f, MAX_STRETCH);
    }

    /**
     * Trigger squash effect (e.g., on landing).
     */
    public void triggerSquash(float amount) {
        targetSquash = Math.min(amount, MAX_SQUASH);
        // Squash also triggers belly wobble
        bellyWobbleVelocity += amount * 10f;
    }

    /**
     * Trigger stretch effect (e.g., during jump).
     */
    public void triggerStretch(float amount) {
        targetStretch = Math.min(amount, MAX_STRETCH);
    }

    /**
     * Get current squash amount for external use.
     */
    public float getSquashAmount() {
        return squashAmount;
    }

    /**
     * Get current stretch amount for external use.
     */
    public float getStretchAmount() {
        return stretchAmount;
    }

    // ============== SECONDARY MOTION ==============

    /**
     * Update secondary motion (lag, wobble effects).
     */
    private void updateSecondaryMotion(float deltaTime) {
        // Calculate movement delta for lag
        float speed = Vector2.Distance(Vector2.zero, velocity);

        // Update tail lag - tail follows with delay
        Vector2 targetTailLag = Vector2.multiply(velocity, -TAIL_LAG_FACTOR);
        tailLag = Vector2.Lerp(tailLag, targetTailLag, TAIL_RECOVERY_SPEED * deltaTime);

        // Update wing lag - wings follow with slight delay
        Vector2 targetWingLag = Vector2.multiply(velocity, -WING_LAG_FACTOR);
        wingLag = Vector2.Lerp(wingLag, targetWingLag, WING_RECOVERY_SPEED * deltaTime);

        // Update belly wobble (spring physics)
        // Spring force pulls back to center
        float springForce = -BELLY_WOBBLE_SPRING * bellyWobble;
        // Damping reduces velocity
        float dampingForce = -BELLY_WOBBLE_DAMPING * bellyWobbleVelocity;

        bellyWobbleVelocity += (springForce + dampingForce) * deltaTime;
        bellyWobble += bellyWobbleVelocity * deltaTime;

        // Add wobble from rapid movement
        if (speed > 50f) {
            bellyWobbleVelocity += (float)Math.sin(Time.time * 15f) * speed * 0.001f;
        }

        // Clamp wobble
        bellyWobble = SamMath.Clamp(bellyWobble, -2f, 2f);
    }

    /**
     * Get tail lag offset for rendering.
     */
    public Vector2 getTailLag() {
        return tailLag;
    }

    /**
     * Get wing lag offset for rendering.
     */
    public Vector2 getWingLag() {
        return wingLag;
    }

    /**
     * Get belly wobble amount.
     */
    public float getBellyWobble() {
        return bellyWobble;
    }

    // ============== EXPRESSION DETAILS ==============

    /**
     * Update detailed expression animations.
     */
    private void updateExpressionDetails(float deltaTime) {
        // Update beak animation
        beakOpenAmount = SamMath.Lerp(beakOpenAmount, targetBeakOpen, BEAK_ANIMATION_SPEED * deltaTime);

        // Update brow curve
        browCurve = SamMath.Lerp(browCurve, targetBrowCurve, BROW_ANIMATION_SPEED * deltaTime);

        // Auto-set targets based on expression
        switch (currentExpression) {
            case SURPRISED:
                targetBeakOpen = 0.3f;  // Slightly open
                targetBrowCurve = 0.5f; // Raised
                break;
            case HAPPY:
                targetBeakOpen = 0.1f;  // Slightly open smile
                targetBrowCurve = 0.3f; // Slightly raised
                break;
            case SAD:
                targetBeakOpen = 0f;    // Closed
                targetBrowCurve = -0.6f; // Drooping
                tearAmount = SamMath.Lerp(tearAmount, 1f, deltaTime);
                break;
            case ANGRY:
                targetBeakOpen = 0.15f;
                targetBrowCurve = -0.4f; // Furrowed (converging)
                break;
            case SLEEPY:
                targetBeakOpen = 0.05f;
                targetBrowCurve = -0.2f;
                break;
            case LOVE:
                targetBeakOpen = 0.2f;
                targetBrowCurve = 0.4f;
                break;
            default:
                targetBeakOpen = 0f;
                targetBrowCurve = 0f;
                tearAmount = SamMath.Lerp(tearAmount, 0f, deltaTime * 2f);
                break;
        }

        // Clamp values
        beakOpenAmount = SamMath.Clamp(beakOpenAmount, 0f, 1f);
        browCurve = SamMath.Clamp(browCurve, -1f, 1f);
        tearAmount = SamMath.Clamp(tearAmount, 0f, 1f);
    }

    /**
     * Manually set beak open amount (e.g., for eating animation).
     */
    public void setBeakOpen(float amount) {
        targetBeakOpen = SamMath.Clamp(amount, 0f, 1f);
    }

    /**
     * Get current beak open amount.
     */
    public float getBeakOpenAmount() {
        return beakOpenAmount;
    }

    /**
     * Get current brow curve.
     */
    public float getBrowCurve() {
        return browCurve;
    }

    /**
     * Get tear amount for SAD expression.
     */
    public float getTearAmount() {
        return tearAmount;
    }

    /**
     * Trigger eating animation (beak opens and closes).
     */
    public void triggerEatingAnimation() {
        // Oscillate beak
        float eatPhase = (float)Math.sin(Time.time * 8f);
        targetBeakOpen = 0.3f + eatPhase * 0.2f;
    }

    // ============== ANTICIPATION ==============

    /**
     * Trigger anticipation before a big action.
     * @param type 0 = generic, 1 = jump prep, 2 = peck prep
     */
    public void triggerAnticipation(int type) {
        switch (type) {
            case 1: // Jump preparation - crouch down
                triggerSquash(0.1f);
                targetBrowCurve = 0.3f; // Focus
                break;
            case 2: // Peck preparation - neck retracts
                neckLerpPercent = Math.max(0, neckLerpPercent - 0.2f);
                break;
            default:
                // Generic anticipation
                triggerSquash(0.05f);
                break;
        }
    }

    /**
     * Reset all animations to default state.
     */
    public void reset() {
        currentExpression = Expression.NEUTRAL;
        targetExpression = Expression.NEUTRAL;
        currentPose = Pose.NORMAL;
        targetPose = Pose.NORMAL;
        scale = 1.0f;
        targetScale = 1.0f;
        breathPhase = 0f;
        isBlinking = false;
        resetBlinkTimer();

        // Reset new animation states
        squashAmount = 0f;
        targetSquash = 0f;
        stretchAmount = 0f;
        targetStretch = 0f;
        tailLag = new Vector2(0, 0);
        wingLag = new Vector2(0, 0);
        bellyWobble = 0f;
        bellyWobbleVelocity = 0f;
        beakOpenAmount = 0f;
        targetBeakOpen = 0f;
        browCurve = 0f;
        targetBrowCurve = 0f;
        tearAmount = 0f;
    }
}
