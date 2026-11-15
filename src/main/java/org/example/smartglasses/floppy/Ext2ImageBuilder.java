package org.example.smartglasses.floppy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a minimal, Linux-mountable 512 KiB ext2 filesystem image in memory
 * for preloaded OC2R floppy disks.
 */
public final class Ext2ImageBuilder {
    public static final int IMAGE_SIZE = 512 * 1024;
    public static final int BLOCK_SIZE = 1024;
    public static final int TOTAL_BLOCKS = IMAGE_SIZE / BLOCK_SIZE; // 512
    public static final int TOTAL_INODES = 64;
    private static final int INODE_SIZE = 128;
    private static final int INODE_TABLE_BLOCKS = (TOTAL_INODES * INODE_SIZE) / BLOCK_SIZE; // 8

    private static final int FIRST_DATA_BLOCK = 1;
    private static final int BLOCK_SUPERBLOCK = 1;
    private static final int BLOCK_BGDT = 2;
    private static final int BLOCK_BITMAP = 3;
    private static final int INODE_BITMAP = 4;
    private static final int INODE_TABLE_START = 5;
    private static final int ROOT_DIR_BLOCK = INODE_TABLE_START + INODE_TABLE_BLOCKS; // 13
    private static final int FIRST_FILE_BLOCK = ROOT_DIR_BLOCK + 1; // 14

    private static final int EXT2_SUPER_MAGIC = 0xEF53;
    private static final int FIRST_NON_RESERVED_INODE = 11;
    private static final int MAX_DIRECT_BLOCKS = 12;

    private final Map<String, byte[]> files = new LinkedHashMap<>();

    public Ext2ImageBuilder addFile(String name, String content) {
        if (name == null || name.isEmpty() || name.contains("/") || name.length() > 255) {
            throw new IllegalArgumentException("Invalid filename: " + name);
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_DIRECT_BLOCKS * BLOCK_SIZE) {
            throw new IllegalArgumentException("File exceeds 12 KiB direct block limit: " + name);
        }
        if (files.size() >= TOTAL_INODES - (FIRST_NON_RESERVED_INODE - 1)) {
            throw new IllegalStateException("Too many files for inode table");
        }
        files.put(name, bytes);
        return this;
    }

    public byte[] build() {
        byte[] image = new byte[IMAGE_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);

        int nextFreeBlock = FIRST_FILE_BLOCK;
        int nextInode = FIRST_NON_RESERVED_INODE;

        // Allocate blocks for each file and write file data + inodes
        int dirOffset = BLOCK_SIZE * ROOT_DIR_BLOCK;
        dirOffset = writeDirEntry(buf, dirOffset, 2, ".", (byte) 2, 12);
        dirOffset = writeDirEntry(buf, dirOffset, 2, "..", (byte) 2, 12);
        int dirBytesRemaining = (BLOCK_SIZE * ROOT_DIR_BLOCK + BLOCK_SIZE) - dirOffset;

        int fileIndex = 0;
        int totalFiles = files.size();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String name = entry.getKey();
            byte[] data = entry.getValue();
            int inodeNum = nextInode++;
            int blocksNeeded = (data.length + BLOCK_SIZE - 1) / BLOCK_SIZE;
            if (nextFreeBlock + blocksNeeded > TOTAL_BLOCKS) {
                throw new IllegalStateException("Out of space on 512 KiB floppy image");
            }

            int[] blockPtrs = new int[MAX_DIRECT_BLOCKS];
            for (int b = 0; b < blocksNeeded; b++) {
                int blk = nextFreeBlock++;
                blockPtrs[b] = blk;
                int copyLen = Math.min(BLOCK_SIZE, data.length - b * BLOCK_SIZE);
                System.arraycopy(data, b * BLOCK_SIZE, image, blk * BLOCK_SIZE, copyLen);
            }

            writeInode(buf, inodeNum, 0x81A4, data.length, 1, blocksNeeded * (BLOCK_SIZE / 512), blockPtrs);

            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            int recLen = (8 + nameBytes.length + 3) & ~3;
            boolean isLast = (++fileIndex == totalFiles);
            if (isLast) {
                recLen = dirBytesRemaining;
            }
            if (recLen > dirBytesRemaining) {
                throw new IllegalStateException("Root directory block full");
            }
            dirOffset = writeDirEntry(buf, dirOffset, inodeNum, name, (byte) 1, recLen);
            dirBytesRemaining -= recLen;
        }

        // Root directory inode (#2)
        writeInode(buf, 2, 0x41ED, BLOCK_SIZE, 2, BLOCK_SIZE / 512, new int[]{ROOT_DIR_BLOCK});

        int usedBlocksInGroup = nextFreeBlock - FIRST_DATA_BLOCK;
        int totalGroupBlocks = TOTAL_BLOCKS - FIRST_DATA_BLOCK;
        int freeBlocks = totalGroupBlocks - usedBlocksInGroup;
        int usedInodes = (FIRST_NON_RESERVED_INODE - 1) + totalFiles;
        int freeInodes = TOTAL_INODES - usedInodes;

        // Block bitmap (bit i = block FIRST_DATA_BLOCK + i)
        int blockBitmapBase = BLOCK_BITMAP * BLOCK_SIZE;
        for (int i = 0; i < usedBlocksInGroup; i++) {
            setBit(image, blockBitmapBase, i);
        }
        for (int i = totalGroupBlocks; i < BLOCK_SIZE * 8; i++) {
            setBit(image, blockBitmapBase, i);
        }

        // Inode bitmap (bit i = inode i + 1)
        int inodeBitmapBase = INODE_BITMAP * BLOCK_SIZE;
        for (int i = 0; i < usedInodes; i++) {
            setBit(image, inodeBitmapBase, i);
        }
        for (int i = TOTAL_INODES; i < BLOCK_SIZE * 8; i++) {
            setBit(image, inodeBitmapBase, i);
        }

        // Block Group Descriptor 0
        int bgBase = BLOCK_BGDT * BLOCK_SIZE;
        buf.putInt(bgBase, BLOCK_BITMAP);
        buf.putInt(bgBase + 4, INODE_BITMAP);
        buf.putInt(bgBase + 8, INODE_TABLE_START);
        buf.putShort(bgBase + 12, (short) freeBlocks);
        buf.putShort(bgBase + 14, (short) freeInodes);
        buf.putShort(bgBase + 16, (short) 1); // bg_used_dirs_count

        // Superblock
        int sbBase = BLOCK_SUPERBLOCK * BLOCK_SIZE;
        buf.putInt(sbBase, TOTAL_INODES);
        buf.putInt(sbBase + 4, TOTAL_BLOCKS);
        buf.putInt(sbBase + 8, 0); // s_r_blocks_count
        buf.putInt(sbBase + 12, freeBlocks);
        buf.putInt(sbBase + 16, freeInodes);
        buf.putInt(sbBase + 20, FIRST_DATA_BLOCK);
        buf.putInt(sbBase + 24, 0); // s_log_block_size (1024)
        buf.putInt(sbBase + 28, 0); // s_log_frag_size
        buf.putInt(sbBase + 32, BLOCK_SIZE * 8);   // s_blocks_per_group (8192, must be multiple of 8)
        buf.putInt(sbBase + 36, BLOCK_SIZE * 8);   // s_frags_per_group (8192)
        buf.putInt(sbBase + 40, TOTAL_INODES);     // s_inodes_per_group
        buf.putShort(sbBase + 54, (short) 0xFFFF); // s_max_mnt_count
        buf.putShort(sbBase + 56, (short) EXT2_SUPER_MAGIC);
        buf.putShort(sbBase + 58, (short) 1); // s_state = EXT2_VALID_FS
        buf.putShort(sbBase + 60, (short) 1); // s_errors = EXT2_ERRORS_CONTINUE
        buf.putInt(sbBase + 76, 1);           // s_rev_level = EXT2_DYNAMIC_REV
        buf.putInt(sbBase + 84, FIRST_NON_RESERVED_INODE);
        buf.putShort(sbBase + 88, (short) INODE_SIZE);
        buf.putInt(sbBase + 96, 0x0002);      // s_feature_incompat = FILETYPE
        // Non-zero s_uuid (16 bytes at offset 104)
        buf.putLong(sbBase + 104, 0x536D617274476C61L);
        buf.putLong(sbBase + 112, 0x737365734F433252L);

        return image;
    }

    private static void writeInode(
            ByteBuffer buf, int inodeNum, int mode, int size, int links, int sectors, int[] blocks) {
        int offset = INODE_TABLE_START * BLOCK_SIZE + (inodeNum - 1) * INODE_SIZE;
        buf.putShort(offset, (short) mode);
        buf.putShort(offset + 2, (short) 0); // uid
        buf.putInt(offset + 4, size);
        buf.putShort(offset + 24, (short) 0); // gid
        buf.putShort(offset + 26, (short) links);
        buf.putInt(offset + 28, sectors);
        for (int i = 0; i < Math.min(blocks.length, MAX_DIRECT_BLOCKS); i++) {
            buf.putInt(offset + 40 + i * 4, blocks[i]);
        }
    }

    private static int writeDirEntry(
            ByteBuffer buf, int offset, int inode, String name, byte fileType, int recLen) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        buf.putInt(offset, inode);
        buf.putShort(offset + 4, (short) recLen);
        buf.put(offset + 6, (byte) nameBytes.length);
        buf.put(offset + 7, fileType);
        for (int i = 0; i < nameBytes.length; i++) {
            buf.put(offset + 8 + i, nameBytes[i]);
        }
        return offset + recLen;
    }

    private static void setBit(byte[] data, int baseOffset, int bitIndex) {
        data[baseOffset + (bitIndex >> 3)] |= (byte) (1 << (bitIndex & 7));
    }
}
