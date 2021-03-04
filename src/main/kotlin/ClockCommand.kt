import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import java.lang.NumberFormatException
import kotlin.math.round

class ClockCommand(private val clockface: Clockface) : TabExecutor {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.isEmpty()) {
            return emptyList()
        }

        if (args.size == 1) {
            return listOf("add", "delete")
        }

        if (args.size == 2) {
            if (args[0] == "delete") {
                return clockface.clocks.keys.toList()
            }
        }

        if (args.size in 4..5) {
            if (args[0] == "add") {
                return Material.values().filter { it.isBlock }.map { it.toString().toLowerCase() }
            }
        }

        return emptyList()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§4You must be an in-game player to use this command.")
            return true
        }
        if (!sender.hasPermission("clockface.manage")) {
            sender.sendMessage("§4You do not have access to that command.")
            return true
        }
        val playerAngle = round(((180 - sender.location.yaw.toDouble()) % 360) * (4.0 / 360.0)).toInt()
        if (args.isEmpty()) {
            return false
        }

        if (args[0] == "add") {
            if (args.size != 5) {
                return false
            }
            val name = args[1]

            if (clockface.clocks.containsKey(name)) {
                sender.sendMessage("§4A clock exists with the name '$name'. Run '/$label delete $name' to delete it.")
                return true
            }

            val radius: Int
            try {
                radius = args[2].toInt()
            } catch (_: NumberFormatException) {
                sender.sendMessage("§4'${args[2]}' is not a valid number.")
                return true
            }

            val hourHandMaterial: Material? = Material.getMaterial(args[3].toUpperCase())

            if (hourHandMaterial == null || !hourHandMaterial.isBlock) {
                sender.sendMessage("§4'${args[3]}' is not a valid block type.")
                return true
            }

            val minuteHandMaterial: Material? = Material.getMaterial(args[4].toUpperCase())

            if (minuteHandMaterial == null || !minuteHandMaterial.isBlock) {
                sender.sendMessage("§4'${args[4]}' is not a valid block type.")
                return true
            }

            val blockLocation = sender.location.toVector()

            clockface.createClock(
                ClockData(
                    name,
                    sender.world.uid.toString(),
                    blockLocation.blockX,
                    blockLocation.blockY,
                    blockLocation.blockZ,
                    hourHandMaterial,
                    minuteHandMaterial,
                    radius,
                    playerAngle
                )
            )

            sender.sendMessage("§6Created a new clock '$name' at (${blockLocation.blockX}, ${blockLocation.blockY}, ${blockLocation.blockZ}).")
            return true
        }

        if (args[0] == "delete") {
            if (args.size != 2) {
                return false
            }
            val name = args[1]

            if (!clockface.clocks.containsKey(name)) {
                sender.sendMessage("§4No clock exists with the name '$name'.")
                return true
            }

            clockface.removeClock(name)

            sender.sendMessage("§6Deleted clock '$name'.")
            return true
        }
        return false
    }
}