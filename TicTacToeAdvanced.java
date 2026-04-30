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
    private static final Color HINT_BG   = new Color(88, 72, 24);
    private static final Color REPLAY_BG = new Color(30, 48, 78);
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
    private TicTacToeGame.Difficulty difficulty = TicTacToeGame.Difficulty.MEDIUM;
    private int           turnSeconds;
    private int           remainingSeconds;
    private boolean       aiThinking, replayMode;
    private int           replayIndex, gameVersion;
    private int           hintRow = -1, hintCol = -1;
    private int playerXWins, playerOWins, ties;
    private final java.util.List<MoveRecord> moveHistory = new ArrayList<>();
    private final java.util.List<MoveRecord> redoHistory = new ArrayList<>();

    // ─── GUI ──────────────────────────────────────────────────────────────────
    private JFrame       frame;
    private CardLayout   cards;
    private JPanel       rootPanel;
    private CellButton[][] cellButtons;
    private JLabel       statusLabel, scoreLabel, timerLabel;
    private JTextArea    historyArea;
    private JButton      undoBtn, redoBtn, hintBtn;
    private JButton      replayFirstBtn, replayPrevBtn, replayNextBtn, replayLatestBtn;
    private javax.swing.Timer turnTimer;
    private SwingWorker<int[], Void> aiWorker;
    private String       gameOverStatusText = "";
    private Color        gameOverStatusColor = WIN_FG;

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
        boolean hinted  = false;

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
            Color bg = winning ? CELL_WIN : hinted ? HINT_BG : replayMode ? REPLAY_BG : hovered ? CELL_HVR : CELL_BG;
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

    private static class MoveRecord {
        final int number;
        final int row;
        final int col;
        final char player;

        MoveRecord(int number, int row, int col, char player) {
            this.number = number;
            this.row = row;
            this.col = col;
            this.player = player;
        }

        int[] toArray() {
            return new int[] { row, col, player };
        }

        String label() {
            return number + ". " + player + "  r" + (row + 1) + " c" + (col + 1);
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
        panel.setPreferredSize(new Dimension(500, 760));

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

        final TicTacToeGame.Difficulty[] difficultyVal = {TicTacToeGame.Difficulty.MEDIUM};
        JButton easyBtn = toggleSegment("Easy", inactiveCol);
        JButton mediumBtn = toggleSegment("Medium", activeCol);
        JButton hardBtn = toggleSegment("Hard", inactiveCol);
        JButton[] difficultyBtns = { easyBtn, mediumBtn, hardBtn };
        TicTacToeGame.Difficulty[] difficultyValues = {
                TicTacToeGame.Difficulty.EASY,
                TicTacToeGame.Difficulty.MEDIUM,
                TicTacToeGame.Difficulty.HARD
        };

        Runnable refreshDifficultyBtns = () -> {
            boolean enabled = !isPvP[0];
            for (int i = 0; i < difficultyBtns.length; i++) {
                boolean selected = difficultyVal[0] == difficultyValues[i];
                difficultyBtns[i].setEnabled(enabled);
                difficultyBtns[i].setBackground(enabled && selected ? activeCol : inactiveCol);
                difficultyBtns[i].repaint();
            }
        };

        pvpBtn.addActionListener(e -> {
            isPvP[0] = true;
            pvpBtn.setBackground(activeCol);
            pvcBtn.setBackground(inactiveCol);
            pvpBtn.repaint();
            pvcBtn.repaint();
            refreshDifficultyBtns.run();
        });
        pvcBtn.addActionListener(e -> {
            isPvP[0] = false;
            pvcBtn.setBackground(activeCol);
            pvpBtn.setBackground(inactiveCol);
            pvpBtn.repaint();
            pvcBtn.repaint();
            refreshDifficultyBtns.run();
        });

        for (int i = 0; i < difficultyBtns.length; i++) {
            final int index = i;
            difficultyBtns[i].addActionListener(e -> {
                difficultyVal[0] = difficultyValues[index];
                refreshDifficultyBtns.run();
            });
        }
        refreshDifficultyBtns.run();

        JPanel modeRow = new JPanel(new GridLayout(1, 2, 4, 0));
        modeRow.setOpaque(false);
        modeRow.setMaximumSize(new Dimension(420, 38));
        modeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeRow.add(pvpBtn);
        modeRow.add(pvcBtn);
        setupContent.add(modeRow);
        setupContent.add(Box.createVerticalStrut(12));
        setupContent.add(cardSectionLabel("AI DIFFICULTY"));
        setupContent.add(Box.createVerticalStrut(6));

        JPanel difficultyRow = new JPanel(new GridLayout(1, 3, 4, 0));
        difficultyRow.setOpaque(false);
        difficultyRow.setMaximumSize(new Dimension(420, 36));
        difficultyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        difficultyRow.add(easyBtn);
        difficultyRow.add(mediumBtn);
        difficultyRow.add(hardBtn);
        setupContent.add(difficultyRow);
        setupContent.add(Box.createVerticalStrut(12));
        setupContent.add(cardSectionLabel("TURN TIMER"));
        setupContent.add(Box.createVerticalStrut(6));

        final int[] timerVal = {0};
        JButton timerOffBtn = toggleSegment("Off", activeCol);
        JButton timer15Btn = toggleSegment("15s", inactiveCol);
        JButton timer30Btn = toggleSegment("30s", inactiveCol);
        JButton timer60Btn = toggleSegment("60s", inactiveCol);
        JButton[] timerBtns = { timerOffBtn, timer15Btn, timer30Btn, timer60Btn };
        int[] timerValues = { 0, 15, 30, 60 };
        Runnable refreshTimerBtns = () -> {
            for (int i = 0; i < timerBtns.length; i++) {
                timerBtns[i].setBackground(timerVal[0] == timerValues[i] ? activeCol : inactiveCol);
                timerBtns[i].repaint();
            }
        };
        for (int i = 0; i < timerBtns.length; i++) {
            final int index = i;
            timerBtns[i].addActionListener(e -> {
                timerVal[0] = timerValues[index];
                refreshTimerBtns.run();
            });
        }

        JPanel timerRow = new JPanel(new GridLayout(1, 4, 4, 0));
        timerRow.setOpaque(false);
        timerRow.setMaximumSize(new Dimension(420, 36));
        timerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        timerRow.add(timerOffBtn);
        timerRow.add(timer15Btn);
        timerRow.add(timer30Btn);
        timerRow.add(timer60Btn);
        setupContent.add(timerRow);

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
            difficulty = difficultyVal[0];
            turnSeconds = timerVal[0];
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
        cancelComputerMove();
        stopTurnTimer();
        game = new TicTacToeGame(size, winLength);
        gameOver      = false;
        aiThinking    = false;
        replayMode    = false;
        replayIndex   = 0;
        hintRow       = -1;
        hintCol       = -1;
        gameOverStatusText = "";
        gameOverStatusColor = WIN_FG;
        remainingSeconds = turnSeconds;
        moveHistory.clear();
        redoHistory.clear();
        gameVersion++;

        JPanel gamePanel = buildGamePanel();
        rootPanel.add(gamePanel, "GAME");
        cards.show(rootPanel, "GAME");
        frame.pack();
        frame.setLocationRelativeTo(null);
        refreshHistory();
        refreshControls();
        resetTurnTimer();
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

        String modeStr = vsComputer ? "vs Computer  \u00b7  " + difficultyLabel() : "2 Players";
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
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        refreshStatus();
        timerLabel = new JLabel("", SwingConstants.CENTER);
        timerLabel.setFont(FONT_SMALL);
        timerLabel.setForeground(SUBTEXT);
        refreshTimerLabel();

        JPanel statusBlock = new JPanel();
        statusBlock.setLayout(new BoxLayout(statusBlock, BoxLayout.Y_AXIS));
        statusBlock.setOpaque(false);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusBlock.add(statusLabel);
        statusBlock.add(timerLabel);
        statusBlock.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        south.add(statusBlock, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        controls.setOpaque(false);

        undoBtn            = roundButton("Undo",     BTN_DARK);
        redoBtn            = roundButton("Redo",     BTN_DARK);
        hintBtn            = roundButton("Hint",     new Color(96, 78, 28));
        JButton newGameBtn = roundButton("New Game", new Color(48, 82, 148));
        JButton menuBtn    = roundButton("Menu",     new Color(70, 48, 120));

        undoBtn.addActionListener(e -> { if (game.canUndo()) { undoMove(); refreshBoard(); } });
        redoBtn.addActionListener(e -> { if (game.canRedo()) { redoMove(); refreshBoard(); } });
        hintBtn.addActionListener(e -> showHint());
        newGameBtn.addActionListener(e -> startNewGame());
        menuBtn.addActionListener(e -> {
            gameVersion++;
            cancelComputerMove();
            stopTurnTimer();
            clearHint();
            cards.show(rootPanel, "MENU");
        });

        controls.add(undoBtn);
        controls.add(redoBtn);
        controls.add(hintBtn);
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
        panel.add(buildHistoryPanel(), BorderLayout.EAST);
        return panel;
    }

    private JPanel buildHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
        panel.setPreferredSize(new Dimension(170, 0));

        JLabel title = cardSectionLabel("MOVE HISTORY");
        title.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        panel.add(title, BorderLayout.NORTH);

        historyArea = new JTextArea(10, 14);
        historyArea.setEditable(false);
        historyArea.setFocusable(false);
        historyArea.setFont(FONT_SMALL);
        historyArea.setForeground(TEXT);
        historyArea.setBackground(CARD_BG);
        historyArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scrollPane = new JScrollPane(historyArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
        scrollPane.getViewport().setBackground(CARD_BG);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel replayPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        replayPanel.setOpaque(false);
        replayFirstBtn = roundButton("|<", BTN_DARK);
        replayPrevBtn = roundButton("<", BTN_DARK);
        replayNextBtn = roundButton(">", BTN_DARK);
        replayLatestBtn = roundButton(">|", BTN_DARK);
        replayFirstBtn.addActionListener(e -> showReplayFrame(0));
        replayPrevBtn.addActionListener(e -> showReplayFrame(replayIndex - 1));
        replayNextBtn.addActionListener(e -> showReplayFrame(replayIndex + 1));
        replayLatestBtn.addActionListener(e -> showReplayFrame(moveHistory.size()));
        replayPanel.add(replayFirstBtn);
        replayPanel.add(replayPrevBtn);
        replayPanel.add(replayNextBtn);
        replayPanel.add(replayLatestBtn);
        panel.add(replayPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ─── Click handler ────────────────────────────────────────────────────────
    private void handleCellClick(int r, int c) {
        if (gameOver || aiThinking || replayMode || game.getCell(r, c) != EMPTY) return;
        if (vsComputer && game.getCurrentPlayer() == 'O') return;

        if (!placeAndRecord(r, c, game.getCurrentPlayer())) return;
        refreshBoard();
        if (checkEnd(r, c)) return;

        game.switchPlayer();
        refreshStatus();
        resetTurnTimer();

        if (vsComputer && game.getCurrentPlayer() == 'O') {
            beginComputerMove();
        }
    }

    private void beginComputerMove() {
        pauseTurnTimer();
        clearHint();
        aiThinking = true;
        refreshStatus();
        refreshControls();
        int workerVersion = gameVersion;
        TicTacToeGame thinkingGame = game;
        TicTacToeGame.Difficulty thinkingDifficulty = difficulty;
        aiWorker = new SwingWorker<int[], Void>() {
            @Override
            protected int[] doInBackground() {
                return thinkingGame.findBestMove(thinkingDifficulty);
            }

            @Override
            protected void done() {
                if (isCancelled() || workerVersion != gameVersion) {
                    return;
                }
                try {
                    int[] best = get();
                    aiThinking = false;
                    if (gameOver || replayMode || best[0] < 0) {
                        refreshStatus();
                        refreshControls();
                        resumeTurnTimer();
                        return;
                    }
                    if (placeAndRecord(best[0], best[1], 'O')) {
                        refreshBoard();
                        if (!checkEnd(best[0], best[1])) {
                            game.switchPlayer();
                            refreshStatus();
                            resetTurnTimer();
                        }
                    }
                } catch (Exception ignored) {
                    aiThinking = false;
                    refreshStatus();
                } finally {
                    refreshControls();
                    refreshTimerLabel();
                }
            }
        };
        aiWorker.execute();
    }

    private String difficultyLabel() {
        switch (difficulty) {
            case EASY:
                return "Easy";
            case HARD:
                return "Hard";
            case MEDIUM:
            default:
                return "Medium";
        }
    }

    private boolean placeAndRecord(int r, int c, char player) {
        if (!game.placeMove(r, c, player)) {
            return false;
        }
        clearHint();
        moveHistory.add(new MoveRecord(moveHistory.size() + 1, r, c, player));
        redoHistory.clear();
        replayIndex = moveHistory.size();
        replayMode = false;
        refreshHistory();
        refreshControls();
        if (player == 'X') soundX(); else soundO();
        return true;
    }

    private boolean checkEnd(int r, int c) {
        if (game.hasWon(game.getCurrentPlayer(), r, c)) {
            stopTurnTimer();
            gameOver = true;
            game.setGameOver(true);
            if (game.getCurrentPlayer() == 'X') playerXWins++; else playerOWins++;
            refreshScore();
            highlightWinningCells();
            soundWin();
            String who = (vsComputer && game.getCurrentPlayer() == 'O') ? "Computer (O)" : "Player " + game.getCurrentPlayer();
            gameOverStatusText = who + " wins!";
            gameOverStatusColor = WIN_FG;
            statusLabel.setText(gameOverStatusText);
            statusLabel.setForeground(gameOverStatusColor);
            refreshControls();
            return true;
        }
        if (game.isBoardFull()) {
            stopTurnTimer();
            gameOver = true;
            game.setGameOver(true);
            ties++;
            refreshScore();
            soundTie();
            gameOverStatusText = "It's a tie!";
            gameOverStatusColor = TIE_COL;
            statusLabel.setText(gameOverStatusText);
            statusLabel.setForeground(gameOverStatusColor);
            refreshControls();
            return true;
        }
        return false;
    }

    // ─── Undo / Redo ─────────────────────────────────────────────────────────
    private void undoMove() {
        if (aiThinking || replayMode) return;
        int count = game.undoTurn(vsComputer);
        if (count == 0) return;
        java.util.List<MoveRecord> undone = new ArrayList<>();
        for (int i = 0; i < count && !moveHistory.isEmpty(); i++) {
            undone.add(0, moveHistory.remove(moveHistory.size() - 1));
        }
        redoHistory.addAll(0, undone);
        gameOver = false;
        replayIndex = moveHistory.size();
        replayMode = false;
        clearHint();
        refreshHistory();
        refreshStatus();
        refreshControls();
        resetTurnTimer();
    }

    private void redoMove() {
        if (aiThinking || replayMode) return;
        int count = game.redoTurn(vsComputer);
        if (count == 0) return;
        for (int i = 0; i < count && !redoHistory.isEmpty(); i++) {
            MoveRecord move = redoHistory.remove(0);
            moveHistory.add(new MoveRecord(moveHistory.size() + 1, move.row, move.col, move.player));
        }
        gameOver = false;
        replayIndex = moveHistory.size();
        replayMode = false;
        clearHint();
        refreshHistory();
        refreshStatus();
        refreshControls();
        resetTurnTimer();
    }

    private void highlightWinningCells() {
        for (int[] cell : game.getWinningCells(game.getCurrentPlayer())) {
            cellButtons[cell[0]][cell[1]].winning = true;
            cellButtons[cell[0]][cell[1]].repaint();
        }
    }

    private void restoreWinningHighlight() {
        if (gameOver && game.hasWon(game.getCurrentPlayer())) {
            highlightWinningCells();
        }
    }

    // ─── UI refresh ──────────────────────────────────────────────────────────
    private void refreshBoard() {
        char[][] replayBoard = replayMode ? game.buildSnapshot(toMoveArrays(replayIndex)) : null;
        for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) {
            cellButtons[r][c].symbol  = replayMode ? replayBoard[r][c] : game.getCell(r, c);
            cellButtons[r][c].winning = false;
            cellButtons[r][c].hinted  = !replayMode && hintRow == r && hintCol == c;
            cellButtons[r][c].repaint();
        }
    }

    private void refreshStatus() {
        if (replayMode) {
            statusLabel.setText("Replay " + replayIndex + " / " + moveHistory.size());
            statusLabel.setForeground(SUBTEXT);
        } else if (gameOver) {
            statusLabel.setText(gameOverStatusText.isEmpty() ? "Game over" : gameOverStatusText);
            statusLabel.setForeground(gameOverStatusColor);
            return;
        } else if (aiThinking || (vsComputer && game.getCurrentPlayer() == 'O')) {
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

    private void showHint() {
        if (!canShowHint()) return;
        TicTacToeGame.Difficulty hintDifficulty = vsComputer ? difficulty : TicTacToeGame.Difficulty.HARD;
        int[] move = game.recommendMove(game.getCurrentPlayer(), hintDifficulty);
        if (move[0] < 0) return;
        hintRow = move[0];
        hintCol = move[1];
        statusLabel.setText("Hint: row " + (hintRow + 1) + ", column " + (hintCol + 1));
        statusLabel.setForeground(TIE_COL);
        refreshBoard();
    }

    private boolean canShowHint() {
        return hintBtn != null
                && !gameOver
                && !aiThinking
                && !replayMode
                && isHumanTurn()
                && !game.getAvailableMoves().isEmpty();
    }

    private boolean isHumanTurn() {
        return !vsComputer || game.getCurrentPlayer() == 'X';
    }

    private void clearHint() {
        hintRow = -1;
        hintCol = -1;
    }

    private void showReplayFrame(int index) {
        if (aiThinking || moveHistory.isEmpty()) return;
        clearHint();
        replayIndex = Math.max(0, Math.min(index, moveHistory.size()));
        replayMode = replayIndex < moveHistory.size();
        if (replayMode) {
            pauseTurnTimer();
        } else {
            resumeTurnTimer();
        }
        refreshBoard();
        if (!replayMode) {
            restoreWinningHighlight();
        }
        refreshHistory();
        refreshStatus();
        refreshControls();
        refreshTimerLabel();
    }

    private java.util.List<int[]> toMoveArrays(int count) {
        java.util.List<int[]> moves = new ArrayList<>();
        int limit = Math.max(0, Math.min(count, moveHistory.size()));
        for (int i = 0; i < limit; i++) {
            moves.add(moveHistory.get(i).toArray());
        }
        return moves;
    }

    private void refreshHistory() {
        if (historyArea == null) return;
        StringBuilder text = new StringBuilder();
        if (moveHistory.isEmpty()) {
            text.append("No moves yet.");
        } else {
            for (int i = 0; i < moveHistory.size(); i++) {
                if (replayMode && i == replayIndex) {
                    text.append("-- replay point --\n");
                }
                text.append(moveHistory.get(i).label()).append('\n');
            }
            if (replayMode && replayIndex == moveHistory.size()) {
                text.append("-- latest --\n");
            }
        }
        historyArea.setText(text.toString());
        historyArea.setCaretPosition(0);
    }

    private void refreshControls() {
        if (undoBtn != null) {
            undoBtn.setEnabled(!aiThinking && !replayMode && game != null && game.canUndo());
        }
        if (redoBtn != null) {
            redoBtn.setEnabled(!aiThinking && !replayMode && game != null && game.canRedo());
        }
        if (hintBtn != null) {
            hintBtn.setEnabled(canShowHint());
        }
        boolean replayReady = !aiThinking && !moveHistory.isEmpty();
        if (replayFirstBtn != null) {
            replayFirstBtn.setEnabled(replayReady && replayIndex > 0);
            replayPrevBtn.setEnabled(replayReady && replayIndex > 0);
            replayNextBtn.setEnabled(replayReady && replayIndex < moveHistory.size());
            replayLatestBtn.setEnabled(replayReady && replayIndex < moveHistory.size());
        }
    }

    private void resetTurnTimer() {
        stopTurnTimer();
        remainingSeconds = turnSeconds;
        resumeTurnTimer();
    }

    private void resumeTurnTimer() {
        stopTurnTimer();
        refreshTimerLabel();
        if (turnSeconds <= 0 || remainingSeconds <= 0 || gameOver || replayMode || aiThinking
                || (vsComputer && game.getCurrentPlayer() == 'O')) {
            return;
        }
        turnTimer = new javax.swing.Timer(1000, e -> {
            remainingSeconds--;
            refreshTimerLabel();
            if (remainingSeconds <= 0) {
                handleTurnTimeout();
            }
        });
        turnTimer.start();
    }

    private void pauseTurnTimer() {
        if (turnTimer != null) {
            turnTimer.stop();
            turnTimer = null;
        }
        refreshTimerLabel();
    }

    private void stopTurnTimer() {
        if (turnTimer != null) {
            turnTimer.stop();
            turnTimer = null;
        }
    }

    private void refreshTimerLabel() {
        if (timerLabel == null) return;
        if (turnSeconds <= 0) {
            timerLabel.setText("Timer Off");
        } else if (gameOver) {
            timerLabel.setText("Timer stopped");
        } else if (replayMode) {
            timerLabel.setText("Timer paused for replay");
        } else if (aiThinking || (vsComputer && game != null && game.getCurrentPlayer() == 'O')) {
            timerLabel.setText("Timer paused for computer");
        } else {
            timerLabel.setText("Timer: " + remainingSeconds + "s");
        }
    }

    private void handleTurnTimeout() {
        stopTurnTimer();
        if (gameOver || replayMode || aiThinking) return;
        char timedOut = game.getCurrentPlayer();
        char winner = game.getTimeoutWinner(timedOut);
        gameOver = true;
        game.setGameOver(true);
        if (winner == 'X') playerXWins++; else playerOWins++;
        refreshScore();
        clearHint();
        refreshBoard();
        refreshControls();
        soundWin();
        String loser = vsComputer && timedOut == 'O' ? "Computer (O)" : "Player " + timedOut;
        String winnerName = vsComputer && winner == 'O' ? "Computer (O)" : "Player " + winner;
        gameOverStatusText = loser + " ran out of time. " + winnerName + " wins!";
        gameOverStatusColor = WIN_FG;
        statusLabel.setText(gameOverStatusText);
        statusLabel.setForeground(gameOverStatusColor);
        refreshTimerLabel();
    }

    private void cancelComputerMove() {
        if (aiWorker != null && !aiWorker.isDone()) {
            aiWorker.cancel(true);
        }
        aiThinking = false;
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
