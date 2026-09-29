package dev.watchwolf.serversmanager;

import dev.watchwolf.core.entities.ServerType;
import dev.watchwolf.core.entities.WorldType;
import dev.watchwolf.core.entities.files.ConfigFile;
import dev.watchwolf.core.entities.files.plugins.Plugin;

import java.io.IOException;

public interface ServerManagerPetition {
    public String getServersManagerVersion() throws IOException;
    public String startServer(ServerStartNotifier onServerStart, ServerErrorNotifier onError, String mcType, String version, Plugin[]plugins, WorldType worldType, String seed, ConfigFile[]maps, ConfigFile[]configFiles) throws IOException;
}
