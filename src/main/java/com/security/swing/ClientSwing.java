package com.security.swing;

import com.security.keyutil.AES256Util;
import com.security.keyutil.RSAUtil;
import com.security.socketService.client.ClientService;
import com.security.socketService.file.FileReceiver;
import com.security.socketService.file.FileSender;

import java.awt.EventQueue;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.Color;
import java.io.*;
import java.security.*;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import static com.security.socketService.client.Client.aesKey;

public class ClientSwing extends JFrame {

    // Swing panel
    private static JPanel contentPane;
    private static JTextField ChattextField;
    private static JLabel IPLabel;
    private static JLabel PortLabel;
    private static JButton ConnectionBtn;
    private static JButton RSAKeyGenerationBtn;
    private static JButton SaveIntoFileBtn;
    private static JButton SendPublicKeyBtn;
    private static JButton LoadFileBtn;
    private static JLabel ConnectionInfo;
    private static JTextField ServerPublicKeyInfo;
    private static JTextField ServerPrivateKeyInfo;
    private static JTextField ServerAESKeyInfo;
    private static JTextField ClientPublicKeyInfo;
    private static JTextField ClientPrivateKeyInfo;
    private static JTextField ClientAESKeyInfo;
    private static JTextArea ChatInfo;
    private static JTextField MsgEncryptInfo;
    private static JTextArea FileEncryptedInfo;
    private static JButton SendFileBtn;
    private static JButton SendMsgBtn;
    private static ClientService clientService;
    private static JButton SendAESKeyBtn;
    private static JButton GETAESKeyBtn;
    private static JButton GetFileBtn;


    /**
     * Launch the application.
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    // make Frame and set visible
                    ClientSwing frame = new ClientSwing();
                    frame.setVisible(true);

                    connection();
                    makeRSAKey();
                    saveMyRSAKeyToTxt();
                    getAndSavePublicKeyFromServer();
                    sendMyPublicKeyToServer();
                    makeAndSendAESKey();
                    GetAESKeyFromServer();
                    sendChatButtonToServer();
                    sendFile();
                    getFile();


                    // continuously check chatting change per 1 second
                    TimerTask task = new TimerTask() {
                        public void run() {
                            checkRefresh();
                        }
                    };
                    java.util.Timer timer = new Timer("Timer");
                    long delay = 3000L;
                    long period = 1000L; // 1 second
                    timer.schedule(task, delay, period);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // get File from server
    private static void getFile() {
        GetFileBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // if client is not connected, return nothing
                if (clientService == null) {
                    return;
                }
                // if AESKey is null, alarm
                if (clientService.client.aesKey == null) {
                    JOptionPane.showMessageDialog(null, "Make AES key first");
                    return;
                }
                // set File directory
                File file = new File("");
                String rootPath = String.valueOf(file.getAbsoluteFile());
                rootPath += "\\src\\main\\java\\com\\security\\filestore\\client\\FileClientReceive";

                // using FileReceiver.java to send file
                FileReceiver fileReceiver = new FileReceiver(clientService.getSk(), clientService.client.aesKey, rootPath, clientService.client.myPublicKey);
                fileReceiver.run();
                String decryptedMsg = fileReceiver.decryptedMsg;


                // if Digital Signature is same, update Swing
                if (fileReceiver.dsEqaul) {
                    String txt = "";
                    txt += "client receive path : " + rootPath + "\n";
                    String receive = rootPath + "\\src\\main\\java\\com\\security\\filestore\\server\\FileServerWillSend";
                    txt += "server path : " + receive + "\n\n";
                    txt += "Digital Signature is same \n";
                    txt += "Digital Signature = " + fileReceiver.staticGetDs.toString() + "\n\n";
                    txt += "CipherText of file by AES : " + decryptedMsg;
                    FileEncryptedInfo.setText(txt);
                    // if not, alarm
                } else {
                    String txt = "Digital signature is different. Integrity problem happen!!";
                    FileEncryptedInfo.setText(txt);
                }

            }
        });
    }

    // Send file to server
    private static void sendFile() {
        SendFileBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // if client is not connected, return nothing
                if (clientService == null) {
                    return;
                }
                // if AESKey is null, alarm
                if (clientService.client.aesKey == null) {
                    JOptionPane.showMessageDialog(null, "Make AES key first");
                    return;
                }
                // using JFileChooser to choose file
                JFileChooser chooser = new JFileChooser();

                // set File directory
                File file = new File("");
                String rootPath = String.valueOf(file.getAbsoluteFile());
                rootPath += "\\src\\main\\java\\com\\security\\filestore\\client\\FileClientWillSend";

                chooser.setCurrentDirectory(new File(rootPath));

                int ret = chooser.showOpenDialog(null);

                // if there is no selection in JFileChooser, alarm
                if (ret != JFileChooser.APPROVE_OPTION) {
                    JOptionPane.showMessageDialog(null, "경로를 선택하지 않았습니다");
                    return;
                }
                // set receving root. root is set
                String filePath = chooser.getSelectedFile().getPath();
                String[] split = filePath.split("\\\\");
                String fileNm = split[split.length - 1];
                filePath = filePath.substring(0, filePath.length() - fileNm.length() - 1);

                // using FileSender.java to send file
                FileSender fileSender = new FileSender(clientService.getSk(), filePath, fileNm, clientService.client.aesKey, clientService.client.publicKey);
                fileSender.run();
                String encryptedMsg = fileSender.encryptedMsg;

                // update Swing with related data
                String txt = "";
                txt += "client path : " + rootPath + "\n";
                String receive = rootPath + "\\src\\main\\java\\com\\security\\filestore\\server\\FileServerReceive";
                txt += "server receive path : " + receive + "\n";
                txt += "Digital Signature = " + clientService.client.publicKey.toString() + "\n\n";
                txt += "CipherText of file by AES : " + encryptedMsg;
                FileEncryptedInfo.setText(txt);
            }
        });
    }

    // continuously check chatting stream
    private static void checkRefresh() {
        InputStream input_data = null;
        // if there is no stream, nothing happen
        try {
            input_data = clientService.chattingSk.getInputStream();
        } catch (Exception e) {
            return;
        }

        // if there is stream, get message
        byte[] receiveBuffer = new byte[4096];
        String data = null;
        String encryptData = "";
        // read text msg
        try {
            int size = input_data.read(receiveBuffer);
            data = new String(receiveBuffer);
            encryptData = data;
            // decrypt ciphertext
            data = AES256Util.decrypt(data.substring(0, size), aesKey);
        } catch (Exception e) {
            return;
        }

        // update chatting history with encrypted message and original message
        int index = clientService.client.chatHistory.size() + 1;
        data += "\n" + "(" + encryptData + ")";
        clientService.client.chatHistory.put("server" + index, data);

        // update Swing chat info
        String text = "";
        Iterator<String> keys = clientService.client.chatHistory.keySet().iterator();
        while (keys.hasNext()) {
            String key = keys.next();
            text += key + " : " + clientService.client.chatHistory.get(key) + "\n";
        }

        ChatInfo.setText("client chat history\n\n" + text);
    }


    // send chat to serveer
    private static void sendChatButtonToServer() {
        SendMsgBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // get text
                String text = ChattextField.getText();
                // if client is not connected, return nothing
                if (aesKey == null) {
                    JOptionPane.showMessageDialog(null, "Make AES key first");
                    return;
                }
                // if AESKey is null, alarm
                if (text.length() == 0) {
                    JOptionPane.showMessageDialog(null, "Write Something!");
                } else {

                    try {
                        // using clientService - sendChatToServer method to send chat
                        String encrypt = clientService.sendChatToServer(text, aesKey);
                        ChattextField.setText("");
                        MsgEncryptInfo.setText(encrypt);

                        // update Swing with related data
                        String text2 = "";
                        Iterator<String> keys = clientService.client.chatHistory.keySet().iterator();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            text2 += key + " : " + clientService.client.chatHistory.get(key) + "\n";
                        }

                        ChatInfo.setText("client chat history\n\n" + text2);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });
    }

    // get AES key from server
    private static void GetAESKeyFromServer() {
        GETAESKeyBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    // using clientService - checkEncryptedAES method
                    clientService.checkEncryptedAES();
                    // update Swing with related data
                    ClientAESKeyInfo.setText(aesKey);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    // make AES Key and send it to server
    private static void makeAndSendAESKey() {
        SendAESKeyBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // using clientService - makeAESKeyInClinet method
                clientService.makeAESKeyInClinet();
                // update Swing with related data
                ClientAESKeyInfo.setText(aesKey);
                try {
                    // using clientService - sendAESKeyToServer method
                    clientService.sendAESKeyToServer();
                } catch (IOException | NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException | InvalidKeyException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    // send my public key to server
    private static void sendMyPublicKeyToServer() {
        SendPublicKeyBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    // using clientService - sendPublicKeyToServer method
                    clientService.sendPublicKeyToServer(clientService.client.myPublicKey);

                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    // get and save publickey from server
    private static void getAndSavePublicKeyFromServer() {
        LoadFileBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    // using clientService - getPublicKeyFromServer method
                    clientService.getPublicKeyFromServer();
                    // update Swing with related data
                    ServerPublicKeyInfo.setText(String.valueOf(clientService.client.publicKey));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                // save public key data to client local
                File file = new File("");
                String rootPath = String.valueOf(file.getAbsoluteFile());
                rootPath += "\\src\\main\\java\\com\\security\\filestore\\client\\keystorage\\ServerPublicKey.txt";

                // using outputstream to save data
                try (OutputStream output = new FileOutputStream(rootPath);
                ) {
                    String str = String.valueOf(clientService.client.publicKey);
                    byte[] by = str.getBytes();
                    output.write(by);
                    // alarm that data is saved
                    JOptionPane.showMessageDialog(null, rootPath + " 에 Server 의 publicKey저장되었습니다.");

                } catch (IOException ex) {
                    ex.printStackTrace();
                }

            }
        });
    }


    // save my RSA key (public key and private key)
    private static void saveMyRSAKeyToTxt() {
        SaveIntoFileBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (clientService == null) {
                    return;
                }

                // save in client local
                File file = new File("");
                String rootPath = String.valueOf(file.getAbsoluteFile());
                rootPath += "\\src\\main\\java\\com\\security\\filestore\\client\\keystorage\\ClientPublicKey.txt";

                // using outputstream to save data
                try (OutputStream output = new FileOutputStream(rootPath);
                ) {
                    String str = String.valueOf(clientService.client.myPublicKey);
                    byte[] by = str.getBytes();
                    output.write(by);
                    // alarm that data is saved
                    JOptionPane.showMessageDialog(null, rootPath + " 에 client 의 publicKey저장되었습니다.");

                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    // make RSA key
    private static void makeRSAKey() {
        RSAKeyGenerationBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // if no connection, nothing happen
                if (clientService == null) {
                    return;
                }

                KeyPair keyPair = null;
                // get RSA key pair
                try {
                    keyPair = RSAUtil.genRSAKeyPair();
                } catch (NoSuchAlgorithmException ex) {
                    ex.printStackTrace();
                }

                // set client's field with data
                PublicKey publicKey = keyPair.getPublic();
                PrivateKey privateKey = keyPair.getPrivate();
                clientService.client.myPublicKey = publicKey;
                clientService.client.myPrivateKey = privateKey;

                // update Swing with related data
                ClientPublicKeyInfo.setText(String.valueOf(clientService.client.myPublicKey));
                ClientPrivateKeyInfo.setText(String.valueOf(clientService.client.myPrivateKey));

            }
        });
    }

    // connection
    private static void connection() {
        ConnectionBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    // make new client and update Swing with related data
                    clientService = new ClientService();
                    ConnectionInfo.setText("Connection : Success");
                    IPLabel.setText("IP address : " + clientService.client.ipAddress);
                    PortLabel.setText("Socket Info : " + clientService.getSk());
                } catch (IOException ex) {
                    // this is failure case
                    ConnectionInfo.setText("Connection : Failure");
                    IPLabel.setText("IP address : ");
                    PortLabel.setText("Socket Info : ");
                    ex.printStackTrace();
                }
            }
        });
    }

    /**
     * Create the frame.
     */
    public ClientSwing() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 653, 741);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);
        contentPane.setLayout(null);

        JLabel modeNo = new JLabel("Mode : Client");
        modeNo.setFont(new Font("Yu Gothic", Font.BOLD, 15));
        modeNo.setBounds(12, 10, 111, 83);
        contentPane.add(modeNo);

        IPLabel = new JLabel("IP address : ");
        IPLabel.setBounds(144, 20, 467, 28);
        contentPane.add(IPLabel);

        PortLabel = new JLabel("Socket Info : ");
        PortLabel.setBounds(144, 48, 467, 28);
        contentPane.add(PortLabel);

        ConnectionBtn = new JButton("Connection");
        ConnectionBtn.setBounds(12, 91, 115, 23);
        contentPane.add(ConnectionBtn);

        JLabel KeyNo = new JLabel("Key management");
        KeyNo.setFont(new Font("굴림", Font.BOLD, 12));
        KeyNo.setBounds(12, 134, 115, 23);
        contentPane.add(KeyNo);

        RSAKeyGenerationBtn = new JButton("RSA key generation");
        RSAKeyGenerationBtn.setBounds(12, 167, 143, 23);
        contentPane.add(RSAKeyGenerationBtn);

        SaveIntoFileBtn = new JButton("Save into a file");
        SaveIntoFileBtn.setBounds(177, 167, 132, 23);
        contentPane.add(SaveIntoFileBtn);

        SendPublicKeyBtn = new JButton("Send Public Key");
        SendPublicKeyBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            }
        });
        SendPublicKeyBtn.setBounds(321, 167, 143, 23);
        contentPane.add(SendPublicKeyBtn);

        LoadFileBtn = new JButton("Load from a file");
        LoadFileBtn.setBounds(476, 167, 151, 23);
        contentPane.add(LoadFileBtn);

        JLabel ServerNo = new JLabel("Server");
        ServerNo.setFont(new Font("굴림", Font.BOLD, 12));
        ServerNo.setBounds(12, 200, 50, 15);
        contentPane.add(ServerNo);

        JLabel PublicNo = new JLabel("Public Key");
        PublicNo.setBounds(12, 225, 85, 15);
        contentPane.add(PublicNo);

        JLabel PrivateNo = new JLabel("Private Key");
        PrivateNo.setBounds(12, 250, 85, 15);
        contentPane.add(PrivateNo);

        JLabel AesNo = new JLabel("AES Key");
        AesNo.setBounds(12, 275, 85, 15);
        contentPane.add(AesNo);

        JLabel ClinetNo = new JLabel("Client");
        ClinetNo.setFont(new Font("굴림", Font.BOLD, 12));
        ClinetNo.setBounds(12, 300, 50, 15);
        contentPane.add(ClinetNo);

        JLabel PublicNo2 = new JLabel("Public Key");
        PublicNo2.setBounds(12, 325, 85, 15);
        contentPane.add(PublicNo2);

        JLabel PrivateNo2 = new JLabel("Private Key");
        PrivateNo2.setBounds(12, 350, 85, 15);
        contentPane.add(PrivateNo2);

        JLabel AesNo2 = new JLabel("AES Key");
        AesNo2.setBounds(12, 375, 85, 15);
        contentPane.add(AesNo2);

        JLabel ChatNo = new JLabel("Chat");
        ChatNo.setFont(new Font("굴림", Font.BOLD, 12));
        ChatNo.setBounds(12, 418, 50, 15);
        contentPane.add(ChatNo);

        ConnectionInfo = new JLabel("Connection : Failure");
        ConnectionInfo.setBounds(144, 95, 483, 15);
        contentPane.add(ConnectionInfo);

        ServerPublicKeyInfo = new JTextField();
        ServerPublicKeyInfo.setBackground(Color.WHITE);
        ServerPublicKeyInfo.setBounds(105, 225, 522, 15);
        contentPane.add(ServerPublicKeyInfo);

        ServerPrivateKeyInfo = new JTextField();
        ServerPrivateKeyInfo.setBackground(Color.WHITE);
        ServerPrivateKeyInfo.setBounds(105, 250, 522, 15);
        contentPane.add(ServerPrivateKeyInfo);

        ServerAESKeyInfo = new JTextField();
        ServerAESKeyInfo.setBackground(Color.WHITE);
        ServerAESKeyInfo.setBounds(105, 275, 522, 15);
        contentPane.add(ServerAESKeyInfo);

        ClientPublicKeyInfo = new JTextField();
        ClientPublicKeyInfo.setBackground(Color.WHITE);
        ClientPublicKeyInfo.setBounds(105, 325, 522, 15);
        contentPane.add(ClientPublicKeyInfo);

        ClientPrivateKeyInfo = new JTextField();
        ClientPrivateKeyInfo.setBackground(Color.WHITE);
        ClientPrivateKeyInfo.setBounds(105, 350, 522, 15);
        contentPane.add(ClientPrivateKeyInfo);

        ClientAESKeyInfo = new JTextField();
        ClientAESKeyInfo.setBackground(Color.WHITE);
        ClientAESKeyInfo.setBounds(105, 375, 522, 15);
        contentPane.add(ClientAESKeyInfo);

        ChatInfo = new JTextArea();
        ChatInfo.setBackground(new Color(255, 255, 255));
        ChatInfo.setBounds(12, 443, 269, 184);
        contentPane.add(ChatInfo);

        ChattextField = new JTextField();
        ChattextField.setBounds(86, 642, 415, 23);
        contentPane.add(ChattextField);
        ChattextField.setColumns(10);

        JLabel writeNo = new JLabel("Write msg");
        writeNo.setBounds(12, 646, 85, 15);
        contentPane.add(writeNo);

        JLabel encryptedNo = new JLabel("encrypted msg");
        encryptedNo.setBounds(12, 671, 96, 15);
        contentPane.add(encryptedNo);

        MsgEncryptInfo = new JTextField();
        MsgEncryptInfo.setBackground(Color.WHITE);
        MsgEncryptInfo.setBounds(110, 671, 507, 15);
        contentPane.add(MsgEncryptInfo);

        JLabel FileNo = new JLabel("File send");
        FileNo.setFont(new Font("굴림", Font.BOLD, 12));
        FileNo.setBounds(321, 418, 85, 15);
        contentPane.add(FileNo);

        JLabel FileNo2 = new JLabel("encryped file stream");
        FileNo2.setBounds(321, 443, 143, 15);
        contentPane.add(FileNo2);

        FileEncryptedInfo = new JTextArea();
        FileEncryptedInfo.setBackground(Color.WHITE);
        FileEncryptedInfo.setBounds(321, 462, 306, 117);
        FileEncryptedInfo.setFont(new Font("Serif", 0, 8));
        contentPane.add(FileEncryptedInfo);

        SendMsgBtn = new JButton("Send msg");
        SendMsgBtn.setBounds(513, 642, 114, 23);
        contentPane.add(SendMsgBtn);

        SendAESKeyBtn = new JButton("Make and send encrypted AES");
        SendAESKeyBtn.setBounds(177, 134, 229, 23);
        contentPane.add(SendAESKeyBtn);

        GETAESKeyBtn = new JButton("Get and decrypt AES Key");
        GETAESKeyBtn.setBounds(418, 134, 209, 23);
        contentPane.add(GETAESKeyBtn);

        SendFileBtn = new JButton("send file");
        SendFileBtn.setBounds(321, 589, 157, 23);
        contentPane.add(SendFileBtn);

        GetFileBtn = new JButton("get file");
        GetFileBtn.setBounds(482, 589, 157, 23);
        contentPane.add(GetFileBtn);
    }
}
