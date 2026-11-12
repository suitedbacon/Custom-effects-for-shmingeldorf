# Fanum Phantom Rework — Fabric 1.21.11

Commands (operator permission level 2):

- `/fanum` – summon a normal Phantom 3 blocks above the command executor.
- `/fanum normal` – also summon a normal Phantom.
- `/fanum eyestrain` – summon an Eyestrain Phantom with a completely black base texture.
- `/fanum hallucination` – apply the entity command tag `hallucination` to the player.

Eyestrain Phantoms have the `fanum_eyestrain` command tag and synchronized client render data to ensure the texture is visible in multiplayer. Future variants can be added independently.

Build: `gradle build` from this directory with Java 21 and Gradle 9.2.1. 
