package server;

import common.VideoInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Χειρίζεται τη σύνδεση με έναν client.
 * Κάθε client εκτελείται σε ξεχωριστό thread (μέσω ExecutorService).
 *
 * Πρωτόκολλο επικοινωνίας (text, line-by-line):
 *  S→C  WELCOME
 *  C→S  SPEED=2.1 FORMAT=mkv
 *  S→C  LIST_START
 *  S→C  <fileName>  (ένα ανά γραμμή)
 *  S→C  LIST_END
 *  C→S  SELECT <fileName> <protocol>     (π.χ.  SELECT Forrest_Gump-480p.mkv UDP)
 *  S→C  READY <port>
 *  C→S  PLAYING   (client ξεκίνησε player – μόνο για UDP/RTP)
 *  C→S  BYE
 */
public class ClientHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    // Κάθε client παίρνει μοναδική streaming θύρα
    private static final AtomicInteger portCounter = new AtomicInteger(9100);

    private final Socket       socket;
    private final VideoLibrary videoLibrary;
    private final ServerGUI    gui;
    private final Statistics   statistics;

    private BufferedReader reader;
    private PrintWriter    writer;

    public ClientHandler(Socket socket, VideoLibrary library, ServerGUI gui) {
        this.socket      = socket;
        this.videoLibrary = library;
        this.gui         = gui;
        this.statistics  = new Statistics(socket.getInetAddress().getHostAddress());
    }

    @Override
    public void run() {
        String ip = socket.getInetAddress().getHostAddress();
        logger.info("Client συνδέθηκε: {}", ip);
        gui.log("Client συνδέθηκε: " + ip);
        gui.incrementClients();

        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            send("WELCOME");

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                logger.debug("Λήφθηκε από {}: {}", ip, line);

                if (line.startsWith("SPEED=")) {
                    handleSpeedFormat(line);
                } else if (line.startsWith("SELECT ")) {
                    handleSelect(line, ip);
                } else if (line.equals("BYE")) {
                    logger.info("Client {} έκλεισε τη σύνδεση.", ip);
                    break;
                }
            }
        } catch (IOException e) {
            logger.warn("Σύνδεση με {} διακόπηκε: {}", ip, e.getMessage());
        } finally {
            statistics.finish();
            gui.decrementClients();
            gui.log("Client αποσυνδέθηκε: " + ip);
            closeQuietly();
        }
    }

    // ---------------------------------------------------------------
    // Χειρισμός εντολών
    // ---------------------------------------------------------------

    /**
     * Επεξεργάζεται την εντολή ταχύτητας/format και στέλνει τη λίστα βίντεο.
     * Μορφή: "SPEED=2.1 FORMAT=mkv"
     */
    private void handleSpeedFormat(String line) {
        double speed  = 4.0;
        String format = "mp4";

        for (String token : line.split(" ")) {
            if (token.startsWith("SPEED=")) {
                try { speed = Double.parseDouble(token.substring(6)); }
                catch (NumberFormatException ignore) {}
            } else if (token.startsWith("FORMAT=")) {
                format = token.substring(7).toLowerCase();
            }
        }

        logger.info("Client – ταχύτητα: {} Mbps, format: {}", speed, format);
        List<VideoInfo> videos = videoLibrary.getFilteredList(speed, format);

        send("LIST_START");
        for (VideoInfo v : videos) send(v.fileName);
        send("LIST_END");

        gui.log(String.format("Στάλθηκε λίστα %d βίντεο (%.1f Mbps, %s)",
                              videos.size(), speed, format));
    }

    /**
     * Επεξεργάζεται επιλογή αρχείου και ξεκινά streaming.
     * Μορφή: "SELECT Forrest_Gump-480p.mkv UDP"
     */
    private void handleSelect(String line, String clientIp) {
        String[] parts = line.split(" ");
        if (parts.length < 3) {
            send("ERROR Λανθασμένη εντολή SELECT");
            return;
        }

        String fileName = parts[1];
        String protocol = parts[2].toUpperCase();

        String filePath = videoLibrary.getFilePath(fileName);
        if (filePath == null) {
            send("ERROR Αρχείο δεν βρέθηκε: " + fileName);
            return;
        }

        String resolution = extractResolution(fileName);
        statistics.setStreamInfo(fileName, protocol, resolution);

        // Αν επιλέχθηκε AUTO, διαλέγουμε protocol βάσει ανάλυσης
        if (protocol.equals("AUTO")) {
            protocol = autoSelectProtocol(resolution);
            logger.info("Auto-select protocol: {}", protocol);
        }

        int streamPort = portCounter.getAndIncrement();
        logger.info("Streaming: {} | {} | θύρα {}", fileName, protocol, streamPort);
        gui.log("Streaming: " + fileName + " (" + protocol + ") → " + clientIp);

        FFmpegHelper ffmpeg = new FFmpegHelper();

        try {
            switch (protocol) {
                case "TCP":
                    // Server ξεκινά σε listen mode, μετά στέλνει READY
                    startTcpStreaming(ffmpeg, filePath, streamPort);
                    break;

                case "UDP":
                    // Στέλνουμε READY, περιμένουμε PLAYING, μετά στέλνουμε
                    send("READY " + streamPort);
                    waitForPlaying();
                    ffmpeg.startUdpServer(filePath, clientIp, streamPort);
                    break;

                case "RTP/UDP":
                case "RTP":
                    send("READY " + streamPort);
                    waitForPlaying();
                    ffmpeg.startRtpServer(filePath, clientIp, streamPort);
                    break;

                default:
                    send("ERROR Άγνωστο protocol: " + protocol);
            }
        } catch (IOException e) {
            logger.error("Σφάλμα streaming: {}", e.getMessage());
            send("ERROR " + e.getMessage());
        }
    }

    /**
     * Για TCP: ξεκινά FFmpeg σε background thread (listen mode), μετά στέλνει READY.
     */
    private void startTcpStreaming(FFmpegHelper ffmpeg, String filePath, int port) {
        Thread t = new Thread(() -> {
            try {
                ffmpeg.startTcpServer(filePath, port);
            } catch (IOException e) {
                logger.error("TCP streaming error: {}", e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();

        // Μικρή αναμονή ώστε το ffmpeg να ξεκινήσει πριν ο client συνδεθεί
        try { Thread.sleep(500); } catch (InterruptedException ignore) {}
        send("READY " + port);
    }

    /** Περιμένει μήνυμα PLAYING από τον client (για UDP/RTP). */
    private void waitForPlaying() {
        try {
            String ack = reader.readLine();
            logger.debug("Ack: {}", ack);
        } catch (IOException e) {
            logger.warn("Δεν ελήφθη PLAYING: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Βοηθητικές μέθοδοι
    // ---------------------------------------------------------------

    /** Αυτόματη επιλογή protocol βάσει ανάλυσης. */
    private String autoSelectProtocol(String resolution) {
        switch (resolution) {
            case "240p":           return "TCP";
            case "360p": case "480p": return "UDP";
            default:               return "RTP/UDP";
        }
    }

    /** Εξάγει την ανάλυση από το όνομα αρχείου (π.χ. "Forrest_Gump-480p.mkv" → "480p"). */
    private String extractResolution(String fileName) {
        for (String res : new String[]{"1080p","720p","480p","360p","240p"}) {
            if (fileName.contains("-" + res)) return res;
        }
        return "unknown";
    }

    private void send(String message) {
        writer.println(message);
    }

    private void closeQuietly() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignore) {}
    }
}
