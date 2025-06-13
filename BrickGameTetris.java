import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Random;

public class BrickGameTetris extends JFrame {
    private static final int WIDTH = 10;
    private static final int HEIGHT = 20;
    private static final int BLOCK_SIZE = 30;
    
    private int[][] grid = new int[HEIGHT][WIDTH];
    private int[][] currentPiece;
    private int currentX, currentY;
    private int currentColor;
    
    private Timer timer;
    private boolean gameOver = false;
    
    // Tetromino shapes (simplified for Brick Game style)
    private final int[][][] TETROMINOS = {
        {{1, 1, 1, 1}}, // I
        {{1, 1}, {1, 1}}, // O
        {{1, 1, 1}, {0, 1, 0}}, // T
        {{1, 1, 1}, {1, 0, 0}}, // L
        {{1, 1, 1}, {0, 0, 1}}, // J
        {{1, 1, 0}, {0, 1, 1}}, // Z
        {{0, 1, 1}, {1, 1, 0}}  // S
    };
    
    // Brick Game style colors (simplified)
    private final Color[] COLORS = {
        Color.BLACK,        // 0 - empty
        Color.RED,          // 1
        Color.BLUE,         // 2
        Color.GREEN,        // 3
        Color.YELLOW,       // 4
        Color.MAGENTA,      // 5
        Color.CYAN,         // 6
        Color.ORANGE        // 7
    };
    
    public BrickGameTetris() {
        setTitle("Brick Game Tetris");
        setSize(WIDTH * BLOCK_SIZE + 16, HEIGHT * BLOCK_SIZE + 39);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        
        // Initialize game
        newPiece();
        
        // Set up timer for game loop
        timer = new Timer(500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!gameOver) {
                    if (!moveDown()) {
                        mergePiece();
                        clearLines();
                        newPiece();
                    }
                    repaint();
                }
            }
        });
        timer.start();
        
        // Keyboard controls
        addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (gameOver) return;
                
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT:
                        moveLeft();
                        break;
                    case KeyEvent.VK_RIGHT:
                        moveRight();
                        break;
                    case KeyEvent.VK_DOWN:
                        moveDown();
                        break;
                    case KeyEvent.VK_UP:
                        rotate();
                        break;
                }
                repaint();
            }
            
            @Override public void keyTyped(KeyEvent e) {}
            @Override public void keyReleased(KeyEvent e) {}
        });
    }
    
    private void newPiece() {
        Random random = new Random();
        currentPiece = TETROMINOS[random.nextInt(TETROMINOS.length)];
        currentColor = 1 + random.nextInt(COLORS.length - 1);
        currentX = WIDTH / 2 - currentPiece[0].length / 2;
        currentY = 0;
        
        // Check if game over
        if (collision()) {
            gameOver = true;
            timer.stop();
            JOptionPane.showMessageDialog(this, "Game Over!");
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
                y++; // Check the same line again
            }
        }
    }
    
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        
        // Draw grid
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (grid[y][x] != 0) {
                    g.setColor(COLORS[grid[y][x]]);
                    g.fillRect(x * BLOCK_SIZE, y * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
                }
                g.setColor(Color.DARK_GRAY);
                g.drawRect(x * BLOCK_SIZE, y * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
            }
        }
        
        // Draw current piece
        if (!gameOver) {
            for (int y = 0; y < currentPiece.length; y++) {
                for (int x = 0; x < currentPiece[y].length; x++) {
                    if (currentPiece[y][x] != 0) {
                        g.setColor(COLORS[currentColor]);
                        g.fillRect((currentX + x) * BLOCK_SIZE, 
                                  (currentY + y) * BLOCK_SIZE, 
                                  BLOCK_SIZE, BLOCK_SIZE);
                        g.setColor(Color.DARK_GRAY);
                        g.drawRect((currentX + x) * BLOCK_SIZE, 
                                  (currentY + y) * BLOCK_SIZE, 
                                  BLOCK_SIZE, BLOCK_SIZE);
                    }
                }
            }
        }
        
        // Draw Brick Game-style border
        g.setColor(Color.BLACK);
        g.drawRect(0, 0, WIDTH * BLOCK_SIZE, HEIGHT * BLOCK_SIZE);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BrickGameTetris game = new BrickGameTetris();
            game.setVisible(true);
        });
    }
}