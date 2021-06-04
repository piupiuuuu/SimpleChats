/**
 * Интерфейс авторизации
 */
public interface AuthService {

    // запустить сервис
    void start();

    // отстановить сервис
    void stop();

    // получить ник
    String getNickByLoginPass(String login, String pass);

}

