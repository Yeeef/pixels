package io.pixelsdb.pixels.cache;

import com.google.common.hash.Hashing;
import org.junit.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.stream.Stream;

public class TestHashCode {

    private int hashcode(byte[] bytes) {
        int var1 = 1;
        int var2 = 0;

//        for(int var3 = bytes.length - 1; var3 >= var2; --var3) {
//            var1 = 31 * var1 + bytes[var3];
//        }

        for(int var3 = 0; var3 < bytes.length; ++var3) {
            var1 = 31 * var1 + bytes[var3];
        }

        return var1;
    }
    @Test
    public void hashOnKeyBuffer() {
        // read the tmp.txt file
        Map<Integer, Integer> hashCodes = new HashMap<>();
        Map<Integer, Integer> hashBuckets = new HashMap<>();
        int dataSize = 512000;
        double loadFactor = 0.5f;
        int tableSize = (int) (((double) dataSize) / loadFactor);
        BufferedReader reader;
        int hashConflictCnt = 0;
        int bucketConflictCnt = 0;
        int total = 0;
        try {
            reader = new BufferedReader(new FileReader(
                    "tmp.txt"));
            String line = reader.readLine();
            while (line != null) {
                String[] keys = line.split(";")[1].split("-");
                long blockId = Long.parseLong(keys[0]);
                short rgId = Short.parseShort(keys[1]);
                short columnId = Short.parseShort(keys[2]);
                ByteBuffer keyBuf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);

                keyBuf.putLong(blockId);
                keyBuf.putShort(rgId);
                keyBuf.putShort(columnId);

                keyBuf.position(0);

//                int hash = keyBuf.hashCode();
                int hash = hashcode(keyBuf.array()) & 0x7fffffff;
//                int hash = Hashing.murmur3_128().hashBytes(keyBuf).asInt() & 0x7fffffff; // better than default hashing
                int bucket = hash % tableSize;
                if (hashCodes.containsKey(hash)) {
                    hashConflictCnt += 1;
                    hashCodes.put(hash, hashCodes.get(hash) + 1);
//                    System.out.println("hash code conflict; hash=" + hash + " " + blockId + " " + rgId + " " + columnId);
                } else {
                    hashCodes.put(hash, 1);
                }

                if (hashBuckets.containsKey(bucket)) {
                    bucketConflictCnt += 1;
                    hashBuckets.put(bucket, hashBuckets.get(bucket) + 1);

//                    System.out.println("hash code conflict; bucket=" + bucket + " " + blockId + " " + rgId + " " + columnId);
                } else {
                    hashBuckets.put(bucket, 1);
                }
                line = reader.readLine();
                total += 1;
            }
            System.out.println(hashConflictCnt + " " + bucketConflictCnt + " " + total);

            System.out.println(hashCodes.values().stream().mapToInt(a -> a).max());
            System.out.println(hashBuckets.values().stream().mapToInt(a -> a).max());

            PrintWriter writer = new PrintWriter("hashCollision.txt", "UTF-8");
            for (Map.Entry<Integer, Integer> entry : hashCodes.entrySet()) {
                writer.println(entry.getValue());
            }
            writer.close();

            PrintWriter writer2 = new PrintWriter("bucketCollision.txt", "UTF-8");
            for (Map.Entry<Integer,Integer> entry : hashBuckets.entrySet()) {
                writer2.println(entry.getValue());
            }
            writer2.close();
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}