import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Stack;

public class TicTacToeGame {
    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public static final char EMPTY = ' ';
    private static final int WIN_SCORE = 1_000_000;
    private static final int MAX_EVAL_SCORE = WIN_SCORE / 2;
    private static final int MAX_CANDIDATE_MOVES = 14;
    private static final Random RANDOM = new Random();

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
        int[] winningMove = findImmediateMove('O');
        if (winningMove != null) {
            return new int[] { winningMove[0], winningMove[1], WIN_SCORE };
        }

        int[] blockingMove = findImmediateMove('X');
        if (blockingMove != null) {
            return new int[] { blockingMove[0], blockingMove[1], WIN_SCORE / 2 };
        }

        int cappedDepth = Math.max(0, Math.min(depth, size * size));
        return minimax(cappedDepth, true, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public int[] findBestMove(Difficulty difficulty) {
        return recommendMove('O', difficulty);
    }

    public int[] recommendMove(char player, Difficulty difficulty) {
        requireValidPlayer(player);
        if (difficulty == null) {
            throw new IllegalArgumentException("Difficulty is required.");
        }
        if (difficulty == Difficulty.EASY) {
            return randomMove();
        }
        return findBestMoveFor(player, searchDepth(difficulty));
    }

    public int undoTurn(boolean vsComputer) {
        int targetMoves = vsComputer && undoStack.size() >= 2 ? 2 : 1;
        return undoMoves(targetMoves);
    }

    public int redoTurn(boolean vsComputer) {
        int targetMoves = vsComputer && redoStack.size() >= 2 ? 2 : 1;
        return redoMoves(targetMoves);
    }

    public char[][] buildSnapshot(List<int[]> moves) {
        char[][] snapshot = new char[size][size];
        for (char[] row : snapshot) {
            Arrays.fill(row, EMPTY);
        }
        if (moves == null) {
            return snapshot;
        }

        for (int[] move : moves) {
            if (move == null || move.length < 3) {
                throw new IllegalArgumentException("Each move must contain row, column, and player.");
            }
            int row = move[0];
            int col = move[1];
            char player = (char) move[2];
            requireValidPlayer(player);
            if (!inBounds(row, col) || snapshot[row][col] != EMPTY) {
                throw new IllegalArgumentException("Move history contains an invalid move.");
            }
            snapshot[row][col] = player;
        }
        return snapshot;
    }

    public char getTimeoutWinner(char timedOutPlayer) {
        requireValidPlayer(timedOutPlayer);
        return opponentOf(timedOutPlayer);
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
        return minimax(depth, maximizing, alpha, beta, 'O');
    }

    private int[] minimax(int depth, boolean maximizing, int alpha, int beta, char aiPlayer) {
        char opponent = opponentOf(aiPlayer);
        if (hasWon(aiPlayer)) {
            return new int[] { -1, -1, WIN_SCORE + depth };
        }
        if (hasWon(opponent)) {
            return new int[] { -1, -1, -WIN_SCORE - depth };
        }

        if (depth == 0) {
            return new int[] { -1, -1, evaluateBoard(aiPlayer) };
        }

        List<int[]> moves = getOrderedCandidateMoves(aiPlayer);
        if (moves.isEmpty()) {
            return new int[] { -1, -1, 0 };
        }

        char player = maximizing ? aiPlayer : opponent;
        int[] best = { -1, -1, maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE };

        for (int[] move : moves) {
            board[move[0]][move[1]] = player;
            int[] result = minimax(depth - 1, !maximizing, alpha, beta, aiPlayer);
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

    private int[] findBestMoveFor(char player, int depth) {
        int[] winningMove = findImmediateMove(player);
        if (winningMove != null) {
            return new int[] { winningMove[0], winningMove[1], WIN_SCORE };
        }

        int[] blockingMove = findImmediateMove(opponentOf(player));
        if (blockingMove != null) {
            return new int[] { blockingMove[0], blockingMove[1], WIN_SCORE / 2 };
        }

        int cappedDepth = Math.max(0, Math.min(depth, size * size));
        return minimax(cappedDepth, true, Integer.MIN_VALUE, Integer.MAX_VALUE, player);
    }

    private int undoMoves(int count) {
        int undone = 0;
        while (undone < count && undoMove()) {
            undone++;
        }
        return undone;
    }

    private int redoMoves(int count) {
        int redone = 0;
        while (redone < count && redoMove()) {
            redone++;
        }
        return redone;
    }

    private int[] randomMove() {
        List<int[]> moves = getAvailableMoves();
        if (moves.isEmpty()) {
            return new int[] { -1, -1, 0 };
        }

        int[] move = moves.get(RANDOM.nextInt(moves.size()));
        return new int[] { move[0], move[1], 0 };
    }

    private int searchDepth(Difficulty difficulty) {
        int remainingMoves = getAvailableMoves().size();
        if (difficulty == Difficulty.MEDIUM) {
            if (size <= 3) {
                return Math.min(remainingMoves, 3);
            }
            if (size <= 5) {
                return Math.min(remainingMoves, 2);
            }
            return Math.min(remainingMoves, 1);
        }

        if (size <= 3) {
            return Math.min(remainingMoves, 9);
        }
        if (size <= 4) {
            return Math.min(remainingMoves, 4);
        }
        if (size <= 6) {
            return Math.min(remainingMoves, 3);
        }
        return Math.min(remainingMoves, 2);
    }

    private int[] findImmediateMove(char player) {
        for (int[] move : getOrderedAvailableMoves(player)) {
            board[move[0]][move[1]] = player;
            boolean wins = hasWon(player, move[0], move[1]);
            board[move[0]][move[1]] = EMPTY;
            if (wins) {
                return move;
            }
        }
        return null;
    }

    private List<int[]> getOrderedCandidateMoves() {
        return getOrderedCandidateMoves('O');
    }

    private List<int[]> getOrderedCandidateMoves(char aiPlayer) {
        List<int[]> moves = getOrderedAvailableMoves(aiPlayer);
        if (size <= 3 || moves.size() <= MAX_CANDIDATE_MOVES) {
            return moves;
        }

        List<int[]> nearbyMoves = new ArrayList<>();
        for (int[] move : moves) {
            if (hasNeighbor(move[0], move[1])) {
                nearbyMoves.add(move);
            }
        }
        if (!nearbyMoves.isEmpty()) {
            moves = nearbyMoves;
        }

        if (moves.size() > MAX_CANDIDATE_MOVES) {
            return new ArrayList<>(moves.subList(0, MAX_CANDIDATE_MOVES));
        }
        return moves;
    }

    private List<int[]> getOrderedAvailableMoves() {
        return getOrderedAvailableMoves('O');
    }

    private List<int[]> getOrderedAvailableMoves(char aiPlayer) {
        List<int[]> moves = getAvailableMoves();
        moves.sort((first, second) -> Integer.compare(
                movePriority(second[0], second[1], aiPlayer),
                movePriority(first[0], first[1], aiPlayer)));
        return moves;
    }

    private int movePriority(int row, int col) {
        return movePriority(row, col, 'O');
    }

    private int movePriority(int row, int col, char aiPlayer) {
        char opponent = opponentOf(aiPlayer);
        int score = centerScore(row, col);
        if (hasNeighbor(row, col)) {
            score += 1_000;
        }
        score += moveLinePotential(row, col, aiPlayer) * 3;
        score += moveLinePotential(row, col, opponent) * 2;
        return score;
    }

    private int moveLinePotential(int row, int col, char player) {
        int[][] directions = { { 1, 0 }, { 0, 1 }, { 1, 1 }, { 1, -1 } };
        int total = 0;
        for (int[] direction : directions) {
            int rowStep = direction[0];
            int colStep = direction[1];
            int pieces = 1
                    + countContiguous(row, col, rowStep, colStep, player)
                    + countContiguous(row, col, -rowStep, -colStep, player);
            int capacity = 1
                    + countCapacity(row, col, rowStep, colStep, player)
                    + countCapacity(row, col, -rowStep, -colStep, player);
            if (capacity >= winLength) {
                total = addCapped(total, lineWeight(Math.min(pieces, winLength), 0));
            }
        }
        return total;
    }

    private int evaluateBoard() {
        return evaluateBoard('O');
    }

    private int evaluateBoard(char aiPlayer) {
        char opponent = opponentOf(aiPlayer);
        int[][] directions = { { 1, 0 }, { 0, 1 }, { 1, 1 }, { 1, -1 } };
        int score = 0;
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (board[row][col] == aiPlayer) {
                    score = addCapped(score, centerScore(row, col));
                } else if (board[row][col] == opponent) {
                    score = addCapped(score, -centerScore(row, col));
                }

                for (int[] direction : directions) {
                    int endRow = row + direction[0] * (winLength - 1);
                    int endCol = col + direction[1] * (winLength - 1);
                    if (inBounds(endRow, endCol)) {
                        score = addCapped(score, scoreWindow(row, col, direction[0], direction[1], aiPlayer));
                    }
                }
            }
        }
        return score;
    }

    private int scoreWindow(int row, int col, int rowStep, int colStep) {
        return scoreWindow(row, col, rowStep, colStep, 'O');
    }

    private int scoreWindow(int row, int col, int rowStep, int colStep, char aiPlayer) {
        char opponent = opponentOf(aiPlayer);
        int xCount = 0;
        int aiCount = 0;
        for (int i = 0; i < winLength; i++) {
            char cell = board[row + rowStep * i][col + colStep * i];
            if (cell == opponent) {
                xCount++;
            } else if (cell == aiPlayer) {
                aiCount++;
            }
        }

        if (xCount > 0 && aiCount > 0) {
            return 0;
        }

        int openEnds = countOpenEnds(row, col, rowStep, colStep);
        if (aiCount > 0) {
            return lineWeight(aiCount, openEnds);
        }
        if (xCount > 0) {
            return -lineWeight(xCount, openEnds);
        }
        return 0;
    }

    private int countOpenEnds(int row, int col, int rowStep, int colStep) {
        int openEnds = 0;
        int beforeRow = row - rowStep;
        int beforeCol = col - colStep;
        int afterRow = row + rowStep * winLength;
        int afterCol = col + colStep * winLength;

        if (inBounds(beforeRow, beforeCol) && board[beforeRow][beforeCol] == EMPTY) {
            openEnds++;
        }
        if (inBounds(afterRow, afterCol) && board[afterRow][afterCol] == EMPTY) {
            openEnds++;
        }
        return openEnds;
    }

    private int lineWeight(int pieces, int openEnds) {
        if (pieces <= 0) {
            return 0;
        }
        if (pieces >= winLength) {
            return WIN_SCORE;
        }

        long score = 1;
        for (int i = 0; i < pieces; i++) {
            score *= 10;
            if (score >= MAX_EVAL_SCORE) {
                return MAX_EVAL_SCORE;
            }
        }
        if (pieces == winLength - 1) {
            score *= 5;
        } else if (pieces == winLength - 2) {
            score *= 2;
        }
        score *= Math.max(1, openEnds + 1);
        return (int) Math.min(score, MAX_EVAL_SCORE);
    }

    private int addCapped(int score, int delta) {
        long total = (long) score + delta;
        if (total > MAX_EVAL_SCORE) {
            return MAX_EVAL_SCORE;
        }
        if (total < -MAX_EVAL_SCORE) {
            return -MAX_EVAL_SCORE;
        }
        return (int) total;
    }

    private int centerScore(int row, int col) {
        int rowDelta = row * 2 - (size - 1);
        int colDelta = col * 2 - (size - 1);
        return size * size * 2 - rowDelta * rowDelta - colDelta * colDelta;
    }

    private boolean hasNeighbor(int row, int col) {
        for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for (int colOffset = -1; colOffset <= 1; colOffset++) {
                if (rowOffset == 0 && colOffset == 0) {
                    continue;
                }
                int neighborRow = row + rowOffset;
                int neighborCol = col + colOffset;
                if (inBounds(neighborRow, neighborCol) && board[neighborRow][neighborCol] != EMPTY) {
                    return true;
                }
            }
        }
        return false;
    }

    private int countContiguous(int row, int col, int rowStep, int colStep, char player) {
        int count = 0;
        for (int i = 1; i < winLength; i++) {
            int nextRow = row + rowStep * i;
            int nextCol = col + colStep * i;
            if (!inBounds(nextRow, nextCol) || board[nextRow][nextCol] != player) {
                break;
            }
            count++;
        }
        return count;
    }

    private int countCapacity(int row, int col, int rowStep, int colStep, char player) {
        char opponent = player == 'X' ? 'O' : 'X';
        int count = 0;
        for (int i = 1; i < winLength; i++) {
            int nextRow = row + rowStep * i;
            int nextCol = col + colStep * i;
            if (!inBounds(nextRow, nextCol) || board[nextRow][nextCol] == opponent) {
                break;
            }
            count++;
        }
        return count;
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

    private char opponentOf(char player) {
        requireValidPlayer(player);
        return player == 'X' ? 'O' : 'X';
    }
}
