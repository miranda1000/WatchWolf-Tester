package dev.watchwolf.serversmanager;

import dev.watchwolf.entities.ServerType;
import dev.watchwolf.entities.WorldType;
import dev.watchwolf.entities.files.ConfigFile;
import dev.watchwolf.entities.files.Plugin;

import java.io.IOException;

public interface ServerManagerPetition {
    public String getServersManagerVersion() throws IOException;
    public String startServer(ServerStartNotifier onServerStart, ServerErrorNotifier onError, ServerType mcType, String version, Plugin[]plugins, WorldType worldType, String seed, ConfigFile[]configFiles) throws IOException;
}
