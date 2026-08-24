package com.cfks.goosedroid;

/**
 * Sistema de necesidades tipo Tamagotchi para la mascota virtual.
 * Gestiona hambre, energia y felicidad que decaen con el tiempo.
 */
public class PetNeeds {
    // Valores 0-100
    public float hunger = 50f;      // 100 = muy hambriento
    public float energy = 100f;     // 0 = cansado
    public float happiness = 75f;   // 0 = triste

    // Tasas de decaimiento (por segundo)
    private static final float HUNGER_RATE = 0.5f;    // ~3.3 min para 100
    private static final float ENERGY_RATE = 0.3f;    // ~5.5 min para 100
    private static final float HAPPINESS_RATE = 0.2f; // ~8.3 min para 100

    // Timestamp de ultima actualizacion
    private long lastUpdateTime = System.currentTimeMillis();

    // Estado actual de la mascota
    public enum MoodState {
        HUNGRY,
        TIRED,
        SAD,
        HAPPY,
        NEUTRAL
    }

    public PetNeeds() {
    }

    /**
     * Retorna la instancia singleton via PetState.
     */
    public static PetNeeds get() {
        return PetState.getInstance().needs;
    }

    /**
     * Actualiza las necesidades basandose en el tiempo transcurrido.
     * Debe ser llamado en cada frame del game loop.
     */
    public void update(float deltaTime) {
        hunger = Math.min(100, hunger + HUNGER_RATE * deltaTime);
        energy = Math.max(0, energy - ENERGY_RATE * deltaTime);
        happiness = Math.max(0, happiness - HAPPINESS_RATE * deltaTime);

        // Efectos cruzados - hambre y cansancio afectan felicidad
        if (hunger > 80) {
            happiness = Math.max(0, happiness - 0.1f * deltaTime);
        }
        if (energy < 20) {
            happiness = Math.max(0, happiness - 0.1f * deltaTime);
        }
    }

    /**
     * Actualiza las necesidades considerando el tiempo offline.
     * Util cuando la app se reanuda despues de estar pausada.
     */
    public void updateOfflineTime() {
        long currentTime = System.currentTimeMillis();
        float elapsedSeconds = (currentTime - lastUpdateTime) / 1000f;

        // Limitar el tiempo offline a 1 hora para evitar cambios drasticos
        elapsedSeconds = Math.min(elapsedSeconds, 3600f);

        if (elapsedSeconds > 0) {
            // Decaimiento durante tiempo offline (reducido a 50% de la tasa normal)
            hunger = Math.min(100, hunger + (HUNGER_RATE * 0.5f) * elapsedSeconds);
            energy = Math.max(0, energy - (ENERGY_RATE * 0.5f) * elapsedSeconds);
            happiness = Math.max(0, happiness - (HAPPINESS_RATE * 0.5f) * elapsedSeconds);
        }

        lastUpdateTime = currentTime;
    }

    /**
     * Alimenta a la mascota, reduciendo hambre y aumentando felicidad.
     */
    public void feed() {
        hunger = Math.max(0, hunger - 40);
        happiness = Math.min(100, happiness + 10);
        PetPersonality.get().onFed();
    }

    /**
     * Acaricia a la mascota, aumentando felicidad.
     */
    public void pet() {
        happiness = Math.min(100, happiness + 15);
        PetPersonality.get().onPetted();
    }

    /**
     * La mascota duerme, recuperando energia.
     */
    public void sleep() {
        energy = Math.min(100, energy + 30);
        happiness = Math.min(100, happiness + 5);
    }

    /**
     * Jugar con la mascota, aumenta felicidad pero gasta energia.
     */
    public void play() {
        happiness = Math.min(100, happiness + 20);
        energy = Math.max(0, energy - 10);
        hunger = Math.min(100, hunger + 5);
        PetPersonality.get().onPlayed();
    }

    /**
     * Obtiene el estado de animo actual basado en las necesidades.
     */
    public MoodState getMoodState() {
        if (hunger > 80) return MoodState.HUNGRY;
        if (energy < 20) return MoodState.TIRED;
        if (happiness < 30) return MoodState.SAD;
        if (happiness > 80) return MoodState.HAPPY;
        return MoodState.NEUTRAL;
    }

    /**
     * Obtiene el estado de animo como string para el config.
     */
    public String getMoodStateString() {
        return getMoodState().name().toLowerCase();
    }

    /**
     * Verifica si la mascota necesita atencion urgente.
     */
    public boolean needsUrgentAttention() {
        return hunger > 90 || energy < 10 || happiness < 20;
    }

    /**
     * Verifica si la mascota esta en buen estado general.
     */
    public boolean isHealthy() {
        return hunger < 50 && energy > 50 && happiness > 50;
    }

    /**
     * Obtiene el porcentaje general de bienestar (0-100).
     */
    public float getOverallWellbeing() {
        float hungerScore = 100 - hunger;  // Invertir porque 0 hambre = bueno
        return (hungerScore + energy + happiness) / 3f;
    }

    /**
     * Resetea las necesidades a valores por defecto.
     */
    public void reset() {
        hunger = 50f;
        energy = 100f;
        happiness = 75f;
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Carga el estado desde valores guardados.
     */
    public void loadState(float savedHunger, float savedEnergy, float savedHappiness, long savedTimestamp) {
        hunger = Math.max(0, Math.min(100, savedHunger));
        energy = Math.max(0, Math.min(100, savedEnergy));
        happiness = Math.max(0, Math.min(100, savedHappiness));
        lastUpdateTime = savedTimestamp > 0 ? savedTimestamp : System.currentTimeMillis();
        updateOfflineTime();
    }

    /**
     * Obtiene el timestamp de ultima actualizacion.
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * Actualiza el timestamp (llamar despues de guardar).
     */
    public void markUpdated() {
        lastUpdateTime = System.currentTimeMillis();
    }
}
