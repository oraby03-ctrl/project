package com.webapp.client.presentation;

import com.webapp.client.application.ClientGameService;
import com.webapp.client.application.RemoteGameGateway;
import com.webapp.client.model.ClientModel;
import com.webapp.server.presentation.ServerMain;
import com.webapp.shared.remote.GameServerRemote;

public class ClientMain {
    public static void main(String[] args) {
        try {
            RemoteGameGateway gateway = new RemoteGameGateway();
            GameServerRemote remote = gateway.connect("localhost", 1099, ServerMain.BINDING_NAME);

            ClientModel model = new ClientModel();
            ConsoleView view = new ConsoleView();
            ClientGameService service = new ClientGameService(remote);
            ClientController controller = new ClientController(model, view, service);

            controller.run();
        } catch (Exception exception) {
            System.err.println("Client failed: " + exception.getMessage());
            exception.printStackTrace();
        }
    }
}
