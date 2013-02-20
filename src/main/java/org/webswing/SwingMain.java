package org.webswing;

import java.lang.reflect.Method;

import org.webswing.ignored.common.PaintManager;
import org.webswing.ignored.special.UIManagerConfigurator;
import org.webswing.server.SwingServer;


public class SwingMain {

    public static Thread notifyExitThread=new Thread() {
        @Override
        public void run() {
            PaintManager.getInstance().notifyShutDown();
        }
    };
    
    public static void main(String[] args) throws Exception {
        UIManagerConfigurator.configureUI();
        Runtime.getRuntime().addShutdownHook(notifyExitThread);

        SwingClassloader cl = new SwingClassloader();
        Class<?> clazz = cl.loadClass(System.getProperty(SwingServer.SWING_START_SYS_PROP_MAIN_CLASS));
        //Class<?> clazz = cl.loadClass("com.sun.swingset3.SwingSet3");
        //Class<?> clazz = cl.loadClass("com.sun.swingset3.demos.filechooser.FileChooserDemo");
        // Get a class representing the type of the main method's argument
        Class<?> mainArgType[] = { (new String[0]).getClass() };
        String progArgs[] = args;

        // Find the standard main method in the class
        Method main = clazz.getMethod("main", mainArgType);

        // Create a list containing the arguments -- in this case,
        // an array of strings
        Object argsArray[] = { progArgs };

        // Call the method
        main.invoke(null, argsArray);
    }
}