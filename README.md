# Shield Slam

A client-side PvP macro for Minecraft **1.21.11** (Fabric). When your opponent
raises a shield, Shield Slam answers for you — swapping to your axe to knock the
shield down, and chaining into a mace smash when you have the height for it.

Left-click is the only trigger. Nothing fires on its own.

---

## The two macros

| | **Stun Slam** | **Shield Break** |
|---|---|---|
| What it does | Axe hit to drop the shield, then a mace hit | Axe hit only |
| Needs a mace | Yes | No |
| Needs a fall | Yes (≥ Min fall) | No |
| Best for | Finishing a shielding opponent from the air | Breaking a shield from the ground |

Both are switched on independently, and **the fall distance decides which one
answers a click**:

```
left-click on a shielding player
  ├─ falling >= Min fall  and Stun Slam is on   -> Stun Slam
  ├─ otherwise            and Shield Break is on -> Shield Break
  └─ neither                                     -> vanilla handles it
```

That means the pair covers the real sequence: **break the shield on the ground,
then jump and slam.**

### Why the mace needs height

The mace only awards its smash bonus above 1.5 blocks of fall — that is vanilla's
rule, not a mod limitation. `Min fall` defaults to **1.5** for that reason. It also
doubles as the divider between the two macros, so leave it above 0.

---

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for **1.21.11**
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into `mods/`
3. Drop `shieldslam-1.0.0.jar` into `mods/`
4. Launch

Client-side only. Works in singleplayer and on LAN with no setup.

---

## Controls

| Key | Action |
|---|---|
| **Left click** | Runs the macro — the only trigger |
| **Right Shift** | Opens the settings panel |
| **V** | Toggles the shield break on/off (rebindable in the panel) |

Only Right Shift appears in `Options → Controls`. The shield-break key is set
inside the mod panel instead — click **Break key**, then press a key. `Esc` unbinds.

---

## Settings

`config/stunslam.json`, all editable from the panel:

| Setting | Default | What it does |
|---|---|---|
| `stunSlamEnabled` | true | Stun slam on/off |
| `delayMs` | 100 | Pause between the axe hit and the mace hit |
| `minFall` | 1.5 | Fall needed to start the stun slam; also the macro divider |
| `maceType` | `ANY` | Accept only a DENSITY or BREACH mace |
| `shieldBreakEnabled` | false | Shield break on/off |
| `shieldBreakKey` | V | Key that toggles the shield break |
| `shieldBreakDelayMs` | 100 | Pause between the shield-break hit and the swap back |

### Timing

Minecraft runs its logic in whole ticks at 20/second, so 50 ms is the smallest
step that exists. The delay sliders move in 50 ms increments for that reason —
anything finer would be a lie about what the engine can do.

| `delayMs` | Combo length |
|---|---|
| 0 | 2 ticks (100 ms) |
| 100 | 4 ticks (200 ms) |
| 500 | 12 ticks (600 ms) |

**Set `delayMs` to at least 50** if you want to see the axe swap. At 0 both hits
land in the same tick and the axe is never visible.

---

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.19.5+
- Fabric API
- Java 21+

## Building

```bash
./gradlew build
```

Output lands in `build/libs/`.

## License

MIT — see [LICENSE](LICENSE).
