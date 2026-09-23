package dev.watchwolf.client;

import dev.watchwolf.entities.Container;
import dev.watchwolf.entities.Position;
import dev.watchwolf.entities.entities.Entity;
import dev.watchwolf.entities.items.Item;
import dev.watchwolf.tester.Petition;

import java.io.File;
import java.io.IOException;

public interface ClientPetition extends Petition {
    public void sendMessage(String msg) throws IOException;
    public String runCommand(String cmd, int timeout) throws IOException;
    public void breakBlock(Position block) throws IOException;
    public void setBlock(Position block) throws IOException;
    public void equipItemInHand(Item item) throws IOException;
    public void moveTo(Position pos) throws IOException;
    public void lookAt(float pitch, float yaw) throws IOException;
    public void hit() throws IOException;
    public void use() throws IOException;
    public void attack(String UUID) throws IOException;
    public Position getPosition() throws IOException;
    public float getPitch() throws IOException;
    public float getYaw() throws IOException;
    public Container getInventory() throws IOException;
    public int start_recording() throws IOException;
    public void stop_recording(int id, File out_path) throws IOException;
}
