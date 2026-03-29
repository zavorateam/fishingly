package fishing;

import java.util.Random;
import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.GameCanvas;

public class FishingCanvas extends GameCanvas implements Runnable, CommandListener {
    private FishingMIDlet midlet;
    private volatile boolean running;
    private boolean isPaused;

    // Софт-кнопки (контекстный интерфейс)
    private Command cmdSettings;
    private Command cmdExit;
    private Command cmdRestart;

    // Настройки сложности (0 - Легко, 1 - Нормально, 2 - Хард)
    private int difficulty = 0;
    private String[] diffNames = {"Легко", "Нормально", "Хард"};
    
    // Параметры игрока
    private int lives;
    private int score;
    private boolean gameOver;

    // Удочка
    private int hookX, hookY;
    private int startHookY = 20;
    private int hookSpeed = 3;
    // 0 - на поверхности, 1 - погружается, 2 - скручивается
    private int hookState = 0; 

    // Игровые объекты: 1 - Рыба, 2 - Медуза (враг), 3 - Мусор
    private int maxObjects = 20;
    private int[] objX = new int[maxObjects];
    private int[] objY = new int[maxObjects];
    private int[] objSpeed = new int[maxObjects];
    private int[] objType = new int[maxObjects];
    private boolean[] objActive = new boolean[maxObjects];

    private Random rnd = new Random();

    public FishingCanvas(FishingMIDlet midlet) {
        super(true); // Включаем двойную буферизацию
        this.midlet = midlet;

        // Инициализация команд
        cmdSettings = new Command("Сложность: " + diffNames[difficulty], Command.SCREEN, 1);
        cmdRestart = new Command("Заново", Command.SCREEN, 2);
        cmdExit = new Command("Выход", Command.EXIT, 3);

        addCommand(cmdSettings);
        addCommand(cmdExit);
        setCommandListener(this);

        setFullScreenMode(true);
        initGame();
    }

    private void initGame() {
        score = 0;
        gameOver = false;
        hookX = getWidth() / 2;
        hookY = startHookY;
        hookState = 0;

        // Задаем параметры от сложности
        if (difficulty == 0) {
            lives = 5;
            spawnObjects(3, 2, 2); // рыбы, медузы, мусор
        } else if (difficulty == 1) {
            lives = 3;
            spawnObjects(2, 4, 4);
        } else {
            lives = 1;
            spawnObjects(1, 6, 6);
        }
        
        removeCommand(cmdRestart);
        addCommand(cmdSettings);
    }

    private void spawnObjects(int fishCount, int jellyCount, int trashCount) {
        for (int i = 0; i < maxObjects; i++) {
            objActive[i] = false;
        }
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
        objType[i] = type;
        objY[i] = 60 + rnd.nextInt(getHeight() - 80); // Глубина
        objX[i] = (rnd.nextInt(2) == 0) ? -20 : getWidth() + 20; // Слева или справа
        objSpeed[i] = 1 + rnd.nextInt(3 + difficulty);
        if (objX[i] > 0) objSpeed[i] = -objSpeed[i]; // Движение влево
    }

    public void start() {
        running = true;
        isPaused = false;
        Thread t = new Thread(this);
        t.start();
    }

    public void stop() {
        running = false;
    }

    public void pause() {
        isPaused = true;
    }

    public void run() {
        Graphics g = getGraphics();
        while (running) {
            if (!isPaused && !gameOver) {
                updateLogic();
            }
            drawScreen(g);
            flushGraphics();
            
            try {
                Thread.sleep(30); // ~33 FPS
            } catch (InterruptedException e) {}
        }
    }

    private void updateLogic() {
        // Логика удочки
        if (hookState == 1) { // Погружение
            hookY += hookSpeed;
            if (hookY >= getHeight() - 10) {
                hookState = 2; // Достигли дна, скручиваем
            }
        } else if (hookState == 2) { // Скручивание
            hookY -= hookSpeed;
            if (hookY <= startHookY) {
                hookY = startHookY;
                hookState = 0; // Готова к новому забросу
            }
        }

        // Логика объектов
        for (int i = 0; i < maxObjects; i++) {
            if (!objActive[i]) continue;

            objX[i] += objSpeed[i];

            // Если объект уплыл за экран, респавним его
            if ((objSpeed[i] > 0 && objX[i] > getWidth() + 20) || 
                (objSpeed[i] < 0 && objX[i] < -20)) {
                resetObject(i, objType[i]);
            }

            // Проверка столкновений удочки (если она погружается или скручивается)
            if (hookState != 0) {
                if (Math.abs(hookX - objX[i]) < 15 && Math.abs(hookY - objY[i]) < 15) {
                    if (objType[i] == 1) {
                        // Поймали рыбу
                        score++;
                        hookState = 2; // Тащим наверх
                        resetObject(i, 1);
                    } else if (objType[i] == 2) {
                        // Наткнулись на медузу (враг)
                        lives--;
                        hookState = 2; // Скручиваем сломанную снасть
                        if (lives <= 0) {
                            gameOver = true;
                            removeCommand(cmdSettings);
                            addCommand(cmdRestart);
                        }
                    } else if (objType[i] == 3) {
                        // Наткнулись на мусор
                        hookState = 2; // Просто скручиваем
                    }
                }
            }
        }
    }

    private void drawScreen(Graphics g) {
        // Очистка экрана (вода)
        g.setColor(0x00AADD);
        g.fillRect(0, 0, getWidth(), getHeight());

        // Лед сверху
        g.setColor(0xDDFFFF);
        g.fillRect(0, 0, getWidth(), startHookY);

        if (gameOver) {
            g.setColor(0xFF0000);
            g.drawString("ИГРА ОКОНЧЕНА", getWidth()/2, getHeight()/2, Graphics.TOP | Graphics.HCENTER);
            g.drawString("Счет: " + score, getWidth()/2, getHeight()/2 + 20, Graphics.TOP | Graphics.HCENTER);
            return;
        }

        // Рисуем леску и крючок
        g.setColor(0x000000);
        g.drawLine(hookX, startHookY, hookX, hookY); // Леска
        g.setColor(0x555555);
        g.fillArc(hookX - 4, hookY, 8, 8, 0, 360); // Крючок/наживка

        // Рисуем объекты
        for (int i = 0; i < maxObjects; i++) {
            if (!objActive[i]) continue;
            
            if (objType[i] == 1) {
                g.setColor(0x00FF00); // Рыба (зеленая)
                g.fillRect(objX[i] - 10, objY[i] - 5, 20, 10);
            } else if (objType[i] == 2) {
                g.setColor(0xFF0000); // Медуза (красная)
                g.fillArc(objX[i] - 10, objY[i] - 10, 20, 20, 0, 180);
            } else if (objType[i] == 3) {
                g.setColor(0x884400); // Мусор (коричневый)
                g.fillRect(objX[i] - 8, objY[i] - 8, 16, 16);
            }
        }

        // Рисуем UI
        g.setColor(0x000000);
        g.drawString("Счет: " + score, 5, 2, Graphics.TOP | Graphics.LEFT);
        g.drawString("Снасти: " + lives, getWidth() - 5, 2, Graphics.TOP | Graphics.RIGHT);
    }

    // Поддержка сенсорного экрана (Nokia N8)
    protected void pointerPressed(int x, int y) {
        if (!gameOver && hookState == 0) {
            // Тап по экрану - заброс удочки
            hookX = x; // Можно закидывать туда, куда тапнули по X
            hookState = 1;
        }
    }

    // Обработка контекстных кнопок
    public void commandAction(Command c, Displayable d) {
        if (c == cmdExit) {
            midlet.exit();
        } else if (c == cmdSettings && !gameOver) {
            // Циклическое переключение сложности
            difficulty = (difficulty + 1) % 3;
            cmdSettings = new Command("Сложность: " + diffNames[difficulty], Command.SCREEN, 1);
            removeCommand(c);
            addCommand(cmdSettings);
            initGame(); // Перезапуск с новой сложностью
        } else if (c == cmdRestart) {
            initGame();
        }
    }
}