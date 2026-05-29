# GraveStones

When a player dies, their items don't scatter — they go into a **grave** marked by a floating
label, protected for their owner, lootable with a right-click. For Paper 1.21+.

## Download

**[Download the latest release »](https://github.com/Standardan/gravestones/releases/latest)**

Drop the `.jar` into your server's `plugins/` folder and restart. Requires Paper 1.21+ (Java 21).

## Features

- **Death drops are buried**, not scattered — captured into a grave at the death spot (plus XP)
- **Floating label** showing the owner and a live lock/decay countdown
- **Owner protection** — only the owner can loot for a configurable time, then it's open to all
- **Optional decay** — graves can drop their contents to the ground after a set time, or wait forever
- **Right-click to loot**; items go to your inventory (overflow drops at your feet)
- Survives restarts — graves are saved to `graves.yml`

## Commands & permissions

| Command | Description |
|---|---|
| `/graves` | List your active graves and their coordinates |
| `/graves reload` | Reload config (`gravestones.admin`) |

`gravestones.bypass` (op) lets staff loot any grave, ignoring owner protection.

## Configuration (`config.yml`)

```yaml
protection-seconds: 60   # owner-only loot window (0 = open immediately)
expire-seconds: 0        # decay time (0 = never)
```

## Design notes

- **Built on Display + Interaction entities** — the modern (1.19.4+) way to do holograms and
  clickable hitboxes, so there's **no dependency on a separate holograms plugin**. A `TextDisplay`
  renders the floating label; an `Interaction` entity provides the right-click hitbox.
- **Entities are persistent and tagged.** Each carries its grave id in its PersistentDataContainer,
  so a click is matched to its grave by tag — never by guessing from position.
- **Items persist via Bukkit serialization** to `graves.yml`, so graves survive restarts; a
  once-per-second task updates the countdown and decays expired graves (only in loaded chunks).

## Building

JDK 21 + Maven. `mvn clean package` → `target/gravestones-1.0.0.jar`.
