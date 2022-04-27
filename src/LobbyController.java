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
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;

public class LobbyController implements Initializable {
    SocketChannel socketChannel;
    Message message;
    String id, pw;

    public void receive() {
        Thread thread = new Thread(() -> {
            while(true) {
                try {
                    message = Message.readMsg(socketChannel);
                    switch(message.getMsgType()) {
                        case INFO:
                            showInfo();     break;
                        case MAKE_SUCCESS:
                        case JOIN_SUCCESS:
                            changeWindow(message.getData());    return;
                        case MAKE_FAILED:
                            System.out.println(message.getData());  break;
                        case JOIN_FAILED:
                            throw new Exception();
                        default:
                    }
                } catch (Exception e) {
                    try {
                        if(socketChannel != null && socketChannel.isOpen())
                            socketChannel.close();
                    } catch (Exception e2) {}
                }
            }
        });
        thread.start();
    }

    /* 서버로 유저와 방 정보 리스트를 보내달라고 요청하는 메소드 */
    public void receiveInfo() {
        try {
            Message message = new Message(id, pw, Room.LOBBY, MsgType.INFO);
            Message.writeMsg(socketChannel, message);
        } catch (Exception e) {}
    }

    /* 서버로부터 받은 유저와 방 정보를 테이블뷰에 출력하도록 하는 메소드 */
    public void showInfo() {
        if(message == null)
            return;
        List<Room> rooms = message.getRooms();
        List<User> users = message.getUsers();
        if(rooms != null)
            showRoomInfo(rooms);
        if(users != null)
            showUserInfo(users);
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
            if(name == null || name.trim().isEmpty())
                throw new Exception();

            Message message = new Message(id, pw, name, MsgType.JOIN);
            Message.writeMsg(socketChannel, message);
        } catch (Exception e) {
            System.out.println("방 이름을 확인해주세요");
        }
    }

    /* 클라이언트가 방 만들기 버튼을 누를 때 실행되는 메소드 */
    public void makeRoom() {
        try {
            /* 만들려는 방 이름을 메시지로 담아 서버로 전송 */
            String name = roomName.getText();
            if(name == null || name.trim().isEmpty())
                throw new Exception();
            Message message = new Message(id, pw, name, MsgType.MAKE);
            Message.writeMsg(socketChannel, message);
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
        receive();
        receiveInfo();
    }

    public void handleBtnAction(ActionEvent event) {
        if(event.getSource().equals(btnJoinRoom)) {
            joinRoom();
        } else if(event.getSource().equals(btnMakeRoom)) {
            makeRoom();
        } else if(event.getSource().equals(btnRefresh)) {
            receiveInfo();
        }
    }

    /* 초대된 경우, 초대 팝업창 띄움 */
    public void showInvitedPopup() {
        try {
            Stage dialog = new Stage(StageStyle.UTILITY);
            dialog.setOnCloseRequest(e -> {

            });
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(primaryStage);
            dialog.setTitle("download popup");

            Parent parent = FXMLLoader.load(getClass().getResource("downloadPopUp.fxml"));

            Button btnOk = (Button) parent.lookup("#btnOk");
            btnOk.setText("다운로드");
            Button btnCancel = (Button) parent.lookup("#btnCancle");
            btnCancel.setText("취소");
            btnCancel.setOnAction(e -> dialog.close());

            Scene scene = new Scene(parent);
            dialog.setResizable(false);
            dialog.setScene(scene);
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
