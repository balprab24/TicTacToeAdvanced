import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

public class TicTacToeGame {
    public static final char EMPTY = ' ';

    private final int size;
    private final int winLength;
    private final char[][] board;
    private final Stack<int[]> undoStack = new Stack<>();
    private final Stack<int[]> redoStack = new Stack<>();

    private char currentPlayer = 'X';
    private boolean gameOver;

    public TicTacToeGame(int size, int winLength) {
        if (size < 3 || size > 10) {
            throw new IllegalArgumentException("Board size must be between 3 and 10.");
        }
        if (winLength < 3 || winLength > size) {
            throw new IllegalArgumentException("Win length must be between 3 and the board size.");
        }
        this.size = size;
        this.winLength = winLength;
        this.board = new char[size][size];
        for (char[] row : board) {
            Arrays.fill(row, EMPTY);
        }
    }

    public int getSize() {
        return size;
    }

    public int getWinLength() {
        return winLength;
    }

    public char getCurrentPlayer() {
        return currentPlayer;
    }

    public char getCell(int row, int col) {
        requireInBounds(row, col);
        return board[row][col];
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }

    public boolean placeMove(int row, int col, char player) {
        requireValidPlayer(player);
        if (gameOver || !inBounds(row, col) || board[row][col] != EMPTY) {
            return false;
        }
        board[row][col] = player;
        undoStack.push(new int[] { row, col, player });
        redoStack.clear();
        return true;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public boolean undoMove() {
        if (undoStack.isEmpty()) {
            return false;
        }
        int[] last = undoStack.pop();
        board[last[0]][last[1]] = EMPTY;
        redoStack.push(last);
        currentPlayer = (char) last[2];
        gameOver = false;
        return true;
    }

    public boolean redoMove() {
        if (redoStack.isEmpty()) {
            return false;
        }
        int[] move = redoStack.pop();
        board[move[0]][move[1]] = (char) move[2];
        undoStack.push(move);
        switchPlayer();
        gameOver = false;
        return true;
    }

    public void switchPlayer() {
        currentPlayer = currentPlayer == 'X' ? 'O' : 'X';
    }

    public boolean hasWon(char player, int lastRow, int lastCol) {
        requireValidPlayer(player);
        if (lastRow < 0 || lastCol < 0) {
            return hasWon(player);
        }
        requireInBounds(lastRow, lastCol);
        return checkDirection(player, lastRow, lastCol, 1, 0)
                || checkDirection(player, lastRow, lastCol, 0, 1)
                || checkDirection(player, lastRow, lastCol, 1, 1)
                || checkDirection(player, lastRow, lastCol, 1, -1);
    }

    public boolean hasWon(char player) {
        requireValidPlayer(player);
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (board[row][col] == player && hasWon(player, row, col)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isBoardFull() {
        for (char[] row : board) {
            for (char cell : row) {
                if (cell == EMPTY) {
                    return false;
                }
            }
        }
        return true;
    }

    public List<int[]> getAvailableMoves() {
        List<int[]> moves = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (board[row][col] == EMPTY) {
                    moves.add(new int[] { row, col });
                }
            }
        }
        return moves;
    }

    public int[] findBestMove(int depth) {
        int cappedDepth = Math.max(0, Math.min(depth, size * size));
        return minimax(cappedDepth, true, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public List<int[]> getWinningCells(char player) {
        requireValidPlayer(player);
        int[][] directions = { { 1, 0 }, { 0, 1 }, { 1, 1 }, { 1, -1 } };
        List<int[]> winningCells = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (board[row][col] != player) {
                    continue;
                }
                for (int[] direction : directions) {
                    List<int[]> cells = collectWinCells(player, row, col, direction[0], direction[1]);
                    if (cells != null) {
                        winningCells.addAll(cells);
                    }
                }
            }
        }
        return winningCells;
    }

    private int[] minimax(int depth, boolean maximizing, int alpha, int beta) {
        if (hasWon('O')) {
            return new int[] { -1, -1, 10 + depth };
        }
        if (hasWon('X')) {
            return new int[] { -1, -1, -10 - depth };
        }

        List<int[]> moves = getAvailableMoves();
        if (moves.isEmpty() || depth == 0) {
            return new int[] { -1, -1, 0 };
        }

        char player = maximizing ? 'O' : 'X';
        int[] best = { -1, -1, maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE };

        for (int[] move : moves) {
            board[move[0]][move[1]] = player;
            int[] result = minimax(depth - 1, !maximizing, alpha, beta);
            board[move[0]][move[1]] = EMPTY;

            if (maximizing ? result[2] > best[2] : result[2] < best[2]) {
                best = new int[] { move[0], move[1], result[2] };
            }

            if (maximizing) {
                alpha = Math.max(alpha, best[2]);
            } else {
                beta = Math.min(beta, best[2]);
            }

            if (beta <= alpha) {
                break;
            }
        }

        return best;
    }

    private boolean checkDirection(char player, int row, int col, int rowStep, int colStep) {
        int count = 0;
        for (int i = 0; inBounds(row + rowStep * i, col + colStep * i)
                && board[row + rowStep * i][col + colStep * i] == player; i++) {
            count++;
        }
        for (int i = 1; inBounds(row - rowStep * i, col - colStep * i)
                && board[row - rowStep * i][col - colStep * i] == player; i++) {
            count++;
        }
        return count >= winLength;
    }

    private List<int[]> collectWinCells(char player, int row, int col, int rowStep, int colStep) {
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; inBounds(row + rowStep * i, col + colStep * i)
                && board[row + rowStep * i][col + colStep * i] == player; i++) {
            cells.add(new int[] { row + rowStep * i, col + colStep * i });
        }
        for (int i = 1; inBounds(row - rowStep * i, col - colStep * i)
                && board[row - rowStep * i][col - colStep * i] == player; i++) {
            cells.add(new int[] { row - rowStep * i, col - colStep * i });
        }
        return cells.size() >= winLength ? cells : null;
    }

    private boolean inBounds(int row, int col) {
        return row >= 0 && row < size && col >= 0 && col < size;
    }

    private void requireInBounds(int row, int col) {
        if (!inBounds(row, col)) {
            throw new IndexOutOfBoundsException("Cell is outside the board.");
        }
    }

    private void requireValidPlayer(char player) {
        if (player != 'X' && player != 'O') {
            throw new IllegalArgumentException("Player must be X or O.");
        }
    }
}
