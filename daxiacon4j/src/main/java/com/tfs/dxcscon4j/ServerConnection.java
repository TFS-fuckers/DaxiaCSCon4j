package com.tfs.dxcscon4j;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.tfs.dxcscon4j.exceptions.AccessToOfflineUserException;
import com.tfs.dxcscon4j.protocol.Datapack;
import com.tfs.dxcscon4j.protocol.Disconnection;

public class ServerConnection {
    /**服务器的唯一实例 */
    private static ServerConnection INSTANCE = null;
    /**服务器每两个tick（逻辑运行）的间隔时间 */
    public static int tickIntervalMilliseconds = 50;
    private ServerSocket server;
    /**服务器是否在运行 */
    private boolean running = true;
    /**服务器已经连接的所有客户端 */
    public final List<User> connectedUsers = new ArrayList<>();
    /**根据标识符获取用户实例的哈希表 */
    public final Map<String, User> nameToUser = new HashMap<String, User>();
    /**服务器从所有客户端收到的所有数据包 */
    public final Queue<Datapack> receivedDatapacks = new LinkedList<>();

    /**
     * 服务器实例构造，也是启动服务器的入口。注意，这是一个阻塞方法，所以应该考虑是否放入一个独立的线程。
     * @param port 服务器监听的端口
     * @param onServerTick 自定义服务器每个tick内的额外逻辑，不得为阻塞方法
     */
    public ServerConnection(int port, Runnable onServerTick){
        if(INSTANCE != null){
            DXSys.Logging.logError("You can't run two or more servers in one process");
            return;
        }
        INSTANCE = this;
        long prepareStart = System.currentTimeMillis();
        Thread.currentThread().setName("ServerThread");
        ThreadPoolExecutor pool = new ThreadPoolExecutor(20, 50, 3L, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
        DXSys.Logging.logInfo("Server Starting...");
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run(){
                try {
                    synchronized(connectedUsers) {
                        for(User handler : connectedUsers){
                            handler.getHandler().onTick();
                        }
                        if(onServerTick != null){
                            onServerTick.run();
                        }
                    }
                } catch (ConcurrentModificationException e) {
                    DXSys.Logging.logError("Concurrent modification occured while processing: %s", e.getMessage());
                } catch (AccessToOfflineUserException e) {
                    DXSys.Logging.logError("A user was already offline but server is still trying to get access to it");
                    e.printStackTrace();
                } catch (Exception e) {
                    DXSys.Logging.logError("Fatal error: %s", e.getMessage());
                    e.printStackTrace();
                    ServerConnection.instance().kill();
                }
            }
        }, 50, 50);
        
        DXSys.Logging.logInfo("Server tick started");
        try {
            this.server = new ServerSocket(port);
            DXSys.Logging.logInfo("Server is starting on port %d", port);
            DXSys.Logging.logInfo("Done! [%.2f seconds]", (System.currentTimeMillis() - prepareStart) * 1.0f / 1000);
            
            while(this.running){
                try{
                    Socket connection = server.accept();
                    DXSys.Logging.logInfo("User %s is connected", connection.getInetAddress().toString());
                    pool.execute(new ClientHandler(connection)::handle);
                } catch(IOException e){
                    DXSys.Logging.logInfo("Server is being closed");
                } catch(Exception e){
                    DXSys.Logging.logError(e.toString());
                    DXSys.Logging.logError("fatal error, server accept down");
                    this.kill();
                }
            }
        } catch (Exception e) {
            DXSys.Logging.logError("Error occured message: %s", e.toString());
            e.printStackTrace();
        }
        pool.shutdown();
        DXSys.Logging.logInfo("Server is shutting down");
        INSTANCE = null;
    }

    /**
     * 获取服务器是否正在运行
     * @return 获取服务器是否正在运行，如果是，返回true
     */
    public boolean isRunning() {
        return this.running;
    }

    /**
     * 中断服务器的运行
     */
    public void kill(){
        try {
            this.server.close();
            this.running = false;
        } catch (Exception e) {
            DXSys.Logging.logError("Exception while shutting down:%s", e.getMessage());
        }
    }

    /**
     * 获取服务器的实例
     * @return 服务器实例
     */
    public static ServerConnection instance(){
        return INSTANCE;
    }

    /**
     * 向与服务器连接的所有客户端发送数据包
     * @param datapack 待发送的数据包
     */
    public void sendToAll(Datapack datapack){
        for(User user : connectedUsers){
            try {
                user.getHandler().sendMessage(datapack);
            } catch (AccessToOfflineUserException e) {
                e.printStackTrace();
            }
        }
        return;
    }
    
    /**
     * 向与服务器连接的所有客户端立即发送数据包
     * @param datapack 立即发送的数据包
     */
    public void sendToAllImmediately(Datapack datapack){
        for(User user : connectedUsers){
            try {
                user.getHandler().sendImmediateMessage(datapack);
            } catch (AccessToOfflineUserException e) {
                e.printStackTrace();
            }
        }
        return;
    }

    /**
     * 向某用户发送数据包（放入队列）
     * @param identifier 用户的标识符
     * @param datapack 发送的数据包
     */
    public void sendToUser(String identifier, Datapack datapack) {
        User user = this.nameToUser.get(identifier);
        if(user == null) {
            DXSys.Logging.logWarning("User %s not found", identifier);
            return;
        }

        try {
            user.getHandler().sendMessage(datapack);
        } catch (AccessToOfflineUserException e) {
            e.printStackTrace();
        }
    }

    /**
     * 向某用户立即发送数据包（立刻发送）
     * @param identifier 用户的标识符
     * @param datapack 立即发送的数据包
     */
    public void sendToUserImmediately(String identifier, Datapack datapack) {
        User user = this.nameToUser.get(identifier);
        if(user == null) {
            DXSys.Logging.logWarning("User %s not found", identifier);
            return;
        }
        try {
            user.getHandler().sendImmediateMessage(datapack);
        } catch (AccessToOfflineUserException e) {
            e.printStackTrace();
        }
    }

    /**
     * 踢出某用户
     * @param identifier 踢出的用户标识符
     */
    public void kickUser(String identifier) {
        User user = getUser(identifier);
        if(user == null) {
            DXSys.Logging.logWarning("User %s not found", identifier);
            return;
        }
        try {
            user.getHandler().askForKillConnection(new Disconnection("kicked"));
        } catch (AccessToOfflineUserException e) {
            e.printStackTrace();
        }
    }

    /**
     * 踢出某用户
     * @param identifier 踢出的用户标识符
     * @param cause 踢出的原因
     */
    public void kickUser(String identifier, String cause) {
        User user = getUser(identifier);
        if(user == null) {
            DXSys.Logging.logWarning("User %s not found", identifier);
            return;
        }
        try {
            user.getHandler().askForKillConnection(new Disconnection(cause));
        } catch (AccessToOfflineUserException e) {
            e.printStackTrace();
        }
    }

    /**
     * 寻找某个用户实例
     * @param identifier 用户的标识符
     * @return 用户实例，如果没有找到，返回null
     */
    public User getUser(String identifier) {
        return this.nameToUser.get(identifier);
    }

    /**
     * 获取指定序号的用户
     * @param index 序号
     * @return 指定序号的用户
     */
    public User getUser(int index) {
        return this.connectedUsers.get(index);
    }

    /**
     * 获取用户数量
     * @return 用户数量
     */
    public int getUserNum() {
        if(connectedUsers == null)
            return 0;
        return this.connectedUsers.size();
    }

    /**
     * 获取某个用户的index编号
     * @param identifier 用户标识符
     * @return 用户编号，如果用户不存在，返回-1
     */
    public int getUserIndex(String identifier) {
        for(int i = 0; i < this.connectedUsers.size(); i++) {
            if(this.connectedUsers.get(i).getName().equals(identifier)){
                return i;
            }
        }
        return -1;
    }
}