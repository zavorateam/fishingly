package fishingly;

import java.util.Random;
import java.io.*;
import javax.microedition.rms.*;
import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.game.Sprite;

public class FishingCanvas extends GameCanvas implements Runnable {
    private FishingMIDlet midlet;
    private volatile boolean running;
    private boolean isPaused;

    private static final int STATE_MENU = 0;
    private static final int STATE_GAME = 1;
    private static final int STATE_SHOP = 2;
    private static final int STATE_SETTINGS = 3;
    private int gameState = STATE_MENU;
    private int selectedMenuIdx = 0; // Для кнопочных телефонов

    private int difficulty = 0;
    private String[] diffNames = {"Легко", "Нормально", "Хард"};
    
    // Экономика и прокачка (Сохраняемые данные)
    private int points;
    private int upwardCatchChance = 67; 
    private int unlockedSkins = 1; 
    private int currentSkin = 0; 
    private int hookSpeedLevel = 0; // Увеличение скорости (макс 5)
    private int startLivesLevel = 0; // Доп. снасти (макс 10 итого)
    
    // Временные данные забега
    private int lives;
    private int fishCount; 
    private int[] fishBag = new int[9999];
    private int currentCatchValue = 0;
    private int currentCatchType = 0; // Чтобы знать, кого тащим (1 - рыба, 6 - щука, 5 - краб)
    private boolean isHeadshot = false;
    private boolean gameOver;

    private int hookX, hookY;
    private int startHookY = 110; 
    private int hookSpeed = 4;
    private int hookState = 0; 

    private int maxObjects = 25;
    private int[] objX = new int[maxObjects];
    private int[] objY = new int[maxObjects];
    private int[] objSpeed = new int[maxObjects];
    private int[] objType = new int[maxObjects]; 
    private boolean[] objActive = new boolean[maxObjects];

    private Random rnd = new Random();

    // Спрайты
    private Image imgWater, imgIce, imgSeaweed, imgShadow;
    private Image imgFishHead, imgPikeHead;
    private Image imgBoot, imgCan, imgCooler;
    private Image imgJelly1_L, imgJelly1_R, imgJelly2_L, imgJelly2_R;
    private Image imgPenguinIdle, imgPenguinPull, imgPenguinShock;
    private Image imgPenguinNegative, imgHat, imgMask;
    private Image[] imgFish1_Depth = new Image[3];
    private Image[] imgFish2_Depth = new Image[3];
    private Image imgPike1, imgPike2; 
    private Image imgCrab1, imgCrab2; 
    private Image[] imgFishHead_Depth = new Image[3];

    private boolean jellyDamageTakenThisPull = false;
    private int currentCatchDir = 1;
    private int currentCatchDepth = 0;

    private int animTick = 0;

    // КООРДИНАТЫ
    private final int[] SPR_PENG_IDLE   = {53, 207, 52, 36};   
    private final int[] SPR_PENG_PULL   = {140, 204, 51, 36};  
    private final int[] SPR_PENG_SHOCK  = {438, 139, 52, 36};  
    private final int[] SPR_FISH_1      = {257, 126, 36, 18};  
    private final int[] SPR_FISH_2      = {257, 145, 36, 18};  
    private final int[] SPR_FISH_HEAD   = {73, 191, 19, 14};   
    private final int[] SPR_JELLY_1     = {435, 67, 29, 29}; 
    private final int[] SPR_JELLY_2     = {464, 67, 30, 30}; 
    private final int[] SPR_BOOT        = {100, 105, 28, 30};    
    private final int[] SPR_CAN         = {193, 146, 23, 28};   
    private final int[] SPR_COOLER      = {227, 219, 38, 24}; 
    private final int[] SPR_ICE         = {100, 0, 333, 10};     
    private final int[] SPR_SEAWEED     = {0, 0, 100, 138}; 
    private final int[] SPR_CRAB        = {328, 93, 45, 20}; 
    private final int[] SPR_HAT         = {192, 200, 38, 22}; 
    private final int[] SPR_MASK        = {230, 198, 16, 21}; 

    private int coolerX = 15;
    private int coolerY;

    private final String[] SKIN_NAMES = {"Обычный", "Негатив", "Сапог", "Шляпа", "Медуза", "Маска"};
    private final int[] SKIN_COSTS = {0, 10000, 5000, 15000, 20000, 25000};

    public FishingCanvas(FishingMIDlet midlet) {
        super(true);
        this.midlet = midlet;
        setFullScreenMode(true);
        loadGame(); 
        loadSprites();
        generateShadow();
    }

    public void paint(Graphics g) {}

    // --- RMS ---
    public void saveGame() {
        try {
            RecordStore rs = RecordStore.openRecordStore("FishingSaveData", true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(points);
            dos.writeInt(upwardCatchChance);
            dos.writeInt(unlockedSkins);
            dos.writeInt(currentSkin);
            dos.writeInt(hookSpeedLevel);
            dos.writeInt(startLivesLevel);
            byte[] data = baos.toByteArray();
            
            if (rs.getNumRecords() == 0) rs.addRecord(data, 0, data.length);
            else rs.setRecord(1, data, 0, data.length);
            rs.closeRecordStore();
        } catch (Exception e) {}
    }

    private void loadGame() {
        try {
            RecordStore rs = RecordStore.openRecordStore("FishingSaveData", true);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
                points = dis.readInt();
                upwardCatchChance = dis.readInt();
                unlockedSkins = dis.readInt();
                currentSkin = dis.readInt();
                hookSpeedLevel = dis.readInt();
                startLivesLevel = dis.readInt();
            }
            rs.closeRecordStore();
        } catch (Exception e) {}
    }

    // --- ИЗОБРАЖЕНИЯ ---
    private Image extractSprite(Image src, int[] rect) {
        if (src == null) return null;
        try { return Image.createImage(src, rect[0], rect[1], rect[2], rect[3], Sprite.TRANS_NONE); } 
        catch (Exception e) { return null; }
    }

    private Image mirrorImage(Image src) {
        if (src == null) return null;
        int w = src.getWidth(); int h = src.getHeight();
        int[] raw = new int[w * h];
        src.getRGB(raw, 0, w, 0, 0, w, h);
        int[] mirrored = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                mirrored[y * w + x] = raw[y * w + (w - 1 - x)];
            }
        }
        return Image.createRGBImage(mirrored, w, h, true);
    }

    private Image scaleImage15(Image src) {
        if (src == null) return null;
        int w = src.getWidth(); int h = src.getHeight();
        int[] raw = new int[w * h];
        src.getRGB(raw, 0, w, 0, 0, w, h);
        int sw = w * 3 / 2; int sh = h * 3 / 2;
        int[] scaled = new int[sw * sh];
        for (int i = 0; i < sh; i++) {
            for (int j = 0; j < sw; j++) {
                scaled[i * sw + j] = raw[(i * 2 / 3) * w + (j * 2 / 3)];
            }
        }
        return Image.createRGBImage(scaled, sw, sh, true);
    }

    private Image applyGrayscale(Image src) {
        if (src == null) return null;
        int w = src.getWidth(); int h = src.getHeight();
        int[] raw = new int[w * h];
        src.getRGB(raw, 0, w, 0, 0, w, h);
        for (int i = 0; i < raw.length; i++) {
            int p = raw[i];
            int a = (p >> 24) & 0xFF;
            if (a > 0) {
                int r = (p >> 16) & 0xFF; int g = (p >> 8) & 0xFF; int b = p & 0xFF;
                int avg = (r + g + b) / 3;
                raw[i] = (a << 24) | (avg << 16) | (avg << 8) | avg;
            }
        }
        return Image.createRGBImage(raw, w, h, true);
    }

    private Image applyRedTint(Image src, int redBoost) {
        if (src == null) return null;
        int w = src.getWidth(); int h = src.getHeight();
        int[] raw = new int[w * h];
        src.getRGB(raw, 0, w, 0, 0, w, h);
        for (int i = 0; i < raw.length; i++) {
            int p = raw[i];
            int a = (p >> 24) & 0xFF;
            if (a > 0) {
                int r = (p >> 16) & 0xFF; int g = (p >> 8) & 0xFF; int b = p & 0xFF;
                r = Math.min(255, r + redBoost);
                g = Math.max(0, g - redBoost/2);
                b = Math.max(0, b - redBoost/2);
                raw[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return Image.createRGBImage(raw, w, h, true);
    }

    private Image applyNegative(Image src) {
        if (src == null) return null;
        int w = src.getWidth(); int h = src.getHeight();
        int[] raw = new int[w * h];
        src.getRGB(raw, 0, w, 0, 0, w, h);
        for (int i = 0; i < raw.length; i++) {
            int p = raw[i];
            int a = (p >> 24) & 0xFF;
            if (a > 0) {
                int r = 255 - ((p >> 16) & 0xFF);
                int g = 255 - ((p >> 8) & 0xFF);
                int b = 255 - (p & 0xFF);
                raw[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return Image.createRGBImage(raw, w, h, true);
    }

    private void generateShadow() {
        int sh = getHeight() - startHookY;
        if (sh <= 0) sh = 100;
        int[] grad = new int[32 * sh];
        for(int y = 0; y < sh; y++) {
            int alpha = (int)(180.0 * y / sh); 
            int col = (alpha << 24) | 0x000000;
            for(int x = 0; x < 32; x++) grad[y*32 + x] = col;
        }
        imgShadow = Image.createRGBImage(grad, 32, sh, true);
    }

    private void loadSprites() {
        Image rawAssets = null;
        try { rawAssets = Image.createImage("/assets.png"); } catch (Exception e) {}
        
        if (rawAssets != null) {
            Image baseFish1 = extractSprite(rawAssets, SPR_FISH_1);
            Image baseFish2 = extractSprite(rawAssets, SPR_FISH_2);
            
            imgFish1_Depth[0] = baseFish1; 
            imgFish2_Depth[0] = baseFish2;
            imgFish1_Depth[1] = applyRedTint(baseFish1, 60);
            imgFish2_Depth[1] = applyRedTint(baseFish2, 60);
            imgFish1_Depth[2] = applyRedTint(baseFish1, 130);
            imgFish2_Depth[2] = applyRedTint(baseFish2, 130);

            imgPike1 = applyGrayscale(baseFish1);
            imgPike2 = applyGrayscale(baseFish2);

            imgFishHead = extractSprite(rawAssets, SPR_FISH_HEAD);
            imgFishHead_Depth[0] = imgFishHead;
            imgFishHead_Depth[1] = applyRedTint(imgFishHead, 60);
            imgFishHead_Depth[2] = applyRedTint(imgFishHead, 130);
            imgPikeHead = applyGrayscale(imgFishHead);
            
            imgJelly1_L = extractSprite(rawAssets, SPR_JELLY_1); imgJelly1_R = imgJelly1_L;
            imgJelly2_L = extractSprite(rawAssets, SPR_JELLY_2); imgJelly2_R = imgJelly2_L;

            imgBoot    = extractSprite(rawAssets, SPR_BOOT);
            imgCan     = extractSprite(rawAssets, SPR_CAN);
            imgCooler  = extractSprite(rawAssets, SPR_COOLER);
            imgIce     = extractSprite(rawAssets, SPR_ICE);
            imgSeaweed = extractSprite(rawAssets, SPR_SEAWEED);
            
            imgCrab1   = extractSprite(rawAssets, SPR_CRAB); 
            imgCrab2   = mirrorImage(imgCrab1);
            imgHat     = extractSprite(rawAssets, SPR_HAT);  
            imgMask    = extractSprite(rawAssets, SPR_MASK); 

            imgPenguinIdle  = scaleImage15(extractSprite(rawAssets, SPR_PENG_IDLE));
            imgPenguinPull  = scaleImage15(extractSprite(rawAssets, SPR_PENG_PULL));
            imgPenguinShock = scaleImage15(extractSprite(rawAssets, SPR_PENG_SHOCK));
            imgPenguinNegative = applyNegative(imgPenguinIdle);

            rawAssets = null; 
        }
        try { imgWater = Image.createImage("/water.png"); } catch (Exception e) {}
    }

    private void initGame() {
        fishCount = 0;
        gameOver = false;
        hookX = getWidth() / 2;
        hookY = startHookY;
        hookState = 0;
        hookSpeed = 4 + hookSpeedLevel; // Применяем прокачку скорости
        isHeadshot = false;
        jellyDamageTakenThisPull = false;
        currentCatchDir = 1;
        currentCatchDepth = 0;

        if (imgCooler != null) coolerY = startHookY - imgCooler.getHeight() - 5;
        else coolerY = startHookY - 30;

        int baseLives = difficulty == 0 ? 5 : (difficulty == 1 ? 3 : 1);
        lives = baseLives + startLivesLevel;
        if (lives > 10) lives = 10;

        if (difficulty == 0) spawnObjects(5, 2, 2, 1); 
        else if (difficulty == 1) spawnObjects(4, 4, 3, 2); 
        else spawnObjects(3, 6, 5, 2); 
        
        gameState = STATE_GAME;
        selectedMenuIdx = 0;
    }

    private void spawnObjects(int fCount, int jCount, int tCount, int pCount) {
        for (int i = 0; i < maxObjects; i++) objActive[i] = false;
        int index = 0;
        index = populate(index, fCount, 1); 
        index = populate(index, jCount, 2); 
        index = populate(index, tCount, 3); 
        populate(index, pCount, 6); 
    }

    private int populate(int startIndex, int count, int type) {
        for (int i = 0; i < count; i++) {
            if (startIndex < maxObjects) {
                resetObject(startIndex, type);
                startIndex++;
            }
        }
        return startIndex;
    }

    private void resetObject(int i, int type) {
        objActive[i] = true;
        objType[i] = (type == 3 || type == 4) ? ((rnd.nextInt(2) == 0) ? 3 : 4) : type;
        
        if (type == 5) { // Краб спавнится у дна
            objY[i] = getHeight() - (imgCrab1 != null ? imgCrab1.getHeight() : 20) + 8;
        } else {
            objY[i] = startHookY + 40 + rnd.nextInt(Math.max(10, getHeight() - startHookY - 80)); 
        }
        
        objX[i] = (rnd.nextInt(2) == 0) ? -50 : getWidth() + 50; 
        
        int baseSpeed = 1 + rnd.nextInt(3 + difficulty);
        if (type == 6) baseSpeed += 4; 
        if (type == 5) baseSpeed = 1 + rnd.nextInt(2); 
        
        objSpeed[i] = (objX[i] > 0) ? -baseSpeed : baseSpeed; 
    }

    private void manageCrabs() {
        int crabsCount = 0;
        for (int i = 0; i < maxObjects; i++) {
            if (objActive[i] && objType[i] == 5) crabsCount++;
        }
        
        // Спавним краба если их меньше 2 с небольшой вероятностью
        if (crabsCount < 2 && rnd.nextInt(100) < 2) {
            for (int i = 0; i < maxObjects; i++) {
                if (!objActive[i]) {
                    resetObject(i, 5);
                    break;
                }
            }
        }
    }

    public void start() {
        running = true;
        isPaused = false;
        new Thread(this).start();
    }

    public void stop() { running = false; }
    public void pause() { isPaused = true; }

    public void run() {
        Graphics g = getGraphics();
        while (running) {
            if (!isPaused) {
                if (gameState == STATE_GAME && !gameOver) {
                    manageCrabs();
                    updateGameLogic();
                }
                animTick++;
            }
            drawScreen(g);
            flushGraphics();
            try { Thread.sleep(30); } catch (InterruptedException e) {}
        }
    }

    private void updateGameLogic() {
        if (hookState == 1) { 
            hookY += hookSpeed;
            if (hookY >= getHeight() - 10) hookState = 2; 
        } else if (hookState == 2 || hookState == 3) { 
            hookY -= hookSpeed;
            if (hookY <= startHookY) {
                if (hookState == 3) {
                    if (fishCount < fishBag.length) {
                        fishBag[fishCount] = currentCatchValue;
                        fishCount++;
                    }
                }
                hookY = startHookY;
                hookState = 0; 
                isHeadshot = false;
                jellyDamageTakenThisPull = false;
                currentCatchValue = 0;
                currentCatchType = 0;
                currentCatchDir = 1;
                currentCatchDepth = 0;
            }
        }

        for (int i = 0; i < maxObjects; i++) {
            if (!objActive[i]) continue;
            
            // Логика движения крабов
            if (objType[i] == 5) {
                if (rnd.nextInt(100) < 5) objSpeed[i] = -objSpeed[i]; // Случайная смена направления
                objX[i] += objSpeed[i];
                if (objX[i] < -60 || objX[i] > getWidth() + 60) {
                    objActive[i] = false; // Ушел за экран - исчез
                }
            } else {
                objX[i] += objSpeed[i];
                if ((objSpeed[i] > 0 && objX[i] > getWidth() + 60) || 
                    (objSpeed[i] < 0 && objX[i] < -60)) {
                    resetObject(i, objType[i]);
                }
            }

            if (hookState == 1 || hookState == 2 || hookState == 3) {
                if (Math.abs(hookX - objX[i]) < 25 && Math.abs(hookY - objY[i]) < 25) {
                    
                    if (objType[i] == 1 || objType[i] == 6) {
                        int headOffset = objSpeed[i] > 0 ? 18 : -18;
                        int headX = objX[i] + headOffset;
                        boolean hitHead = (Math.abs(hookX - headX) < 15 && Math.abs(hookY - objY[i]) < 15);
                        boolean hitBody = (Math.abs(hookX - objX[i]) < 20 && Math.abs(hookY - objY[i]) < 15);

                        if (hitHead || hitBody) {
                            boolean caught = false;
                            if (hookState == 1) caught = true;
                            else if (hookState == 2 && rnd.nextInt(100) < upwardCatchChance) caught = true;

                            if (caught) {
                                hookState = 3; 
                                isHeadshot = hitHead;
                                currentCatchType = objType[i];
                                currentCatchDir = objSpeed[i] > 0 ? 1 : -1;
                                
                                float depthFactor = (float)(hookY - startHookY) / (getHeight() - startHookY);
                                int depthIndex = depthFactor < 0.33f ? 0 : (depthFactor < 0.66f ? 1 : 2);
                                currentCatchDepth = depthIndex;
                                int basePoints = 10 + (int)(depthFactor * 90); 
                                
                                if (objType[i] == 6) basePoints *= 5; 
                                if (isHeadshot) basePoints += basePoints / 2;  
                                
                                currentCatchValue = basePoints;
                                resetObject(i, objType[i]); 
                            }
                        }
                    } 
                    else if (objType[i] == 5 && (hookState == 1 || hookState == 2)) {
                        if (rnd.nextInt(100) < 67) {
                            hookState = 3;
                            currentCatchType = 5;
                            currentCatchValue = 200; 
                            objActive[i] = false; // Краб пропадает
                        } else {
                            hookState = 2; 
                        }
                    }
                    else if (objType[i] == 2) {
                        if (hookState == 3) {
                            // РЫБА СРЫВАЕТСЯ ПРИ ПОПАДАНИИ НА МЕДУЗУ
                            hookState = 2;
                            currentCatchValue = 0;
                        }
                        if (!jellyDamageTakenThisPull) {
                            lives--;
                            jellyDamageTakenThisPull = true;
                            if (lives <= 0) {
                                gameOver = true;
                                saveGame();
                            }
                        }
                        hookState = 2; 
                    } 
                    else if ((objType[i] == 3 || objType[i] == 4) && hookState == 1) {
                        hookState = 2; 
                    }
                }
            }
        }
    }

    private void drawImg(Graphics g, Image img, int x, int y, int transform) {
        if (img == null) return;
        g.drawRegion(img, 0, 0, img.getWidth(), img.getHeight(), transform, x, y, Graphics.VCENTER | Graphics.HCENTER);
    }

    private void drawScreen(Graphics g) {
        g.setColor(0x87CEEB); 
        g.fillRect(0, 0, getWidth(), getHeight()); 

        if (gameState == STATE_MENU) drawMainMenu(g);
        else if (gameState == STATE_SHOP) drawShop(g);
        else if (gameState == STATE_SETTINGS) drawSettings(g);
        else drawGameView(g);
    }

    private void drawGameView(Graphics g) {
        if (imgWater != null) {
            int ww = imgWater.getWidth(); int wh = imgWater.getHeight();
            int offsetX = (int)(Math.sin(animTick * 0.02) * 8); 
            int offsetY = (int)(Math.cos(animTick * 0.015) * 4);
            int startX = (offsetX % ww) - ww;
            int startY = startHookY + (offsetY % wh) - wh;

            g.setClip(0, startHookY, getWidth(), getHeight() - startHookY);
            for (int wx = startX; wx < getWidth(); wx += ww) {
                for (int wy = startY; wy < getHeight(); wy += wh) {
                    g.drawImage(imgWater, wx, wy, Graphics.TOP | Graphics.LEFT);
                }
            }
            g.setClip(0, 0, getWidth(), getHeight());
        } else {
            g.setColor(0x0066CC);
            g.fillRect(0, startHookY, getWidth(), getHeight() - startHookY);
        }

        if (imgShadow != null) {
            for(int x = 0; x < getWidth(); x += 32) {
                g.drawImage(imgShadow, x, startHookY, Graphics.TOP | Graphics.LEFT);
            }
        }

        if (imgSeaweed != null) {
            int sw = imgSeaweed.getWidth(); int sh = imgSeaweed.getHeight();
            int startY = getHeight() - sh; 
            for (int row = 0; row < sh; row += 2) {
                int sliceH = Math.min(2, sh - row);
                double waveFactor = 1.0 - ((double)row / sh); 
                int offsetX = (int)(Math.sin(animTick * 0.1 + row * 0.05) * (4 * waveFactor));

                g.drawRegion(imgSeaweed, 0, row, sw, sliceH, Sprite.TRANS_NONE, 
                             -10 + offsetX, startY + row, Graphics.TOP | Graphics.LEFT);
                g.drawRegion(imgSeaweed, 0, row, sw, sliceH, Sprite.TRANS_MIRROR, 
                             getWidth() - sw + 10 + offsetX, startY + row, Graphics.TOP | Graphics.LEFT);
            }
        }

        if (imgIce != null) {
            for (int ix = 0; ix < getWidth(); ix += imgIce.getWidth()) {
                g.drawImage(imgIce, ix, startHookY - imgIce.getHeight() + 2, Graphics.TOP | Graphics.LEFT);
            }
        }

        if (imgCooler != null) {
            g.drawImage(imgCooler, coolerX, coolerY, Graphics.TOP | Graphics.LEFT);
            if (!gameOver && fishCount > 0) {
                g.setColor(0x006600); 
                g.drawString("Продать", coolerX, coolerY - 15, Graphics.TOP | Graphics.LEFT);
            }
        }

        int pw = imgPenguinIdle != null ? imgPenguinIdle.getWidth() : 40;
        int ph = imgPenguinIdle != null ? imgPenguinIdle.getHeight() : 40;
        int px = getWidth() - pw - 5;
        int py = startHookY - ph - 6; 
        
        Image activePenguin = imgPenguinIdle;
        if (currentSkin == 1 && imgPenguinNegative != null) activePenguin = imgPenguinNegative;
        
        if (gameOver && imgPenguinShock != null) activePenguin = imgPenguinShock;
        else if (hookState == 3 && imgPenguinPull != null) activePenguin = imgPenguinPull;
        
        if (activePenguin != null) g.drawImage(activePenguin, px, py, Graphics.TOP | Graphics.LEFT);

        if (currentSkin == 2 && imgBoot != null) g.drawImage(imgBoot, px + 45, py - 15, Graphics.TOP | Graphics.LEFT);
        else if (currentSkin == 3 && imgHat != null) g.drawImage(imgHat, px + 42, py - 5, Graphics.TOP | Graphics.LEFT);
        else if (currentSkin == 4 && imgJelly1_L != null) g.drawImage(imgJelly1_L, px + 45, py, Graphics.TOP | Graphics.LEFT);
        else if (currentSkin == 5 && imgMask != null) g.drawImage(imgMask, px + 50, py, Graphics.TOP | Graphics.LEFT);

        if (!gameOver) {
            int rodTipX = px + 4; int rodTipY = py + 8; 
            g.setColor(0x000000);
            g.drawLine(rodTipX, rodTipY, hookX, startHookY); 
            g.drawLine(hookX, startHookY, hookX, hookY); 

            g.setColor(0xFF0000); g.fillArc(hookX - 4, startHookY - 4, 8, 8, 0, 180);
            g.setColor(0xFFFFFF); g.fillArc(hookX - 4, startHookY - 4, 8, 8, 180, 180);
            g.setColor(0x000000); g.drawArc(hookX - 4, startHookY - 4, 8, 8, 0, 360);

            if (hookState == 3) {
                Image caughtBody = null;
                Image caughtHead = null;
                int transform = Sprite.TRANS_ROT270;

                if (currentCatchType == 6) {
                    caughtBody = imgPike1;
                    caughtHead = imgPikeHead;
                } else if (currentCatchType == 5) {
                    caughtBody = imgCrab1;
                    transform = Sprite.TRANS_MIRROR_ROT180;
                } else {
                    caughtBody = imgFish1_Depth[currentCatchDepth];
                    caughtHead = imgFishHead_Depth[currentCatchDepth];
                }

                if (caughtBody != null) drawImg(g, caughtBody, hookX, hookY, transform);
                
                // Рисуем голову у рыбы/щуки при вытягивании
                if (caughtHead != null && currentCatchType != 5) {
                    int hOff = currentCatchDir > 0 ? 20 : -20;
                    drawImg(g, caughtHead, hookX, hookY - 20, transform);
                }
            } else {
                g.setColor(0x555555); g.drawArc(hookX - 4, hookY - 4, 8, 8, 180, 180);
                g.drawLine(hookX + 4, hookY - 4, hookX + 4, hookY);
                g.setColor(0xFF8888); g.fillArc(hookX - 2, hookY - 2, 4, 6, 0, 360);
            }
        }

        for (int i = 0; i < maxObjects; i++) {
            if (!objActive[i]) continue;
            int transform = (objSpeed[i] > 0) ? Sprite.TRANS_NONE : Sprite.TRANS_MIRROR;
            
            if (objType[i] == 1) { 
                float depthF = (float)(objY[i] - startHookY) / (getHeight() - startHookY);
                int dIndex = depthF < 0.33f ? 0 : (depthF < 0.66f ? 1 : 2);
                Image frame = ((animTick / 5) % 2 == 0) ? imgFish1_Depth[dIndex] : imgFish2_Depth[dIndex];
                if (frame != null) {
                    drawImg(g, frame, objX[i], objY[i], transform);
                    Image headFrame = imgFishHead_Depth[dIndex];
                    if (headFrame != null) {
                        int hOff = objSpeed[i] > 0 ? 20 : -20;
                        drawImg(g, headFrame, objX[i] + hOff, objY[i], transform);
                    }
                }
            } 
            else if (objType[i] == 6) { 
                Image frame = ((animTick / 3) % 2 == 0) ? imgPike1 : imgPike2; 
                if (frame != null) {
                    drawImg(g, frame, objX[i], objY[i], transform);
                    if (imgPikeHead != null) {
                        int hOff = objSpeed[i] > 0 ? 20 : -20;
                        drawImg(g, imgPikeHead, objX[i] + hOff, objY[i], transform);
                    }
                }
            }
            else if (objType[i] == 5) { 
                Image frame = ((animTick / 7) % 2 == 0) ? imgCrab1 : imgCrab2;
                if (frame != null) drawImg(g, frame, objX[i], objY[i], Sprite.TRANS_MIRROR_ROT180);
            }
            else if (objType[i] == 2) { 
                Image frame = ((animTick / 6) % 2 == 0) ? imgJelly1_L : imgJelly2_L;
                if (frame != null) drawImg(g, frame, objX[i], objY[i], transform);
            }
            else if (objType[i] == 3 && imgBoot != null) drawImg(g, imgBoot, objX[i], objY[i], transform);
            else if (objType[i] == 4 && imgCan != null) drawImg(g, imgCan, objX[i], objY[i], Sprite.TRANS_NONE);
        }

        g.setColor(0x000000); 
        g.drawString("Рыбы: " + fishCount, 5, 2, Graphics.TOP | Graphics.LEFT);
        g.drawString("Очки: " + points, 5, 20, Graphics.TOP | Graphics.LEFT);
        g.drawString("Снасти: " + lives, getWidth() - 5, 2, Graphics.TOP | Graphics.RIGHT);
        
        g.setColor(0x555555);
        g.fillRect(getWidth() - 60, 20, 55, 20);
        g.setColor(0xFFFFFF);
        g.drawString("Меню", getWidth() - 32, 22, Graphics.TOP | Graphics.HCENTER);

        if (gameOver) {
            int cx = getWidth() / 2; int cy = getHeight() / 2;
            g.setColor(0xFF0000); g.fillRect(cx - 65, cy - 45, 130, 30);
            g.setColor(0xFFFFFF); g.drawString("ИГРА ОКОНЧЕНА", cx, cy - 38, Graphics.TOP | Graphics.HCENTER);
            g.setColor(0x00AA00); 
            if (selectedMenuIdx == 0) { g.setColor(0x00FF00); g.drawRect(cx - 66, cy - 6, 132, 37); }
            g.fillRect(cx - 65, cy - 5, 130, 35);   
            g.setColor(0xFFFFFF); g.drawString("В меню", cx, cy + 4, Graphics.TOP | Graphics.HCENTER);
        }
    }

    private void drawButton(Graphics g, String text, int x, int y, int w, int h, int color, boolean isSelected) {
        g.setColor(color);
        g.fillRect(x, y, w, h);
        if (isSelected) {
            g.setColor(0xFFFFFF);
            g.drawRect(x - 2, y - 2, w + 4, h + 4);
        }
        g.setColor(0xFFFFFF);
        g.drawString(text, x + w/2, y + h/2 - 8, Graphics.TOP | Graphics.HCENTER);
    }

    private void drawMainMenu(Graphics g) {
        int cx = getWidth()/2;
        g.setColor(0x000000);
        g.drawString("FiSHiNGLY", cx, 30, Graphics.TOP | Graphics.HCENTER);
        drawButton(g, "Играть", cx - 60, 80, 120, 35, 0x00AA00, selectedMenuIdx == 0);
        drawButton(g, "Магазин", cx - 60, 125, 120, 35, 0x0055AA, selectedMenuIdx == 1);
        drawButton(g, "Настройки", cx - 60, 170, 120, 35, 0xAA5500, selectedMenuIdx == 2);
        drawButton(g, "Выход", cx - 60, 215, 120, 35, 0xAA0000, selectedMenuIdx == 3);
    }

    private void drawSettings(Graphics g) {
        int cx = getWidth()/2;
        g.setColor(0x000000);
        g.drawString("НАСТРОЙКИ", cx, 30, Graphics.TOP | Graphics.HCENTER);
        drawButton(g, "Сложность: " + diffNames[difficulty], cx - 70, 100, 140, 35, 0x555555, selectedMenuIdx == 0);
        drawButton(g, "Назад", cx - 60, 200, 120, 35, 0x000000, selectedMenuIdx == 1);
    }

    private void drawShop(Graphics g) {
        int cx = getWidth()/2;
        g.setColor(0x000000);
        g.drawString("МАГАЗИН (Очки: " + points + ")", cx, 5, Graphics.TOP | Graphics.HCENTER);
        
        int startY = 25;
        
        String upgText = upwardCatchChance >= 100 ? "Шанс Макс" : "Шанс возврата - 8000";
        drawButton(g, upgText, cx - 100, startY, 200, 25, upwardCatchChance >= 100 ? 0x555555 : 0x008800, selectedMenuIdx == 0);

        String speedText = hookSpeedLevel >= 5 ? "Скорость Макс" : "Скорость лески - 5000";
        drawButton(g, speedText, cx - 100, startY + 30, 200, 25, hookSpeedLevel >= 5 ? 0x555555 : 0x008800, selectedMenuIdx == 1);

        String livesText = startLivesLevel >= 5 ? "Снасти Макс" : "Доп. Снасть - 12000";
        drawButton(g, livesText, cx - 100, startY + 60, 200, 25, startLivesLevel >= 5 ? 0x555555 : 0x008800, selectedMenuIdx == 2);

        for(int i = 1; i < SKIN_NAMES.length; i++) {
            boolean unlocked = (unlockedSkins & (1 << i)) != 0;
            boolean eq = (currentSkin == i);
            String txt = SKIN_NAMES[i] + (unlocked ? (eq ? " [НАДЕТ]" : "") : " - " + SKIN_COSTS[i]);
            int col = eq ? 0xAA8800 : (unlocked ? 0x0055AA : 0xAA0000);
            drawButton(g, txt, cx - 100, startY + 90 + (i-1)*28, 200, 24, col, selectedMenuIdx == (i+2));
        }

        drawButton(g, "Обычный пингвин", cx - 100, startY + 90 + 5*28, 200, 24, currentSkin == 0 ? 0xAA8800 : 0x0055AA, selectedMenuIdx == 8);
        drawButton(g, "Назад", cx - 60, getHeight() - 30, 120, 25, 0x000000, selectedMenuIdx == 9);
    }

    // --- УПРАВЛЕНИЕ ДЛЯ КНОПОЧНЫХ ТЕЛЕФОНОВ ---
    protected void keyPressed(int keyCode) {
        int action = getGameAction(keyCode);

        if (gameState == STATE_MENU) {
            if (action == Canvas.UP) { selectedMenuIdx--; if(selectedMenuIdx < 0) selectedMenuIdx = 3; }
            else if (action == Canvas.DOWN) { selectedMenuIdx++; if(selectedMenuIdx > 3) selectedMenuIdx = 0; }
            else if (action == Canvas.FIRE || keyCode == Canvas.KEY_NUM5) {
                if (selectedMenuIdx == 0) initGame();
                else if (selectedMenuIdx == 1) { gameState = STATE_SHOP; selectedMenuIdx = 0; }
                else if (selectedMenuIdx == 2) { gameState = STATE_SETTINGS; selectedMenuIdx = 0; }
                else if (selectedMenuIdx == 3) midlet.exit();
            }
        } 
        else if (gameState == STATE_SETTINGS) {
            if (action == Canvas.UP || action == Canvas.DOWN) { selectedMenuIdx = (selectedMenuIdx == 0) ? 1 : 0; }
            else if (action == Canvas.FIRE || keyCode == Canvas.KEY_NUM5) {
                if (selectedMenuIdx == 0) difficulty = (difficulty + 1) % 3;
                else { gameState = STATE_MENU; selectedMenuIdx = 2; }
            }
        }
        else if (gameState == STATE_SHOP) {
            if (action == Canvas.UP) { selectedMenuIdx--; if(selectedMenuIdx < 0) selectedMenuIdx = 9; }
            else if (action == Canvas.DOWN) { selectedMenuIdx++; if(selectedMenuIdx > 9) selectedMenuIdx = 0; }
            else if (action == Canvas.FIRE || keyCode == Canvas.KEY_NUM5) {
                handleShopClick(selectedMenuIdx);
            }
        }
        else if (gameState == STATE_GAME) {
            if (keyCode == Canvas.KEY_NUM7) {
                sellFish(); // Кнопка 7 - Продать 1 рыбу
            }
            else if (action == Canvas.FIRE || keyCode == Canvas.KEY_NUM5) {
                if (gameOver) { saveGame(); gameState = STATE_MENU; selectedMenuIdx = 0; }
                else if (hookState == 0) { hookX = getWidth() / 2; hookState = 1; jellyDamageTakenThisPull = false; currentCatchValue = 0; currentCatchType = 0; isHeadshot = false; }
            }
            else if (keyCode == Canvas.KEY_NUM3 || action == Canvas.RIGHT) {
                // Вызов меню
                saveGame(); gameState = STATE_MENU; selectedMenuIdx = 0;
            }
        }
    }

    private void handleShopClick(int idx) {
        if (idx == 0 && upwardCatchChance < 100 && points >= 8000) { points -= 8000; upwardCatchChance += 5; if (upwardCatchChance > 100) upwardCatchChance = 100; }
        else if (idx == 1 && hookSpeedLevel < 5 && points >= 5000) { points -= 5000; hookSpeedLevel++; }
        else if (idx == 2 && startLivesLevel < 5 && points >= 12000) { points -= 12000; startLivesLevel++; }
        else if (idx >= 3 && idx <= 7) {
            int skinIdx = idx - 2;
            boolean unlocked = (unlockedSkins & (1 << skinIdx)) != 0;
            if (unlocked) currentSkin = skinIdx;
            else if (points >= SKIN_COSTS[skinIdx]) { points -= SKIN_COSTS[skinIdx]; unlockedSkins |= (1 << skinIdx); currentSkin = skinIdx; }
        }
        else if (idx == 8) { currentSkin = 0; }
        else if (idx == 9) { saveGame(); gameState = STATE_MENU; selectedMenuIdx = 1; }
    }

    // Оставлено для сенсорных экранов
    protected void pointerPressed(int x, int y) {
        int cx = getWidth()/2;

        if (gameState == STATE_MENU) {
            if (x > cx-60 && x < cx+60) {
                if (y > 80 && y < 115) initGame();
                else if (y > 125 && y < 160) { gameState = STATE_SHOP; selectedMenuIdx = 0; }
                else if (y > 170 && y < 205) { gameState = STATE_SETTINGS; selectedMenuIdx = 0; }
                else if (y > 215 && y < 250) midlet.exit();
            }
        } 
        else if (gameState == STATE_SETTINGS) {
            if (x > cx-70 && x < cx+70 && y > 100 && y < 135) difficulty = (difficulty + 1) % 3;
            else if (x > cx-60 && x < cx+60 && y > 200 && y < 235) { gameState = STATE_MENU; selectedMenuIdx = 2; }
        }
        else if (gameState == STATE_SHOP) {
            if (y > getHeight() - 40) { handleShopClick(9); return; }
            int startY = 25;
            if (y > startY && y < startY + 25) handleShopClick(0);
            else if (y > startY + 30 && y < startY + 55) handleShopClick(1);
            else if (y > startY + 60 && y < startY + 85) handleShopClick(2);
            else {
                for(int i = 1; i < SKIN_NAMES.length; i++) {
                    int btnY = startY + 90 + (i-1)*28;
                    if (y > btnY && y < btnY + 24) handleShopClick(i + 2);
                }
                if (y > startY + 90 + 5*28 && y < startY + 90 + 5*28 + 24) handleShopClick(8);
            }
        }
        else if (gameState == STATE_GAME) {
            if (x > getWidth() - 60 && y > 20 && y < 40) { saveGame(); gameState = STATE_MENU; selectedMenuIdx = 0; return; }
            if (gameOver) {
                if (x > cx - 65 && x < cx + 65 && y > getHeight() / 2 - 5 && y < getHeight() / 2 + 30) { gameState = STATE_MENU; selectedMenuIdx = 0; }
                return;
            }
            int cw = imgCooler != null ? imgCooler.getWidth() : 50;
            int ch = imgCooler != null ? imgCooler.getHeight() : 25;
            if (x >= coolerX && x <= coolerX + cw && y >= coolerY - 15 && y <= coolerY + ch) { sellFish(); return; }
            if (hookState == 0) { hookX = x; hookState = 1; }
        }
    }

    private void sellFish() {
        if (fishCount > 0) {
            fishCount--; 
            points += fishBag[fishCount]; 
            saveGame(); 
        }
    }
}