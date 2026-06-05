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

    static final int PIECE_SIZE = 512 * 1024; // 512KB

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.out.println("Usage: java App <torrent-file> [port] [mode]");
            System.out.println("Example: java App torrents\\forza.torrent 6881 leecher");
            return;
        }

        String torrentPath = args[0];
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6881;
        String mode = args.length > 2 ? args[2] : "leecher";

        System.out.println("Loading torrent: " + torrentPath);
        TorrentMetadata metadata = TorrentMetadata.fromFile(torrentPath);

        String infoHashEncoded = metadata.getInfoHashUrlEncoded();
        byte[] infoHash = metadata.getInfoHash();
        String announceUrl = metadata.getAnnounceUrl();
        long fileSize = metadata.getFileSize();
        int totalPieces = metadata.getTotalPieces();
        int pieceSize = metadata.getPieceLength();
        byte[][] pieceHashes = metadata.getPieceHashes();

        String peerIdStr = "-BT0001-" + String.format("%012d",
                new java.util.Random().nextInt(1000000000));
        byte[] peerId = peerIdStr.getBytes("UTF-8");

        System.out.println("Starting client on port: " + port + " mode: " + mode);

        TrackerClient tracker = new TrackerClient(announceUrl, infoHashEncoded, port);

        // SEEDER MODE
        if (mode.equals("seeder")) {
            System.out.println("Running as seeder...");

            try {
                tracker.getPeers();
                System.out.println("Announced to tracker");
            } catch (Exception e) {
                System.out.println("Tracker note: " + e.getMessage());
            }

            File fileToServe = new File(
                    "c:\\Users\\Harsh\\Documents\\bit-torrent-client\\test-files\\sample.pdf");
            byte[] fileData = java.nio.file.Files.readAllBytes(fileToServe.toPath());
            System.out.println("Serving file: " + fileToServe.getName()
                    + " (" + fileData.length + " bytes)");

            int filePieces = (int) Math.ceil((double) fileData.length / PIECE_SIZE);
            byte[][] pieces = new byte[filePieces][];
            for (int i = 0; i < filePieces; i++) {
                int start = i * PIECE_SIZE;
                int end = Math.min(start + PIECE_SIZE, fileData.length);
                pieces[i] = new byte[end - start];
                System.arraycopy(fileData, start, pieces[i], 0, pieces[i].length);
            }
            System.out.println("File split into " + filePieces + " pieces");

            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Seeder listening on port " + port + "...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Peer connected: "
                        + socket.getInetAddress().getHostAddress());
                final Socket s = socket;
                new Thread(() -> {
                    try {
                        handlePeer(s, infoHash, peerId, pieces, filePieces);
                    } catch (Exception e) {
                        System.out.println("Peer error: " + e.getMessage());
                    }
                }).start();
            }
        }

        // LEECHER MODE
        System.out.println("Running as leecher...");

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

        System.out.println("File size: " + fileSize + " bytes");
        System.out.println("Total pieces: " + totalPieces);

        PieceManager pieceManager = new PieceManager(
                totalPieces, pieceSize, fileSize, pieceHashes);

        String outputPath = "c:\\Users\\Harsh\\Documents\\bit-torrent-client\\received\\"
                + metadata.getFileName();

        FileWriter fileWriter = new FileWriter(outputPath, fileSize, pieceSize);

        PeerManager peerManager = new PeerManager(peers, pieceManager, fileWriter, infoHash, peerId, port);
        peerManager.startDownload();

        System.out.println("Done! Check received/" + metadata.getFileName());
    }

    private static void handlePeer(Socket socket, byte[] infoHash,
            byte[] peerId, byte[][] pieces, int totalPieces) throws Exception {

        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        int pstrLen = in.readByte() & 0xFF;
        byte[] pstr = new byte[pstrLen];
        in.readFully(pstr);
        in.readFully(new byte[8]);
        in.readFully(new byte[20]);
        in.readFully(new byte[20]);

        ByteArrayOutputStream handshake = new ByteArrayOutputStream();
        handshake.write(19);
        handshake.write("BitTorrent protocol".getBytes("UTF-8"));
        handshake.write(new byte[8]);
        handshake.write(infoHash);
        handshake.write(peerId);
        out.write(handshake.toByteArray());
        out.flush();
        System.out.println("Handshake completed");

        boolean[] seederBitfield = new boolean[totalPieces];
        Arrays.fill(seederBitfield, true);
        byte[] bitfieldPayload = PeerMessage.buildBitfield(seederBitfield);
        PeerMessage.send(out, PeerMessage.BITFIELD, bitfieldPayload);
        System.out.println("Sent BITFIELD");

        PeerMessage msg = PeerMessage.read(in);
        System.out.println("Received: " + msg);

        PeerMessage.send(out, PeerMessage.UNCHOKE);
        System.out.println("Sent UNCHOKE — peer can now request pieces");

        try {
            while (true) {
                int messageLength = in.readInt();
                if (messageLength == 0)
                    continue;

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