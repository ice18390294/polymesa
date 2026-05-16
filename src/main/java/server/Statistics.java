package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Καταγράφει στατιστικά για κάθε streaming session.
 * Αποθηκεύει τα δεδομένα και στο log αρχείο και σε CSV.
 */
public class Statistics {

    private static final Logger logger = LoggerFactory.getLogger(Statistics.class);
    private static final String CSV_FILE = "statistics.csv";

    private String clientIp;
    private String fileName;
    private String protocol;
    private String resolution;
    private long startTime;

    public Statistics(String clientIp) {
        this.clientIp = clientIp;
        this.startTime = System.currentTimeMillis();
        createHeaderIfNeeded();
    }

    /** Καταγράφει τις επιλογές του client (αρχείο, protocol, ανάλυση). */
    public void setStreamInfo(String fileName, String protocol, String resolution) {
        this.fileName = fileName;
        this.protocol = protocol;
        this.resolution = resolution;
        logger.info("Streaming: {} | Protocol: {} | Client: {}", fileName, protocol, clientIp);
    }

    /**
     * Ολοκληρώνει τη session, εκτυπώνει σύνοψη και αποθηκεύει στο CSV.
     */
    public void finish() {
        long durationSec = (System.currentTimeMillis() - startTime) / 1000;
        double bitrate     = estimateBitrate(resolution);
        long   bytes       = (long) (bitrate * 1_000_000.0 / 8.0 * durationSec);

        logger.info("Session τέλος – Client: {}, Αρχείο: {}, Protocol: {}, "
                  + "Διάρκεια: {} sec, Εκτιμώμενα bytes: {}",
                  clientIp, fileName, protocol, durationSec, bytes);

        saveToCsv(durationSec, bitrate, bytes);
    }

    // ---------------------------------------------------------------
    // Βοηθητικές μέθοδοι
    // ---------------------------------------------------------------

    private double estimateBitrate(String res) {
        if (res == null) return 1.0;
        switch (res) {
            case "240p":  return 0.5;
            case "360p":  return 1.0;
            case "480p":  return 2.0;
            case "720p":  return 5.0;
            case "1080p": return 8.0;
            default:      return 1.0;
        }
    }

    private void createHeaderIfNeeded() {
        java.io.File f = new java.io.File(CSV_FILE);
        if (!f.exists()) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(CSV_FILE, true))) {
                pw.println("Timestamp,ClientIP,FileName,Protocol,Resolution,Duration(sec),Bitrate(Mbps),Bytes");
            } catch (Exception e) {
                logger.error("Δεν ήταν δυνατή η δημιουργία CSV: {}", e.getMessage());
            }
        }
    }

    private void saveToCsv(long duration, double bitrate, long bytes) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(CSV_FILE, true))) {
            String ts = LocalDateTime.now()
                           .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            pw.printf("%s,%s,%s,%s,%s,%d,%.2f,%d%n",
                ts, clientIp,
                fileName   != null ? fileName   : "N/A",
                protocol   != null ? protocol   : "N/A",
                resolution != null ? resolution : "N/A",
                duration, bitrate, bytes);
        } catch (Exception e) {
            logger.error("Σφάλμα αποθήκευσης CSV: {}", e.getMessage());
        }
    }
}
