package server;

import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import common.BitrateTable;
import common.VideoInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Διαχειρίζεται τη βιβλιοθήκη βίντεο:
 *  – σκανάρει τον φάκελο videos
 *  – δημιουργεί αρχεία που λείπουν με FFmpeg (Jaffree)
 *  – φιλτράρει τη λίστα βάσει ταχύτητας και format
 */
public class VideoLibrary {

    private static final Logger logger = LoggerFactory.getLogger(VideoLibrary.class);

    // Υποστηριζόμενες αναλύσεις (από μικρότερη σε μεγαλύτερη)
    private static final String[] RESOLUTIONS = {"240p", "360p", "480p", "720p", "1080p"};

    // Ύψος σε pixels για κάθε ανάλυση
    private static final Map<String, Integer> HEIGHT = new HashMap<>();

    // Υποστηριζόμενα formats
    private static final String[] FORMATS = {"mp4", "avi", "mkv"};

    static {
        HEIGHT.put("240p",   240);
        HEIGHT.put("360p",   360);
        HEIGHT.put("480p",   480);
        HEIGHT.put("720p",   720);
        HEIGHT.put("1080p", 1080);
    }

    private final File videosFolder;
    private List<VideoInfo> allVideos = new ArrayList<>();

    public VideoLibrary(String path) {
        this.videosFolder = new File(path);
        if (!videosFolder.exists()) {
            videosFolder.mkdirs();
            logger.info("Δημιουργήθηκε φάκελος: {}", path);
        }
    }

    // ---------------------------------------------------------------
    // Δημόσιες μέθοδοι
    // ---------------------------------------------------------------

    /**
     * Σκανάρει τον φάκελο videos, δημιουργεί αρχεία που λείπουν
     * και ανανεώνει την εσωτερική λίστα.
     *
     * @param onFileConverted καλείται μετά από κάθε επιτυχή μετατροπή
     */
    public synchronized void scanAndConvert(Runnable onFileConverted) {
        logger.info("Σκανάρισμα: {}", videosFolder.getAbsolutePath());

        // Βρίσκουμε τα υπάρχοντα αρχεία
        // Δομή: movieName → format → resolution → File
        Map<String, Map<String, Map<String, File>>> existing = findExistingFiles();

        if (existing.isEmpty()) {
            logger.warn("Δεν βρέθηκαν βίντεο.");
            loadAllVideos();
            return;
        }

        for (String movie : existing.keySet()) {
            Map<String, Map<String, File>> byFormat = existing.get(movie);

            // Μέγιστη ανάλυση που ήδη υπάρχει για αυτή την ταινία
            String maxRes    = findMaxResolution(byFormat);
            int    maxHeight = HEIGHT.get(maxRes);

            // Ψάχνουμε το καλύτερο υπάρχον αρχείο ως πηγή
            File source = findBestSource(byFormat, maxRes);
            if (source == null) {
                logger.error("Δεν βρέθηκε πηγαίο αρχείο για: {}", movie);
                continue;
            }

            logger.info("Ταινία '{}' – μέγιστη ανάλυση: {}, πηγή: {}",
                        movie, maxRes, source.getName());

            // Δημιουργία αρχείων που λείπουν
            for (String fmt : FORMATS) {
                for (String res : RESOLUTIONS) {
                    int h = HEIGHT.get(res);
                    if (h > maxHeight) continue; // Δεν μεγαλώνουμε ανάλυση

                    boolean exists = byFormat.containsKey(fmt)
                                  && byFormat.get(fmt).containsKey(res);
                    if (!exists) {
                        String outName = movie + "-" + res + "." + fmt;
                        File   outFile = new File(videosFolder, outName);
                        logger.info("Δημιουργία: {}", outName);
                        boolean ok = convertVideo(source.getAbsolutePath(),
                                                  outFile.getAbsolutePath(), h);
                        if (ok && onFileConverted != null) {
                            onFileConverted.run();
                        }
                    }
                }
            }
        }

        loadAllVideos();
        logger.info("Σύνολο διαθέσιμων βίντεο: {}", allVideos.size());
    }

    /**
     * Επιστρέφει λίστα βίντεο φιλτραρισμένη βάσει ταχύτητας και format.
     */
    public synchronized List<VideoInfo> getFilteredList(double speedMbps, String format) {
        List<VideoInfo> result = new ArrayList<>();
        for (VideoInfo v : allVideos) {
            if (!v.format.equalsIgnoreCase(format)) continue;
            if (BitrateTable.canStream(v.resolution, speedMbps)) {
                result.add(v);
            }
        }
        return result;
    }

    /** Επιστρέφει το πλήρες path ενός αρχείου βάσει ονόματος. */
    public synchronized String getFilePath(String fileName) {
        for (VideoInfo v : allVideos) {
            if (v.fileName.equals(fileName)) return v.filePath;
        }
        return null;
    }

    /** Επιστρέφει όλα τα βίντεο. */
    public synchronized List<VideoInfo> getAllVideos() {
        return new ArrayList<>(allVideos);
    }

    // ---------------------------------------------------------------
    // Βοηθητικές μέθοδοι
    // ---------------------------------------------------------------

    /**
     * Σκανάρει τον φάκελο και χτίζει τη δομή: movieName → format → resolution → File
     */
    private Map<String, Map<String, Map<String, File>>> findExistingFiles() {
        Map<String, Map<String, Map<String, File>>> result = new HashMap<>();
        File[] files = videosFolder.listFiles();
        if (files == null) return result;

        for (File f : files) {
            if (!f.isFile()) continue;
            VideoInfo info = parseFileName(f.getName(), f.getAbsolutePath());
            if (info == null) continue;
            result.computeIfAbsent(info.movieName, k -> new HashMap<>())
                  .computeIfAbsent(info.format,    k -> new HashMap<>())
                  .put(info.resolution, f);
        }
        return result;
    }

    /**
     * Αναλύει ένα όνομα αρχείου της μορφής "MovieName-720p.mkv".
     * Επιστρέφει null αν δεν αναγνωριστεί.
     */
    private VideoInfo parseFileName(String name, String path) {
        // Βρίσκουμε extension
        String format = null;
        for (String fmt : FORMATS) {
            if (name.toLowerCase().endsWith("." + fmt)) {
                format = fmt;
                break;
            }
        }
        if (format == null) return null;

        String noExt = name.substring(0, name.lastIndexOf('.'));

        // Βρίσκουμε ανάλυση στο τέλος (π.χ. "-720p")
        String resolution = null;
        String movieName  = null;
        for (String res : RESOLUTIONS) {
            if (noExt.endsWith("-" + res)) {
                resolution = res;
                movieName  = noExt.substring(0, noExt.length() - res.length() - 1);
                break;
            }
        }
        if (resolution == null || movieName == null || movieName.isEmpty()) return null;

        return new VideoInfo(movieName, resolution, format, path);
    }

    /** Βρίσκει την μέγιστη ανάλυση που υπάρχει για μια ταινία. */
    private String findMaxResolution(Map<String, Map<String, File>> byFormat) {
        String maxRes = "240p";
        int    maxH   = 0;
        for (Map<String, File> byRes : byFormat.values()) {
            for (String res : byRes.keySet()) {
                int h = HEIGHT.getOrDefault(res, 0);
                if (h > maxH) { maxH = h; maxRes = res; }
            }
        }
        return maxRes;
    }

    /**
     * Βρίσκει το καλύτερο πηγαίο αρχείο (προτιμά mkv στη μέγιστη ανάλυση).
     */
    private File findBestSource(Map<String, Map<String, File>> byFormat, String maxRes) {
        for (String fmt : new String[]{"mkv", "mp4", "avi"}) {
            Map<String, File> byRes = byFormat.get(fmt);
            if (byRes != null && byRes.containsKey(maxRes)) {
                return byRes.get(maxRes);
            }
        }
        // Αν δεν βρούμε στη μέγιστη ανάλυση, παίρνουμε οτιδήποτε
        for (Map<String, File> byRes : byFormat.values()) {
            for (File f : byRes.values()) return f;
        }
        return null;
    }

    /**
     * Μετατρέπει ένα βίντεο σε νέο format/ανάλυση με τη βοήθεια Jaffree (FFmpeg wrapper).
     */
    private boolean convertVideo(String inputPath, String outputPath, int targetHeight) {
        try {
            logger.info("Μετατροπή: {} → {} ({}p)", inputPath, outputPath, targetHeight);

            FFmpeg.atPath()
                .addInput(UrlInput.fromUrl(inputPath))
                .addOutput(UrlOutput.toUrl(outputPath)
                    .addArguments("-vf",     "scale=-2:" + targetHeight)
                    .setCodec(StreamType.VIDEO, "libx264")
                    .setCodec(StreamType.AUDIO, "aac")
                    .addArguments("-preset", "fast")
                    .addArguments("-crf",    "23")
                )
                .execute();

            logger.info("Επιτυχής μετατροπή: {}", outputPath);
            return true;
        } catch (Exception e) {
            logger.error("Σφάλμα μετατροπής: {}", e.getMessage());
            return false;
        }
    }

    /** Φορτώνει ξανά τη λίστα allVideos από τον φάκελο. */
    private void loadAllVideos() {
        allVideos.clear();
        File[] files = videosFolder.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isFile()) continue;
            VideoInfo info = parseFileName(f.getName(), f.getAbsolutePath());
            if (info != null) allVideos.add(info);
        }
        allVideos.sort((a, b) -> a.fileName.compareTo(b.fileName));
    }
}
