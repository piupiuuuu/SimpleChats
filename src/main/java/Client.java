import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


public class Client extends JFrame {

    private JTextField inputField; // текстовое поле для ввода сообщений
    private JTextArea chatArea; // текстовое поле для вывода сообщений

    private Socket socket; //соединение с сервером
    private DataInputStream inputStream; //поток ввода
    private DataOutputStream outputStream; //поток вывода
    private DataOutputStream fileOutputStream; // поток для записи в файл
    private DataInputStream fileInputStream;
    private String nameFile;
    private File file;


    public String getNameFile() {
        return nameFile;
    }

    public Client() {
        try {
            openConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
        initGUI();
    }

    //открытие соединения
    public void openConnection() throws IOException {
        socket = new Socket(Constants.HOST, Constants.PORT); //открытие сокета
        inputStream = new DataInputStream(socket.getInputStream()); //доступ к исходящему потоку сервера
        outputStream = new DataOutputStream(socket.getOutputStream()); //доступ к входящему потоку сервера

        //входящие сообщения
        Thread thread = new Thread(() -> {
            try {
                // успешная авторизация
                while (true) {
                    String strFromServer = inputStream.readUTF();
                    chatArea.append(strFromServer + "\n");
                    if (strFromServer.startsWith(Constants.AUTH_OK)) {
                        break;
                    }
                }
                // сообщение nick вошел в чат
                while (true) {
                    String strFromServer = inputStream.readUTF();
                    chatArea.append(strFromServer + "\n");
                    if (strFromServer.endsWith("вошел в чат")) {
                        // создать файл
                        String[] messageFull = strFromServer.split("\\s+");
                        nameFile = "history_" + messageFull[0] + ".txt";
                        file = new File(getNameFile());
                        if (!file.exists()) {
                            file.createNewFile();
                        }
                        // добавить историю в чат
                        chatArea.append("Начало истории чата: \n");
                        chatArea.append(sendHistory());
                        chatArea.append("Конец истории чата.\n");
                        chatArea.append("\n");
                        break;
                    }
                }

                // чтение сообщений
                fileOutputStream = new DataOutputStream(new FileOutputStream(getNameFile(), true));
                saveHistory(LocalDateTime.now().toString());
                while (true) {
                    String strFromServer = inputStream.readUTF();
//                    if(!(strFromServer.endsWith("вошел в чат")) || !(strFromServer.endsWith("из чата")) )  {
//                        saveHistory(strFromServer);
//                    }
                    saveHistory(strFromServer);
                    if (strFromServer.startsWith(Constants.STOP_WORD)) {
                        break;
                    } else {
                        chatArea.append(strFromServer);
                    }
                    chatArea.append("\n");
                }
                fileOutputStream.flush();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
            } catch (Exception e) {
                System.out.println("Fail!");
                Runtime.getRuntime().exit(0);
            }
        });
        thread.start();
    }

    public void saveHistory(String text) throws IOException {
        fileOutputStream.writeUTF(text + "\n");
    }

    public String sendHistory() {
        List<String> list = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        try {
            fileInputStream = new DataInputStream(new FileInputStream(getNameFile()));
            String line;
            while(fileInputStream.available()>0) {
                line = fileInputStream.readUTF();
                list.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // поставить условие, если list.size() >= 100
        if(list.size() >= 100) {
            for(int i = list.size() - 100; i < list.size(); i++) {
                text.append(list.get(i));
            }
        } else {
            for (String s : list) {
                text.append(s);
            }
        }
        return text.toString();
    }

    //закрытие соединения
    public void closeConnection() {
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

    //окно c чатом
    public void initGUI() {
        //параметры окна
        setBounds(600, 300, 500, 500);
        setTitle("Клиент");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        //текстовое поле для вывода сообщений
        chatArea = new JTextArea();
        chatArea.setEditable(false); //запрет на редактирование текста
        chatArea.setLineWrap(true); //перенос строк
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        //нижняя панель: поле для ввода ссобщений + кнопка для отправки сообщений
        JPanel panel = new JPanel(new BorderLayout());
        //кнопка для отправки сообщений
        JButton button = new JButton("Отправить");
        panel.add(button, BorderLayout.EAST);
        button.addActionListener(e -> sendMessage()); //нажатие на кнопку - отправка сообщения из TF в TA
        //текстовое поле для ввода сообщений
        inputField = new JTextField();
        panel.add(inputField, BorderLayout.CENTER);
        inputField.addActionListener(e -> sendMessage()); //нажатие на enter в TF - отправка сообщения из TF в TA
        add(panel, BorderLayout.SOUTH);

        //действие на закрытие окна
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                try {
                    outputStream.writeUTF(Constants.STOP_WORD);
                    closeConnection();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        });

        setVisible(true);
    }

    //исходящие сообщения
    public void sendMessage() {
        if(!inputField.getText().trim().isEmpty()) {
            try {
                outputStream.writeUTF(inputField.getText()); //отправить текст из TF на сервер
                inputField.setText(""); //очистить TF
                inputField.grabFocus(); //установить курсор на TF
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Ошибка отправки сообщения"); //всплывающее окно с текстом ошибки
            }
        }

    }

    public static void main(String[] args) {
        //открыть новый поток, запуская клиента
        SwingUtilities.invokeLater(Client::new);
    }
}

