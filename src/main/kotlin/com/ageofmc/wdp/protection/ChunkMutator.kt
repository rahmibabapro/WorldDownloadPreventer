package com.ageofmc.wdp.protection

import com.ageofmc.wdp.WorldDownloadPreventerPlugin
import com.ageofmc.wdp.config.ProtectionSettings
import com.ageofmc.wdp.nms.NmsHandles
import org.bukkit.Chunk
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Method

/**
 * Mutates in-memory LevelChunk data before it is serialized to clients (WorldTools captures packets).
 *
 * Targets WorldTools save pipeline:
 * - Chunk sections + yPos (extended height)
 * - Heightmaps (43 vs 37 long array)
 * - PostProcessing ShortList[] per section index
 * - Section biome palettes (custom namespace)
 * - Block entity CustomName JSON traps
 */
class ChunkMutator(
    private val plugin: WorldDownloadPreventerPlugin,
) {
    private val config get() = plugin.configSnapshot
    private val cacheKey = NamespacedKey(plugin, "mutation_version")
    private val trapBiomeId = "${config.datapack.namespace}:trap_highlands"

    private val heightmapTypesClass by lazy {
        Class.forName("net.minecraft.world.level.levelgen.Heightmap\$Types")
    }
    private val typesValues: kotlin.Array<*> by lazy {
        heightmapTypesClass.enumConstants ?: emptyArray<Any>()
    }

    fun shouldSkip(chunk: Chunk): Boolean {
        if (!config.performance.cacheMutations) return false
        val pdc = chunk.persistentDataContainer
        val v = pdc.get(cacheKey, PersistentDataType.INTEGER) ?: return false
        return v == config.performance.mutationCacheVersion
    }

    fun markDone(chunk: Chunk) {
        if (!config.performance.cacheMutations) return
        chunk.persistentDataContainer.set(
            cacheKey,
            PersistentDataType.INTEGER,
            config.performance.mutationCacheVersion,
        )
    }

    fun mutate(chunk: Chunk, settings: ProtectionSettings): Boolean {
        var changed = false
        if (settings.customBiomes) changed = applyTrapBiomesBukkit(chunk) or changed
        if (!NmsHandles.isAvailable()) {
            if (changed) markDone(chunk)
            return changed
        }
        val levelChunk = NmsHandles.levelChunk(chunk) ?: return changed
        return try {
            if (settings.extendedHeightmaps) changed = applyExtendedHeightmaps(levelChunk) or changed
            if (settings.extendedPostProcessing) {
                changed = applyExtendedPostProcessing(levelChunk) or changed
            }
            if (settings.customBiomes) changed = applyTrapBiomes(levelChunk) or changed
            if (settings.blockEntityJsonTrap) changed = applyBlockEntityTrap(levelChunk) or changed
            if (changed) markDone(chunk)
            changed
        } catch (ex: Exception) {
            if (config.debug) plugin.logger.warning("Chunk mutate ${chunk.x},${chunk.z}: ${ex.message}")
            changed
        }
    }

    private fun applyTrapBiomesBukkit(chunk: Chunk): Boolean {
        val key = NamespacedKey(config.datapack.namespace, "trap_highlands")
        val biome = org.bukkit.Registry.BIOME.get(key) ?: return false
        val baseX = chunk.x shl 4
        val baseZ = chunk.z shl 4
        for (y in -64..319 step 16) {
            chunk.world.setBiome(baseX + 8, y, baseZ + 8, biome)
        }
        return true
    }

    /**
     * LOTC uses 43-length heightmap storage for extended worlds; vanilla SP expects 37.
     */
    private fun applyExtendedHeightmaps(levelChunk: Any): Boolean {
        val heightmapsField = levelChunk.javaClass.getDeclaredField("heightmaps").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val map = heightmapsField.get(levelChunk) as? MutableMap<Any, Any> ?: return false
        val extendedSize = 43
        val filler = LongArray(extendedSize) { 0L }
        for (type in typesValues) {
            val hm = map[type] ?: continue
            val dataField = hm.javaClass.getDeclaredField("data").apply { isAccessible = true }
            val current = dataField.get(hm)
            if (current is LongArray && current.size == extendedSize) continue
            dataField.set(hm, filler.copyOf(extendedSize))
        }
        return true
    }

    /**
     * PostProcessing is indexed by section; extended worlds use 40 entries (yPos -8 .. 31).
     */
    private fun applyExtendedPostProcessing(levelChunk: Any): Boolean {
        val targetSections = 40
        val lists = findShortListArray(levelChunk, "postProcessing")
            ?: findShortListArray(levelChunk, "postProcessingBlocks")
        if (lists != null && ReflectArray.getLength(lists) == targetSections) return false

        val emptyListClass = Class.forName("it.unimi.dsi.fastutil.shorts.ShortArrayList")
        val emptyCtor = emptyListClass.getConstructor()
        val newArray = ReflectArray.newInstance(emptyListClass, targetSections)
        for (i in 0 until targetSections) {
            ReflectArray.set(newArray, i, emptyCtor.newInstance())
        }
        setShortListArray(levelChunk, newArray)
        return true
    }

    private fun findShortListArray(levelChunk: Any, vararg names: String): Any? {
        for (name in names) {
            try {
                val f = levelChunk.javaClass.getDeclaredField(name).apply { isAccessible = true }
                return f.get(levelChunk)
            } catch (_: NoSuchFieldException) {
            }
        }
        return null
    }

    private fun setShortListArray(levelChunk: Any, array: Any) {
        for (name in arrayOf("postProcessing", "postProcessingBlocks")) {
            try {
                val f = levelChunk.javaClass.getDeclaredField(name).apply { isAccessible = true }
                f.set(levelChunk, array)
                return
            } catch (_: NoSuchFieldException) {
            }
        }
    }

    private fun applyTrapBiomes(levelChunk: Any): Boolean {
        val sections = getSections(levelChunk) ?: return false
        val holderClass = Class.forName("net.minecraft.core.Holder")
        val biomeRegistry = resolveBiomeHolder(trapBiomeId) ?: return false
        var changed = false
        for (i in 0 until ReflectArray.getLength(sections)) {
            val section = ReflectArray.get(sections, i) ?: continue
            val biomesField = section.javaClass.getDeclaredField("biomes").apply { isAccessible = true }
            val container = biomesField.get(section) ?: continue
            val replace = replacePaletteWithTrap(container, biomeRegistry)
            changed = replace or changed
        }
        return changed
    }

    private fun replacePaletteWithTrap(container: Any, biomeHolder: Any): Boolean {
        return try {
            val dataField = container.javaClass.getDeclaredField("data").apply { isAccessible = true }
            val paletteField = dataField.type.getDeclaredField("palette").apply { isAccessible = true }
            val data = dataField.get(container)
            val palette = paletteField.get(data)
            if (palette is List<*>) {
                val list = palette as MutableList<Any>
                if (list.isNotEmpty() && list.all { it == biomeHolder }) return false
                list.clear()
                list.add(biomeHolder)
                return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveBiomeHolder(id: String): Any? = try {
        val server = org.bukkit.Bukkit.getServer()
        val craft = server.javaClass.getMethod("getServer").invoke(server)
        val registryAccess = craft.javaClass.getMethod("registryAccess").invoke(craft)
        val registry = registryAccess.javaClass.getMethod("registryOrThrow", Class.forName("net.minecraft.core.RegistryKey"))
            .invoke(registryAccess, biomeRegistryKey())
        val resourceKey = Class.forName("net.minecraft.resources.ResourceKey")
            .getMethod("create", Class.forName("net.minecraft.core.RegistryKey"), Class.forName("net.minecraft.resources.ResourceLocation"))
            .invoke(null, biomeRegistryKey(), resourceLocation(id))
        registry.javaClass.getMethod("getHolderOrThrow", resourceKey.javaClass).invoke(registry, resourceKey)
    } catch (_: Exception) {
        null
    }

    private fun biomeRegistryKey(): Any =
        Class.forName("net.minecraft.core.registries.Registries").getField("BIOME").get(null)

    private fun resourceLocation(id: String): Any =
        Class.forName("net.minecraft.resources.ResourceLocation").getMethod("parse", String::class.java)
            .invoke(null, id)

    private fun getSections(levelChunk: Any): Any? = try {
        val m: Method = levelChunk.javaClass.getMethod("getSections")
        m.invoke(levelChunk)
    } catch (_: Exception) {
        try {
            val f = levelChunk.javaClass.getDeclaredField("sections").apply { isAccessible = true }
            f.get(levelChunk)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Gson in vanilla SP fails on non-JSON text components — WorldTools still saves the NBT.
     */
    private fun applyBlockEntityTrap(levelChunk: Any): Boolean {
        val blockEntities = try {
            val m = levelChunk.javaClass.getMethod("getBlockEntities")
            @Suppress("UNCHECKED_CAST")
            m.invoke(levelChunk) as? Map<*, *>
        } catch (_: Exception) {
            null
        } ?: return false

        val trap = """{"text":"Wooden Stool","italic":false}"""
        var changed = false
        for ((_, be) in blockEntities) {
            if (be == null) continue
            val tag = be.javaClass.getMethod("getTag").invoke(be) as? Any ?: continue
            val setString = tag.javaClass.getMethod("putString", String::class.java, String::class.java)
            val getString = tag.javaClass.getMethod("getString", String::class.java)
            val existing = getString.invoke(tag, "CustomName") as? String
            if (existing == trap) continue
            setString.invoke(tag, "CustomName", trap)
            changed = true
            break // one per chunk is enough for protection signal
        }
        return changed
    }
}
