package dev.watchwolf.tester;

import dev.watchwolf.client.ClientPetition;
import dev.watchwolf.core.entities.Container;
import dev.watchwolf.core.protocol.Message;
import dev.watchwolf.core.entities.Position;
import dev.watchwolf.core.protocol.SocketHelper;
import dev.watchwolf.core.entities.entities.Entity;
import dev.watchwolf.core.entities.files.ConfigFile;
import dev.watchwolf.core.entities.items.Item;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;

public class ClientSocket implements ClientPetition {
    protected final String username;
    private final Socket socket;
    private final AsyncPetitionResolver asyncResolver;
    private final SynchronizationManager syncManager;

    public ClientSocket(String username, Socket socket, AsyncPetitionResolver asyncResolver, SynchronizationManager syncManager) {
        this.username = username;
        this.socket = socket;
        this.asyncResolver = asyncResolver;
        this.syncManager = syncManager;
    }

    public String getClientUsername() {
        return this.username;
    }

    protected Socket getSocket() {
        return this.socket;
    }

    @Override
    public void sendMessage(String msg) throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);

        // send message header
        message.add((short) 0b000000000011_0_011);

        message.add(msg);

        message.send();
    }

    @Override
    public String runCommand(String cmd, int timeout) throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);

        // send command header
        message.add((short) 0b000000000100_0_011);

        message.add(cmd);
        message.add((short) timeout);

        synchronized (this.socket) {
            message.send();

            // read response
            DataInputStream dis = new DataInputStream(this.socket.getInputStream());
            int r = SocketHelper.readShort(dis);
            while (r != 0b000000000100_1_011) {
                this.asyncResolver.processAsyncReturn(r, dis); // expected return, found async return from another request
                r = SocketHelper.readShort(dis);
            }
            return SocketHelper.readString(dis);
        }
    }

    @Override
    public void breakBlock(Position block) throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);

        // break block header
        message.add((short) 0b000000000101_0_011);

        message.add(block);

        message.send();
    }

    @Override
    public void setBlock(Position block) throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);

        // break block header
        message.add((short) 0b000000001100_0_011);

        message.add(block);

        message.send();
    }

    @Override
    public void equipItemInHand(Item item) throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);

        // equip in hand header
        message.add((short) 0b000000000110_0_011);

        message.add(item);

        message.send();
    }

    @Override
    public void moveTo(Position pos) throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);

        // move at header
        message.add((short) 0b000000000111_0_011);

        message.add(pos);

        message.send();
    }

    @Override
    public void lookAt(float pitch, float yaw) throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);

        // look at header
        message.add((short) 0b000000001000_0_011);

        message.add(pitch);
        message.add(yaw);

        message.send();
    }

    @Override
    public void hit() throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);

        // hit header
        message.add((short) 0b000000001010_0_011);

        message.send();
    }

    @Override
    public void use() throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);

        // use header
        message.add((short) 0b000000001011_0_011);

        message.send();
    }

    @Override
    public void attack(String uuid) throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);

        // look at header
        message.add((short) 0b000000001101_0_011);

        message.add(uuid);

        message.send();
    }

    @Override
    public Position getPosition() throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);
        message.add((short) 0b000000010001_0_011);

        synchronized (this.socket) {
            message.send();
            DataInputStream dis = new DataInputStream(this.socket.getInputStream());
            this.awaitResponse(dis, 0b000000010001_1_011);
            return (Position) SocketHelper.readObject(dis, Position.class);
        }
    }

    @Override
    public float getPitch() throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);
        message.add((short) 0b000000010010_0_011);

        synchronized (this.socket) {
            message.send();
            DataInputStream dis = new DataInputStream(this.socket.getInputStream());
            this.awaitResponse(dis, 0b000000010010_1_011);
            return SocketHelper.readFloat(dis);
        }
    }

    @Override
    public float getYaw() throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);
        message.add((short) 0b000000010011_0_011);

        synchronized (this.socket) {
            message.send();
            DataInputStream dis = new DataInputStream(this.socket.getInputStream());
            this.awaitResponse(dis, 0b000000010011_1_011);
            return SocketHelper.readFloat(dis);
        }
    }

    @Override
    public Container getInventory() throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);
        message.add((short) 0b000000010100_0_011);

        synchronized (this.socket) {
            message.send();
            DataInputStream dis = new DataInputStream(this.socket.getInputStream());
            this.awaitResponse(dis, 0b000000010100_1_011);
            return (Container) SocketHelper.readObject(dis, Container.class);
        }
    }

    private void awaitResponse(DataInputStream dis, int expectedHeader) throws IOException {
        int response = SocketHelper.readShort(dis);
        while (response != expectedHeader) {
            this.asyncResolver.processAsyncReturn(response, dis);
            response = SocketHelper.readShort(dis);
        }
    }

    @Override
    public int start_recording() throws IOException {
        Message message = new Message(this.socket);

        // start recording header
        message.add((short) 0b000000001111_0_011);

        synchronized (this.socket) {
            message.send();

            // read response
            DataInputStream dis = new DataInputStream(this.socket.getInputStream());
            int r = SocketHelper.readShort(dis);
            while (r != 0b000000001111_1_011) {
                this.asyncResolver.processAsyncReturn(r, dis); // expected return, found async return from another request
                r = SocketHelper.readShort(dis);
            }
            return SocketHelper.readShort(dis);
        }
    }

    @Override
    public void stop_recording(int id, File out_path) throws IOException {
        Message message = new Message(this.socket);

        // stop recording header
        message.add((short) 0b000000010000_0_011);
        message.add((short) id);

        ConfigFile video;
        synchronized (this.socket) {
            message.send();

            // read response
            DataInputStream dis = new DataInputStream(this.socket.getInputStream());
            int r = SocketHelper.readShort(dis);
            while (r != 0b000000010000_1_011) {
                this.asyncResolver.processAsyncReturn(r, dis); // expected return, found async return from another request
                r = SocketHelper.readShort(dis);
            }
            video = (ConfigFile)SocketHelper.readObject(dis, ConfigFile.class);
        }
        video.saveToFile(out_path);
    }

    @Override
    public String getVersion() throws IOException {
        // no need to sync

        Message message = new Message(this.socket);

        // get version header
        message.add((short) 0b111111111111_0_011);

        synchronized (this.socket) {
            message.send();

            // TODO if none got, then it's <0.1.22

            // read response
            DataInputStream dis = new DataInputStream(this.socket.getInputStream());
            int r = SocketHelper.readShort(dis);
            while (r != 0b111111111111_1_011) {
                this.asyncResolver.processAsyncReturn(r, dis); // expected return, found async return from another request
                r = SocketHelper.readShort(dis);
            }
            return SocketHelper.readString(dis);
        }
    }

    @Override
    public void synchronize() throws IOException {
        this.syncManager.requestSynchronization(this);

        Message message = new Message(this.socket);

        // synchronize header
        message.add((short) 0b000000001001_0_011);

        synchronized (this.socket) {
            message.send();

            // read response
            DataInputStream dis = new DataInputStream(this.socket.getInputStream());
            int r = SocketHelper.readShort(dis);
            while (r != 0b000000001001_1_011) {
                this.asyncResolver.processAsyncReturn(r, dis); // expected return, found async return from another request
                r = SocketHelper.readShort(dis);
            }
        }
    }
}
