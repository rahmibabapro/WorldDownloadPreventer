# WorldDownloadPreventer

Folia **26.1.2** / **JDK 25** plugin that hardens worlds against [WorldTools](https://github.com/Avanatiker/WorldTools) and similar client-side world downloaders.

Part of the [AgeOfMC](https://github.com/) plugin stack — uses `age-lib` (`ServerRequirements`, `SchedulerCompat`, Folia region threading).

## Requirements

| | |
|---|---|
| Server | **Folia** only (Paper-only is rejected at enable) |
| Minecraft | **26.1.2.x** |
| Java | **JDK 25** |

## Build (AgeOfMC monorepo — required)

This module depends on **`age-lib`** (`ServerRequirements`, `SchedulerCompat`). Build from the Plugins root:

```bash
cd minecraft/Plugins
./gradlew.bat :WorldDownloadPreventer:jar
```

Output: `WorldDownloadPreventer/build/libs/WorldDownloadPreventer.jar`

> The GitHub checkout alone does not include `age-lib`; clone the full AgeOfMC `Plugins` tree or add `age-lib` as a composite build dependency.

## Install

1. Place `WorldDownloadPreventer.jar` in `plugins/`
2. Start Folia 26.1.2 server
3. `/wdp protect <world>` for each gameplay world

## Commands

| Command | Description |
|---------|-------------|
| `/wdp reload` | Reload config |
| `/wdp status` | Profile, worlds, Folia/NMS status |
| `/wdp protect <world>` | Enable protection + deploy datapack |
| `/wdp unprotect <world>` | Disable protection |
| `/wdp datapack` | Redeploy datapack to protected worlds |
| `/wdp scan` | Test-mutate chunk under first online player |

## Config

Default profile: **`lotc`** (all protection layers). See `config.yml` for `balanced`, `paranoid`, `custom`.

## Protection vs WorldTools

WorldTools saves **server packets** client-side (`F12` capture). This plugin mutates chunk metadata before send:

- Custom biomes (`wdp:trap_highlands`)
- Extended heightmaps / PostProcessing (SP parse failure)
- Optional block-entity JSON traps
- Void datapack dimension registry
- Heuristic downloader detection (chunk rate + ring exploration)

## Folia threading

- Chunk mutations: **RegionScheduler** per chunk (`SchedulerCompat.runTaskForChunk`)
- Global drain queue: **GlobalRegionScheduler** timer
- Player kick / datapack reload: **EntityScheduler** / **GlobalRegionScheduler**

## Permissions

- `wdp.admin` — commands (op)
- `wdp.bypass` — skip detection
- `wdp.notify` — staff alerts

## License

GPL-3.0-or-later (compatible with WorldTools GPL ecosystem).
