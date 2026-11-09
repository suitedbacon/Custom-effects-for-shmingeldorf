# Shmingledorph Effects v0.2.1-dev — Minecraft Java 1.21.11 / Fabric

**Development test release, not approved for production SMP.** GitHub Actions compiles the mod and runs a dedicated server startup smoke test. Client visuals, multiplayer behavior, and cross-mod integrations still require in-game testing.

## Installation
Download the latest successful **Build Fabric mod** artifact from [GitHub Actions](https://github.com/suitedbacon/Custom-effects-for-shmingeldorf/actions). Unzip the artifact; install the non-sources `shmingledorph-effects-0.2.1-dev.jar` in the `mods` folders of both Minecraft client and Fabric server. Requires Fabric Loader 0.18.4+, Fabric API, Java 21 and Minecraft 1.21.11. Do not install the earlier 0.1.0 JAR alongside this one.

## Commands
```mcfunction
/effect give @s shmingledorph:shield 60 0
/effect give @s shmingledorph:shield 60 2
/effect give @s shmingledorph:void_omen 30 4
/effect give @s shmingledorph:stunned 5 0
/effect give @s shmingledorph:true_blindness 10 0
/effect give @s shmingledorph:flashed 10 2
/effect give @s shmingledorph:nimble 60 9
/effect give @s shmingledorph:vulnerable 60 9
/effect give @s shmingledorph:cursed 60 0
/effect give @s shmingledorph:immortality 60 0
/effect clear @s
```

## Effects and behavior
- **Shield**: +2 blue hearts per level. The blue hearts now use vanilla's actual heart-bar origin and row spacing. Red hearts stay on the bottom, blue hearts sit above red, and vanilla gold Absorption hearts are reflowed above blue without sharing a partial red/blue row. Incoming damage uses **gold Absorption first → blue Shield second → red health last**; affected blue hearts do not heal with ordinary natural regeneration. Instant Health heals shield. When the status effect ends, remaining blue HP converts to golden Absorption HP. The pool is synchronized from the server with entity tracked data. Persistence across restart/death and coordination with health-rendering mods need in-game testing.
- **Void Omen I–V**: progressively darker purple visual fog caps at 96 / 64 / 42 / 26 / 12 blocks. This is a *camera visibility fog cap*, **not an actual setting that decreases chunk/render-distance options**. Plays an ongoing Warden heartbeat every 28 ticks while active.
- **Stunned**: client movement/action input disabled; server blocks movement, digging, attack, interact, use, inventory click, hotbar change, drop, swap and other normal gameplay packets. Players can still look around; **pause and disconnect remain allowed**. Inventory, chat screens, and ordinary gameplay menus are blocked. Not guaranteed to stop every action from third-party mods or command access.
- **Nimble I–X**: 10% dodge chance at level I, +5 percentage points per level, 55% at level X for eligible combat hits.
- **Vulnerable I–X**: +40% incoming damage per level: I = 1.4x, V = 3.0x, X = 5.0x.
- **Cursed**: death-event capture fix so a player who actually dies with Cursed has their SMP `life` objective set to zero. This requires the existing lives datapack/scoreboard. Totem saves/Immortality deaths should not trigger the curse. Verify on a disposable world: it can permanently impact player lives.
- **True Blindness**: black world-view overlay while the HUD stays visible.
- **Flashed I–III**: original white flash remains; press **F8** in-game to toggle an alternative dark-amber palette. Preference persists in `config/shmingledorph-effects.json` via `flashColor` = `white` or `amber`. **The alternative is an accessibility preference, not a guarantee of seizure safety.**
- **Hallucinating**: randomized nearby sound illusions.
- **Quiet Hallucination**: subtle fake nearby sounds; not yet secret/milk-resistant.
- **Inverted**: client movement inputs inverted without camera inversion.
- **Immortality**: first-pass prevention of fatal player death with tracked zero state; full display/restart robustness is unfinished.

## Test warnings
**DO NOT deploy this version to the live SMP yet.** Test Shield damage order and blue-heart HUD under armor/Absorption, Instant Health, expiration, effect amplifier changes, and damage from multiple sources. Test Stunned chat/escape, Cursed with the `life` scoreboard and your Death's Domain datapack, deaths with totems, and other installed custom mods.

### For developers
Run `gradle build` on Java 21. CI builds the remapped JAR and runs an automated Fabric dedicated-server startup smoke test.

## Shield HUD hotfix (0.2.1-dev)
The old independent HUD overlay positioned blue hearts using a hard-coded `screenHeight - 39`, extra armor offset, and `10px` rows. That could drift upward or collide with gold Absorption when Shield strength increased. This hotfix renders inside vanilla `InGameHud.renderHealthBar` using its `x, y, lines` values. Vanilla gold hearts are moved into their own rows above blue Shield hearts; regular health stays where Minecraft originally draws it. Normal health plus Shield X and Absorption are now laid out as separate contiguous 10-heart rows. No shield damage/healing balance mechanics were changed.
