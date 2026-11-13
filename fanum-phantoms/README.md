# Fanum Phantom Rework — Fabric 1.21.11

Commands (operator permission level 2):

- `/fanum` – summon a normal Phantom 3 blocks above the command executor.
- `/fanum normal` – also summon a normal Phantom.
- `/fanum eyestrain` – summon an Eyestrain Phantom with a completely black base texture.
- `/fanum hallucination` – apply the entity command tag `hallucination` to the player.

Eyestrain Phantoms have the `fanum_eyestrain` command tag and synchronized client render data to ensure the texture is visible in multiplayer. Future variants can be added independently.

Build: `gradle build` from this directory with Java 21 and Gradle 9.2.1. 

## v0.1.2
- Move `FanumEyestrain` and `FanumRenderState` interfaces from the reserved Mixin package to `com.suitedbacon.fanum.api`, fixing the Fabric bootstrap `IllegalClassLoadError`.
- The GitHub build runs a client startup smoke test before publishing a JAR.

## v0.2.0 — Eyestrain encounter

- Looking directly at an Eyestrain Phantom from <= 32 blocks away, with no blocks in the way, starts a 20-second encounter.
- The Phantom rapidly lunges toward the player, then orbits slowly at 3.7 blocks around the player's eye level. Its AI is paused for this minigame and restored afterward.
- While active, player receives Resistance V and Darkness (refreshed effects), with swooping/ambient Phantom sounds.
- Eye contact while the encounter is active causes 1 true-health damage every 5 game ticks. This bypasses Resistance, armor, shields and hurt cooldowns. Normal healing from food, potions and regeneration is blocked during the encounter.
- The player survives by **looking away** until the encounter's timer ends. Killing the Eyestrain Phantom, dying, changing dimensions, going creative or moving far away also ends the encounter.
- A short cooldown prevents immediate retriggering; vanilla Phantoms and /fanum hallucination are unchanged.
- A server-side `fanum_eyestrain_active` command tag marks participants during the minigame and is removed on completion.

The Eyestrain event runs server-side; install the JAR on both server and clients for multiplayer.

## v0.3.0 — Replace (no entity spawned)

- `/fanum replace` (operator, player-only): searches for an existing living mob within 20 blocks that stands in light level **7 or lower**. It chooses the nearest eligible mob; if none exists, nothing happens.
- The mob retains its identity, hitbox, model, drops, and health, but for **13 seconds** the client renders its body completely black using a synchronized tracked flag.
- It sprints and repeatedly pathfinds toward the player who ran the command, making loud Enderman screaming sounds roughly every 1.15 seconds.
- After 13 seconds, on mob death, on the player's death/disconnect, or if the mob is too far away, its original skin, sprint state and former AI target are restored.
- The command does **not spawn, summon, clone, or replace a physical entity**. Multiplayer requires clients and server to install the mod for the black model to be shown.
- Works for vanilla and modded `MobEntity` renderers that use Minecraft's standard `LivingEntityRenderer` body render layer. Does not apply to items, boats, projectiles or players.

## v0.4.0 — Eyestrain pack hunt

- When a player first stares at an Eyestrain Phantom, their health is set to **20 health points (10 hearts)** and a single 20-second minigame starts.
- Eye-contact damage accelerates from once every five ticks to once every **three ticks**: 1 direct HP per hit, bypassing Resistance V and armor. Only one hit happens per damage tick, even when several Phantoms are in sight.
- **One lead Eyestrain** rapidly closes in, then chases a point about 4 blocks in front of the player's line of sight with a slight following delay, continually trying to appear in the center of their vision.
- **All other Eyestrains in a 36-block radius join the same encounter** without needing to be stared at individually. They orbit the general area around the player, moving farther outward when the group grows. New nearby Eyestrains are recruited twice per second.
- If the lead Eyestrain dies, the next living pack member takes over tracking the player's gaze. Removing every Eyestrain ends the game.
- Each Phantom's original AI-disabled state is restored afterward, and the group is released on death, logout, dimension changes, timeout or leaving the vicinity.
- Existing `/fanum replace`, `/fanum eyestrain`, and hallucination commands remain available.
