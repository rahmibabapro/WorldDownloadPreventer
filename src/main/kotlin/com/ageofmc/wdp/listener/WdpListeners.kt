package com.ageofmc.wdp.listener

import com.ageofmc.wdp.WorldDownloadPreventerPlugin
import com.ageofmc.wdp.detection.WorldToolsDetector
import com.ageofmc.wdp.protection.ChunkProtectionService
import com.ageofmc.wdp.world.ProtectedWorldRegistry
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent

class WdpListeners(
    private val plugin: WorldDownloadPreventerPlugin,
    private val registry: ProtectedWorldRegistry,
    private val chunkService: ChunkProtectionService,
    private val detector: WorldToolsDetector,
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!registry.isProtected(event.world)) return
        chunkService.queue(event.chunk)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        // Paper: detect plugin channels registered by client (WorldTools / Fabric bridge)
        try {
            val channels = player.listeningPluginChannels
            channels.forEach { detector.recordPluginChannel(player, it) }
        } catch (_: Exception) {
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        detector.clear(event.player)
    }
}
