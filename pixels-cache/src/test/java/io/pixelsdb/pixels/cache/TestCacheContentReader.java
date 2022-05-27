package io.pixelsdb.pixels.cache;

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TestCacheContentReader {

    // TODO: scan all items, do a sanity check

    @Test
    public void testFileCacheContentReader() throws IOException {
        CacheContentReader reader = new FileCacheContentReader("/scratch/yeeef/pixels-cache/cache_fs");

    }

    @Test
    public void testDiskCacheContentReader() throws IOException, NoSuchFieldException, IllegalAccessException {
        CacheContentReader reader = new DiskCacheContentReader("/scratch/yeeef/pixels-cache/pixels.cache");

        // 40125714472, 4608
        // 4633903504, 4608
        PixelsCacheIdx idx = new PixelsCacheIdx(40125714472L, 4608);
        ByteBuffer buf = ByteBuffer.allocate(idx.length);
        buf.limit(idx.length);
        reader.read(idx, buf);
        System.out.println(StandardCharsets.UTF_8.decode(buf));
        buf.position(0);
        reader.read(new PixelsCacheIdx(4633903504L, 4608), buf);
        System.out.println("shit");
        System.out.println(StandardCharsets.UTF_8.decode(buf));
    }

        @Test
    public void testNativeDiskCacheContentReader() throws IOException, NoSuchFieldException, IllegalAccessException {
//        CacheContentReader reader = new NativeDiskCacheContentReader("/mnt/nvme1n1/pixels.cache");
        CacheContentReader reader = new NativeDiskCacheContentReader("/scratch/yeeef/pixels-cache/pixels.cache");
        //13773413676, 4608
        //
        //30260437256, 1588
        //
        //37003456744, 25605
        //7182082499, 20528
        PixelsCacheIdx idx = new PixelsCacheIdx(13773413676L, 4608);
        ByteBuffer buf = ByteBuffer.allocate(10240);
        buf.limit(4608);
        reader.read(idx, buf);
        System.out.println(StandardCharsets.US_ASCII.decode(buf));
//        reader.read(n);

    }
}
