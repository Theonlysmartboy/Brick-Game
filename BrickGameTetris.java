import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferStrategy;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class BrickGameTetris extends JFrame {
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
    private final Color BG_COLOR = new Color(220, 220, 220);
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

    // Sound variables
    private Clip moveSound;
    private Clip lineClearSound;
    private Clip gameStartSound;
    private Clip levelUpSound;
    private Clip gameOverSound;
    private boolean soundsEnabled = true;
    private volatile boolean isGameActive = false;
    private volatile boolean allowMoveSounds = false;

    // Sound durations
    private static final int START_SOUND_DURATION = 12000;
    private static final int MOVE_SOUND_DURATION = 500;
    private static final int CLEAR_SOUND_DURATION = 600;
    private static final int LEVEL_UP_SOUND_DURATION = 2000;
    private static final int GAME_OVER_DURATION = 2000;

    // Sound priorities
    private static final int PRIORITY_HIGH = 2;
    private static final int PRIORITY_MED = 1;
    private static final int PRIORITY_LOW = 0;
    private ScheduledExecutorService soundExecutor = Executors.newSingleThreadScheduledExecutor();
    private long lastSoundEndTime = 0;

    // Animation variables
    private boolean isAnimating = false;
    private int animationStep = 0;
    private int rotationAngle = 0;
    private final int ANIMATION_DURATION = 12000;

    //Variable to track the next piece
    private int[][] nextPiece;
    private int nextPieceColor;

    public BrickGameTetris() {
        setTitle("BRICK GAME 9999-in-1 - TETRIS");
        setSize(WIDTH * BLOCK_SIZE + SIDEBAR_WIDTH + 16, HEIGHT * BLOCK_SIZE + 39);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        setIgnoreRepaint(true);

        setupGame();
        setupControls();
        initSounds();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        createBufferStrategy(2);
    }

    private void setupGame() {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid[y][x] = 0;
            }
        }
        score = 0;
        linesCleared = 0;
        level = 1;
        gameSpeed = 500;
        generateNextPiece(); // Initialize the first next piece
    }

    private void generateNextPiece() {
        nextPiece = TETROMINOS[random.nextInt(TETROMINOS.length)];
        nextPieceColor = 1 + random.nextInt(COLORS.length - 1);
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
            gameStartSound = loadSound("/assets/mr_9999_00.wav");
            moveSound = loadSound("/assets/mr_9999_14.wav");
            levelUpSound = loadSound("/assets/mr_9999_02.wav");
            lineClearSound = loadSound("/assets/mr_9999_15.wav");
            gameOverSound = loadSound("/assets/mr_9999_04.wav");

            // Verify all sounds loaded
            soundsEnabled = gameStartSound != null && moveSound != null 
                && levelUpSound != null && lineClearSound != null 
                && gameOverSound != null;
        } catch (Exception e) {
            System.out.println("Audio files not found - sounds disabled");
            soundsEnabled = false;
        }
    }

    private Clip loadSound(String path) throws Exception {
        AudioInputStream audio = AudioSystem.getAudioInputStream(getClass().getResourceAsStream(path));
        Clip clip = AudioSystem.getClip();
        clip.open(audio);
        return clip;
    }

    @Override
    public void paint(Graphics g) {
        BufferStrategy strategy = getBufferStrategy();
        if (strategy == null) {
            createBufferStrategy(2);
            strategy = getBufferStrategy();
        }

        do {
            Graphics graphics = strategy.getDrawGraphics();
            try {
                // Clear screen
                graphics.setColor(BG_COLOR);
                graphics.fillRect(0, 0, getWidth(), getHeight());

                if (isAnimating) {
                    drawAnimation(graphics);
                } else {
                    drawGameContent(graphics);
                }
            } finally {
                graphics.dispose();
            }
            strategy.show();
        } while (strategy.contentsLost());
    }

    private void drawGameContent(Graphics g) {
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

    private synchronized void playSoundWithPriorityDelay(Clip clip, int duration, int priority) {
        if (!soundsEnabled || clip == null) return;
        
        soundExecutor.execute(() -> {
            try {
                long currentTime = System.currentTimeMillis();
                // Only skip if a higher priority sound is playing and we're not HIGH priority
                if (currentTime < lastSoundEndTime && priority < PRIORITY_HIGH) {
                   // For MEDIUM priority sounds, only skip if another MED or HIGH is playing
                    if (priority == PRIORITY_MED && 
                        (lastSoundEndTime - currentTime) > CLEAR_SOUND_DURATION/2) {
                        return;
                    }
                    // Always allow HIGH priority sounds to play
                }
                
                clip.stop();
                clip.setFramePosition(0);
                clip.start();
                lastSoundEndTime = currentTime + duration;
            } catch (Exception e) {
                System.err.println("Sound error: " + e.getMessage());
            }
        });
    }

    private void startGame() {
        if (isAnimating) return;
        
        gameState = GameState.PLAYING;
        isGameActive = true;
        allowMoveSounds = true;
        
        setupGame();
        
        if (gameTimer != null) {
            gameTimer.stop();
        }
        gameTimer = new Timer(gameSpeed, e -> gameUpdate());
        newPiece();
        gameTimer.start();
    }

    private void playGameStartSound() {
        if (!soundsEnabled) {
            startGame();
            return;
        }

        // Start animation
        isAnimating = true;
        animationStep = 0;
        rotationAngle = 0;
        

        // Play sound immediately
        soundExecutor.execute(() -> {
            try {
                gameStartSound.stop();
                gameStartSound.setFramePosition(0);
                gameStartSound.start();
                lastSoundEndTime = System.currentTimeMillis() + START_SOUND_DURATION;
            } catch (Exception e) {
                System.err.println("Start sound error: " + e.getMessage());
            }
        });

        // Start animation timer
        Timer animationTimer = new Timer(16, new ActionListener() {
            private long startTime = System.currentTimeMillis();

            @Override
            public void actionPerformed(ActionEvent e) {
                long elapsed = System.currentTimeMillis() - startTime;
                animationStep = (int)(elapsed / 16);
                rotationAngle = (int)(elapsed / 20); // Slower rotation

                // End animation when sound finishes or max steps reached
                if (elapsed >= START_SOUND_DURATION) {
                    ((Timer)e.getSource()).stop();
                    isAnimating = false;
                    startGame(); //start actual game
                }
                repaint();
            }
        });
        animationTimer.setCoalesce(true);
        animationTimer.start();
    }

    private void playMoveSound() {
        if (!soundsEnabled || !allowMoveSounds)
            return;
        playSoundWithPriorityDelay(moveSound, MOVE_SOUND_DURATION, PRIORITY_LOW);
    }

    private void playLevelUpSound() {
        if (!soundsEnabled)
            return;
        playSoundWithPriorityDelay(levelUpSound, LEVEL_UP_SOUND_DURATION, PRIORITY_MED);
    }

    private void playLineClearSound() {
        if (!soundsEnabled)
            return;
        playSoundWithPriorityDelay(lineClearSound, CLEAR_SOUND_DURATION, PRIORITY_MED);
    }

    private void playGameOverSound() {
        if (!soundsEnabled)
            return;
        playSoundWithPriorityDelay(gameOverSound, GAME_OVER_DURATION, PRIORITY_HIGH);
    }

    private void handleMenuInput(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            playGameStartSound(); // No callback needed now
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
                boolean moved = moveDown();
                if (!moved || !moveDown()) { // Try to move down twice
                    if (!moved) {  // If first move failed, just merge
                        mergePiece();
                        clearLines();
                        newPiece();
                    }
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
        long currentTime = System.currentTimeMillis();

        // Don't process game update if important sound is playing
        if (currentTime < lastSoundEndTime - 100) { // 100ms buffer
            return;
        }
        if (!moveDown()) {
            mergePiece();
            clearLines();
            newPiece();
        }
        repaint();
    }

    private void newPiece() {
        currentPiece = nextPiece;
        currentColor = nextPieceColor;
        currentX = WIDTH / 2 - currentPiece[0].length / 2;
        currentY = 0;

         // Generate new preview piece
        generateNextPiece();

        // Only play move sound if game is fully active
        if (isGameActive && allowMoveSounds) {
            // Only play sound if allowed
            playMoveSound();
        }

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
        if (allowMoveSounds) { // Only play sound if allowed
            SwingUtilities.invokeLater(() -> {
                playMoveSound(); // Play sound with each movement
            });
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
        int linesRemoved = 0;
        int oldLevel = level; // Store current level before potential change

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
            // Check if level changed
            int newLevel = 1 + (linesCleared / 10);
            if (newLevel > oldLevel) {
                level = newLevel;
                gameSpeed = Math.max(100, 500 - (level * 40));
                gameTimer.setDelay(gameSpeed);
                playLevelUpSound(); // Play level up sound
            }

            playLineClearSound(); // Play long beep for line clear
        }
    }

    private int calculateScore(int lines) {
        switch (lines) {
            case 1:
                return 100 * level;
            case 2:
                return 300 * level;
            case 3:
                return 500 * level;
            case 4:
                return 800 * level;
            default:
                return 0;
        }
    }

    private void gameOver() {
        isGameActive = false;
        allowMoveSounds = false; // Disable move sounds when game ends
        playGameOverSound(); // Play end sound
        gameTimer.stop();
        gameState = GameState.GAME_OVER;
    }

    private void drawBlock(Graphics g, int x, int y, int colorIdx) {
        Color color = COLORS[colorIdx];

        // Check if this is a preview block (smaller size)
        boolean isPreview = (x < 0 || y < 0); // Negative coordinates indicate preview

        int size = isPreview ? BLOCK_SIZE/2 : BLOCK_SIZE;
        int drawX = isPreview ? -x : 10 + x * BLOCK_SIZE;
        int drawY = isPreview ? -y : 10 + y * BLOCK_SIZE;

        // Block with 3D effect
        g.setColor(color);
        g.fillRect(10 + x * BLOCK_SIZE, 10 + y * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);

        // Highlight
        g.setColor(color.brighter());
        g.drawLine(drawX, drawY, drawX + size - 1, drawY);
        g.drawLine(drawX, drawY, drawX, drawY + size - 1);

        // Shadow
        g.setColor(color.darker());
        g.drawLine(drawX + size - 1, drawY, drawX + size - 1, drawY + size - 1);
        g.drawLine(drawX, drawY + size - 1, drawX + size - 1, drawY + size - 1);
    }

    private void drawAnimation(Graphics g) {
        Graphics2D g2d = (Graphics2D)g;
        
        // Clear background
        g2d.setColor(BG_COLOR);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        
        // Calculate animation progress (0.0 to 1.0)
        float progress = Math.min(1.0f, (float)animationStep * 30 / ANIMATION_DURATION);
        
        // Draw expanding square pattern
        int maxDist = Math.max(WIDTH, HEIGHT)/2;
        int currentDist = (int)(progress * maxDist);
        
        // Draw grid of blocks expanding outward
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int distX = Math.abs(x - WIDTH/2);
                int distY = Math.abs(y - HEIGHT/2);
                int distance = Math.max(distX, distY);
                
                if (distance <= currentDist) {
                    // Calculate color based on position and animation step
                    int colorIdx = 1 + ((x + y + animationStep/3) % (COLORS.length-1));
                    drawAnimatedBlock(g2d, x, y, colorIdx, 
                        rotationAngle * (distance+1) / 2); // Vary rotation by distance
                }
            }
        }
        
        // Draw center piece that grows and rotates
        int centerSize = (int)(progress * 4); // Grows from 0 to 4 blocks
        if (centerSize > 0) {
            int[][] demoPiece = new int[centerSize][centerSize];
            for (int y = 0; y < centerSize; y++) {
                for (int x = 0; x < centerSize; x++) {
                    demoPiece[y][x] = 1;
                }
            }
            
            Graphics2D centerG = (Graphics2D)g2d.create();
            int centerX = 10 + WIDTH * BLOCK_SIZE / 2 - (centerSize * BLOCK_SIZE)/2;
            int centerY = 10 + HEIGHT * BLOCK_SIZE / 2 - (centerSize * BLOCK_SIZE)/2;
            
            centerG.translate(centerX, centerY);
            centerG.rotate(Math.toRadians(rotationAngle), 
                        (centerSize * BLOCK_SIZE)/2, 
                        (centerSize * BLOCK_SIZE)/2);
            
            for (int y = 0; y < demoPiece.length; y++) {
                for (int x = 0; x < demoPiece[y].length; x++) {
                    if (demoPiece[y][x] != 0) {
                        centerG.setColor(COLORS[7]); // Orange
                        centerG.fillRect(x * BLOCK_SIZE, y * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
                    }
                }
            }
            centerG.dispose();
        }
        
        // Draw "BRICK GAME" text that appears
        if (progress > 0.3) {
            float textAlpha = Math.min(1.0f, (progress - 0.3f) / 0.2f);
            g2d.setColor(new Color(255, 255, 255, (int)(textAlpha * 255)));
            g2d.setFont(new Font("Arial", Font.BOLD, 24));
            
            String text = "BRICK GAME";
            FontMetrics fm = g2d.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = HEIGHT * BLOCK_SIZE / 3;
            
            g2d.drawString(text, x, y);
        }
        
        // Draw "TETRIS" text that appears later
        if (progress > 0.6) {
            float textAlpha = Math.min(1.0f, (progress - 0.6f) / 0.2f);
            g2d.setColor(new Color(255, 255, 255, (int)(textAlpha * 255)));
            g2d.setFont(new Font("Arial", Font.BOLD, 36));
            
            String text = "TETRIS";
            FontMetrics fm = g2d.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = HEIGHT * BLOCK_SIZE * 2 / 3;
            
            g2d.drawString(text, x, y);
        }
    }

    private void drawAnimatedBlock(Graphics g, int x, int y, int colorIdx, int angle) {
        Graphics2D g2d = (Graphics2D) g;
        AffineTransform originalTransform = g2d.getTransform();

        try {
            int centerX = 10 + x * BLOCK_SIZE + BLOCK_SIZE / 2;
            int centerY = 10 + y * BLOCK_SIZE + BLOCK_SIZE / 2;

            g2d.translate(centerX, centerY);
            g2d.rotate(Math.toRadians(angle));
            g2d.translate(-BLOCK_SIZE / 2, -BLOCK_SIZE / 2);

            Color color = COLORS[colorIdx];
            g2d.setColor(color);
            g2d.fillRect(0, 0, BLOCK_SIZE, BLOCK_SIZE);

            // Optimized highlight/shadow drawing
            g2d.setColor(color.brighter());
            g2d.drawLine(0, 0, BLOCK_SIZE - 1, 0);
            g2d.drawLine(0, 0, 0, BLOCK_SIZE - 1);

            g2d.setColor(color.darker());
            g2d.drawLine(BLOCK_SIZE - 1, 0, BLOCK_SIZE - 1, BLOCK_SIZE - 1);
            g2d.drawLine(0, BLOCK_SIZE - 1, BLOCK_SIZE - 1, BLOCK_SIZE - 1);

        } finally {
            g2d.setTransform(originalTransform);
        }
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

        // Next piece preview
        g.drawString("NEXT:", sidebarX + 10, 80);
        if (nextPiece != null) {
            // Calculate center position for the preview
            int previewX = sidebarX + (SIDEBAR_WIDTH - nextPiece[0].length * BLOCK_SIZE/2) / 2;
            int previewY = 110;
            
            // Draw the next piece preview
            for (int y = 0; y < nextPiece.length; y++) {
                for (int x = 0; x < nextPiece[y].length; x++) {
                    if (nextPiece[y][x] != 0) {
                        drawBlock(g, 
                            (previewX + x * BLOCK_SIZE/2 - 10) / BLOCK_SIZE, 
                            (previewY + y * BLOCK_SIZE/2 - 10) / BLOCK_SIZE, 
                            nextPieceColor);
                    }
                }
            }
        }

        // Score
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.drawString("SCORE:", sidebarX + 10, 160);
        g.drawString(String.valueOf(score), sidebarX + 10, 180);

        //Level
        g.drawString("LEVEL:", sidebarX + 10, 210);
        g.drawString(String.valueOf(level), sidebarX + 10, 230);

        // Add speed indicator
        g.drawString("SPEED:", sidebarX + 10, 260);
        g.drawString(String.valueOf(500 - gameSpeed) + "x", sidebarX + 10, 280); // Shows speed multiplier

        //Lines Removed
        g.drawString("LINES:", sidebarX + 10, 310);
        g.drawString(String.valueOf(linesCleared), sidebarX + 10, 330);

        // Controls help
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        g.drawString("CONTROLS:", sidebarX + 10, 370);
        g.drawString("← → : Move", sidebarX + 10, 390);
        g.drawString("↑ : Rotate", sidebarX + 10, 410);
        g.drawString("↓ : Drop", sidebarX + 10, 430);
        g.drawString("P : Pause", sidebarX + 10, 450);
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

    @Override
    public void dispose() {
        soundExecutor.shutdownNow();
        if (levelUpSound != null)
            levelUpSound.close();
        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BrickGameTetris game = new BrickGameTetris();
            game.setVisible(true);
            
            // Initialize buffer strategy after showing
            game.createBufferStrategy(2);
            
            // Start rendering loop at 60 FPS
            new Timer(16, e -> game.repaint()).start();
        });
    }
}