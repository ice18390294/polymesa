package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Κύριος Streaming Server.
 * Ακούει για συνδέσεις clients και δημιουργεί έναν ClientHandler
 * (σε ξεχωριστό thread) για κάθε νέα σύνδεση.
 */
public class StreamingServer {

    private static final Logger logger = LoggerFactory.getLogger(StreamingServer.class);

    private final int           port;
    private final VideoLibrary  videoLibrary;
    private final ServerGUI     gui;

    private ServerSocket    serverSocket;
    private boolean         running = false;

    // Thread pool: εξυπηρέτηση πολλαπλών clients ταυτόχρονα
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public StreamingServer(int port, VideoLibrary library, ServerGUI gui) {
        this.port        = port;
        this.videoLibrary = library;
        this.gui         = gui;
    }

    // ---------------------------------------------------------------
    // Εκκίνηση / Διακοπή
    // ---------------------------------------------------------------

    /** Ξεκινά τον server σε background thread ώστε να μην παγώνει το GUI. */
    public void start() {
        if (running) {
            logger.warn("Ο server ήδη τρέχει.");
            return;
        }

        Thread t = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                running = true;
                logger.info("Server ξεκίνησε – θύρα {}", port);
                gui.log("Server ξεκίνησε – θύρα " + port);
                gui.setStatus("Τρέχει (Θύρα: " + port + ")");

                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        // Κάθε client σε δικό του thread από το pool
                        threadPool.execute(new ClientHandler(clientSocket, videoLibrary, gui));
                    } catch (IOException e) {
                        if (running) {
                            logger.error("Σφάλμα αποδοχής σύνδεσης: {}", e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Σφάλμα εκκίνησης server: {}", e.getMessage());
                gui.log("Σφάλμα: " + e.getMessage());
                gui.setStatus("Σφάλμα εκκίνησης");
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /** Σταματά τον server και το thread pool. */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException e) {
            logger.error("Σφάλμα κλεισίματος server socket: {}", e.getMessage());
        }
        threadPool.shutdownNow();
        logger.info("Server σταμάτησε.");
        gui.log("Server σταμάτησε.");
        gui.setStatus("Σταματημένος");
    }

    public boolean isRunning() { return running; }
}
