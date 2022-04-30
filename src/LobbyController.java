import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
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
                        case ROOM_INFO:
                            showInfo();     break;
                        case INVITE:
                            invited();      break;
                        case INVITE_SUCCESS:
                        case MAKE_SUCCESS:
                        case JOIN_SUCCESS:
                            changeWindow(message.getData());    return;
                        case MAKE_FAILED:
                            showPopupMsg(message.getData());    break;
                        case JOIN_FAILED:
                            throw new Exception();
                        case REFRESH:
                            receiveInfo();  break;
                        default:
                    }
                } catch (Exception e) {
                    try {
                        if(socketChannel != null && socketChannel.isOpen())
                            socketChannel.close();
                        break;
                    } catch (Exception e2) {}
                }
            }
        });
        thread.start();
    }

    /* 서버로 유저와 방 정보 리스트를 보내달라고 요청하는 메소드 */
    public void receiveInfo() {
        try {
            Message message = new Message(id, pw, Room.LOBBY, MsgType.ROOM_INFO);
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

        roomName.setCellValueFactory(new PropertyValueFactory<Room, String>("name"));
        roomOwner.setCellValueFactory(new PropertyValueFactory<Room, String>("owner"));

        ObservableList<Room> data = FXCollections.observableArrayList(rooms);
        roomInfo.setItems(data);

    }

    /* 서버로부터 받은 대기실 유저 정보를 테이블 뷰에 출력*/
    public void showUserInfo(List<User> users) {
        TableColumn userId = userInfo.getColumns().get(0);
        userId.setCellValueFactory(new PropertyValueFactory<User, String>("id"));

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
            showPopupMsg("방 이름을 확인해주세요");
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
        } catch (Exception e) {
            showPopupMsg("방 이름을 확인해주세요");
        }
    }

    /* 채팅방에 있는 유저가 대기실 유저를 초대했을 때 초대받는 사람에게 수락/거부 팝업창이 뜬다 */
    public void invited() {
        Platform.runLater(() -> {
            Stage dialog = new Stage(StageStyle.UTILITY);
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(primaryStage);
            dialog.setTitle("invite");

            Parent parent = null;
            try {
                parent = FXMLLoader.load(getClass().getResource("alertPopup.fxml"));
            } catch (IOException e) {e.printStackTrace();}

            /* 초대한 사람의 id와 방 이름 */
            String id = message.getId();
            String roomName = message.getData();

            Label txtTitle = (Label) parent.lookup("#txtTitle");
            txtTitle.setText("[채팅방 초대]" +
                            "\n초대자: " + id +
                            "\n방 이름: " + roomName);
            Button btnOk = (Button) parent.lookup("#btnOk");
            btnOk.setText("수락");
            btnOk.setOnAction(event -> {
                try {
                    Message message = new Message(id, "", roomName, MsgType.INVITE_SUCCESS);
                    Message.writeMsg(socketChannel, message);
                    dialog.close();
                } catch (Exception e) {}
            });

            Button btnNo = (Button) parent.lookup("#btnNo");
            btnNo.setText("거부");
            btnNo.setOnAction(event -> {
                try {
                    Message message = new Message(id, "", roomName, MsgType.INVITE_FAILED);
                    Message.writeMsg(socketChannel, message);
                } catch (Exception e) {}
                dialog.close();
            });

            Scene scene = new Scene(parent);
            dialog.setResizable(false);
            dialog.setScene(scene);
            dialog.show();
        });
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
        primaryStage.setOnCloseRequest(event -> {System.exit(0);});
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

    public void showPopupMsg(String msg) {
        Platform.runLater(() -> {
            Stage dialog = new Stage(StageStyle.DECORATED);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(primaryStage);
            dialog.setTitle("알림");

            dialog.setHeight(150);

            Parent parent = null;
            try {
                parent = FXMLLoader.load(getClass().getResource("alertPopup.fxml"));
            } catch (IOException e) {e.printStackTrace();}

            Label txtTitle = (Label) parent.lookup("#txtTitle");
            txtTitle.setText(msg);
            txtTitle.setFont(Font.font(15));
            txtTitle.setLayoutY(25);

            Button btnOk = (Button) parent.lookup("#btnOk");
            btnOk.setText("확인");
            btnOk.setLayoutX(70);
            btnOk.setLayoutY(60);
            btnOk.setOnAction(event -> {dialog.close();});

            Button btnNo = (Button) parent.lookup("#btnNo");
            btnNo.setVisible(false);

            Scene scene = new Scene(parent);
            dialog.setResizable(false);
            dialog.setScene(scene);
            dialog.show();
        });
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
