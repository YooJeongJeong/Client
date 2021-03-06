import java.io.Serializable;

public class Room implements Serializable {
    static final long serialVersionUID = 1L;

    static final String LOBBY = "lobby";

    private String name;
    private String owner;

    Room(String name, String owner) {
        this.name = name;
        this.owner = owner;
    }

    public String getName() {
        return name;
    }
}
