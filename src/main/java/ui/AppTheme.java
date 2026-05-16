package ui;

import javax.swing.*;
import java.awt.*;

public class AppTheme {
    // Colors
    public static final Color PRIMARY      = new Color(59, 130, 246);
    public static final Color SUCCESS      = new Color(34, 197, 94);
    public static final Color DANGER       = new Color(239, 68, 68);
    public static final Color BG_PANEL     = new Color(245, 247, 250);
    public static final Color LOG_BG       = new Color(20, 22, 28);
    public static final Color LOG_FG       = new Color(80, 220, 120);
    public static final Color BORDER_COLOR = new Color(200, 210, 225);

    // Fonts
    public static final Font FONT_BASE  = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BOLD  = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_MONO  = new Font("Consolas", Font.PLAIN, 12);
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 15);

    public static void applyButtonStyle(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(FONT_BOLD);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}
