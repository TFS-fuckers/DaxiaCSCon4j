import com.tfs.dxcscon4j.Connection;
import com.tfs.dxcscon4j.protocol.Vertification;

public class ClientTest {
    public static void main(String[] args) {
        new Connection("localhost", 25585, new Vertification("TEST"));
    }
}
