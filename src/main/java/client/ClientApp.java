package client;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;

/**
 * Κύριο σημείο εκκίνησης του Streaming Client.
 * Εκκινεί το γραφικό περιβάλλον (GUI) στο Event Dispatch Thread.
 */
public class ClientApp {

    public static void main(String[] args) {
        // Χρήση FlatLaf dark look-and-feel
        try {
            FlatDarkLaf.setup();
        } catch (Exception e) {
            // fallback to default
        }

        // Εκκίνηση GUI στο Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            ClientGUI gui = new ClientGUI();
            gui.setVisible(true);
        });
    }
}
