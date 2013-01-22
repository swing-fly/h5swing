package sk.viktor.ignored.common;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import sk.viktor.SwingClassloader;
import sk.viktor.ignored.model.c2s.JsonConnectionHandshake;
import sk.viktor.ignored.model.c2s.JsonEvent;
import sk.viktor.ignored.model.c2s.JsonEventKeyboard;
import sk.viktor.ignored.model.c2s.JsonEventMouse;
import sk.viktor.ignored.model.c2s.JsonEventWindow;
import sk.viktor.ignored.model.s2c.JsonPaintRequest;
import sk.viktor.ignored.model.s2c.JsonWindowRequest;
import sk.viktor.util.Util;

import com.corundumstudio.socketio.SocketIOClient;

public class PaintManager {

    private static Map<String, PaintManager> instances = new HashMap<String, PaintManager>();

    private SocketIOClient client;
    private Map<String, Window> windows = new HashMap<String, Window>();
    private Long currentPaintRequestSeq = 0L;
    private MouseEvent lastMouseEvent;

    public PaintManager(String clientId, SocketIOClient client) {
        this.client = client;
    }

    public static Graphics beforePaintInterceptor(Graphics g, JComponent c) {
        GraphicsWrapper result;
        if (g instanceof GraphicsWrapper) {
            result = (GraphicsWrapper) g;
            if (result.getRootPaintComponent() == null) {
                result.setRootPaintComponent(c);
            }
        } else {
            return g;
        }
        return result;
    }

    public static void afterPaintInterceptor(Graphics g, JComponent c) {
        if (g instanceof GraphicsWrapper) {
            GraphicsWrapper gw = (GraphicsWrapper) g;
            if (Util.getObjectIdentity(gw.getRootPaintComponent()).equals(Util.getObjectIdentity(c))) {
                getInstance(Util.resolveClientId(c)).doSendPaintRequest(gw, c);
            }
        }
    }

    public void doSendPaintRequest(GraphicsWrapper gw, JComponent c) {
        BufferedImage img = gw.getImg();
        if (img != null) {
            long seq = nextSeq(client);
            String identity = "" + seq;
            gw.getWebWindow().addChangesToDiff();
            client.sendJsonObject(new JsonPaintRequest(Util.resolveClientId(c), seq, identity, 0, 0, gw.getWindowInfo()));
        }
    }

    private long nextSeq(SocketIOClient client) {
        long result = 0;
        result = currentPaintRequestSeq;
        currentPaintRequestSeq = result + 1;
        return result;
    }

    public void disposeWindow(Window webWindow) {
        windows.remove(webWindow);
    }

    public void registerWindow(Window webWindow) {
        windows.put(Util.getObjectIdentity(webWindow), webWindow);
    }

    public void hideWindowInBrowser(Window webWindow) {
        client.sendJsonObject(new JsonWindowRequest(((WebWindow) webWindow).getWindowInfo().getId()));
    }

    public WebWindow getWebWindow(String guid) {
        return (WebWindow) windows.get(guid);
    }

    public void dispatchEvent(JsonEvent event) {
        if (event instanceof JsonEventMouse) {
            dispatchMouseEvent((JsonEventMouse) event);
        }
        if (event instanceof JsonEventWindow) {
            dispatchWindowEvent((JsonEventWindow) event);
        }
        if (event instanceof JsonEventKeyboard) {
            dispatchKeyboardEvent((JsonEventKeyboard) event);
        }
    }

    private void dispatchWindowEvent(JsonEventWindow event) {
        Window w = windows.get(event.windowId);
        w.dispose();
    }

    private void dispatchKeyboardEvent(JsonEventKeyboard event) {
        Window w = windows.get(event.windowId);
        long when = System.currentTimeMillis();
        int modifiers = Util.getKeyModifiersAWTFlag(event);
        int type = Util.getKeyType(event.type);
        Component src = w.getFocusOwner()==null?w:w.getFocusOwner();
        if (type == KeyEvent.KEY_TYPED) {
            AWTEvent e = new KeyEvent(src, KeyEvent.KEY_TYPED, when, modifiers, 0, (char) event.character);
            dispatchEventInSwing(w, e);
        } else {
            AWTEvent e = new KeyEvent(src, type, when, modifiers, event.keycode, (char) event.character);
            dispatchEventInSwing(w, e);
        }
    }

    private void dispatchMouseEvent(JsonEventMouse event) {
        MouseEvent e = null;
        Window w = windows.get(event.windowId);
        WebWindow ww = (WebWindow) w;
        int x = event.x + ww.getFrameTranslation().x;
        int y = event.y + ww.getFrameTranslation().y;
        long when = System.currentTimeMillis();
        int modifiers = Util.getMouseModifiersAWTFlag(event);
        int id = 0;
        int clickcount = 0;
        int buttons = Util.getMouseButtonsAWTFlag(event.button);
        switch (event.type) {
            case mousemove:
                id = event.button == 1 ? MouseEvent.MOUSE_DRAGGED : MouseEvent.MOUSE_MOVED;
                e = new MouseEvent(w, id, when, modifiers, x, y, x, y, clickcount, false, buttons);
                dispatchEventInSwing(w, e);
                lastMouseEvent=e;
                break;
            case mouseup:
                id = MouseEvent.MOUSE_RELEASED;
                boolean popupTrigger = (buttons == 3) ? true : false;
                clickcount = 1;
                e = new MouseEvent(w, id, when, modifiers, x, y, x, y, clickcount, popupTrigger, buttons);
                dispatchEventInSwing(w, e);
                if(lastMouseEvent!=null && lastMouseEvent.getID()==MouseEvent.MOUSE_PRESSED && lastMouseEvent.getX()==x && lastMouseEvent.getY()==y){
                    e = new MouseEvent(w, MouseEvent.MOUSE_CLICKED, when, modifiers, x, y, x, y, clickcount, popupTrigger, buttons);
                    dispatchEventInSwing(w, e);
                    lastMouseEvent=null;
                }
                break;
            case mousedown:
                id = MouseEvent.MOUSE_PRESSED;
                clickcount = 1;
                e = new MouseEvent(w, id, when, modifiers, x, y, x, y, clickcount, false, buttons);
                dispatchEventInSwing(w, e);
                lastMouseEvent=e;
                break;
            case mousewheel:
                id = MouseEvent.MOUSE_WHEEL;
                buttons = 0;
                modifiers = 0;
                e = new MouseWheelEvent(w, id, when, modifiers, x, y, clickcount, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, event.wheelDelta);
                dispatchEventInSwing(w, e);
                lastMouseEvent=e;
                break;
            case dblclick:
                e = new MouseEvent(w, MouseEvent.MOUSE_CLICKED, when, modifiers, x, y, x, y, 2, false, buttons);
                dispatchEventInSwing(w, e);
                break;
            default:
                break;
        }
    }

    private void dispatchEventInSwing(final Window w, final AWTEvent e) {
        Runnable callDispatchEvent = new Runnable() {

            public void run() {
                w.dispatchEvent(e);
            }
        };
        SwingUtilities.invokeLater(callDispatchEvent);

    }

    public static PaintManager getInstance(String clientId) {
        if (clientId != null) {
            return instances.get(clientId);
        }
        try {
            throw new Exception("no clientID");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void clientConnected(SocketIOClient client, final JsonConnectionHandshake handshake) {
        if (!instances.containsKey(handshake.clientId)) {
            instances.put(handshake.clientId, new PaintManager(handshake.clientId, client));
            try {
                new Thread(new ThreadGroup(handshake.clientId), new Runnable() {

                    public void run() {
                        try {
                            SwingClassloader cl = new SwingClassloader(handshake.clientId);
                            Class<?> clazz = cl.loadClass("com.sun.swingset3.SwingSet3");
                            //Class<?> clazz = cl.loadClass("com.sun.swingset3.demos.filechooser.FileChooserDemo");
                            // Get a class representing the type of the main method's argument
                            Class<?> mainArgType[] = { (new String[0]).getClass() };
                            String progArgs[] = new String[0];

                            // Find the standard main method in the class
                            Method main = clazz.getMethod("main", mainArgType);

                            // Create a list containing the arguments -- in this case,
                            // an array of strings
                            Object argsArray[] = { progArgs };

                            // Call the method
                            main.invoke(null, argsArray);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            instances.get(handshake.clientId).currentPaintRequestSeq = 0L;
        }
    }

    public void disposeApplication() {
        for (Window w : windows.values()) {
            w.dispose();
        }
    }
}
