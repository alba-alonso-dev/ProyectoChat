package io.github.albaalonso.chat.cliente;

import io.github.albaalonso.chat.cliente.vista.Dialogos;
import io.github.albaalonso.chat.cliente.vista.VChat;
import io.github.albaalonso.chat.common.config.ConfigManager;
import io.github.albaalonso.chat.common.message.ConnectionData;
import io.github.albaalonso.chat.common.protocol.Protocol;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente del chat.
 * <p>
 * La ventana se maneja en el hilo de Swing (EDT) y la red en un hilo aparte,
 * para que la interfaz nunca se quede congelada esperando al servidor.
 */
public class ChatClient {
    private static final Logger LOG = Logger.getLogger(ChatClient.class.getName());

    private final ConfigManager config;
    private final JFrame frame = new JFrame("Chat Client");
    private final VChat vChat = new VChat();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Se escriben en el hilo de red y se leen en el EDT: volatile
    private volatile Socket serverSocket;
    private volatile ObjectOutputStream out;
    private volatile MessageReceiverThread receiverThread;

    /**
     * @param config configuración con los datos del servidor
     */
    public ChatClient(ConfigManager config) {
        this.config = config;
    }

    public static void main(String[] args) {
        ConfigManager config = ConfigManager.getInstance();
        config.configureLogging();
        SwingUtilities.invokeLater(() -> new ChatClient(config).start());
    }

    /**
     * Muestra la ventana y empieza la conexión en segundo plano.
     * Debe llamarse desde el EDT.
     */
    public void start() {
        frame.setContentPane(vChat.getpPrincipal());
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(600, 450);
        frame.setLocationRelativeTo(null);
        URL icon = ChatClient.class.getResource("/" + config.getIconPath());
        if (icon != null)
            frame.setIconImage(new ImageIcon(icon).getImage());

        // Enter en el campo de texto dispara el mismo ActionListener que el botón
        vChat.getEnviarButton().addActionListener(e -> sendFromTextField());
        vChat.getTfMsg().addActionListener(e -> sendFromTextField());
        vChat.getLogOutButton().addActionListener(e -> closeClient());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeClient();
            }
        });
        frame.setVisible(true);

        Thread network = new Thread(this::connect, "chat-connection");
        network.setDaemon(true);
        network.start();
    }

    /**
     * Conexión con el servidor. Se ejecuta en un hilo de red.
     */
    private void connect() {
        String host = config.getServerIP();
        int port = config.getServerPort();
        try {
            serverSocket = new Socket(host, port);
            out = new ObjectOutputStream(serverSocket.getOutputStream());
            out.flush();
            ObjectInputStream in = Protocol.secureInput(serverSocket.getInputStream());

            // Unirse al multicast antes del handshake para no perder mensajes
            receiverThread = new MessageReceiverThread(
                    InetAddress.getByName(config.getBroadcastIP()), config.getBroadcastPort(), vChat);
            receiverThread.start();

            String nickname = negotiateNickname(in);
            if (nickname == null) { // el usuario ha cancelado
                closeClient();
                return;
            }
            SwingUtilities.invokeLater(() -> {
                vChat.setNickname(nickname);
                vChat.setChatEnabled(true);
            });

            // El servidor no envía nada más por TCP: esta lectura solo termina
            // cuando se corta la conexión, y así detectamos la caída del servidor
            while (!closed.get())
                in.readObject();
        } catch (ConnectException e) {
            fail("No se pudo conectar con el servidor " + host + ":" + port, e);
        } catch (IOException | ClassNotFoundException e) {
            fail("Se ha perdido la conexión con el servidor", e);
        }
    }

    /**
     * Pide un nickname al usuario hasta que el servidor lo acepta.
     *
     * @return el nickname aceptado, o null si el usuario cancela
     */
    private String negotiateNickname(ObjectInputStream in) throws IOException, ClassNotFoundException {
        String nickname = null;
        while (true) {
            Object response = in.readObject();
            if (response instanceof ConnectionData data) {
                SwingUtilities.invokeLater(() -> vChat.updateConnectedUsersList(data.getConnectedUsers()));
                return nickname;
            } else if (Protocol.NICKNAME_REQUEST.equals(response)) {
                nickname = Dialogos.pedirNickname(frame);
                if (nickname == null)
                    return null;
                send(nickname.trim());
                nickname = nickname.trim();
            } else if (Protocol.NICKNAME_ACCEPTED.equals(response)) {
                SwingUtilities.invokeLater(() -> vChat.appendToChatArea((String) response));
            } else if (response instanceof String error) {
                Dialogos.mostrarError(frame, error);
            }
        }
    }

    private void fail(String userMessage, Exception e) {
        if (closed.get())
            return; // cierre voluntario: no es un error
        LOG.log(Level.FINE, userMessage, e);
        Dialogos.mostrarError(frame, userMessage);
        closeClient();
    }

    private void sendFromTextField() {
        String message = vChat.getTfMsg().getText();
        if (!message.isBlank()) {
            try {
                send(Protocol.truncate(message));
                vChat.getTfMsg().setText("");
            } catch (IOException e) {
                fail("No se pudo enviar el mensaje", e);
            }
        }
    }

    /**
     * Mandar un objeto al servidor mediante TCP
     */
    private synchronized void send(Object obj) throws IOException {
        out.writeObject(obj);
        out.flush();
    }

    /**
     * Cerrar las conexiones y la ventana. Se puede llamar varias veces
     * y desde cualquier hilo: solo la primera tiene efecto.
     */
    private void closeClient() {
        if (!closed.compareAndSet(false, true))
            return;
        LOG.fine("Cerrando cliente");
        if (receiverThread != null)
            receiverThread.close();
        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Error cerrando el socket", e);
        }
        SwingUtilities.invokeLater(frame::dispose);
    }
}
