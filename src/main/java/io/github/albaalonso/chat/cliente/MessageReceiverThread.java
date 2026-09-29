package io.github.albaalonso.chat.cliente;

import io.github.albaalonso.chat.cliente.vista.VChat;
import io.github.albaalonso.chat.common.message.ConnectionData;
import io.github.albaalonso.chat.common.message.Message;
import io.github.albaalonso.chat.common.protocol.Protocol;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hilo que recibe los mensajes del grupo multicast y actualiza la ventana.
 */
public class MessageReceiverThread extends Thread {
    private static final Logger LOG = Logger.getLogger(MessageReceiverThread.class.getName());

    private final MulticastSocket multicastSocket;
    private final InetAddress group;
    private final VChat vChat;
    /** volatile: lo escribe otro hilo (el de Swing) al cerrar. */
    private volatile boolean receive = true;

    /**
     * Se une al grupo multicast indicado.
     *
     * @param group grupo del multicast
     * @param port  puerto del multicast
     * @param vChat ventana con el entorno gráfico del chat
     * @throws IOException si no se puede unir al grupo
     */
    @SuppressWarnings("deprecation") // joinGroup(InetAddress) elige la interfaz por defecto en todos los SO
    public MessageReceiverThread(InetAddress group, int port, VChat vChat) throws IOException {
        super("multicast-receiver");
        setDaemon(true);
        this.group = group;
        this.vChat = vChat;
        this.multicastSocket = new MulticastSocket(port);
        multicastSocket.joinGroup(group);
    }

    /**
     * Recibir mensajes mediante el multicast socket
     * y actualizar la ventana: área de chat y lista de usuarios conectados
     */
    @Override
    public void run() {
        byte[] buffer = new byte[Protocol.MAX_DATAGRAM_SIZE];
        while (receive) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastSocket.receive(packet);
                Message message = Protocol.fromBytes(packet.getData(), packet.getLength());
                SwingUtilities.invokeLater(() -> show(message));
            } catch (ClassNotFoundException | IOException e) {
                if (!receive || multicastSocket.isClosed())
                    break; // el socket se ha cerrado
                // Un paquete corrupto o ajeno no debe tumbar el chat: se ignora
                LOG.log(Level.FINE, "Paquete multicast ignorado", e);
            }
        }
    }

    private void show(Message message) {
        if (message instanceof ConnectionData data) {
            vChat.updateConnectedUsersList(data.getConnectedUsers());
            vChat.appendToChatArea(data.getNickname() + " " + data.getMsg());
        } else {
            vChat.appendToChatArea(message.getNickname(), message.getMsg());
        }
    }

    /**
     * Para el hilo y cierra la conexión multicast. Cerrar el socket
     * desbloquea {@code receive()}, que de otro modo esperaría indefinidamente.
     */
    @SuppressWarnings("deprecation")
    public void close() {
        receive = false;
        try {
            multicastSocket.leaveGroup(group);
        } catch (IOException e) {
            LOG.log(Level.FINE, "Error al salir del grupo multicast", e);
        }
        multicastSocket.close();
    }
}
