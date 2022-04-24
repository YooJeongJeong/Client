import com.sun.glass.ui.CommonDialogs;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.ResourceBundle;

public class RoomController implements Initializable {
    String id, pw, roomName;
    SocketChannel socketChannel;

    public void stopClient() {
        try {
            Platform.runLater(() -> {
                displayText("[연결 끊음]");
                btnSend.setDisable(true);
            });
            if(socketChannel != null && socketChannel.isOpen())
                socketChannel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void receive() {
        Thread thread = new Thread(() -> {
           while(true) {
               try {
                   Message message = Message.readMsg(socketChannel);
                   switch(message.getMsgType()) {
                       /* 클라이언트 입장, 퇴장 메시지일 경우 유저 목록 갱신 */
                       case JOIN:
                       case EXIT:
                           receiveUserInfo();
                           break;
                       default:
                   }

                   Platform.runLater(() -> {
                       displayText(message.getData());
                   });
               } catch (Exception e) {
                   Platform.runLater(() -> displayText("[서버 통신 안됨]"));
                   stopClient();
                   break;
               }
           }
        });
        thread.start();
    }

    public void send(String data) {
        Thread thread = new Thread(() -> {
           try {
               Message message = new Message(id, pw, data, MsgType.SEND);
               Message.writeMsg(socketChannel, message);
           } catch (Exception e) {
               Platform.runLater(() -> {
                   displayText("[메시지 전송 실패]");
                   stopClient();
               });
           }
        });
        thread.start();
    }

    public void doUpload(String filePath) {

    }

    public void doDownload(String filePath) {

    }

    public void doInvite() {

    }

    public void doExit() {
        Thread thread = new Thread(() -> {
            try {
                Message message = new Message(id, pw, roomName, MsgType.EXIT);
                Message.writeMsg(socketChannel, message);
                changeWindow();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    displayText("[연결 오류 : 방 나가기 실패]");
                    stopClient();
                });
            }
        });
        thread.start();
    }

    /* 서버로부터 방에 있는 유저 리스트를 받아온다 */
    public void receiveUserInfo() {
        Thread thread = new Thread(() -> {
            try {
                /* 서버로 방 유저 정보 요청 */
                Message message = new Message(id, pw, roomName, MsgType.USER_INFO);
                Message.writeMsg(socketChannel, message);

                /* 서버로부터 응답 받음 */
                message = Message.readMsg(socketChannel);
                List<User> users = message.getUsers();
                showUserInfo(users);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
    }

    /* 서버로부터 받은 방 정보를 테이블 뷰에 출력 */
    public void showUserInfo(List<User> users) {
        TableColumn userId = userInfo.getColumns().get(0);
        userId.setCellValueFactory(new PropertyValueFactory("id"));

        ObservableList<User> data = FXCollections.observableArrayList(users);
        userInfo.setItems(data);
    }

    /* 퇴장하고 로비로 이동 */
    public void changeWindow() {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("lobby.fxml"));
                Parent lobby = loader.load();
                LobbyController controller = loader.getController();
                controller.setPrimaryStage(primaryStage);
                controller.setInformation(socketChannel, id, pw);

                Scene scene = new Scene(lobby);
                primaryStage.setTitle(Room.LOBBY);
                primaryStage.setResizable(false);
                primaryStage.setScene(scene);
                primaryStage.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    Stage primaryStage;

    @FXML TextArea txtDisplay;
    @FXML TextField txtInput;
    @FXML Button btnSend;
    @FXML TableView<User> userInfo;


    @Override
    public void initialize(URL location, ResourceBundle resources) {}

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setOnCloseRequest(e -> stopClient());
    }

    public void setInformation(SocketChannel socketChannel, String id, String pw, String roomName) {
        this.socketChannel = socketChannel;
        this.id = id;
        this.pw = pw;
        this.roomName = roomName;

        receiveUserInfo();
        receive();
    }

    public void displayText(String text) {
        txtDisplay.appendText(text + "\n");
    }

    public void handleBtnAction(ActionEvent event) {
        if(event.getSource().equals(btnSend)) {
            send(txtInput.getText());
        }
    }

    public void handleUploadAction(ActionEvent event){
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                    new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.gif"),
                    new FileChooser.ExtensionFilter("Audio Files", "*.wav", ".mp3", "*.aac"),
                    new FileChooser.ExtensionFilter("All Files", "*.*"));
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            String selectedFilePath = selectedFile.getPath();
            Platform.runLater(() -> {displayText("[" + selectedFilePath + " 파일 업로드 시작]");});
            doUpload(selectedFilePath);
        } catch (Exception e) {
            Platform.runLater(() -> {displayText("[파일 업로드 취소]");});
        }
    }

    public void handleDownloadAction(ActionEvent event) {

    }

    public void handleInviteAction(ActionEvent event) {

    }

    public void handleExitAction(ActionEvent event) {
        doExit();
    }
}
