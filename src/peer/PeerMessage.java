package peer;

import java.io.*;

public class PeerMessage {

    public static final int CHOKE        = 0;
    public static final int UNCHOKE      = 1;
    public static final int INTERESTED   = 2;
    public static final int UNINTERESTED = 3;
    public static final int HAVE         = 4;
    public static final int BITFIELD     = 5;
    public static final int REQUEST      = 6;
    public static final int PIECE        = 7;
    public static final int CANCEL       = 8;

    private final int type;
    private final byte[] payload;

    public PeerMessage(int type, byte[] payload) {
        this.type = type;
        this.payload = payload;
    }

    public int getType() { return type; }
    public byte[] getPayload() { return payload; }

    public static PeerMessage read(DataInputStream in) throws Exception {
        int length = in.readInt();
        if (length == 0) return new PeerMessage(-1, new byte[0]);
        int type = in.readByte() & 0xFF;
        byte[] payload = new byte[length - 1];
        if (payload.length > 0) in.readFully(payload);
        return new PeerMessage(type, payload);
    }

    public static void send(DataOutputStream out, int type) throws Exception {
        out.writeInt(1);
        out.writeByte(type);
        out.flush();
    }

    public static void send(DataOutputStream out, int type,
            byte[] payload) throws Exception {
        out.writeInt(1 + payload.length);
        out.writeByte(type);
        out.write(payload);
        out.flush();
    }

    public static byte[] buildBitfield(boolean[] pieces) {
        int numBytes = (int) Math.ceil(pieces.length / 8.0);
        byte[] bitfield = new byte[numBytes];
        for (int i = 0; i < pieces.length; i++) {
            if (pieces[i]) {
                bitfield[i / 8] |= (1 << (7 - (i % 8)));
            }
        }
        return bitfield;
    }

    public static boolean[] parseBitfield(byte[] payload, int totalPieces) {
        boolean[] bitfield = new boolean[totalPieces];
        for (int i = 0; i < totalPieces; i++) {
            int byteIndex = i / 8;
            int bitIndex = 7 - (i % 8);
            if (byteIndex < payload.length) {
                bitfield[i] = (payload[byteIndex] & (1 << bitIndex)) != 0;
            }
        }
        return bitfield;
    }

    public static byte[] buildRequest(int pieceIndex,
            int offset, int length) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeInt(pieceIndex);
        dos.writeInt(offset);
        dos.writeInt(length);
        return bos.toByteArray();
    }

    public String getTypeName() {
        switch (type) {
            case CHOKE:        return "CHOKE";
            case UNCHOKE:      return "UNCHOKE";
            case INTERESTED:   return "INTERESTED";
            case UNINTERESTED: return "UNINTERESTED";
            case HAVE:         return "HAVE";
            case BITFIELD:     return "BITFIELD";
            case REQUEST:      return "REQUEST";
            case PIECE:        return "PIECE";
            case CANCEL:       return "CANCEL";
            case -1:           return "KEEPALIVE";
            default:           return "UNKNOWN(" + type + ")";
        }
    }

    @Override
    public String toString() {
        return "PeerMessage[" + getTypeName() + ", "
            + payload.length + " bytes]";
    }
}
