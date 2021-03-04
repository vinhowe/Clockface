import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.io.File
import java.nio.file.Paths
import java.util.*
import kotlin.math.*

class Clockface : JavaPlugin(), Listener {
    var clocks: MutableMap<String, ClockData> = mutableMapOf()
    private val clocksDataPath: File = Paths.get(dataFolder.path, "clocks.json").toFile()

    override fun onEnable() {
        super.onEnable()
        server.pluginManager.registerEvents(this, this)
        val clockCommand = ClockCommand(this)
        getCommand("clock")?.setExecutor(clockCommand)
        getCommand("clock")?.tabCompleter = clockCommand

        loadClocks()
        Bukkit.getScheduler().scheduleSyncRepeatingTask(
            this,
            {
                if (clocks.isEmpty()) {
                    return@scheduleSyncRepeatingTask
                }

                for ((_, clock) in clocks) {
                    updateClock(clock)
                }
            },
            10, 10
        )
    }

    private fun clearClock(clock: ClockData) {
        val actualAngle = clock.angle * (Math.PI / 2.0)
        val world = Bukkit.getWorld(
            UUID.fromString(clock.clockWorldUuid)
        )!!

        if (!clock.position.toLocation(world).chunk.isLoaded) {
            return
        }

        fillCirclePoints(0, 0, 0, clock.radius).forEach {
            val blockAtLocation = it.rotateAroundAxis(Vector(0, 1, 0), actualAngle)
                .add(clock.position.toBlockVector())
                .toLocation(world).block
            if (blockAtLocation.type == Material.AIR) {
                return@forEach
            }
            blockAtLocation.type = Material.AIR
        }
        fillCirclePoints(0, 0, 0, (clock.radius * 0.8).roundToInt()).forEach {
            val blockAtLocation = it.add(Vector(0, 0, -1))
                .rotateAroundAxis(Vector(0, 1, 0), actualAngle)
                .add(clock.position.toBlockVector())
                .toLocation(world).block
            if (blockAtLocation.type == Material.AIR) {
                return@forEach
            }
            blockAtLocation.type = Material.AIR
        }
    }

    private fun updateClock(clock: ClockData) {
        val actualAngle = clock.angle * (Math.PI / 2.0)
        val world = Bukkit.getWorld(
            UUID.fromString(clock.clockWorldUuid)
        )!!

        if (!clock.position.toLocation(world).chunk.isLoaded) {
            return
        }

        clearClock(clock)
        val hourAngle = (Math.PI * (2 - (((world.time + 3000) % 12000) * (1 / 6000.0)))) % (2 * Math.PI)
        val minuteAngle = (Math.PI * (2 - (((world.time) % 1000) * (1 / 500.0)))) % (2 * Math.PI)
        lineAtAngle(Vector(), minuteAngle, clock.radius).forEach {
            it.rotateAroundAxis(Vector(0, 1, 0), actualAngle)
                .add(clock.position.toBlockVector())
                .toLocation(world).block.type = clock.hourHandMaterial
        }
        lineAtAngle(Vector(), hourAngle, (clock.radius * 0.75).roundToInt()).forEach {
            it.add(Vector(0, 0, -1)).rotateAroundAxis(Vector(0, 1, 0), actualAngle)
                .add(clock.position.toBlockVector())
                .toLocation(world).block.type = clock.minuteHandMaterial
        }
    }

    fun createClock(clock: ClockData) {
        if (clocks.containsKey(clock.name)) {
            removeClock(clock.name)
        }

        clocks[clock.name] = clock
        flushClocks()
    }

    fun removeClock(name: String) {
        if (!clocks.containsKey(name)) {
            return
        }

        clearClock(clocks.remove(name)!!)
        flushClocks()
    }

    fun fillCirclePoints(centerX: Int, centerY: Int, centerZ: Int, radius: Int): Set<Vector> {
        val points = mutableSetOf<Vector>()

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                if (sqrt(x.toDouble().pow(2) + y.toDouble().pow(2)) < radius + 1.5) {
                    points += Vector(centerX + x, centerY + y, centerZ)
                }
            }
        }

        return points
    }

    fun circlePoints(centerX: Int, centerY: Int, centerZ: Int, radius: Int): List<Vector> {
        var d = (5 - radius * 4) / 4
        var x = 0
        var y = radius
        val points = mutableListOf<Vector>()

        do {
            points += listOf(
                Vector(centerX + x, centerY + y, centerZ),
                Vector(centerX + x, centerY - y, centerZ),
                Vector(centerX - x, centerY + y, centerZ),
                Vector(centerX - x, centerY - y, centerZ),
                Vector(centerX + y, centerY + x, centerZ),
                Vector(centerX + y, centerY - x, centerZ),
                Vector(centerX - y, centerY + x, centerZ),
                Vector(centerX - y, centerY - x, centerZ),
            )
            if (d < 0) {
                d += 2 * x + 1
            } else {
                d += 2 * (x - y) + 1
                y--
            }
            x++
        } while (x <= y)
        return points
    }

    fun linePoints(x0: Int, y0: Int, z: Int, x1: Int, y1: Int): List<Vector> {
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        var x = x0
        var y = y0
        val sx = sign((x1 - x0).toDouble()).toInt()
        val sy = sign((y1 - y0).toDouble()).toInt()

        val points = mutableListOf<Vector>()
        if (dx > dy) {
            var error = dx / 2.0
            while (x != x1) {
                points += Vector(x, y, z)
                error -= dy
                if (error < 0) {
                    y += sy
                    error += dx
                }
                x += sx
            }
        } else {
            var error = dy / 2.0
            while (y != y1) {
                points += Vector(x, y, z)
                error -= dx
                if (error < 0) {
                    x += sx
                    error += dy
                }
                y += sy
            }
        }
        points += Vector(x, y, z)
        return points
    }

    fun lineAtAngle(position: Vector, angle: Double, radius: Int): List<Vector> {
        return linePoints(
            position.blockX,
            position.blockY,
            position.blockZ,
            floor(position.blockX + (cos(angle) * radius)).toInt(),
            floor(position.blockY + (sin(angle) * radius)).toInt()
        )
    }

    private fun flushClocks() {
        val clocksSerialized = Json.encodeToString(clocks)

        if (!dataFolder.exists()) {
            dataFolder.mkdir()
        }

        if (!clocksDataPath.exists()) {
            clocksDataPath.createNewFile()
        }

        clocksDataPath.writeText(clocksSerialized)
    }

    private fun loadClocks() {
        if (!clocksDataPath.exists()) {
            return
        }

        clocks = Json.decodeFromString<MutableMap<String, ClockData>>(clocksDataPath.readText())
    }
}

@Serializable
data class ClockData(
    val name: String,
    val clockWorldUuid: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val hourHandMaterial: Material,
    val minuteHandMaterial: Material,
    val radius: Int,
    val angle: Int,
) {
    val position: Vector
        get() {
            return Vector(x, y, z)
        }
}