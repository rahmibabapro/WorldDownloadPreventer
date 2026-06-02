package com.ageofmc.wdp.world

import org.bukkit.World
import java.util.concurrent.ConcurrentHashMap

class ProtectedWorldRegistry(initial: Set<String>) {
    private val protected = ConcurrentHashMap.newKeySet<String>().apply { addAll(initial) }

    fun isProtected(world: World): Boolean = protected.contains(world.name.lowercase())

    fun protect(name: String) {
        protected.add(name.lowercase())
    }

    fun unprotect(name: String): Boolean = protected.remove(name.lowercase())

    fun all(): Set<String> = protected.toSet()
}
