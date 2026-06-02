package com.ageofmc.wdp

import com.ageofmc.lib.platform.ServerRequirements
import com.ageofmc.wdp.command.WdpCommand
import com.ageofmc.wdp.config.WdpConfig
import com.ageofmc.wdp.datapack.DatapackManager
import com.ageofmc.wdp.detection.WorldToolsDetector
import com.ageofmc.wdp.listener.WdpListeners
import com.ageofmc.wdp.nms.NmsHandles
import com.ageofmc.wdp.protection.ChunkMutator
import com.ageofmc.wdp.protection.ChunkProtectionService
import com.ageofmc.wdp.world.ProtectedWorldRegistry
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class WorldDownloadPreventerPlugin : JavaPlugin() {
    lateinit var configSnapshot: WdpConfig
        private set
    lateinit var registry: ProtectedWorldRegistry
        private set
    lateinit var datapackManager: DatapackManager
        private set
    lateinit var mutator: ChunkMutator
        private set
    lateinit var chunkService: ChunkProtectionService
        private set
    lateinit var detector: WorldToolsDetector
        private set

    override fun onEnable() {
        if (!ServerRequirements.validate(logger, "WorldDownloadPreventer")) {
            server.pluginManager.disablePlugin(this)
            return
        }

        saveDefaultConfig()
        reloadAll()
        extractBundledDatapack()
        NmsHandles.resolve()

        datapackManager.deployAllProtected(Bukkit.getWorlds().filter { registry.isProtected(it) })

        server.pluginManager.registerEvents(WdpListeners(this, registry, chunkService, detector), this)

        val cmd = WdpCommand(this)
        getCommand("wdp")?.setExecutor(cmd)
        getCommand("wdp")?.tabCompleter = cmd

        logger.info(
            "WorldDownloadPreventer enabled — Folia ${ServerRequirements.MINECRAFT_VERSION}, " +
                "JDK ${ServerRequirements.REQUIRED_JAVA_FEATURE}, profile=${configSnapshot.profile}, " +
                "protected=${registry.all().size}, nms=${NmsHandles.isAvailable()}",
        )
    }

    override fun onDisable() {
        if (::chunkService.isInitialized) chunkService.shutdown()
        logger.info("WorldDownloadPreventer disabled")
    }

    fun reloadAll() {
        reloadConfig()
        configSnapshot = WdpConfig.load(config)
        registry = ProtectedWorldRegistry(configSnapshot.protectedWorlds)
        datapackManager = DatapackManager(this)
        mutator = ChunkMutator(this)
        if (::chunkService.isInitialized) chunkService.shutdown()
        detector = WorldToolsDetector(this, registry)
        chunkService = ChunkProtectionService(this, registry, mutator, detector)
        chunkService.startTask()
    }

    private fun extractBundledDatapack() {
        val root = dataFolder.toPath().resolve("datapack/wdp_pack")
        if (root.resolve("pack.mcmeta").toFile().exists()) return
        listOf(
            "datapack/wdp_pack/pack.mcmeta",
            "datapack/wdp_pack/data/wdp/dimension_type/extended.json",
            "datapack/wdp_pack/data/wdp/worldgen/biome/trap_highlands.json",
            "datapack/wdp_pack/data/wdp/dimension/protected.json",
        ).forEach { path ->
            val out = root.resolve(path.removePrefix("datapack/wdp_pack/"))
            Files.createDirectories(out.parent)
            getResource(path)?.use { Files.copy(it, out, StandardCopyOption.REPLACE_EXISTING) }
        }
    }
}
