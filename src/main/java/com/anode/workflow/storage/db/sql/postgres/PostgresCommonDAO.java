package com.anode.workflow.storage.db.sql.postgres;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.anode.tool.service.CommonService;
import com.anode.tool.service.IdFactory;

import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PostgresCommonDAO implements CommonService{
    
    private Connection connection;

    // -------------------- Utility Methods --------------------

    private String getTableName(Class<?> clazz) {
        Table table = clazz.getAnnotation(Table.class);
        if (table != null && !table.name().isEmpty()) {
            return table.name();
        }
        return clazz.getSimpleName().toLowerCase();
    }

    private List<Field> getPersistentFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Transient.class)) continue; // ignore transient fields
            if (field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Id.class)) {
                field.setAccessible(true);
                fields.add(field);
            }
        }
        return fields;
    }

    private String getColumnName(Field field) {
        if (field.isAnnotationPresent(Column.class)) {
            Column column = field.getAnnotation(Column.class);
            if (!column.name().isEmpty()) return column.name();
        }
        return field.getName();
    }

    private Field getIdField(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException("No @Id field found in class " + clazz.getName());
    }

    private GeneratedValue getGeneratedValue(Field f) {
        return f.getAnnotation(GeneratedValue.class);
    }

    private Object generateId(Field idField) throws Exception {
            GeneratedValue gv = getGeneratedValue(idField);
            if (gv == null) return null;

            GenerationType strategy = gv.strategy();
            String generator = gv.generator();

            switch (strategy) {
                case IDENTITY:
                    // handled by PostgreSQL automatically
                    return null;
                case SEQUENCE:
                    if (generator == null || generator.isEmpty()) {
                        throw new IllegalStateException("Sequence strategy requires @GeneratedValue(generator = 'sequence_name')");
                    }
                    try (PreparedStatement ps = connection.prepareStatement("SELECT nextval(?)")) {
                        ps.setString(1, generator);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) return rs.getObject(1);
                        }
                    }
                    break;
                case TABLE:
                    // simplified table generator (rarely used)
                    String tableName = "id_generator";
                    String pkColumnName = "seq_name";
                    String valueColumnName = "next_val";

                    if (generator == null || generator.isEmpty()) {
                        generator = idField.getDeclaringClass().getSimpleName().toLowerCase() + "_seq";
                    }

                    connection.createStatement().executeUpdate(
                        "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                        pkColumnName + " VARCHAR(255) PRIMARY KEY, " +
                        valueColumnName + " BIGINT NOT NULL)"
                    );

                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO " + tableName + " (" + pkColumnName + ", " + valueColumnName + ") " +
                            "VALUES (?, 1) ON CONFLICT (" + pkColumnName + ") DO UPDATE " +
                            "SET " + valueColumnName + " = " + tableName + "." + valueColumnName + " + 1 RETURNING " + valueColumnName)) {
                        ps.setString(1, generator);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) return rs.getObject(1);
                        }
                    }
                    break;
                case AUTO:
                default:
                    // For PostgreSQL default to SEQUENCE-based generation
                    String autoSeq = (generator == null || generator.isEmpty())
                            ? idField.getDeclaringClass().getSimpleName().toLowerCase() + "_id_seq"
                            : generator;
                    try (PreparedStatement ps = connection.prepareStatement("SELECT nextval(?)")) {
                        ps.setString(1, autoSeq);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) return rs.getObject(1);
                        }
                    }
            }
            return null;
        }


    
    @Override
    public void delete(Serializable arg0) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'delete'");
    }

    @Override
    public void save(Object entity) {
        Class<?> clazz = entity.getClass();
        String tableName = getTableName(clazz);
        Field idField = getIdField(clazz);
        List<Field> fields = getPersistentFields(clazz);

        try {
            Object idValue = idField.get(entity);
            GeneratedValue gv = getGeneratedValue(idField);
            GenerationType strategy = gv != null ? gv.strategy() : null;

            // If ID is null and not identity, pre-generate it
            if (idValue == null && gv != null && strategy != GenerationType.IDENTITY) {
                idValue = generateId(idField);
                if (idValue != null) idField.set(entity, idValue);
            }

            boolean idGeneratedByDB = (strategy == GenerationType.IDENTITY && idValue == null);

            StringBuilder columns = new StringBuilder();
            StringBuilder placeholders = new StringBuilder();

            for (Field f : fields) {
                if (idGeneratedByDB && f.equals(idField)) continue;
                Object value = f.get(entity);
                if (value != null) {
                    if (columns.length() > 0) {
                        columns.append(", ");
                        placeholders.append(", ");
                    }
                    columns.append(getColumnName(f));
                    placeholders.append("?");
                }
            }

            String sql = idGeneratedByDB
                    ? "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ") RETURNING " + getColumnName(idField)
                    : "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";

            log.debug("Executing SQL: {}", sql);

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int index = 1;
                for (Field f : fields) {
                    if (idGeneratedByDB && f.equals(idField)) continue;
                    Object value = f.get(entity);
                    if (value != null) ps.setObject(index++, value);
                }

                if (idGeneratedByDB) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            Object generatedId = rs.getObject(1);
                            idField.set(entity, generatedId);
                            log.debug("Generated ID for {}: {}", clazz.getSimpleName(), generatedId);
                        }
                    }
                } else {
                    ps.executeUpdate();
                }
            }

        } catch (Exception e) {
            log.error("Error saving entity {}", clazz.getSimpleName(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T get(Class<T> clazz, Serializable id) {
        String table = getTableName(clazz);
        Field idField = getIdField(clazz);
        String idCol = getColumnName(idField);

        String sql = "SELECT * FROM " + table + " WHERE " + idCol + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    T instance = clazz.getDeclaredConstructor().newInstance();
                    for (Field f : getPersistentFields(clazz)) {
                        Object val = rs.getObject(getColumnName(f));
                        f.set(instance, val);
                    }
                    return instance;
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving {} with id {}", clazz.getSimpleName(), id, e);
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public void update(Object entity) {
        Class<?> clazz = entity.getClass();
        String table = getTableName(clazz);
        Field idField = getIdField(clazz);
        String idCol = getColumnName(idField);
        List<Field> fields = getPersistentFields(clazz);

        try {
            Object idValue = idField.get(entity);
            if (idValue == null) throw new IllegalArgumentException("Cannot update entity without ID");

            String setClause = fields.stream()
                    .filter(f -> !f.equals(idField))
                    .map(f -> getColumnName(f) + " = ?")
                    .collect(Collectors.joining(", "));

            String sql = "UPDATE " + table + " SET " + setClause + " WHERE " + idCol + " = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int idx = 1;
                for (Field f : fields) {
                    if (f.equals(idField)) continue;
                    ps.setObject(idx++, f.get(entity));
                }
                ps.setObject(idx, idValue);
                ps.executeUpdate();
            }

        } catch (Exception e) {
            log.error("Error updating {}", clazz.getSimpleName(), e);
            throw new RuntimeException(e);
        }
    }


     // -------------------- Helper & Batch Methods --------------------

    @Override
    public void saveCollection(Collection collection) {
        for (Object o : collection) save(o);
    }

    @Override
    public void saveOrUpdate(Object entity) {
        try {
            Field idField = getIdField(entity.getClass());
            Object idVal = idField.get(entity);
            if (idVal == null || get(entity.getClass(), (Serializable) idVal) == null) {
                save(entity);
            } else {
                update(entity);
            }
        } catch (Exception e) {
            log.error("Error saveOrUpdate {}", entity.getClass().getSimpleName(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveOrUpdateCollection(Collection collection) {
        for (Object o : collection) saveOrUpdate(o);
    }

    @Override
    public <T> T getLocked(Class<T> clazz, Serializable id) {
        String table = getTableName(clazz);
        Field idField = getIdField(clazz);
        String idCol = getColumnName(idField);

        String sql = "SELECT * FROM " + table + " WHERE " + idCol + " = ? FOR UPDATE";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    T instance = clazz.getDeclaredConstructor().newInstance();
                    for (Field f : getPersistentFields(clazz)) {
                        f.set(instance, rs.getObject(getColumnName(f)));
                    }
                    return instance;
                }
            }
        } catch (Exception e) {
            log.error("Error getLocked {}", clazz.getSimpleName(), e);
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public <T> List<T> getAll(Class<T> type) {
        String tableName = getTableName(type);
        List<T> result = new ArrayList<>();

        String sql = "SELECT * FROM " + tableName;

        try (PreparedStatement ps = connection.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {

            List<Field> fields = getPersistentFields(type);

            while (rs.next()) {
                T instance = type.getDeclaredConstructor().newInstance();

                for (Field field : fields) {
                    String columnName = getColumnName(field);
                    Object value = rs.getObject(columnName);
                    field.setAccessible(true);
                    field.set(instance, value);
                }

                result.add(instance);
            }

        } catch (Exception e) {
            log.error("Error fetching all entities of {}", type.getSimpleName(), e);
            throw new RuntimeException(e);
        }

        return result;
    }


    @Override
    public <T> T getUniqueItem(Class<T> type, String uniqueKeyName, String uniqueKeyValue) {
        String tableName = getTableName(type);
        String columnName = uniqueKeyName;

        // Try to resolve column name via @Column mapping
        try {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(Transient.class)) continue;
                if (field.getName().equalsIgnoreCase(uniqueKeyName)) {
                    Column col = field.getAnnotation(Column.class);
                    if (col != null && !col.name().isEmpty()) {
                        columnName = col.name();
                    }
                    break;
                }
            }

            String sql = "SELECT * FROM " + tableName + " WHERE " + columnName + " = ? LIMIT 1";
            log.debug("Executing SQL: {}", sql);

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, uniqueKeyValue);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        T instance = type.getDeclaredConstructor().newInstance();
                        for (Field f : getPersistentFields(type)) {
                            Object value = rs.getObject(getColumnName(f));
                            f.setAccessible(true);
                            f.set(instance, value);
                        }
                        return instance;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving unique item of {} where {} = {}", type.getSimpleName(), uniqueKeyName, uniqueKeyValue, e);
            throw new RuntimeException(e);
        }

        return null;
    }

    @Override
    public long incrCounter(String counterName) {
        String table = "counters"; // a simple table to store counters
        try (Statement stmt = connection.createStatement()) {
            // Create table if not exists
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " (" +
                    "counter_name VARCHAR(255) PRIMARY KEY, " +
                    "counter_value BIGINT NOT NULL)"
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create counter table", e);
        }

        long newValue = 0;
        String sql = "INSERT INTO " + table + " (counter_name, counter_value) " +
                    "VALUES (?, 1) " +
                    "ON CONFLICT (counter_name) DO UPDATE SET counter_value = " + table + ".counter_value + 1 " +
                    "RETURNING counter_value";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, counterName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    newValue = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to increment counter " + counterName, e);
        }

        return newValue;
    }

   @Override
    public Map<Serializable, Serializable> makeClone(Object obj, IdFactory idFactory) {
        Map<Serializable, Serializable> idMapping = new HashMap<>();
        Class<?> clazz = obj.getClass();
        try {
            Field idField = getIdField(clazz);
            idField.setAccessible(true);
            Serializable oldId = (Serializable) idField.get(obj);

            // Create new instance
            Object clone = clazz.getDeclaredConstructor().newInstance();

            for (Field field : getPersistentFields(clazz)) {
                field.setAccessible(true);
                if (field.equals(idField)) {
                    // Generate new ID using IdFactory
                    Serializable newId = idFactory.newId();
                    field.set(clone, newId);
                    idFactory.consumeId(newId);
                    idMapping.put(oldId, newId);
                } else {
                    Object value = field.get(obj);
                    field.set(clone, value);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to clone object of class " + clazz.getSimpleName(), e);
        }
        return idMapping;
    }

   @Override
    public Serializable getMinimalId(Comparator<Serializable> comparator) {
        String table = "counters";
        List<Serializable> ids = new ArrayList<>();

        try (Statement stmt = connection.createStatement()) {
            // Ensure the counters table exists
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " (" +
                    "counter_name VARCHAR(255) PRIMARY KEY, " +
                    "counter_value BIGINT NOT NULL)"
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure counters table exists", e);
        }

        String sql = "SELECT counter_value FROM " + table;
        try (PreparedStatement ps = connection.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                ids.add(rs.getLong("counter_value"));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to read counters table", e);
        }

        if (ids.isEmpty()) {
            return null; // no IDs available
        }

        // Use provided comparator to find the smallest (or custom-defined minimal)
        return ids.stream().min(comparator).orElse(null);
    }


}
