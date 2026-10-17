package com.anode.workflow.test_durable;

import com.anode.tool.service.CommonService;
import com.anode.workflow.storage.WorkflowJson;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable-like DAO for tests: every object is stored as JSON text only, so reading a key back always
 * goes through deserialization, exactly like a database-backed store after a restart.
 */
public class JsonDao implements CommonService {

    private final Map<Serializable, String> rows;
    private final Map<String, Long> counters = new ConcurrentHashMap<>();

    public JsonDao(Map<Serializable, String> rows) {
        this.rows = rows;
    }

    @Override
    public void saveOrUpdate(Serializable id, Object object) {
        rows.put(id, WorkflowJson.write(object));
    }

    @Override
    public void save(Serializable id, Object object) {
        rows.putIfAbsent(id, WorkflowJson.write(object));
    }

    @Override
    public void update(Serializable id, Object object) {
        rows.computeIfPresent(id, (k, v) -> WorkflowJson.write(object));
    }

    @Override
    public <T> T get(Class<T> type, Serializable id) {
        String json = rows.get(id);
        return json == null ? null : WorkflowJson.read(json, type);
    }

    @Override
    public long incrCounter(String key) {
        return counters.merge(key, 1L, Long::sum);
    }

    @Override
    public void delete(Serializable id) {
        rows.remove(id);
    }

    @Override
    public <T> T getLocked(Class<T> type, Serializable id) {
        return get(type, id);
    }

    @Override
    public void saveCollection(Collection objects) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveOrUpdateCollection(Collection objects) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List getAll(Class type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<Serializable, Serializable> makeClone(Object object, com.anode.tool.service.IdFactory idFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Serializable getMinimalId(java.util.Comparator<Serializable> comparator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getUniqueItem(Class<T> type, String uniqueKeyName, String uniqueKeyValue) {
        throw new UnsupportedOperationException();
    }
}
