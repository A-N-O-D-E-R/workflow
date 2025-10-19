package com.anode.workflow.storage.db.sql.common.repository;

import com.anode.tool.service.CommonRepository;
import com.anode.tool.service.IdFactory;
import com.anode.workflow.entities.steps.Step;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.*;

public class StepRepositoryImpl implements CommonRepository<Step, Long> {

    private final EntityManager entityManager;
    private final Class<? extends Step> type; // The subclass to work with

    /**
     * @param entityManager The JPA EntityManager
     * @param type          Subclass of Step (e.g., Task.class, DecisionStep.class)
     */
    public StepRepositoryImpl(EntityManager entityManager, Class<? extends Step> type) {
        this.entityManager = entityManager;
        this.type = type;
    }

    @Override
    public Optional<Step> get(Long id) {
        return Optional.ofNullable(entityManager.find(type, id));
    }

    @Override
    public <S extends Step> S save(S entity) {
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
    public <S extends Step> S saveOrUpdate(S entity) {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            if (entity.getHibid() == null || entityManager.find(type, entity.getHibid()) == null) {
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
    public <S extends Step> S update(S entity) {
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
            Step step = entityManager.find(type, id);
            if (step != null) entityManager.remove(step);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    @Override
    public <S extends Step> List<S> getAll() {
        TypedQuery<? extends Step> query = entityManager.createQuery("SELECT s FROM " + type.getSimpleName() + " s", type);
        @SuppressWarnings("unchecked")
        List<S> list = (List<S>) query.getResultList();
        return list;
    }

    @Override
    public <S extends Step> S getUniqueItem(String key, String value) {
        TypedQuery<? extends Step> query = entityManager.createQuery(
                "SELECT s FROM " + type.getSimpleName() + " s WHERE s." + key + " = :val", type);
        query.setParameter("val", value);
        List<? extends Step> list = query.getResultList();
        return list.isEmpty() ? null : (S) list.get(0);
    }

    @Override
    public <S extends Step> S getLocked(Long id) {
        return (S) entityManager.find(type, id, LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public <S extends Step> void saveCollection(Collection<S> objects) {
        if (objects == null || objects.isEmpty()) return;
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            for (S obj : objects) entityManager.persist(obj);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    @Override
    public <S extends Step> void saveOrUpdateCollection(Collection<S> objects) {
        if (objects == null || objects.isEmpty()) return;
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            for (S obj : objects) {
                if (obj.getHibid() == null || entityManager.find(type, obj.getHibid()) == null) {
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
