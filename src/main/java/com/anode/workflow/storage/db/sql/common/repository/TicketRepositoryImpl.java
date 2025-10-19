package com.anode.workflow.storage.db.sql.common.repository;

import com.anode.tool.service.CommonRepository;
import com.anode.workflow.entities.tickets.Ticket;

import jakarta.persistence.*;

import java.util.*;

public class TicketRepositoryImpl implements CommonRepository<Ticket, Long> {

    private final EntityManager entityManager;

    public TicketRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Ticket> get(Long id) {
        return Optional.ofNullable(entityManager.find(Ticket.class, id));
    }

    @Override
    public <S extends Ticket> S save(S entity) {
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
    public <S extends Ticket> S saveOrUpdate(S entity) {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            if (entity.getHibid() == 0 || entityManager.find(Ticket.class, entity.getHibid()) == null) {
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
    public <S extends Ticket> S update(S entity) {
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
            Ticket ticket = entityManager.find(Ticket.class, id);
            if (ticket != null) entityManager.remove(ticket);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    @Override
    public <S extends Ticket> List<S> getAll() {
        TypedQuery<Ticket> query = entityManager.createQuery("SELECT t FROM Ticket t", Ticket.class);
        @SuppressWarnings("unchecked")
        List<S> result = (List<S>) query.getResultList();
        return result;
    }

    @Override
    public <S extends Ticket> S getUniqueItem(String key, String value) {
        TypedQuery<Ticket> query = entityManager.createQuery(
                "SELECT t FROM Ticket t WHERE t." + key + " = :val", Ticket.class);
        query.setParameter("val", value);
        List<Ticket> results = query.getResultList();
        return results.isEmpty() ? null : (S) results.get(0);
    }

    @Override
    public <S extends Ticket> S getLocked(Long id) {
        return (S) entityManager.find(Ticket.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    @Override
    public <S extends Ticket> void saveCollection(Collection<S> objects) {
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
    public <S extends Ticket> void saveOrUpdateCollection(Collection<S> objects) {
        if (objects == null || objects.isEmpty()) return;
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            for (S obj : objects) {
                if (obj.getHibid() == 0 || entityManager.find(Ticket.class, obj.getHibid()) == null) {
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
