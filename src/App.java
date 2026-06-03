import tracker.TrackerClient;
import tracker.PeerInfo;
import peer.PeerConnection;
import peer.PeerManager;
import download.PieceManager;
import download.FileWriter;
import java.util.List;
import java.util.ArrayList;
import java.net.*;
import java.io.*;
import java.util.Arrays;
import peer.PeerMessage;

public class App {

    static final String INFO_HASH_ENCODED = "%45%5A%D4%8C%E8%CD%08%15%6D%F3%6F%31%5B%42%B1%AC%3A%71%82%45";
    static final String TRACKER_URL = "http://localhost:8080/announce";
    static final int PIECE_SIZE = 512 * 1024; // 512KB

    public static void main(String[] args) throws Exception {

        int port = args.length > 0 ? Integer.parseInt(args[0]) : 6881;
        String mode = args.length > 1 ? args[1] : "leecher";
        System.out.println("Starting client on port: " + port + " mode: " + mode);

        byte[] infoHash = urlDecodeToBytes(INFO_HASH_ENCODED);
        String peerIdStr = "-BT0001-" + String.format("%012d",
            new java.util.Random().nextInt(1000000000));
        byte[] peerId = peerIdStr.getBytes("UTF-8");

        TrackerClient tracker = new TrackerClient(TRACKER_URL, INFO_HASH_ENCODED, port);

        // SEEDER MODE
        if (mode.equals("seeder")) {
            System.out.println("Running as seeder...");

            try {
                tracker.getPeers();
                System.out.println("Announced to tracker");
            } catch (Exception e) {
                System.out.println("Tracker note: " + e.getMessage());
            }

            // load file to serve
            File fileToServe = new File(
                "c:\\Users\\Harsh\\Documents\\bit-torrent-client\\test-files\\sample.pdf");
            byte[] fileData = java.nio.file.Files.readAllBytes(fileToServe.toPath());
            System.out.println("Serving file: " + fileToServe.getName() 
                + " (" + fileData.length + " bytes)");

            // split into pieces
            int totalPieces = (int) Math.ceil((double) fileData.length / PIECE_SIZE);
            byte[][] pieces = new byte[totalPieces][];
            for (int i = 0; i < totalPieces; i++) {
                int start = i * PIECE_SIZE;
                int end = Math.min(start + PIECE_SIZE, fileData.length);
                pieces[i] = new byte[end - start];
                System.arraycopy(fileData, start, pieces[i], 0, pieces[i].length);
            }
            System.out.println("File split into " + totalPieces + " pieces");

            // listen for incoming connections
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Seeder listening on port " + port + "...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Peer connected: " 
                    + socket.getInetAddress().getHostAddress());
                final Socket s = socket;
                new Thread(() -> {
                    try {
                        handlePeer(s, infoHash, peerId, pieces, totalPieces);
                    } catch (Exception e) {
                        System.out.println("Peer error: " + e.getMessage());
                    }
                }).start();
            }
        }

        // LEECHER MODE
        System.out.println("Running as leecher...");

        // get peers from tracker
        List<PeerInfo> peers = new ArrayList<>();
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                peers = tracker.getPeers();
            } catch (Exception e) {
                System.out.println("Tracker error: " + e.getMessage());
            }
            if (!peers.isEmpty()) {
                System.out.println("Got " + peers.size() + " peers");
                break;
            }
            System.out.println("No peers yet, retrying... (" + attempt + "/5)");
            Thread.sleep(2000);
        }

        if (peers.isEmpty()) {
            System.out.println("No peers found");
            return;
        }

        // get file size from seeder first
        // for now hardcode — week 6 will read from .torrent file
        File sourceFile = new File(
            "c:\\Users\\Harsh\\Documents\\bit-torrent-client\\test-files\\sample.pdf");
        long fileSize = sourceFile.length();
        int totalPieces = (int) Math.ceil((double) fileSize / PIECE_SIZE);

        System.out.println("File size: " + fileSize + " bytes");
        System.out.println("Total pieces: " + totalPieces);

        // set up download components
        PieceManager pieceManager = new PieceManager(
            totalPieces, PIECE_SIZE, fileSize, null); // null hashes for now

        FileWriter fileWriter = new FileWriter(
            "c:\\Users\\Harsh\\Documents\\bit-torrent-client\\received\\downloaded.pdf",
            fileSize, PIECE_SIZE);

        // start parallel download
        PeerManager peerManager = new PeerManager(
            peers, pieceManager, fileWriter, infoHash, peerId, port);
        peerManager.startDownload();

        System.out.println("Done! Check received/downloaded.pdf");
    }

    private static void handlePeer(Socket socket, byte[] infoHash,
            byte[] peerId, byte[][] pieces, int totalPieces) throws Exception {

        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        // read incoming handshake
        int pstrLen = in.readByte() & 0xFF;
        byte[] pstr = new byte[pstrLen];
        in.readFully(pstr);
        in.readFully(new byte[8]);
        in.readFully(new byte[20]);
        in.readFully(new byte[20]);

        // send handshake back
        ByteArrayOutputStream handshake = new ByteArrayOutputStream();
        handshake.write(19);
        handshake.write("BitTorrent protocol".getBytes("UTF-8"));
        handshake.write(new byte[8]);
        handshake.write(infoHash);
        handshake.write(peerId);
        out.write(handshake.toByteArray());
        out.flush();
        System.out.println("Handshake completed");

        // BITFIELD → INTERESTED → UNCHOKE
        boolean[] seederBitfield = new boolean[totalPieces];
        Arrays.fill(seederBitfield, true);
        byte[] bitfieldPayload = PeerMessage.buildBitfield(seederBitfield);
        PeerMessage.send(out, PeerMessage.BITFIELD, bitfieldPayload);
        System.out.println("Sent BITFIELD");

        PeerMessage msg = PeerMessage.read(in);
        System.out.println("Received: " + msg);

        PeerMessage.send(out, PeerMessage.UNCHOKE);
        System.out.println("Sent UNCHOKE — peer can now request pieces");

        // serve piece requests
        try {
            while (true) {
                int messageLength = in.readInt();
                if (messageLength == 0) continue;

                int messageId = in.readByte() & 0xFF;

                if (messageId == 6) {
                    int pieceIndex = in.readInt();
                    int blockOffset = in.readInt();
                    int blockLength = in.readInt();

                    System.out.println("Serving piece " + pieceIndex);

                    byte[] pieceData = pieces[pieceIndex];

                    out.writeInt(9 + pieceData.length);
                    out.writeByte(7);
                    out.writeInt(pieceIndex);
                    out.writeInt(blockOffset);
                    out.write(pieceData);
                    out.flush();

                    System.out.println("Sent piece " + pieceIndex
                        + " (" + pieceData.length + " bytes)");
                }
            }
        } catch (EOFException e) {
            System.out.println("Peer disconnected cleanly");
        } catch (Exception e) {
            System.out.println("Peer disconnected: " + e.getMessage());
        }
    }

    private static byte[] urlDecodeToBytes(String encoded) throws Exception {
        String[] parts = encoded.split("%");
        byte[] bytes = new byte[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            bytes[i - 1] = (byte) Integer.parseInt(parts[i], 16);
        }
        return bytes;
    }
}