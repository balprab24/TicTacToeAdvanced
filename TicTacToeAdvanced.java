import javax.swing.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class TicTacToeAdvanced {

    // ─── Palette ──────────────────────────────────────────────────────────────
    private static final Color CELL_BG   = new Color(34,  32,  62);
    private static final Color CELL_HVR  = new Color(50,  48,  88);
    private static final Color CELL_WIN  = new Color(14,  58,  38);
    private static final Color X_COLOR   = new Color(255, 80,  80);
    private static final Color O_COLOR   = new Color(48,  190, 255);
    private static final Color WIN_FG    = new Color(72,  230, 128);
    private static final Color TIE_COL   = new Color(251, 191, 36);
    private static final Color ACCENT    = new Color(167, 139, 250);
    private static final Color TEXT      = new Color(226, 226, 248);
    private static final Color SUBTEXT   = new Color(148, 148, 180);
    private static final Color BTN_DARK  = new Color(50,  48,  82);
    private static final Color GRID_GAP  = new Color(8,   6,   18);
    private static final Color CARD_BG   = new Color(24,  22,  46);
    private static final Color CARD_BORDER = new Color(58, 48, 95);

    // ─── Fonts ────────────────────────────────────────────────────────────────
    private static final String FONT_FAMILY;
    private static final Font FONT_TITLE, FONT_BODY, FONT_SMALL, FONT_BTN, FONT_SECTION;
    static {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Set<String> avail = new HashSet<>(Arrays.asList(ge.getAvailableFontFamilyNames()));
        String base = "SansSerif";
        for (String n : new String[]{"SF Pro Display","Inter","Segoe UI","Ubuntu","Helvetica Neue"}) {
            if (avail.contains(n)) { base = n; break; }
        }
        FONT_FAMILY  = base;
        FONT_TITLE   = new Font(base, Font.BOLD,  30);
        FONT_BODY    = new Font(base, Font.PLAIN, 14);
        FONT_SMALL   = new Font(base, Font.PLAIN, 13);
        FONT_BTN     = new Font(base, Font.BOLD,  13);
        FONT_SECTION = new Font(base, Font.BOLD,  12);
    }

    // ─── Game State ───────────────────────────────────────────────────────────
    private static final char EMPTY = TicTacToeGame.EMPTY;
    private TicTacToeGame game;
    private int           size, winLength;
    private boolean       vsComputer, gameOver;
    private int playerXWins, playerOWins, ties;

    // ─── GUI ──────────────────────────────────────────────────────────────────
    private JFrame       frame;
    private CardLayout   cards;
    private JPanel       rootPanel;
    private CellButton[][] cellButtons;
    private JLabel       statusLabel, scoreLabel;

    // ═══════════════════════════════════════════════════════════════════════════
    //  SOUND
    // ═══════════════════════════════════════════════════════════════════════════
    private static void playTones(int[] hz, int[] ms, double vol) {
        Thread t = new Thread(() -> {
            try {
                int sr = 44100;
                for (int i = 0; i < hz.length; i++) {
                    int n    = sr * ms[i] / 1000;
                    int fade = Math.min(n / 5, sr / 50);
                    byte[] buf = new byte[n * 2];
                    for (int j = 0; j < n; j++) {
                        double env = j < fade      ? (double) j / fade
                                   : j > n - fade ? (double)(n - j) / fade : 1.0;
                        double angle = 2.0 * Math.PI * hz[i] * j / sr;
                        short  s    = (short)(vol * env * 32767 * Math.sin(angle));
                        buf[2*j]   = (byte)(s & 0xff);
                        buf[2*j+1] = (byte)((s >> 8) & 0xff);
                    }
                    AudioFormat fmt = new AudioFormat(sr, 16, 1, true, false);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                    try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                        line.open(fmt, buf.length);
                        line.start();
                        line.write(buf, 0, buf.length);
                        line.drain();
                    }
                }
            } catch (Exception ignored) {}
        });
        t.setDaemon(true);
        t.start();
    }

    private static void soundX()   { playTones(new int[]{750, 1050}, new int[]{55, 55},           0.30); }
    private static void soundO()   { playTones(new int[]{520,  400}, new int[]{55, 55},           0.30); }
    private static void soundWin() { playTones(new int[]{523, 659, 784, 1047}, new int[]{95,95,95,200}, 0.36); }
    private static void soundTie() { playTones(new int[]{440, 370, 310}, new int[]{110,110,200},  0.26); }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CELL BUTTON — draws X (two lines) and O (circle) with Graphics2D
    // ═══════════════════════════════════════════════════════════════════════════
    private class CellButton extends JButton {
        char    symbol  = EMPTY;
        boolean winning = false;

        CellButton(int row, int col, int sz) {
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(sz, sz));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { repaint(); }
                public void mouseExited (MouseEvent e) { repaint(); }
            });
            addActionListener(e -> handleCellClick(row, col));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            boolean hovered = getModel().isRollover() && symbol == EMPTY && !gameOver;
            Color bg = winning ? CELL_WIN : hovered ? CELL_HVR : CELL_BG;
            g2.setColor(bg);
            g2.fillRoundRect(1, 1, w-2, h-2, 16, 16);

            if (symbol != EMPTY) {
                int   pad = w / 5;
                float sw  = Math.max(3.5f, w / 10f);
                g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Color col = winning ? WIN_FG : (symbol == 'X' ? X_COLOR : O_COLOR);
                g2.setColor(col);
                if (symbol == 'X') {
                    g2.drawLine(pad, pad, w-pad, h-pad);
                    g2.drawLine(w-pad, pad, pad, h-pad);
                } else {
                    g2.drawOval(pad, pad, w-2*pad, h-2*pad);
                }
            }
            g2.dispose();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  LAUNCH
    // ═══════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TicTacToeAdvanced().launch());
    }

    private void launch() {
        frame = new JFrame("Ultimate Tic Tac Toe");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);

        cards     = new CardLayout();
        rootPanel = new JPanel(cards);
        rootPanel.add(buildMenuPanel(), "MENU");

        frame.add(rootPanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MENU SCREEN
    // ═══════════════════════════════════════════════════════════════════════════
    private JPanel buildMenuPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, new Color(18, 14, 38), 0, getHeight(), new Color(10, 8, 22)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setBorder(BorderFactory.createEmptyBorder(28, 40, 24, 40));
        panel.setPreferredSize(new Dimension(500, 700));

        // ────────────────────────────────────────────────────────────────
        //  TITLE BLOCK
        // ────────────────────────────────────────────────────────────────
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);

        JLabel superTitle = new JLabel("ULTIMATE", SwingConstants.CENTER);
        superTitle.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
        superTitle.setForeground(new Color(130, 100, 230));
        superTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        superTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));

        JLabel mainTitle = new JLabel("Tic Tac Toe", SwingConstants.CENTER);
        mainTitle.setFont(FONT_TITLE);
        mainTitle.setForeground(ACCENT);
        mainTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Mini decorative board
        JPanel miniBoard = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cs = 18, gap = 3;
                int bw = cs * 3 + gap * 2;
                int bx = (getWidth() - bw) / 2, by = 0;
                for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) {
                    g2.setColor(new Color(34, 32, 62, 100));
                    g2.fillRoundRect(bx + c*(cs+gap), by + r*(cs+gap), cs, cs, 5, 5);
                }
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int p = 4;
                // X at (0,0)
                g2.setColor(new Color(255, 80, 80, 160));
                drawMiniX(g2, bx, by, cs, p);
                // O at (0,2)
                g2.setColor(new Color(48, 190, 255, 160));
                drawMiniO(g2, bx + 2*(cs+gap), by, cs, p);
                // X at (1,1)
                g2.setColor(new Color(255, 80, 80, 160));
                drawMiniX(g2, bx + (cs+gap), by + (cs+gap), cs, p);
                // O at (2,1)
                g2.setColor(new Color(48, 190, 255, 160));
                drawMiniO(g2, bx + (cs+gap), by + 2*(cs+gap), cs, p);
                // X at (2,2)
                g2.setColor(new Color(255, 80, 80, 160));
                drawMiniX(g2, bx + 2*(cs+gap), by + 2*(cs+gap), cs, p);
                g2.dispose();
            }
            private void drawMiniX(Graphics2D g2, int x, int y, int s, int p) {
                g2.drawLine(x+p, y+p, x+s-p, y+s-p);
                g2.drawLine(x+s-p, y+p, x+p, y+s-p);
            }
            private void drawMiniO(Graphics2D g2, int x, int y, int s, int p) {
                g2.drawOval(x+p, y+p, s-2*p, s-2*p);
            }
        };
        miniBoard.setOpaque(false);
        miniBoard.setPreferredSize(new Dimension(80, 60));
        miniBoard.setMaximumSize(new Dimension(80, 60));
        miniBoard.setAlignmentX(Component.CENTER_ALIGNMENT);

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(70, 55, 120));
        sep.setMaximumSize(new Dimension(260, 1));
        sep.setAlignmentX(Component.CENTER_ALIGNMENT);

        titleBlock.add(superTitle);
        titleBlock.add(mainTitle);
        titleBlock.add(Box.createVerticalStrut(10));
        titleBlock.add(miniBoard);
        titleBlock.add(Box.createVerticalStrut(14));
        titleBlock.add(sep);
        titleBlock.add(Box.createVerticalStrut(16));
        panel.add(titleBlock, BorderLayout.NORTH);

        // ────────────────────────────────────────────────────────────────
        //  CENTER — Rules Card + Setup Card
        // ────────────────────────────────────────────────────────────────
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        // ── Rules Card ──
        JPanel rulesContent = new JPanel();
        rulesContent.setLayout(new BoxLayout(rulesContent, BoxLayout.Y_AXIS));
        rulesContent.setOpaque(false);

        rulesContent.add(cardSectionLabel("HOW TO PLAY"));
        rulesContent.add(Box.createVerticalStrut(6));
        rulesContent.add(ruleLine("Click a cell to place your symbol (X or O)"));
        rulesContent.add(ruleLine("Align 'Win Length' symbols in a row to win"));
        rulesContent.add(ruleLine("Full board with no winner is a tie"));
        rulesContent.add(Box.createVerticalStrut(10));

        rulesContent.add(cardSectionLabel("CONTROLS"));
        rulesContent.add(Box.createVerticalStrut(4));
        rulesContent.add(controlLine("Undo",     "remove last move"));
        rulesContent.add(controlLine("Redo",     "reapply undone move"));
        rulesContent.add(controlLine("New Game", "restart same settings"));
        rulesContent.add(controlLine("Menu",     "change settings"));

        center.add(wrapInCard(rulesContent));
        center.add(Box.createVerticalStrut(14));

        // ── Setup Card ──
        JPanel setupContent = new JPanel();
        setupContent.setLayout(new BoxLayout(setupContent, BoxLayout.Y_AXIS));
        setupContent.setOpaque(false);

        setupContent.add(cardSectionLabel("GAME SETUP"));
        setupContent.add(Box.createVerticalStrut(10));

        // Value holders for steppers
        final int[] sizeVal = {3};
        final int[] winVal  = {3};
        final int[] winMax  = {3};  // mutable max for win length

        JLabel sizeValLabel = new JLabel("3", SwingConstants.CENTER);
        JLabel winValLabel  = new JLabel("3", SwingConstants.CENTER);
        styleBigValueLabel(sizeValLabel);
        styleBigValueLabel(winValLabel);

        // Board size stepper
        JButton sizeDown = circleBtn("\u2212");
        JButton sizeUp   = circleBtn("+");
        sizeDown.addActionListener(e -> {
            if (sizeVal[0] > 3) {
                sizeVal[0]--;
                sizeValLabel.setText(String.valueOf(sizeVal[0]));
                winMax[0] = sizeVal[0];
                if (winVal[0] > winMax[0]) {
                    winVal[0] = winMax[0];
                    winValLabel.setText(String.valueOf(winVal[0]));
                }
            }
        });
        sizeUp.addActionListener(e -> {
            if (sizeVal[0] < 10) {
                sizeVal[0]++;
                sizeValLabel.setText(String.valueOf(sizeVal[0]));
                winMax[0] = sizeVal[0];
            }
        });

        JPanel sizeRow = stepperRow("Board Size", sizeDown, sizeValLabel, sizeUp);
        setupContent.add(sizeRow);
        setupContent.add(Box.createVerticalStrut(8));

        // Win length stepper
        JButton winDown = circleBtn("\u2212");
        JButton winUp   = circleBtn("+");
        winDown.addActionListener(e -> {
            if (winVal[0] > 3) {
                winVal[0]--;
                winValLabel.setText(String.valueOf(winVal[0]));
            }
        });
        winUp.addActionListener(e -> {
            if (winVal[0] < winMax[0]) {
                winVal[0]++;
                winValLabel.setText(String.valueOf(winVal[0]));
            }
        });

        JPanel winRow = stepperRow("Win Length", winDown, winValLabel, winUp);
        setupContent.add(winRow);
        setupContent.add(Box.createVerticalStrut(14));

        // Mode toggle
        final boolean[] isPvP = {true};
        Color activeCol   = new Color(88, 58, 210);
        Color inactiveCol = new Color(36, 34, 65);

        JButton pvpBtn = toggleSegment("Player vs Player",    activeCol);
        JButton pvcBtn = toggleSegment("Player vs Computer",  inactiveCol);

        pvpBtn.addActionListener(e -> {
            isPvP[0] = true;
            pvpBtn.setBackground(activeCol);
            pvcBtn.setBackground(inactiveCol);
            pvpBtn.repaint();
            pvcBtn.repaint();
        });
        pvcBtn.addActionListener(e -> {
            isPvP[0] = false;
            pvcBtn.setBackground(activeCol);
            pvpBtn.setBackground(inactiveCol);
            pvpBtn.repaint();
            pvcBtn.repaint();
        });

        JPanel modeRow = new JPanel(new GridLayout(1, 2, 4, 0));
        modeRow.setOpaque(false);
        modeRow.setMaximumSize(new Dimension(420, 38));
        modeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeRow.add(pvpBtn);
        modeRow.add(pvcBtn);
        setupContent.add(modeRow);

        center.add(wrapInCard(setupContent));
        panel.add(center, BorderLayout.CENTER);

        // ────────────────────────────────────────────────────────────────
        //  START BUTTON
        // ────────────────────────────────────────────────────────────────
        JButton startBtn = roundButton("Start Game", new Color(88, 58, 210));
        startBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 17));
        startBtn.setBorder(BorderFactory.createEmptyBorder(12, 44, 12, 44));
        startBtn.addActionListener(e -> {
            size       = sizeVal[0];
            winLength  = winVal[0];
            vsComputer = !isPvP[0];
            startNewGame();
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.setOpaque(false);
        south.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));
        south.add(startBtn);
        panel.add(south, BorderLayout.SOUTH);

        return panel;
    }

    // ── Menu helper: wrap content in a rounded card ──
    private JPanel wrapInCard(JPanel content) {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 18, 18);
                g2.setColor(CARD_BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 18, 18);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(content);
        return card;
    }

    // ── Menu helper: section label inside a card ──
    private JLabel cardSectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_SECTION);
        lbl.setForeground(new Color(130, 105, 210));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    // ── Menu helper: rule bullet line ──
    private JPanel ruleLine(String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 1));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(420, 22));

        JPanel dot = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT);
                g2.fillOval(3, 5, 7, 7);
                g2.dispose();
            }
        };
        dot.setOpaque(false);
        dot.setPreferredSize(new Dimension(16, 18));

        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_BODY);
        lbl.setForeground(new Color(200, 200, 228));
        row.add(dot);
        row.add(lbl);
        return row;
    }

    // ── Menu helper: control key-value line ──
    private JPanel controlLine(String key, String desc) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 1));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(420, 22));

        JLabel kLbl = new JLabel(key);
        kLbl.setFont(FONT_BTN);
        kLbl.setForeground(ACCENT);
        kLbl.setPreferredSize(new Dimension(80, 18));

        JLabel vLbl = new JLabel(desc);
        vLbl.setFont(FONT_SMALL);
        vLbl.setForeground(SUBTEXT);

        row.add(kLbl);
        row.add(vLbl);
        return row;
    }

    // ── Menu helper: stepper row  "Label   [ - ]  val  [ + ]" ──
    private JPanel stepperRow(String label, JButton down, JLabel valLbl, JButton up) {
        JPanel row = new JPanel(new BorderLayout(0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(420, 36));

        JLabel lbl = new JLabel(label);
        lbl.setFont(FONT_BODY);
        lbl.setForeground(TEXT);
        row.add(lbl, BorderLayout.WEST);

        JPanel stepper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        stepper.setOpaque(false);
        stepper.add(down);
        stepper.add(valLbl);
        stepper.add(up);
        row.add(stepper, BorderLayout.EAST);

        return row;
    }

    // ── Menu helper: value label between stepper buttons ──
    private void styleBigValueLabel(JLabel lbl) {
        lbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 20));
        lbl.setForeground(ACCENT);
        lbl.setPreferredSize(new Dimension(32, 32));
        lbl.setHorizontalAlignment(SwingConstants.CENTER);
    }

    // ── Menu helper: small circular +/− button ──
    private JButton circleBtn(String symbol) {
        Color bg    = new Color(44, 42, 72);
        Color hover = new Color(62, 58, 100);
        JButton btn = new JButton(symbol) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillOval(0, 0, getWidth()-1, getHeight()-1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        btn.setForeground(TEXT);
        btn.setBackground(bg);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(32, 32));
        btn.setHorizontalAlignment(SwingConstants.CENTER);
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(hover); btn.repaint(); }
            public void mouseExited (MouseEvent e) { btn.setBackground(bg);    btn.repaint(); }
        });
        return btn;
    }

    // ── Menu helper: mode toggle segment button ──
    private JButton toggleSegment(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FONT_BTN);
        btn.setForeground(TEXT);
        btn.setBackground(bg);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        return btn;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  GAME SCREEN
    // ═══════════════════════════════════════════════════════════════════════════
    private void startNewGame() {
        game = new TicTacToeGame(size, winLength);
        gameOver      = false;

        JPanel gamePanel = buildGamePanel();
        rootPanel.add(gamePanel, "GAME");
        cards.show(rootPanel, "GAME");
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    private JPanel buildGamePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, new Color(16, 12, 32), 0, getHeight(), new Color(10, 8, 22)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 16, 20));

        // Header
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        JLabel titleLbl = new JLabel("Tic Tac Toe", SwingConstants.CENTER);
        titleLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 22));
        titleLbl.setForeground(ACCENT);
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        String modeStr = vsComputer ? "vs Computer" : "2 Players";
        JLabel subtitleLbl = new JLabel(size + " \u00d7 " + size + "  \u00b7  Win " + winLength + "  \u00b7  " + modeStr,
            SwingConstants.CENTER);
        subtitleLbl.setFont(FONT_SMALL);
        subtitleLbl.setForeground(SUBTEXT);
        subtitleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        header.add(titleLbl);
        header.add(Box.createVerticalStrut(3));
        header.add(subtitleLbl);
        panel.add(header, BorderLayout.NORTH);

        // Board
        int cellSize = Math.max(64, Math.min(94, 470 / size));
        JPanel boardPanel = new JPanel(new GridLayout(size, size, 5, 5));
        boardPanel.setBackground(GRID_GAP);
        boardPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 50, 100), 2),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        cellButtons = new CellButton[size][size];
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                cellButtons[r][c] = new CellButton(r, c, cellSize);
                boardPanel.add(cellButtons[r][c]);
            }
        }

        JPanel boardWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        boardWrap.setOpaque(false);
        boardWrap.add(boardPanel);
        panel.add(boardWrap, BorderLayout.CENTER);

        // South
        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.setOpaque(false);
        south.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 17));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        refreshStatus();
        south.add(statusLabel, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        controls.setOpaque(false);

        JButton undoBtn    = roundButton("Undo",     BTN_DARK);
        JButton redoBtn    = roundButton("Redo",     BTN_DARK);
        JButton newGameBtn = roundButton("New Game", new Color(48, 82, 148));
        JButton menuBtn    = roundButton("Menu",     new Color(70, 48, 120));

        undoBtn.addActionListener(e -> { if (game.canUndo()) { undoMove(); refreshBoard(); } });
        redoBtn.addActionListener(e -> { if (game.canRedo()) { redoMove(); refreshBoard(); } });
        newGameBtn.addActionListener(e -> startNewGame());
        menuBtn.addActionListener(e -> cards.show(rootPanel, "MENU"));

        controls.add(undoBtn);
        controls.add(redoBtn);
        controls.add(newGameBtn);
        controls.add(menuBtn);
        south.add(controls, BorderLayout.CENTER);

        scoreLabel = new JLabel("", SwingConstants.CENTER);
        scoreLabel.setFont(FONT_SMALL);
        scoreLabel.setForeground(SUBTEXT);
        scoreLabel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        refreshScore();
        south.add(scoreLabel, BorderLayout.SOUTH);

        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    // ─── Click handler ────────────────────────────────────────────────────────
    private void handleCellClick(int r, int c) {
        if (gameOver || game.getCell(r, c) != EMPTY) return;
        if (vsComputer && game.getCurrentPlayer() == 'O') return;

        placeAndRecord(r, c, game.getCurrentPlayer());
        refreshBoard();
        if (checkEnd(r, c)) return;

        game.switchPlayer();
        refreshStatus();

        if (vsComputer && game.getCurrentPlayer() == 'O') {
            javax.swing.Timer t = new javax.swing.Timer(300, e -> doComputerMove());
            t.setRepeats(false);
            t.start();
        }
    }

    private void doComputerMove() {
        int[] best = game.findBestMove(Math.min(5, size * size));
        if (best[0] < 0) return;
        placeAndRecord(best[0], best[1], 'O');
        refreshBoard();
        if (!checkEnd(best[0], best[1])) {
            game.switchPlayer();
            refreshStatus();
        }
    }

    private void placeAndRecord(int r, int c, char player) {
        game.placeMove(r, c, player);
        if (player == 'X') soundX(); else soundO();
    }

    private boolean checkEnd(int r, int c) {
        if (game.hasWon(game.getCurrentPlayer(), r, c)) {
            gameOver = true;
            game.setGameOver(true);
            if (game.getCurrentPlayer() == 'X') playerXWins++; else playerOWins++;
            refreshScore();
            highlightWinningCells();
            soundWin();
            String who = (vsComputer && game.getCurrentPlayer() == 'O') ? "Computer (O)" : "Player " + game.getCurrentPlayer();
            statusLabel.setText(who + " wins!");
            statusLabel.setForeground(WIN_FG);
            return true;
        }
        if (game.isBoardFull()) {
            gameOver = true;
            game.setGameOver(true);
            ties++;
            refreshScore();
            soundTie();
            statusLabel.setText("It's a tie!");
            statusLabel.setForeground(TIE_COL);
            return true;
        }
        return false;
    }

    // ─── Undo / Redo ─────────────────────────────────────────────────────────
    private void undoMove() {
        game.undoMove();
        gameOver = false;
        refreshStatus();
    }

    private void redoMove() {
        game.redoMove();
        gameOver = false;
        refreshStatus();
    }

    private void highlightWinningCells() {
        for (int[] cell : game.getWinningCells(game.getCurrentPlayer())) {
            cellButtons[cell[0]][cell[1]].winning = true;
            cellButtons[cell[0]][cell[1]].repaint();
        }
    }

    // ─── UI refresh ──────────────────────────────────────────────────────────
    private void refreshBoard() {
        for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) {
            cellButtons[r][c].symbol  = game.getCell(r, c);
            cellButtons[r][c].winning = false;
            cellButtons[r][c].repaint();
        }
    }

    private void refreshStatus() {
        if (gameOver) return;
        if (vsComputer && game.getCurrentPlayer() == 'O') {
            statusLabel.setText("Computer is thinking\u2026");
            statusLabel.setForeground(O_COLOR);
        } else {
            statusLabel.setText("Player " + game.getCurrentPlayer() + "'s turn");
            statusLabel.setForeground(game.getCurrentPlayer() == 'X' ? X_COLOR : O_COLOR);
        }
    }

    private void refreshScore() {
        if (scoreLabel == null) return;
        scoreLabel.setText(
            "X  " + playerXWins + "  win" + (playerXWins == 1 ? "" : "s") +
            "     O  " + playerOWins + "  win" + (playerOWins == 1 ? "" : "s") +
            "     Ties  " + ties
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SHARED STYLED HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    private JButton roundButton(String text, Color bg) {
        Color hover = bg.brighter();
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FONT_BTN);
        btn.setForeground(TEXT);
        btn.setBackground(bg);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(7, 18, 7, 18));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(hover); btn.repaint(); }
            public void mouseExited (MouseEvent e) { btn.setBackground(bg);    btn.repaint(); }
        });
        return btn;
    }
}
