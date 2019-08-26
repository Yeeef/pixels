package io.pixelsdb.pixels.daemon.metadata.dao;

import java.util.List;

public interface Dao<T>
{
    public T getById(long id);

    public List<T> getAll();
}