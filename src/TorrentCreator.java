import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Random;

public class TorrentCreator {

    public static void main(String[] args) {
        String fileName;
        long fileSize;
        int pieceSize = 512 * 1024; // 512 KB default
        String trackerUrl = "http://192.168.1.8:8080/announce";
        boolean generateFile = false;

        File testFilesDir = new File("c:\\Users\\Harsh\\Documents\\bit-torrent-client\\test-files");
        File torrentsDir = new File("c:\\Users\\Harsh\\Documents\\bit-torrent-client\\torrents");

        if (args.length > 0) {
            fileName = args[0];
            // If piece size is specified (in KB)
            if (args.length > 1) {
                try {
                    pieceSize = Integer.parseInt(args[1]) * 1024;
                } catch (NumberFormatException e) {
                    System.out.println("Invalid piece size KB. Using default 512 KB.");
                }
            }
        } else {
            fileName = "random-1g.bin";
            generateFile = true;
        }

        File targetFile = new File(testFilesDir, fileName);
        if (!generateFile && !targetFile.exists()) {
            System.err.println("Error: File not found at " + targetFile.getAbsolutePath());
            System.out.println(
                    "Usage for existing file: java -cp bin TorrentCreator <filename_in_test-files> [piece_size_in_kb]");
            System.out.println("Usage for generating 1GB random file: java -cp bin TorrentCreator");
            return;
        }

        if (generateFile) {
            fileSize = 1024L * 1024L * 1024L; // 1 GB
        } else {
            fileSize = targetFile.length();
        }

        String torrentName = fileName + ".torrent";
        if (fileName.contains(".")) {
            torrentName = fileName.substring(0, fileName.lastIndexOf('.')) + ".torrent";
        }
        File torrentFile = new File(torrentsDir, torrentName);

        if (generateFile) {
            System.out.println("Generating 1GB file of random bytes at: " + targetFile.getAbsolutePath());
        } else {
            System.out.println("Creating torrent for existing file: " + targetFile.getAbsolutePath());
        }
        System.out.println("File size: " + fileSize + " bytes");
        System.out.println("Piece size: " + pieceSize + " bytes");

        int totalPieces = (int) Math.ceil((double) fileSize / pieceSize);
        byte[] pieceHashes = new byte[totalPieces * 20];

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            long startTime = System.currentTimeMillis();

            if (generateFile) {
                if (!testFilesDir.exists())
                    testFilesDir.mkdirs();
                Random random = new Random();
                byte[] buffer = new byte[pieceSize];
                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(targetFile))) {
                    for (int i = 0; i < totalPieces; i++) {
                        random.nextBytes(buffer);
                        digest.reset();
                        byte[] hash = digest.digest(buffer);
                        System.arraycopy(hash, 0, pieceHashes, i * 20, 20);
                        bos.write(buffer);
                        if ((i + 1) % 200 == 0) {
                            System.out.println("Processed " + (i + 1) + "/" + totalPieces + " pieces ("
                                    + ((i + 1) * 100 / totalPieces) + "%)");
                        }
                    }
                }
            } else {
                // Read existing file and compute hashes
                byte[] buffer = new byte[pieceSize];
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(targetFile))) {
                    int bytesRead;
                    int pieceIndex = 0;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        digest.reset();
                        digest.update(buffer, 0, bytesRead);
                        byte[] hash = digest.digest();
                        System.arraycopy(hash, 0, pieceHashes, pieceIndex * 20, 20);
                        pieceIndex++;
                        if (pieceIndex % 200 == 0 || pieceIndex == totalPieces) {
                            System.out.println("Hashed " + pieceIndex + "/" + totalPieces + " pieces ("
                                    + (pieceIndex * 100 / totalPieces) + "%)");
                        }
                    }
                }
            }

            long endTime = System.currentTimeMillis();
            System.out.println("Processed file in " + (endTime - startTime) + " ms.");

            if (!torrentsDir.exists())
                torrentsDir.mkdirs();
            System.out.println("Generating torrent file at: " + torrentFile.getAbsolutePath());
            writeTorrentFile(torrentFile, trackerUrl, fileName, fileSize, pieceSize, pieceHashes);
            System.out.println("Torrent file created successfully: " + torrentFile.getName());

        } catch (Exception e) {
            System.err.println("Error generating files:");
            e.printStackTrace();
        }
    }

    private static void writeTorrentFile(File file, String announceUrl, String name, long length, int pieceLength,
            byte[] pieces) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            // Write outer dict start
            fos.write('d');

            // Write announce url
            writeString(fos, "announce");
            writeString(fos, announceUrl);

            // Write info dict start
            writeString(fos, "info");
            fos.write('d');

            // Write length
            writeString(fos, "length");
            writeInteger(fos, length);

            // Write name
            writeString(fos, "name");
            writeString(fos, name);

            // Write piece length
            writeString(fos, "piece length");
            writeInteger(fos, pieceLength);

            // Write pieces
            writeString(fos, "pieces");
            writeByteArray(fos, pieces);

            // Close info dict
            fos.write('e');

            // Close outer dict
            fos.write('e');
        }
    }

    private static void writeString(FileOutputStream fos, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        fos.write((bytes.length + ":").getBytes(StandardCharsets.UTF_8));
        fos.write(bytes);
    }

    private static void writeByteArray(FileOutputStream fos, byte[] bytes) throws IOException {
        fos.write((bytes.length + ":").getBytes(StandardCharsets.UTF_8));
        fos.write(bytes);
    }

    private static void writeInteger(FileOutputStream fos, long val) throws IOException {
        fos.write(('i' + String.valueOf(val) + 'e').getBytes(StandardCharsets.UTF_8));
    }
}
