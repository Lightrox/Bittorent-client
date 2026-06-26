package download;

import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

public class PieceManager {

    public enum PieceState {
        MISSING,
        PENDING,
        COMPLETED
    }

    private final int totalPieces;
    private final int pieceSize;
    private final long fileSize;
    private final byte[][] expectedHashes;

    private final Map<Integer, PieceState> pieceStates;
    private final Map<Integer, Integer> pieceAvailability;

    private int completedCount = 0;

    public PieceManager(int totalPieces, int pieceSize, long fileSize, byte[][] expectedHashes) {
        this.totalPieces = totalPieces;
        this.pieceSize = pieceSize;
        this.fileSize = fileSize;
        this.expectedHashes = expectedHashes;

        this.pieceStates = new ConcurrentHashMap<>();
        this.pieceAvailability = new ConcurrentHashMap<>();

        for (int i = 0; i < totalPieces; i++) {
            pieceStates.put(i, PieceState.MISSING);
            pieceAvailability.put(i, 0);
        }
    }

    public synchronized void updateAvailability(boolean[] bitfield) {
        for (int i = 0; i < bitfield.length && i < totalPieces; i++) {
            if (bitfield[i]) {
                pieceAvailability.merge(i, 1, Integer::sum);
            }
        }
    }

    public synchronized int getNextPiece(boolean[] peerBitfield) {
        int rarestPiece = -1;
        int rarestCount = Integer.MAX_VALUE;

        for (int i = 0; i < totalPieces; i++) {
            if (pieceStates.get(i) != PieceState.MISSING) continue;

            if (peerBitfield != null && i < peerBitfield.length && !peerBitfield[i]) continue;

            int availability = pieceAvailability.getOrDefault(i, 0);
            if (availability < rarestCount) {
                rarestCount = availability;
                rarestPiece = i;
            }
        }

        if (rarestPiece != -1) {
            pieceStates.put(rarestPiece, PieceState.PENDING);
            System.out.println("Piece " + rarestPiece + " assigned — "
                + getRemainingCount() + " pieces remaining");
        }

        return rarestPiece;
    }

    public synchronized void markCompleted(int pieceIndex) {
        pieceStates.put(pieceIndex, PieceState.COMPLETED);
        completedCount++;
        System.out.println("Piece " + pieceIndex + " completed — "
            + completedCount + "/" + totalPieces + " done");
    }

    public synchronized void markFailed(int pieceIndex) {
        pieceStates.put(pieceIndex, PieceState.MISSING);
        System.out.println("Piece " + pieceIndex + " failed — re-queued");
    }

    public synchronized void markMissing(int pieceIndex) {
        if (pieceStates.get(pieceIndex) == PieceState.PENDING) {
            pieceStates.put(pieceIndex, PieceState.MISSING);
        }
    }

    public synchronized boolean isComplete() {
        return completedCount == totalPieces;
    }

    public synchronized int getCompletedCount() {
        return completedCount;
    }

    public synchronized int getRemainingCount() {
        return totalPieces - completedCount;
    }

    public int getPieceSize(int pieceIndex) {
        if (pieceIndex == totalPieces - 1) {
            long remainder = fileSize % pieceSize;
            return remainder == 0 ? pieceSize : (int) remainder;
        }
        return pieceSize;
    }

    public byte[] getExpectedHash(int pieceIndex) {
        if (expectedHashes == null) return null;
        return expectedHashes[pieceIndex];
    }

    public int getTotalPieces() {
        return totalPieces;
    }

    public int getPieceSize() {
        return pieceSize;
    }

    public void printStatus() {
        long percent = (completedCount * 100L) / totalPieces;
        int filled = (int) (percent / 5);
        String bar = "█".repeat(filled) + "░".repeat(20 - filled);
        System.out.print("\r[" + bar + "] " + percent + "% | "
            + completedCount + "/" + totalPieces + " pieces");
    }
}
