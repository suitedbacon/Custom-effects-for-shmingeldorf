# Shmingledorph Effects — Minecraft Java 1.21.11 / Fabric

**DEV BUILD: compiles successfully on GitHub Actions. Not approved for the live SMP.** No Minecraft client or multiplayer test has yet been run.

## Download
Open [GitHub Actions](https://github.com/suitedbacon/Custom-effects-for-shmingeldorf/actions), choose the latest successful **Build Fabric mod** run, and download the `shmingledorph-effects-jar` artifact. Extract the ZIP and put **shmingledorph-effects-0.1.0.jar** in the mods folder of both the Fabric client and Fabric server. Install Fabric API too.

The compiled JAR is **not** the source ZIP. Java 21, Minecraft 1.21.11 and Fabric Loader 0.18.4+ are required.

## Custom effects
All eleven IDs exist and are usable in `/effect give`, though not all mechanics are complete.

| ID | What this development build currently does |
| --- | --- |
| `flashed` | Client renders a layered white screen overlay, intensifying from I to III. HUD layers are drawn over the whiteout. |
| `hallucinating` | Periodically plays randomly positioned fake vanilla sounds just for the affected player. |
| `quiet_hallucination` | Periodically plays subtle fake sounds, **but still behaves as a normal removable/visible status effect**. |
| `void_omen` | Repeated vanilla Darkness, sound hallucinations, occasional Warden heartbeat. |
| `stunned` | High levels of Slowness, Mining Fatigue and Weakness; client-side movement keys are blocked. Server has NOT yet secured all item and block actions. |
| `shield` | ID is registered; no extra blue-heart mechanics yet. |
| `immortality` | Cancels fatal player death via the Fabric death event, temporarily keeping one true HP. A tracked logically-zero player dies on effect expiration if not healed. The zero-health HUD and persistence are unfinished. |
| `inverted` | Client reverses forward/back, strafing and jump/sneak keyboard inputs, without inverting camera. |
| `vulnerable` | Incoming damage multiplier via Mixin: level I = 1.2x, level V = 2x, level X = 3x. |
| `nimble` | 10% chance to dodge eligible attacks/projectiles; player swoosh and particles. |
| `cursed` | On a genuine player death runs scoreboard command setting the dying player's existing `life` score to 0. The SMP's existing datapack must handle Death's Domain. |

### Example test commands
```mcfunction
/effect give @s shmingledorph:flashed 30 2
/effect give @s shmingledorph:inverted 30 0
/effect give @s shmingledorph:stunned 5 0
/effect give @s shmingledorph:vulnerable 30 9
/effect give @s shmingledorph:immortality 15 0
/effect give @s shmingledorph:nimble 30 0
```

## Still required for full SMP release
- Shield's separate light-blue hearts, shield-only Instant Health, damage interception and remaining-shield-to-Absorption conversion.
- Quiet Hallucination's secret, milk-resistant server-side state and admin controls.
- Full Stunned action enforcement including server interaction checks.
- Immortality's true zero-health display and safe persistence through reconnect/restart.
- Extra Void Omen custom darkness progression.
- Full client, multiplayer, and scoreboard integration testing; ensure Cursed interoperates with the existing lives system, totems, Immortality and respawns.
- Validate behavior with other custom mods and during long running server sessions.

## Development
`gradle build` (Java 21) produces the remapped mod JAR in `build/libs`. CI uses GitHub Actions to build and upload the JAR.

**Do not deploy to the live SMP until the unfinished mechanics and interoperability testing are complete.**

## Active development
Shield / Void Omen / true stun overhaul and extra effects are under development on this branch. Do not ship until CI and in-game tests pass.
