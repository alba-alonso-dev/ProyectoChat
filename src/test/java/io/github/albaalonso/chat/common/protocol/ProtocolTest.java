package io.github.albaalonso.chat.common.protocol;

import io.github.albaalonso.chat.common.message.ChatMsg;
import io.github.albaalonso.chat.common.message.ConnectionData;
import io.github.albaalonso.chat.common.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {

    @ParameterizedTest
    @ValueSource(strings = {"alba", "Alba_99", "josé-luis", "x"})
    void aceptaNicknamesValidos(String nickname) {
        assertTrue(Protocol.isValidNickname(nickname));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "con espacio", "<script>", "abcdefghijklmnopqrstu"})
    void rechazaNicknamesInvalidos(String nickname) {
        assertFalse(Protocol.isValidNickname(nickname));
    }

    @Test
    void recortaMensajesLargos() {
        String largo = "a".repeat(Protocol.MAX_MESSAGE_LENGTH + 50);
        assertEquals(Protocol.MAX_MESSAGE_LENGTH, Protocol.truncate(largo).length());
        assertEquals("hola", Protocol.truncate("hola"));
    }

    @Test
    void serializaYDeserializaChatMsg() throws Exception {
        byte[] data = Protocol.toBytes(new ChatMsg("alba", "hola"));

        Message msg = Protocol.fromBytes(data, data.length);

        assertInstanceOf(ChatMsg.class, msg);
        assertEquals("alba", msg.getNickname());
        assertEquals("hola", msg.getMsg());
    }

    @Test
    void serializaYDeserializaConnectionData() throws Exception {
        byte[] data = Protocol.toBytes(new ConnectionData("alba", "has joined", List.of("alba", "luis")));

        ConnectionData msg = (ConnectionData) Protocol.fromBytes(data, data.length);

        assertEquals(List.of("alba", "luis"), msg.getConnectedUsers());
    }

    /** Clase ajena al protocolo: simula un objeto malicioso enviado por la red. */
    record Intruso(String payload) implements Serializable {
    }

    @Test
    void rechazaClasesQueNoSonDelProtocolo() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new Intruso("rm -rf /"));
        }
        byte[] data = bytes.toByteArray();

        assertThrows(InvalidClassException.class, () -> Protocol.fromBytes(data, data.length));
    }
}
