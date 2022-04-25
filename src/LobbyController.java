import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;

public class LobbyController implements Initializable {
    String id, pw;
    SocketChannel socketChannel;

    /* 서버로부터 방 리스트 정보를 받아온다 */
    public void receiveRoomInfo() {
        try {
            /* 서버로 방 정보 요청 */
            Message message = new Message(id, pw, Room.LOBBY, MsgType.ROOM_INFO);
            Message.writeMsg(socketChannel, message);

            /* 서버로부터 응답 받음 */
            message = Message.readMsg(socketChannel);
            if(message == null)
                System.out.println("null");
            List<Room> rooms = message.getRooms();

            if(rooms != null)
                showRoomInfo(rooms);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* 서버로부터 대기실에 있는 유저 리스트를 받아온다 */
    public void receiveUserInfo() {
        try {
            /* 서버로 대기실 유저 정보 요청 */
            Message message = new Message(id, pw, Room.LOBBY, MsgType.USER_INFO);
            Message.writeMsg(socketChannel, message);

            /* 서버로부터 응답 받음 */
            message = Message.readMsg(socketChannel);

            List<User> users = message.getUsers();
            if(users != null)
                showUserInfo(users);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* 서버로부터 받은 방 정보를 테이블 뷰에 출력 */
    public void showRoomInfo(List<Room> rooms) {
        Iterator<Room> iterator = rooms.iterator();
        while(iterator.hasNext()) {
            Room room = iterator.next();
            if(room.getName().equals(Room.LOBBY)) {
                iterator.remove();
                break;
            }
        }

        TableColumn roomName = roomInfo.getColumns().get(0);
        TableColumn roomOwner = roomInfo.getColumns().get(1);

        roomName.setCellValueFactory(new PropertyValueFactory("name"));
        roomOwner.setCellValueFactory(new PropertyValueFactory("owner"));

        ObservableList<Room> data = FXCollections.observableArrayList(rooms);
        roomInfo.setItems(data);

    }

    /* 서버로부터 받은 대기실 유저 정보를 테이블 뷰에 출력*/
    public void showUserInfo(List<User> users) {
        TableColumn userId = userInfo.getColumns().get(0);
        userId.setCellValueFactory(new PropertyValueFactory("id"));

        ObservableList<User> data = FXCollections.observableArrayList(users);
        userInfo.setItems(data);
    }

    /* 클라이언트가 방 입장 버튼을 누를 때 실행되는 메소드 */
    public void joinRoom() {
        try {
            /* 입장할 방 이름을 메시지에 담아 서버로 전송 */
            Room room = roomInfo.getSelectionModel().getSelectedItem();
            String name = room.getName();
            Message message = new Message(id, pw, name, MsgType.JOIN);
            Message.writeMsg(socketChannel, message);

            /* 서버로부터 응답을 받고 참가가 가능하면 채팅방으로 이동 */
            message = Message.readMsg(socketChannel);
            if(message.getMsgType() == MsgType.SUCCESS) {
                changeWindow(name);
            } else if(message.getMsgType() == MsgType.FAILED) {
                throw new Exception();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* 클라이언트가 방 만들기 버튼을 누를 때 실행되는 메소드 */
    public void makeRoom() {
        try {
            /* 만들려는 방 이름을 메시지로 담아 서버로 전송 */
            String name = roomName.getText();

            if(name == null || name.trim().isEmpty())
                throw new Exception();

            Message message = new Message(id, pw, name, MsgType.MAKE_ROOM);
            Message.writeMsg(socketChannel, message);

            /* 서버로부터 응답을 받고 방 만들기에 성공하면 채팅방으로 이동 */
            message = Message.readMsg(socketChannel);
            if(message.getMsgType() == MsgType.SUCCESS) {
                changeWindow(name);
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            System.out.println("방 이름을 확인해주세요");
        }
    }

    /************************************************ JavaFx UI ************************************************/
    Stage primaryStage;
    @FXML TableView<Room> roomInfo;
    @FXML TableView<User> userInfo;
    @FXML TextField roomName;
    @FXML Button btnJoinRoom, btnMakeRoom, btnRefresh;

    @Override
    public void initialize(URL location, ResourceBundle resources) {}

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setInformation(SocketChannel socketChannel, String id, String pw) {
        this.socketChannel = socketChannel;
        this.id = id;
        this.pw = pw;
        refresh();
    }

    public void handleBtnAction(ActionEvent event) {
        if(event.getSource().equals(btnJoinRoom)) {
            joinRoom();
        } else if(event.getSource().equals(btnMakeRoom)) {
            makeRoom();
        } else if(event.getSource().equals(btnRefresh)) {
            refresh();
        }
    }

    public void refresh() {
        receiveRoomInfo();
        receiveUserInfo();
    }

    public void changeWindow(String roomName) {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("room.fxml"));
                Parent room = loader.load();
                RoomController controller = loader.getController();
                controller.setPrimaryStage(primaryStage);
                controller.setInformation(socketChannel, id, pw, roomName);

                Scene scene = new Scene(room);
                primaryStage.setTitle(roomName + " (" + id + ")");
                primaryStage.setResizable(false);
                primaryStage.setScene(scene);
                primaryStage.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
