package com.tfs.dxcscon4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;

import com.tfs.dxcscon4j.protocol.AccessInstruction;
import com.tfs.dxcscon4j.protocol.Datapack;
import com.tfs.dxcscon4j.protocol.Disconnection;
import com.tfs.dxcscon4j.protocol.Vertification;

public class ClientHandler{
    private final Socket clientSocket;
    private String mainThreadName;
    /**发送字符串队列 */
    private final Queue<Datapack> toSend = new LinkedList<>();
    /**接受字符串队列 */
    private final Queue<Datapack> receive = new LinkedList<>();
    private User user;
    /** 收取信息的触发器，用于检测是否有信息流入*/
    private boolean receiveTrigger = false;

    private PrintWriter writer;
    private BufferedReader reader;

    /**
     * 创建一个socket管理实例
     * @param clientSocket 与客户端的连接的socket实例
     */
    public ClientHandler(Socket clientSocket){
        this.clientSocket = clientSocket;
    }

    /**
     * 与客户端之间发送HeartBeat验证信息的时间间隔
     */
    public static final int HEART_BEAT_INTERVAL_MILLISECONDS = 1000;

    /**
     * 客户端没有回应的最大次数容忍限度
     */
    public static final int NO_RESPONSE_TIMEOUT_TRIES = 5;

    /**
     * 客户端没有发送验证信息的最大次数容忍限度
     */
    public static final int NO_VERTIFICATION_MAX_COUNT = 10;

    protected void handle(){
        this.mainThreadName = String.format("ClientHandler IP %s", clientSocket.getInetAddress().toString());
        Thread.currentThread().setName(this.mainThreadName);
        try {
            this.writer = new PrintWriter(clientSocket.getOutputStream(), true);
            this.reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (Exception e) {
            DXSys.Logging.logError(e.getMessage());
            this.killConnection();
        }
        int noResponseCount = 0;
        boolean vertified = false;
        DXSys.Logging.logInfo("User connected, vertifying...");
        for(int i = 0; i < NO_VERTIFICATION_MAX_COUNT; i++) {
            try {
                if(this.reader.ready()) {
                    Datapack vertificationPack = new Datapack(reader.readLine());
                    Vertification ver = vertificationPack.deserializeContent(Vertification.class);
                    
                    String id = ver.getIdentifier();
                    boolean result = DXSys.vertificationStrategy.vertify(ver);
                    if(result == false) {
                        DXSys.Logging.logInfo("User [%s] vertification failed", id);
                        this.askForKillConnection(new Disconnection("Vertification failed"));
                        return;
                    }

                    if(ServerConnection.instance().nameToUser.containsKey(id)) {
                        DXSys.Logging.logInfo("A user with same identifier: [%s] already exists, kicking old user", id);
                        ServerConnection.instance().kickUser(id, "Same identifer user logged in");
                    }
                    this.sendImmediateMessage(new Datapack("AccessInstruction", new AccessInstruction("Granted", "")));
                    
                    this.user = new User(this, id);
                    synchronized(ServerConnection.instance().nameToUser){
                        ServerConnection.instance().nameToUser.put(this.user.getName(), this.user);
                    }

                    synchronized(ServerConnection.instance().connectedUsers){
                        ServerConnection.instance().connectedUsers.add(this.user);
                    }
                    //将自己加入服务器单例的clients实例名单，方便通过Server单例进行统一管理
                    vertified = true;
                    break;
                }
                Thread.sleep(200);
            } catch (Exception e) {
                DXSys.Logging.logError("Error while receiving vertification info");
                e.printStackTrace();
                this.killConnection();
                return;
            }
        }

        if(!vertified) {
            DXSys.Logging.logInfo("User is not sending vertification info, kicked.");
            this.killConnection();
            return;
        }
        DXSys.Logging.logInfo("User %s logged in", this.user.getName());
        
        while(this.isConnected()){
            try {
                //主线程进行初始化后就进入监视模式，时刻监视是否还与客户端保持连接
                Thread.sleep(HEART_BEAT_INTERVAL_MILLISECONDS);
                if(this.receiveTrigger){
                    this.receiveTrigger = false;
                    noResponseCount = 0;
                    continue;
                }
                if(noResponseCount > NO_RESPONSE_TIMEOUT_TRIES){
                    this.killConnection();
                    DXSys.Logging.logInfo("Connection timed out");
                    break;
                }
                //如果还没有到达最大容忍限度，可能是因为网络速度低，尝试发送HeartBeat数据包
                this.sendMessage(Datapack.HEARTBEAT);
                noResponseCount++;
            } catch (Exception e) {
                //此处发生错误大概率为严重错误，直接崩溃此线程
                DXSys.Logging.logError(e.getMessage());
                this.killConnection();
                break;
            }
        }
    }

    public void killConnection(){
        try {
            //断开连接
            this.clientSocket.close();
            synchronized(ServerConnection.instance().connectedUsers) {
                try {
                    ServerConnection.instance().connectedUsers.remove(this.user);
                } catch (Exception e) {
                    DXSys.Logging.logWarning("Removing null user, it might be a vertification failure");
                }
            }
            synchronized(ServerConnection.instance().nameToUser) {
                try {
                    ServerConnection.instance().nameToUser.remove(this.user.getName());
                } catch (Exception e) {
                    DXSys.Logging.logWarning("Removing null user, it might be a vertification failure");
                }
            }
            if(this.user != null) {
                this.user.setConnected(false);
            }

            DXSys.Logging.logInfo("%s disconnected from the server", user == null ? "UNVERTIFIED" : this.user.getName());
        } catch (IOException err) {
            DXSys.Logging.logError("Error while closing socket");
            DXSys.Logging.logError(err.getMessage());
        }
    }

    public void sendMessage(Datapack message) {
        synchronized(this.toSend) {
            this.toSend.add(message);
        }
    }

    public void sendImmediateMessage(Datapack message) {
        synchronized(this.writer) {
            this.writer.println(message.toJson());
            this.writer.flush();
        }
    }

    public Datapack receiveMessage() {
        synchronized(this.receive) {
            if(this.receive.isEmpty()) {
                return null;
            }
            return this.receive.remove();
        }
    }

    public void askForKillConnection(Disconnection killConnectionCommand){
        this.sendImmediateMessage(new Datapack("ControlConnect", killConnectionCommand));
        DXSys.Logging.logInfo("kicked user %s from server, cause: %s", user.getName(), killConnectionCommand.getCause());
        this.killConnection();
    }

    protected void onTick() {
        try {
            //服务器tick一次的逻辑
            this.sendMessageTick();
            this.receiveMessageTick();
            this.popReceiveTick();
        } catch (IOException ioException) {
            //如果出现IOException，应为连接已经被断开，所以直接断开本地的连接
            DXSys.Logging.logError(ioException.getMessage());
            this.killConnection();
        }
    }

    private void sendMessageTick() throws IOException {
        synchronized(this.toSend) {
            if(this.toSend.size() != 0) {
                this.writer.println(this.toSend.remove().toJson());
            }
        }
    }

    private void receiveMessageTick() throws IOException {
        //如果不用ready()，readLine()将会阻塞线程，用ready()来确保缓冲区有数据可读
        if(this.reader.ready()){
            Datapack received = Datapack.toDatapack(this.reader.readLine());
            this.receiveTrigger = true;
            //如果是HeartBeat验证消息，不用加入数据包集合，因为这只是个辅助消息，对其他功能没有用处
            if(received.identifier.equals(Datapack.HEARTBEAT.identifier)){
                return;
            }
            DXSys.Logging.logInfo("received message from client: %s", received);
            
            synchronized(this.receive){
                this.receive.add(received);
            }
        }
    }

    private void popReceiveTick() {
        synchronized(this.receive){
            if(this.receive.size() != 0) {
                synchronized(ServerConnection.instance().receivedDatapacks) {
                    Datapack received = this.receive.remove();
                    received.senderTag = this.user.getName();
                    ServerConnection.instance().receivedDatapacks.add(this.receive.remove());
                }
            }
        }
    }

    public boolean isConnected(){
        return this.clientSocket.isConnected() && !this.clientSocket.isClosed();
    }
}