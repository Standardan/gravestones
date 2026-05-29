package io.github.standardan.gravestones.listener;

import io.github.standardan.gravestones.grave.Grave;
import io.github.standardan.gravestones.grave.GraveManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures drops into a grave on death, and hands them back when the grave's
 * clickable hitbox (an Interaction entity) is right-clicked.
 */
public final class GraveListener implements Listener {

    private final GraveManager manager;

    public GraveListener(GraveManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return; // keepInventory gamerule on - nothing to bury
        }
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        int xp = event.getDroppedExp();
        if (drops.isEmpty() && xp == 0) {
            return;
        }
        // Take the drops out of the world so they don't scatter, and bury them.
        event.getDrops().clear();
        event.setDroppedExp(0);

        Grave grave = manager.createGrave(event.getEntity(), drops, xp);
        event.getEntity().sendMessage(Component.text("Your items are in a grave at "
                + (int) grave.x() + ", " + (int) grave.y() + ", " + (int) grave.z() + ".",
                NamedTextColor.YELLOW));
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // fires for both hands; only act on the main hand
        }
        if (!(event.getRightClicked() instanceof Interaction interaction)) {
            return;
        }
        String id = manager.graveIdOf(interaction);
        if (id == null) {
            return; // not one of our graves
        }
        event.setCancelled(true);

        Grave grave = manager.getById(id);
        if (grave == null) {
            interaction.remove(); // stray hitbox with no record - clean it up
            return;
        }
        Player player = event.getPlayer();
        boolean owner = player.getUniqueId().equals(grave.owner());
        if (!owner && !player.hasPermission("gravestones.bypass")
                && System.currentTimeMillis() < grave.protectUntil()) {
            long left = (grave.protectUntil() - System.currentTimeMillis()) / 1000 + 1;
            player.sendMessage(Component.text("This grave is locked for another " + left + "s.",
                    NamedTextColor.RED));
            return;
        }
        manager.loot(player, grave);
        player.sendMessage(Component.text("You looted " + grave.ownerName() + "'s grave.",
                NamedTextColor.GREEN));
    }
}
