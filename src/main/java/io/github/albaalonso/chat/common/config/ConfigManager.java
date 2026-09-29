package io.github.albaalonso.chat.common.config;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuración de la aplicación leída de {@code config.xml}.
 * <p>
 * Orden de búsqueda del fichero:
 * <ol>
 *     <li>{@code config.xml} en el directorio de trabajo (permite cambiarlo sin recompilar).</li>
 *     <li>{@code config.xml} dentro del classpath ({@code src/main/resources}).</li>
 * </ol>
 * Cualquier valor puede sobrescribirse al arrancar con {@code -Dchat.<tag>=valor},
 * por ejemplo {@code -Dchat.server_ip=192.168.1.10}.
 */
public final class ConfigManager {
    private static final String FILE_NAME = "config.xml";
    private static final String OVERRIDE_PREFIX = "chat.";

    private final Document document;

    /**
     * Idiom "holder": la JVM garantiza que la instancia se crea una sola vez
     * y de forma segura entre hilos, sin necesidad de {@code synchronized}.
     */
    private static final class Holder {
        private static final ConfigManager INSTANCE = loadDefault();
    }

    ConfigManager(InputStream xml) {
        this.document = parse(xml);
    }

    /**
     * @return instancia única del manager
     */
    public static ConfigManager getInstance() {
        return Holder.INSTANCE;
    }

    private static ConfigManager loadDefault() {
        Path external = Path.of(FILE_NAME);
        try (InputStream in = Files.exists(external)
                ? Files.newInputStream(external)
                : ConfigManager.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in == null)
                throw new IllegalStateException("No se encuentra " + FILE_NAME);
            return new ConfigManager(in);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + FILE_NAME, e);
        }
    }

    private static Document parse(InputStream xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Evita ataques XXE: el fichero de configuración no necesita DTDs
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            return factory.newDocumentBuilder().parse(xml);
        } catch (Exception e) {
            throw new IllegalStateException("config.xml no es válido", e);
        }
    }

    /**
     * @param tagName nombre del tag que se encuentra en el xml config
     * @return valor del tag, o de la propiedad {@code chat.<tagName>} si se ha definido
     * @throws IllegalStateException si el tag no existe
     */
    public String getPropertyValue(String tagName) {
        String override = System.getProperty(OVERRIDE_PREFIX + tagName);
        if (override != null)
            return override.trim();

        Node node = document.getElementsByTagName(tagName).item(0);
        if (node == null)
            throw new IllegalStateException("Falta <" + tagName + "> en " + FILE_NAME);
        return node.getTextContent().trim();
    }

    private int getIntProperty(String tagName) {
        String value = getPropertyValue(tagName);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("<" + tagName + "> debe ser un número: " + value, e);
        }
    }

    /**
     * @return ruta del icono dentro del classpath
     */
    public String getIconPath() {
        return getPropertyValue("icon_path");
    }

    /**
     * @return IP del servidor
     */
    public String getServerIP() {
        return getPropertyValue("server_ip");
    }

    /**
     * @return puerto TCP del servidor
     */
    public int getServerPort() {
        return getIntProperty("server_port");
    }

    /**
     * @return IP del grupo multicast
     */
    public String getBroadcastIP() {
        return getPropertyValue("broadcast_ip");
    }

    /**
     * @return puerto UDP del grupo multicast
     */
    public int getBroadcastPort() {
        return getIntProperty("broadcast_port");
    }

    /**
     * @return si está activado el modo debug
     */
    public boolean isDebug() {
        return Boolean.parseBoolean(getPropertyValue("debug"));
    }

    /**
     * Configura el nivel de log de la aplicación: con debug se muestran
     * también los mensajes {@link Level#FINE}.
     */
    public void configureLogging() {
        Level level = isDebug() ? Level.FINE : Level.INFO;
        Logger appLogger = Logger.getLogger("io.github.albaalonso.chat");
        appLogger.setLevel(level);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(level);
        appLogger.addHandler(handler);
        appLogger.setUseParentHandlers(false);
    }
}
