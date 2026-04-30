import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicTacToeGameTest {

    @Test
    void initializesEmptyBoard() {
        TicTacToeGame game = new TicTacToeGame(3, 3);

        assertEquals('X', game.getCurrentPlayer());
        assertFalse(game.isBoardFull());
        for (int row = 0; row < game.getSize(); row++) {
            for (int col = 0; col < game.getSize(); col++) {
                assertEquals(TicTacToeGame.EMPTY, game.getCell(row, col));
            }
        }
    }

    @Test
    void rejectsInvalidMoves() {
        TicTacToeGame game = new TicTacToeGame(3, 3);

        assertTrue(game.placeMove(0, 0, 'X'));
        assertFalse(game.placeMove(0, 0, 'O'));
        assertFalse(game.placeMove(-1, 0, 'O'));
        assertFalse(game.placeMove(0, 3, 'O'));
        assertEquals('X', game.getCell(0, 0));
    }

    @Test
    void detectsHorizontalWin() {
        TicTacToeGame game = new TicTacToeGame(3, 3);

        game.placeMove(0, 0, 'X');
        game.placeMove(0, 1, 'X');
        game.placeMove(0, 2, 'X');

        assertTrue(game.hasWon('X', 0, 2));
    }

    @Test
    void detectsVerticalWin() {
        TicTacToeGame game = new TicTacToeGame(3, 3);

        game.placeMove(0, 1, 'O');
        game.placeMove(1, 1, 'O');
        game.placeMove(2, 1, 'O');

        assertTrue(game.hasWon('O', 2, 1));
    }

    @Test
    void detectsDiagonalWin() {
        TicTacToeGame game = new TicTacToeGame(3, 3);

        game.placeMove(0, 0, 'X');
        game.placeMove(1, 1, 'X');
        game.placeMove(2, 2, 'X');

        assertTrue(game.hasWon('X', 2, 2));
    }

    @Test
    void supportsLargerCustomWinLength() {
        TicTacToeGame game = new TicTacToeGame(5, 4);

        game.placeMove(1, 0, 'O');
        game.placeMove(1, 1, 'O');
        game.placeMove(1, 2, 'O');
        assertFalse(game.hasWon('O', 1, 2));

        game.placeMove(1, 3, 'O');
        assertTrue(game.hasWon('O', 1, 3));
    }

    @Test
    void detectsFullBoardTieState() {
        TicTacToeGame game = new TicTacToeGame(3, 3);
        char[][] tieBoard = {
                { 'X', 'O', 'X' },
                { 'X', 'O', 'O' },
                { 'O', 'X', 'X' }
        };

        fillBoard(game, tieBoard);

        assertTrue(game.isBoardFull());
        assertFalse(game.hasWon('X'));
        assertFalse(game.hasWon('O'));
    }

    @Test
    void undoAndRedoRestoreMoves() {
        TicTacToeGame game = new TicTacToeGame(3, 3);

        game.placeMove(0, 0, 'X');
        game.switchPlayer();
        game.placeMove(1, 1, 'O');

        assertTrue(game.undoMove());
        assertEquals(TicTacToeGame.EMPTY, game.getCell(1, 1));
        assertEquals('O', game.getCurrentPlayer());

        assertTrue(game.redoMove());
        assertEquals('O', game.getCell(1, 1));
        assertEquals('X', game.getCurrentPlayer());
    }

    @Test
    void newMoveClearsRedoHistory() {
        TicTacToeGame game = new TicTacToeGame(3, 3);

        game.placeMove(0, 0, 'X');
        game.undoMove();
        assertTrue(game.canRedo());

        game.placeMove(2, 2, 'X');
        assertFalse(game.canRedo());
    }

    @Test
    void computerTakesImmediateWin() {
        TicTacToeGame game = new TicTacToeGame(3, 3);

        game.placeMove(0, 0, 'O');
        game.placeMove(0, 1, 'O');
        game.placeMove(1, 0, 'X');
        game.placeMove(2, 1, 'X');

        int[] bestMove = game.findBestMove(3);

        assertArrayEquals(new int[] { 0, 2 }, new int[] { bestMove[0], bestMove[1] });
    }

    @Test
    void computerBlocksImmediateLoss() {
        TicTacToeGame game = new TicTacToeGame(3, 3);

        game.placeMove(0, 0, 'X');
        game.placeMove(0, 1, 'X');
        game.placeMove(1, 1, 'O');

        int[] bestMove = game.findBestMove(2);

        assertArrayEquals(new int[] { 0, 2 }, new int[] { bestMove[0], bestMove[1] });
    }

    @Test
    void computerChoosesCenterOnEmptyLargerBoard() {
        TicTacToeGame game = new TicTacToeGame(5, 4);

        int[] bestMove = game.findBestMove(1);

        assertArrayEquals(new int[] { 2, 2 }, moveOnly(bestMove));
    }

    @Test
    void computerExtendsStrongLineOnLargerBoard() {
        TicTacToeGame game = new TicTacToeGame(5, 4);

        game.placeMove(2, 0, 'X');
        game.placeMove(2, 1, 'O');
        game.placeMove(2, 2, 'O');
        game.placeMove(4, 4, 'X');

        int[] bestMove = game.findBestMove(1);

        assertArrayEquals(new int[] { 2, 3 }, moveOnly(bestMove));
    }

    @Test
    void computerTakesImmediateWinOnLargerBoard() {
        TicTacToeGame game = new TicTacToeGame(5, 4);

        game.placeMove(1, 0, 'X');
        game.placeMove(1, 1, 'O');
        game.placeMove(1, 2, 'O');
        game.placeMove(1, 3, 'O');

        int[] bestMove = game.findBestMove(1);

        assertArrayEquals(new int[] { 1, 4 }, moveOnly(bestMove));
    }

    @Test
    void computerBlocksImmediateLossOnLargerBoard() {
        TicTacToeGame game = new TicTacToeGame(5, 4);

        game.placeMove(3, 0, 'O');
        game.placeMove(3, 1, 'X');
        game.placeMove(3, 2, 'X');
        game.placeMove(3, 3, 'X');

        int[] bestMove = game.findBestMove(1);

        assertArrayEquals(new int[] { 3, 4 }, moveOnly(bestMove));
    }

    @Test
    void easyDifficultyReturnsLegalAvailableMove() {
        TicTacToeGame game = new TicTacToeGame(5, 4);

        game.placeMove(0, 0, 'X');
        game.placeMove(1, 1, 'O');

        int[] bestMove = game.findBestMove(TicTacToeGame.Difficulty.EASY);

        assertTrue(bestMove[0] >= 0 && bestMove[0] < game.getSize());
        assertTrue(bestMove[1] >= 0 && bestMove[1] < game.getSize());
        assertEquals(TicTacToeGame.EMPTY, game.getCell(bestMove[0], bestMove[1]));
    }

    @Test
    void mediumDifficultyBlocksImmediateLargerBoardThreat() {
        TicTacToeGame game = new TicTacToeGame(5, 4);

        game.placeMove(3, 0, 'O');
        game.placeMove(3, 1, 'X');
        game.placeMove(3, 2, 'X');
        game.placeMove(3, 3, 'X');

        int[] bestMove = game.findBestMove(TicTacToeGame.Difficulty.MEDIUM);

        assertArrayEquals(new int[] { 3, 4 }, moveOnly(bestMove));
    }

    @Test
    void mediumDifficultyChoosesCenterOnEmptyLargerBoard() {
        TicTacToeGame game = new TicTacToeGame(5, 4);

        int[] bestMove = game.findBestMove(TicTacToeGame.Difficulty.MEDIUM);

        assertArrayEquals(new int[] { 2, 2 }, moveOnly(bestMove));
    }

    @Test
    void hardDifficultyChoosesCenterOnEmptyLargerBoard() {
        TicTacToeGame game = new TicTacToeGame(5, 4);

        int[] bestMove = game.findBestMove(TicTacToeGame.Difficulty.HARD);

        assertArrayEquals(new int[] { 2, 2 }, moveOnly(bestMove));
    }

    @Test
    void hardDifficultyExtendsStrongLargerBoardLine() {
        TicTacToeGame game = new TicTacToeGame(6, 5);

        game.placeMove(2, 1, 'O');
        game.placeMove(2, 2, 'O');
        game.placeMove(2, 3, 'O');
        game.placeMove(5, 5, 'X');

        int[] bestMove = game.findBestMove(TicTacToeGame.Difficulty.HARD);

        assertTrue(isMove(bestMove, 2, 0) || isMove(bestMove, 2, 4));
    }

    @Test
    void recommendationUsesRequestedPlayerPerspective() {
        TicTacToeGame game = new TicTacToeGame(3, 3);

        game.placeMove(0, 0, 'X');
        game.placeMove(0, 1, 'X');
        game.placeMove(1, 1, 'O');

        int[] bestMove = game.recommendMove('X', TicTacToeGame.Difficulty.MEDIUM);

        assertArrayEquals(new int[] { 0, 2 }, moveOnly(bestMove));
    }

    @Test
    void playerVsComputerUndoAndRedoOperateAsPairs() {
        TicTacToeGame game = new TicTacToeGame(3, 3);

        game.placeMove(0, 0, 'X');
        game.placeMove(1, 1, 'O');

        assertEquals(2, game.undoTurn(true));
        assertEquals(TicTacToeGame.EMPTY, game.getCell(0, 0));
        assertEquals(TicTacToeGame.EMPTY, game.getCell(1, 1));

        assertEquals(2, game.redoTurn(true));
        assertEquals('X', game.getCell(0, 0));
        assertEquals('O', game.getCell(1, 1));
    }

    @Test
    void snapshotRebuildsBoardFromMoveHistory() {
        TicTacToeGame game = new TicTacToeGame(4, 3);
        java.util.List<int[]> moves = new java.util.ArrayList<>();
        moves.add(new int[] { 0, 0, 'X' });
        moves.add(new int[] { 2, 1, 'O' });
        moves.add(new int[] { 3, 3, 'X' });

        char[][] snapshot = game.buildSnapshot(moves);

        assertEquals('X', snapshot[0][0]);
        assertEquals('O', snapshot[2][1]);
        assertEquals('X', snapshot[3][3]);
        assertEquals(TicTacToeGame.EMPTY, snapshot[1][1]);
    }

    @Test
    void timeoutWinnerIsOpponentOfTimedOutPlayer() {
        TicTacToeGame game = new TicTacToeGame(3, 3);

        assertEquals('O', game.getTimeoutWinner('X'));
        assertEquals('X', game.getTimeoutWinner('O'));
    }

    private static int[] moveOnly(int[] move) {
        return new int[] { move[0], move[1] };
    }

    private static boolean isMove(int[] move, int row, int col) {
        return move[0] == row && move[1] == col;
    }

    private static void fillBoard(TicTacToeGame game, char[][] values) {
        for (int row = 0; row < values.length; row++) {
            for (int col = 0; col < values[row].length; col++) {
                game.placeMove(row, col, values[row][col]);
            }
        }
    }
}
