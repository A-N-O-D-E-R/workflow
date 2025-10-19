package com.anode.workflow.storage.db.sql.common.repository;

import com.anode.tool.service.CommonRepository;
import com.anode.workflow.entities.sla.Milestone;

import jakarta.persistence.*;

import java.util.*;

public class MilestoneRepositoryImpl implements CommonRepository<Milestone, Long> {

    private final EntityManager entityManager;

    public MilestoneRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Milestone> get(Long id) {
        return Optional.ofNullable(entityManager.find(Milestone.class, id));
    }

    @Override
    public <S extends Milestone> S save(S entity) {
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
    public <S extends Milestone> S saveOrUpdate(S entity) {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            if (entity.getHibid() == null || entityManager.find(Milestone.class, entity.getHibid()) == null) {
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
    public <S extends Milestone> S update(S entity) {
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
            Milestone m = entityManager.find(Milestone.class, id);
            if (m != null) entityManager.remove(m);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    @Override
    public <S extends Milestone> List<S> getAll() {
        TypedQuery<Milestone> query = entityManager.createQuery("SELECT m FROM Milestone m", Milestone.class);
        @SuppressWarnings("unchecked")
        List<S> result = (List<S>) query.getResultList();
        return result;
    }

    @Override
    public <S extends Milestone> S getUniqueItem(String key, String value) {
        TypedQuery<Milestone> query = entityManager.createQuery(
                "SELECT m FROM Milestone m WHERE m." + key + " = :val", Milestone.class);
        query.setParameter("val", value);
        List<Milestone> results = query.getResultList();
        return results.isEmpty() ? null : (S) results.get(0);
    }

    @Override
    public <S extends Milestone> S getLocked(Long id) {
        return (S) entityManager.find(Milestone.class, id, LockModeType.PESSIMISTIC_WRITE);
    }


    @Override
    public <S extends Milestone> void saveCollection(Collection<S> objects) {
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
    public <S extends Milestone> void saveOrUpdateCollection(Collection<S> objects) {
        if (objects == null || objects.isEmpty()) return;
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            for (S obj : objects) {
                if (obj.getHibid() == null || entityManager.find(Milestone.class, obj.getHibid()) == null) {
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
