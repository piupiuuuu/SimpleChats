import java.io.*;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * Обмен сообщениями между клиентами и сервером
 */
public class ClientHandler {

    private MyServer server;
    private Socket socket;
    //private final ScheduledExecutorService scheduledExecutor = new ScheduledThreadPoolExecutor(1);
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private volatile boolean isAuth;
    private String name; // ник пользователя

    public String getName() {
        return name;
    }


    public ClientHandler(MyServer server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;
            this.inputStream = new DataInputStream(socket.getInputStream());
            this.outputStream = new DataOutputStream(socket.getOutputStream());
            this.name = "";
            //socket.setSoTimeout(30000);
            Thread thread = new Thread(() -> {
                try {
                    //scheduledExecutor.schedule(() -> sendMessages("/authGuestNo"), 120, TimeUnit.SECONDS);
                    authentication();
                    readMessages();

                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    closeConnection();
                }
            });
            thread.start();

            // убить через 120 сек, если не авторизовался
            Thread killThread = new Thread(() -> {
                try{
                    TimeUnit.SECONDS.sleep(120);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(!isAuth) {
                    try{
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            killThread.start();

        } catch (IOException e) {
            System.out.println("Проблема при создании клиента");
        }
    }

    /**
     * Авторизация - сообщение в чат ввиде: /auth login pass
     */
    private void authentication() throws IOException {
        while (true) {
            String message = inputStream.readUTF();
            if (message.startsWith(Constants.AUTH_COMMAND)) {
                String[] parts = message.split("\\s+"); // разбить сообщение на массив строк
                String nick = server.getAuthService().getNickByLoginPass(parts[1], parts[2]);
                if (nick != null) {
                    // проверка что такого клиента нет
                    if (!server.isNickBusy(nick)) {
                        name = nick;
                        server.subscribe(this); // добавить пользователя в сервис обмена сообщениями
                        sendMessages(Constants.AUTH_OK);
                        server.broadcastMessage(name + " вошел в чат"); // сообщение всем авторизованным пользователям: ник вошел в чат
                        isAuth = true;
                        return;
                    } else {
                        sendMessages("Ник уже используется");
                    }
                }
            } else sendMessages("Неверный логин или пароль");
        }
    }

    /**
     * Чтение сообщения сервером от клиента
     */
    private void readMessages() throws IOException {
        while (true) {
            String messageFromClient = inputStream.readUTF(); // читаем сообщение от клиента
            System.out.println("от " + name + ": " + messageFromClient); // вывод сообщение от клиента в консоль сервера
            String msg = "[" + name + "]: ";

            // если сообщение /end: ничего не делаем
            if (messageFromClient.equals(Constants.STOP_WORD)) {
                return;
            }

            // если сообщение начинается с /w nick: сервер отправляет сообщение от пользователя пользователю с ником nick
            else if (messageFromClient.startsWith(Constants.MESSAGE_LS)) {
                // получить ник пользователя
                String[] messageList = messageFromClient.split("\\s+");
                String nick = messageList[1];
                // получить сообщение: /w_nick_message
                int lengthNick = nick.length();
                String message = messageFromClient.substring(lengthNick+4);
                String messageFull = msg + message;
                server.broadcastMessageToClient(messageFull, nick); // отправляет сообщение пользователю с ником nick
                server.broadcastMessageToClient(messageFull, this.getName()); // отправление сообщение кто написал
            }

            // если сообщение начинается с /al: сервер отправляет сообщение от клиента всем клиентам
            else if(messageFromClient.startsWith(Constants.MESSAGE_ALL)) {
                String message = messageFromClient.substring(4); // /al_ = 0 1 2 3, c 4 индекса сообщение
                String messageFull = msg + message;
                server.broadcastMessage(messageFull);
            }

            // если сообщение не начинается с команды
            else {
                String messageFull = msg + messageFromClient;
                server.broadcastMessageToClient(messageFull, this.getName());
            }
        }
    }

    public void sendMessages(String message) {
        try {
            outputStream.writeUTF(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        server.unsubscribe(this); // закрываем соединение с сервером обмена сообщениями
        server.broadcastMessage(name + " вышел из чата"); // сообщение всем авторизованным пользователям: ник вышел из чата
        //закрываем все открытые потоки
        try {
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
