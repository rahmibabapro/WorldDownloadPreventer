package com.ageofmc.wdp.config

import org.bukkit.configuration.file.FileConfiguration

enum class DetectionAction { NOTIFY, KICK, TEMPBAN }

enum class ExplorationPattern { RING, GRID, ANY }

enum class ProtectionProfileKind { LOTC, BALANCED, PARANOID, CUSTOM }

data class ProtectionSettings(
    val customDimension: Boolean,
    val customBiomes: Boolean,
    val extendedHeightmaps: Boolean,
    val extendedPostProcessing: Boolean,
    val blockEntityJsonTrap: Boolean,
    val voidDecoyGenerator: Boolean,
    val stripStructureRefs: Boolean,
)

data class DetectionSettings(
    val enabled: Boolean,
    val chunksPerWindow: Int,
    val windowSeconds: Int,
    val strikesBeforeAction: Int,
    val pattern: ExplorationPattern,
    val suspiciousChannels: List<String>,
    val action: DetectionAction,
    val kickMessage: String,
)

data class PerformanceSettings(
    val mutateOnlyNearPlayers: Boolean,
    val playerChunkRadius: Int,
    val mutationsPerTick: Int,
    val cacheMutations: Boolean,
    val mutationCacheVersion: Int,
)

data class DatapackSettings(
    val namespace: String,
    val autoReload: Boolean,
    val protectedDimension: String,
)

data class WdpConfig(
    val profile: ProtectionProfileKind,
    val protectedWorlds: Set<String>,
    val protection: ProtectionSettings,
    val detection: DetectionSettings,
    val performance: PerformanceSettings,
    val datapack: DatapackSettings,
    val notifyPermission: String,
    val logToConsole: Boolean,
    val debug: Boolean,
) {
    companion object {
        fun load(yml: FileConfiguration): WdpConfig {
            val profile = ProtectionProfileKind.valueOf(
                yml.getString("profile", "lotc")!!.uppercase(),
            )
            val base = readProtection(yml.getConfigurationSection("protection"))
            val protection = when (profile) {
                ProtectionProfileKind.LOTC -> ProtectionSettings(
                    customDimension = true,
                    customBiomes = true,
                    extendedHeightmaps = true,
                    extendedPostProcessing = true,
                    blockEntityJsonTrap = true,
                    voidDecoyGenerator = true,
                    stripStructureRefs = false,
                )
                ProtectionProfileKind.BALANCED -> ProtectionSettings(
                    customDimension = true,
                    customBiomes = true,
                    extendedHeightmaps = true,
                    extendedPostProcessing = true,
                    blockEntityJsonTrap = false,
                    voidDecoyGenerator = true,
                    stripStructureRefs = false,
                )
                ProtectionProfileKind.PARANOID -> ProtectionSettings(
                    customDimension = true,
                    customBiomes = true,
                    extendedHeightmaps = true,
                    extendedPostProcessing = true,
                    blockEntityJsonTrap = true,
                    voidDecoyGenerator = true,
                    stripStructureRefs = true,
                )
                ProtectionProfileKind.CUSTOM -> base
            }
            return WdpConfig(
                profile = profile,
                protectedWorlds = yml.getStringList("protected-worlds").map { it.lowercase() }.toSet(),
                protection = protection,
                detection = DetectionSettings(
                    enabled = yml.getBoolean("detection.enabled", true),
                    chunksPerWindow = yml.getInt("detection.chunks-per-window", 120),
                    windowSeconds = yml.getInt("detection.window-seconds", 10),
                    strikesBeforeAction = yml.getInt("detection.strikes-before-action", 3),
                    pattern = ExplorationPattern.valueOf(
                        yml.getString("detection.pattern", "ring")!!.uppercase(),
                    ),
                    suspiciousChannels = yml.getStringList("detection.suspicious-channels")
                        .map { it.lowercase() },
                    action = DetectionAction.valueOf(
                        yml.getString("detection.action", "notify")!!.uppercase(),
                    ),
                    kickMessage = yml.getString(
                        "detection.kick-message",
                        "&cWorld download tools are not allowed.",
                    )!!.replace('&', '§'),
                ),
                performance = PerformanceSettings(
                    mutateOnlyNearPlayers = yml.getBoolean("performance.mutate-only-near-players", true),
                    playerChunkRadius = yml.getInt("performance.player-chunk-radius", 10),
                    mutationsPerTick = yml.getInt("performance.mutations-per-tick", 8),
                    cacheMutations = yml.getBoolean("performance.cache-mutations", true),
                    mutationCacheVersion = yml.getInt("performance.mutation-cache-version", 1),
                ),
                datapack = DatapackSettings(
                    namespace = yml.getString("datapack.namespace", "wdp")!!,
                    autoReload = yml.getBoolean("datapack.auto-reload", true),
                    protectedDimension = yml.getString("datapack.protected-dimension", "protected")!!,
                ),
                notifyPermission = yml.getString("staff.notify-permission", "wdp.notify")!!,
                logToConsole = yml.getBoolean("staff.log-to-console", true),
                debug = yml.getBoolean("debug", false),
            )
        }

        private fun readProtection(section: org.bukkit.configuration.ConfigurationSection?): ProtectionSettings {
            section ?: return ProtectionSettings(
                true, true, true, true, true, true, false,
            )
            return ProtectionSettings(
                customDimension = section.getBoolean("custom-dimension", true),
                customBiomes = section.getBoolean("custom-biomes", true),
                extendedHeightmaps = section.getBoolean("extended-heightmaps", true),
                extendedPostProcessing = section.getBoolean("extended-post-processing", true),
                blockEntityJsonTrap = section.getBoolean("block-entity-json-trap", true),
                voidDecoyGenerator = section.getBoolean("void-decoy-generator", true),
                stripStructureRefs = section.getBoolean("strip-structure-refs", false),
            )
        }
    }
}
