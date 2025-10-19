package com.anode.workflow.storage.db.sql.common.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.anode.tool.service.CommonRepository;
import com.anode.workflow.entities.sla.FutureMilestones;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;

public class FutureMilestonesRepositoryImpl implements CommonRepository<FutureMilestones, Long> {

    private final EntityManager entityManager;

    public FutureMilestonesRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<FutureMilestones> get(Long id) {
        return Optional.ofNullable(entityManager.find(FutureMilestones.class, id));
    }

    @Override
    public <S extends FutureMilestones> S save(S entity) {
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
    public <S extends FutureMilestones> S saveOrUpdate(S entity) {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            if (entity.getHibid() == null || entityManager.find(FutureMilestones.class, entity.getHibid()) == null) {
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
    public <S extends FutureMilestones> S update(S entity) {
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
            FutureMilestones fm = entityManager.find(FutureMilestones.class, id);
            if (fm != null) entityManager.remove(fm);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    @Override
    public <S extends FutureMilestones> List<S> getAll() {
        TypedQuery<FutureMilestones> query = entityManager.createQuery(
                "SELECT f FROM FutureMilestones f", FutureMilestones.class);
        @SuppressWarnings("unchecked")
        List<S> list = (List<S>) query.getResultList();
        return list;
    }

    @Override
    public <S extends FutureMilestones> S getUniqueItem(String key, String value) {
        TypedQuery<FutureMilestones> query = entityManager.createQuery(
                "SELECT f FROM FutureMilestones f WHERE f." + key + " = :val", FutureMilestones.class);
        query.setParameter("val", value);
        List<FutureMilestones> results = query.getResultList();
        return results.isEmpty() ? null : (S) results.get(0);
    }

    @Override
    public <S extends FutureMilestones> S getLocked(Long id) {
        return (S) entityManager.find(FutureMilestones.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public <S extends FutureMilestones> void saveCollection(Collection<S> objects) {
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
    public <S extends FutureMilestones> void saveOrUpdateCollection(Collection<S> objects) {
        if (objects == null || objects.isEmpty()) return;
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            for (S obj : objects) {
                if (obj.getHibid() == null || entityManager.find(FutureMilestones.class, obj.getHibid()) == null) {
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
