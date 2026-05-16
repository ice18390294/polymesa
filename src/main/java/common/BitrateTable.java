package common;

import java.util.HashMap;
import java.util.Map;

/**
 * Πίνακας ελάχιστου bandwidth ανά ανάλυση.
 * Βασίζεται στα YouTube resolution/bitrate standards.
 *
 *  240p  →  0.5 Mbps
 *  360p  →  1.0 Mbps
 *  480p  →  2.0 Mbps
 *  720p  →  5.0 Mbps
 * 1080p  →  8.0 Mbps
 */
public class BitrateTable {

    // Ελάχιστο bandwidth (Mbps) για κάθε ανάλυση
    private static final Map<String, Double> REQUIRED = new HashMap<>();

    static {
        REQUIRED.put("240p",   0.5);
        REQUIRED.put("360p",   1.0);
        REQUIRED.put("480p",   2.0);
        REQUIRED.put("720p",   5.0);
        REQUIRED.put("1080p",  8.0);
    }

    /** Επιστρέφει το ελάχιστο bandwidth για μια ανάλυση. */
    public static double getRequired(String resolution) {
        return REQUIRED.getOrDefault(resolution, 999.0);
    }

    /**
     * Ελέγχει αν η διαθέσιμη ταχύτητα αρκεί για μια ανάλυση.
     *
     * @param resolution  π.χ. "480p"
     * @param speedMbps   διαθέσιμη ταχύτητα σε Mbps
     */
    public static boolean canStream(String resolution, double speedMbps) {
        return speedMbps >= getRequired(resolution);
    }
}
