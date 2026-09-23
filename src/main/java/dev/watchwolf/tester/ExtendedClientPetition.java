package dev.watchwolf.tester;

import dev.watchwolf.client.ClientPetition;
import dev.watchwolf.entities.Position;
import dev.watchwolf.entities.blocks.Block;
import dev.watchwolf.entities.entities.Entity;
import dev.watchwolf.entities.items.Item;
import dev.watchwolf.entities.items.ItemNotFoundInContainerException;
import dev.watchwolf.entities.items.ItemType;

import java.io.IOException;
import java.util.Arrays;

/**
 * Convenience operations built on top of the client petition API.
 */
public interface ExtendedClientPetition extends ClientPetition {
    /**
     * Instead of calculating the pitch and yaw, just specify the target position
     * @param targetPosition Place to look
     * @throws IOException Socket error
     */
    default public void lookAt(Position targetPosition) throws IOException {
        final float PLAYER_HEIGHT = 1.6f;
        Position currentPosition = this.getPosition().add(0,PLAYER_HEIGHT,0); // we need to offset the "camera" (player's eyes)

        double dx = targetPosition.getX() - currentPosition.getX();
        double dy = targetPosition.getY() - currentPosition.getY();
        double dz = targetPosition.getZ() - currentPosition.getZ();

        double distanceXZ = Math.sqrt(dx*dx + dz*dz);
        double pitch;
        if (distanceXZ != 0f) pitch = Math.toDegrees(Math.atan2(-dy, distanceXZ));
        else pitch = (dy > 0) ? -90 : 90;

        double yaw;
        if (dz != 0f) yaw = Math.toDegrees(Math.atan2(-dx, dz));
        else yaw = (dx > 0) ? -90 : 90;

        this.lookAt((float)pitch, (float)yaw);
    }

    /**
     * Set a block from your inventory
     * @param item Block to set
     * @param pos Where to place the block
     * @throws IOException Socket error
     */
    default public void setBlock(Item item, Position pos) throws IOException {
        this.equipItemInHand(item);
        this.setBlock(pos);
    }

    /**
     * Set a block from your inventory
     * @param block Block to set
     * @param pos Where to place the block
     * @throws IOException Socket error
     * @throws ItemNotFoundInContainerException The specified item couldn't be found on player's inventory
     */
    default public void setBlock(final ItemType block, Position pos) throws IOException, ItemNotFoundInContainerException {
        Item i = Arrays.stream(this.getInventory().getItems()).filter(it -> it.getType().equals(block)).findFirst().orElseThrow(() -> new ItemNotFoundInContainerException(block.name() + " not found in player's inventory."));
        this.equipItemInHand(i);
        this.setBlock(pos);
    }

    /**
     * Set a block from your inventory
     * @param block Block to set
     * @param pos Where to place the block
     * @throws IOException Socket error
     * @throws ItemNotFoundInContainerException The specified item couldn't be found on player's inventory
     */
    default public void setBlock(Block block, Position pos) throws IOException, ItemNotFoundInContainerException {
        this.setBlock(block.getItemType(), pos);
    }

    default public void attack(Entity e) throws IOException {
        this.attack(e.getUUID());
    }

    default public String runCommand(String cmd) throws IOException {
        return this.runCommand(cmd, 6000); // 6s should be more than enough for low-tier servers to process anything they need
    }
}
