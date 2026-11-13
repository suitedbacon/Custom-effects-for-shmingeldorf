package com.suitedbacon.fanum.api;

/** The replacement flag is synced through Minecraft's entity data tracker.
 *  Accessor interfaces must never live in a configured Mixin package.
 */
public interface FanumReplaced {
    boolean fanum$isReplaced();
    void fanum$setReplaced(boolean value);
}
