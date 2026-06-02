package com.ageofmc.wdp.protection

import com.ageofmc.lib.util.SchedulerCompat
import com.ageofmc.wdp.WorldDownloadPreventerPlugin
import com.ageofmc.wdp.world.ProtectedWorldRegistry
import org.bukkit.Chunk
import org.bukkit.World
import java.util.ArrayDeque

/** Folia: chunk mutations run on the owning region thread via RegionScheduler. */
class ChunkProtectionService(
    private val plugin: WorldDownloadPreventerPlugin,
    private val registry: ProtectedWorldRegistry,
    private val mutator: ChunkMutator,
    private val detector: com.ageofmc.wdp.detection.WorldToolsDetector,
) {
    private val config get() = plugin.configSnapshot
    private val queue = ArrayDeque<ChunkRef>()
    private val queued = HashSet<Long>()
    private var timerTask: SchedulerCompat.CompatTask? = null

    private data class ChunkRef(val world: World, val x: Int, val z: Int)

    fun queue(chunk: Chunk) {
        if (!registry.isProtected(chunk.world)) return
        if (mutator.shouldSkip(chunk)) return
        if (config.performance.mutateOnlyNearPlayers && !hasNearbyPlayer(chunk)) return
        val key = chunkKey(chunk.world, chunk.x, chunk.z)
        if (!queued.add(key)) return
        synchronized(queue) { queue.addLast(ChunkRef(chunk.world, chunk.x, chunk.z)) }
        if (config.detection.enabled) {
            chunk.world.players
                .filter { p ->
                    val pcx = p.location.blockX shr 4
                    val pcz = p.location.blockZ shr 4
                    pcx == chunk.x && pcz == chunk.z
                }
                .forEach { detector.recordChunk(it, chunk.x, chunk.z) }
        }
    }

    fun startTask() {
        if (timerTask != null) return
        timerTask = SchedulerCompat.runTaskTimer(plugin, 1L, 1L) { drain() }
    }

    fun shutdown() {
        timerTask?.cancel()
        timerTask = null
        synchronized(queue) { queue.clear() }
        queued.clear()
    }

    private fun drain() {
        repeat(config.performance.mutationsPerTick) {
            val ref = synchronized(queue) { queue.pollFirst() } ?: return
            queued.remove(chunkKey(ref.world, ref.x, ref.z))
            val chunk = ref.world.getChunkAt(ref.x, ref.z)
            SchedulerCompat.runTaskForChunk(plugin, ref.world, ref.x, ref.z) {
                mutator.mutate(chunk, config.protection)
            }
        }
    }

    private fun hasNearbyPlayer(chunk: Chunk): Boolean {
        val radius = config.performance.playerChunkRadius
        val cx = chunk.x
        val cz = chunk.z
        return chunk.world.players.any { p ->
            val pcx = p.location.blockX shr 4
            val pcz = p.location.blockZ shr 4
            kotlin.math.abs(pcx - cx) <= radius && kotlin.math.abs(pcz - cz) <= radius
        }
    }

    private fun chunkKey(world: World, x: Int, z: Int): Long =
        (world.name.hashCode().toLong() shl 32) or ((x.toLong() and 0xFFFFL) shl 16) or (z.toLong() and 0xFFFFL)
}
