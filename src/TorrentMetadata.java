import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public class TorrentMetadata {

    private String announceUrl;
    private String fileName;
    private long fileSize;
    private int pieceLength;
    private byte[][] pieceHashes;
    private byte[] infoHash;
    private int totalPieces;

    public static TorrentMetadata fromFile(String torrentPath) throws Exception {
        byte[] torrentBytes = Files.readAllBytes(
                new File(torrentPath).toPath());

        BencodeParser parser = new BencodeParser(torrentBytes);
        Map<String, Object> torrent = (Map<String, Object>) parser.parse();

        TorrentMetadata metadata = new TorrentMetadata();

        // extract announce URL
        metadata.announceUrl = BencodeParser.getString(torrent, "announce");
        System.out.println("Announce URL: " + metadata.announceUrl);

        // try to find an HTTP tracker in announce-list
        List<Object> announceList = (List<Object>) torrent.get("announce-list");
        if (announceList != null) {
            for (Object tier : announceList) {
                List<Object> tierList = (List<Object>) tier;
                for (Object trackerObj : tierList) {
                    String trackerUrl = new String((byte[]) trackerObj, "UTF-8");
                    if (trackerUrl.startsWith("http")) {
                        metadata.announceUrl = trackerUrl;
                        System.out.println("Found HTTP tracker: " + trackerUrl);
                        break;
                    }
                }
                if (metadata.announceUrl.startsWith("http")) break;
            }
        }

        // extract info dictionary
        Map<String, Object> info = (Map<String, Object>) torrent.get("info");

        metadata.fileName = BencodeParser.getString(info, "name");
        
        // handle both single-file and multi-file torrents
        long length = BencodeParser.getLong(info, "length");
        if (length > 0) {
            // single file torrent
            metadata.fileSize = length;
        } else {
            // multi-file torrent — sum up all file sizes
            List<Object> files = (List<Object>) info.get("files");
            if (files != null) {
                long totalSize = 0;
                System.out.println("Multi-file torrent — " + files.size() + " files:");
                for (Object fileObj : files) {
                    Map<String, Object> file = (Map<String, Object>) fileObj;
                    long fileLength = BencodeParser.getLong(file, "length");
                    totalSize += fileLength;

                    // print file path
                    List<Object> pathList = (List<Object>) file.get("path");
                    if (pathList != null && !pathList.isEmpty()) {
                        String fileName = new String(
                            (byte[]) pathList.get(pathList.size() - 1), "UTF-8");
                        System.out.println("  " + fileName 
                            + " (" + fileLength + " bytes)");
                    }
                }
                metadata.fileSize = totalSize;
                System.out.println("Total size: " + totalSize + " bytes");
            }
        }
        
        metadata.pieceLength = (int) BencodeParser.getLong(info, "piece length");

        System.out.println("File: " + metadata.fileName);
        System.out.println("Size: " + metadata.fileSize + " bytes");
        System.out.println("Piece length: " + metadata.pieceLength + " bytes");

        // extract piece hashes — concatenated SHA-1 hashes, 20 bytes each
        byte[] piecesBytes = (byte[]) info.get("pieces");
        metadata.totalPieces = piecesBytes.length / 20;
        metadata.pieceHashes = new byte[metadata.totalPieces][20];

        for (int i = 0; i < metadata.totalPieces; i++) {
            System.arraycopy(piecesBytes, i * 20,
                    metadata.pieceHashes[i], 0, 20);
        }

        System.out.println("Total pieces: " + metadata.totalPieces);

        // compute info hash — SHA-1 of the bencoded info dictionary
        int infoStart = parser.getInfoStart();
        int infoEnd = parser.getInfoEnd();
        if (infoStart == -1 || infoEnd == -1) {
            throw new Exception("Could not find start/end of info dictionary");
        }
        byte[] infoBytes = new byte[infoEnd - infoStart];
        System.arraycopy(torrentBytes, infoStart, infoBytes, 0, infoBytes.length);

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        metadata.infoHash = digest.digest(infoBytes);
        System.out.println("Info hash: " + bytesToHex(metadata.infoHash));

        return metadata;
    }

    // convert info hash bytes to URL encoded string for tracker
    public String getInfoHashUrlEncoded() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte b : infoHash) {
            sb.append(String.format("%%%02X", b & 0xFF));
        }
        return sb.toString();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    // getters
    public String getAnnounceUrl() {
        return announceUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getPieceLength() {
        return pieceLength;
    }

    public byte[][] getPieceHashes() {
        return pieceHashes;
    }

    public byte[] getInfoHash() {
        return infoHash;
    }

    public int getTotalPieces() {
        return totalPieces;
    }
}