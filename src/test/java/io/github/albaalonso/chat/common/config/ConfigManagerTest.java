package io.github.albaalonso.chat.common.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
    private static final String XML = """
            <config>
                <server_ip>10.0.0.5</server_ip>
                <server_port>4000</server_port>
                <debug>true</debug>
            </config>
            """;

    private static ConfigManager load(String xml) {
        return new ConfigManager(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @AfterEach
    void limpiarPropiedades() {
        System.clearProperty("chat.server_ip");
    }

    @Test
    void leeLosValoresDelXml() {
        ConfigManager config = load(XML);

        assertEquals("10.0.0.5", config.getServerIP());
        assertEquals(4000, config.getServerPort());
        assertTrue(config.isDebug());
    }

    @Test
    void unaPropiedadDelSistemaSobrescribeElXml() {
        System.setProperty("chat.server_ip", "192.168.1.20");

        assertEquals("192.168.1.20", load(XML).getServerIP());
    }

    @Test
    void fallaConUnMensajeClaroSiFaltaUnTag() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> load(XML).getBroadcastIP());
        assertTrue(e.getMessage().contains("broadcast_ip"));
    }

    @Test
    void fallaSiElPuertoNoEsUnNumero() {
        ConfigManager config = load("<config><server_port>abc</server_port></config>");
        assertThrows(IllegalStateException.class, config::getServerPort);
    }

    @Test
    void elConfigPorDefectoDelClasspathEsValido() {
        ConfigManager config = ConfigManager.getInstance();
        assertEquals(12345, config.getServerPort());
        assertEquals(8888, config.getBroadcastPort());
    }
}
