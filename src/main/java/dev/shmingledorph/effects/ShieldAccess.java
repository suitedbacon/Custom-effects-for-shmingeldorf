package dev.shmingledorph.effects;

/** Synced client/server shield pool, measured in vanilla health points (2 = 1 heart). */
public interface ShieldAccess {
    float shmingledorph$getShield();
    float shmingledorph$getShieldMax();
    void shmingledorph$setShield(float value);
    void shmingledorph$setShieldMax(float value);
}
