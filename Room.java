
import java.util.ArrayList;
import java.util.List;

public class Room  {

    private String roomId;

    private List<PeerIdentity> listPeer = new ArrayList<>();

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public List<PeerIdentity> getListPeer() {
        return listPeer;
    }

    public void setListPeer(List<PeerIdentity> listPeer) {
        this.listPeer = listPeer;
    }



    @Override
    public String toString() {
        return "{" +
                "roomId='" + roomId + '\'' +
                ",count=" + listPeer.size() +
                '}';
    }
}
