package dev.watchwolf.tester;

import java.net.Socket;

public class ExtendedClientSocket extends ClientSocket implements ExtendedClientPetition {
    public ExtendedClientSocket(String username, Socket socket, AsyncPetitionResolver asyncResolver, SynchronizationManager syncManager) {
        super(username, socket, asyncResolver, syncManager);
    }
}
