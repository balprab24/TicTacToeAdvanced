# Ultimate Tic Tac Toe

![Java](https://img.shields.io/badge/Java-11%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Swing](https://img.shields.io/badge/GUI-Java%20Swing-5C6BC0?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-brightgreen?style=for-the-badge)

A feature-rich, fully graphical Tic Tac Toe game built with Java Swing — featuring a polished dark UI, AI opponent, sound effects, and configurable board sizes up to 10×10.

---

## Features

- **Graphical Window** — dark-themed GUI with gradient backgrounds and anti-aliased graphics
- **Configurable Board** — choose any board size from 3×3 up to 10×10
- **Custom Win Length** — set how many in a row you need to win
- **AI Opponent** — minimax algorithm with alpha-beta pruning
- **Sound Effects** — unique tones for X placement, O placement, win, and tie
- **Undo / Redo** — step back and forward through moves at any point
- **Score Tracking** — running tally of X wins, O wins, and ties across rounds
- **Win Highlighting** — winning cells light up green when a round ends
- **Rules Menu** — start screen explains all controls before you play

---

## Getting Started

### Prerequisites
- Java 11 or higher

### Run
```bash
# Clone the repo
git clone https://github.com/balprab24/TicTacToeAdvanced.git
cd TicTacToeAdvanced

# Compile
javac TicTacToeAdvanced.java

# Run
java TicTacToeAdvanced
```

---

## How to Play

1. Launch the game — a menu window appears
2. Set your **board size**, **win length**, and **game mode**
3. Click **Start Game**
4. Click any empty cell to place your symbol

| Control | Action |
|---|---|
| Click cell | Place your symbol |
| Undo | Remove your last move |
| Redo | Reapply an undone move |
| New Game | Restart with the same settings |
| Menu | Return to setup screen |

---

## Game Modes

| Mode | Description |
|---|---|
| Player vs Player | Two players take turns on the same machine |
| Player vs Computer | Play against the minimax AI |

---

## Tech Stack

- **Language** — Java
- **GUI** — Java Swing (`javax.swing`)
- **Audio** — Java Sound API (`javax.sound.sampled`) — synthesized tones, no audio files needed
- **AI** — Minimax with alpha-beta pruning, depth-limited for performance

---

## Project Structure

```
TicTacToeAdvanced.java
│
├── main()               Entry point, launches Swing window
├── buildMenuPanel()     Start screen with rules + game setup
├── buildGamePanel()     In-game board, controls, and score
├── CellButton           Inner class — custom-painted game cells
├── minimax()            AI move selection (alpha-beta pruning)
├── playTones()          Sound synthesis engine
└── Game logic           Win detection, undo/redo, board state
```

---

## License

This project is open source under the [MIT License](LICENSE).
