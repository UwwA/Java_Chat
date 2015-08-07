

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class ChatClient {

	//Initialiserar variabler
	BufferedReader in;
	DataOutputStream out;
	JFrame frame = new JFrame("Multiklients-chatt");
	JTextField textField = new JTextField(40);
	int timeZone;
	JTextArea messageArea = new JTextArea(8, 40);
	int auth = 0;

	public ChatClient() throws Exception {

		//Skapar gui
		messageArea.setEditable(false);
		frame.getContentPane().add(textField, "South");
		frame.getContentPane().add(new JScrollPane(messageArea), "Center");
		frame.pack();
		String name = getName();
		//Skapa en listener till textfältet
		textField.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//Om användaren vill ändra tidzon
				if (textField.getText().startsWith("/time")) {
					String zone = getTime();
					timeZone = Integer.parseInt(zone);
				} //Annars skicka meddelande, om användaren är autentiserad
				else if (auth == 1) {
					try {
						byte[] message = encrypt(name + ": " + textField.getText());
						out.writeInt(message.length);
						out.write(message);
						textField.setText("");
					} catch (Exception ex) {
						Logger.getLogger(ChatClient.class.getName()).log(Level.SEVERE, null, ex);
					}

				} //Om användaren inte är autentiserad md5a texten
				else if (auth == 0) {
					try {

						String send = md5(textField.getText());
						byte[] message = encrypt(send);
						out.writeInt(message.length);
						out.write(message);
						textField.setText("");
					} catch (Exception ex) {
						Logger.getLogger(ChatClient.class.getName()).log(Level.SEVERE, null, ex);
					}
				}
			}
		});
	}
	
	//Funktion som krypterar en sträng med AES
	private static byte[] encrypt(String text) throws Exception {
		String key = "Bar12345Bar12345"; // 128 bit key

		
		Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.ENCRYPT_MODE, aesKey);
		byte[] encrypted = cipher.doFinal(text.getBytes());
		return encrypted;
	}

	//Funktion som frågar användaren efter tidzon
	private String getTime() {
		return JOptionPane.showInputDialog(
		frame,
		"Ange din tidszon:",
		"Tidsval",
		JOptionPane.PLAIN_MESSAGE);
	}

	//Funktion som frågar avändaren efter namn
	private String getName() {
		return JOptionPane.showInputDialog(
		frame,
		"Välj ditt namn:",
		"Screen name selection",
		JOptionPane.PLAIN_MESSAGE);
	}

	//Funktion som hashar en sträng med md5 och konverterar till hex
	private String md5(String encode) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
			md.update(encode.getBytes());
		} catch (NoSuchAlgorithmException ex) {
			Logger.getLogger(ChatClient.class.getName()).log(Level.SEVERE, null, ex);
		}

		byte byteData[] = md.digest();
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < byteData.length; i++) {
			sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
		}
		return sb.toString();
	}
	
	//Funktion som dekrypterar en bytearray till en sträng
	private static String decrypt(byte[] decrypt) throws Exception {
		String key = "Bar12345Bar12345"; // 128 bit key
		Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
		Cipher cipher = Cipher.getInstance("AES");
		
		cipher.init(Cipher.DECRYPT_MODE, aesKey);
		String decrypted = new String(cipher.doFinal(decrypt));
		return decrypted;

	}

	private void run() throws IOException, InterruptedException, ConnectException, NoSuchAlgorithmException, Exception {

		//Inidtialiserar sockets och strömmar
		Socket socket = new Socket("localhost", 6869);
		
		DataInputStream dIn = new DataInputStream(socket.getInputStream());
		out = new DataOutputStream(socket.getOutputStream());
		//Skapar en SDF för tidsutskrift
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		messageArea.append("Skriv /time för att ändra tidzon\n");
		String line = "";
		int length = 0;
		//Läser in meddelanden och skriver ut dessa
		while (true) {
			textField.setEditable(true);
			Calendar cal = Calendar.getInstance();
			//Ändra tiden till användarens angivna tidzon
			cal.add(Calendar.HOUR, timeZone);
			cal.getTime();

			try {
				//Läs in text
				try {
					length = dIn.readInt();                    
				} catch (EOFException x) {
					x.printStackTrace(System.out);
				}

				if (length > 0) {
					byte[] message = new byte[length];
					dIn.readFully(message, 0, message.length); // read the message
					String finished = decrypt(message);
					line = finished;
				}

				if (line != null) {
					if (line.equals("Authenticated")) {
						auth = 1;
					}
				}

				//Skriv ut text
				messageArea.append(sdf.format(cal.getTime()) + " " + line + "\n");
			} catch (IOException e) {
				e.printStackTrace(System.out);
				//Om användaren tappat anslutningen
				textField.setEditable(false);
				messageArea.append(sdf.format(cal.getTime()) + " Disconnected. Attempting to reconnect... \n");
				Thread.sleep(5000);
				try {
					//Försök återansluta
					socket = new Socket("localhost", 6869);
					messageArea.append(sdf.format(cal.getTime()) + " Reconnect succesful! \n");
					dIn = new DataInputStream(socket.getInputStream());
					out = new DataOutputStream(socket.getOutputStream());
				} catch (ConnectException ce) {
					System.out.println(ce);
				}

			}

		}
	}

	//Kör klienten
	public static void main(String[] args) throws Exception {
		ChatClient client = new ChatClient();
		client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		client.frame.setVisible(true);
		client.run();
	}
}
