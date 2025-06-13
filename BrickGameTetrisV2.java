import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

public class BrickGameTetrisV2 extends JFrame {
    // Game constants
    private static final int WIDTH = 10;
    private static final int HEIGHT = 20;
    private static final int BLOCK_SIZE = 25;
    private static final int SIDEBAR_WIDTH = 100;
    
    // Game states
    private enum GameState { MENU, PLAYING, GAME_OVER }
    private GameState gameState = GameState.MENU;
    
    // Game variables
    private int[][] grid = new int[HEIGHT][WIDTH];
    private int[][] currentPiece;
    private int currentX, currentY, currentColor;
    private int score = 0;
    private int level = 1;
    private int linesCleared = 0;
    private int gameSpeed = 500; // Initial speed (ms)
    
    // Brick Game styling
    private final Color BG_COLOR = new Color(220, 220, 220); // Light gray background
    private final Color[] COLORS = {
        Color.BLACK,        // 0 - empty
        new Color(255, 50, 50),   // 1 - Red
        new Color(50, 50, 255),   // 2 - Blue
        new Color(50, 200, 50),   // 3 - Green
        new Color(255, 255, 50),  // 4 - Yellow
        new Color(180, 50, 180),  // 5 - Purple
        new Color(50, 200, 200),  // 6 - Cyan
        new Color(255, 150, 50)   // 7 - Orange
    };
    
    private final int[][][] TETROMINOS = {
        {{1, 1, 1, 1}}, // I
        {{1, 1}, {1, 1}}, // O
        {{1, 1, 1}, {0, 1, 0}}, // T
        {{1, 1, 1}, {1, 0, 0}}, // L
        {{1, 1, 1}, {0, 0, 1}}, // J
        {{1, 1, 0}, {0, 1, 1}}, // Z
        {{0, 1, 1}, {1, 1, 0}}  // S
    };
    
    private Timer gameTimer;
    private Random random = new Random();

    // Audio variables
    private Clip moveSound;
    private Clip lineClearSound;
    private Clip gameStartSound;
    private Clip gameOverSound;
    private boolean soundsEnabled = true;
    
    public BrickGameTetrisV2() {
        setTitle("BRICK GAME 9999-in-1 - TETRIS");
        setSize(WIDTH * BLOCK_SIZE + SIDEBAR_WIDTH + 16, HEIGHT * BLOCK_SIZE + 39);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        
        setupGame();
        setupControls();

        // Initialize audio
        initSounds();
    }
    
    private void setupGame() {
        // Initialize empty grid
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid[y][x] = 0;
            }
        }
        
        score = 0;
        linesCleared = 0;
        level = 1;
        gameSpeed = 500;
        
        gameTimer = new Timer(gameSpeed, e -> gameUpdate());
    }
    
    private void setupControls() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (gameState) {
                    case MENU:
                        handleMenuInput(e);
                        break;
                    case PLAYING:
                        handleGameInput(e);
                        break;
                    case GAME_OVER:
                        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                            gameState = GameState.MENU;
                            repaint();
                        }
                        break;
                }
            }
        });
    }
    
    private void initSounds() {
        try {
            // Block movement sound
            AudioInputStream moveAudio = AudioSystem.getAudioInputStream(
                getClass().getResourceAsStream("/assets/mr_9999_14.wav"));
            moveSound = AudioSystem.getClip();
            moveSound.open(moveAudio);
            
            // Line clear sound
            AudioInputStream clearAudio = AudioSystem.getAudioInputStream(
                getClass().getResourceAsStream("/assets/mr_9999_15.wav"));
            lineClearSound = AudioSystem.getClip();
            lineClearSound.open(clearAudio);

            // Game start sound
            AudioInputStream startAudio = AudioSystem.getAudioInputStream(
                getClass().getResourceAsStream("/assets/mr_9999_00.wav"));
            gameStartSound = AudioSystem.getClip();
            gameStartSound.open(startAudio);
        
            // Game over sound (descending tone)
            AudioInputStream overAudio = AudioSystem.getAudioInputStream(
                getClass().getResourceAsStream("/assets/mr_9999_04.wav"));
            gameOverSound = AudioSystem.getClip();
            gameOverSound.open(overAudio);
        } catch (Exception e) {
            System.out.println("Audio files not found - sounds disabled");
            soundsEnabled = false;
        }
    }

    private void playGameStartSound() {
        if (!soundsEnabled) return;
    
        if (gameStartSound.isRunning()) {
            gameStartSound.stop();
        }
        gameStartSound.setFramePosition(0);
        gameStartSound.start();
    }

    private void playMoveSound() {
        if (!soundsEnabled) return;
        
        if (moveSound.isRunning()) {
            moveSound.stop();
        }
        moveSound.setFramePosition(0);
        moveSound.start();
    }
    
    private void playLineClearSound() {
        if (!soundsEnabled) return;
        
        if (lineClearSound.isRunning()) {
            lineClearSound.stop();
        }
        lineClearSound.setFramePosition(0);
        lineClearSound.start();
    }

    private void playGameOverSound() {
        if (!soundsEnabled) return;
        
        if (gameOverSound.isRunning()) {
            gameOverSound.stop();
        }
        gameOverSound.setFramePosition(0);
        gameOverSound.start();
    }

    private void handleMenuInput(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            playGameStartSound(); // Play sound when starting the game
            gameState = GameState.PLAYING;
            setupGame();
            newPiece();
            gameTimer.start();
            repaint();
        }
    }
    
    private void handleGameInput(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT:
                moveLeft();
                break;
            case KeyEvent.VK_RIGHT:
                moveRight();
                break;
            case KeyEvent.VK_DOWN:
                if (!moveDown()) {
                    mergePiece();
                    clearLines();
                    newPiece();
                }
                break;
            case KeyEvent.VK_UP:
                rotate();
                break;
            case KeyEvent.VK_P:
                togglePause();
                break;
        }
        repaint();
    }
    
    private void togglePause() {
        if (gameTimer.isRunning()) {
            gameTimer.stop();
        } else {
            gameTimer.start();
        }
    }
    
    private void gameUpdate() {
        if (!moveDown()) {
            mergePiece();
            clearLines();
            newPiece();
        }
        repaint();
    }
    
    private void newPiece() {
        currentPiece = TETROMINOS[random.nextInt(TETROMINOS.length)];
        currentColor = 1 + random.nextInt(COLORS.length - 1);
        currentX = WIDTH / 2 - currentPiece[0].length / 2;
        currentY = 0;
        playMoveSound(); // Play sound when new piece appears

        if (collision()) {
            gameOver();
        }
    }
    
    private boolean collision() {
        for (int y = 0; y < currentPiece.length; y++) {
            for (int x = 0; x < currentPiece[y].length; x++) {
                if (currentPiece[y][x] != 0) {
                    int newX = currentX + x;
                    int newY = currentY + y;
                    
                    if (newX < 0 || newX >= WIDTH || newY >= HEIGHT) {
                        return true;
                    }
                    
                    if (newY >= 0 && grid[newY][newX] != 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private boolean moveDown() {
        currentY++;
        if (collision()) {
            currentY--;
            return false;
        }
        playMoveSound(); // Play sound with each movement
        return true;
    }
    
    private void moveLeft() {
        currentX--;
        if (collision()) {
            currentX++;
        }
    }
    
    private void moveRight() {
        currentX++;
        if (collision()) {
            currentX--;
        }
    }
    
    private void rotate() {
        int[][] rotated = new int[currentPiece[0].length][currentPiece.length];
        
        for (int y = 0; y < currentPiece.length; y++) {
            for (int x = 0; x < currentPiece[y].length; x++) {
                rotated[x][currentPiece.length - 1 - y] = currentPiece[y][x];
            }
        }
        
        int[][] oldPiece = currentPiece;
        currentPiece = rotated;
        if (collision()) {
            currentPiece = oldPiece;
        }
    }
    
    private void mergePiece() {
        for (int y = 0; y < currentPiece.length; y++) {
            for (int x = 0; x < currentPiece[y].length; x++) {
                if (currentPiece[y][x] != 0) {
                    grid[currentY + y][currentX + x] = currentColor;
                }
            }
        }
    }
    
    private void clearLines() {
        int linesRemoved = 0;
        
        for (int y = HEIGHT - 1; y >= 0; y--) {
            boolean fullLine = true;
            for (int x = 0; x < WIDTH; x++) {
                if (grid[y][x] == 0) {
                    fullLine = false;
                    break;
                }
            }
            
            if (fullLine) {
                // Move all lines above down
                for (int yy = y; yy > 0; yy--) {
                    System.arraycopy(grid[yy - 1], 0, grid[yy], 0, WIDTH);
                }
                // Clear top line
                grid[0] = new int[WIDTH];
                linesRemoved++;
                y++; // Check the same line again
            }
        }
        
        // Update score and level
        if (linesRemoved > 0) {
            linesCleared += linesRemoved;
            score += calculateScore(linesRemoved);
            
            // Increase level every 10 lines
            level = 1 + (linesCleared / 10);
            gameSpeed = Math.max(100, 500 - (level * 40)); // Speed increases with level
            gameTimer.setDelay(gameSpeed);

            playLineClearSound(); // Play long beep for line clear
        }
    }
    
    private int calculateScore(int lines) {
        switch (lines) {
            case 1: return 100 * level;
            case 2: return 300 * level;
            case 3: return 500 * level;
            case 4: return 800 * level;
            default: return 0;
        }
    }
    
    private void gameOver() {
        playGameOverSound(); // Play sound when the game ends
        gameTimer.stop();
        gameState = GameState.GAME_OVER;
    }
    
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        
        // Set Brick Game-style background
        g.setColor(BG_COLOR);
        g.fillRect(0, 0, getWidth(), getHeight());
        
        // Draw game border
        g.setColor(Color.BLACK);
        g.drawRect(5, 5, WIDTH * BLOCK_SIZE + 10, HEIGHT * BLOCK_SIZE + 10);
        
        // Draw grid background
        g.setColor(Color.WHITE);
        g.fillRect(10, 10, WIDTH * BLOCK_SIZE, HEIGHT * BLOCK_SIZE);
        
        // Draw grid lines
        g.setColor(new Color(200, 200, 200));
        for (int x = 0; x <= WIDTH; x++) {
            g.drawLine(10 + x * BLOCK_SIZE, 10, 10 + x * BLOCK_SIZE, 10 + HEIGHT * BLOCK_SIZE);
        }
        for (int y = 0; y <= HEIGHT; y++) {
            g.drawLine(10, 10 + y * BLOCK_SIZE, 10 + WIDTH * BLOCK_SIZE, 10 + y * BLOCK_SIZE);
        }
        
        // Draw blocks
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (grid[y][x] != 0) {
                    drawBlock(g, x, y, grid[y][x]);
                }
            }
        }
        
        // Draw current piece
        if (gameState == GameState.PLAYING) {
            for (int y = 0; y < currentPiece.length; y++) {
                for (int x = 0; x < currentPiece[y].length; x++) {
                    if (currentPiece[y][x] != 0) {
                        drawBlock(g, currentX + x, currentY + y, currentColor);
                    }
                }
            }
        }
        
        // Draw sidebar
        drawSidebar(g);
        
        // Draw menu/game over screens
        if (gameState == GameState.MENU) {
            drawMenu(g);
        } else if (gameState == GameState.GAME_OVER) {
            drawGameOver(g);
        }
    }
    
    private void drawBlock(Graphics g, int x, int y, int colorIdx) {
        Color color = COLORS[colorIdx];
        
        // Block with 3D effect
        g.setColor(color);
        g.fillRect(10 + x * BLOCK_SIZE, 10 + y * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
        
        // Highlight
        g.setColor(color.brighter());
        g.drawLine(10 + x * BLOCK_SIZE, 10 + y * BLOCK_SIZE, 
                  10 + (x+1) * BLOCK_SIZE - 1, 10 + y * BLOCK_SIZE);
        g.drawLine(10 + x * BLOCK_SIZE, 10 + y * BLOCK_SIZE, 
                  10 + x * BLOCK_SIZE, 10 + (y+1) * BLOCK_SIZE - 1);
        
        // Shadow
        g.setColor(color.darker());
        g.drawLine(10 + (x+1) * BLOCK_SIZE - 1, 10 + y * BLOCK_SIZE, 
                  10 + (x+1) * BLOCK_SIZE - 1, 10 + (y+1) * BLOCK_SIZE - 1);
        g.drawLine(10 + x * BLOCK_SIZE, 10 + (y+1) * BLOCK_SIZE - 1, 
                  10 + (x+1) * BLOCK_SIZE - 1, 10 + (y+1) * BLOCK_SIZE - 1);
    }
    
    private void drawSidebar(Graphics g) {
        int sidebarX = WIDTH * BLOCK_SIZE + 20;
        
        // Sidebar background
        g.setColor(new Color(240, 240, 240));
        g.fillRect(sidebarX, 10, SIDEBAR_WIDTH - 10, HEIGHT * BLOCK_SIZE);
        g.setColor(Color.BLACK);
        g.drawRect(sidebarX, 10, SIDEBAR_WIDTH - 10, HEIGHT * BLOCK_SIZE);
        
        // Game info
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString("BRICK GAME", sidebarX + 10, 30);
        g.drawString("9999-in-1", sidebarX + 15, 50);
        
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.drawString("SCORE:", sidebarX + 10, 80);
        g.drawString(String.valueOf(score), sidebarX + 10, 100);
        
        g.drawString("LEVEL:", sidebarX + 10, 130);
        g.drawString(String.valueOf(level), sidebarX + 10, 150);
        
        g.drawString("LINES:", sidebarX + 10, 180);
        g.drawString(String.valueOf(linesCleared), sidebarX + 10, 200);
        
        // Controls help
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        g.drawString("CONTROLS:", sidebarX + 10, 250);
        g.drawString("← → : Move", sidebarX + 10, 270);
        g.drawString("↑ : Rotate", sidebarX + 10, 290);
        g.drawString("↓ : Drop", sidebarX + 10, 310);
        g.drawString("P : Pause", sidebarX + 10, 330);
    }
    
    private void drawMenu(Graphics g) {
        // Semi-transparent overlay
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(10, 10, WIDTH * BLOCK_SIZE, HEIGHT * BLOCK_SIZE);
        
        // Menu text
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        drawCenteredString(g, "BRICK GAME", WIDTH * BLOCK_SIZE / 2 + 10, 100);
        
        g.setFont(new Font("Arial", Font.BOLD, 16));
        drawCenteredString(g, "9999-in-1", WIDTH * BLOCK_SIZE / 2 + 10, 130);
        drawCenteredString(g, "TETRIS", WIDTH * BLOCK_SIZE / 2 + 10, 180);
        
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        drawCenteredString(g, "Press ENTER to start", WIDTH * BLOCK_SIZE / 2 + 10, 250);
    }
    
    private void drawGameOver(Graphics g) {
        // Semi-transparent overlay
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(10, 10, WIDTH * BLOCK_SIZE, HEIGHT * BLOCK_SIZE);
        
        // Game over text
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        drawCenteredString(g, "GAME OVER", WIDTH * BLOCK_SIZE / 2 + 10, 150);
        
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        drawCenteredString(g, "Score: " + score, WIDTH * BLOCK_SIZE / 2 + 10, 200);
        drawCenteredString(g, "Press ENTER for menu", WIDTH * BLOCK_SIZE / 2 + 10, 250);
    }
    
    private void drawCenteredString(Graphics g, String text, int x, int y) {
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        g.drawString(text, x - textWidth / 2, y);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BrickGameTetrisV2 game = new BrickGameTetrisV2();
            game.setVisible(true);
        });
    }
}