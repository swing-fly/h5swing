package org.webswing;



public interface Constants {
    public static final String PAINT_ACK_PREFIX = "paintAck";
    public static final String UNLOAD_PREFIX = "unload";
    
    public static final String SWING_SHUTDOWN_NOTIFICATION = "shutDownNotification";
    public static final String TOO_MANY_CLIENTS_NOTIFICATION = "tooManyClientsNotification";
    public static final String SWING_KILL_SIGNAL = "killSwing";

    public static final String SWING_START_SYS_PROP_CLIENT_ID = "webswing.clientId";
    public static final String SWING_START_SYS_PROP_MAIN_CLASS = "webswing.mainClass";
    public static final String SWING_START_SYS_PROP_CLASS_PATH = "webswing.classPath";


    public static final String SWING2SERVER = "Swing2Server";
    public static final String SERVER2SWING = "Server2Swing";

    public static final String JMS_URL = "nio://127.0.0.1:34455";
    public static final String WAR_FILE_LOCATION = "webswing.warLocation";

    public static final String TEMP_DIR_PATH="webswing.tempDirPath";
}
