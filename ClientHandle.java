import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientHandle implements Runnable {



    public static ArrayList<ClientHandle> listNeighbors = new ArrayList<>();
    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String clientName;
    private String roomId;
    private String ip;
    private int port;
    private String formerName = "";
    private static ChatPeer chatPeer;

    private static BufferedWriter netWorkBufferedWriter;

    public ClientHandle(Socket socket, ChatPeer chatPeer) {
        ClientHandle.chatPeer = chatPeer;
        try {
            this.socket = socket;


            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            clientName = "guest" + listNeighbors.size();
            ip = socket.getInetAddress().getHostAddress();
            port = socket.getPort();
            listNeighbors.add(this);
        } catch (IOException e) {
            close(socket, bufferedWriter, bufferedReader);
        }
    }

    @Override
    public void run() {
        while (socket.isConnected()) {
            try {
                String receive = bufferedReader.readLine();
                JSONObject messageObject = JSONObject.parseObject(receive);
                String type = messageObject.getString("type");
                if ("join".equals(type)) {

                    String roomid = messageObject.getString("roomid");
                    joinTargetRoom(roomid);
                    JSONObject sendMessage = new JSONObject();
                    sendMessage.put("type", "roomchange");
                    sendMessage.put("identity", ip + ":" + port);
                    sendMessage.put("former", this.formerName);
                    sendMessage.put("roomid", roomid);
                    this.roomId = roomid;
                    this.formerName = roomid;
                    broadcast(sendMessage.toJSONString());
                }

                if ("who".equals(type)) {
                    String roomid = messageObject.getString("roomid");
                    List<Room> roomList = chatPeer.roomList;

                    for (int i = 0; i < roomList.size(); i++) {
                        if (roomid.equals(roomList.get(i).getRoomId())) {

                            JSONObject sendContentMessage = new JSONObject();
                            sendContentMessage.put("type", "roomcontents");
                            sendContentMessage.put("roomid", roomid);
                            sendContentMessage.put("identities", roomList.get(i).getListPeer());
                            sendContentMessage.put("owner", chatPeer.ip + ":" + chatPeer.serverPort);

                            this.bufferedWriter.write(sendContentMessage.toJSONString());
                            this.bufferedWriter.newLine();
                            this.bufferedWriter.flush();
                        }
                    }
                }

                if ("list".equals(type)) {
                    List<Room> roomList = chatPeer.roomList;
                    JSONObject sendContentMessage = new JSONObject();
                    sendContentMessage.put("type", "roomlist");
                    JSONArray rooms = new JSONArray();
                    for (Room room : roomList) {
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("rooms", room.getRoomId());
                        jsonObject.put("count", room.getListPeer().size());
                        rooms.add(jsonObject);
                    }

                    sendContentMessage.put("rooms", JSON.toJSONString(rooms));
                    this.bufferedWriter.write(sendContentMessage.toJSONString());
                    this.bufferedWriter.newLine();
                    this.bufferedWriter.flush();
                }

                if ("message".equals(type)) {
                    JSONObject sendMessage = new JSONObject();
                    sendMessage.put("type", "message");
                    sendMessage.put("identity", ip + ":" + port);
                    sendMessage.put("content", messageObject.getString("content"));
                    broadcast(sendMessage.toJSONString());
                }

                if ("quit".equals(type)) {
                    List<Room> roomList = chatPeer.roomList;
                    PeerIdentity peerIdentity = new PeerIdentity(this.ip, this.port);

                    quitRoom(roomList, peerIdentity);

                    JSONObject sendMessage = new JSONObject();
                    sendMessage.put("type", "roomchange");
                    sendMessage.put("identity", ip + ":" + port);
                    sendMessage.put("former", "");
                    sendMessage.put("roomid", "");
                    broadcast(sendMessage.toJSONString());
                    close(this.socket, this.bufferedWriter, this.bufferedReader);
                }

                if ("listneighbors".equals(type)) {
                    ClientHandle.netWorkBufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                    JSONObject sendMessage = new JSONObject();
                    List<String> neighbors = new ArrayList<>();
                    for (int i = 0; i < listNeighbors.size(); i++) {
                        ClientHandle clientHandle = listNeighbors.get(i);
                        if (clientHandle.ip.equals(this.ip) && clientHandle.port == this.port) {
                            continue;
                        }
                        neighbors.add(listNeighbors.get(i).ip + ":" + listNeighbors.get(i).port);
                    }
                    sendMessage.put("type", "neighbors");
                    sendMessage.put("neighbors", neighbors);

                    sendMessageToClient(sendMessage);
                }

                if ("searchnetwork".equals(type)) {
                    String initiator = messageObject.getString("initiator");
                    if (initiator.equals(chatPeer.ip + ":" + chatPeer.serverPort)) {
                        System.out.println(messageObject.getString("identity"));
                        JSONArray jsonArray = messageObject.getJSONArray("roomList");
                        List<Room> roomList = jsonArray.toJavaList(Room.class);
                        for (Room room : roomList) {
                            System.out.println(room.getRoomId() + " " + room.getListPeer().size() + " users");
                        }
                    } else {
                        chatPeer.sendClientMessage(messageObject.toJSONString());
                    }
                }

                if ("shout".equals(type)) {
                    String content = messageObject.getString("content");
                    String identity = messageObject.getString("identity");
                    System.out.println(identity+": "+content);
                    shoutChildren(content,identity);
                }

            } catch (IOException e) {
                System.out.println("connection lost");
                close(socket,bufferedWriter,bufferedReader);
                break;
            }

        }
    }

    public void broadcast(String messageToSend) {
        for (ClientHandle clientHandle : listNeighbors) {

            try {
                if (clientHandle.roomId == null && this.roomId == null) {
                    clientHandle.bufferedWriter.write(messageToSend);
                    clientHandle.bufferedWriter.newLine();
                    clientHandle.bufferedWriter.flush();
                    continue;
                }
                if (clientHandle.roomId == null) {
                    continue;
                }
                if (clientHandle.roomId.equals(this.roomId)) {
                    clientHandle.bufferedWriter.write(messageToSend);
                    clientHandle.bufferedWriter.newLine();
                    clientHandle.bufferedWriter.flush();
                }
            } catch (IOException e) {
                close(socket, bufferedWriter, bufferedReader);
            }
        }
    }

    public static void broadcast(String messageToSend, String rooid) {
        for (ClientHandle clientHandle : listNeighbors) {
            try {
                if (clientHandle.roomId.equals(rooid)) {
                    clientHandle.bufferedWriter.write(messageToSend);
                    clientHandle.bufferedWriter.newLine();
                    clientHandle.bufferedWriter.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public static void broadcast(String messageToSend, String ip, int port) {
        for (ClientHandle clientHandle : listNeighbors) {
            try {
                if (clientHandle.ip.equals(ip) && clientHandle.port == port) {
                    continue;
                }
                clientHandle.bufferedWriter.write(messageToSend);
                clientHandle.bufferedWriter.newLine();
                clientHandle.bufferedWriter.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public static void broadcastAll(String messageToSend) {
        for (ClientHandle clientHandle : listNeighbors) {
            try {
                clientHandle.bufferedWriter.write(messageToSend);
                clientHandle.bufferedWriter.newLine();
                clientHandle.bufferedWriter.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

//    public List<String> getMemberList(String group) {
//        List<String> memberList = new ArrayList<String>();
//        for (ClientHandle c: listNeighbors) {
//            if (c.roomId.equals(group) ){
//                memberList.add(c.clientName);
//            }
//        }
//        return memberList;
//    }


    public static void kickTargetPeer(String targetIp, int targetPort) {

        for (int i = 0; i < listNeighbors.size(); i++) {
            ClientHandle clientHandle = listNeighbors.get(i);
            if (listNeighbors.get(i).ip.equals(targetIp) && listNeighbors.get(i).port == targetPort) {
                //先退出房间
                PeerIdentity peerIdentity = new PeerIdentity(targetIp, targetPort);
                quitRoom(chatPeer.roomList, peerIdentity);
                //关闭链接
                clientHandle.close(clientHandle.socket, clientHandle.bufferedWriter, clientHandle.bufferedReader);

            }
        }
    }

    public void leave() {
        listNeighbors.remove(this);
    }

    public void close(Socket socket, BufferedWriter bufferedWriter, BufferedReader bufferedReader) {
        leave();
        try {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void sendMessageToClient(JSONObject sendMessage) throws IOException {
        this.bufferedWriter.write(sendMessage.toJSONString());
        this.bufferedWriter.newLine();
        this.bufferedWriter.flush();
    }

    public void joinTargetRoom(String roomid) {
        List<Room> roomList = chatPeer.roomList;
        PeerIdentity peerIdentity = new PeerIdentity(this.ip, this.port);

        quitRoom(roomList, peerIdentity);

        for (int i = 0; i < roomList.size(); i++) {

            if (roomid.equals(roomList.get(i).getRoomId())) {
                roomList.get(i).getListPeer().add(peerIdentity);
            }
        }
    }

    public static void quitRoom(List<Room> roomList, PeerIdentity peerIdentity) {
        Room room = findRoom(roomList, peerIdentity);
        if (room == null) {
            return;
        }
        room.getListPeer().remove(peerIdentity);
    }


    public static Room findRoom(List<Room> roomList, PeerIdentity peerIdentity) {
        for (int i = 0; i < roomList.size(); i++) {

            List<PeerIdentity> peerIdentityList = roomList.get(i).getListPeer();
            if (peerIdentityList == null) {
                continue;
            }
            for (int j = 0; j < peerIdentityList.size(); j++) {
                if (peerIdentity.equals(peerIdentityList.get(j))) {
                    return roomList.get(i);
                }
            }
        }
        return null;
    }

    public static void searchNetwork(String initiator) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "searchnetwork");
        jsonObject.put("initiator", initiator);
        String messageToSend = jsonObject.toJSONString();
        broadcastAll(messageToSend);
    }

    public static void shoutChildren(String content,String identity) {

        //to child
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "shout");
        jsonObject.put("identity", identity);
        jsonObject.put("content", content);
        String messageToSend = jsonObject.toJSONString();
        broadcastAll(messageToSend);

    }


}
