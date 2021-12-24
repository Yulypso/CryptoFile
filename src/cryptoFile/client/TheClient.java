package client;

import java.util.*;
import java.io.*;
import opencard.core.service.*;
import opencard.core.terminal.*;
import opencard.core.util.*;
import opencard.opt.util.*;

public class TheClient {

	private PassThruCardService servClient = null;

	/* APDU */
	private static short DMS = 255; // DATA MAX SIZE
	private static short DMS_DES = 8; // DATA MAX SIZE for DES
	private static final byte CLA = (byte) 0x37;
	private static final byte P1 = (byte) 0x00;
	private static final byte P2 = (byte) 0x00;

	/* INSTRUCTION CODES */
	private static final byte RETRIEVEFILEBYID = (byte) 0x06;
	private static final byte LISTFILES = (byte) 0x05;
	private static final byte ADDFILE = (byte) 0x04;
	private static final byte COMPAREFILES = (byte) 0x03;
	private static final byte DECRYPTFILE = (byte) 0x02;
	private static final byte ENCRYPTFILE = (byte) 0x01;

	/* RESPONSE STATUS */

	/* P1 CODES */
	private static final byte SENDMETADATA = (byte) 0xcc;
	private static final byte SENDTRUNK = (byte) 0xca;
	private static final byte SENDLASTTRUNK = (byte) 0xfe;
	private static final byte GETFILES = (byte) 0xef;

	/* P2 CODES */

	/* BOOLEANS */
	private boolean DISPLAY = true;
	private boolean loop = true;

	public TheClient() {
		try {
			SmartCard.start();
			System.out.print("Smartcard inserted?... ");

			CardRequest cr = new CardRequest(CardRequest.ANYCARD, null, null);

			SmartCard sm = SmartCard.waitForCard(cr);

			if (sm != null) {
				System.out.println("got a SmartCard object!\n");
			} else
				System.out.println("did not get a SmartCard object!\n");

			this.initNewCard(sm);

			SmartCard.shutdown();

		} catch (Exception e) {
			System.out.println("TheClient error: " + e.getMessage());
		}
		java.lang.System.exit(0);
	}

	private ResponseAPDU sendAPDU(CommandAPDU cmd) {
		return sendAPDU(cmd, true);
	}

	private ResponseAPDU sendAPDU(CommandAPDU cmd, boolean display) {
		ResponseAPDU result = null;
		try {
			result = this.servClient.sendCommandAPDU(cmd);
			if (display)
				displayAPDU(cmd, result);
		} catch (Exception e) {
			System.out.println("Exception caught in sendAPDU: " + e.getMessage());
			java.lang.System.exit(-1);
		}
		return result;
	}

	/************************************************
	 ************ BEGINNING OF TOOLS ****************
	 ************************************************/

	private String apdu2string(APDU apdu) {
		return removeCR(HexString.hexify(apdu.getBytes()));
	}

	public void displayAPDU(APDU apdu) {
		System.out.println(removeCR(HexString.hexify(apdu.getBytes())) + "\n");
	}

	public void displayAPDU(CommandAPDU termCmd, ResponseAPDU cardResp) {
		System.out.println("--> Term: " + removeCR(HexString.hexify(termCmd.getBytes())));
		System.out.println("<-- Card: " + removeCR(HexString.hexify(cardResp.getBytes())));
	}

	private String removeCR(String string) {
		return string.replace('\n', ' ');
	}

	/******************************************
	 ************ END OF TOOLS ****************
	 ******************************************/

	private boolean selectApplet() {
		boolean cardOk = false;
		try {
			CommandAPDU cmd = new CommandAPDU(new byte[] {
					(byte) 0x00, (byte) 0xA4, (byte) 0x04, (byte) 0x00, (byte) 0x0A,
					(byte) 0xA0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x62,
					(byte) 0x03, (byte) 0x01, (byte) 0x0C, (byte) 0x06, (byte) 0x01
			});
			ResponseAPDU resp = this.sendAPDU(cmd);
			if (this.apdu2string(resp).equals("90 00"))
				cardOk = true;
		} catch (Exception e) {
			System.out.println("Exception caught in selectApplet: " + e.getMessage());
			java.lang.System.exit(-1);
		}
		return cardOk;
	}

	private void initNewCard(SmartCard card) {
		if (card != null)
			System.out.println("Smartcard inserted\n");
		else {
			System.out.println("Did not get a smartcard");
			System.exit(-1);
		}

		System.out.println("ATR: " + HexString.hexify(card.getCardID().getATR()) + "\n");

		try {
			this.servClient = (PassThruCardService) card.getCardService(PassThruCardService.class, true);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

		System.out.println("Applet selecting...");
		if (!this.selectApplet()) {
			System.out.println("Wrong card, no applet to select!\n");
			System.exit(1);
			return;
		} else
			System.out.println("Applet selected\n");

		mainLoop();
	}

	/************************************************
	 ************ BEGINNING OF METHODS **************
	 *************************************************/

	private String readKeyboard() {
		String result = null;

		try {
			BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
			result = input.readLine();
		} catch (Exception e) {
		}
		return result;
	}

	private int readMenuChoice() {
		int result = -1;

		try {
			String choice = readKeyboard();
			result = Integer.parseInt(choice.trim());
		} catch (Exception e) {
		}
		System.out.println("");
		return result;
	}

	private static boolean getExceptionMessage(String msg, String respCode) {
		boolean isClear = false;
		if (respCode.equals("90 00")) {
			System.out.println(msg + ": [SW_NO_ERROR] No Error\n");
			isClear = true;
		} else if (respCode.equals("63 47")) {
			System.out.println(msg + ": [DEBUG] Debug\n");
		} else if (respCode.equals("69 99")) {
			System.out.println(msg + ": [SW_APPLET_SELECT_FAILED] Applet selection failed\n");
		} else if (respCode.equals("61 00")) {
			System.out.println(msg + ": [SW_BYTES_REMAINING_00] Response bytes remaining\n");
		} else if (respCode.equals("6E 00")) {
			System.out.println(msg + ": [SW_CLA_NOT_SUPPORTED] CLA value not supported\n");
		} else if (respCode.equals("68 84")) {
			System.out.println(msg + ": [SW_COMMAND_CHAINING_NOT_SUPPORTED] Command chaining not supported\n");
		} else if (respCode.equals("69 86")) {
			System.out.println(msg + ": [SW_COMMAND_NOT_ALLOWED] Command not allowed (no current EF)\n");
		} else if (respCode.equals("69 85")) {
			System.out.println(msg + ": [SW_CONDITIONS_NOT_SATISFIED] Conditions of use not satisfied\n");
		} else if (respCode.equals("6C 00")) {
			System.out.println(msg + ": [SW_CORRECT_LENGTH_00] Correct Expected Length (Le)\n");
		} else if (respCode.equals("69 84")) {
			System.out.println(msg + ": [SW_DATA_INVALID] Data invalid\n");
		} else if (respCode.equals("6A 84")) {
			System.out.println(msg + ": [SW_FILE_FULL] Not enough memory space in the file\n");
		} else if (respCode.equals("69 83")) {
			System.out.println(msg + ": [SW_FILE_INVALID] File invalid\n");
		} else if (respCode.equals("6A 82")) {
			System.out.println(msg + ": [SW_FILE_NOT_FOUND] File not found\n");
		} else if (respCode.equals("6A 81")) {
			System.out.println(msg + ": [SW_FUNC_NOT_SUPPORTED] Function not supported\n");
		} else if (respCode.equals("6A 86")) {
			System.out.println(msg + ": [SW_INCORRECT_P1P2] Incorrect parameters (P1,P2)\n");
		} else if (respCode.equals("6D 00")) {
			System.out.println(msg + ": [SW_INS_NOT_SUPPORTED] INS value not supported\n");
		} else if (respCode.equals("68 83")) {
			System.out.println(msg + ": [SW_LAST_COMMAND_EXPECTED] Last command in chain expected\n");
		} else if (respCode.equals("68 81")) {
			System.out.println(msg
					+ ": [SW_LOGICAL_CHANNEL_NOT_SUPPORTED] Card does not support the operation on the specified logical channel\n");
		} else if (respCode.equals("6A 83")) {
			System.out.println(msg + ": [SW_RECORD_NOT_FOUND] Record not found\n");
		} else if (respCode.equals("68 82")) {
			System.out.println(msg + ": [SW_SECURE_MESSAGING_NOT_SUPPORTED] Card does not support secure messaging\n");
		} else if (respCode.equals("69 82")) {
			System.out.println(msg + ": [SW_SECURITY_STATUS_NOT_SATISFIED] Security condition not satisfied\n");
		} else if (respCode.equals("6F 00")) {
			System.out.println(msg + ": [SW_UNKNOWN] No precise diagnosis\n");
		} else if (respCode.equals("62 00")) {
			System.out.println(msg + ": [SW_WARNING_STATE_UNCHANGED] Warning, card state unchanged\n");
		} else if (respCode.equals("6A 80")) {
			System.out.println(msg + ": [SW_WRONG_DATA] Wrong data\n");
		} else if (respCode.equals("67 00")) {
			System.out.println(msg + ": [SW_WRONG_LENGTH] Wrong length\n");
		} else if (respCode.equals("6B 00")) {
			System.out.println(msg + ": [SW_WRONG_P1P2] Incorrect parameters (P1,P2)\n");
		} else {
			System.out.println(msg + ": Undefined Error code\n");
		}
		return isClear;
	}

	private static short byteToShort(byte b) {
		return (short) (b & 0xff);
	}

	private static short byteArrayToShort(byte[] ba, short offset) {
		return (short) (((ba[offset] << 8)) | ((ba[(short) (offset + 1)] & 0xff)));
	}

	private static byte[] shortToByteArray(short s) {
		return new byte[] { (byte) ((s & (short) 0xff00) >> 8), (byte) (s & (short) 0x00ff) };
	}

	private static byte[] addPadding(byte[] data, long fileLength) {
		short paddingSize = (short) (DMS_DES - (fileLength % 8));
		byte[] paddingData = new byte[DMS_DES];

		System.arraycopy(data, 0, paddingData, 0, (short) data.length);
		for (short i = (short) data.length; i < DMS_DES; ++i)
			paddingData[i] = shortToByteArray(paddingSize)[1];

		return paddingData;
	}

	private static byte[] removePadding(byte[] paddingData) {
		short paddingSize = byteToShort(paddingData[paddingData.length - 1]);
		if (paddingSize > 8)
			return paddingData;

		/* check if padding exists */
		for (short i = (short) (paddingData.length - paddingSize); i < paddingData.length; ++i)
			if (paddingData[i] != (byte) paddingSize)
				return paddingData;

		/* Remove padding */
		short dataLength = (short) (paddingData.length - paddingSize);
		byte[] data = new byte[dataLength];
		System.arraycopy(paddingData, 0, data, 0, (short) dataLength);

		return data;
	}

	/******************************************
	 ************ END OF METHODS **************
	 *******************************************/

	private void retrieveFileByID() {
		System.out.println("RETRIEVEFILEBYID: Please enter file [#ID] (without # symbol)\n");

		CommandAPDU cmd;
		ResponseAPDU resp;

		short fileID = Short.valueOf(readKeyboard().trim());

		byte[] payload = new byte[5];
		payload[0] = CLA;
		payload[1] = RETRIEVEFILEBYID;
		payload[2] = SENDMETADATA;
		payload[3] = (byte) fileID;
		payload[4] = (byte) 0;

		cmd = new CommandAPDU(payload);
		displayAPDU(cmd);
		resp = this.sendAPDU(cmd, DISPLAY);

		if (getExceptionMessage("RETRIEVEFILEBYID (METADATA)",
				this.apdu2string(resp).trim().substring(this.apdu2string(resp).trim().length() - 5))) {

			byte[] bytes = resp.getBytes();
			short nbTrunks = byteToShort(bytes[0]);
			short lastTrunkLength = byteToShort(bytes[1]);
			String filename = "";

			for (short i = 2; i < bytes.length - 2; ++i)
				filename += new StringBuffer("").append((char) bytes[i]);

			System.out.println("[filename: " + filename + ", nbTrunks: " + nbTrunks + ", lastTrunkLength: "
					+ lastTrunkLength + "]\n");

			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				DataOutputStream dos = new DataOutputStream(baos);
				FileOutputStream fout = new FileOutputStream(fileID + "-retrieved_" + filename);

				String msg = "(DATA TRUNK)";
				for (short i = 0; i <= nbTrunks; ++i) {
					if (i < nbTrunks) { // Trunks
						payload = new byte[7];
						payload[0] = CLA;
						payload[1] = RETRIEVEFILEBYID;
						payload[2] = SENDTRUNK;
						payload[3] = (byte) fileID;
						payload[4] = (byte) 1;
						payload[5] = (byte) i;
						payload[payload.length - 1] = (byte) DMS;

					} else if (i == nbTrunks) { // Last Trunk
						payload = new byte[5];
						payload[0] = CLA;
						payload[1] = RETRIEVEFILEBYID;
						payload[2] = SENDLASTTRUNK;
						payload[3] = (byte) fileID;
						payload[4] = (byte) 0;
						msg = "(LAST DATA TRUNK)";
					}

					cmd = new CommandAPDU(payload);
					resp = this.sendAPDU(cmd, DISPLAY);

					if (getExceptionMessage("RETRIEVEFILEBYID " + msg + " " + i,
							this.apdu2string(resp).trim().substring(this.apdu2string(resp).trim().length() - 5))) {
						bytes = resp.getBytes();

						byte[] data = new byte[bytes.length - 2];
						System.arraycopy(bytes, 0, data, 0, bytes.length - 2);

						stream.write(data);
					}
				}
				dos.write(stream.toByteArray());

				try {
					baos.writeTo(fout);
					stream.flush();
					fout.flush();
				} finally {
					fout.close();
					System.out.println("------------------------------");
					System.out.println("[+] Output: " + fileID + "-retrieved_" + filename);
					System.out.println("------------------------------");
				}
			} catch (IOException oe) {
				System.out.println("[IOException] " + oe.getMessage());
			}
		}
	}

	private void listFiles() {
		System.out.println("LISTFILES\n");

		CommandAPDU cmd;
		ResponseAPDU resp;

		byte[] payload = new byte[5];
		payload[0] = CLA;
		payload[1] = LISTFILES;
		payload[2] = SENDMETADATA;
		payload[3] = P2;
		payload[4] = (byte) 0;

		cmd = new CommandAPDU(payload);
		resp = this.sendAPDU(cmd, DISPLAY);

		if (getExceptionMessage("LISTFILES",
				this.apdu2string(resp).trim().substring(this.apdu2string(resp).trim().length() - 5))) {
			byte[] bytes = resp.getBytes();
			short nbFiles = (short) bytes[0];

			if (nbFiles < 1) {
				System.out.println("------------------------------");
				System.out.println("> 0 file stored inside the card.");
				System.out.println("------------------------------");
			} else {
				List<Short> fileIDList = new ArrayList<Short>();
				List<Short> fileLengthList = new ArrayList<Short>();
				for (short i = 0, j = 1; i < nbFiles; ++i, j += 2) {
					fileLengthList.add(byteArrayToShort(bytes, j));
					fileIDList.add(i);
				}

				List<String> fileList = new ArrayList<String>();
				for (short i = 0; i < nbFiles; ++i) {
					payload = new byte[5];
					payload[0] = CLA;
					payload[1] = LISTFILES;
					payload[2] = GETFILES;
					payload[3] = (byte) i;
					payload[4] = (byte) 0;

					cmd = new CommandAPDU(payload);
					displayAPDU(cmd);
					resp = this.sendAPDU(cmd, DISPLAY);

					if (getExceptionMessage("LISTFILES [" + i + "]",
							this.apdu2string(resp).trim().substring(this.apdu2string(resp).trim().length() - 5))) {
						String filename = "";
						for (short j = 0; j < resp.getBytes().length - 2; j++)
							filename += new StringBuffer("").append((char) resp.getBytes()[j]);
						fileList.add(filename);
					}
				}

				Short[] fileIDArray = new Short[fileIDList.size()];
				fileIDArray = fileIDList.toArray(fileIDArray);

				Short[] fileLengthArray = new Short[fileLengthList.size()];
				fileLengthArray = fileLengthList.toArray(fileLengthArray);

				String[] fileArray = new String[fileList.size()];
				fileArray = fileList.toArray(fileArray);

				for (short i = 1; i < fileLengthArray.length; ++i) {
					boolean flag = true;

					for (short j = 0; j < fileLengthArray.length - i; ++j) {
						if (fileLengthArray[j] > fileLengthArray[j + 1]) {
							short tmp = fileLengthArray[j];
							fileLengthArray[j] = fileLengthArray[j + 1];
							fileLengthArray[j + 1] = tmp;

							tmp = fileIDArray[j];
							fileIDArray[j] = fileIDArray[j + 1];
							fileIDArray[j + 1] = tmp;

							String tmpS = fileArray[j];
							fileArray[j] = fileArray[j + 1];
							fileArray[j + 1] = tmpS;

							flag = false;
						}
					}
					if (flag)
						break;
				}

				fileLengthList = Arrays.asList(fileLengthArray);
				fileList = Arrays.asList(fileArray);
				fileIDList = Arrays.asList(fileIDArray);

				System.out.println("------------------------------");
				System.out.println(
						nbFiles > 1 ? ("> " + nbFiles + " files stored inside the card: [#ID]")
								: ("> " + nbFiles + " file stored inside the card: [#ID]"));
				for (short i = 0; i < fileList.size(); ++i)
					System.out.println(" [#" + fileIDList.get(i) + "] " + fileList.get(i) + " ("
							+ (fileLengthList.get(i) > 1 ? (fileLengthList.get(i) + " bytes)")
									: (fileLengthList.get(i) + " byte)")));
				System.out.println("------------------------------");
			}
		}
	}

	private void addFile() {
		CommandAPDU cmd;
		ResponseAPDU resp;

		System.out.println("ADDFILE: Please enter file name.");
		String filename = readKeyboard();
		File f = new File(filename.trim());
		System.out.println("");

		if (!f.exists()) {
			System.err.println("[Error]: File doesn't exist");
			return;
		}

		/* metadata */
		byte filenameLength = (byte) filename.length(); // 1 byte
		byte[] filename_b = filename.getBytes(); // n bytes
		short fileLength = (short) f.length(); // 2 bytes

		if (filenameLength < 0) {
			System.err.println("[Error]: Filename is too large (size must be <= 127)");
			return;
		}

		if (fileLength < 0) {
			System.err.println("[Error]: File is too large (size must be <= 32767)");
			return;
		}

		short LC = (short) (filenameLength + 3);
		byte[] payload = new byte[LC + 5];
		payload[0] = CLA;
		payload[1] = ADDFILE;
		payload[2] = SENDMETADATA;
		payload[3] = P2;
		payload[4] = (byte) LC;
		payload[5] = filenameLength; // filename length
		System.arraycopy(filename_b, 0, payload, 6, filenameLength); // filename
		payload[payload.length - 2] = shortToByteArray(fileLength)[0]; // file length
		payload[payload.length - 1] = shortToByteArray(fileLength)[1]; // file length

		cmd = new CommandAPDU(payload);
		resp = this.sendAPDU(cmd, DISPLAY);

		System.out.println("File data length: " + byteArrayToShort(payload, (short) (payload.length - 2)));

		if (getExceptionMessage("ADDFILE (METADATA)",
				this.apdu2string(resp).trim().substring(this.apdu2string(resp).trim().length() - 5))) {

			try {
				FileInputStream fin = new FileInputStream(f);
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				int by = 0, i = 0, cpt = 0;

				while (by != -1) {
					by = fin.read();
					outputStream.write(by);
					++i;

					if (by != -1 && i == DMS) {
						byte data[] = outputStream.toByteArray();
						outputStream = new ByteArrayOutputStream();
						i = 0;
						System.out.print("\n" + "Trunk #" + cpt++ + " [length: " + data.length + "]");
						System.out.println("");

						LC = (short) data.length;
						payload = new byte[LC + 5];
						payload[0] = CLA;
						payload[1] = ADDFILE;
						payload[2] = SENDTRUNK;
						payload[3] = P2;
						payload[4] = (byte) LC;
						System.arraycopy(data, 0, payload, 5, LC);

						cmd = new CommandAPDU(payload);
						resp = this.sendAPDU(cmd, DISPLAY);

						if (!getExceptionMessage("ADDFILE (DATA TRUNK) " + (cpt - 1),
								this.apdu2string(resp).trim().substring(this.apdu2string(resp).trim().length() - 5)))
							break;

					} else if (by == -1 && i > 1) {
						byte data[] = outputStream.toByteArray();
						outputStream = new ByteArrayOutputStream();
						System.out.print("\n" + "Trunk #" + cpt++ + " [length: " + (data.length - 1) + "]\n");

						LC = (short) (data.length - 1);
						payload = new byte[LC + 5];
						payload[0] = CLA;
						payload[1] = ADDFILE;
						payload[2] = SENDLASTTRUNK;
						payload[3] = P2;
						payload[4] = (byte) LC;
						System.arraycopy(data, 0, payload, 5, LC);

						cmd = new CommandAPDU(payload);
						resp = this.sendAPDU(cmd, DISPLAY);

						if (!getExceptionMessage("ADDFILE (LAST DATA TRUNK) " + (cpt - 1),
								this.apdu2string(resp).trim().substring(this.apdu2string(resp).trim().length() - 5)))
							break;
					}
				}
				fin.close();
				System.out.println("------------------------------");
				System.out.println("[+] Stored: " + filename);
				System.out.println("------------------------------");
			} catch (IOException oe) {
				System.err.println("[IOException exception] " + oe.getMessage());
			}
		}
	}

	private void compareFiles() {
		System.out.println("COMPAREFILES: Please enter first file name.");
		String filename1 = readKeyboard();
		File f1 = new File(filename1.trim());
		System.out.println("");

		if (!f1.exists()) {
			System.err.println("[Error]: First file doesn't exist");
			return;
		}

		System.out.println("COMPAREFILES: Please enter second file name.");
		String filename2 = readKeyboard();
		File f2 = new File(filename2.trim());
		System.out.println("");

		if (!f2.exists()) {
			System.err.println("[Error]: Second file doesn't exist");
			return;
		}

		if (f1.length() != f2.length()) {
			System.out.println("------------------------------------------------------------");
			System.out.println("[x] " + filename1 + " != " + filename2 + ": Distinct file size");
			System.out.println("------------------------------------------------------------");
			return;
		}

		try {
			FileInputStream fin1 = new FileInputStream(f1);
			FileInputStream fin2 = new FileInputStream(f2);

			for (int i = 0; i < f1.length(); ++i) {
				if (fin1.read() != fin2.read()) {
					System.out.println("------------------------------------------------------------");
					System.out.println("[x] " + filename1 + " != " + filename2 + ": Distinct file content");
					System.out.println("------------------------------------------------------------");
					return;
				}
			}
		} catch (IOException oe) {
			System.err.println("[IOException exception] " + oe.getMessage());
		}

		System.out.println("------------------------------------------------------------");
		System.out.println("[+] " + filename1 + " == " + filename2 + ": Same file content");
		System.out.println("------------------------------------------------------------");
	}

	private void decryptFile() {
		CommandAPDU cmd;
		ResponseAPDU resp;

		System.out.println("DECRYPTFILE: Please enter file name to decrypt.");
		String filename = readKeyboard();
		File f = new File(filename.trim());
		System.out.println("");

		if (!f.exists()) {
			System.err.println("[Error]: File doesn't exist");
			return;
		}

		try {
			FileInputStream fin = new FileInputStream(f);
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

			ByteArrayOutputStream stream = new ByteArrayOutputStream(); // sortir
			FileOutputStream fout = new FileOutputStream("decrypted_" + filename);
			int by = 0, i = 0, cpt = 1;

			while (by != -1) {
				by = fin.read();
				if (by != -1)
					outputStream.write(by);
				++i;

				if (by != -1 && i == DMS_DES) {
					byte data[] = outputStream.toByteArray();
					outputStream = new ByteArrayOutputStream();
					i = 0;

					System.out.print("\n" + "Trunk #" + cpt + " [length: " + data.length + "]");
					System.out.println("");

					byte[] payload = new byte[DMS_DES + 6];
					payload[0] = CLA;
					payload[1] = DECRYPTFILE;
					payload[2] = P1;
					payload[3] = P2;
					payload[4] = (byte) DMS_DES;
					System.arraycopy(data, 0, payload, 5, DMS_DES);
					payload[payload.length - 1] = (byte) DMS_DES;

					cmd = new CommandAPDU(payload);
					resp = this.sendAPDU(cmd, DISPLAY);

					if (getExceptionMessage("DECRYPTFILE (DATA TRUNK) " + (cpt),
							this.apdu2string(resp).trim().substring(this.apdu2string(resp).trim().length() - 5))) {

						byte[] bytes = resp.getBytes();
						try {
							DataOutputStream dos = new DataOutputStream(stream);
							data = new byte[bytes.length - 2];
							System.arraycopy(bytes, 0, data, 0, bytes.length - 2);
							if (cpt++ * DMS_DES == f.length())
								data = removePadding(data);
							dos.write(data);

						} catch (IOException oe) {
							System.out.println("[IOException] " + oe.getMessage());
						}
					}
				}
			}
			try {
				stream.writeTo(fout);
				stream.flush();
				fout.flush();
			} finally {
				fout.close();
			}
			fin.close();
			System.out.println("------------------------------");
			System.out.println("[+] Output: " + "decrypted_" + filename);
			System.out.println("------------------------------");
		} catch (IOException oe) {
			System.err.println("[IOException exception] " + oe.getMessage());
		}
	}

	private void encryptFile() {
		CommandAPDU cmd;
		ResponseAPDU resp;

		System.out.println("ENCRYPTFILE: Please enter file name to encrypt.");
		String filename = readKeyboard();
		File f = new File(filename.trim());
		System.out.println("");

		if (!f.exists()) {
			System.err.println("[Error]: File doesn't exist");
			return;
		}

		try {
			FileInputStream fin = new FileInputStream(f);
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

			ByteArrayOutputStream stream = new ByteArrayOutputStream(); // sortir
			FileOutputStream fout = new FileOutputStream("encrypted_" + filename);
			int by = 0, i = 0, cpt = 0;

			while (by != -1) {
				by = fin.read();
				if (by != -1)
					outputStream.write(by);
				++i;

				if ((by != -1 && i == DMS_DES) || (by == -1 && i > 1)) {
					byte data[] = outputStream.toByteArray();
					outputStream = new ByteArrayOutputStream();
					i = 0;

					data = addPadding(data, f.length());
					System.out.print("\n" + "Trunk #" + cpt++ + " [length: " + data.length + "]");
					System.out.println("");

					byte[] payload = new byte[DMS_DES + 6];
					payload[0] = CLA;
					payload[1] = ENCRYPTFILE;
					payload[2] = P1;
					payload[3] = P2;
					payload[4] = (byte) DMS_DES;
					System.arraycopy(data, 0, payload, 5, DMS_DES);
					payload[payload.length - 1] = (byte) DMS_DES;

					cmd = new CommandAPDU(payload);
					resp = this.sendAPDU(cmd, DISPLAY);

					if (getExceptionMessage("ENCRYPTFILE (DATA TRUNK) " + (cpt - 1),
							this.apdu2string(resp).trim().substring(this.apdu2string(resp).trim().length() - 5))) {

						byte[] bytes = resp.getBytes();
						try {
							DataOutputStream dos = new DataOutputStream(stream);
							data = new byte[bytes.length - 2];
							System.arraycopy(bytes, 0, data, 0, bytes.length - 2);
							dos.write(data);

						} catch (IOException oe) {
							System.out.println("[IOException] " + oe.getMessage());
						}
					}
				}
			}
			try {
				stream.writeTo(fout);
				stream.flush();
				fout.flush();
			} finally {
				fout.close();
			}
			fin.close();
			System.out.println("------------------------------");
			System.out.println("[+] Output: " + "encrypted_" + filename);
			System.out.println("------------------------------");
		} catch (IOException oe) {
			System.err.println("[IOException exception] " + oe.getMessage());
		}
	}

	private void exit() {
		System.out.println("EXIT: See you soon! :)");
		loop = false;
	}

	private void runAction(int choice) {
		switch (choice) {
			case 6:
				retrieveFileByID();
				break;
			case 5:
				listFiles();
				break;
			case 4:
				addFile();
				break;
			case 3:
				compareFiles();
				break;
			case 2:
				decryptFile();
				break;
			case 1:
				encryptFile();
				break;
			case 0:
				exit();
				break;
			default:
				System.out.println("Unknown choice!");
		}
	}

	private void printMenu() {
		System.out.println("\n------------------------------------------------------------");
		System.out.println("  Welcome on board! #Thierry Khamphousone MS-SIS 2021/2022  ");
		System.out.println("------------------------------------------------------------");
		System.out.println("6: Retrieve a file from the card by [#ID]");
		System.out.println("5: List files stored inside the card (#sorted by file size)");
		System.out.println("4: Upload a file to the card");
		System.out.println("3: Compare 2 files");
		System.out.println("2: Decrypt a file");
		System.out.println("1: Encrypt a file");
		System.out.println("0: Exit");
		System.out.print("--> ");
	}

	private void mainLoop() {
		while (loop) {
			printMenu();
			int choice = readMenuChoice();
			runAction(choice);
		}
	}

	public static void main(String[] args) throws InterruptedException {
		new TheClient();
	}
}
