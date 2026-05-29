package io.github.standardan.gravestones.command;

import io.github.standardan.gravestones.grave.Grave;
import io.github.standardan.gravestones.grave.GraveManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * /graves - list your own graves, or /graves reload (admin).
 */
public final class GraveCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final GraveManager manager;

    public GraveCommand(JavaPlugin plugin, GraveManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("gravestones.admin")) {
                sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            plugin.reloadConfig();
            manager.loadSettings();
            manager.load();
            sender.sendMessage(Component.text("GraveStones reloaded.", NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Usage: /graves reload");
            return true;
        }
        List<Grave> mine = manager.gravesOf(player.getUniqueId());
        if (mine.isEmpty()) {
            player.sendMessage(Component.text("You have no active graves.", NamedTextColor.GRAY));
            return true;
        }
        player.sendMessage(Component.text("Your graves (" + mine.size() + "):", NamedTextColor.AQUA));
        for (Grave g : mine) {
            player.sendMessage(Component.text("- " + g.world() + " at "
                    + (int) g.x() + ", " + (int) g.y() + ", " + (int) g.z(), NamedTextColor.GRAY));
        }
        return true;
    }
}
