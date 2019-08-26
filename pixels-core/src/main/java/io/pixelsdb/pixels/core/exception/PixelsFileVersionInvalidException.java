package io.pixelsdb.pixels.core.exception;

import io.pixelsdb.pixels.core.PixelsVersion;

/**
 * pixels
 *
 * @author guodong
 */
public class PixelsFileVersionInvalidException
        extends PixelsRuntimeException
{
    private static final long serialVersionUID = -6176420971384589629L;

    public PixelsFileVersionInvalidException(int version)
    {
        super(String.format("This is not a valid file version %d for current reader %d",
                version, PixelsVersion.currentVersion().getVersion()));
    }
}