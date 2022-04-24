import java.io.Serializable;

public class Room implements Serializable {
    static final long serialVersionUID = 1L;

    static final String LOBBY = "lobby";

    private String name;
    private String owner;
    private int status;

    Room(String name, String owner) {
        this.name = name;
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public int getStatus() {
        return status;
    }
}
