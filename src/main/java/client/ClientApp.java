package client;

import javax.swing.*;

/**
 * Κύριο σημείο εκκίνησης του Streaming Client.
 * Εκκινεί το γραφικό περιβάλλον (GUI) στο Event Dispatch Thread.
 */
public class ClientApp {

    public static void main(String[] args) {
        // Χρήση look-and-feel του λειτουργικού συστήματος
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Αν αποτύχει, χρησιμοποιούμε το προεπιλεγμένο Java look-and-feel
        }

        // Εκκίνηση GUI στο Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            ClientGUI gui = new ClientGUI();
            gui.setVisible(true);
        });
    }
}
