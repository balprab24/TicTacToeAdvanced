import javax.swing.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

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

    // ─── Fonts ────────────────────────────────────────────────────────────────
    private static Font FONT_TITLE, FONT_BODY, FONT_SMALL, FONT_BTN, FONT_SECTION;
    static {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Set<String> avail = new HashSet<>(Arrays.asList(ge.getAvailableFontFamilyNames()));
        String base = "SansSerif";
        for (String n : new String[]{"SF Pro Display","Inter","Segoe UI","Ubuntu","Helvetica Neue"}) {
            if (avail.contains(n)) { base = n; break; }
        }
        FONT_TITLE   = new Font(base, Font.BOLD,  30);
        FONT_BODY    = new Font(base, Font.PLAIN, 14);
        FONT_SMALL   = new Font(base, Font.PLAIN, 13);
        FONT_BTN     = new Font(base, Font.BOLD,  13);
        FONT_SECTION = new Font(base, Font.BOLD,  14);
    }

    // ─── Game State ───────────────────────────────────────────────────────────
    private static final char EMPTY = ' ';
    private char[][]      board;
    private int           size, winLength;
    private char          currentPlayer;
    private boolean       vsComputer, gameOver;
    private final Stack<int[]> undoStack = new Stack<>();  // {row, col, (int)player}
    private final Stack<int[]> redoStack = new Stack<>();
    private int playerXWins, playerOWins, ties;

    // ─── GUI ──────────────────────────────────────────────────────────────────
    private JFrame       frame;
    private CardLayout   cards;
    private JPanel       rootPanel;
    private CellButton[][] cellButtons;
    private JLabel       statusLabel, scoreLabel;

    // ═══════════════════════════════════════════════════════════════════════════
    //  SOUND  — pure sine-wave synthesis, plays on a daemon thread
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
                        double env = j < fade       ? (double) j / fade
                                   : j > n - fade  ? (double)(n - j) / fade : 1.0;
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

            // Background
            boolean hovered = getModel().isRollover() && symbol == EMPTY && !gameOver;
            Color bg = winning ? CELL_WIN : hovered ? CELL_HVR : CELL_BG;
            g2.setColor(bg);
            g2.fillRoundRect(1, 1, w-2, h-2, 16, 16);

            // Symbol
            if (symbol != EMPTY) {
                int   pad = w / 5;
                float sw  = Math.max(3.5f, w / 10f);
                g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Color col = winning ? WIN_FG : (symbol == 'X' ? X_COLOR : O_COLOR);
                g2.setColor(col);
                if (symbol == 'X') {
                    g2.drawLine(pad,   pad,   w-pad, h-pad);
                    g2.drawLine(w-pad, pad,   pad,   h-pad);
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
        // Gradient background
        JPanel panel = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, new Color(18, 14, 38), 0, getHeight(), new Color(10, 8, 22)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setBorder(BorderFactory.createEmptyBorder(32, 48, 32, 48));
        panel.setPreferredSize(new Dimension(510, 650));

        // ── Title block ──
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);

        JLabel superTitle = new JLabel("ULTIMATE", SwingConstants.CENTER);
        superTitle.setFont(new Font(FONT_TITLE.getFamily(), Font.BOLD, 13));
        superTitle.setForeground(new Color(140, 110, 240));
        superTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        superTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));

        JLabel mainTitle = new JLabel("Tic Tac Toe", SwingConstants.CENTER);
        mainTitle.setFont(FONT_TITLE);
        mainTitle.setForeground(ACCENT);
        mainTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Thin divider
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(80, 60, 130));
        sep.setMaximumSize(new Dimension(300, 1));
        sep.setAlignmentX(Component.CENTER_ALIGNMENT);

        titleBlock.add(superTitle);
        titleBlock.add(mainTitle);
        titleBlock.add(Box.createVerticalStrut(12));
        titleBlock.add(sep);
        titleBlock.add(Box.createVerticalStrut(20));
        panel.add(titleBlock, BorderLayout.NORTH);

        // ── Center: Rules + Setup ──
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        center.add(sectionLabel("HOW TO PLAY"));
        JTextArea rules = new JTextArea(
            "  • Click a cell to place your symbol (X or O).\n" +
            "  • First to align 'Win Length' symbols in a row,\n" +
            "    column, or diagonal wins the round.\n" +
            "  • A full board with no winner is a tie.\n\n" +
            "  Undo      remove your last move\n" +
            "  Redo      reapply an undone move\n" +
            "  New Game  restart with the same settings\n" +
            "  Menu      return here to change settings"
        );
        rules.setEditable(false);
        rules.setFocusable(false);
        rules.setFont(FONT_BODY);
        rules.setForeground(new Color(200, 200, 225));
        rules.setBackground(new Color(30, 28, 55));
        rules.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(70, 58, 110), 1),
            BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
        rules.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(rules);
        center.add(Box.createVerticalStrut(24));

        center.add(sectionLabel("GAME SETUP"));

        SpinnerNumberModel sizeModel = new SpinnerNumberModel(3, 3, 10, 1);
        SpinnerNumberModel winModel  = new SpinnerNumberModel(3, 3, 3,  1);
        JSpinner sizeSpinner = styledSpinner(sizeModel);
        JSpinner winSpinner  = styledSpinner(winModel);
        sizeSpinner.addChangeListener(e -> {
            int s = (int) sizeSpinner.getValue();
            winModel.setMaximum(s);
            if ((int) winSpinner.getValue() > s) winSpinner.setValue(s);
        });

        String[] modes = {"Player vs Player", "Player vs Computer"};
        JComboBox<String> modeBox = new JComboBox<>(modes);
        styleComboBox(modeBox);

        JPanel setupGrid = new JPanel(new GridLayout(3, 2, 12, 12));
        setupGrid.setOpaque(false);
        setupGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        setupGrid.setMaximumSize(new Dimension(440, 140));
        setupGrid.add(menuLabel("Board Size  (3 – 10)"));  setupGrid.add(sizeSpinner);
        setupGrid.add(menuLabel("Win Length  (3 – size)")); setupGrid.add(winSpinner);
        setupGrid.add(menuLabel("Game Mode"));             setupGrid.add(modeBox);
        center.add(setupGrid);
        panel.add(center, BorderLayout.CENTER);

        // ── Start button ──
        JButton startBtn = roundButton("Start Game", new Color(88, 58, 210));
        startBtn.setFont(new Font(FONT_BTN.getFamily(), Font.BOLD, 16));
        startBtn.setPreferredSize(new Dimension(180, 46));
        startBtn.addActionListener(e -> {
            size       = (int) sizeSpinner.getValue();
            winLength  = (int) winSpinner.getValue();
            vsComputer = modeBox.getSelectedIndex() == 1;
            startNewGame();
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.setOpaque(false);
        south.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
        south.add(startBtn);
        panel.add(south, BorderLayout.SOUTH);

        return panel;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  GAME SCREEN
    // ═══════════════════════════════════════════════════════════════════════════
    private void startNewGame() {
        board = new char[size][size];
        for (char[] row : board) Arrays.fill(row, EMPTY);
        currentPlayer = 'X';
        gameOver      = false;
        undoStack.clear();
        redoStack.clear();

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

        // ── Header: title + subtitle ──
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        JLabel titleLbl = new JLabel("Tic Tac Toe", SwingConstants.CENTER);
        titleLbl.setFont(new Font(FONT_TITLE.getFamily(), Font.BOLD, 22));
        titleLbl.setForeground(ACCENT);
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        String modeStr = vsComputer ? "vs Computer" : "2 Players";
        JLabel subtitleLbl = new JLabel(size + " × " + size + "  ·  Win " + winLength + "  ·  " + modeStr,
            SwingConstants.CENTER);
        subtitleLbl.setFont(FONT_SMALL);
        subtitleLbl.setForeground(SUBTEXT);
        subtitleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        header.add(titleLbl);
        header.add(Box.createVerticalStrut(3));
        header.add(subtitleLbl);
        panel.add(header, BorderLayout.NORTH);

        // ── Board ──
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

        // ── South: status + controls + score ──
        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.setOpaque(false);
        south.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font(FONT_TITLE.getFamily(), Font.BOLD, 17));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        refreshStatus();
        south.add(statusLabel, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        controls.setOpaque(false);

        JButton undoBtn    = roundButton("Undo",     BTN_DARK);
        JButton redoBtn    = roundButton("Redo",     BTN_DARK);
        JButton newGameBtn = roundButton("New Game", new Color(48, 82, 148));
        JButton menuBtn    = roundButton("Menu",     new Color(70, 48, 120));

        undoBtn.addActionListener(e -> { if (!undoStack.isEmpty()) { undoMove(); refreshBoard(); } });
        redoBtn.addActionListener(e -> { if (!redoStack.isEmpty()) { redoMove(); refreshBoard(); } });
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
        if (gameOver || board[r][c] != EMPTY) return;
        if (vsComputer && currentPlayer == 'O') return;

        placeAndRecord(r, c, currentPlayer);
        refreshBoard();
        if (checkEnd(r, c)) return;

        switchPlayer();
        refreshStatus();

        if (vsComputer && currentPlayer == 'O') {
            javax.swing.Timer t = new javax.swing.Timer(300, e -> doComputerMove());
            t.setRepeats(false);
            t.start();
        }
    }

    private void doComputerMove() {
        int[] best = minimax(Math.min(5, size * size), true, Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (best[0] < 0) return;
        placeAndRecord(best[0], best[1], 'O');
        refreshBoard();
        if (!checkEnd(best[0], best[1])) {
            switchPlayer();
            refreshStatus();
        }
    }

    private void placeAndRecord(int r, int c, char player) {
        board[r][c] = player;
        undoStack.push(new int[]{ r, c, player });
        redoStack.clear();
        if (player == 'X') soundX(); else soundO();
    }

    private boolean checkEnd(int r, int c) {
        if (hasWon(currentPlayer, r, c)) {
            gameOver = true;
            if (currentPlayer == 'X') playerXWins++; else playerOWins++;
            refreshScore();
            highlightWinningCells();
            soundWin();
            String who = (vsComputer && currentPlayer == 'O') ? "Computer (O)" : "Player " + currentPlayer;
            statusLabel.setText(who + " wins!");
            statusLabel.setForeground(WIN_FG);
            return true;
        }
        if (isBoardFull()) {
            gameOver = true;
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
        int[] last = undoStack.pop();
        board[last[0]][last[1]] = EMPTY;
        redoStack.push(last);
        currentPlayer = (char) last[2];
        gameOver = false;
        refreshStatus();
    }

    private void redoMove() {
        int[] move = redoStack.pop();
        board[move[0]][move[1]] = (char) move[2];
        undoStack.push(move);
        switchPlayer();
        gameOver = false;
        refreshStatus();
    }

    // ─── Minimax with alpha-beta pruning ─────────────────────────────────────
    private int[] minimax(int depth, boolean maximizing, int alpha, int beta) {
        if (hasWon('O'))              return new int[]{ -1, -1,  10 + depth };
        if (hasWon('X'))              return new int[]{ -1, -1, -10 - depth };
        List<int[]> moves = getAvailableMoves();
        if (moves.isEmpty() || depth == 0) return new int[]{ -1, -1, 0 };

        char   player = maximizing ? 'O' : 'X';
        int[]  best   = { -1, -1, maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE };

        for (int[] m : moves) {
            board[m[0]][m[1]] = player;
            int[] res = minimax(depth - 1, !maximizing, alpha, beta);
            board[m[0]][m[1]] = EMPTY;
            if (maximizing ? res[2] > best[2] : res[2] < best[2])
                best = new int[]{ m[0], m[1], res[2] };
            if (maximizing) alpha = Math.max(alpha, best[2]);
            else            beta  = Math.min(beta,  best[2]);
            if (beta <= alpha) break;
        }
        return best;
    }

    // ─── Win detection ───────────────────────────────────────────────────────
    private boolean hasWon(char p, int lr, int lc) {
        if (lr < 0 || lc < 0) return hasWon(p);
        return checkDir(p,lr,lc,1,0)||checkDir(p,lr,lc,0,1)||checkDir(p,lr,lc,1,1)||checkDir(p,lr,lc,1,-1);
    }
    private boolean hasWon(char p) {
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (board[r][c] == p && hasWon(p, r, c)) return true;
        return false;
    }
    private boolean checkDir(char p, int r, int c, int dr, int dc) {
        int cnt = 0;
        for (int i = 0; inBounds(r+dr*i, c+dc*i) && board[r+dr*i][c+dc*i] == p; i++) cnt++;
        for (int i = 1; inBounds(r-dr*i, c-dc*i) && board[r-dr*i][c-dc*i] == p; i++) cnt++;
        return cnt >= winLength;
    }

    private void highlightWinningCells() {
        int[][] dirs = {{1,0},{0,1},{1,1},{1,-1}};
        for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) {
            if (board[r][c] != currentPlayer) continue;
            for (int[] d : dirs) {
                List<int[]> cells = collectWinCells(currentPlayer, r, c, d[0], d[1]);
                if (cells != null) for (int[] cell : cells) {
                    cellButtons[cell[0]][cell[1]].winning = true;
                    cellButtons[cell[0]][cell[1]].repaint();
                }
            }
        }
    }

    private List<int[]> collectWinCells(char p, int r, int c, int dr, int dc) {
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; inBounds(r+dr*i,c+dc*i) && board[r+dr*i][c+dc*i] == p; i++) cells.add(new int[]{r+dr*i,c+dc*i});
        for (int i = 1; inBounds(r-dr*i,c-dc*i) && board[r-dr*i][c-dc*i] == p; i++) cells.add(new int[]{r-dr*i,c-dc*i});
        return cells.size() >= winLength ? cells : null;
    }

    // ─── Board helpers ───────────────────────────────────────────────────────
    private boolean inBounds(int r, int c) { return r >= 0 && r < size && c >= 0 && c < size; }
    private boolean isBoardFull() {
        for (char[] row : board) for (char ch : row) if (ch == EMPTY) return false;
        return true;
    }
    private void switchPlayer() { currentPlayer = currentPlayer == 'X' ? 'O' : 'X'; }
    private List<int[]> getAvailableMoves() {
        List<int[]> moves = new ArrayList<>();
        for (int r = 0; r < size; r++) for (int c = 0; c < size; c++)
            if (board[r][c] == EMPTY) moves.add(new int[]{r, c});
        return moves;
    }

    // ─── UI refresh ──────────────────────────────────────────────────────────
    private void refreshBoard() {
        for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) {
            cellButtons[r][c].symbol  = board[r][c];
            cellButtons[r][c].winning = false;
            cellButtons[r][c].repaint();
        }
    }

    private void refreshStatus() {
        if (gameOver) return;
        if (vsComputer && currentPlayer == 'O') {
            statusLabel.setText("Computer is thinking…");
            statusLabel.setForeground(O_COLOR);
        } else {
            statusLabel.setText("Player " + currentPlayer + "'s turn");
            statusLabel.setForeground(currentPlayer == 'X' ? X_COLOR : O_COLOR);
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
    //  STYLED UI HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    private JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_SECTION);
        lbl.setForeground(new Color(140, 110, 210));
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JLabel menuLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_BODY);
        lbl.setForeground(TEXT);
        return lbl;
    }

    private JSpinner styledSpinner(SpinnerNumberModel model) {
        JSpinner sp = new JSpinner(model);
        sp.setFont(FONT_BODY);
        JComponent ed = sp.getEditor();
        if (ed instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
            tf.setBackground(new Color(30, 28, 55));
            tf.setForeground(TEXT);
            tf.setCaretColor(TEXT);
        }
        return sp;
    }

    private void styleComboBox(JComboBox<String> box) {
        box.setFont(FONT_BODY);
        box.setBackground(new Color(30, 28, 55));
        box.setForeground(TEXT);
    }

    /** Rounded, custom-painted button. */
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
