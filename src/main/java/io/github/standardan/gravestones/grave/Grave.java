package io.github.standardan.gravestones.grave;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * One death grave: who it belongs to, where it is, what's inside, and the two
 * entities that represent it in the world (a floating label + a clickable
 * hitbox). Stored in graves.yml so graves survive restarts.
 */
public record Grave(
        String id,
        UUID owner,
        String ownerName,
        String world,
        double x, double y, double z,
        List<ItemStack> items,
        int xp,
        long createdAt,
        int protectSeconds,
        int expireSeconds,
        UUID textUuid,
        UUID interactionUuid) {

    /** Epoch millis until which only the owner may loot. */
    public long protectUntil() {
        return createdAt + protectSeconds * 1000L;
    }

    public boolean expires() {
        return expireSeconds > 0;
    }

    /** Epoch millis at which the grave decays (only meaningful if expires()). */
    public long expireAt() {
        return createdAt + expireSeconds * 1000L;
    }
}
