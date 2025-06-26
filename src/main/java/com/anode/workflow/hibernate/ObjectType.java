package com.anode.workflow.hibernate;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

public class ObjectType implements UserType<Object> {

    @Override
    public Class<Object> returnedClass() {
        return Object.class; // We are returning Object because it can be one of the 4 types
    }

    @Override
    public boolean equals(Object x, Object y) {
        return x != null && y != null && x.equals(y);
    }

    @Override
    public int hashCode(Object x) {
        return x.hashCode();
    }

    @Override
    public Object deepCopy(Object value) {
        return value; // Return the object as-is since we are using simple types
    }

    @Override
    public boolean isMutable() {
        return true; // The type is mutable, as we are just using a string representation
    }

    @Override
    public Serializable disassemble(Object value) {
        return (Serializable) value;
    }

    @Override
    public Object assemble(Serializable cached, Object owner) {
        return cached;
    }

    @Override
    public Object replace(Object original, Object target, Object owner) {
        return original;
    }

    @Override
    public int getSqlType() {
        return Types.VARCHAR;
    }

    @Override
    public Object nullSafeGet(
            ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        String value = rs.getString(position);

        if (value == null) {
            return null;
        }

        // Here we can return the correct type based on some logic (e.g., prefix, suffix, or a
        // metadata column)
        // For simplicity, we assume it’s a string, boolean, long, or integer based on parsing
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.valueOf(value);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            // ignore
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            // ignore
        }
        return value;
    }

    @Override
    public void nullSafeSet(
            PreparedStatement st, Object value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            st.setNull(index, Types.VARCHAR);
        } else {
            st.setString(index, value.toString());
        }
    }
}
