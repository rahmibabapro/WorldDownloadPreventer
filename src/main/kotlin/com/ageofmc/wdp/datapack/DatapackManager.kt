package com.ageofmc.wdp.datapack

import com.ageofmc.lib.folia.PlayerTasks
import com.ageofmc.wdp.WorldDownloadPreventerPlugin
import org.bukkit.Bukkit
import org.bukkit.World
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class DatapackManager(private val plugin: WorldDownloadPreventerPlugin) {
    private val config get() = plugin.configSnapshot
    private val packFolderName = "wdp_pack"

    fun deployToWorld(world: World): Boolean {
        val source = plugin.dataFolder.toPath().resolve("datapack/$packFolderName")
        if (!source.resolve("pack.mcmeta").toFile().exists()) {
            plugin.logger.warning("Bundled datapack missing — restart after first enable.")
            return false
        }
        val target = world.worldFolder.toPath().resolve("datapacks/$packFolderName")
        return try {
            if (target.toFile().exists()) target.toFile().deleteRecursively()
            Files.walk(source).forEach { path ->
                val dest = target.resolve(source.relativize(path))
                if (Files.isDirectory(path)) Files.createDirectories(dest)
                else {
                    Files.createDirectories(dest.parent)
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            if (config.datapack.autoReload) {
                PlayerTasks.global(plugin) {
                    runCatching { Bukkit.getServer().reloadData() }
                }
            }
            true
        } catch (ex: Exception) {
            plugin.logger.warning("Datapack deploy failed for ${world.name}: ${ex.message}")
            false
        }
    }

    fun deployAllProtected(worlds: Collection<World>) = worlds.forEach { deployToWorld(it) }
}
