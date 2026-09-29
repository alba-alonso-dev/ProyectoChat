package io.github.albaalonso.chat.servidor;

import io.github.albaalonso.chat.common.message.ConnectionData;
import io.github.albaalonso.chat.common.protocol.Protocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del handshake TCP del servidor usando clientes de prueba.
 */
@Timeout(10)
class ChatServerTest {
    private ChatServer server;

    @BeforeEach
    void arrancarServidor() throws IOException {
        server = new ChatServer(0, "239.0.0.1", 8888); // puerto 0: uno libre cualquiera
        server.start();
    }

    @AfterEach
    void pararServidor() {
        server.close();
    }

    /** Cliente mínimo que habla el protocolo por TCP. */
    private class TestClient implements AutoCloseable {
        final Socket socket = new Socket("localhost", server.getPort());
        final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        final ObjectInputStream in = Protocol.secureInput(socket.getInputStream());

        TestClient() throws IOException {
        }

        Object read() throws Exception {
            return in.readObject();
        }

        void send(Object obj) throws IOException {
            out.writeObject(obj);
            out.flush();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @Test
    void aceptaUnNicknameLibreYDevuelveLaListaDeUsuarios() throws Exception {
        try (TestClient client = new TestClient()) {
            assertEquals(Protocol.NICKNAME_REQUEST, client.read());
            client.send("alba");

            assertEquals(Protocol.NICKNAME_ACCEPTED, client.read());
            ConnectionData data = (ConnectionData) client.read();
            assertEquals(List.of("alba"), data.getConnectedUsers());
        }
    }

    @Test
    void rechazaUnNicknameInvalidoYVuelveAPedirlo() throws Exception {
        try (TestClient client = new TestClient()) {
            client.read();
            client.send("   ");

            assertEquals(Protocol.NICKNAME_INVALID, client.read());
            assertEquals(Protocol.NICKNAME_REQUEST, client.read());
        }
    }

    @Test
    void rechazaUnNicknameRepetido() throws Exception {
        try (TestClient primero = new TestClient(); TestClient segundo = new TestClient()) {
            primero.read();
            primero.send("alba");
            assertEquals(Protocol.NICKNAME_ACCEPTED, primero.read());

            segundo.read();
            segundo.send("alba");
            assertEquals(Protocol.NICKNAME_TAKEN, segundo.read());
        }
    }

    @Test
    void liberaElNicknameCuandoElClienteSeDesconecta() throws Exception {
        try (TestClient client = new TestClient()) {
            client.read();
            client.send("alba");
            assertEquals(Protocol.NICKNAME_ACCEPTED, client.read());
        }

        // El servidor tarda un instante en detectar la desconexión
        Object respuesta;
        do {
            try (TestClient otro = new TestClient()) {
                otro.read();
                otro.send("alba");
                respuesta = otro.read();
            }
        } while (Protocol.NICKNAME_TAKEN.equals(respuesta));
        assertEquals(Protocol.NICKNAME_ACCEPTED, respuesta);
    }

    /**
     * Antes (HashMap + comprobar y luego insertar) dos clientes simultáneos
     * podían quedarse con el mismo nickname. Con putIfAbsent solo gana uno.
     */
    @Test
    void soloUnClienteConsigueElNicknameAunqueLleguenALaVez() throws Exception {
        int clientes = 20;
        ExecutorService pool = Executors.newFixedThreadPool(clientes);
        CountDownLatch salida = new CountDownLatch(1);
        List<TestClient> conexiones = new ArrayList<>();
        List<Future<Object>> respuestas = new ArrayList<>();
        try {
            for (int i = 0; i < clientes; i++) {
                TestClient client = new TestClient();
                conexiones.add(client);
                client.read(); // NICKNAME_REQUEST
                respuestas.add(pool.submit((Callable<Object>) () -> {
                    salida.await();
                    client.send("alba");
                    return client.read();
                }));
            }
            salida.countDown(); // todos envían a la vez

            long aceptados = 0;
            for (Future<Object> r : respuestas)
                if (Protocol.NICKNAME_ACCEPTED.equals(r.get()))
                    aceptados++;
            assertEquals(1, aceptados);
        } finally {
            pool.shutdownNow();
            for (TestClient c : conexiones)
                c.close();
        }
    }
}
