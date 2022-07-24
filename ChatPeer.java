import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatPeer {
    //server
    private ServerSocket serverSocket;
    Integer serverPort = 4444;
    //client
    private Socket socket;
    private Integer clientPort;

    String ip;
//    client used
    private BufferedReader p2pBufferedReader;
    private BufferedWriter p2pBufferedWriter;


    String roomId = "";

    List<Room> roomList = new ArrayList<>();

    public ChatPeer(Integer serverPort, Integer clientPort) {
        //default port number
        if (serverPort != null) {
            this.serverPort = serverPort;
        }
        if (clientPort != null) {
            this.clientPort = clientPort;
        }

    }

    public void startPeerClient() {
        readLocalCommand();
        this.startServer();
    }


    public void startServer() {
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
            serverSocket = new ServerSocket(serverPort);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        try {
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                ClientHandle clientHandle = new ClientHandle(socket, this);
                Thread thread = new Thread(clientHandle);
                thread.start();
            }
        } catch (IOException e) {
        }
    }


    public void readLocalCommand() {

        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNext()) {
                if (socket == null) {
                    System.out.println(">");
                } else {
                    System.out.println("[" + roomId + "]" + " " + ip + ":" + clientPort + ">");
                }
                String command = scanner.nextLine();
                String[] params = command.split(" ");

                if (params[0].equals("#connect")) {
                    if (params.length == 3) {
                        clientPort = Integer.parseInt(params[2]);
                    }
                    connectTargetPeer(params[1]);
                } else if (params[0].equals("#quit")) {
                    this.closeClient();
                } else if (params[0].equals("#createroom")) {
                    String roomName = params[1];

                    boolean result = isValidRoom(roomName);
                    if (result) {
                        Room room = new Room();
                        room.setRoomId(roomName);
                        roomList.add(room);
                    } else {
                        System.out.println("is invalid or already inuse");
                    }
                } else if (params[0].equals("#deleteroom")) {
                    String roomName = params[1];

                    for (int i = 0; i < roomList.size(); i++) {
                        if (roomName.equals(roomList.get(i).getRoomId())) {
                            List<PeerIdentity> peerIdentityList = roomList.get(i).getListPeer();
                            for (int j = 0; j < peerIdentityList.size(); j++) {
                                JSONObject sendMessage = new JSONObject();
                                sendMessage.put("type", "roomchange");
                                sendMessage.put("identity", peerIdentityList.get(j).getIp() + ":" + peerIdentityList.get(j).getPort());
                                sendMessage.put("former", "");
                                sendMessage.put("roomid", "");
                                ClientHandle.broadcast(sendMessage.toJSONString(), roomName);
                            }
                            roomList.remove(i);
                            break;

                        }
                    }
                } else if ("#join".equals(params[0])) {
                    String roomName = params[1];
                    JSONObject sendMessage = new JSONObject();
                    sendMessage.put("type", "join");
                    sendMessage.put("roomid", roomName);
                    sendClientMessage(sendMessage.toJSONString());

                } else if ("#kick".equals(params[0])) {
                    String identity = params[1];
                    String address[] =identity.split(":");
                    String ipAddress = address[0];
                    int addressPort = Integer.parseInt(address[1]);
                    ClientHandle.kickTargetPeer(ipAddress,addressPort);
                }else if ("#list".equals(params[0])) {
                    JSONObject sendMessage = new JSONObject();
                    sendMessage.put("type", "list");
                    sendClientMessage(sendMessage.toJSONString());
                } else if (!params[0].startsWith("#")) {
                    String content = params[0];
                    JSONObject sendMessage = new JSONObject();
                    sendMessage.put("type", "message");
                    sendMessage.put("content", content);
                    sendClientMessage(sendMessage.toJSONString());
                } else if ("#quit".equals(params[0])) {
                    String content = params[1];
                    JSONObject sendMessage = new JSONObject();
                    sendMessage.put("type", "quit");
                    sendClientMessage(sendMessage.toJSONString());
                } else if ("#listneighbors".equals(params[0])) {
                    JSONObject sendMessage = new JSONObject();
                    sendMessage.put("type", "listneighbors");
                    sendClientMessage(sendMessage.toJSONString());
                } else if ("#who".equals(params[0])) {
                    String roomwantknow = params[1];
                    JSONObject sendMessage = new JSONObject();
                    sendMessage.put("type", "who");
                    sendMessage.put("roomid", roomwantknow);
                    sendClientMessage(sendMessage.toJSONString());
                }else if("#searchnetwork".equals(params[0])){
                    ClientHandle.searchNetwork(ip+":"+serverPort);
                } else if ("#shout".equals(params[0])) {
                    String content = params[1];
                    ClientHandle.shoutChildren(content,ip+":"+serverPort);
                    shoutParent(content);
                }  else if (params[0].equals("#help")) {
                    System.out.println("type in any content without start with # to send a normal message..");
                    System.out.println("#help - list all the commands and how to use them");
                    System.out.println("#connect - IP[:port][localport]-connect to another peer");
                    System.out.println("#quit - disconnect from the peer which is connected to");
                    System.out.println("#searchnetwork - local command can be used to search the whole network belongs to this peer");
                    System.out.println("#listneighbors - list all the neighbors that connected to this peer directly");
                    System.out.println("#list - list all the rooms that the server peer has been created");
                    System.out.println("#join - join a room");
                    System.out.println("#deleteroom - local command, a peer can use it to delete a room");
                    System.out.println("#createroom - local command, a peer can use it to create a room");

                } else {
                    System.out.println("command not found");
                }

            }
        }).start();


    }

    private void connectTargetPeer(String targetAddress) {

        String[] ipAndPort = targetAddress.split(":");
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            if (clientPort == null) {
                socket = new Socket(ipAndPort[0], Integer.parseInt(ipAndPort[1]));
            } else {
                socket = new Socket(ipAndPort[0], Integer.parseInt(ipAndPort[1]), inetAddress, clientPort);
            }
            ip = inetAddress.getHostAddress();
            clientPort = socket.getLocalPort();

            if (socket.isConnected()) {
                System.out.println("[" + roomId + "]" + " " + inetAddress.getHostAddress() + ":" + clientPort);
            }
            this.p2pBufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.p2pBufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            listen();


        } catch (Exception ex) {
            ex.printStackTrace();
            clientPort = null;

        }

    }


//    send message to server
    public void sendClientMessage(String message) {
        if(socket==null){
            return;
        }
        try {
            p2pBufferedWriter.write(message);
            p2pBufferedWriter.newLine();
            p2pBufferedWriter.flush();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public void listen() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (socket.isConnected()){
                        String receive = p2pBufferedReader.readLine();
                        JSONObject messageObject = JSONObject.parseObject(receive);
                        if(messageObject==null){
                            closeClient();
                            System.out.println("connect lost....");
                            break;
                        }
                        String type = messageObject.getString("type");
                        if ("roomchange".equals(type)) {
                            String identity = messageObject.getString("identity");

                            String former = messageObject.getString("former");
                            roomId = messageObject.getString("roomid");

                            if (roomId != "" && former.equals("")) {
                                System.out.println(identity + " moved to " + roomId);
                            }
                            else if (roomId != "" && !former.equals("")) {
                                System.out.println(identity + " moved from " + former + " to " + roomId);
                            }

                        }
                        if ("roomcontents".equals(type)) {
                            JSONArray identities = messageObject.getJSONArray("identities");
                            String owner = messageObject.getString("owner");
                            String room = messageObject.getString("roomid");
                            System.out.println("room-" + room + " : ");
                            List<PeerIdentity> peerIdentityList = identities.toJavaList(PeerIdentity.class);
                            for (PeerIdentity peerIdentity:peerIdentityList){
                                System.out.println(peerIdentity);
                            }
                            System.out.println("owner is " + owner);
                        }

                        if (type.equals("neighbors")) {
                            JSONArray neighbors = messageObject.getJSONArray("neighbors");
                            System.out.println(neighbors.toJSONString());
                        }
                        if (type.equals("message")) {
                            String identity = messageObject.getString("identity");
                            String content = messageObject.getString("content");
                            System.out.println(identity+":"+content);
                        }
                        if (type.equals("roomlist")) {
                            String rooms = messageObject.getString("rooms");
                            System.out.println(rooms);
                        }

                        if(type.equals("searchnetwork")){
                            JSONObject message=new JSONObject();
                            message.put("type","searchnetwork");
                            message.put("identity",ip+":"+clientPort);
                            message.put("roomList",roomList);
                            String initiator = messageObject.getString("initiator");
                            message.put("initiator",messageObject.getString("initiator"));
                            sendClientMessage(message.toJSONString());
                            ClientHandle.searchNetwork(initiator);
                        }

                        if ("shout".equals(type)) {
                            String content = messageObject.getString("content");
                            String identity = messageObject.getString("identity");
                            if(identity.equals(ip+":"+serverPort)){
                                continue;
                            }
                            System.out.println(identity+": "+content);
                            ClientHandle.shoutChildren(content,ip+":"+serverPort);
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }).start();
    }

    public void closeClient() {
        try {
            if (socket != null) {
                socket.close();
                p2pBufferedWriter.close();
                p2pBufferedReader.close();
                clientPort = null;
                socket = null;
                roomId = "";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public boolean isValidRoom(String name) {
        Pattern p = Pattern.compile("[a-zA-Z]\\w{1,32}");
        Matcher m = p.matcher(name);
        if (m.find()) {
            for (int i = 0; i < roomList.size(); i++) {
                if (roomList.get(i).getRoomId().equals(name)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }


    public void shoutParent(String content){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "shout");
        jsonObject.put("identity", ip + ":" + clientPort);
        jsonObject.put("content",content);
        sendClientMessage(jsonObject.toJSONString());
    }

    public static void main(String[] args) {

        if (args.length == 0) {
            ChatPeer peerClient = new ChatPeer(4444, null);
            peerClient.startPeerClient();
        }
        if (args.length == 2){
            if (args[0].equals("-p")){
                ChatPeer peerClient = new ChatPeer(Integer.parseInt(args[1]), null);
                peerClient.startPeerClient();
            }
            if (args[0].equals("-i")){
                ChatPeer peerClient = new ChatPeer(4444, Integer.parseInt(args[1]));
                peerClient.startPeerClient();
            }
        }
        if (args.length == 4){
            if (args[0].equals("-p")){
                ChatPeer peerClient = new ChatPeer(Integer.parseInt(args[1]), Integer.parseInt(args[3]));
                peerClient.startPeerClient();
            }
            if (args[0].equals("-i")){
                ChatPeer peerClient = new ChatPeer(Integer.parseInt(args[3]), Integer.parseInt(args[1]));
                peerClient.startPeerClient();
            }
        }

    }


}
