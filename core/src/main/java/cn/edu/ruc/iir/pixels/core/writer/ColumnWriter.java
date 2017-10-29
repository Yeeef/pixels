package cn.edu.ruc.iir.pixels.core.writer;

import cn.edu.ruc.iir.pixels.core.PixelsProto;
import cn.edu.ruc.iir.pixels.core.stats.StatsRecorder;
import cn.edu.ruc.iir.pixels.core.vector.ColumnVector;

import java.io.IOException;

/**
 * pixels
 *
 * @author guodong
 */
public interface ColumnWriter
{
    int writeBatch(ColumnVector vector, int length) throws IOException;
    byte[] getColumnChunkContent();
    int getColumnChunkSize();
    PixelsProto.ColumnChunkIndex.Builder getColumnChunkIndex();
    PixelsProto.ColumnStatistic.Builder getColumnChunkStat();
    StatsRecorder getColumnChunkStatRecorder();
    void newChunk();
    void reset();
}