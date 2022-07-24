import java.util.ArrayList;
import java.util.List;

public class Network {
    private String identity;
    private List<RoomInfo> roomInfoList;

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public List<RoomInfo> getRoomInfoList() {
        return roomInfoList;
    }

    public void setRoomInfoList(List<RoomInfo> roomInfoList) {
        this.roomInfoList = roomInfoList;
    }

}

class RoomInfo{
    private String roomId;
    private int count;

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}