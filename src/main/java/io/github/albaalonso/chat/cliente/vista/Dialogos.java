package io.github.albaalonso.chat.cliente.vista;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Diálogos de la aplicación. Se pueden llamar desde cualquier hilo:
 * internamente siempre se muestran en el hilo de Swing (EDT).
 */
public final class Dialogos {

    private Dialogos() {
    }

    /**
     * @param parent  ventana sobre la que mostrar el diálogo (puede ser null)
     * @param mensaje mensaje a mostrar en la ventana de error
     */
    public static void mostrarError(Component parent, String mensaje) {
        onEdt(() -> {
            JOptionPane.showMessageDialog(parent, mensaje, "ERROR", JOptionPane.ERROR_MESSAGE);
            return null;
        });
    }

    /**
     * @param parent ventana sobre la que mostrar el diálogo (puede ser null)
     * @return nickname introducido, o null si el usuario cancela
     */
    public static String pedirNickname(Component parent) {
        return onEdt(() -> JOptionPane.showInputDialog(parent, "Introduce tu nickname:",
                "Conectar al chat", JOptionPane.QUESTION_MESSAGE));
    }

    /**
     * Ejecuta la acción en el EDT y espera su resultado.
     */
    private static <T> T onEdt(Supplier<T> action) {
        if (SwingUtilities.isEventDispatchThread())
            return action.get();

        AtomicReference<T> result = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> result.set(action.get()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
        return result.get();
    }
}
