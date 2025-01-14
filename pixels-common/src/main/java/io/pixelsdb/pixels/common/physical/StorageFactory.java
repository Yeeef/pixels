/*
 * Copyright 2021 PixelsDB.
 *
 * This file is part of Pixels.
 *
 * Pixels is free software: you can redistribute it and/or modify
 * it under the terms of the Affero GNU General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Pixels is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Affero GNU General Public License for more details.
 *
 * You should have received a copy of the Affero GNU General Public
 * License along with Pixels.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package io.pixelsdb.pixels.common.physical;

import com.google.common.collect.ImmutableList;
import io.pixelsdb.pixels.common.physical.storage.HDFS;
import io.pixelsdb.pixels.common.physical.storage.LocalFS;
import io.pixelsdb.pixels.common.physical.storage.Mock;
import io.pixelsdb.pixels.common.physical.storage.S3;
import io.pixelsdb.pixels.common.utils.ConfigFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * Created at: 20/08/2021
 * Author: hank
 */
public class StorageFactory
{
    private static Logger logger = LogManager.getLogger(StorageFactory.class);
    private Map<Storage.Scheme, Storage> storageImpls = new HashMap<>();
    private Set<Storage.Scheme> enabledSchemes = new TreeSet<>();

    private StorageFactory()
    {
        String value = ConfigFactory.Instance().getProperty("enabled.storage.schemes");
        requireNonNull(value, "enabled.storage.schemes is not configured");
        String[] schemeNames = value.trim().split(",");
        checkArgument(schemeNames.length > 0,
                "at lease one storage scheme must be enabled");
        for (String name : schemeNames)
        {
            enabledSchemes.add(Storage.Scheme.from(name));
        }
    }

    private static StorageFactory instance = null;

    public static StorageFactory Instance()
    {
        if (instance == null)
        {
            instance = new StorageFactory();
            Runtime.getRuntime().addShutdownHook(new Thread(()->
            {
                try
                {
                    instance.closeAll();
                } catch (IOException e)
                {
                    logger.error("Failed to close all storage instances.", e);
                    e.printStackTrace();
                }
            }));
        }
        return instance;
    }

    public List<Storage.Scheme> getEnabledSchemes()
    {
        return ImmutableList.copyOf(this.enabledSchemes);
    }

    /**
     * Recreate the Storage instances. This is only needed in the Presto connector.
     * @throws IOException
     */
    public synchronized void reload() throws IOException
    {
        this.storageImpls.clear();
        for (Storage.Scheme scheme : enabledSchemes)
        {
            Storage storage = this.getStorage(scheme);
            requireNonNull(storage, "failed to create Storage instance");
        }
    }
    /**
     * Get the storage instance from a scheme name or a scheme prefixed path.
     * @param schemeOrPath
     * @return
     * @throws IOException
     */
    public synchronized Storage getStorage(String schemeOrPath) throws IOException
    {
        try
        {
            // 'synchronized' in Java is reentrant, it is fine the call the other getStorage().
            if (schemeOrPath.contains("://"))
            {
                return getStorage(Storage.Scheme.fromPath(schemeOrPath));
            }
            else
            {
                return getStorage(Storage.Scheme.from(schemeOrPath));
            }
        }
        catch (RuntimeException re)
        {
            throw new IOException("Invalid storage scheme or path: " + schemeOrPath, re);
        }
    }

    public synchronized Storage getStorage(Storage.Scheme scheme) throws IOException
    {
        checkArgument(this.enabledSchemes.contains(scheme), "storage scheme '" +
                scheme.toString() + "' is not enabled.");
        if (storageImpls.containsKey(scheme))
        {
            return storageImpls.get(scheme);
        }

        Storage storage = null;
        if (scheme == Storage.Scheme.hdfs)
        {
            storage = new HDFS();
        }
        else if (scheme == Storage.Scheme.s3)
        {
            storage = new S3();
        }
        else if (scheme == Storage.Scheme.file)
        {
            storage = new LocalFS();
        }
        else if (scheme == Storage.Scheme.mock)
        {
            storage = new Mock();
        }
        if (storage != null)
        {
            storageImpls.put(scheme, storage);
        }

        return storage;
    }

    public void closeAll() throws IOException
    {
        for (Storage.Scheme scheme : storageImpls.keySet())
        {
            storageImpls.get(scheme).close();
        }
    }
}
