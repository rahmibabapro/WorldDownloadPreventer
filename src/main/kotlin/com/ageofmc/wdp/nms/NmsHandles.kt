package com.ageofmc.wdp.nms

import org.bukkit.Bukkit
import org.bukkit.Chunk
import java.lang.reflect.Method

object NmsHandles {
    private var craftChunkClass: Class<*>? = null
    private var getHandleMethod: Method? = null

    fun resolve(): Boolean {
        if (getHandleMethod != null) return true
        return try {
            val pkg = Bukkit.getServer().javaClass.`package`.name
            craftChunkClass = Class.forName("$pkg.craftbukkit.CraftChunk")
            getHandleMethod = craftChunkClass!!.getMethod("getHandle")
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isAvailable(): Boolean = resolve()

    fun levelChunk(chunk: Chunk): Any? = try {
        val craft = craftChunkClass?.cast(chunk) ?: return null
        getHandleMethod?.invoke(craft)
    } catch (_: Exception) {
        null
    }
}
