package fishingly;

import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.Display;

public class FishingMIDlet extends MIDlet {
    private FishingCanvas canvas;

    public void startApp() {
        if (canvas == null) {
            canvas = new FishingCanvas(this);
        }
        Display.getDisplay(this).setCurrent(canvas);
        canvas.start();
    }

    public void pauseApp() {
        if (canvas != null) {
            canvas.pause();
        }
    }

    public void destroyApp(boolean unconditional) {
        if (canvas != null) {
            canvas.saveGame();
            canvas.stop();
        }
    }
    
    public void exit() {
        destroyApp(true);
        notifyDestroyed();
    }
}