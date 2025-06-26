package com.anode.workflow;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public interface CommonDao {
    public void saveOrUpdate(Serializable id, Object object);

    public void save(Serializable id, Object object);

    public void update(Serializable id, Object object);

    public void saveCollection(Collection objects);

    public void saveOrUpdateCollection(Collection objects);

    public void delete(Serializable id);

    public <T> T get(Class<T> objectClass, Serializable id);

    public <T> List<T> getAll(Class<T> type);

    public <T> T getUniqueItem(Class<T> type, String uniqueKeyName, String uniqueKeyValue);

    /**
     * Recupere l'objet en faisant un lock pessimiste sur la ligne en base (ie
     * select for update)<br> ! A faire dans une transaction qui libere l'objet
     * rapidement
     */
    public <T> T getLocked(Class<T> objectClass, Serializable id);

    /**
     * The method used to increment the value of a counter
     *
     * @param key the key for the counter
     * @return the incremented value of the counter
     */
    public long incrCounter(String key);
}
