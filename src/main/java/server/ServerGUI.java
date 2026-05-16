package server;

import ui.AppTheme;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;

/**
 * Γραφικό περιβάλλον (GUI) του Streaming Server.
 *
 * Διάταξη:
 *  – Πάνω   : ρυθμίσεις (φάκελος videos, θύρα, κουμπιά)
 *  – Κέντρο : log area
 *  – Κάτω   : status bar (κατάσταση + αριθμός clients)
 */
public class ServerGUI extends JFrame {

    private JTextField  videosFolderField;
    private JTextField  portField;
    private JButton     scanButton;
    private JButton     startButton;
    private JButton     stopButton;
    private JTextArea   logArea;
    private JLabel      statusLabel;
    private JLabel      clientCountLabel;

    private StreamingServer server;
    private VideoLibrary    videoLibrary;

    private int clientCount = 0;

    public ServerGUI() {
        buildWindow();
        buildComponents();
    }

    // ---------------------------------------------------------------
    // Δόμηση
    // ---------------------------------------------------------------

    private void buildWindow() {
        setTitle("Streaming Server");
        setSize(720, 520);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int ans = JOptionPane.showConfirmDialog(
                        ServerGUI.this,
                        "Τερματισμός server;",
                        "Έξοδος",
                        JOptionPane.YES_NO_OPTION);
                if (ans == JOptionPane.YES_OPTION) {
                    if (server != null && server.isRunning()) server.stop();
                    System.exit(0);
                }
            }
        });
    }

    private void buildComponents() {
        setLayout(new BorderLayout(8, 8));

        // ── Πάνω: ρυθμίσεις ─────────────────────────────────────
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(new TitledBorder("Ρυθμίσεις Server"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill   = GridBagConstraints.HORIZONTAL;

        // Φάκελος videos
        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        topPanel.add(new JLabel("Φάκελος Videos:"), g);
        videosFolderField = new JTextField("videos", 22);
        g.gridx = 1; g.weightx = 1.0;
        topPanel.add(videosFolderField, g);
        JButton browseBtn = new JButton("Αναζήτηση…");
        g.gridx = 2; g.weightx = 0;
        topPanel.add(browseBtn, g);

        // Θύρα
        g.gridx = 0; g.gridy = 1; g.weightx = 0;
        topPanel.add(new JLabel("Θύρα:"), g);
        portField = new JTextField("9000", 8);
        g.gridx = 1; g.weightx = 1.0;
        topPanel.add(portField, g);

        // Κουμπιά
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        scanButton  = new JButton("🔍 Σκανάρισμα & Μετατροπή");
        startButton = new JButton("▶ Εκκίνηση Server");
        stopButton  = new JButton("■ Διακοπή Server");
        AppTheme.applyButtonStyle(scanButton,  AppTheme.PRIMARY);
        AppTheme.applyButtonStyle(startButton, AppTheme.SUCCESS);
        AppTheme.applyButtonStyle(stopButton,  AppTheme.DANGER);
        stopButton.setEnabled(false);
        btnPanel.add(scanButton);
        btnPanel.add(startButton);
        btnPanel.add(stopButton);
        g.gridx = 0; g.gridy = 2; g.gridwidth = 3;
        topPanel.add(btnPanel, g);

        add(topPanel, BorderLayout.NORTH);

        // ── Κέντρο: log ─────────────────────────────────────────
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(AppTheme.FONT_MONO);
        logArea.setBackground(AppTheme.LOG_BG);
        logArea.setForeground(AppTheme.LOG_FG);
        logArea.setCaretColor(Color.WHITE);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(new TitledBorder("Καταγραφή (Log)"));
        add(scrollPane, BorderLayout.CENTER);

        // ── Κάτω: status bar ────────────────────────────────────
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusLabel      = new JLabel("  Κατάσταση: Σταματημένος");
        clientCountLabel = new JLabel("Clients: 0  ");
        statusPanel.add(statusLabel,      BorderLayout.WEST);
        statusPanel.add(clientCountLabel, BorderLayout.EAST);
        add(statusPanel, BorderLayout.SOUTH);

        // ── Listeners ───────────────────────────────────────────
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(".");
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                videosFolderField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        scanButton.addActionListener(e  -> scanVideos());
        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e  -> stopServer());
    }

    // ---------------------------------------------------------------
    // Ενέργειες κουμπιών
    // ---------------------------------------------------------------

    /** Σκανάρει τον φάκελο videos και δημιουργεί αρχεία που λείπουν. */
    private void scanVideos() {
        String folder = videosFolderField.getText().trim();
        if (folder.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Παρακαλώ επιλέξτε φάκελο videos.");
            return;
        }

        videoLibrary = new VideoLibrary(folder);
        scanButton.setEnabled(false);
        startButton.setEnabled(false);
        log("Σκανάρισμα: " + folder);

        // Τρέχει σε background thread για να μην παγώνει το GUI
        new Thread(() -> {
            videoLibrary.scanAndConvert(() ->
                SwingUtilities.invokeLater(() -> log("  ✔ Μετατροπή ολοκληρώθηκε…"))
            );
            SwingUtilities.invokeLater(() -> {
                int total = videoLibrary.getAllVideos().size();
                log("Σκανάρισμα τέλος – διαθέσιμα βίντεο: " + total);
                scanButton.setEnabled(true);
                startButton.setEnabled(true);
            });
        }).start();
    }

    /** Ξεκινά τον streaming server. */
    private void startServer() {
        if (videoLibrary == null) {
            JOptionPane.showMessageDialog(this, "Κάντε πρώτα σκανάρισμα.");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Μη έγκυρος αριθμός θύρας.");
            return;
        }

        server = new StreamingServer(port, videoLibrary, this);
        server.start();
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        portField.setEditable(false);
        scanButton.setEnabled(false);
    }

    /** Σταματά τον server. */
    private void stopServer() {
        if (server != null) server.stop();
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        portField.setEditable(true);
        scanButton.setEnabled(true);
    }

    // ---------------------------------------------------------------
    // Δημόσιες μέθοδοι (καλούνται από άλλα threads)
    // ---------------------------------------------------------------

    /** Προσθέτει γραμμή στο log – thread-safe. */
    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalTime.now().toString().substring(0, 8);
            logArea.append("[" + time + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /** Ενημερώνει την ένδειξη κατάστασης – thread-safe. */
    public void setStatus(String s) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("  Κατάσταση: " + s));
    }

    /** Αυξάνει τον μετρητή ενεργών clients – thread-safe. */
    public synchronized void incrementClients() {
        clientCount++;
        SwingUtilities.invokeLater(() -> clientCountLabel.setText("Clients: " + clientCount + "  "));
    }

    /** Μειώνει τον μετρητή ενεργών clients – thread-safe. */
    public synchronized void decrementClients() {
        clientCount = Math.max(0, clientCount - 1);
        SwingUtilities.invokeLater(() -> clientCountLabel.setText("Clients: " + clientCount + "  "));
    }
}
