# ProyectoChat 💬

Chat de escritorio multiusuario en **Java** que combina **sockets TCP** (cliente → servidor) con **UDP multicast** (servidor → clientes). Hecho como proyecto de la asignatura *Programación de Servicios y Procesos* (DAM).

> Objetivo: practicar programación en red a bajo nivel (sockets, hilos, serialización y multicast) sin usar frameworks.

<!-- Añade aquí una captura o un GIF del chat funcionando con 2-3 clientes -->
<!-- ![Demo](docs/demo.gif) -->

---

## ✨ Funcionalidades

- Conexión de varios clientes a un servidor central.
- Elección de *nickname* único, validado por el servidor.
- Mensajes en tiempo real para todos los usuarios conectados.
- Lista de usuarios conectados que se actualiza al entrar o salir alguien.
- Avisos de entrada y salida (`X has joined the chat`).
- Configuración externa (IP, puertos, grupo multicast) en `config.xml`.
- Interfaz gráfica con Java Swing.

## 🏗️ Arquitectura

```
        ┌──────────────┐   TCP (nickname, mensajes)   ┌──────────────────────┐
        │  Cliente A   │ ───────────────────────────▶ │                      │
        └──────────────┘                              │     ChatServer       │
        ┌──────────────┐   TCP                        │  (un hilo por        │
        │  Cliente B   │ ───────────────────────────▶ │   cliente)           │
        └──────────────┘                              └──────────┬───────────┘
               ▲  ▲                                              │
               │  └──────────── UDP multicast 239.0.0.1:8888 ────┘
               └─────────────── (mensajes + lista de usuarios)
```

1. El cliente abre una conexión **TCP** con el servidor y negocia su nickname.
2. Cada mensaje que escribe el usuario viaja por TCP al servidor.
3. El servidor lo reenvía una sola vez al **grupo multicast**; todos los clientes suscritos lo reciben.
4. Los cambios de conexión (`ConnectionData`) llevan la lista actualizada de usuarios.

**¿Por qué TCP + multicast?** TCP garantiza que el servidor recibe cada mensaje y permite controlar quién está conectado. Multicast permite que el servidor envíe **un único paquete** para todos, en lugar de uno por cliente. El coste: UDP no garantiza entrega ni orden, y el multicast solo funciona dentro de una red local.

## 📁 Estructura

```
src/
├── Servidor/
│   └── ChatServer.java            # Acepta conexiones TCP y difunde por multicast
├── Cliente/
│   ├── ChatClient.java            # Conexión TCP, envío de mensajes y eventos de la UI
│   ├── MessageReceiverThread.java # Hilo que escucha el grupo multicast
│   └── Vista/VChat.java (+ .form) # Ventana Swing (IntelliJ GUI Designer)
└── Common/
    ├── Message/                   # Modelos serializables: ChatMsg, ConnectionData, PrivateMsg
    ├── Mangers/ConfigManager.java # Lectura de config.xml (singleton)
    └── Validar/Validaciones.java  # Utilidades de validación y diálogos de error
config.xml                         # Configuración de red
resources/img/icon.png             # Icono de la ventana
JavaDoc/                           # Documentación generada
```

## 🚀 Cómo ejecutarlo

### Requisitos

- JDK 17 o superior (desarrollado con OpenJDK 20).
- **IntelliJ IDEA**: la ventana `VChat` está diseñada con el *GUI Designer* de IntelliJ, que genera el código de los componentes al compilar. Si se compila con `javac` a secas, la interfaz no se inicializa.
- Todos los clientes y el servidor en la **misma red local** (requisito del multicast).

### Pasos

1. Clonar el repositorio y abrirlo en IntelliJ IDEA.
2. Revisar `config.xml`:

   ```xml
   <config>
       <icon_path>resources/img/icon.png</icon_path>
       <server_ip>127.0.0.1</server_ip>      <!-- IP del servidor -->
       <server_port>12345</server_port>      <!-- Puerto TCP -->
       <broadcast_ip>239.0.0.1</broadcast_ip><!-- Grupo multicast -->
       <broadcast_port>8888</broadcast_port> <!-- Puerto UDP -->
       <debug>false</debug>                  <!-- Trazas por consola -->
   </config>
   ```

3. Ejecutar `Servidor.ChatServer`.
4. Ejecutar `Cliente.ChatClient` una vez por cada usuario (en IntelliJ: *Run configuration → Allow multiple instances*).
5. Introducir un nickname y empezar a chatear.

> El directorio de trabajo debe ser la raíz del proyecto, porque `config.xml` se lee con una ruta relativa.

## 📚 Documentación

- [Manual de usuario](Manual%20de%20Usuario.pdf)
- [Método de transmisión](Metodo%20de%20trasmision.pdf)
- JavaDoc: abrir `JavaDoc/index.html` en el navegador.

## ⚠️ Limitaciones conocidas

Proyecto académico; no está pensado para producción:

- El multicast solo funciona en LAN (no atraviesa Internet ni la mayoría de redes wifi públicas).
- Los mensajes viajan **sin cifrar** y cualquier equipo de la red puede unirse al grupo multicast y leerlos.
- Se usa serialización nativa de Java (`ObjectInputStream`), que no es segura con datos no confiables.
- Los paquetes UDP de más de 1024 bytes (mensajes largos o muchas personas conectadas) se truncan.
- Los mensajes privados (`PrivateMsg`) están modelados pero aún no implementados.

## 🗺️ Próximos pasos

- [ ] Migrar a Maven/Gradle y sustituir el `.form` por Swing escrito a mano para compilar fuera de IntelliJ.
- [ ] Hacer thread-safe el registro de clientes (`ConcurrentHashMap` + `putIfAbsent`).
- [ ] Protocolo propio en JSON en lugar de serialización Java.
- [ ] Implementar mensajes privados.
- [ ] Tests unitarios (JUnit 5) y CI con GitHub Actions.
- [ ] Versión web: backend con WebSockets (Spring Boot) y frontend en navegador.

## 🛠️ Tecnologías

Java · Swing · Sockets TCP · UDP Multicast · Hilos · Serialización · XML (DOM)

## 👤 Autoría

**Alba Alonso** — [GitHub](https://github.com/alba-alonso-dev)
