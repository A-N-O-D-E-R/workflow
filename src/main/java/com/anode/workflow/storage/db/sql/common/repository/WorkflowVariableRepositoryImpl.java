package com.anode.workflow.storage.db.sql.common.repository;

import com.anode.tool.service.CommonRepository;
import com.anode.workflow.entities.workflows.WorkflowVariable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class WorkflowVariableRepositoryImpl implements CommonRepository<WorkflowVariable, Long> {

    private final EntityManager entityManager;

    public WorkflowVariableRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<WorkflowVariable> get(Long id) {
        return Optional.ofNullable(entityManager.find(WorkflowVariable.class, id));
    }

    @Override
    public <S extends WorkflowVariable> S save(S entity) {
        entityManager.getTransaction().begin();
        entityManager.persist(entity);
        entityManager.getTransaction().commit();
        return entity;
    }

    @Override
    public <S extends WorkflowVariable> S saveOrUpdate(S entity) {
        entityManager.getTransaction().begin();
        if (entityManager.find(WorkflowVariable.class, entity.getHibid()) == null) {
            entityManager.persist(entity);
        } else {
            entity = entityManager.merge(entity);
        }
        entityManager.getTransaction().commit();
        return entity;
    }

    @Override
    public <S extends WorkflowVariable> S update(S entity) {
        entityManager.getTransaction().begin();
        S merged = entityManager.merge(entity);
        entityManager.getTransaction().commit();
        return merged;
    }

    @Override
    public void delete(Long id) {
        entityManager.getTransaction().begin();
        WorkflowVariable var = entityManager.find(WorkflowVariable.class, id);
        if (var != null) entityManager.remove(var);
        entityManager.getTransaction().commit();
    }

    @Override
    public <S extends WorkflowVariable> List<S> getAll() {
        TypedQuery<WorkflowVariable> query =
                entityManager.createQuery(
                        "SELECT wv FROM WorkflowVariable wv", WorkflowVariable.class);
        @SuppressWarnings("unchecked")
        List<S> result = (List<S>) query.getResultList();
        return result;
    }

    @Override
    public <S extends WorkflowVariable> S getUniqueItem(String key, String value) {
        TypedQuery<WorkflowVariable> query =
                entityManager.createQuery(
                        "SELECT wv FROM WorkflowVariable wv WHERE wv." + key + " = :val",
                        WorkflowVariable.class);
        query.setParameter("val", value);
        List<WorkflowVariable> results = query.getResultList();
        return results.isEmpty() ? null : (S) results.get(0);
    }

    @Override
    public <S extends WorkflowVariable> S getLocked(Long id) {
        return (S) entityManager.find(WorkflowVariable.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public <S extends WorkflowVariable> void saveCollection(Collection<S> objects) {
        if (objects == null || objects.isEmpty()) return;

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();

        try {
            for (S obj : objects) {
                entityManager.persist(obj);
            }
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    @Override
    public <S extends WorkflowVariable> void saveOrUpdateCollection(Collection<S> objects) {
        if (objects == null || objects.isEmpty()) return;

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();

        try {
            for (S obj : objects) {
                if (obj.getHibid() == null
                        || entityManager.find(WorkflowVariable.class, obj.getHibid()) == null) {
                    entityManager.persist(obj);
                } else {
                    entityManager.merge(obj);
                }
            }
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }
}
