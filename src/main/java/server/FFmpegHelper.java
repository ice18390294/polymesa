package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Βοηθητική κλάση για εκκίνηση FFmpeg streaming με ProcessBuilder.
 *
 * Υποστηρίζει 3 πρωτόκολλα:
 *  – TCP  : server ακούει, client συνδέεται
 *  – UDP  : server στέλνει στον client
 *  – RTP  : server στέλνει στον client μέσω RTP/UDP
 */
public class FFmpegHelper {

    private static final Logger logger = LoggerFactory.getLogger(FFmpegHelper.class);

    private Process streamProcess;  // Το τρέχον FFmpeg process

    // ---------------------------------------------------------------
    // Δημόσιες μέθοδοι εκκίνησης streaming
    // ---------------------------------------------------------------

    /**
     * TCP Listen mode: ο server ακούει και ο client συνδέεται.
     *
     * Εντολή: ffmpeg -re -i <file> -c copy -f mpegts tcp://0.0.0.0:<port>?listen=1
     */
    public void startTcpServer(String inputFile, int port) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-re");                        // Αναπαραγωγή σε πραγματικό χρόνο
        cmd.add("-i");   cmd.add(inputFile);
        cmd.add("-c");   cmd.add("copy");      // Χωρίς επανακωδικοποίηση
        cmd.add("-f");   cmd.add("mpegts");    // Μορφή μεταφοράς
        cmd.add("tcp://0.0.0.0:" + port + "?listen=1");

        logger.info("TCP Server streaming – θύρα {}", port);
        startProcess(cmd);
    }

    /**
     * UDP mode: ο server στέλνει στον client.
     *
     * Εντολή: ffmpeg -re -i <file> -c copy -f mpegts udp://<clientIp>:<port>
     */
    public void startUdpServer(String inputFile, String clientIp, int port) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-re");
        cmd.add("-i");   cmd.add(inputFile);
        cmd.add("-c");   cmd.add("copy");
        cmd.add("-f");   cmd.add("mpegts");
        cmd.add("udp://" + clientIp + ":" + port);

        logger.info("UDP streaming → {}:{}", clientIp, port);
        startProcess(cmd);
    }

    /**
     * RTP/UDP mode: ο server στέλνει στον client μέσω RTP.
     *
     * Εντολή: ffmpeg -re -i <file> -c:v libx264 -c:a aac -f rtp rtp://<clientIp>:<port>
     */
    public void startRtpServer(String inputFile, String clientIp, int port) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-re");
        cmd.add("-i");     cmd.add(inputFile);
        cmd.add("-c:v");   cmd.add("libx264");
        cmd.add("-c:a");   cmd.add("aac");
        cmd.add("-preset"); cmd.add("ultrafast");
        cmd.add("-f");     cmd.add("rtp");
        cmd.add("rtp://" + clientIp + ":" + port);

        logger.info("RTP/UDP streaming → {}:{}", clientIp, port);
        startProcess(cmd);
    }

    /** Σταματά το τρέχον streaming process. */
    public void stopStreaming() {
        if (streamProcess != null && streamProcess.isAlive()) {
            streamProcess.destroy();
            logger.info("Streaming σταμάτησε.");
        }
    }

    /** Ελέγχει αν το streaming είναι ενεργό. */
    public boolean isStreaming() {
        return streamProcess != null && streamProcess.isAlive();
    }

    // ---------------------------------------------------------------
    // Βοηθητικές μέθοδοι
    // ---------------------------------------------------------------

    /**
     * Ξεκινά ένα FFmpeg process με τις δοθείσες εντολές.
     * Διαβάζει το output σε background thread για να μην κολλήσει.
     */
    private void startProcess(List<String> command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);  // Ενοποίηση stdout + stderr
        streamProcess = pb.start();

        // Διαβάζουμε το output αλλιώς ο buffer γεμίζει και κολλάει
        Thread reader = new Thread(() -> {
            try {
                byte[] buf = new byte[4096];
                while (streamProcess.isAlive()) {
                    if (streamProcess.getInputStream().read(buf) == -1) break;
                }
            } catch (IOException e) {
                // Φυσιολογικό – το process τερματίστηκε
            }
        });
        reader.setDaemon(true);
        reader.start();
    }
}
