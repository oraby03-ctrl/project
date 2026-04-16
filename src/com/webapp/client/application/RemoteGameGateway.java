package com.webapp.client.application;

import com.webapp.shared.remote.GameServerRemote;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RemoteGameGateway {
    public GameServerRemote connect(String host, int port, String bindingName) {
        try {
            Registry registry = LocateRegistry.getRegistry(host, port);
            return (GameServerRemote) registry.lookup(bindingName);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to connect to RMI server", exception);
        }
    }
}
