# Simple Enchantments — Personal Fork

> **This is a personal, modified version** of the Simple Enchantments mod for **Hytale**. It is **not** the official mod and is **not** maintained for public use. Bugs you find here should not be reported to the upstream project unless you can reproduce them on the original.

The official mod lives at **[Herolias/Simple-Enchantments](https://github.com/Herolias/Simple-Enchantments)**. If you want the supported, polished version, go there.

---

## Why this fork exists

I run this on a personal Hytale world to play around with the enchantment system. The fork diverged from upstream so I could add a couple of features I wanted for my own playthrough.

### Changes vs. upstream

- **New enchantment: Plentiful Harvest** (2 tiers, sickles only). Multiplies crop drops when harvesting grown crops with a sickle. Balanced to feel similar to Fortune II/III:
  - Level I → ~1.5× crops on average (≈ Fortune II)
  - Level II → ~2× crops on average (≈ Fortune III, slightly stronger to compensate for only having 2 tiers)
  - Hooks `InteractivelyPickupItemEvent` so it fires *only* during a sickle's harvest pickup — no false positives from chest pickups, `/give`, etc.
- **New item category: `SICKLE`** with auto-detection (any item whose ID contains `"sickle"`). The category-translation strings (`itemCategory.SICKLE`) are filled in for all 11 supported locales.
- **DynamicTooltipsLib compatibility fix** in `ItemCategoryManager.categorizeItem(String)`. When an enchantment is applied via DTT, the item gets renamed to `<base>__dtt_<hash>` (e.g. `Tool_Sickle_Iron__dtt_5214df7f`). The original code returned `UNKNOWN` for these variants on cache miss, silently breaking *any* category-gated enchant on enchanted items. The fix strips the suffix and inherits the base item's category.
- **Local-only `build.bat`** that rebuilds, copies the jar to my personal Hytale install, and wipes the dev-server config. It's `.gitignore`d so it doesn't end up upstream.

### Version numbering

Versioned `4.2.0` on purpose. The upstream is on `1.x.x` — bumping the major makes it obvious at a glance that this jar is *not* the official build.

---

## Building

```bash
git clone <this repo>
cd Simple-Enchantments
./gradlew build -Phytale_home="path/to/your/Hytale/install"
```

The compiled jar lands in `build/libs/SimpleEnchantments-4.2.0.jar`.

For convenience on Windows I keep a private `build.bat` (gitignored) that runs the build, drops the jar into my dev server's `mods/` folder, and clears the config so changes pick up cleanly. Adapt for your own paths if you want it.

### Prerequisites

- Hytale installed via the official launcher (build references `HytaleServer.jar` from your install)
- Java 25
- Bundled Gradle wrapper — no global install needed

---

## Plentiful Harvest details

| Tier | Trigger | Mechanic | Avg yield |
|------|---------|----------|-----------|
| I    | Right-click harvest a mature crop with an enchanted sickle | 50% chance to double *every* drop in that harvest | ~1.5× |
| II   | Same | Two independent 50% rolls — 25% / 50% / 25% chance of ×1 / ×2 / ×3 drops | ~2.0× |

**Where it hooks**: the sickle's `Sickle_Attack → BreakBlockInteraction(Harvest=true)` interaction routes through `BlockHarvestUtils.performPickupByInteraction()`, which calls `ItemUtils.interactivelyPickupItem()` once per drop. That call fires `InteractivelyPickupItemEvent` — a cancellable, mutable event meant for exactly this kind of mod intervention. The system listens for it, verifies the player is holding a sickle with the enchantment, and replaces the event's `ItemStack` with one of larger quantity. The engine then deposits the larger stack as if it were the original.

**Why not `BreakBlockEvent` / `UseBlockEvent`**: I tried both. Decompiling Hytale's server confirmed neither fires on the sickle's harvest path — `performPickupByInteraction` removes the block via low-level chunk operations and dispatches no events of its own. `InteractivelyPickupItemEvent` is the only point where mods can intervene.

**Config**: the per-level chance is exposed as `config.multiplier.plentiful_harvest` (default `0.50`) in the in-game `/enchantconfig` editor.

---

## What I didn't change

Everything else is upstream's work. Same 31 original enchantments, same scroll system, same enchanting table, same UI, same localisation, same public API for third-party addons. The technical overview, API docs, and full feature breakdown live in the [upstream README](https://github.com/Herolias/Simple-Enchantments#readme) — no point in duplicating it here.

---

## Credits

- **Original mod**: Herolias (developer) + Sorath (artist), plus the upstream contributors listed in their README.
- **This fork**: just my personal tinkering on top.
- The upstream license applies — see [LICENSE.md](LICENSE.md).
