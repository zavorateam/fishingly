package fishingly;

import java.util.Random;
import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.game.Sprite;

public class FishingCanvas extends GameCanvas implements Runnable, CommandListener {
    private FishingMIDlet midlet;
    private volatile boolean running;
    private boolean isPaused;

    private Command cmdSettings;
    private Command cmdExit;
    private Command cmdRestart;
    private Command cmdSell1;
    private Command cmdSellAll;
    private Command cmdBuyLife;

    private int difficulty = 0;
    private String[] diffNames = {"Легко", "Нормально", "Хард"};
    
    // Экономика
    private int lives;
    private int fishCount;
    private int points;    // Очки для покупки снастей
    
    // Инвентарь рыб (хранит стоимость каждой пойманной рыбы)
    private int[] fishBag = new int[9999];
    
    // Текущий улов
    private int currentCatchValue = 0;
    private boolean isHeadshot = false;

    private boolean gameOver;

    private int hookX, hookY;
    private int startHookY = 110; 
    private int hookSpeed = 4;
    private int hookState = 0; 

    private int maxObjects = 20;
    private int[] objX = new int[maxObjects];
    private int[] objY = new int[maxObjects];
    private int[] objSpeed = new int[maxObjects];
    private int[] objType = new int[maxObjects];
    private boolean[] objActive = new boolean[maxObjects];

    private Random rnd = new Random();

    // Спрайты
    private Image imgWater, imgIce, imgSeaweed;
    private Image imgFish1, imgFish2, imgFishHead;
    private Image imgBoot, imgCan, imgCooler;
    private Image imgJelly1_L, imgJelly1_R, imgJelly2_L, imgJelly2_R;
    private Image imgPenguinIdle, imgPenguinPull, imgPenguinShock;

    private int animTick = 0;

    // ВАШИ КООРДИНАТЫ
    private final int[] SPR_PENG_IDLE   = {53, 207, 52, 36};   
    private final int[] SPR_PENG_PULL   = {140, 204, 51, 36};  
    private final int[] SPR_PENG_SHOCK  = {438, 139, 52, 36};  
    private final int[] SPR_FISH_1      = {257, 126, 36, 18};  
    private final int[] SPR_FISH_2      = {257, 145, 36, 18};  
    private final int[] SPR_FISH_HEAD   = {73, 191, 19, 14};   
    private final int[] SPR_JELLY_1     = {438, 70, 25, 30}; 
    private final int[] SPR_JELLY_2     = {468, 70, 25, 30}; 
    private final int[] SPR_BOOT        = {100, 105, 28, 30};    
    private final int[] SPR_CAN         = {193, 146, 23, 28};   
    private final int[] SPR_COOLER      = {227, 222, 38, 24}; 
    private final int[] SPR_ICE         = {100, 0, 333, 10};     
    private final int[] SPR_SEAWEED     = {0, 0, 100, 138}; 

    private int coolerX = 10;
    private int coolerY;

    public FishingCanvas(FishingMIDlet midlet) {
        super(true);
        this.midlet = midlet;

        cmdSettings = new Command("Сложность: " + diffNames[difficulty], Command.SCREEN, 1);
        cmdRestart = new Command("Заново", Command.SCREEN, 2);
        cmdSell1 = new Command("Продать рыбу", Command.ITEM, 3);
        cmdSellAll = new Command("Продать всех", Command.ITEM, 4);
        cmdBuyLife = new Command("Снасть (500очк.)", Command.ITEM, 5);
        cmdExit = new Command("Выход", Command.EXIT, 6);

        addCommand(cmdSettings);
        setCommandListener(this);

        setFullScreenMode(true);
        loadSprites();
        initGame();
    }

    private Image extractSprite(Image src, int[] rect) {
        if (src == null) return null;
        try { return Image.createImage(src, rect[0], rect[1], rect[2], rect[3], Sprite.TRANS_NONE); } 
        catch (Exception e) { return null; }
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

    private Image rotateImage(Image src, double angleDegrees) {
        if (src == null) return null;
        int w = src.getWidth(); int h = src.getHeight();
        int[] raw = new int[w * h];
        src.getRGB(raw, 0, w, 0, 0, w, h);

        double rad = angleDegrees * 3.14159265 / 180.0;
        double cos = Math.cos(rad); double sin = Math.sin(rad);

        int newW = (int) (Math.abs(w * cos) + Math.abs(h * sin));
        int newH = (int) (Math.abs(h * cos) + Math.abs(w * sin));
        int[] rotated = new int[newW * newH];

        int cx = w / 2; int cy = h / 2;
        int newCx = newW / 2; int newCy = newH / 2;

        for (int y = 0; y < newH; y++) {
            for (int x = 0; x < newW; x++) {
                int rx = x - newCx; int ry = y - newCy;
                int srcX = (int) (rx * cos + ry * sin) + cx;
                int srcY = (int) (-rx * sin + ry * cos) + cy;

                if (srcX >= 0 && srcX < w && srcY >= 0 && srcY < h) {
                    rotated[y * newW + x] = raw[srcY * w + srcX];
                } else { rotated[y * newW + x] = 0x00000000; }
            }
        }
        return Image.createRGBImage(rotated, newW, newH, true);
    }

    private void loadSprites() {
        Image rawAssets = null;
        try { rawAssets = Image.createImage("/assets.png"); } catch (Exception e) {}
        
        if (rawAssets != null) {
            imgFish1    = extractSprite(rawAssets, SPR_FISH_1);
            imgFish2    = extractSprite(rawAssets, SPR_FISH_2);
            imgFishHead = extractSprite(rawAssets, SPR_FISH_HEAD);
            
            Image baseJelly1 = extractSprite(rawAssets, SPR_JELLY_1);
            Image baseJelly2 = extractSprite(rawAssets, SPR_JELLY_2);
            imgJelly1_L = rotateImage(baseJelly1, -67); imgJelly1_R = rotateImage(baseJelly1, 67);  
            imgJelly2_L = rotateImage(baseJelly2, -67); imgJelly2_R = rotateImage(baseJelly2, 67);

            imgBoot    = extractSprite(rawAssets, SPR_BOOT);
            imgCan     = extractSprite(rawAssets, SPR_CAN);
            imgCooler  = extractSprite(rawAssets, SPR_COOLER);
            imgIce     = extractSprite(rawAssets, SPR_ICE);
            imgSeaweed = extractSprite(rawAssets, SPR_SEAWEED);

            imgPenguinIdle  = scaleImage15(extractSprite(rawAssets, SPR_PENG_IDLE));
            imgPenguinPull  = scaleImage15(extractSprite(rawAssets, SPR_PENG_PULL));
            imgPenguinShock = scaleImage15(extractSprite(rawAssets, SPR_PENG_SHOCK));

            rawAssets = null; 
        }
        try { imgWater = Image.createImage("/water.png"); } catch (Exception e) {}
    }

    private void initGame() {
        fishCount = 0;
        points = 0;
        gameOver = false;
        hookX = getWidth() / 2;
        hookY = startHookY;
        hookState = 0;
        isHeadshot = false;

        if (imgCooler != null) coolerY = startHookY - imgCooler.getHeight() - 5;
        else coolerY = startHookY - 30;

        if (difficulty == 0) { lives = 5; spawnObjects(4, 2, 2); } 
        else if (difficulty == 1) { lives = 3; spawnObjects(3, 4, 4); } 
        else { lives = 1; spawnObjects(2, 6, 6); }
        
        removeCommand(cmdRestart);
        addCommand(cmdSettings);
        addCommand(cmdSell1);
        addCommand(cmdSellAll);
        addCommand(cmdBuyLife);
    }

    private void spawnObjects(int fishCount, int jellyCount, int trashCount) {
        for (int i = 0; i < maxObjects; i++) objActive[i] = false;
        int index = 0;
        index = populate(index, fishCount, 1);
        index = populate(index, jellyCount, 2);
        populate(index, trashCount, 3);
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
        
        objY[i] = startHookY + 40 + rnd.nextInt(Math.max(10, getHeight() - startHookY - 80)); 
        objX[i] = (rnd.nextInt(2) == 0) ? -50 : getWidth() + 50; 
        objSpeed[i] = (1 + rnd.nextInt(3 + difficulty));
        if (objX[i] > 0) objSpeed[i] = -objSpeed[i]; 
    }

    private void spawnFishAt(int x, int y) {
        for (int i = 0; i < maxObjects; i++) {
            if (!objActive[i]) {
                objActive[i] = true;
                objType[i] = 1; objX[i] = x; objY[i] = y;
                objSpeed[i] = (rnd.nextInt(2) == 0 ? 1 : -1) * (1 + rnd.nextInt(3 + difficulty));
                return;
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
            if (!isPaused && !gameOver) updateLogic();
            drawScreen(g);
            flushGraphics();
            try { Thread.sleep(30); } catch (InterruptedException e) {}
        }
    }

    private void updateLogic() {
        animTick++; 

        if (hookState == 1) { 
            hookY += hookSpeed;
            if (hookY >= getHeight() - 10) hookState = 2; 
        } else if (hookState == 2 || hookState == 3) { 
            hookY -= hookSpeed;
            if (hookY <= startHookY) {
                if (hookState == 3) {
                    // Успешно дотащили рыбу! Сохраняем её стоимость в рюкзак
                    if (fishCount < fishBag.length) {
                        fishBag[fishCount] = currentCatchValue;
                        fishCount++;
                    }
                }
                hookY = startHookY;
                hookState = 0; 
                isHeadshot = false;
            }
        }

        for (int i = 0; i < maxObjects; i++) {
            if (!objActive[i]) continue;
            objX[i] += objSpeed[i];

            if ((objSpeed[i] > 0 && objX[i] > getWidth() + 60) || 
                (objSpeed[i] < 0 && objX[i] < -60)) {
                resetObject(i, objType[i]);
            }

            if (hookState == 1 || hookState == 3) {
                if (Math.abs(hookX - objX[i]) < 25 && Math.abs(hookY - objY[i]) < 25) {
                    
                    if (objType[i] == 1 && hookState == 1) {
                        // Рассчет попадания в голову (голова спереди по ходу движения)
                        int headOffset = objSpeed[i] > 0 ? 18 : -18;
                        int headX = objX[i] + headOffset;
                        boolean hitHead = (Math.abs(hookX - headX) < 15 && Math.abs(hookY - objY[i]) < 15);
                        boolean hitBody = (Math.abs(hookX - objX[i]) < 20 && Math.abs(hookY - objY[i]) < 15);

                        if (hitHead || hitBody) {
                            hookState = 3; 
                            isHeadshot = hitHead;
                            
                            // Расчет очков за рыбу (Глубина)
                            float depthFactor = (float)(hookY - startHookY) / (getHeight() - startHookY);
                            int basePoints = 10 + (int)(depthFactor * 90); // От 10 до 100
                            if (isHeadshot) basePoints += basePoints / 2;  // +50% за голову
                            
                            currentCatchValue = basePoints;
                            resetObject(i, 1); 
                        }
                    } else if (objType[i] == 2) {
                        lives--;
                        if (hookState == 3) spawnFishAt(hookX, hookY); 
                        hookState = 2; 
                        if (lives <= 0) {
                            gameOver = true;
                            removeCommand(cmdSettings); removeCommand(cmdSell1); 
                            removeCommand(cmdSellAll); removeCommand(cmdBuyLife);
                            addCommand(cmdRestart);
                        }
                    } else if (objType[i] == 3 || objType[i] == 4) {
                        if (hookState == 1) hookState = 2; 
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
        // --- 0. Дневное светлое небо ---
        g.setColor(0x87CEEB); // Sky Blue
        g.fillRect(0, 0, getWidth(), startHookY);

        // --- 1. Вода ---
        if (imgWater != null) {
            for (int wx = 0; wx < getWidth(); wx += imgWater.getWidth()) {
                for (int wy = startHookY; wy < getHeight(); wy += imgWater.getHeight()) {
                    g.drawImage(imgWater, wx, wy, Graphics.TOP | Graphics.LEFT);
                }
            }
        } else {
            g.setColor(0x0077CC); g.fillRect(0, startHookY, getWidth(), getHeight() - startHookY);
        }

        // --- 2. Водоросли ---
        if (imgSeaweed != null) {
            int sw = imgSeaweed.getWidth();
            int sh = imgSeaweed.getHeight();
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

        // --- 3. Лед ---
        if (imgIce != null) {
            for (int ix = 0; ix < getWidth(); ix += imgIce.getWidth()) {
                g.drawImage(imgIce, ix, startHookY - imgIce.getHeight() + 2, Graphics.TOP | Graphics.LEFT);
            }
        } else {
            g.setColor(0xEEFFFF); g.drawLine(0, startHookY, getWidth(), startHookY);
        }

        // --- 4. Ящик (Кулер) ---
        if (imgCooler != null) {
            g.drawImage(imgCooler, coolerX, coolerY, Graphics.TOP | Graphics.LEFT);
            if (!gameOver && fishCount > 0) {
                g.setColor(0x006600); // Тёмно-зеленый текст для дня
                g.drawString("Продать", coolerX, coolerY - 15, Graphics.TOP | Graphics.LEFT);
            }
        }

        // --- 5. Поднятый Пингвин ---
        int pw = imgPenguinIdle != null ? imgPenguinIdle.getWidth() : 40;
        int ph = imgPenguinIdle != null ? imgPenguinIdle.getHeight() : 40;
        int px = getWidth() - pw - 5;
        int py = startHookY - ph - 6; 
        
        if (gameOver && imgPenguinShock != null) {
            g.drawImage(imgPenguinShock, px, py, Graphics.TOP | Graphics.LEFT);
        } else if (hookState == 3 && imgPenguinPull != null) {
            g.drawImage(imgPenguinPull, px, py, Graphics.TOP | Graphics.LEFT);
        } else if (imgPenguinIdle != null) {
            g.drawImage(imgPenguinIdle, px, py, Graphics.TOP | Graphics.LEFT);
        }

        // --- 6. Леска, Поплавок и Крючок ---
        if (!gameOver) {
            int rodTipX = px + 4;
            int rodTipY = py + 8; 
            
            g.setColor(0x000000);
            g.drawLine(rodTipX, rodTipY, hookX, startHookY); 
            g.drawLine(hookX, startHookY, hookX, hookY); 

            // Поплавок
            g.setColor(0xFF0000); g.fillArc(hookX - 4, startHookY - 4, 8, 8, 0, 180);
            g.setColor(0xFFFFFF); g.fillArc(hookX - 4, startHookY - 4, 8, 8, 180, 180);
            g.setColor(0x000000); g.drawArc(hookX - 4, startHookY - 4, 8, 8, 0, 360);

            if (hookState == 3) {
                // ПРАВИЛЬНАЯ ЛОГИКА ВЫТАСКИВАНИЯ (Смотрит вверх)
                if (imgFish1 != null) {
                    drawImg(g, imgFish1, hookX, hookY, Sprite.TRANS_ROT270);
                    if (imgFishHead != null) {
                        drawImg(g, imgFishHead, hookX, hookY - 20, Sprite.TRANS_ROT270);
                    }
                }
                // Рисуем бонусный текст если в голову!
                if (isHeadshot) {
                    g.setColor(0xFF0000);
                    g.drawString("+50% (В голову!)", hookX + 15, hookY - 15, Graphics.TOP | Graphics.LEFT);
                }
            } else {
                g.setColor(0x555555); g.drawArc(hookX - 4, hookY - 4, 8, 8, 180, 180);
                g.drawLine(hookX + 4, hookY - 4, hookX + 4, hookY);
                g.setColor(0xFF8888); g.fillArc(hookX - 2, hookY - 2, 4, 6, 0, 360);
            }
        }

        // --- 7. Объекты под водой (Исправленное отзеркаливание) ---
        for (int i = 0; i < maxObjects; i++) {
            if (!objActive[i]) continue;
            
            if (objType[i] == 1) { 
                Image frame = ((animTick / 5) % 2 == 0) ? imgFish1 : imgFish2;
                if (frame != null) {
                    // Оригинальный спрайт смотрит ВПРАВО
                    int transform = (objSpeed[i] > 0) ? Sprite.TRANS_NONE : Sprite.TRANS_MIRROR;
                    int headOffset = (objSpeed[i] > 0) ? 20 : -20;
                    
                    drawImg(g, frame, objX[i], objY[i], transform);
                    if (imgFishHead != null) {
                        drawImg(g, imgFishHead, objX[i] + headOffset, objY[i], transform);
                    }
                }
            } 
            else if (objType[i] == 2) { 
                Image frame = (objSpeed[i] > 0) ? 
                    (((animTick / 6) % 2 == 0) ? imgJelly1_R : imgJelly2_R) :
                    (((animTick / 6) % 2 == 0) ? imgJelly1_L : imgJelly2_L);
                if (frame != null) drawImg(g, frame, objX[i], objY[i], Sprite.TRANS_NONE);
            } 
            else if (objType[i] == 3) { 
                int transform = (objSpeed[i] > 0) ? Sprite.TRANS_NONE : Sprite.TRANS_MIRROR;
                if (imgBoot != null) drawImg(g, imgBoot, objX[i], objY[i], transform);
            } 
            else if (objType[i] == 4) { 
                if (imgCan != null) drawImg(g, imgCan, objX[i], objY[i], Sprite.TRANS_NONE);
            }
        }

        // --- 8. UI ---
        if (gameOver) {
            g.setColor(0xFF0000);
            g.drawString("ИГРА ОКОНЧЕНА", getWidth()/2, getHeight()/2 - 10, Graphics.TOP | Graphics.HCENTER);
        }
        g.setColor(0x000000); // Чёрный текст для дневного неба
        g.drawString("Рыбы: " + fishCount, 5, 2, Graphics.TOP | Graphics.LEFT);
        g.drawString("Очки: " + points, 5, 20, Graphics.TOP | Graphics.LEFT);
        g.drawString("Снасти: " + lives, getWidth() - 5, 2, Graphics.TOP | Graphics.RIGHT);
    }

    protected void pointerPressed(int x, int y) {
        if (!gameOver) {
            int cw = imgCooler != null ? imgCooler.getWidth() : 50;
            int ch = imgCooler != null ? imgCooler.getHeight() : 25;
            // Тап по кулеру продает ОДНУ рыбу
            if (x >= coolerX && x <= coolerX + cw && y >= coolerY - 15 && y <= coolerY + ch) {
                sellFish(1);
                return;
            }
            if (hookState == 0) {
                hookX = x; 
                hookState = 1; 
            }
        }
    }

    private void sellFish(int amount) {
        for (int i = 0; i < amount; i++) {
            if (fishCount > 0) {
                fishCount--; // Жертвуем крутостью
                points += fishBag[fishCount]; // Получаем очки
            }
        }
    }

    private void buyLife() {
        if (points >= 500) { points -= 500; lives++; }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdExit) midlet.exit();
        else if (c == cmdSettings && !gameOver) {
            difficulty = (difficulty + 1) % 3;
            cmdSettings = new Command("Сложность: " + diffNames[difficulty], Command.SCREEN, 1);
            removeCommand(c); addCommand(cmdSettings); initGame(); 
        } 
        else if (c == cmdSell1 && !gameOver) sellFish(1);
        else if (c == cmdSellAll && !gameOver) sellFish(fishCount);
        else if (c == cmdBuyLife && !gameOver) buyLife();
        else if (c == cmdRestart) initGame();
    }
}