package org.example.smartglasses.floppy;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Ext2ImageBuilderTest {

    @Test
    void buildsValidExt2ImageWithExactFileContents() {
        String readme = "Hello OC2R!";
        String script = "local devices = require('devices')\nprint('ok')\n";

        byte[] image = new Ext2ImageBuilder()
                .addFile("README.txt", readme)
                .addFile("test.lua", script)
                .build();

        assertEquals(Ext2ImageBuilder.IMAGE_SIZE, image.length);

        ByteBuffer buf = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        int sb = 1024;
        assertEquals(Ext2ImageBuilder.TOTAL_INODES, buf.getInt(sb));
        assertEquals(Ext2ImageBuilder.TOTAL_BLOCKS, buf.getInt(sb + 4));
        assertEquals(0, buf.getInt(sb + 32) % 8);
        assertEquals((short) 0xEF53, buf.getShort(sb + 56));
        assertEquals((short) 128, buf.getShort(sb + 88));

        // Parse root directory block (#13)
        Map<String, Integer> entries = new LinkedHashMap<>();
        int offset = 13 * 1024;
        int end = offset + 1024;
        while (offset < end) {
            int inode = buf.getInt(offset);
            int recLen = Short.toUnsignedInt(buf.getShort(offset + 4));
            int nameLen = Byte.toUnsignedInt(buf.get(offset + 6));
            assertTrue(recLen >= 8 && offset + recLen <= end);
            String name = new String(image, offset + 8, nameLen, StandardCharsets.UTF_8);
            entries.put(name, inode);
            offset += recLen;
        }

        assertEquals(2, entries.get("."));
        assertEquals(2, entries.get(".."));
        assertTrue(entries.containsKey("README.txt"));
        assertTrue(entries.containsKey("test.lua"));

        assertEquals(readme, readFileFromImage(image, buf, entries.get("README.txt")));
        assertEquals(script, readFileFromImage(image, buf, entries.get("test.lua")));
    }

    @Test
    void allFloppyPresetsBuildCleanly() {
        for (TestFloppyManager.FloppyPreset preset : TestFloppyManager.FloppyPreset.values()) {
            byte[] image = TestFloppyManager.buildImage(preset);
            assertEquals(Ext2ImageBuilder.IMAGE_SIZE, image.length);
            ByteBuffer buf = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals((short) 0xEF53, buf.getShort(1024 + 56));
        }
    }

    private static String readFileFromImage(byte[] image, ByteBuffer buf, int inodeNum) {
        int inodeOffset = 5 * 1024 + (inodeNum - 1) * 128;
        int size = buf.getInt(inodeOffset + 4);
        int firstBlock = buf.getInt(inodeOffset + 40);
        assertTrue(firstBlock >= 14);
        return new String(image, firstBlock * 1024, size, StandardCharsets.UTF_8);
    }
}
