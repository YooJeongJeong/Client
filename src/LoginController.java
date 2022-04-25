import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ResourceBundle;

public class LoginController implements Initializable {
    String id, pw;
    SocketChannel socketChannel;

    public void doLogin() {
        Thread thread = new Thread(() -> {
            try {
                socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(true);
                socketChannel.connect(new InetSocketAddress("localhost", 5001));

                /* 로그인 정보를 Message 객체에 담아 직렬화하여 서버로 전송 */
                Message message = new Message(userId.getText(), userPw.getText(), MsgType.LOG_IN);
                Message.writeMsg(socketChannel, message);

                /* 서버로부터 응답을 받고 Message 객체로 복원 */
                message = Message.readMsg(socketChannel);

                /* 로그인 성공하면 화면 전환, 실패하면 메시지를 화면에 출력하고 소켓을 닫는다 */
                if(message.getMsgType() == MsgType.SUCCESS) {
                    id = message.getId();
                    pw = message.getPw();
                    changeWindow();
                } else {
                    Message finalMessage = message;
                    Platform.runLater(() -> {
                        alertLogin.setText(finalMessage.getData());
                        alertLogin.setOpacity(1);
                    });
                    clear();
                    if(socketChannel.isOpen())
                        socketChannel.close();
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    alertLogin.setText("[서버와 연결이 되지 않습니다]");
                    alertLogin.setOpacity(1);
                });
                try {
                    if(socketChannel.isOpen())
                        socketChannel.close();
                } catch (Exception e2) {
                    e2.printStackTrace();
                }
            }
        });
        thread.start();
    }

    public void doRegister() {
        Thread thread = new Thread(() -> {
            try {
                socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(true);
                socketChannel.connect(new InetSocketAddress("localhost", 5001));

                /* 회원가입 정보를 Message 객체로 담은 뒤 직렬화하여 서버로 전송 */
                Message message = new Message(newId.getText(), newPw.getText(), MsgType.SIGN_UP);
                Message.writeMsg(socketChannel, message);

                /* 서버로부터 응답을 받고 Message 객체로 복원 */
                message = Message.readMsg(socketChannel);

                /* 회원가입 결과에 상관없이 메시지를 화면에 출력하고 소켓을 닫는다 */
                Message finalMessage = message;
                Platform.runLater(() -> {
                    alertSignup.setText(finalMessage.getData());
                    alertSignup.setOpacity(1);
                });
                if(socketChannel.isOpen())
                    socketChannel.close();

            } catch (Exception e) {
                Platform.runLater(() -> {
                    alertSignup.setText("[서버와의 연결이 끊어졌습니다]");
                    alertSignup.setOpacity(1);
                });
                try {
                    if(socketChannel.isOpen())
                        socketChannel.close();
                } catch (Exception e2) {}
            }
        });
        thread.start();
    }
    /************************************************ JavaFx UI ************************************************/
    Stage primaryStage;
    @FXML Pane loginPanel, signupPanel;
    @FXML TextField userId, newId;
    @FXML PasswordField userPw, newPw;
    @FXML Button btnSignup, btnLogin, btnGoToSignup, btnGoToLogin;
    @FXML Label alertLogin, alertSignup;

    @Override
    public void initialize(URL location, ResourceBundle resources) {}

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setOnCloseRequest(e -> System.exit(0));
    }

    public void handleBtnAction(ActionEvent event) {

        alertLogin.setOpacity(0);
        alertSignup.setOpacity(0);

        if(event.getSource().equals(btnGoToSignup)) {
            clear();
            signupPanel.toFront();
        } else if(event.getSource().equals(btnLogin)) {
            doLogin();
        } else if(event.getSource().equals(btnSignup)) {
            doRegister();
            clear();
        } else if(event.getSource().equals(btnGoToLogin)) {
            clear();
            loginPanel.toFront();
        }
    }

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

    public void clear() {
        userId.setText("");
        userPw.setText("");
        newId.setText("");
        newPw.setText("");
    }
}
