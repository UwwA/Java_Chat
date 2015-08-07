//TCP-server, blockerande version, Anton Gärdälv 2015-03

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Key;
import java.util.HashSet;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class ChatServer {

    //Port för servern
    private static final int PORT = 6869;

    //Hashset som sparar alla utströmmar
    private static HashSet<PrintWriter> writers = new HashSet<PrintWriter>();
    private static HashSet<DataOutputStream> clients = new HashSet<DataOutputStream>();

    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running.");
        ServerSocket listener = new ServerSocket(PORT);
        try {
            while (true) {
                //Om någon connectar ge denna en Handler(tråd)
                new Handler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }

    //Funktion som ersätter rader i en fil
    private static void updateLine(String toUpdate, String updated) throws IOException {
        BufferedReader file = new BufferedReader(new FileReader("sample.txt"));
        String line;
        String input = "";

        while ((line = file.readLine()) != null) {
            input += line + System.lineSeparator();
        }

        input = input.replace(toUpdate, updated);

        FileOutputStream os = new FileOutputStream("sample.txt");
        os.write(input.getBytes());

        file.close();
        os.close();
    }

    //Funktion som krypterar en sträng med AES
    private static byte[] encrypt(String text) throws Exception {
        String key = "Bar12345Bar12345"; // 128 bit key
        // Create key and cipher
        Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey);
        byte[] encrypted = cipher.doFinal(text.getBytes());
        System.out.println((new String(encrypted, "ISO-8859-1")));
        return encrypted;
    }
    
    //Funktion som dekrypterar en bytearray till en sträng
    private static String decrypt(byte[] decrypt) throws Exception {
        String key = "Bar12345Bar12345"; // 128 bit key
        Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, aesKey);
        System.out.println(decrypt.length);
        String decrypted = new String(cipher.doFinal(decrypt));
        return decrypted;
    }

    private static class Handler extends Thread {

        private String name;
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private int auth = 0;

        //Skapar en handler-tråd 
        public Handler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {

                //Skapar strömmar till klienten och sparar de i writers-hashsetet
                in = new DataInputStream(socket.getInputStream());
                //out = new PrintWriter(socket.getOutputStream(), true);
                out = new DataOutputStream(socket.getOutputStream());
                Random generator = new Random(System.currentTimeMillis());
                int num = generator.nextInt(10);
                Scanner sc = new Scanner(new File("sample.txt"));
                int count = 0;
                int found = 0;
                String keep = "";
                System.out.println("Searching for row number " + num);
                byte[] inmessage = {,};
                String input = "";
                byte[] message = {,};
                //Hittar OTP
                while (found == 0) {
                    while (sc.hasNext()) {
                        
                        String ignore = sc.nextLine();
                        System.out.println("num=" + num + " count=" + count);
                        //Om räknaren är samma som som det slumpmässiga numret
                        if (count == num) {
                            System.out.println("Attempting to read :" + num);
                            keep = ignore.trim();
                            //Av någon anledning lades en ﻿ till varje gång man loopa igenom filen
                            keep = keep.replaceAll("﻿", ""); 
                            System.out.println("Keep = " + keep);
                            //Om OTP'n är använd
                            if (keep.contains("used")) {
                                num = generator.nextInt(10);
                                System.out.println("Rerolling");
                            } else {
                                found = 1;
                                sc.close();
                                System.out.println("OTP " + num + "=" + keep);
                                break;
                            }

                        }
                        count++;
                    }
                    Thread.sleep(100);
                    count = 0;
                    //Stäng och skapa en ny scanner eftersom scanner inte kan återställas(till BOF)
                    sc.close();
                    sc = new Scanner(new File("sample.txt"));
                }
                num = num + 1;
                while (auth == 0) {
                    //Be användaren efter ett OTP, vänta sedan på input
                    message = encrypt("Vänligen ange otp nummer " + num + ":");
                    out.writeInt(message.length);
                    out.write(message);
                    int length = in.readInt();                    // read length of incoming message
                    if (length > 0) {
                        inmessage = new byte[length];
                        in.readFully(inmessage, 0, inmessage.length); // read the message
                        input = decrypt(inmessage);
                    }

                    System.out.println("input = '" + input + "'" + " otp = '" + keep + "'");
                    
                    if (input.equals(keep)) {
                        updateLine(keep, "used");
                        message = encrypt("Authenticated");
                        System.out.println("Authenticated user");
                        out.writeInt(message.length);
                        out.write(message);
                        auth = 1;
                    }
                    Thread.sleep(1000);
                }
                clients.add(out);

                while (true) {
                    //Läs in längden
                    int length = in.readInt();                    
                    if (length > 0) {
                        inmessage = new byte[length];
                        //Läs in meddelandet
                        in.readFully(inmessage, 0, inmessage.length); 
                    }

                    //Om något skickas, skriv ut det till alla
                    for (DataOutputStream client : clients) {

                        client.writeInt(inmessage.length);
                        client.write(inmessage);
                    }

                }
            } catch (IOException e) {
                System.out.println(e);
            } catch (InterruptedException ex) {
                Logger.getLogger(ChatServer.class.getName()).log(Level.SEVERE, null, ex);
            } catch (Exception ex) {
                Logger.getLogger(ChatServer.class.getName()).log(Level.SEVERE, null, ex);
            } finally {

                //Stäng socketen och ta bort från writers-hashsetet
                if (out != null) {
                    clients.remove(out);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }
}
