import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервер
 */
public class MyServer {
    private List<ClientHandler> clients;
    private AuthService authService;

    public MyServer() {
        try (ServerSocket server = new ServerSocket(Constants.PORT)) {
            authService = new BaseAuthService();
            authService.start();
            clients = new ArrayList<>();

            while (true) {
                System.out.println("Сервер ожидает подключения");
                Socket socket = server.accept();
                System.out.println("Клиент поключился");
                new ClientHandler(this, socket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (authService != null) {
                authService.stop();
            }
        }
    }

    public AuthService getAuthService() {
        return authService;
    }

    public synchronized boolean isNickBusy(String nick) {
        for (ClientHandler client : clients) {
            if (client.getName().equals(nick)) {
                return true;
            }
        }
        return false;
    }

    /**
     * добавление пользователя при успешной авторизации в сервер обмена сообщениями
     */
    public synchronized void subscribe(ClientHandler clientHandler) {
        clients.add(clientHandler);
    }

    /**
     * удаление пользователя из сервера обмена сообщениями
     */
    public synchronized void unsubscribe(ClientHandler clientHandler) {
        clients.remove(clientHandler);
    }

    /**
     * отправка сообщения сервером всем пользователям
     */
    public synchronized void broadcastMessage(String message) {
        for (ClientHandler client : clients) {
            client.sendMessages(message);
        }
    }

    /**
     * отправление сообщения сервером пользователю с ником nicks
     */
    public synchronized void broadcastMessageToClient(String message, String nick) {
        for (ClientHandler client : clients) {
            if (!nick.contains(client.getName())) {
                continue;
            }
            client.sendMessages(message);
        }
    }
}
