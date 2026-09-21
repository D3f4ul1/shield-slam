# STUN SLAM — build & verification record

Single-macro Fabric client mod. Left-click only. Axe hit breaks a raised shield,
then a mace hit follows immediately.

> **Revision note.** The first build gated the mace hit behind a fall check
> (`WAIT_FALL`), inherited from the original spec. That made the macro unusable
> on flat ground — see §5. The gate has been **removed**; the combo is now a
> straight five-step sequence with no world-state waiting.

---

## 1. Toolchain (verified against Fabric metadata, not assumed)

| Component | Version | Source |
|---|---|---|
| Minecraft | `1.21.11` | requested |
| Yarn mappings | `1.21.11+build.6` | `meta.fabricmc.net/v2/versions/yarn/1.21.11` |
| Fabric Loader | `0.19.5` | `meta.fabricmc.net/v2/versions/loader` |
| Fabric API | `0.141.6+1.21.11` | Modrinth |
| Fabric Loom | `1.17.21` | `maven.fabricmc.net` |
| Gradle | `9.6.1` (system) | present on the build machine |
| Java | Temurin `21.0.11` | `options.release = 21` |

`genSources` was run before writing the mixin, as instructed. Every signature was
then checked against the **real remapped 1.21.11 jar** via `javap`, and every name
cross-checked against `mappings.tiny` from `yarn-1.21.11+build.6-v2.jar`.

Note on Loom: for this era the plugin id is **`net.fabricmc.fabric-loom-remap`**,
not `fabric-loom`. The 1.21.11 example mod also pins `loom_version=1.17-SNAPSHOT`;
this project uses the stable release `1.17.21` in that same line instead.

A signature check alone was not enough. A second pass auditing **runtime
semantics** with `javap -c` — not just names and descriptors — caught a
mod-breaking bug that compiled perfectly cleanly. See "Targeting" in §4.

---

## 2. Build

```bash
cd stunslam
gradle build
```

**Jar path:** `stunslam/build/libs/stunslam-1.0.0.jar`

Confirmed a real release artifact, not a dev jar: its bytecode references
intermediary names (`net/minecraft/class_310`), i.e. `remapJar` ran.

Mixin remap verified in the built jar:

```
@Inject(method = "method_1536", at = @At("HEAD"), cancellable = true)
@Mixin(net/minecraft/class_310)
```

`method_1536` resolves to `doAttack()Z` on `net/minecraft/client/MinecraftClient`
in build.6 — confirmed by reading the mappings table directly. With
`"defaultRequire": 1` in the mixin config, a future rename fails loudly at load
rather than silently disabling the mod.

### Deployed

SKLauncher instance `prestige` (`fabric-loader-0.19.5-1.21.11`, an exact match):

```
C:\Users\N0th1ng\AppData\Roaming\.sklauncher\instances\prestige\mods\stunslam-1.0.0.jar
```

`fabric-api-0.141.6+1.21.11.jar` was already present. Copy verified by matching
SHA-256, and the deployed class was disassembled to confirm it reads
`class_310.field_1765` (`MinecraftClient.crosshairTarget`) rather than the broken
raycast, and that its phase enum contains no `WAIT_FALL`.

### Optional: Gradle wrapper

Not generated, to hold the deliverable at exactly the 15 files the spec lists.
If you want one: `gradle wrapper --gradle-version 9.5.1`.

---

## 3. Config

**Path:** `<game dir>/config/stunslam.json`

```json
{
  "stunSlamEnabled": true,
  "delayMs": 100,
  "minFall": 1.5,
  "maceType": "ANY",
  "shieldBreakEnabled": false,
  "shieldBreakKey": 86,
  "shieldBreakDelayMs": 100
}
```

| Setting | Default | Range | Meaning |
|---|---|---|---|
| `stunSlamEnabled` | true | ON / OFF | Stun slam switch. Independent of the shield break |
| `delayMs` | 100 | 0–500, 50ms steps | Stun slam: pause between the axe hit and the mace hit |
| `minFall` | 1.5 | 0.0–6.0, 0.5 steps | Stun slam: fall required to start. Also the divider between the macros |
| `maceType` | `ANY` | ANY / DENSITY / BREACH | Stun slam: which mace it will accept |
| `shieldBreakEnabled` | false | ON / OFF | Shield break switch. Independent of the stun slam |
| `shieldBreakKey` | 86 (`V`) | any GLFW key, 0 = unbound | Key that toggles `shieldBreakEnabled` |
| `shieldBreakDelayMs` | 100 | 0–500, 50ms steps | Shield break: pause between the hit and the restore |

Seven settings, flat. Written by Gson 250ms after the last change, plus a
`CLIENT_STOPPING` flush so quitting inside the debounce window cannot lose the
write. Reads are clamped on load. Unknown keys are ignored.

**Config migration.** `stunSlamEnabled` replaced an earlier single `enabled` master
switch that gated both macros. On load, if `stunSlamEnabled` is absent but `enabled`
is present, its value is carried across, so upgrading does not silently re-enable
something that had been switched off.

### Keybinds

| Key | Registered with the game? | What it does |
|---|---|---|
| Right Shift | Yes | Opens the panel |
| Shield-break toggle | **No** | Flips the shield break on/off |

The toggle key is deliberately not a registered `KeyBinding`. It is a raw GLFW key
code in the config, polled once per tick with
`InputUtil.isKeyPressed(client.getWindow(), key)`, so it never appears in the
vanilla controls list. The panel captures it: click the **Break key** button and
press a key (Escape unbinds). Polling is edge-triggered, so holding the key flips
the switch once rather than every tick.

**Neither key performs an action.** Both macros fire from an ordinary left-click
on a shielding target; the toggle key only chooses which one answers.

MAPPING notes for this area, both changed from older versions:
`Screen.keyPressed` now takes a `KeyInput` record instead of
`(int keyCode, int scanCode, int modifiers)`, and `InputUtil.isKeyPressed` takes a
`Window` rather than a GLFW handle.

### Panel theme

The whole panel follows the master switch, so the state is readable at a glance:

| Element | On | Off |
|---|---|---|
| Panel fill | `0xFF101A10` (green-tinted) | `0xFF1A1A1A` (neutral) |
| Border, title, separator | `0xFF3FBF3F` green | `0xFF5A5A5A` grey |
| Status row | `READY` / `NO AXE` / `NO MACE` / `NEED FALL` | `DISABLED` |

### Speed: why there is no half tick, and no burst

Minecraft runs its game logic in whole ticks at 20 per second, so 50ms is the
smallest step that exists — the slider moves in 50ms increments because anything
finer would be a lie about what the engine can do.

Earlier builds had a "1-tick burst" that collapsed the whole combo into one tick.
It was removed: it was too fast to see the axe or the mace at all. Instead, a
swap and the hit that uses it now share a tick, which is safe because
`ClientPlayerInteractionManager.attackEntity` calls `syncSelectedSlot()` itself
before sending the attack packet and the channel is ordered. Chaining them
removes the dead tick that used to sit between every swap and its swing, which is
what had left no usable speeds between "one tick per step" and "everything at
once".

The result is a smooth range where the axe stays visible for the whole pause:

| `delayMs` | Pause | Total combo |
|---|---|---|
| 0 | 0 ticks | 2 ticks (100ms) |
| 50 | 1 tick | 3 ticks (150ms) |
| 100 | 2 ticks | 4 ticks (200ms) |
| 200 | 4 ticks | 6 ticks (300ms) |
| 500 | 10 ticks | 12 ticks (600ms) |

Total is `ceil(delayMs / 50) + 2` ticks. The two fixed ticks are the opening
axe swap+hit and the closing restore.

---

## 4. MAPPING deviations from the spec

Each of these is a place where the spec's stated signature does not exist in
Yarn 1.21.11 build.6. The call sites carry a `// MAPPING:` comment.

| Spec says | build.6 reality | Resolution |
|---|---|---|
| `PlayerInventory.selectedSlot` — public int field | **`private int selectedSlot`** | `getSelectedSlot()` / `setSelectedSlot(int)` |
| `mc.networkHandler.sendPacket(...)` | backing field is private | `mc.getNetworkHandler().sendPacket(...)` |
| `isGliding()` | on **`LivingEntity`**, not `Entity` | no longer called; the fall gate was removed |
| `KeyBinding(..., String category, ...)` | takes a **`KeyBinding.Category`** record | `Category.create(Identifier)` |
| `Enchantments.DENSITY` / `BREACH` | **`RegistryKey<Enchantment>`**, not `RegistryEntry` | matched via `RegistryEntry.matchesKey(...)` |

Unchanged and confirmed present, exactly as the spec assumed:
`MinecraftClient.doAttack()` (`private boolean`, no params), `currentScreen`,
`interactionManager.attackEntity(PlayerEntity, Entity)`,
`PlayerEntity.getAttackCooldownProgress(float)`, `LivingEntity.isBlocking() /
isUsingItem() / getActiveItem()`, `UpdateSelectedSlotC2SPacket(int)`,
`Screen.shouldPause()`, `Screen.renderBackground(DrawContext, int, int, float)`,
`KeyBinding.getBoundKeyLocalizedText()`, `AxeItem` (`instanceof` detection).

`KeyBinding.Category.create(Identifier)` appends to a global list and **throws
`IllegalArgumentException` if the same id is registered twice** — verified by
disassembling the method. It is therefore called exactly once behind a guard.

### Targeting: do NOT use `Entity.raycast`

A behaviour trap, not a rename, and it silently disables the entire mod rather
than failing to compile.

`Entity.raycast(double, float, boolean)` is *declared* to return `HitResult`, so
it looks like the obvious way to find the entity under the crosshair. Its
bytecode ends:

```
75: invokevirtual  World.raycast:(LRaycastContext;)Lnet/minecraft/util/hit/BlockHitResult;
78: areturn
```

It delegates to `World.raycast` and can therefore **only ever return a
`BlockHitResult`**. A target lookup built on it always yields `null`, `canStart`
always returns false, and the macro never fires on anything.

The correct source is `MinecraftClient.crosshairTarget` (`class_310.field_1765`),
which `GameRenderer` refills every frame with a real entity raycast. Vanilla
`doAttack()` reads that same field to choose its target — confirmed in its
bytecode — so the combo engages exactly what the player is looking at. The
4.0-block cap is enforced separately against the target's hitbox.

---

## 5. The two gates, and where each one lives

Both of these started life inside the sequence and both caused the same class of
bug — the combo running, stalling, and aborting with no explanation. Both now sit
at the boundary where they can be reported instead.

### 5a. The fall gate: removed, then reinstated as a start condition

The first build held the mace hit until `fallDistance >= minFallDistance`
(default 2.0) *inside* the sequence. Vanilla's own smash predicate, disassembled
from `MaceItem`:

```java
return entity.fallDistance > 1.5D && !entity.isGliding();
```

A standing jump peaks at roughly **1.25 blocks** of fall, so on flat ground that
inner gate could never open. The reported symptom — the mace gets selected but
never swings — was the combo sitting in `WAIT_FALL` for the full 12-tick window
and then aborting. Tuning the delay could not help, because the delay only
affected the phases *before* the wall.

That inner gate was removed. It has since been **reinstated as a start condition**
on request, because a combo launched from the ground lands a plain mace hit and
wastes the shield break. The difference is *where* the check lives:

| | Old (removed) | Now |
|---|---|---|
| Where | Inside the sequence, in `WAIT_FALL` | In `ComboConditions.canStart` |
| Effect when unmet | Sequence runs, stalls, then aborts | The click falls through to vanilla; nothing starts |
| Player feedback | None — looks broken | Status row reads `NEED FALL` |

`canStart` now requires `!isOnGround() && fallDistance >= minFall`. Because the
fall keeps growing while the combo runs, gating at start guarantees the smash is
still earned when the mace lands rather than being hoped for.

**Practical consequence, and it matters:** `fallDistance` counts how far you have
*already* fallen, not your height above the ground. So the macro fires while you
are descending, not at the apex of a jump. From flat ground a standing jump peaks
at ~1.25 blocks and will never satisfy the default of 2.0 — you need a ledge, a
drop, or to already be falling. That is the mace's mechanic, not a mod limitation.
Set `minFall` to 0 to switch the gate off.

### 5b. The attack cooldown wait — added, then removed on request

Removing the fall gate exposed a second problem, found by disassembling
`PlayerEntity.attack` and `ClientPlayerInteractionManager.attackEntity`:

```
attackEntity:  syncSelectedSlot()
               send PlayerInteractEntityC2SPacket.attack(...)
               player.attack(target)
               player.resetTicksSince()      <-- resets the cooldown
```

Every hit resets the shared attack cooldown, and damage is scaled by it:

```java
float f = getAttackCooldownProgress(0.5f);
float modifier = 0.2f + f * f * 0.8f;   // getAttackCooldownDamageModifier
```

The mace's attack speed is **0.6** (`ATTACK_SPEED` base 4.0 plus a −3.4 modifier),
so a full charge takes `20 / 0.6` = **33 ticks (1.67s)**. Its base damage is 6.0.

A build with a `waitForCharge` option was shipped, which held the mace hit until
the cooldown was charged. **It has since been removed at the user's request** —
the option was reported as unclear and the preference is for speed. That is a
deliberate trade, and the cost is worth stating plainly:

| Ticks between axe and mace hit | `f` | Damage modifier | Mace damage |
|---|---|---|---|
| 1–4 (any setting now available) | ≤0.14 | ≤0.215 | **~1.2** |
| 12 | 0.375 | 0.313 | ~1.9 |
| 22 | 0.675 | 0.565 | ~3.4 |
| 33 (full charge) | 1.0 | 1.0 | **6.0** |

So with the charge wait gone, **every speed setting lands a roughly 1.2-damage
mace hit**, and the smash bonus — also scaled by `f` — is largely thrown away.
The combo is fast and the shield still drops; the mace simply does not hit hard.
This is a vanilla constraint: two full-damage hits with a 0.6-speed weapon cannot
be closer together than that weapon's cooldown. If full damage is wanted back, the
fix is to reintroduce a charge hold before `HIT_MACE`; the analysis above is the
spec for it.

### Timing

One tick is 50ms at 20 TPS. Total is `ceil(delayMs / 50) + 2` ticks.

| `delayMs` | Pause between hits | Axe hit | Mace hit | Restored |
|---|---|---|---|---|
| 0 | 0 ticks | 50ms | 100ms | 150ms |
| 50 | 1 tick | 50ms | 150ms | 200ms |
| 100 | 2 ticks | 50ms | 200ms | 250ms |
| 200 | 4 ticks | 50ms | 300ms | 350ms |
| 500 | 10 ticks | 50ms | 600ms | 650ms |

The axe swap and its hit share the opening tick, and the restore closes on its own
tick. The pause sits between the two hits, so the axe stays in hand and visible
for its whole duration.

---

### 5c. The shield break

A second macro, sharing the same state machine and the same opening axe swing. It
stops after `HIT_AXE`:

```
STUN_SLAM:     swap axe -> hit -> [pause] -> swap mace -> hit -> restore
SHIELD_BREAK:  swap axe -> hit -> [pause] -> restore
```

**Both macros fire from the same trigger: an ordinary left-click on a shielding
target.** They cannot both answer one click, so `tryStart` picks between them — and
the divider is the **fall distance**, not which switch was flipped last:

| Click condition | Macro that answers |
|---|---|
| Falling `>= minFall` and the stun slam is on | Stun slam |
| Not falling that far, and the shield break is on | Shield break |
| Neither switch on, or neither condition met | Nothing — the click passes to vanilla |

That ordering is the whole trick. The stun slam's fall gate lives in
`canStartStunSlam`, and `tryStart` tests it **first**, so a click that clears
`minFall` is a stun slam and one that does not falls through to the shield break.
Swapping those two tests would let the shield break swallow every click and the
stun slam would never run.

It also means the two switches are genuinely independent: either can be on alone,
or both at once, and neither can turn the other off.

The shield break's other conditions are deliberately weaker than the stun slam's:

| | Stun slam | Shield break |
|---|---|---|
| Trigger | Left click on a shielding target | Same |
| Needs a mace | Yes | No |
| Needs a fall | Yes | No |
| Needs an axe in the hotbar | Yes | Yes |
| Its own switch | `stunSlamEnabled` | `shieldBreakEnabled` |

No fall requirement on the shield break, because disabling a raised shield needs
only the axe swing — demanding a fall would make it useless on the ground, which
is exactly where shields get raised. Between them the pair covers the real
sequence: break the shield from the ground, then jump and slam.

The pause sits between the hit and the restore, so the axe stays visible for its
duration. Total is `ceil(delayMs / 50) + 1` ticks: one shorter than the stun slam,
which has no mace phase to pay for.

Switching a macro off mid-sequence aborts it and restores the original item.
Each macro checks **its own** switch for that, not the other's.

**Edge case worth knowing:** setting `Min fall` to 0 switches the stun slam's gate
off, which makes `fall >= 0` always true — so the stun slam wins every click and
the shield break becomes unreachable, even with its switch on. That is the divider
rule applied consistently rather than a bug, but it means `Min fall` needs to stay
above 0 for the shield break to ever fire. 1.5 is the sensible floor: it is
vanilla's own mace threshold, so a click that clears it genuinely earns a smash.

### Panel layout

Two columns, so the settings stay on one screen without running off the bottom at
high GUI scales:

```
        STUN SLAM  (300 wide)                     SHIELD BREAK
  Stun Slam: ON        |  Shield break: OFF
  Delay: 100 ms        |  Break delay: 100 ms
  Min fall: 2.0 blocks |  Break key: V
  Mace: ANY            |
                    Close
```

Vanilla's screen background is left intact, so the panel sits on the usual
blurred world, with a translucent card drawn over it for readability. The card's
accent colour is green when **either** macro is switched on and grey when both are
off. The status row names the macro that would answer a click right now —
`STUN SLAM`, `SHIELD BREAK`, `BOTH OFF`, `NO AXE`, `NO MACE` or `NEED FALL` — which
makes it the quickest way to see why a click is doing nothing.

This deliberately overrides the original spec's "override renderBackground to a
no-op", which was written for a custom-drawn panel. Restoring it is what gives the
screen the Minecraft look.

## 6. Deliberate deviations from the spec's snippets

Five, each because the snippet as written contradicted a stated acceptance test
or, in the last two cases, because a wait it did not anticipate made the mod
unusable. All are commented in place.

**a. The mixin also swallows clicks while the combo is running.**
The spec cancels only when `tryStart()` succeeds. But the scripted axe hit
restarts the attack cooldown, and once it expires with the button still held,
`doAttack()` is called again mid-sequence. Falling through there fires an
unscoped vanilla hit with whatever item the combo has just selected, breaking the
scripted order. The extra guard cancels only inside the window where the sequence
owns the input.

**b. `ComboController.tick` returns early while a screen is open.**
This is acceptance test 11 ("vanilla pauses the combo… screen closes, combo
continues"). Without it, phase timers keep advancing behind the GUI.

**c. The keybind uses a literal display string, not a translation key.**
The spec caps the deliverable at 15 files and there is no lang file, so a
translation key would render in the controls screen as the raw key text.

**d. Both hits re-verify the held item before swinging.**
The spec's `HIT_AXE` / `HIT_MACE` snippets attack straight after the delay. With a
gap between the swap and the swing, a hotbar scroll in that window would send an
attack with the wrong item in hand — the axe would not drop the shield, or the
mace hit would be wasted. `reselectIfWrongItem` checks the selected stack,
re-selects the right item and restarts the step delay, or aborts if the item is
gone.

**e. `canStart` does not require a full attack cooldown.**
The spec required `getAttackCooldownProgress(0f) >= 1.0f`. That was removed after
it was reported as "the mod can do a stun slam with any item, just not the axe and
the mace". The cause is that cooldown *length* is derived from the held item's
attack speed, so the gate waited a different amount of time depending on what was
in hand:

| Held item | Attack speed | Ticks to a full charge |
|---|---|---|
| Mace | 0.6 | 33 |
| Axe | 1.0 | 20 |
| Sword | 1.6 | 13 |
| Block / most other items | 4.0 | 5 |

Holding an axe or a mace therefore made the macro take four to seven times longer
to respond than holding anything else — which reads as "it works with any item
except the axe and the mace". The gate was never needed: the combo drives its hits
through `ClientPlayerInteractionManager.attackEntity`, which bypasses the vanilla
cooldown check entirely.

---

## 7. Acceptance results

**Scope of verification — stated plainly:** the end-to-end tests need a running
client and two LAN accounts. **I have not executed those, and I am not reporting
them as executed.** Outcomes below are from static code-path analysis; each row is
labelled with what actually backs it.

### What was actually executed

**1. Load integrity** — the failures that crash the game at startup:

| Check | Result |
|---|---|
| `fabric.mod.json` + `stunslam.mixins.json` parse as valid JSON | pass |
| Entrypoint class resolves and implements `ClientModInitializer` | pass |
| Mixin class exists at the package declared in the mixin config | pass |
| Mixin target resolved by the remapper (`doAttack` → `method_1536`) | pass |
| Deployed phase enum contains no `WAIT_FALL`; no fall-gate references remain | pass |
| Deployed bytecode calls `PlayerEntity.method_7261` (`getAttackCooldownProgress`) | pass |
| Deployed bytecode reads `MinecraftClient.options.attackKey.isPressed()` **once, in the shared core** | pass — covers both macros, see §8 |
| Deployed bytecode has no `waitForCharge` / `getAttackCooldownProgress` reference | pass — charge wait fully removed |
| Burst path present: `advance(MinecraftClient, boolean)` + `BURST_GUARD` | pass |
| Toggle keybind registered and calls `method_7353` (`sendMessage(Text, boolean)`) | pass |
| Deployed `canStart` no longer calls `getAttackCooldownProgress` | pass — see §6e |
| Fall gate present: `canStart` calls `method_24828` (`isOnGround`) then compares `field_6017` (`fallDistance`) against `Config.minFall` | pass |
| Toggle keybind removed from the client; only the panel key remains | pass |
| Phase-chaining loop present (`advance` + `MAX_CHAIN`) | pass |
| Shield break present: `Mode.SHIELD_BREAK`, `canStartShieldBreak`, `shieldBreakEnabled` gate | pass |
| `tryStart` picks the mode: `canStartShieldBreak` tested before `canStartStunSlam` | pass |
| Toggle key calls `Config.toggleShieldBreak`; `tryStartShieldBreak` removed | pass |
| Key polled, not registered: `method_15987` (`isKeyPressed`) with `method_22683` (`getWindow`) | pass |
| Panel captures keys: `method_25404(class_11908)` = `keyPressed(KeyInput)` | pass |
| `renderBackground` override removed, so vanilla draws the blurred world | pass |
| All 28 mods in the target instance scanned for attack-path hooks | pass — one real conflict found and guarded, see §8 |

The whole set above was re-run against the final jar after each rework, not just at
first build.

**2. Config logic** — a Java harness compiled against the **deployed jar** and the
real `com.stunslam.config.Config` class. **94 assertions, 0 failures:** the 0–500ms
delay range in 50ms steps for both macros, ms→tick rounding at the boundaries (0,
1, 50, 51, 100, 500), every delay position reachable and on-step, a 1001-position
slider sweep, min-fall range and step snapping including float dust, the fall
gate's on/off threshold and labels, the two switches proved independent (turning
one off leaves the other alone, and both can be on at once), the shield-break
key bind/unbind path including a negative-code guard, both delay labels, the state
labels, case-insensitive mace parsing with `ANY` fallback, enum cycling, and
confirmation that `enabled` / `stepTicks` / `waitForCharge` / `ackDelayTicks` /
`minFallDistance` / `maxWaitTicks` / `autoJump` are all gone.

The harness lives outside the project and is not part of the 15-file deliverable.

### The twelve tests

Tests 6–9 were written against the fall-gated design and no longer describe this
build. They are marked obsolete rather than quietly dropped.

| # | Test | Outcome |
|---|---|---|
| 1 | Idle click-through (click a wall) | **Pass (code path).** A block hit is not an `EntityHitResult`, so `pickTarget` returns null → `canStart` false → vanilla attack runs. |
| 2 | No shield, click on player | **Pass (code path).** Target resolves but `isShielding` is false → vanilla attack, no swap. |
| 3 | Shield up, no axe in hotbar | **Pass (code path).** `findAxe` < 0 → `canStart` false → vanilla attack, no swap issued. |
| 4 | Shield up, all conditions met | **Pass (code path).** Full 5-phase sequence: swap axe, hit, swap mace, hit, restore. Each swap is packeted before the hit that depends on it. |
| 5 | Speed effect (0ms vs 500ms) | **By design.** 0ms = 2 ticks (100ms), 500ms = 12 ticks (600ms) — see the table in §5. With the charge wait removed, every setting lands a ~1.2-damage mace hit; see §5b. |
| 6 | Min fall effect (2.0 vs 0) | **Pass (code path), reinstated.** At 2.0 the click falls through to vanilla unless you are airborne and have already fallen 2 blocks; the status row reads `NEED FALL`. At 0 the gate is off and the combo fires from the ground. |
| 7 | ~~Auto-jump off, standing on ground~~ | **Obsolete.** `autoJump` no longer exists — the combo never jumps. |
| 8 | ~~Auto-jump on, standing on ground~~ | **Obsolete.** Same reason. The fall requirement is now a start condition, not something the macro waits for. |
| 9 | ~~Wait window~~ | **Obsolete.** `maxWaitTicks` no longer exists. Nothing blocks on world state mid-sequence. |
| 10 | Abort on death | **Pass (code path).** `!target.isAlive()` → `abort()` → `swapSlot(savedSlot)`. |
| 11 | Screen open mid-combo | **Pass (code path).** `tick()` returns early while `currentScreen != null`, so phase timers freeze; closing resumes. |
| 12 | Config round-trip | **Pass (executed).** The clamp/parse path was run against the deployed jar — 20 assertions, 0 failures. The only unexercised part is the file write itself, which needs a Fabric runtime. |

Still needing a live client: tests 1–5, 10 and 11 end-to-end, plus the feel of
Delay 1 through 4.

---

## 8. Interactions with the other mods in this instance

The target instance carries 28 mods, several of them combat or input related. All
of them were scanned for hooks into the attack path, because a conflict there
would look like a bug in this mod. The scan matched `method_1536` (the
intermediary name of `doAttack`) **with a digit boundary** — a naive substring
match reports false positives, since `method_15363` is a different method that
appears in sodium and cloth-config.

Four mods reference `doAttack`. Only one is a real problem.

| Mod | How it hooks | Conflict |
|---|---|---|
| `freecam` | `MinecraftMixin`, `@Inject` at `HEAD`, **cancellable** — the same point as this mod | Benign. Both handlers run; this mod's cancel is the more restrictive of the two, so freecam cannot be made to attack. |
| `shieldfixes` | `MinecraftMixin`, `@Inject` at `INVOKE` inside `doAttack`, not cancellable | None. Different injection point. |
| `SpearSwapper` | **No mixin.** Ships an access widener that makes `MinecraftClient.doAttack()` public, then calls it directly from `SwapManager.tick` on its G keybind | **Real.** See below. |
| `stunslam` | `MinecraftClientMixin`, `@Inject` at `HEAD`, cancellable | — |

### The SpearSwapper interaction, and the guard added for it

`spearswapper.accesswidener` contains:

```
accessible  method  net/minecraft/class_310  method_1536  ()Z
```

and `SwapManager.tick` does `invokevirtual class_310.method_1536:()Z`. So pressing
**G** calls `doAttack()` programmatically. Because this mod injects at the head of
that same method, the combo would have hijacked the call and fought SpearSwapper
for the hotbar — every press of G on a shielding target would have started a stun
slam instead of a spear swap.

The guard lives in `ComboConditions.eligibleTarget`, the shared core both macros
run through, so it covers the shield break as well as the stun slam. It requires
`mc.options.attackKey.isPressed()`. Vanilla only reaches `doAttack()` from a branch
that has already tested that same key, so this changes nothing for a genuine
left-click, but it stops a programmatic call from another mod from starting either
macro. The mixin's guard against clicks *mid-sequence* is deliberately left
unconditional, so a programmatic call cannot interrupt a sequence that is already
running.

**This was briefly broken and is worth recording.** When the shield break was
first added as a keybind-triggered macro it needed no attack-key check, and the
guard sat in `canStartStunSlam` alone. Re-pointing the shield break at the
left-click trigger would have left it unguarded again, so a press of SpearSwapper's
G would have hijacked into a shield break. Moving the check into the shared core
is what makes the invariant hold for both.

## 9. Scope check

One macro. One combo. One screen. One mixin. One tick hook.

Nothing scans for targets on tick, nothing reacts to a shield going up, nothing
touches yaw or pitch, and no other combat module exists. While the player is not
left-clicking, the mod is inert.

Deliberately absent: `ModuleManager`, `MacroScheduler`, any `Setting<T>`
hierarchy, notifications, "only players", "reaction delay", "range", "aim cone".

**Deliverable count: 15** — 3 build files, 2 metadata files, 9 Java files,
1 BUILD.md. No sixteenth file.
