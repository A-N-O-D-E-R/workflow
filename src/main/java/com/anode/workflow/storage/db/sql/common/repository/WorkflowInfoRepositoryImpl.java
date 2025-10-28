package com.anode.workflow.storage.db.sql.common.repository;

import com.anode.tool.service.CommonRepository;
import com.anode.workflow.entities.workflows.WorkflowInfo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class WorkflowInfoRepositoryImpl implements CommonRepository<WorkflowInfo, Long> {

    private final EntityManager entityManager;

    public WorkflowInfoRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<WorkflowInfo> get(Long id) {
        return Optional.ofNullable(entityManager.find(WorkflowInfo.class, id));
    }

    @Override
    public <S extends WorkflowInfo> S save(S entity) {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            entityManager.persist(entity);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
        return entity;
    }

    @Override
    public <S extends WorkflowInfo> S saveOrUpdate(S entity) {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            if (entity.getHibid() == null
                    || entityManager.find(WorkflowInfo.class, entity.getHibid()) == null) {
                entityManager.persist(entity);
            } else {
                entity = entityManager.merge(entity);
            }
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
        return entity;
    }

    @Override
    public <S extends WorkflowInfo> S update(S entity) {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            S merged = entityManager.merge(entity);
            tx.commit();
            return merged;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    @Override
    public void delete(Long id) {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            WorkflowInfo info = entityManager.find(WorkflowInfo.class, id);
            if (info != null) entityManager.remove(info);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    @Override
    public <S extends WorkflowInfo> List<S> getAll() {
        TypedQuery<WorkflowInfo> query =
                entityManager.createQuery("SELECT w FROM WorkflowInfo w", WorkflowInfo.class);
        @SuppressWarnings("unchecked")
        List<S> result = (List<S>) query.getResultList();
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S extends WorkflowInfo> S getUniqueItem(String key, String value) {
        TypedQuery<WorkflowInfo> query =
                entityManager.createQuery(
                        "SELECT w FROM WorkflowInfo w WHERE w." + key + " = :val",
                        WorkflowInfo.class);
        query.setParameter("val", value);
        List<WorkflowInfo> results = query.getResultList();
        return results.isEmpty() ? null : (S) results.get(0);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S extends WorkflowInfo> S getLocked(Long id) {
        return (S) entityManager.find(WorkflowInfo.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public <S extends WorkflowInfo> void saveCollection(Collection<S> objects) {
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
    public <S extends WorkflowInfo> void saveOrUpdateCollection(Collection<S> objects) {
        if (objects == null || objects.isEmpty()) return;
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            for (S obj : objects) {
                if (obj.getHibid() == null
                        || entityManager.find(WorkflowInfo.class, obj.getHibid()) == null) {
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
