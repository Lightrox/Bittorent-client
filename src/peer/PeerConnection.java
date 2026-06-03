package peer;

import tracker.PeerInfo;
import java.io.*;
import java.net.*;

public class PeerConnection {

    private PeerInfo peer;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private byte[] infoHash;
    private byte[] peerId;
    private int port;

    public PeerConnection(PeerInfo peer, byte[] infoHash, byte[] peerId, int port) {
        this.peer = peer;
        this.infoHash = infoHash;
        this.peerId = peerId;
        this.port = port;
    }

    public boolean connect() {
        try {
            System.out.println("Connecting to peer: " + peer);
            System.out.println("From local client port: " + port);

            socket = new Socket();
            socket.connect(
                new InetSocketAddress(peer.getIp(), peer.getPort()),
                5000
            );
            System.out.println("TCP connection established!");

            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            System.out.println("Connected! Performing handshake...");
            boolean result = performHandshake();
            System.out.println("Handshake result: " + result);
            return result;

        } catch (ConnectException e) {
            System.out.println("Connection refused by peer: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (SocketTimeoutException e) {
            System.out.println("Connection timed out: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.out.println("Unexpected error: " + e.getClass().getName()
                + " — " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean performHandshake() throws Exception {
        byte[] protocolName = "BitTorrent protocol".getBytes("UTF-8");

        ByteArrayOutputStream handshake = new ByteArrayOutputStream();
        handshake.write(19);
        handshake.write(protocolName);
        handshake.write(new byte[8]);
        handshake.write(infoHash);
        handshake.write(peerId);

        out.write(handshake.toByteArray());
        out.flush();

        System.out.println("Handshake sent — waiting for response...");

        int pstrLen = in.readByte() & 0xFF;
        if (pstrLen != 19) {
            System.out.println("Invalid handshake — wrong protocol length: " + pstrLen);
            return false;
        }

        byte[] pstr = new byte[19];
        in.readFully(pstr);
        String protocol = new String(pstr, "UTF-8");
        if (!protocol.equals("BitTorrent protocol")) {
            System.out.println("Invalid handshake — wrong protocol: " + protocol);
            return false;
        }

        byte[] reserved = new byte[8];
        in.readFully(reserved);

        byte[] receivedInfoHash = new byte[20];
        in.readFully(receivedInfoHash);

        byte[] receivedPeerId = new byte[20];
        in.readFully(receivedPeerId);

        System.out.println("Handshake successful!");
        System.out.println("Peer ID: " + new String(receivedPeerId, "UTF-8"));
        return true;
    }

    public byte[] requestPiece(int pieceIndex, int pieceSize) throws Exception {
        out.writeInt(13);
        out.writeByte(6);
        out.writeInt(pieceIndex);
        out.writeInt(0);
        out.writeInt(pieceSize);
        out.flush();

        int messageLength = in.readInt();
        int messageId = in.readByte() & 0xFF;

        if (messageId != 7) {
            throw new Exception("Expected piece message (7) but got: " + messageId);
        }

        int receivedIndex = in.readInt();
        in.readInt();
        int dataLength = messageLength - 9;

        byte[] pieceData = new byte[dataLength];
        in.readFully(pieceData);

        System.out.println("Received piece " + receivedIndex
            + " — " + dataLength + " bytes");
        return pieceData;
    }

    public boolean[] performMessageExchange(int totalPieces) throws Exception {
        System.out.println("Waiting for bitfield...");

        // read first message — should be bitfield
        PeerMessage msg = PeerMessage.read(in);
        System.out.println("Received: " + msg);

        boolean[] peerBitfield = new boolean[totalPieces];

        if (msg.getType() == PeerMessage.BITFIELD) {
            peerBitfield = PeerMessage.parseBitfield(msg.getPayload(), totalPieces);
            int count = 0;
            for (boolean b : peerBitfield) if (b) count++;
            System.out.println("Peer has " + count + "/" + totalPieces + " pieces");
        }

        // send interested
        System.out.println("Sending INTERESTED...");
        PeerMessage.send(out, PeerMessage.INTERESTED);

        // wait for unchoke
        System.out.println("Waiting for UNCHOKE...");
        msg = PeerMessage.read(in);
        System.out.println("Received: " + msg);

        if (msg.getType() != PeerMessage.UNCHOKE) {
            throw new Exception("Expected UNCHOKE but got: " + msg.getTypeName());
        }

        System.out.println("Unchoked! Ready to download pieces.");
        return peerBitfield;
    }

    public void close() {
        try {
            if (socket != null) socket.close();
        } catch (Exception e) {
            System.out.println("Error closing connection: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
