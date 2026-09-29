package io.github.albaalonso.chat.common.message;

import java.io.Serial;

/**
 * Mensaje de chat escrito por un usuario. Es inmutable: se comparte entre hilos
 * y viaja por la red, así que no debe cambiar después de crearse.
 */
public class ChatMsg implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String nickname;
    private final String msg;

    /**
     * @param nickname nombre de usuario
     * @param msg      el texto del mensaje
     */
    public ChatMsg(String nickname, String msg) {
        this.nickname = nickname;
        this.msg = msg;
    }

    @Override
    public String getMsg() {
        return msg;
    }

    @Override
    public String getNickname() {
        return nickname;
    }
}
