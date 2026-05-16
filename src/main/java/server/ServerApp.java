package server;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;

/**
 * Κύριο σημείο εκκίνησης του Streaming Server.
 * Εκκινεί το γραφικό περιβάλλον (GUI) στο Event Dispatch Thread.
 */
public class ServerApp {

    public static void main(String[] args) {
        // Χρήση FlatLaf dark look-and-feel
        try {
            FlatDarkLaf.setup();
        } catch (Exception e) {
            // fallback to default
        }

        // Εκκίνηση GUI στο Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            gui.setVisible(true);
        });
    }
}
