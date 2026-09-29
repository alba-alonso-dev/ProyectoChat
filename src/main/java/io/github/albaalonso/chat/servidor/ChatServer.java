package io.github.albaalonso.chat.servidor;

import io.github.albaalonso.chat.common.config.ConfigManager;
import io.github.albaalonso.chat.common.message.ChatMsg;
import io.github.albaalonso.chat.common.message.ConnectionData;
import io.github.albaalonso.chat.common.message.Message;
import io.github.albaalonso.chat.common.protocol.Protocol;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servidor del chat.
 * <p>
 * Recibe por TCP el nickname y los mensajes de cada cliente, y los difunde a
 * todos los clientes mediante un único paquete UDP multicast.
 */
public class ChatServer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ChatServer.class.getName());

    private final int port;
    private final InetAddress group;
    private final int groupPort;

    /** Clientes conectados. Concurrente porque cada cliente tiene su propio hilo. */
    private final Map<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    private ServerSocket serverSocket;
    private MulticastSocket multicastSocket;

    /**
     * @param port      puerto TCP en el que escuchar (0 = cualquiera libre)
     * @param groupIp   IP del grupo multicast
     * @param groupPort puerto del grupo multicast
     * @throws IOException si la IP del grupo no es válida
     */
    public ChatServer(int port, String groupIp, int groupPort) throws IOException {
        this.port = port;
        this.group = InetAddress.getByName(groupIp);
        this.groupPort = groupPort;
    }

    public static void main(String[] args) {
        ConfigManager config = ConfigManager.getInstance();
        config.configureLogging();
        try {
            ChatServer server = new ChatServer(
                    config.getServerPort(), config.getBroadcastIP(), config.getBroadcastPort());
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            server.start();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "No se pudo arrancar el servidor", e);
            System.exit(1);
        }
    }

    /**
     * Abre los sockets y empieza a aceptar clientes en segundo plano.
     *
     * @throws IOException si el puerto está ocupado
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        // El servidor solo envía al grupo, no necesita unirse a él
        multicastSocket = new MulticastSocket();
        multicastSocket.setTimeToLive(1); // no salir de la red local
        pool.execute(this::acceptLoop);
        LOG.info(() -> "Servidor escuchando en el puerto " + getPort());
    }

    /**
     * @return puerto TCP real en el que escucha el servidor
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                pool.execute(new ClientHandler(clientSocket));
            } catch (IOException e) {
                if (!serverSocket.isClosed())
                    LOG.log(Level.WARNING, "Error aceptando un cliente", e);
            }
        }
    }

    /**
     * Cierra el servidor y desconecta a todos los clientes.
     */
    @Override
    public void close() {
        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Error cerrando el servidor", e);
        }
        connectedClients.values().forEach(ClientHandler::closeSocket);
        if (multicastSocket != null)
            multicastSocket.close();
        pool.shutdownNow();
    }

    /**
     * @return lista ordenada de los nicknames conectados
     */
    private List<String> connectedUsers() {
        List<String> users = new ArrayList<>(connectedClients.keySet());
        users.sort(String.CASE_INSENSITIVE_ORDER);
        return users;
    }

    /**
     * Mandar un mensaje mediante multicast socket
     *
     * @param message mensaje para enviar
     */
    private void broadcast(Message message) {
        if (multicastSocket.isClosed())
            return; // el servidor se está apagando
        try {
            byte[] data = Protocol.toBytes(message);
            multicastSocket.send(new DatagramPacket(data, data.length, group, groupPort));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo difundir el mensaje", e);
        }
    }

    /**
     * Hilo que atiende a un cliente
     */
    private class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private String nickname;

        /**
         * @param socket socket del cliente
         */
        ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        /**
         * Confirmar que el nickname está disponible
         * y quedar a la espera de recibir mensajes del cliente que enviar al resto
         */
        @Override
        public void run() {
            try (clientSocket;
                 ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                 ObjectInputStream in = Protocol.secureInput(clientSocket.getInputStream())) {

                nickname = negotiateNickname(out, in);
                send(out, Protocol.NICKNAME_ACCEPTED);
                send(out, new ConnectionData(nickname, "", connectedUsers()));
                broadcast(new ConnectionData(nickname, "has joined the chat.", connectedUsers()));
                LOG.info(() -> nickname + " se ha conectado");

                while (true) {
                    if (in.readObject() instanceof String text && !text.isBlank())
                        broadcast(new ChatMsg(nickname, Protocol.truncate(text)));
                }
            } catch (EOFException | SocketException e) {
                LOG.fine(() -> "Cliente desconectado: " + e.getMessage());
            } catch (IOException | ClassNotFoundException e) {
                LOG.log(Level.WARNING, "Error con el cliente " + nickname, e);
            } finally {
                disconnectClient();
            }
        }

        /**
         * Pide un nickname hasta que el cliente envía uno válido y libre.
         * {@code putIfAbsent} comprueba y reserva el nombre en una sola
         * operación atómica: dos clientes no pueden quedarse con el mismo.
         */
        private String negotiateNickname(ObjectOutputStream out, ObjectInputStream in)
                throws IOException, ClassNotFoundException {
            while (true) {
                send(out, Protocol.NICKNAME_REQUEST);
                Object received = in.readObject();
                String candidate = received instanceof String s ? s.trim() : null;

                if (!Protocol.isValidNickname(candidate))
                    send(out, Protocol.NICKNAME_INVALID);
                else if (connectedClients.putIfAbsent(candidate, this) != null)
                    send(out, Protocol.NICKNAME_TAKEN);
                else
                    return candidate;
            }
        }

        private void send(ObjectOutputStream out, Object obj) throws IOException {
            out.writeObject(obj);
            out.flush();
        }

        private void closeSocket() {
            try {
                clientSocket.close();
            } catch (IOException e) {
                LOG.log(Level.FINE, "Error cerrando socket", e);
            }
        }

        /**
         * Desconectar un cliente
         */
        private void disconnectClient() {
            if (nickname != null && connectedClients.remove(nickname, this)) {
                LOG.info(() -> nickname + " ha salido del chat");
                broadcast(new ConnectionData(nickname, "has left the chat.", connectedUsers()));
            }
        }
    }
}
