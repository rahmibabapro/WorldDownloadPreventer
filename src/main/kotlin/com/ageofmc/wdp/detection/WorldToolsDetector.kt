package com.ageofmc.wdp.detection

import com.ageofmc.lib.folia.PlayerTasks
import com.ageofmc.wdp.WorldDownloadPreventerPlugin
import net.kyori.adventure.text.Component
import com.ageofmc.wdp.config.DetectionAction
import com.ageofmc.wdp.config.ExplorationPattern
import com.ageofmc.wdp.config.WdpConfig
import com.ageofmc.wdp.world.ProtectedWorldRegistry
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Heuristic detection tuned for WorldTools capture behavior:
 * - High chunk throughput while exploring (F12 capture = walk + load chunks)
 * - Ring/grid expansion from a center point
 * - Optional plugin channel fingerprint
 *
 * WorldTools is client-side Fabric; it records chunk/entity packets from the server.
 * It does not send a standard plugin channel, but modded clients may expose channels.
 */
class WorldToolsDetector(
    private val plugin: WorldDownloadPreventerPlugin,
    private val registry: ProtectedWorldRegistry,
) {
    private val config get() = plugin.configSnapshot
    private data class Window(
        var chunkCount: Int = 0,
        var minX: Int = Int.MAX_VALUE,
        var maxX: Int = Int.MIN_VALUE,
        var minZ: Int = Int.MAX_VALUE,
        var maxZ: Int = Int.MIN_VALUE,
        var strikes: Int = 0,
        var windowStartMs: Long = System.currentTimeMillis(),
    )

    private val windows = ConcurrentHashMap<UUID, Window>()
    private val flaggedChannels = ConcurrentHashMap.newKeySet<UUID>()

    fun recordChunk(player: Player, chunkX: Int, chunkZ: Int) {
        if (!config.detection.enabled) return
        if (!registry.isProtected(player.world)) return
        if (player.hasPermission("wdp.bypass")) return

        val w = windows.computeIfAbsent(player.uniqueId) { Window() }
        val now = System.currentTimeMillis()
        if (now - w.windowStartMs > config.detection.windowSeconds * 1000L) {
            evaluateWindow(player, w)
            w.windowStartMs = now
            w.chunkCount = 0
            w.minX = Int.MAX_VALUE
            w.maxX = Int.MIN_VALUE
            w.minZ = Int.MAX_VALUE
            w.maxZ = Int.MIN_VALUE
        }
        w.chunkCount++
        w.minX = minOf(w.minX, chunkX)
        w.maxX = maxOf(w.maxX, chunkX)
        w.minZ = minOf(w.minZ, chunkZ)
        w.maxZ = maxOf(w.maxZ, chunkZ)
    }

    fun recordPluginChannel(player: Player, channel: String) {
        if (!config.detection.enabled) return
        val lower = channel.lowercase()
        if (config.detection.suspiciousChannels.any { lower.contains(it) }) {
            flaggedChannels.add(player.uniqueId)
            alert(player, "suspicious channel: $channel")
        }
    }

    private fun evaluateWindow(player: Player, w: Window) {
        if (w.chunkCount < config.detection.chunksPerWindow) return
        if (!matchesPattern(w)) return
        w.strikes++
        if (w.strikes < config.detection.strikesBeforeAction) {
            alert(player, "high chunk rate (${w.chunkCount}/${config.detection.windowSeconds}s) strike ${w.strikes}")
            return
        }
        act(player, w.chunkCount)
        w.strikes = 0
    }

    private fun matchesPattern(w: Window): Boolean =
        when (config.detection.pattern) {
            ExplorationPattern.ANY -> true
            ExplorationPattern.GRID -> {
                val dx = w.maxX - w.minX
                val dz = w.maxZ - w.minZ
                dx >= 4 && dz >= 4
            }
            ExplorationPattern.RING -> {
                val dx = w.maxX - w.minX
                val dz = w.maxZ - w.minZ
                val ratio = dx.toDouble() / (dz + 1).coerceAtLeast(1)
                ratio in 0.5..2.0 && dx >= 3
            }
        }

    private fun act(player: Player, chunks: Int) {
        val msg = "WorldTools-like exploration: ${player.name} loaded $chunks chunks quickly"
        alert(player, msg)
        when (config.detection.action) {
            DetectionAction.NOTIFY -> Unit
            DetectionAction.KICK, DetectionAction.TEMPBAN ->
                PlayerTasks.sync(plugin, player) {
                    player.kick(Component.text(config.detection.kickMessage))
                }
        }
    }

    private fun alert(player: Player, detail: String) {
        if (config.logToConsole) {
            plugin.logger.warning("[WDP] $detail")
        }
        Bukkit.getOnlinePlayers()
            .filter { it.hasPermission(config.notifyPermission) }
            .forEach { staff ->
                staff.sendMessage("§c[WDP] §f$detail")
            }
    }

    fun clear(player: Player) {
        windows.remove(player.uniqueId)
        flaggedChannels.remove(player.uniqueId)
    }
}
