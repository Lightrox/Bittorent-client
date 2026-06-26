# BitTorrent Client

A peer-to-peer file transfer client that implements the BitTorrent 
protocol from scratch in Java, including peer discovery, parallel 
piece downloading, SHA-1 integrity verification, and the official 
BitTorrent handshake and wire protocol.

## How It Works

1. Parse the `.torrent` file — extract tracker URL, piece hashes, 
   file size and piece length
2. Announce to the tracker — receive list of active peers in the swarm
3. Connect to each peer via TCP — perform the 68-byte BitTorrent handshake
4. Exchange wire protocol messages — peer sends BITFIELD, client sends 
   INTERESTED, peer responds with UNCHOKE
5. Download pieces using rarest-first selection — verify each piece 
   against SHA-1 hash — write to correct byte offset using 
   RandomAccessFile — repeat until file is fully assembled

## Architecture
.torrent file
↓
BencodeParser → TorrentMetadata
↓
TrackerClient → PeerInfo[]
↓
PeerConnection (TCP + Handshake + Wire Protocol)
↓
PeerManager (ExecutorService — one thread per peer)
↓
PieceManager (rarest-first selection, thread-safe state tracking)
↓
PieceVerifier (SHA-1 verification)
↓
FileWriter (RandomAccessFile — correct byte offset per piece)
↓
output file

## Key CS Concepts Implemented

- **TCP Sockets** — raw socket programming for peer connections
- **BitTorrent Handshake** — 68-byte protocol handshake 
  (pstrlen + pstr + reserved + info_hash + peer_id)
- **Wire Protocol** — BITFIELD, INTERESTED, UNCHOKE, REQUEST, 
  PIECE messages
- **Multithreading** — ExecutorService thread pool, one thread 
  per peer downloading simultaneously
- **Thread Safety** — synchronized methods in PieceManager 
  prevent two threads downloading the same piece
- **SHA-1 Verification** — every piece verified before writing 
  to disk, corrupted pieces discarded and re-queued
- **RandomAccessFile** — pieces written at exact byte offsets 
  allowing out-of-order assembly
- **Rarest-First Strategy** — pieces available from fewest peers 
  requested first to improve swarm health
- **Bencode Parsing** — decoding the binary encoding format used 
  by .torrent files

## Design Decisions

**Why Java?** Strong standard library for networking 
(`Socket`, `ServerSocket`, `ExecutorService`) and explicit 
threading model makes concurrency behaviour easy to reason about.

**Why synchronized over locks?** For a project of this scale, 
`synchronized` methods in `PieceManager` are sufficient and 
readable. A production implementation would use 
`ReentrantLock` or `ConcurrentHashMap` for finer-grained control.

**Why 512KB piece size?** Matches common real-world torrent 
piece sizes. Small enough for fast verification, large enough 
to minimize request overhead.

**What I'd do differently:** I'd make piece size and max peer 
connections configurable via command line arguments rather than 
hardcoding them, and separate the seeder and leecher into 
distinct entry points for cleaner code.

## Project Structure
bit-torrent-client/
├── src/
│   ├── App.java                  ← entry point
│   ├── peer/
│   │   ├── PeerConnection.java   ← TCP + handshake + wire protocol
│   │   ├── PeerManager.java      ← thread pool, download coordination
│   │   └── PeerMessage.java      ← encode/decode wire protocol messages
│   ├── tracker/
│   │   ├── Tracker.java          ← local HTTP tracker server
│   │   ├── TrackerClient.java    ← HTTP announce, peer list parsing
│   │   └── PeerInfo.java         ← peer IP and port
│   └── download/
│       ├── PieceManager.java     ← piece state tracking, rarest-first
│       ├── PieceVerifier.java    ← SHA-1 integrity verification
│       └── FileWriter.java       ← RandomAccessFile piece assembly
├── torrents/                     ← put .torrent files here
├── received/                     ← downloaded files appear here
└── test-files/                   ← files used for local testing

## How To Run

**Compile:**
```bash
javac -d bin src\tracker\PeerInfo.java src\tracker\TrackerClient.java \
src\tracker\Tracker.java src\download\PieceVerifier.java \
src\download\PieceManager.java src\download\FileWriter.java \
src\peer\PeerMessage.java src\peer\PeerConnection.java \
src\peer\PeerManager.java src\App.java
```

**Run tracker:**
```bash
java -cp bin tracker.Tracker
```

**Run seeder:**
```bash
java -cp bin App 6882 seeder
```

**Run leecher:**
```bash
java -cp bin App 6881 leecher
```

## What's Next

- Bencode parser to read real `.torrent` files
- Connect to real peers on the internet
- JAR packaging for easy distribution# BitTorrent Client

A peer-to-peer file transfer client that implements the BitTorrent 
protocol from scratch in Java, including peer discovery, parallel 
piece downloading, SHA-1 integrity verification, and the official 
BitTorrent handshake and wire protocol.

## How It Works

1. Parse the `.torrent` file — extract tracker URL, piece hashes, 
   file size and piece length
2. Announce to the tracker — receive list of active peers in the swarm
3. Connect to each peer via TCP — perform the 68-byte BitTorrent handshake
4. Exchange wire protocol messages — peer sends BITFIELD, client sends 
   INTERESTED, peer responds with UNCHOKE
5. Download pieces using rarest-first selection — verify each piece 
   against SHA-1 hash — write to correct byte offset using 
   RandomAccessFile — repeat until file is fully assembled

## Architecture
.torrent file
↓
BencodeParser → TorrentMetadata
↓
TrackerClient → PeerInfo[]
↓
PeerConnection (TCP + Handshake + Wire Protocol)
↓
PeerManager (ExecutorService — one thread per peer)
↓
PieceManager (rarest-first selection, thread-safe state tracking)
↓
PieceVerifier (SHA-1 verification)
↓
FileWriter (RandomAccessFile — correct byte offset per piece)
↓
output file

## Key CS Concepts Implemented

- **TCP Sockets** — raw socket programming for peer connections
- **BitTorrent Handshake** — 68-byte protocol handshake 
  (pstrlen + pstr + reserved + info_hash + peer_id)
- **Wire Protocol** — BITFIELD, INTERESTED, UNCHOKE, REQUEST, 
  PIECE messages
- **Multithreading** — ExecutorService thread pool, one thread 
  per peer downloading simultaneously
- **Thread Safety** — synchronized methods in PieceManager 
  prevent two threads downloading the same piece
- **SHA-1 Verification** — every piece verified before writing 
  to disk, corrupted pieces discarded and re-queued
- **RandomAccessFile** — pieces written at exact byte offsets 
  allowing out-of-order assembly
- **Rarest-First Strategy** — pieces available from fewest peers 
  requested first to improve swarm health
- **Bencode Parsing** — decoding the binary encoding format used 
  by .torrent files

## Design Decisions

**Why Java?** Strong standard library for networking 
(`Socket`, `ServerSocket`, `ExecutorService`) and explicit 
threading model makes concurrency behaviour easy to reason about.

**Why synchronized over locks?** For a project of this scale, 
`synchronized` methods in `PieceManager` are sufficient and 
readable. A production implementation would use 
`ReentrantLock` or `ConcurrentHashMap` for finer-grained control.

**Why 512KB piece size?** Matches common real-world torrent 
piece sizes. Small enough for fast verification, large enough 
to minimize request overhead.

**What I'd do differently:** I'd make piece size and max peer 
connections configurable via command line arguments rather than 
hardcoding them, and separate the seeder and leecher into 
distinct entry points for cleaner code.

## Project Structure
bit-torrent-client/
├── src/
│   ├── App.java                  ← entry point
│   ├── peer/
│   │   ├── PeerConnection.java   ← TCP + handshake + wire protocol
│   │   ├── PeerManager.java      ← thread pool, download coordination
│   │   └── PeerMessage.java      ← encode/decode wire protocol messages
│   ├── tracker/
│   │   ├── Tracker.java          ← local HTTP tracker server
│   │   ├── TrackerClient.java    ← HTTP announce, peer list parsing
│   │   └── PeerInfo.java         ← peer IP and port
│   └── download/
│       ├── PieceManager.java     ← piece state tracking, rarest-first
│       ├── PieceVerifier.java    ← SHA-1 integrity verification
│       └── FileWriter.java       ← RandomAccessFile piece assembly
├── torrents/                     ← put .torrent files here
├── received/                     ← downloaded files appear here
└── test-files/                   ← files used for local testing

## How To Run

**Compile:**
```bash
javac -d bin src\tracker\PeerInfo.java src\tracker\TrackerClient.java \
src\tracker\Tracker.java src\download\PieceVerifier.java \
src\download\PieceManager.java src\download\FileWriter.java \
src\peer\PeerMessage.java src\peer\PeerConnection.java \
src\peer\PeerManager.java src\App.java
```

**Run tracker:**
```bash
java -cp bin tracker.Tracker
```

**Run seeder:**
```bash
java -cp bin App 6882 seeder
```

**Run leecher:**
```bash
java -cp bin App 6881 leecher
```

**Run GUI:**
To launch the graphical user interface, run the client with no arguments:
```bash
java -cp bin App
```
*(This launches a Swing window where you can browse and select a `.torrent` file, set the port, and click "Start Download".)*

**Local Test Swarm (Demonstration):**
To run a complete download demonstration locally using the generated sample torrent:
1. **Start Tracker:**
   ```bash
   java -cp bin tracker.Tracker
   ```
2. **Start Seeder:**
   ```bash
   java -cp bin App torrents\sample.torrent 6882 seeder
   ```
3. **Start Leecher (GUI):**
   * Run `java -cp bin App` to open the GUI.
   * Click **Browse** and select `torrents\sample.torrent`.
   * Set Port to `6881` and Mode to `leecher`.
   * Click **Start Download** to download the file directly from the local seeder.


## What's Next

- JAR packaging for easy distribution
- Implementing UDP protocol