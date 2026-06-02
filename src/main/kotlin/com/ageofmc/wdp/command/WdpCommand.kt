package com.ageofmc.wdp.command

import com.ageofmc.wdp.WorldDownloadPreventerPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class WdpCommand(private val plugin: WorldDownloadPreventerPlugin) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("wdp.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
            return true
        }
        if (args.isEmpty()) {
            help(sender)
            return true
        }
        when (args[0].lowercase()) {
            "reload" -> {
                plugin.reloadAll()
                sender.sendMessage(Component.text("WorldDownloadPreventer reloaded.", NamedTextColor.GREEN))
            }
            "status" -> {
                val cfg = plugin.configSnapshot
                sender.sendMessage(Component.text("Profile: ${cfg.profile}", NamedTextColor.AQUA))
                sender.sendMessage(Component.text("Protected worlds: ${plugin.registry.all().joinToString()}", NamedTextColor.GRAY))
                sender.sendMessage(Component.text("MC: ${com.ageofmc.lib.platform.ServerRequirements.MINECRAFT_VERSION} | Folia: ${com.ageofmc.lib.platform.ServerRequirements.isFoliaRuntime()}", NamedTextColor.GRAY))
                sender.sendMessage(Component.text("NMS: ${if (com.ageofmc.wdp.nms.NmsHandles.isAvailable()) "OK" else "unavailable"}", NamedTextColor.GRAY))
            }
            "protect" -> {
                val name = args.getOrNull(1) ?: return missing(sender, "world name")
                val world = Bukkit.getWorld(name)
                if (world == null) {
                    sender.sendMessage(Component.text("World not found.", NamedTextColor.RED))
                    return true
                }
                plugin.registry.protect(name)
                plugin.datapackManager.deployToWorld(world)
                sender.sendMessage(Component.text("Protected ${world.name}", NamedTextColor.GREEN))
            }
            "unprotect" -> {
                val name = args.getOrNull(1) ?: return missing(sender, "world name")
                plugin.registry.unprotect(name)
                sender.sendMessage(Component.text("Unprotected $name", NamedTextColor.YELLOW))
            }
            "datapack" -> {
                val worlds = Bukkit.getWorlds().filter { plugin.registry.isProtected(it) }
                plugin.datapackManager.deployAllProtected(worlds)
                sender.sendMessage(Component.text("Datapack deployed to ${worlds.size} world(s).", NamedTextColor.GREEN))
            }
            "scan" -> {
                val player = Bukkit.getOnlinePlayers().firstOrNull()
                if (player == null) {
                    sender.sendMessage(Component.text("No online players to test NMS.", NamedTextColor.RED))
                    return true
                }
                val chunk = player.location.chunk
                val ok = plugin.mutator.mutate(chunk, plugin.configSnapshot.protection)
                sender.sendMessage(Component.text("Test mutate ${chunk.x},${chunk.z}: $ok", NamedTextColor.GREEN))
            }
            else -> help(sender)
        }
        return true
    }

    private fun help(sender: CommandSender) {
        sender.sendMessage(Component.text("WDP: reload | status | protect <world> | unprotect <world> | datapack | scan", NamedTextColor.YELLOW))
    }

    private fun missing(sender: CommandSender, what: String): Boolean {
        sender.sendMessage(Component.text("Missing: $what", NamedTextColor.RED))
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (!sender.hasPermission("wdp.admin")) return emptyList()
        if (args.size == 1) {
            return listOf("reload", "status", "protect", "unprotect", "datapack", "scan")
                .filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && args[0].lowercase() in setOf("protect", "unprotect")) {
            return Bukkit.getWorlds().map { it.name }.filter { it.startsWith(args[1], true) }
        }
        return emptyList()
    }
}
