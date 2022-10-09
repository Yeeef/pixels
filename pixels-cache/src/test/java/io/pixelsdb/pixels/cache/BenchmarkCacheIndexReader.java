package io.pixelsdb.pixels.cache;
import io.pixelsdb.pixels.common.utils.ConfigFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

public class BenchmarkCacheIndexReader {
    static int KEYS = 512000;
    static int READ_COUNT = KEYS * 100;
    int threadNum = 1;
    // it can be generated by RadixTreeDumper
    String dumpedCacheIndexFileName = "dumpedCache.txt";
    String bigEndianRadixFileName = "/dev/shm/pixels.index";
    String littleEndianRadixFileName = "/dev/shm/pixels.index.little";
    String hashIndexFileName = "/dev/shm/pixels.hash-index";

    MemoryMappedFile bigEndianIndexFile;
    MemoryMappedFile littleEndianIndexFile;
    MemoryMappedFile hashIndexFile;

    PixelsCacheKey[] pixelsCacheKeys = new PixelsCacheKey[KEYS];

    @Before
    public void init() {
        try {
            Configurator.setRootLevel(Level.DEBUG);
            bigEndianIndexFile = new MemoryMappedFile(bigEndianRadixFileName, 102400000);
            littleEndianIndexFile = new MemoryMappedFile(littleEndianRadixFileName, 102400000);
//            hashIndexFile = new MemoryMappedFile("/dev/shm/pixels.hash-index", 102400000);
            hashIndexFile = new MemoryMappedFile(hashIndexFileName, 102400000);

            BufferedReader br = new BufferedReader(new FileReader(dumpedCacheIndexFileName));
            String line = br.readLine();
            int ptr = 0;
            while (line != null) {
                line = line.split(";")[1];
                String[] tokens = line.split("-");
                long blockId = Long.parseLong(tokens[0]);
                short rowGroupId = Short.parseShort(tokens[1]);
                short columnId = Short.parseShort(tokens[2]);
                pixelsCacheKeys[ptr] = new PixelsCacheKey(blockId, rowGroupId, columnId);
                ptr += 1;
                line = br.readLine();
            }
            System.out.println(Arrays.toString(Arrays.copyOfRange(pixelsCacheKeys, 0, 10)));

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    static class BenchmarkResult {
        double elapsed;
        double totalIO;
        double iops;
        double latency;
        long dramAccess;

        BenchmarkResult(double totalIO, double elapsedInMili, long dramAccess) {
            this.elapsed = elapsedInMili;
            this.totalIO = totalIO;
            this.iops = totalIO / (elapsed / 1e3);
            this.latency = 1.0 / this.iops * 1000;
            this.dramAccess = dramAccess;
        }

        @Override
        public String toString() {
            return String.format("elapsed=%fms(%fs), IOPS=%f, latency=%fms, totalIO=%f, ramAccessPerKey=%f",
                    elapsed, elapsed / 1e3, iops, latency, totalIO, dramAccess / (double) totalIO);
        }

    }

    void benchmarkIndexReader(int threadNum, Supplier<CacheIndexReader> factory) throws ExecutionException, InterruptedException {
        Random random = new Random(233);
        List<Future<BenchmarkResult>> futures = new ArrayList<>();
        for (int i = 0; i < threadNum; i++)
        {
            Future<BenchmarkResult> future = Executors.newSingleThreadExecutor().submit(() -> {
                int[] accesses = new int[READ_COUNT];
                for (int k = 0; k < READ_COUNT; k++)
                {
                    accesses[k] = random.nextInt(READ_COUNT) % KEYS;
                }
                CacheIndexReader reader = factory.get();
                long searchStart = System.nanoTime();
                long totalRamAccess = 0;
                for (int access : accesses) {
                    PixelsCacheKey cacheKey = pixelsCacheKeys[access];
                    PixelsCacheIdx idx = reader.read(cacheKey.blockId,
                            cacheKey.rowGroupId,
                            cacheKey.columnId);
                    if (idx == null) {
                        System.out.println("[error] cannot find " + cacheKey.blockId
                                + "-" + cacheKey.rowGroupId
                                + "-" + cacheKey.columnId);
                    } else {
                        totalRamAccess += idx.dramAccessCount;
                    }
                }
                long searchEnd = System.nanoTime();
                BenchmarkResult result = new BenchmarkResult(accesses.length,
                        (searchEnd - searchStart) / ((double) 1e6), totalRamAccess);
                System.out.println(result);
                return result;

            });
            futures.add(future);
        }
        List<BenchmarkResult> results = new ArrayList<>(threadNum);
        for (int i = 0; i < threadNum; ++i) {
            results.add(futures.get(i).get());
        }
        double totalIOPS = 0.0;

        double averageLatency = 0.0;
        for (BenchmarkResult res : results) {
            totalIOPS += res.iops;
            averageLatency += res.latency;
        }
        averageLatency /= threadNum;
        System.out.println(String.format("threads=%d, totalIOPS=%f, latency=%fms", threadNum, totalIOPS, averageLatency));
    }

    @Test
    public void benchmarkHash() throws ExecutionException, InterruptedException {
        benchmarkIndexReader(threadNum, () -> {
            return new HashIndexReader(hashIndexFile);
        });
    }

    @Test
    public void benchmarkNativeHash() throws ExecutionException, InterruptedException {
        benchmarkIndexReader(threadNum, () -> {
            return new NativeHashIndexReader(hashIndexFile);
        });
    }

    @Test // naive search without protocol
    public void benchmarkPartitionedHash() throws Exception {
        ConfigFactory config = ConfigFactory.Instance();
        // disk cache
//        config.addProperty("cache.location", "/scratch/yeeef/pixels-cache/partitioned/pixels.cache");
        config.addProperty("cache.location", "/mnt/nvme1n1/partitioned/pixels.cache");

        config.addProperty("cache.size", String.valueOf(70 * 1024 * 1024 * 1024L)); // 70GiB
        config.addProperty("cache.partitions", "32");


        config.addProperty("index.location", "/dev/shm/pixels-partitioned-cache/pixels.hash-index");
//        config.addProperty("index.disk.location", "/scratch/yeeef/pixels-cache/partitioned/pixels.hash-index");
        config.addProperty("index.disk.location", "/mnt/nvme1n1/partitioned/pixels.hash-index");

        config.addProperty("index.size", String.valueOf(100 * 1024 * 1024)); // 100 MiB

        config.addProperty("cache.storage.scheme", "mock"); // 100 MiB
        config.addProperty("cache.schema", "pixels");
        config.addProperty("cache.table", "test_mock");
        config.addProperty("lease.ttl.seconds", "20");
        config.addProperty("heartbeat.period.seconds", "10");
        config.addProperty("enable.absolute.balancer", "false");
        config.addProperty("cache.enabled", "true");
        config.addProperty("enabled.storage.schemes", "mock");
        PixelsCacheConfig cacheConfig = new PixelsCacheConfig();

        long realIndexSize = cacheConfig.getIndexSize() / (cacheConfig.getPartitions()) * (cacheConfig.getPartitions() + 1) + PixelsCacheUtil.PARTITION_INDEX_META_SIZE;
        long realCacheSize = cacheConfig.getCacheSize() / (cacheConfig.getPartitions()) * (cacheConfig.getPartitions() + 1) + PixelsCacheUtil.CACHE_DATA_OFFSET;

        MemoryMappedFile indexFile = new MemoryMappedFile(cacheConfig.getIndexLocation(), realIndexSize);
        MemoryMappedFile cacheFile = new MemoryMappedFile(cacheConfig.getCacheLocation(), realCacheSize);

        benchmarkIndexReader(threadNum, () -> {
            PartitionCacheReader reader = PartitionCacheReader.newBuilder().setIndexFile(indexFile).setCacheFile(cacheFile)
                    .setIndexType("hash")
                    .build();
            return new CacheIndexReader() {
                @Override
                public PixelsCacheIdx read(PixelsCacheKey key) {
                    return reader.naivesearch(key);
                }
            };
        });
    }

    @Test // optimized protocol without readercount
    public void benchmarkProtocolPartitionHash() throws Exception {
        ConfigFactory config = ConfigFactory.Instance();
        // disk cache
//        config.addProperty("cache.location", "/scratch/yeeef/pixels-cache/partitioned/pixels.cache");
        config.addProperty("cache.location", "/mnt/nvme1n1/partitioned/pixels.cache");

        config.addProperty("cache.size", String.valueOf(70 * 1024 * 1024 * 1024L)); // 70GiB
        config.addProperty("cache.partitions", "32");


        config.addProperty("index.location", "/dev/shm/pixels-partitioned-cache/pixels.hash-index");
//        config.addProperty("index.disk.location", "/scratch/yeeef/pixels-cache/partitioned/pixels.hash-index");
        config.addProperty("index.disk.location", "/mnt/nvme1n1/partitioned/pixels.hash-index");

        config.addProperty("index.size", String.valueOf(100 * 1024 * 1024)); // 100 MiB

        config.addProperty("cache.storage.scheme", "mock"); // 100 MiB
        config.addProperty("cache.schema", "pixels");
        config.addProperty("cache.table", "test_mock");
        config.addProperty("lease.ttl.seconds", "20");
        config.addProperty("heartbeat.period.seconds", "10");
        config.addProperty("enable.absolute.balancer", "false");
        config.addProperty("cache.enabled", "true");
        config.addProperty("enabled.storage.schemes", "mock");
        PixelsCacheConfig cacheConfig = new PixelsCacheConfig();

        long realIndexSize = cacheConfig.getIndexSize() / (cacheConfig.getPartitions()) * (cacheConfig.getPartitions() + 1) + PixelsCacheUtil.PARTITION_INDEX_META_SIZE;
        long realCacheSize = cacheConfig.getCacheSize() / (cacheConfig.getPartitions()) * (cacheConfig.getPartitions() + 1) + PixelsCacheUtil.CACHE_DATA_OFFSET;

        MemoryMappedFile indexFile = new MemoryMappedFile(cacheConfig.getIndexLocation(), realIndexSize);
        MemoryMappedFile cacheFile = new MemoryMappedFile(cacheConfig.getCacheLocation(), realCacheSize);

        benchmarkIndexReader(threadNum, () -> {
            PartitionCacheReader reader = PartitionCacheReader.newBuilder().setIndexFile(indexFile).setCacheFile(cacheFile)
                    .setIndexType("hash")
                    .build();
            return new CacheIndexReader() {
                @Override
                public PixelsCacheIdx read(PixelsCacheKey key) {
                    return reader.search(key);
                }
            };
//            return new CacheReaderAdaptor(reader);
        });

    }

    @Test // old protocol with readercount
    public void benchmarkProtocolPartitionWithReaderCntHash() throws Exception {
        ConfigFactory config = ConfigFactory.Instance();
        // disk cache
//        config.addProperty("cache.location", "/scratch/yeeef/pixels-cache/partitioned/pixels.cache");
        config.addProperty("cache.location", "/mnt/nvme1n1/partitioned/pixels.cache");

        config.addProperty("cache.size", String.valueOf(70 * 1024 * 1024 * 1024L)); // 70GiB
        config.addProperty("cache.partitions", "32");


        config.addProperty("index.location", "/dev/shm/pixels-partitioned-cache/pixels.hash-index");
//        config.addProperty("index.disk.location", "/scratch/yeeef/pixels-cache/partitioned/pixels.hash-index");
        config.addProperty("index.disk.location", "/mnt/nvme1n1/partitioned/pixels.hash-index");

        config.addProperty("index.size", String.valueOf(100 * 1024 * 1024)); // 100 MiB

        config.addProperty("cache.storage.scheme", "mock"); // 100 MiB
        config.addProperty("cache.schema", "pixels");
        config.addProperty("cache.table", "test_mock");
        config.addProperty("lease.ttl.seconds", "20");
        config.addProperty("heartbeat.period.seconds", "10");
        config.addProperty("enable.absolute.balancer", "false");
        config.addProperty("cache.enabled", "true");
        config.addProperty("enabled.storage.schemes", "mock");
        PixelsCacheConfig cacheConfig = new PixelsCacheConfig();

        long realIndexSize = cacheConfig.getIndexSize() / (cacheConfig.getPartitions()) * (cacheConfig.getPartitions() + 1) + PixelsCacheUtil.PARTITION_INDEX_META_SIZE;
        long realCacheSize = cacheConfig.getCacheSize() / (cacheConfig.getPartitions()) * (cacheConfig.getPartitions() + 1) + PixelsCacheUtil.CACHE_DATA_OFFSET;

        MemoryMappedFile indexFile = new MemoryMappedFile(cacheConfig.getIndexLocation(), realIndexSize);
        MemoryMappedFile cacheFile = new MemoryMappedFile(cacheConfig.getCacheLocation(), realCacheSize);

        benchmarkIndexReader(threadNum, () -> {
            PartitionCacheReader reader = PartitionCacheReader.newBuilder().setIndexFile(indexFile).setCacheFile(cacheFile)
                    .setIndexType("hash")
                    .build();
            return new CacheIndexReader() {
                @Override
                public PixelsCacheIdx read(PixelsCacheKey key) {
                    return reader.complexsearch(key);
                }
            };
//            return new CacheReaderAdaptor(reader);
        });

    }

    @Test
    public void benchmarkRadixTree() throws ExecutionException, InterruptedException {
        benchmarkIndexReader(threadNum, () -> {
            return new RadixIndexReader(bigEndianIndexFile);
        });
    }

    @Test
    public void benchmarkNativeRadixTree() throws ExecutionException, InterruptedException {
        benchmarkIndexReader(threadNum, () -> {
            return new NativeRadixIndexReader(littleEndianIndexFile);
        });
    }


    @Test
    public void benchmarkPartitionedRadixTree() throws Exception {
        ConfigFactory config = ConfigFactory.Instance();
        // disk cache
//        config.addProperty("cache.location", "/scratch/yeeef/pixels-cache/partitioned/pixels.cache");
        config.addProperty("cache.location", "/mnt/nvme1n1/partitioned/pixels.cache");

        config.addProperty("cache.size", String.valueOf(70 * 1024 * 1024 * 1024L)); // 70GiB
        config.addProperty("cache.partitions", "32");


        config.addProperty("index.location", "/dev/shm/pixels-partitioned-cache/pixels.index");
//        config.addProperty("index.disk.location", "/scratch/yeeef/pixels-cache/partitioned/pixels.index");
        config.addProperty("index.disk.location", "/mnt/nvme1n1/partitioned/pixels.index");

        config.addProperty("index.size", String.valueOf(100 * 1024 * 1024)); // 100 MiB

        config.addProperty("cache.storage.scheme", "mock"); // 100 MiB
        config.addProperty("cache.schema", "pixels");
        config.addProperty("cache.table", "test_mock");
        config.addProperty("lease.ttl.seconds", "20");
        config.addProperty("heartbeat.period.seconds", "10");
        config.addProperty("enable.absolute.balancer", "false");
        config.addProperty("cache.enabled", "true");
        config.addProperty("enabled.storage.schemes", "mock");
        PixelsCacheConfig cacheConfig = new PixelsCacheConfig();

        long realIndexSize = cacheConfig.getIndexSize() / (cacheConfig.getPartitions()) * (cacheConfig.getPartitions() + 1) + PixelsCacheUtil.PARTITION_INDEX_META_SIZE;
        long realCacheSize = cacheConfig.getCacheSize() / (cacheConfig.getPartitions()) * (cacheConfig.getPartitions() + 1) + PixelsCacheUtil.CACHE_DATA_OFFSET;

        MemoryMappedFile indexFile = new MemoryMappedFile(cacheConfig.getIndexLocation(), realIndexSize);
        MemoryMappedFile cacheFile = new MemoryMappedFile(cacheConfig.getCacheLocation(), realCacheSize);

        benchmarkIndexReader(threadNum, () -> {
            PartitionCacheReader reader = PartitionCacheReader.newBuilder().setIndexFile(indexFile).setCacheFile(cacheFile)
                    .setCacheIndexReader(RadixIndexReader::new)
                    .build();
            return new CacheIndexReader() {
                @Override
                public PixelsCacheIdx read(PixelsCacheKey key) {
                    return reader.naivesearch(key);
                }
            };
        });
    }

    @Test
    public void benchmarkPartitionedWithReaderCntRadixTree() throws Exception {
        ConfigFactory config = ConfigFactory.Instance();
        // disk cache
//        config.addProperty("cache.location", "/scratch/yeeef/pixels-cache/partitioned/pixels.cache");
        config.addProperty("cache.location", "/mnt/nvme1n1/partitioned/pixels.cache");

        config.addProperty("cache.size", String.valueOf(70 * 1024 * 1024 * 1024L)); // 70GiB
        config.addProperty("cache.partitions", "32");


        config.addProperty("index.location", "/dev/shm/pixels-partitioned-cache/pixels.index");
//        config.addProperty("index.disk.location", "/scratch/yeeef/pixels-cache/partitioned/pixels.index");
        config.addProperty("index.disk.location", "/mnt/nvme1n1/partitioned/pixels.index");

        config.addProperty("index.size", String.valueOf(100 * 1024 * 1024)); // 100 MiB

        config.addProperty("cache.storage.scheme", "mock"); // 100 MiB
        config.addProperty("cache.schema", "pixels");
        config.addProperty("cache.table", "test_mock");
        config.addProperty("lease.ttl.seconds", "20");
        config.addProperty("heartbeat.period.seconds", "10");
        config.addProperty("enable.absolute.balancer", "false");
        config.addProperty("cache.enabled", "true");
        config.addProperty("enabled.storage.schemes", "mock");
        PixelsCacheConfig cacheConfig = new PixelsCacheConfig();

        long realIndexSize = cacheConfig.getIndexSize() / (cacheConfig.getPartitions()) * (cacheConfig.getPartitions() + 1) + PixelsCacheUtil.PARTITION_INDEX_META_SIZE;
        long realCacheSize = cacheConfig.getCacheSize() / (cacheConfig.getPartitions()) * (cacheConfig.getPartitions() + 1) + PixelsCacheUtil.CACHE_DATA_OFFSET;

        MemoryMappedFile indexFile = new MemoryMappedFile(cacheConfig.getIndexLocation(), realIndexSize);
        MemoryMappedFile cacheFile = new MemoryMappedFile(cacheConfig.getCacheLocation(), realCacheSize);

        benchmarkIndexReader(threadNum, () -> {
            PartitionCacheReader reader = PartitionCacheReader.newBuilder().setIndexFile(indexFile).setCacheFile(cacheFile)
                    .setCacheIndexReader(RadixIndexReader::new)
                    .build();
            return new CacheIndexReader() {
                @Override
                public PixelsCacheIdx read(PixelsCacheKey key) {
                    return reader.complexsearch(key);
                }
            };
        });
    }


    @Test
    public void benchmarkProtocolPartitionRadixTree() throws Exception {
        ConfigFactory config = ConfigFactory.Instance();
        // disk cache
        config.addProperty("cache.location", "/mnt/nvme1n1/partitioned/pixels.cache");
        config.addProperty("cache.size", String.valueOf(70 * 1024 * 1024 * 1024L)); // 70GiB
        config.addProperty("cache.partitions", "32");


        config.addProperty("index.location", "/dev/shm/pixels-partitioned-cache/pixels.index");
        config.addProperty("index.disk.location", "/mnt/nvme1n1/partitioned/pixels.index");
        config.addProperty("index.size", String.valueOf(100 * 1024 * 1024)); // 100 MiB

        config.addProperty("cache.storage.scheme", "mock"); // 100 MiB
        config.addProperty("cache.schema", "pixels");
        config.addProperty("cache.table", "test_mock");
        config.addProperty("lease.ttl.seconds", "20");
        config.addProperty("heartbeat.period.seconds", "10");
        config.addProperty("enable.absolute.balancer", "false");
        config.addProperty("cache.enabled", "true");
        config.addProperty("enabled.storage.schemes", "mock");
        PixelsCacheConfig cacheConfig = new PixelsCacheConfig();

        long realIndexSize = cacheConfig.getIndexSize() / (cacheConfig.getPartitions()) * (cacheConfig.getPartitions() + 1) + PixelsCacheUtil.PARTITION_INDEX_META_SIZE;
        long realCacheSize = cacheConfig.getCacheSize() / (cacheConfig.getPartitions()) * (cacheConfig.getPartitions() + 1) + PixelsCacheUtil.CACHE_DATA_OFFSET;

        MemoryMappedFile indexFile = new MemoryMappedFile(cacheConfig.getIndexLocation(), realIndexSize);
        MemoryMappedFile cacheFile = new MemoryMappedFile(cacheConfig.getCacheLocation(), realCacheSize);

        benchmarkIndexReader(threadNum, () -> {
            PartitionCacheReader reader = PartitionCacheReader.newBuilder().setIndexFile(indexFile).setCacheFile(cacheFile)
                    .setCacheIndexReader(RadixIndexReader::new)
                    .build();
            return new CacheIndexReader() {
                @Override
                public PixelsCacheIdx read(PixelsCacheKey key) {
                    return reader.search(key);
                }
            };
        });

    }

}
