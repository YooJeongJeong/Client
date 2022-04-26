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
    SocketChannel socketChannel;
    String id, pw;

    public void doLogin() {
        try {
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(true);
            socketChannel.connect(new InetSocketAddress("localhost", 5001));

            Message message = new Message(userId.getText(), userPw.getText(), MsgType.LOGIN);
            Message.writeMsg(socketChannel, message);

            message = Message.readMsg(socketChannel);
            if(message.getMsgType() == MsgType.LOGIN_SUCCESS) {
                id = message.getId();
                pw = message.getPw();
                changeWindow();
            } else if(message.getMsgType() == MsgType.LOGIN_FAILED) {
                Message finalMessage = message;
                Platform.runLater(() -> {
                    alertLogin.setText(finalMessage.getData());
                    alertLogin.setOpacity(1);
                });
                clear();
                if (socketChannel.isOpen())
                    socketChannel.close();
            }
        } catch (Exception e) {
            try {
                alertLogin.setText("[서버와 연결이 되지 않습니다]");
                alertLogin.setOpacity(1);
                if(socketChannel != null && socketChannel.isOpen())
                    socketChannel.close();
            } catch (Exception e2) {e2.printStackTrace();}
        }
    }

    public void doRegister() {
        try {
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(true);
            socketChannel.connect(new InetSocketAddress("localhost", 5001));

            Message message = new Message(newId.getText(), newPw.getText(), MsgType.SIGNUP);
            Message.writeMsg(socketChannel, message);

            message = Message.readMsg(socketChannel);
            Message finalMessage = message;
            Platform.runLater(() -> {
                alertSignup.setText(finalMessage.getData());
                alertSignup.setOpacity(1);
            });
            if (socketChannel.isOpen())
                socketChannel.close();
        } catch (Exception e) {
            try {
                alertSignup.setText("[서버와 연결이 되지 않습니다]");
                alertSignup.setOpacity(1);
                if(socketChannel != null && socketChannel.isOpen())
                    socketChannel.close();
            } catch (Exception e2) {e2.printStackTrace();}
        }
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
