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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Vector;

public class RoomController implements Initializable {
    SocketChannel socketChannel;
    FileChannel fileChannel;
    Message message;
    String id, pw, roomName, fileName;


    public void stopClient() {
        try {
            Platform.runLater(() -> {
                displayText("[연결 끊음]");
                btnSend.setDisable(true);
            });
            if(socketChannel != null && socketChannel.isOpen())
                socketChannel.close();
        } catch (Exception e) {}
    }

    public void receive() {
        Thread thread = new Thread(() -> {
           while(true) {
               try {
                   message = Message.readMsg(socketChannel);
                   switch(message.getMsgType()) {
                       case INFO:
                           showInfo();
                           break;
                       case JOIN:
                       case EXIT:
                           Platform.runLater(() -> {displayText(message.getData());});
                           receiveInfo();
                           break;
                       case EXIT_SUCCESS:
                           changeWindow();
                           return;
                       /* 클라이언트의 업로드 요청에 대한 서버의 응답 */
                       case DOWNLOAD_READY:
                       case DOWNLOAD_DOING:
                           sendFile();
                           break;
                       case DOWNLOAD_END:
                           closeFileChannel();
                           break;
                       /* 클라이언트의 다운로드 요청에 대한 서버의 응답*/
                       case FILE_LIST:
                       /* 서버가 클라이언트로 파일을 보낼 때 */
                       case UPLOAD_START:
                       case UPLOAD_DOING:
                       case UPLOAD_END:
                       default:
                           Platform.runLater(() -> {displayText(message.getData());});
                   }
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
        try {
            Message message = new Message(id, pw, data, MsgType.SEND);
            Message.writeMsg(socketChannel, message);
        } catch (Exception e) {
            Platform.runLater(() -> {
                displayText("[메시지 전송 실패]");
                stopClient();
            });
        }
    }

    public void openFileChannel(String filePath) {
        try {
            String[] pathArray = filePath.split(File.separator);
            int pathLength = pathArray.length;
            fileName = pathArray[pathLength - 1];

            Path path = Paths.get(filePath);
            fileChannel = FileChannel.open(path, StandardOpenOption.READ);

            Message message = new Message(id, pw, fileName, MsgType.UPLOAD_START);
            Message.writeMsg(socketChannel, message);
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                displayText("[채널 여는 중 오류 발생]");
                stopClient();
            });
        }
    }

    public void sendFile() {
        try {
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            if (fileChannel.read(byteBuffer) == -1) {
                Message message = new Message(id, pw, fileName , MsgType.UPLOAD_END);
                Message.writeMsg(socketChannel, message);
            } else {
                Charset charset = Charset.defaultCharset();
                byteBuffer.flip();
                String data = charset.decode(byteBuffer).toString();
                Message message = new Message(id, pw, data, MsgType.UPLOAD_DOING);
                Message.writeMsg(socketChannel, message);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> displayText("[파일 전송 중 오류 발생]"));
        }
    }

    public void closeFileChannel() {
        try {
            fileChannel.close();
            Platform.runLater(() -> displayText("[" + fileName + " 업로드 완료]"));
        } catch (Exception e) {
            Platform.runLater(() -> displayText("[채널 닫는 중 오류 발생]"));
        }
    }

    public List<String> receiveFileList() {
        List<String> filePath = new Vector<String>();

        return filePath;
    }

    public String getSelectedFileName(List<String> filePath) {
        String selectedFileName = null;

        return selectedFileName;
    }

    public void doDownload(String filePath) {

    }

    public void inviteUser() {

    }

    public void exitRoom() {
        try {
            Message message = new Message(id, pw, roomName, MsgType.EXIT);
            Message.writeMsg(socketChannel, message);
        } catch (Exception e) {
            Platform.runLater(() -> {
                displayText("[연결 오류 : 방 나가기 실패]");
                stopClient();
            });
        }
    }


    /* 서버로 현재 방의 유저 리스트를 달라고 요청한다 */
    public void receiveInfo() {
        try {
            Message message = new Message(id, pw, roomName, MsgType.INFO);
            Message.writeMsg(socketChannel, message);
        } catch (Exception e) {}
    }

    /* 서버로부터 받은 방 정보를 테이블 뷰에 출력 */
    public void showInfo() {
        if(message == null)
            return;
        List<User> users = message.getUsers();
        if(users != null) {
            TableColumn userId = userInfo.getColumns().get(0);
            userId.setCellValueFactory(new PropertyValueFactory("id"));

            ObservableList<User> data = FXCollections.observableArrayList(users);
            userInfo.setItems(data);
        }
    }

    /************************************************ JavaFx UI ************************************************/
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

        receiveInfo();
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

    public void handleUploadAction (ActionEvent event) {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("All Files", "*.*"),
                    new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                    new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.gif"),
                    new FileChooser.ExtensionFilter("Audio Files", "*.wav", ".mp3", "*.aac"));
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            String selectedFilePath = selectedFile.getPath();
            openFileChannel(selectedFilePath);
        } catch (Exception e) {
            Platform.runLater(() -> {displayText("[파일 업로드 취소]");});
        }
    }

    public void handleDownloadAction(ActionEvent event) {
        List<String> fileList = receiveFileList();
        String selectedFileName = getSelectedFileName(fileList);
        doDownload(selectedFileName);
    }

    public void handleInviteAction(ActionEvent event) {
        inviteUser();
    }

    public void handleExitAction(ActionEvent event) {
        exitRoom();
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
                primaryStage.setTitle(Room.LOBBY + " (" + id + ")");
                primaryStage.setResizable(false);
                primaryStage.setScene(scene);
                primaryStage.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
