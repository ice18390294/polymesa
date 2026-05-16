package server;

import javax.swing.*;

/**
 * Κύριο σημείο εκκίνησης του Streaming Server.
 * Εκκινεί το γραφικό περιβάλλον (GUI) στο Event Dispatch Thread.
 */
public class ServerApp {

    public static void main(String[] args) {
        // Χρήση look-and-feel του λειτουργικού συστήματος
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Αν αποτύχει, χρησιμοποιούμε το προεπιλεγμένο Java look-and-feel
        }

        // Εκκίνηση GUI στο Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            gui.setVisible(true);
        });
    }
}
