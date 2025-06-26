package com.anode.workflow;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class MemoryDao implements CommonDao {

    private Map<Serializable, Long> counters = new HashMap<>();
    private Map<Serializable, Boolean> lockedObjects = new HashMap<>();
    private Map<Serializable, Object> documents = new HashMap<>();

    @Override
    public synchronized long incrCounter(String key) {
        Long val = counters.get(key);
        if (val == null) {
            val = 0L;
            counters.put(key, val);
        } else {
            val = val + 1;
            counters.put(key, val);
        }
        return val;
    }

    public Map<Serializable, Object> getDocumentMap() {
        return documents;
    }

    @Override
    public void saveOrUpdate(Serializable id, Object object) {
        documents.put(id, object);
    }

    @Override
    public void save(Serializable id, Object object) {
        documents.putIfAbsent(id, object);
    }

    @Override
    public void update(Serializable id, Object object) {
        if (Objects.nonNull(documents.get(id))) {
            documents.put(id, object);
        }
    }

    @Override
    public void saveCollection(Collection objects) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'saveCollection'");
    }

    @Override
    public void saveOrUpdateCollection(Collection objects) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'saveOrUpdateCollection'");
    }

    @Override
    public void delete(Serializable id) {
        documents.remove(id);
    }

    @Override
    public <T> T get(Class<T> objectClass, Serializable id) {
        return objectClass.cast(documents.get(id));
    }

    @Override
    public List getAll(Class type) {
        return documents.values().stream()
                .filter(obj -> type.isAssignableFrom(obj.getClass()))
                .collect(Collectors.toList());
    }

    @Override
    public <T> T getUniqueItem(Class<T> type, String uniqueKeyName, String uniqueKeyValue) {
        return documents.values().stream()
                .filter(obj -> type.isAssignableFrom(obj.getClass())) // Check the type
                .map(obj -> type.cast(obj))
                .filter(
                        obj -> {
                            try {
                                // Check if the object has the field
                                obj.getClass().getDeclaredField(uniqueKeyName);
                                return true;
                            } catch (NoSuchFieldException e) {
                                return false;
                            }
                        })
                .filter(
                        obj -> {
                            try {
                                // Compare the field's value to the uniqueKeyValue
                                return uniqueKeyValue.equals(
                                        obj.getClass().getDeclaredField(uniqueKeyName).get(obj));
                            } catch (IllegalArgumentException
                                    | IllegalAccessException
                                    | NoSuchFieldException e) {
                                return false;
                            }
                        })
                .findFirst() // Return the first match (or Optional.empty() if not found)
                .orElse(null); // Return null if no match is found
    }

    @Override
    public <T> T getLocked(Class<T> objectClass, Serializable id) {
        // Check if the object exists in the storage
        if (!documents.containsKey(id)) {
            throw new IllegalArgumentException("Object with the given ID does not exist.");
        }

        // Retrieve the object from the storage
        Object object = documents.get(id);

        // Check if the object is locked
        if (lockedObjects.getOrDefault(id, false)) {
            throw new IllegalStateException("Object is already locked.");
        }

        // "Lock" the object
        lockedObjects.put(id, true);

        // Ensure the object is of the requested class type
        if (!objectClass.isInstance(object)) {
            throw new ClassCastException("Object is not of the expected type.");
        }

        // Return the object after it has been "locked"
        return objectClass.cast(object);
    }

    public void releaseLock(Serializable id) {
        // Release the lock on the object
        lockedObjects.put(id, false);
    }
}
