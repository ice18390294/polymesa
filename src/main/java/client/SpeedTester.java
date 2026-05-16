package client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Μετράει την ταχύτητα σύνδεσης (downlink) κατεβάζοντας δεδομένα
 * από ένα δοκιμαστικό URL για 5 δευτερόλεπτα.
 *
 * Αν η σύνδεση αποτύχει, επιστρέφει 4.0 Mbps ως προεπιλογή.
 */
public class SpeedTester {

    private static final Logger logger = LoggerFactory.getLogger(SpeedTester.class);

    // URL αρχείου για download test (10 MB από γνωστό speed-test server)
    private static final String TEST_URL = "https://speed.hetzner.de/10MB.bin";

    // Διάρκεια δοκιμής
    private static final int DURATION_MS = 5000;

    /**
     * Εκτελεί speed test και επιστρέφει ταχύτητα σε Mbps.
     */
    public static double measureSpeed() {
        logger.info("Εκκίνηση speed test ({} sec)…", DURATION_MS / 1000);

        try {
            URL url = new URL(TEST_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(DURATION_MS + 2000);
            conn.connect();

            InputStream in  = conn.getInputStream();
            byte[] buffer   = new byte[8192];
            long   bytesRead  = 0;
            long   startTime  = System.currentTimeMillis();
            int    read;

            // Διαβάζουμε μέχρι να περάσουν DURATION_MS milliseconds
            while (System.currentTimeMillis() - startTime < DURATION_MS) {
                read = in.read(buffer);
                if (read == -1) break;
                bytesRead += read;
            }

            in.close();
            conn.disconnect();

            long elapsedMs = System.currentTimeMillis() - startTime;
            if (elapsedMs == 0) return 4.0;

            // Mbps = (bytes * 8 bits) / (elapsed in sec) / 1,000,000
            double speedMbps = (bytesRead * 8.0) / (elapsedMs / 1000.0) / 1_000_000.0;
            logger.info("Speed test: {:.2f} Mbps ({} bytes σε {} ms)",
                        speedMbps, bytesRead, elapsedMs);
            return speedMbps;

        } catch (Exception e) {
            logger.warn("Speed test απέτυχε: {}. Χρήση προεπιλογής 4.0 Mbps.", e.getMessage());
            return 4.0;
        }
    }
}
