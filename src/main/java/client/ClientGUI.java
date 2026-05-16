package client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Γραφικό περιβάλλον (GUI) του Streaming Client.
 *
 * Βήματα χρήσης:
 *  1. Σύνδεση με server + speed test
 *  2. Επιλογή format και αποστολή → λήψη λίστας βίντεο
 *  3. Επιλογή βίντεο + protocol
 *  4. Εκκίνηση streaming
 */
public class ClientGUI extends JFrame {

    private static final Logger logger = LoggerFactory.getLogger(ClientGUI.class);

    // ── Σύνδεση ─────────────────────────────────────────────────────
    private JTextField hostField;
    private JTextField portField;
    private JButton    connectButton;
    private JLabel     speedLabel;
    private JButton    speedTestButton;

    // ── Format / Λίστα ──────────────────────────────────────────────
    private JComboBox<String>    formatCombo;
    private JButton              getListButton;
    private DefaultListModel<String> videoListModel;
    private JList<String>        videoList;

    // ── Protocol ────────────────────────────────────────────────────
    private JRadioButton autoRadio;
    private JRadioButton tcpRadio;
    private JRadioButton udpRadio;
    private JRadioButton rtpRadio;

    // ── Streaming ───────────────────────────────────────────────────
    private JButton streamButton;
    private JButton stopButton;

    // ── Log ─────────────────────────────────────────────────────────
    private JTextArea logArea;

    // ── Εσωτερική κατάσταση ─────────────────────────────────────────
    private Socket       controlSocket;
    private BufferedReader reader;
    private PrintWriter    writer;
    private boolean      connected = false;
    private double       speedMbps = 4.0;
    private Process      playerProcess;  // Τρέχον ffplay process

    public ClientGUI() {
        buildWindow();
        buildComponents();
        refreshButtonStates();
    }

    // ---------------------------------------------------------------
    // Δόμηση GUI
    // ---------------------------------------------------------------

    private void buildWindow() {
        setTitle("Streaming Client");
        setSize(800, 620);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                System.exit(0);
            }
        });
    }

    private void buildComponents() {
        setLayout(new BorderLayout(6, 6));

        // ── Αριστερά: πάνελ ελέγχου ─────────────────────────────
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setPreferredSize(new Dimension(290, 0));

        leftPanel.add(buildConnectionPanel());
        leftPanel.add(buildSpeedPanel());
        leftPanel.add(buildFormatPanel());
        leftPanel.add(buildProtocolPanel());
        leftPanel.add(buildStreamPanel());

        add(leftPanel, BorderLayout.WEST);

        // ── Κέντρο: λίστα βίντεο ─────────────────────────────────
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(new TitledBorder("Διαθέσιμα Βίντεο"));
        videoListModel = new DefaultListModel<>();
        videoList = new JList<>(videoListModel);
        videoList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        videoList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        centerPanel.add(new JScrollPane(videoList), BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // ── Κάτω: log ────────────────────────────────────────────
        logArea = new JTextArea(7, 0);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("Log"));
        logScroll.setPreferredSize(new Dimension(0, 160));
        add(logScroll, BorderLayout.SOUTH);
    }

    private JPanel buildConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("1. Σύνδεση με Server"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 4, 3, 4);
        g.fill   = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        panel.add(new JLabel("Host:"), g);
        hostField = new JTextField("localhost", 12);
        g.gridx = 1; g.weightx = 1.0;
        panel.add(hostField, g);

        g.gridx = 0; g.gridy = 1; g.weightx = 0;
        panel.add(new JLabel("Θύρα:"), g);
        portField = new JTextField("9000", 6);
        g.gridx = 1; g.weightx = 1.0;
        panel.add(portField, g);

        connectButton = new JButton("Σύνδεση");
        g.gridx = 0; g.gridy = 2; g.gridwidth = 2;
        panel.add(connectButton, g);

        connectButton.addActionListener(e -> {
            if (!connected) connectToServer();
            else            disconnect();
        });

        return panel;
    }

    private JPanel buildSpeedPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("2. Ταχύτητα Σύνδεσης"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 4, 3, 4);
        g.fill   = GridBagConstraints.HORIZONTAL;

        speedLabel = new JLabel("Ταχύτητα: --");
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
        panel.add(speedLabel, g);

        speedTestButton = new JButton("Speed Test (5 sec)");
        g.gridx = 0; g.gridy = 1;
        panel.add(speedTestButton, g);

        speedTestButton.addActionListener(e -> runSpeedTest());

        return panel;
    }

    private JPanel buildFormatPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("3. Format & Λίστα"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 4, 3, 4);
        g.fill   = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        panel.add(new JLabel("Format:"), g);
        formatCombo = new JComboBox<>(new String[]{"mkv", "mp4", "avi"});
        g.gridx = 1; g.weightx = 1.0;
        panel.add(formatCombo, g);

        getListButton = new JButton("Λήψη Λίστας Βίντεο");
        g.gridx = 0; g.gridy = 1; g.gridwidth = 2;
        panel.add(getListButton, g);

        getListButton.addActionListener(e -> getVideoList());

        return panel;
    }

    private JPanel buildProtocolPanel() {
        JPanel panel = new JPanel(new GridLayout(5, 1, 2, 2));
        panel.setBorder(new TitledBorder("4. Πρωτόκολλο Μετάδοσης"));

        ButtonGroup group = new ButtonGroup();
        autoRadio = new JRadioButton("Αυτόματο (βάσει ανάλυσης)", true);
        tcpRadio  = new JRadioButton("TCP");
        udpRadio  = new JRadioButton("UDP");
        rtpRadio  = new JRadioButton("RTP/UDP");

        group.add(autoRadio);
        group.add(tcpRadio);
        group.add(udpRadio);
        group.add(rtpRadio);

        panel.add(new JLabel("Επιλέξτε protocol:"));
        panel.add(autoRadio);
        panel.add(tcpRadio);
        panel.add(udpRadio);
        panel.add(rtpRadio);

        return panel;
    }

    private JPanel buildStreamPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 4, 4));
        panel.setBorder(new TitledBorder("5. Streaming"));

        streamButton = new JButton("▶  Έναρξη Streaming");
        streamButton.setBackground(new Color(34, 139, 34));
        streamButton.setForeground(Color.WHITE);
        streamButton.setFont(streamButton.getFont().deriveFont(Font.BOLD, 13f));

        stopButton = new JButton("■  Διακοπή");
        stopButton.setBackground(new Color(178, 34, 34));
        stopButton.setForeground(Color.WHITE);

        panel.add(streamButton);
        panel.add(stopButton);

        streamButton.addActionListener(e -> startStreaming());
        stopButton.addActionListener(e   -> stopStreaming());

        return panel;
    }

    // ---------------------------------------------------------------
    // Ενέργειες
    // ---------------------------------------------------------------

    /** Συνδέεται με τον server. */
    private void connectToServer() {
        String host = hostField.getText().trim();
        int    port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Μη έγκυρος αριθμός θύρας.");
            return;
        }

        new Thread(() -> {
            try {
                log("Σύνδεση με " + host + ":" + port + "…");
                controlSocket = new Socket(host, port);
                reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
                writer = new PrintWriter(new OutputStreamWriter(controlSocket.getOutputStream()), true);

                // Διαβάζουμε WELCOME
                String welcome = reader.readLine();
                if ("WELCOME".equals(welcome)) {
                    connected = true;
                    SwingUtilities.invokeLater(() -> {
                        connectButton.setText("Αποσύνδεση");
                        log("✔ Συνδέθηκε με server.");
                        refreshButtonStates();
                    });
                } else {
                    log("Απροσδόκητη απάντηση server: " + welcome);
                }
            } catch (IOException ex) {
                log("✖ Αποτυχία σύνδεσης: " + ex.getMessage());
                logger.error("Σφάλμα σύνδεσης: {}", ex.getMessage());
            }
        }).start();
    }

    /** Αποσυνδέεται από τον server. */
    private void disconnect() {
        if (writer != null) {
            try { writer.println("BYE"); } catch (Exception ignore) {}
        }
        try {
            if (controlSocket != null && !controlSocket.isClosed()) controlSocket.close();
        } catch (IOException ignore) {}
        connected = false;
        SwingUtilities.invokeLater(() -> {
            connectButton.setText("Σύνδεση");
            log("Αποσυνδέθηκε.");
            refreshButtonStates();
        });
    }

    /** Εκτελεί speed test σε background thread. */
    private void runSpeedTest() {
        speedTestButton.setEnabled(false);
        speedLabel.setText("Ταχύτητα: μέτρηση…");
        log("Speed test σε εξέλιξη (5 sec)…");

        new Thread(() -> {
            double speed = SpeedTester.measureSpeed();
            speedMbps = speed;
            SwingUtilities.invokeLater(() -> {
                speedLabel.setText(String.format("Ταχύτητα: %.2f Mbps", speed));
                speedTestButton.setEnabled(true);
                log(String.format("Speed test: %.2f Mbps", speed));
            });
        }).start();
    }

    /**
     * Στέλνει ταχύτητα + format στον server και λαμβάνει τη λίστα βίντεο.
     */
    private void getVideoList() {
        if (!connected) {
            JOptionPane.showMessageDialog(this, "Συνδεθείτε πρώτα με τον server.");
            return;
        }

        String format = (String) formatCombo.getSelectedItem();

        new Thread(() -> {
            try {
                // Στέλνουμε ταχύτητα και format
                writer.println(String.format("SPEED=%.2f FORMAT=%s", speedMbps, format));
                log(String.format("Αποστολή: ταχύτητα=%.2f Mbps, format=%s", speedMbps, format));

                // Λαμβάνουμε τη λίστα
                List<String> videos = new ArrayList<>();
                String line;
                boolean inList = false;
                while ((line = reader.readLine()) != null) {
                    if ("LIST_START".equals(line)) { inList = true; continue; }
                    if ("LIST_END".equals(line))   { break; }
                    if (inList) videos.add(line);
                }

                List<String> finalVideos = videos;
                SwingUtilities.invokeLater(() -> {
                    videoListModel.clear();
                    for (String v : finalVideos) videoListModel.addElement(v);
                    log("Ελήφθησαν " + finalVideos.size() + " βίντεο.");
                    if (finalVideos.isEmpty()) {
                        log("Δεν υπάρχουν βίντεο για αυτή τη ταχύτητα και format.");
                    }
                    refreshButtonStates();
                });
            } catch (IOException ex) {
                log("✖ Σφάλμα λήψης λίστας: " + ex.getMessage());
            }
        }).start();
    }

    /**
     * Ξεκινά streaming για το επιλεγμένο βίντεο.
     */
    private void startStreaming() {
        String selected = videoList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Επιλέξτε βίντεο από τη λίστα.");
            return;
        }

        String protocol = getSelectedProtocol();
        String serverHost = hostField.getText().trim();

        log("Αίτηση streaming: " + selected + " | " + protocol);

        new Thread(() -> {
            try {
                // Στέλνουμε επιλογή στον server
                writer.println("SELECT " + selected + " " + protocol);

                // Λαμβάνουμε READY <port> ή ERROR
                String reply = reader.readLine();
                if (reply == null || reply.startsWith("ERROR")) {
                    log("✖ Server: " + reply);
                    return;
                }

                // Μορφή: "READY 9100 TCP"
                String[] parts = reply.split(" ");
                int streamPort = Integer.parseInt(parts[1]);
                String actualProtocol = parts.length > 2 ? parts[2] : protocol;

                log("Server έτοιμος – θύρα " + streamPort + " (" + actualProtocol + ")");

                // Εκκίνηση ffplay βάσει πραγματικού protocol (από server)
                if ("TCP".equals(actualProtocol)) {
                    // Μικρή καθυστέρηση ώστε το server να ξεκινήσει σε listen mode
                    Thread.sleep(800);
                    startFfplay("tcp://" + serverHost + ":" + streamPort);
                } else if ("UDP".equals(actualProtocol)) {
                    // Ξεκινάμε ffplay (ακούει), μετά λέμε PLAYING
                    startFfplay("udp://0.0.0.0:" + streamPort);
                    Thread.sleep(1000);  // Αναμονή ώσπου το ffplay ξεκινήσει
                    writer.println("PLAYING");
                } else {
                    // RTP/UDP
                    startFfplay("rtp://0.0.0.0:" + streamPort);
                    Thread.sleep(1000);
                    writer.println("PLAYING");
                }

                SwingUtilities.invokeLater(() -> {
                    log("▶ Streaming ξεκίνησε.");
                    refreshButtonStates();
                });

            } catch (Exception ex) {
                log("✖ Σφάλμα streaming: " + ex.getMessage());
                logger.error("Σφάλμα streaming client: {}", ex.getMessage());
            }
        }).start();
    }

    /** Σταματά το τρέχον ffplay process. */
    private void stopStreaming() {
        if (playerProcess != null && playerProcess.isAlive()) {
            playerProcess.destroy();
            log("■ Streaming σταμάτησε.");
        }
        refreshButtonStates();
    }

    // ---------------------------------------------------------------
    // Βοηθητικές μέθοδοι
    // ---------------------------------------------------------------

    /**
     * Ξεκινά ffplay για αναπαραγωγή βίντεο από URL.
     * Προστίθεται buffering για πιο ομαλή αναπαραγωγή.
     */
    private void startFfplay(String url) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffplay");
        cmd.add("-analyzeduration"); cmd.add("10000000");  // Buffer ανάλυσης
        cmd.add("-probesize");       cmd.add("10000000");  // Μέγεθος probe
        cmd.add("-i");               cmd.add(url);

        logger.info("Εκκίνηση ffplay: {}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        playerProcess = pb.start();

        // Διαβάζουμε output σε background thread
        Thread t = new Thread(() -> {
            try {
                byte[] buf = new byte[4096];
                while (playerProcess.isAlive()) {
                    if (playerProcess.getInputStream().read(buf) == -1) break;
                }
            } catch (IOException ignore) {}
        });
        t.setDaemon(true);
        t.start();
    }

    /** Επιστρέφει το επιλεγμένο protocol ως String. */
    private String getSelectedProtocol() {
        if (tcpRadio.isSelected())  return "TCP";
        if (udpRadio.isSelected())  return "UDP";
        if (rtpRadio.isSelected())  return "RTP/UDP";
        return "AUTO";  // Αυτόματο
    }

    /** Ενημερώνει το enabled/disabled των κουμπιών βάσει κατάστασης. */
    private void refreshButtonStates() {
        boolean hasVideo    = !videoListModel.isEmpty();
        boolean playerAlive = playerProcess != null && playerProcess.isAlive();

        speedTestButton.setEnabled(true);
        getListButton.setEnabled(connected);
        streamButton.setEnabled(connected && hasVideo && !playerAlive);
        stopButton.setEnabled(playerAlive);
    }

    /** Προσθέτει μήνυμα στο log – thread-safe. */
    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalTime.now().toString().substring(0, 8);
            logArea.append("[" + time + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
