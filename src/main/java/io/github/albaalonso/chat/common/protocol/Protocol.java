package io.github.albaalonso.chat.common.protocol;

import io.github.albaalonso.chat.common.message.Message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.regex.Pattern;

/**
 * Reglas compartidas por cliente y servidor: mensajes del handshake,
 * límites de tamaño y (de)serialización segura.
 */
public final class Protocol {
    /** Petición del servidor para que el cliente envíe su nickname. */
    public static final String NICKNAME_REQUEST = "Enter your nickname: ";
    /** El nickname se ha aceptado. */
    public static final String NICKNAME_ACCEPTED = "Nickname accepted. Welcome to the chat!";
    /** El nickname ya está en uso. */
    public static final String NICKNAME_TAKEN = "Nickname is already in use. Please choose another one.";
    /** El nickname no cumple el formato. */
    public static final String NICKNAME_INVALID =
            "Invalid nickname: use 1-" + Protocol.MAX_NICKNAME_LENGTH + " letters, numbers, '_' or '-'.";

    /** Longitud máxima de un nickname. */
    public static final int MAX_NICKNAME_LENGTH = 20;
    /** Longitud máxima de un mensaje de chat; los más largos se recortan. */
    public static final int MAX_MESSAGE_LENGTH = 1000;
    /** Tamaño máximo de un datagrama UDP. */
    public static final int MAX_DATAGRAM_SIZE = 65_507;

    private static final Pattern NICKNAME_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}_-]{1," + MAX_NICKNAME_LENGTH + "}");

    /**
     * Lista blanca de clases que se aceptan al deserializar. Cualquier otra
     * clase se rechaza: así nadie puede enviarnos objetos maliciosos.
     */
    public static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=10;maxarray=10000;maxbytes=" + MAX_DATAGRAM_SIZE * 4 + ";"
                    + "java.lang.String;java.lang.Object;java.util.*;"
                    + "io.github.albaalonso.chat.common.message.*;!*");

    private Protocol() {
    }

    /**
     * @param nickname nickname propuesto (puede ser null)
     * @return si el nickname tiene un formato válido
     */
    public static boolean isValidNickname(String nickname) {
        return nickname != null && NICKNAME_PATTERN.matcher(nickname).matches();
    }

    /**
     * @param text texto escrito por el usuario
     * @return el texto recortado a {@link #MAX_MESSAGE_LENGTH} caracteres
     */
    public static String truncate(String text) {
        return text.length() <= MAX_MESSAGE_LENGTH ? text : text.substring(0, MAX_MESSAGE_LENGTH);
    }

    /**
     * Crea un {@link ObjectInputStream} con el filtro de seguridad activado.
     *
     * @param in stream de entrada
     * @return stream que solo acepta las clases del protocolo
     * @throws IOException si falla la lectura de la cabecera
     */
    public static ObjectInputStream secureInput(InputStream in) throws IOException {
        ObjectInputStream ois = new ObjectInputStream(in);
        ois.setObjectInputFilter(FILTER);
        return ois;
    }

    /**
     * Serializa un mensaje para enviarlo en un datagrama.
     *
     * @param message mensaje a enviar
     * @return bytes del mensaje
     * @throws IOException si el mensaje no cabe en un datagrama
     */
    public static byte[] toBytes(Message message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(message);
        }
        if (bytes.size() > MAX_DATAGRAM_SIZE)
            throw new IOException("Mensaje demasiado grande para UDP: " + bytes.size() + " bytes");
        return bytes.toByteArray();
    }

    /**
     * Deserializa un mensaje recibido en un datagrama.
     *
     * @param data   buffer recibido
     * @param length bytes válidos del buffer
     * @return mensaje recibido
     * @throws IOException            si los datos no son un mensaje válido
     * @throws ClassNotFoundException si la clase no existe
     */
    public static Message fromBytes(byte[] data, int length) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = secureInput(new ByteArrayInputStream(data, 0, length))) {
            Object obj = in.readObject();
            if (obj instanceof Message message)
                return message;
            throw new InvalidObjectException("Tipo de mensaje inesperado: " + obj.getClass().getName());
        }
    }
}
