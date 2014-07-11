package org.webswing;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class ServerMain {

    public static void main(String[] args) throws Exception {
        Configuration config = ConfigurationImpl.parse(args);
        System.setProperty(Constants.SERVER_PORT, config.getPort());
        System.setProperty(Constants.SERVER_HOST, config.getHost());
        if(config.getConfigFile()!=null){
            File configFile = new File(config.getConfigFile());
            if(configFile.exists()){
                System.setProperty(Constants.CONFIG_FILE_PATH, configFile.toURI().toString());
            }else{
                System.err.print("Webswing configuration file "+config.getConfigFile()+" not found. Using default location.");
            }
        }
        if(config.getUsersFile()!=null){
            File usersFile = new File(config.getUsersFile());
            if(usersFile.exists()){
                System.setProperty(Constants.USER_FILE_PATH, usersFile.toURI().toString());
            }else{
                System.err.print("Webswing users property file "+config.getUsersFile()+" not found. Using default location.");
            }
        }
        
        InetSocketAddress address = new InetSocketAddress(config.getHost(), Integer.parseInt(config.getPort()));
        Server server = new Server(address);
        WebAppContext webapp = new WebAppContext();
        webapp.setContextPath("/");
        webapp.setWar(System.getProperty(Constants.WAR_FILE_LOCATION));
        webapp.setTempDirectory(new File(URI.create(System.getProperty(Constants.TEMP_DIR_PATH))));
        server.setHandler(webapp);
        server.start();
        server.join();

    }

}