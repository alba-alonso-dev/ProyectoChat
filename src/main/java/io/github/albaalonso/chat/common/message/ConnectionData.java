package io.github.albaalonso.chat.common.message;

import java.io.Serial;
import java.util.List;

/**
 * Aviso de conexión o desconexión de un usuario, con la lista actualizada
 * de usuarios conectados.
 */
public class ConnectionData extends ChatMsg {
    @Serial
    private static final long serialVersionUID = 1L;

    private final List<String> connectedUsers;

    /**
     * @param nickname       nombre de usuario
     * @param msg            el texto del mensaje
     * @param connectedUsers lista de usuarios conectados
     */
    public ConnectionData(String nickname, String msg, List<String> connectedUsers) {
        super(nickname, msg);
        // Copia inmutable: nadie puede modificar la lista después de enviarla
        this.connectedUsers = List.copyOf(connectedUsers);
    }

    /**
     * @return lista de usuarios conectados (no modificable)
     */
    public List<String> getConnectedUsers() {
        return connectedUsers;
    }
}
