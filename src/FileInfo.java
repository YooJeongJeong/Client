import java.io.Serializable;
import java.text.DecimalFormat;

public class FileInfo implements Serializable{
    static final long serialVersionUID = 1L;

    private String name;
    private String size;

    FileInfo(String name, String size) {
        this.name = name;
        this.size = size;
    }

    public String  getName() {return name;}
    public String getSize() {return size;}
}