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
            {'X', 'O', 'X'},
            {'X', 'O', 'O'},
            {'O', 'X', 'X'}
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

        assertArrayEquals(new int[]{0, 2}, new int[]{bestMove[0], bestMove[1]});
    }

    @Test
    void computerBlocksImmediateLoss() {
        TicTacToeGame game = new TicTacToeGame(3, 3);

        game.placeMove(0, 0, 'X');
        game.placeMove(0, 1, 'X');
        game.placeMove(1, 1, 'O');

        int[] bestMove = game.findBestMove(2);

        assertArrayEquals(new int[]{0, 2}, new int[]{bestMove[0], bestMove[1]});
    }

    private static void fillBoard(TicTacToeGame game, char[][] values) {
        for (int row = 0; row < values.length; row++) {
            for (int col = 0; col < values[row].length; col++) {
                game.placeMove(row, col, values[row][col]);
            }
        }
    }
}
