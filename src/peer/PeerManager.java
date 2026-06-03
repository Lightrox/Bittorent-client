package peer;

import download.FileWriter;
import download.PieceManager;
import download.PieceVerifier;
import tracker.PeerInfo;
import java.util.List;
import java.util.concurrent.*;

public class PeerManager {

    private final List<PeerInfo> peers;
    private final PieceManager pieceManager;
    private final FileWriter fileWriter;
    private final byte[] infoHash;
    private final byte[] peerId;
    private final int port;

    // thread pool — one thread per peer
    private final ExecutorService threadPool;

    public PeerManager(List<PeerInfo> peers, PieceManager pieceManager,
            FileWriter fileWriter, byte[] infoHash, byte[] peerId, int port) {
        this.peers = peers;
        this.pieceManager = pieceManager;
        this.fileWriter = fileWriter;
        this.infoHash = infoHash;
        this.peerId = peerId;
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(peers.size());
    }

    public void startDownload() throws Exception {
        System.out.println("Starting download with " + peers.size() + " peers");

        // submit one download task per peer
        for (PeerInfo peer : peers) {
            threadPool.submit(() -> {
                try {
                    downloadFromPeer(peer);
                } catch (Exception e) {
                    System.out.println("Peer " + peer + " error: " + e.getMessage());
                }
            });
        }

        // wait for all threads to finish
        threadPool.shutdown();
        threadPool.awaitTermination(30, TimeUnit.MINUTES);

        if (pieceManager.isComplete()) {
            System.out.println("\nDownload complete!");
            fileWriter.close();
        } else {
            System.out.println("\nDownload incomplete — some pieces missing");
        }
    }

    private void downloadFromPeer(PeerInfo peerInfo) throws Exception {
        System.out.println("Thread started for peer: " + peerInfo);

        // step 1 — connect and handshake
        PeerConnection connection = new PeerConnection(peerInfo, infoHash, peerId, port);
        if (!connection.connect()) {
            System.out.println("Could not connect to peer: " + peerInfo);
            return;
        }

        // step 2 — BITFIELD / INTERESTED / UNCHOKE
        boolean[] peerBitfield = connection.performMessageExchange(
            pieceManager.getTotalPieces());
        pieceManager.updateAvailability(peerBitfield);
        // step 3 — download pieces one by one
        while (!pieceManager.isComplete()) {
            // get next piece to download — rarest first
            int pieceIndex = pieceManager.getNextPiece(peerBitfield);

            if (pieceIndex == -1) {
                // no more pieces to download
                break;
            }

            try {
                // step 4 — request and receive piece
                byte[] pieceData = connection.requestPiece(
                    pieceIndex,
                    pieceManager.getPieceSize(pieceIndex)
                );

                byte[] expectedHash = pieceManager.getExpectedHash(pieceIndex);
                if (expectedHash != null) {
                    if (!PieceVerifier.verify(pieceData, expectedHash)) {
                        System.out.println("Piece " + pieceIndex + " failed verification");
                        pieceManager.markFailed(pieceIndex);
                        continue;
                    }
                } else {
                    System.out.println("No hash available — skipping verification");
                }

                fileWriter.writePiece(pieceIndex, pieceData);
                pieceManager.markCompleted(pieceIndex);
                pieceManager.printStatus();

            } catch (Exception e) {
                System.out.println("Failed to download piece "
                    + pieceIndex + ": " + e.getMessage());
                e.printStackTrace();
                pieceManager.markMissing(pieceIndex);
                break;
            }
        }

        connection.close();
        System.out.println("Thread finished for peer: " + peerInfo);
    }
}