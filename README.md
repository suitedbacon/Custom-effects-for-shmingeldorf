# Shmingledorph Effects (Fabric 1.21.11)

**Status: development build. NOT ready for SMP deployment.**

This repository is for the Shmingledorph SMP custom effects mod.

## Effects registered
`shmingledorph:flashed`, `hallucinating`, `quiet_hallucination`, `void_omen`, `stunned`, `shield`, `immortality`, `inverted`, `vulnerable`, `nimble`, `cursed`.

Example: `/effect give @s shmingledorph:cursed 60 0`

## Mechanics implemented so far (unverified in-game)
- Cursed: after an actual player death, sets the dying player's existing `life` scoreboard to 0. The SMP's existing Death's Domain system must perform the transfer.
- Hallucinating: occasional randomized vanilla sound, heard by the affected player, from offset nearby positions.
- Quiet Hallucination: occasional realistic sounds, **but not yet hidden or milk-resistant**.
- Void Omen: applies repeated vanilla Darkness and participates in random sounds, including occasional heartbeat.

- Nimble: 10% chance to cancel eligible combat damage; particle and swoosh feedback for players (unverified).\n- Stunned: first-pass Slowness 255, Mining Fatigue 255, Weakness 255 refreshed each second. **Not a full stun**; client inputs and server actions remain unrestricted.\n\n## Not yet implemented
- Flashed custom screen overlay.
- Hidden persistent Quiet Hallucination state, immunity to milk, and specialized removal command.
- Complete Stunned input and server-side action restrictions (the first-pass potion penalties are insufficient).
- Shield separate blue heart pool, damage absorption, healing, and conversion.
- Immortality lethal damage interception and zero-HP behavior.
- Inverted controls.
- Vulnerable damage multiplier.
- Nimble dodge chance.
- Void Omen's full custom visuals.
- Thorough integration tests for Cursed with the SMP lives datapack.

## Build
GitHub Actions: [Actions](https://github.com/suitedbacon/Custom-effects-for-shmingeldorf/actions).
The workflow attempts `gradle build` with Java 21 and uploads `build/libs/*.jar` on success. **A successful compilation does not mean the unimplemented mechanics work.**

## Safety
Test on a disposable world with a copy of your datapack and scoreboard before deploying. Especially test `life` zero and death/respawn transitions.

## Build validation
Automated build diagnostics are performed on pull request branches before promoting a tested release.
