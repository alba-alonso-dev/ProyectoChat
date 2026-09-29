package io.github.albaalonso.chat.cliente.vista;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;

/**
 * Ventana del chat. Solo contiene componentes gráficos: la lógica de red
 * está en {@link io.github.albaalonso.chat.cliente.ChatClient}.
 * <p>
 * Todos sus métodos deben llamarse desde el hilo de Swing (EDT).
 */
public class VChat {
    private static final Color FONDO = new Color(0xE9FCFC);
    private static final Color TEXTO = new Color(0x4B6276);
    private static final Color SALIR = new Color(0xBB5750);
    private static final Font FUENTE_BOTON = new Font(Font.SANS_SERIF, Font.BOLD, 14);

    private final JPanel pPrincipal = new JPanel(new BorderLayout(10, 10));
    private final JButton enviarButton = new JButton("Enviar");
    private final JButton logOutButton = new JButton("Exit");
    private final JTextField tfMsg = new JTextField();
    private final JTextArea chatArea = new JTextArea();
    private final JTable userList = new JTable();
    private final JLabel lNickname = new JLabel();

    /**
     * Constructor de la ventana que contiene el chat
     */
    public VChat() {
        pPrincipal.setBackground(FONDO);
        pPrincipal.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        pPrincipal.add(crearCabecera(), BorderLayout.NORTH);
        pPrincipal.add(crearZonaChat(), BorderLayout.CENTER);
        pPrincipal.add(crearListaUsuarios(), BorderLayout.EAST);

        updateConnectedUsersList(List.of());
        setChatEnabled(false);
    }

    private JPanel crearCabecera() {
        JLabel titulo = new JLabel("CHAT");
        titulo.setForeground(TEXTO);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));

        estilizarBoton(logOutButton, SALIR);

        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setBackground(FONDO);
        cabecera.add(titulo, BorderLayout.WEST);
        cabecera.add(logOutButton, BorderLayout.EAST);
        return cabecera;
    }

    private JPanel crearZonaChat() {
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        JTabbedPane pTabs = new JTabbedPane();
        pTabs.setForeground(TEXTO);
        pTabs.addTab("Principal", new JScrollPane(chatArea));

        lNickname.setForeground(TEXTO);
        estilizarBoton(enviarButton, TEXTO);

        JPanel pEnvio = new JPanel(new BorderLayout(10, 0));
        pEnvio.setBackground(FONDO);
        pEnvio.add(lNickname, BorderLayout.WEST);
        pEnvio.add(tfMsg, BorderLayout.CENTER);
        pEnvio.add(enviarButton, BorderLayout.EAST);

        JPanel pData = new JPanel(new BorderLayout(0, 10));
        pData.setBackground(FONDO);
        pData.add(pTabs, BorderLayout.CENTER);
        pData.add(pEnvio, BorderLayout.SOUTH);
        return pData;
    }

    private JScrollPane crearListaUsuarios() {
        JScrollPane scroll = new JScrollPane(userList);
        scroll.setPreferredSize(new Dimension(150, 0));
        return scroll;
    }

    private static void estilizarBoton(JButton boton, Color fondo) {
        boton.setBackground(fondo);
        boton.setForeground(FONDO);
        boton.setFont(FUENTE_BOTON);
    }

    /**
     * @return pPrincipal panel principal del chat
     */
    public JPanel getpPrincipal() {
        return pPrincipal;
    }

    /**
     * Añadir un mensaje de un usuario al área de chat
     *
     * @param nickname usuario que envía el mensaje
     * @param message  mensaje enviado
     */
    public void appendToChatArea(String nickname, String message) {
        appendToChatArea(nickname + ": " + message);
    }

    /**
     * Añadir una línea al área de chat y desplazarse al final
     *
     * @param message mensaje enviado
     */
    public void appendToChatArea(String message) {
        chatArea.append(message + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    /**
     * Actualizar la lista de usuarios conectados
     *
     * @param connectedUsers lista de usuarios conectados
     */
    public void updateConnectedUsersList(List<String> connectedUsers) {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Usuarios"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (String user : connectedUsers)
            model.addRow(new Object[]{user});
        userList.setModel(model);
    }

    /**
     * Activa o desactiva el envío de mensajes (desactivado hasta conectarse)
     *
     * @param enabled si se pueden enviar mensajes
     */
    public void setChatEnabled(boolean enabled) {
        tfMsg.setEnabled(enabled);
        enviarButton.setEnabled(enabled);
        if (enabled)
            tfMsg.requestFocusInWindow();
    }

    /**
     * @return enviarButton botón de enviar
     */
    public JButton getEnviarButton() {
        return enviarButton;
    }

    /**
     * @return logOutButton botón de cerrar sesión
     */
    public JButton getLogOutButton() {
        return logOutButton;
    }

    /**
     * @return tfMsg campo de texto donde se escribe el mensaje
     */
    public JTextField getTfMsg() {
        return tfMsg;
    }

    /**
     * @param nickname nombre de usuario para mostrar en pantalla
     */
    public void setNickname(String nickname) {
        lNickname.setText(nickname);
    }
}
