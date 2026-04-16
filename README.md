
<div align="center">

```
     ╔═══╦═══╦═══╗
     ║ X ║   ║ O ║
     ╠═══╬═══╬═══╣
     ║   ║ X ║   ║
     ╠═══╬═══╬═══╣
     ║ O ║   ║ X ║
     ╚═══╩═══╩═══╝
```

# Ultimate Tic Tac Toe

**A game older than civilization. Reimagined.**

![Java](https://img.shields.io/badge/Java-11%2B-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Swing](https://img.shields.io/badge/Swing-GUI-5C6BC0?style=flat-square)
![Sound](https://img.shields.io/badge/Sound-Synthesized-E91E63?style=flat-square)
![AI](https://img.shields.io/badge/AI-Minimax-00BCD4?style=flat-square)

---

*Dark-themed. Hand-drawn symbols. Synthesized audio.*
*No assets. No dependencies. One file. Pure Java.*

</div>

---

## The Idea

Tic Tac Toe doesn't need to be boring.

This version opens as a standalone window with a dark, gradient-washed interface. X's are drawn as two crossing strokes. O's are drawn as circles. Every symbol is rendered with anti-aliased `Graphics2D` — no fonts, no images, just math and pixels.

The board scales from **3×3** to **10×10**. The win condition scales with it. You choose how many in a row it takes to win. You choose whether you're playing a friend or the machine.

The machine plays minimax with alpha-beta pruning. It doesn't make mistakes on small boards. On larger boards, it thinks ahead as far as it can before time runs out.

Every move has a sound. X gets an ascending two-tone click. O gets a descending one. Win triggers a four-note fanfare. Tie gets a slow fade. All audio is synthesized from sine waves at runtime — no `.wav` files, no libraries. Just `javax.sound.sampled` and a byte buffer.

---

## Quickstart

```bash
git clone https://github.com/balprab24/TicTacToeAdvanced.git
cd TicTacToeAdvanced
javac TicTacToeAdvanced.java
java TicTacToeAdvanced
```

That's it. Java 11+, nothing else.

---

## The Menu

The game opens to a setup screen built around two **card panels**:

**How to Play** — rules and controls, laid out with colored bullet points and a key-value control reference.

**Game Setup** — three settings, zero typing:

| Setting | Control | Range |
|---|---|---|
| Board Size | `−` / `+` stepper | 3 – 10 |
| Win Length | `−` / `+` stepper | 3 – board size |
| Game Mode | segmented toggle | PvP or PvC |

A decorative mini-board with X's and O's sits below the title. Everything is custom-painted — no default Swing chrome anywhere.

Hit **Start Game** and the board opens.

---

## In-Game

| Action | What it does |
|---|---|
| Click a cell | Place your symbol |
| **Undo** | Take back the last move |
| **Redo** | Reapply an undone move |
| **New Game** | Fresh board, same settings |
| **Menu** | Back to setup |

Winning cells highlight green. The status bar changes color to match whose turn it is. Scores persist across rounds until you close the window.

---

## Under the Hood

```
TicTacToeAdvanced.java
│
├─ main()             → launches Swing on the EDT
├─ buildMenuPanel()   → card-based setup screen
│   ├─ wrapInCard()       rounded card containers
│   ├─ circleBtn()        circular +/− steppers
│   ├─ toggleSegment()    PvP / PvC segmented control
│   └─ ruleLine()         bullet-point rule rows
│
├─ buildGamePanel()   → game board + controls
│   └─ CellButton         inner class, draws X/O via Graphics2D
│
├─ minimax()          → AI with alpha-beta pruning
├─ playTones()        → sine-wave audio synthesis
├─ checkEnd()         → win detection + cell highlighting
└─ undoMove/redoMove  → full move history stack
```

**Zero dependencies.** The entire game — UI, AI, audio — lives in one `.java` file using only the JDK standard library.

---

## Design Choices

- **No images.** Every visual element is drawn with `Graphics2D`. Symbols scale cleanly to any cell size.
- **No audio files.** Tones are generated from frequency + duration + volume parameters, synthesized into PCM byte arrays, and played through `SourceDataLine`.
- **No L&F overrides.** Every component (`JButton`, `JPanel`, `JSpinner` replacement) is individually custom-painted to avoid the default Swing look entirely.
- **One file.** No build system, no packages, no folders. Clone, compile, run.

---

<div align="center">

*Built with nothing but `javac` and stubbornness.*

</div>
