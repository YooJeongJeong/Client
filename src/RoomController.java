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
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.ResourceBundle;

public class RoomController implements Initializable {
    SocketChannel socketChannel;
    FileChannel fileChannel;
    Message message;
    String id, pw, roomName;
    String separator, dirPath;

    long startTime_upload, endTime_upload;
    long startTime_download, endTime_download;

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
                       case ROOM_INFO:
                           showInfo();
                           break;
                       case JOIN:
                       case EXIT:
                       case DISCONNECT:
                       case INVITE_SUCCESS:
                           receiveInfo();       // 유저가 들어오거나 나가면 유저 목록 새로고침 요청 후 메시지 출력
                       case INVITE_FAILED:
                       case SEND:
                           Platform.runLater(() -> displayText(message.getData()));
                           break;
                       case EXIT_SUCCESS:
                           changeWindow();
                           return;
                       case UPLOAD_START:
                       case UPLOAD_DO:
                           sendFile();
                           break;
                       case DOWNLOAD_LIST:
                           showDownloadPopup(message.getFileInfo());
                           break;
                       case DOWNLOAD_START:
                           openFileChannel(dirPath + message.getData(), MsgType.DOWNLOAD_START);
                           break;
                       case DOWNLOAD_DO:
                           receiveFile();
                           break;
                       case DOWNLOAD_END:
                           closeFileChannel();
                           break;
                       default:
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

    public void openFileChannel(String filePath, MsgType msgType) {
        try {
            if (msgType == MsgType.UPLOAD_START) {
                startTime_upload = System.nanoTime();
                String[] pathArray = filePath.split(separator);
                int pathLength = pathArray.length;
                String fileName = pathArray[pathLength - 1];

                Path path = Paths.get(filePath);
                fileChannel = FileChannel.open(path, StandardOpenOption.READ);

                message = new Message(fileName, MsgType.UPLOAD_START);
                Message.writeMsg(socketChannel, message);

                Platform.runLater(() -> displayText("[업로드 시작: " + fileName + "]"));
            } else if (msgType == MsgType.DOWNLOAD_START) {
                startTime_download = System.nanoTime();
                String fileName = message.getData();

                Path path = Paths.get(filePath);
                Files.createDirectories(path.getParent());
                fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

                message = new Message(fileName, MsgType.DOWNLOAD_DO);
                Message.writeMsg(socketChannel, message);

                Platform.runLater(() -> {displayText("[다운로드 시작 : " + fileName + "]");});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendFile() {
        try {
            String fileName = message.getData();
            ByteBuffer byteBuffer = ByteBuffer.allocate(500);
            int byteCount = fileChannel.read(byteBuffer);
            if(byteCount == -1) {
                message = new Message(fileName, MsgType.UPLOAD_END);
                Message.writeMsg(socketChannel, message);

                fileChannel.close();
                endTime_upload = System.nanoTime();
                Platform.runLater(() -> {displayText("[업로드 완료 : " + fileName + ", " +
                        "수행시간 : " + (endTime_upload - startTime_upload) / 1000000000 + "초]");});
            } else {
                byteBuffer.flip();
                byte[] fileData = new byte[byteBuffer.remaining()];
                byteBuffer.get(fileData);

                message = new Message(fileName, fileData, MsgType.UPLOAD_DO);
                Message.writeMsg(socketChannel, message);
            }
        } catch (Exception e) {
            Platform.runLater(() -> {displayText("[업로드 중 오류 발생]");});
        }
    }

    public void receiveFile() {
        try {
            String fileName = message.getData();
            byte[] fileData = message.getFileData();
            ByteBuffer byteBuffer = ByteBuffer.wrap(fileData);
            fileChannel.write(byteBuffer);

            message = new Message(fileName, MsgType.DOWNLOAD_DO);
            Message.writeMsg(socketChannel, message);
        } catch (Exception e) {
            Platform.runLater(() -> {displayText("[다운로드 중 오류 발생]");});
        }
    }

    public void closeFileChannel() {
        try {
            endTime_download = System.nanoTime();
            fileChannel.close();
            String fileName = message.getData();
            Platform.runLater(() -> {
                displayText("[다운로드 완료 : " + fileName + ", " +
                        "수행시간 : " + (endTime_download - startTime_download) / 1000000000 + "초]");
                });
        } catch (Exception e) {}
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

    /* 서버로 방에 있는 유저 리스트를 달라고 요청 */
    public void receiveInfo() {
        try {
            Message message = new Message(id, pw, roomName, MsgType.ROOM_INFO);
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
            userId.setCellValueFactory(new PropertyValueFactory<User, String>("id"));

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
    public void initialize(URL location, ResourceBundle resources) {
        separator = File.separator;
        if(separator.equals("\\"))
            separator += separator;
        dirPath = "file" + separator;
    }

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
            Platform.runLater(() -> txtInput.setText(""));
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
            openFileChannel(selectedFilePath, MsgType.UPLOAD_START);
        } catch (Exception e) {
            Platform.runLater(() -> {displayText("[파일 업로드 취소]");});
        }
    }

    public void handleDownloadAction(ActionEvent event) {
        try {
            Message message = new Message(MsgType.DOWNLOAD_LIST);
            Message.writeMsg(socketChannel, message);
        } catch (Exception e) {}
    }

    public void handleInviteAction(ActionEvent event) {
        Platform.runLater(() -> {
            Stage dialog = new Stage(StageStyle.DECORATED);
            dialog.setOnCloseRequest(e -> displayText("[초대 취소]"));
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(primaryStage);
            dialog.setTitle("invite");

            Parent parent = null;
            try {
                parent = FXMLLoader.load(getClass().getResource("invitePopup.fxml"));
            } catch (IOException e) {e.printStackTrace();}

            Label txtTitle = (Label) parent.lookup("#txtTitle");
            txtTitle.setText("초대할 유저의 id를 입력해주세요");

            TextField userId = (TextField) parent.lookup("#userId");

            Button btnOk = (Button) parent.lookup("#btnOk");
            btnOk.setText("초대");
            btnOk.setOnAction(event2 -> {
                Message message = new Message(userId.getText(), "", roomName, MsgType.INVITE);
                try {
                    Message.writeMsg(socketChannel, message);
                } catch (Exception e) {}
                dialog.close();
            });

            Button btnNo = (Button) parent.lookup("#btnNo");
            btnNo.setText("취소");
            btnNo.setOnAction(e -> {
                displayText("[초대 취소]");
                dialog.close();
            });

            Scene scene = new Scene(parent);
            dialog.setResizable(false);
            dialog.setScene(scene);
            dialog.show();
        });
    }

    public void handleExitAction(ActionEvent event) {
        exitRoom();
    }

    /* 서버로부터 받은 다운로드 가능한 파일 리스트를 팝업창에 띄움 */
    public void showDownloadPopup(List<FileInfo> fileList) {
        Platform.runLater(() -> {
            Stage dialog = new Stage(StageStyle.DECORATED);
            dialog.setOnCloseRequest(e -> displayText("[다운로드 취소]"));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(primaryStage);
            dialog.setTitle("download");

            Parent parent = null;
            try {
                parent = FXMLLoader.load(getClass().getResource("downloadPopup.fxml"));
            } catch (IOException e) {e.printStackTrace();}

            TableView fileInfo = (TableView) parent.lookup("#fileInfo");
            TableColumn fileName = (TableColumn) fileInfo.getColumns().get(0);
            TableColumn fileSize = (TableColumn) fileInfo.getColumns().get(1);

            fileName.setCellValueFactory(new PropertyValueFactory<FileInfo, String>("name"));
            fileSize.setCellValueFactory(new PropertyValueFactory<FileInfo, String>("size"));

            ObservableList<FileInfo> data = FXCollections.observableArrayList(fileList);
            fileInfo.setItems(data);

            Button btnOk = (Button) parent.lookup("#btnOk");
            btnOk.setText("다운로드");
            btnOk.setOnAction(event->{
                FileInfo selectedFile = (FileInfo) fileInfo.getSelectionModel().getSelectedItem();
                if(selectedFile == null) {
                    dialog.close();
                    return;
                }
                String file = selectedFile.getName();
                if(file == null || file.trim().isEmpty()) {
                    dialog.close();
                    return;
                }
                /* 서버에 파일 다운로드 요청 */
                Message message = new Message(file, MsgType.DOWNLOAD_START);
                try {
                    Message.writeMsg(socketChannel, message);
                } catch (Exception e) {}
                dialog.close();
            });

            Button btnCancel = (Button) parent.lookup("#btnCancle");
            btnCancel.setText("취소");
            btnCancel.setOnAction(e -> {
                displayText("[다운로드 취소]");
                dialog.close();
            });

            Scene scene = new Scene(parent);
            dialog.setResizable(false);
            dialog.setScene(scene);
            dialog.show();
        });
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
