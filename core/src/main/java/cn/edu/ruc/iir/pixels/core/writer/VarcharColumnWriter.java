package cn.edu.ruc.iir.pixels.core.writer;

import cn.edu.ruc.iir.pixels.core.TypeDescription;
import cn.edu.ruc.iir.pixels.core.vector.BytesColumnVector;
import cn.edu.ruc.iir.pixels.core.vector.ColumnVector;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * pixels
 *
 * @author guodong
 */
public class VarcharColumnWriter extends BaseColumnWriter
{
    private final int maxLength;

    public VarcharColumnWriter(TypeDescription schema, int pixelStride)
    {
        super(schema, pixelStride);
        maxLength = schema.getMaxLength();
    }

    @Override
    public int writeBatch(ColumnVector vector, int length, boolean encoding)
    {
        BytesColumnVector columnVector = (BytesColumnVector) vector;
        byte[][] values = columnVector.vector;
        int size = 0;
        for (byte[] v : values) {
            size += Math.min(v.length, maxLength);
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (int i = 0; i < length; i++) {
            curPixelSize++;
            byte[] v = values[i];
            int itemLength = Math.min(v.length, maxLength);
            buffer.put(v, 0, itemLength);
            curPixelPosition += itemLength;
            // todo currently only support urf-8
            pixelStatRecorder.updateString(new String(v, 0, itemLength, Charset.forName("UTF-8")), 1);
            // if current pixel size satisfies the pixel stride, end the current pixel and start a new one
            if (curPixelSize >= pixelStride) {
                newPixel();
            }
        }
        // append buffer of this batch to rowBatchBufferList
        buffer.flip();
        rowBatchBufferList.add(buffer);
        colChunkSize += buffer.limit();
        return buffer.limit();
    }
}