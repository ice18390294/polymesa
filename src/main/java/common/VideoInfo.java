package common;

/**
 * Αντικείμενο που αναπαριστά ένα αρχείο βίντεο.
 * Κρατά πληροφορίες για το όνομα ταινίας, ανάλυση, format και διαδρομή αρχείου.
 */
public class VideoInfo {

    public String movieName;   // Όνομα ταινίας, π.χ. "Forrest_Gump"
    public String resolution;  // Ανάλυση,       π.χ. "480p"
    public String format;      // Format,         π.χ. "mkv"
    public String fileName;    // Όνομα αρχείου,  π.χ. "Forrest_Gump-480p.mkv"
    public String filePath;    // Πλήρης διαδρομή στο δίσκο

    public VideoInfo(String movieName, String resolution, String format, String filePath) {
        this.movieName = movieName;
        this.resolution = resolution;
        this.format = format;
        this.fileName = movieName + "-" + resolution + "." + format;
        this.filePath = filePath;
    }

    @Override
    public String toString() {
        return fileName;
    }
}
