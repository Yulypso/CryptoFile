package applet;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.*;

public class TheApplet extends Applet {

    /* APDU */
    private static short DMS = 255; // DATA MAX SIZE
    private static final byte CLA = (byte) 0x37;

    /* INSTRUCTION CODES */
    private static final byte RETRIEVEFILEBYID = (byte) 0x06;
    private static final byte LISTFILES = (byte) 0x05;
    static final byte ADDFILE = (byte) 0x04;
    private static final byte DECRYPTFILE = (byte) 0x02;
    private static final byte ENCRYPTFILE = (byte) 0x01;

    /* RESPONSE STATUS */
    final static short SW_DEBUG = (short) 0x6347;

    /* P1 CODES */
    private static final byte SENDMETADATA = (byte) 0xcc;
    private static final byte SENDTRUNK = (byte) 0xca;
    private static final byte SENDLASTTRUNK = (byte) 0xfe;
    private static final byte GETFILES = (byte) 0xef;

    /* P2 CODES */

    /* BOOLEANS */
    boolean pseudoRandom, secureRandom,
            SHA1, MD5, RIPEMD160,
            keyDES, DES_ECB_NOPAD, DES_CBC_NOPAD;

    /* CARD MEMORY */
    private final static short NVRSIZE = (short) 32767; // production
    // private final static short NVRSIZE = (short) 2048; // development
    private static byte[] NVR = new byte[NVRSIZE];
    static final byte[] theDESKey = new byte[] { (byte) 0xCA, (byte) 0xCA, (byte) 0xCA, (byte) 0xCA, (byte) 0xCA,
            (byte) 0xCA, (byte) 0xCA, (byte) 0xCA };

    // cipher instances
    private Cipher cDES_ECB_NOPAD_enc, cDES_ECB_NOPAD_dec;
    private Key secretDESKey;

    protected TheApplet() {
        initKeyDES();
        initDES_ECB_NOPAD();

        this.register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) throws ISOException {
        new TheApplet();
    }

    /************************************************
     ************ BEGINNING OF METHODS **************
     *************************************************/

    private void initKeyDES() {
        try {
            secretDESKey = KeyBuilder.buildKey(KeyBuilder.TYPE_DES, KeyBuilder.LENGTH_DES, false);
            ((DESKey) secretDESKey).setKey(theDESKey, (short) 0);
            keyDES = true;
        } catch (Exception e) {
            keyDES = false;
        }
    }

    private void initDES_ECB_NOPAD() {
        if (keyDES)
            try {
                cDES_ECB_NOPAD_enc = Cipher.getInstance(Cipher.ALG_DES_ECB_NOPAD, false);
                cDES_ECB_NOPAD_dec = Cipher.getInstance(Cipher.ALG_DES_ECB_NOPAD, false);
                cDES_ECB_NOPAD_enc.init(secretDESKey, Cipher.MODE_ENCRYPT);
                cDES_ECB_NOPAD_dec.init(secretDESKey, Cipher.MODE_DECRYPT);
                DES_ECB_NOPAD = true;
            } catch (Exception e) {
                DES_ECB_NOPAD = false;
            }
    }

    private static short byteToShort(byte b) {
        return (short) (b & 0xff);
    }

    // for 2 consecutive bytes
    private static short byteArrayToShort(byte[] ba, short offset) {
        return (short) (((ba[offset] << 8)) | ((ba[(short) (offset + 1)] & 0xff)));
    }

    private static byte[] shortToByteArray(short s) {
        return new byte[] { (byte) ((s & (short) 0xff00) >> 8), (byte) (s & (short) 0x00ff) };
    }

    private static short[] getGigaFile(short offset) {
        short filenameLengthOffset; // [0]
        short filenameOffset; // [1]
        short filenameLength; // [2]
        short nbTrunksOffset; // [3]
        short nbTrunks; // [4]
        short lastTrunkLengthOffset; // [5]
        short lastTrunkLength; // [6]
        short dataOffset; // [7]
        short nextOffset; // [8]

        filenameLengthOffset = offset;
        filenameOffset = (short) (offset + 1);
        filenameLength = (short) byteToShort(NVR[filenameLengthOffset]);
        nbTrunksOffset = (short) (filenameOffset + filenameLength);
        nbTrunks = (short) byteToShort(NVR[nbTrunksOffset]);
        lastTrunkLengthOffset = (short) (nbTrunksOffset + 1);
        lastTrunkLength = (short) byteToShort(NVR[lastTrunkLengthOffset]);
        dataOffset = (short) (lastTrunkLengthOffset + 1);
        nextOffset = (short) (dataOffset + nbTrunks * DMS + lastTrunkLength);

        return new short[] { filenameLengthOffset, filenameOffset, filenameLength, nbTrunksOffset, nbTrunks,
                lastTrunkLengthOffset, lastTrunkLength, dataOffset, nextOffset };
    }

    private static short getRemainingSpace() {
        return (short) (NVRSIZE - getCurrentFile()[8]);
    }

    private static short[] getCurrentFile() { // Matches to the last current file
        short[] gigaFile = getGigaFile((short) 0);

        while (NVR[gigaFile[8]] != (byte) 0)
            gigaFile = getGigaFile(gigaFile[8]);

        return gigaFile;
    }

    private static short getNVRFreeOffset() { // Only for adding new file use
        short[] gigaFile = getGigaFile((short) 0);

        while (gigaFile[2] != (short) 0)
            gigaFile = getGigaFile(gigaFile[8]);

        return gigaFile[0];
    }

    private static short getNbFiles() { // Get total number saved files
        short cpt = 0;
        short[] gigaFile = getGigaFile((short) 0);

        if (gigaFile[2] != (short) 0) {
            ++cpt;

            while (NVR[gigaFile[8]] != (byte) 0) {
                gigaFile = getGigaFile(gigaFile[8]);
                ++cpt;
            }
        }
        return cpt;
    }

    private static short[] getFileByID(short id) { // Get file by id, first file is #ID=0
        short[] gigaFile = getGigaFile((short) 0);

        if (id == (short) 0)
            return gigaFile;
        else {
            short cpt = 0; // for id >= 1

            while (NVR[gigaFile[8]] != (byte) 0) {
                gigaFile = getGigaFile(gigaFile[8]);
                if (++cpt == id)
                    break;
            }
            return gigaFile;
        }
    }

    /******************************************
     ************ END OF METHODS **************
     *******************************************/

    private void retrieveFileByID(APDU apdu) throws ISOException {
        apdu.setIncomingAndReceive();
        byte[] buffer = apdu.getBuffer();
        short[] gigaFile;

        switch (buffer[ISO7816.OFFSET_P1]) {
            case SENDMETADATA:
                if (byteToShort(buffer[ISO7816.OFFSET_P2]) >= getNbFiles())
                    ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
                else {
                    gigaFile = getFileByID(byteToShort(buffer[ISO7816.OFFSET_P2]));
                    buffer[0] = (byte) gigaFile[4]; // nb Trunks
                    buffer[1] = (byte) gigaFile[6]; // last Trunk length
                    Util.arrayCopy(NVR, gigaFile[1], buffer, (short) 2, gigaFile[2]); // filename
                    apdu.setOutgoingAndSend((short) 0, (short) (2 + gigaFile[2]));
                }
                break;

            case SENDTRUNK:
                gigaFile = getFileByID(byteToShort(buffer[ISO7816.OFFSET_P2]));
                Util.arrayCopy(NVR, (short) (gigaFile[7] + byteToShort(buffer[5]) * DMS), buffer, (short) 0, DMS);
                apdu.setOutgoingAndSend((short) 0, DMS);
                break;

            case SENDLASTTRUNK:
                gigaFile = getFileByID(byteToShort(buffer[ISO7816.OFFSET_P2]));
                Util.arrayCopy(NVR, (short) (gigaFile[7] + gigaFile[4] * DMS), buffer, (short) 0, gigaFile[6]);
                apdu.setOutgoingAndSend((short) 0, gigaFile[6]);
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
    }

    private void listFiles(APDU apdu) throws ISOException {
        byte[] buffer = apdu.getBuffer();
        short length = 0;
        short[] gigaFile;

        switch (buffer[ISO7816.OFFSET_P1]) {
            case SENDMETADATA:
                buffer[0] = (byte) getNbFiles();
                short j = 0;
                for (short i = 0; i < byteToShort(buffer[0]); ++i, j += (short) 2) {
                    gigaFile = getFileByID(i);
                    buffer[(short) (j + 1)] = shortToByteArray((short) (gigaFile[4] * DMS + gigaFile[6]))[0];
                    buffer[(short) (j + 2)] = shortToByteArray((short) (gigaFile[4] * DMS + gigaFile[6]))[1];
                }
                apdu.setOutgoingAndSend((short) 0, (short) (j + 1));
                break;

            case GETFILES:
                short fileOffset = getFileByID(byteToShort(buffer[ISO7816.OFFSET_P2]))[0];
                Util.arrayCopy(NVR, (short) (fileOffset + 1), buffer, (short) 0, (short) NVR[fileOffset]);
                apdu.setOutgoingAndSend((short) 0, (short) NVR[fileOffset]);
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
    }

    private void addFile(APDU apdu) throws ISOException {
        apdu.setIncomingAndReceive();
        byte[] buffer = apdu.getBuffer();
        short[] gigaFile;

        switch (buffer[ISO7816.OFFSET_P1]) {
            case SENDMETADATA:
                /* check if enough space */
                short space = (short) (3 // metadata required filenameLength, nbTrunks & lastTrunkLength
                        + byteToShort(buffer[5]) // filename length
                        + byteArrayToShort(buffer, (short) (4 + byteToShort(buffer[ISO7816.OFFSET_LC]) - 1) // fileLength
                        ));
                if (space > getRemainingSpace() || space > NVRSIZE)
                    ISOException.throwIt(ISO7816.SW_FILE_FULL);
                else
                    Util.arrayCopy(buffer, ISO7816.OFFSET_CDATA, NVR, getNVRFreeOffset(),
                            (short) (byteToShort(buffer[ISO7816.OFFSET_LC]) - 2));
                break;

            case SENDTRUNK:
                gigaFile = getCurrentFile();
                Util.arrayCopy(buffer, ISO7816.OFFSET_CDATA,
                        NVR, (short) (gigaFile[7] + byteToShort(NVR[gigaFile[3]]) * DMS),
                        byteToShort(buffer[ISO7816.OFFSET_LC]));
                NVR[gigaFile[3]]++;
                break;

            case SENDLASTTRUNK:
                gigaFile = getCurrentFile();
                Util.arrayCopy(buffer, ISO7816.OFFSET_CDATA, NVR,
                        (short) (gigaFile[7] + byteToShort(NVR[gigaFile[3]]) * DMS),
                        byteToShort(buffer[ISO7816.OFFSET_LC]));
                NVR[gigaFile[5]] = buffer[ISO7816.OFFSET_LC];
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
    }

    private void cipherGeneric(APDU apdu, Cipher cipher, short keyLength) {
        apdu.setIncomingAndReceive();
        byte[] buffer = apdu.getBuffer();
        short length = (short) byteToShort(buffer[4]);
        cipher.doFinal(buffer, (short) 5, (short) length, buffer, (short) 0);
        apdu.setOutgoingAndSend((short) 0, length);
    }

    public void process(APDU apdu) throws ISOException {
        if (selectingApplet() == true)
            return;

        byte[] buffer = apdu.getBuffer();

        if (buffer[ISO7816.OFFSET_CLA] != CLA)
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);

        switch (buffer[ISO7816.OFFSET_INS]) {
            case RETRIEVEFILEBYID:
                retrieveFileByID(apdu);
                break;

            case LISTFILES:
                listFiles(apdu);
                break;

            case ADDFILE:
                addFile(apdu);
                break;

            case DECRYPTFILE:
                cipherGeneric(apdu, cDES_ECB_NOPAD_dec, KeyBuilder.LENGTH_DES);
                break;

            case ENCRYPTFILE:
                cipherGeneric(apdu, cDES_ECB_NOPAD_enc, KeyBuilder.LENGTH_DES);
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}