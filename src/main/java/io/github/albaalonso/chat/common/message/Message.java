package io.github.albaalonso.chat.common.message;

import java.io.Serializable;

/**
 * Mensaje que el servidor difunde a los clientes por multicast.
 */
public interface Message extends Serializable {
    /**
     * @return texto del mensaje
     */
    String getMsg();

    /**
     * @return nickname del usuario que genera el mensaje
     */
    String getNickname();
}
