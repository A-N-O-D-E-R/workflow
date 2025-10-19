package com.anode.workflow.storage.db.sql.common;

import com.anode.tool.service.CommonRepository;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import jakarta.persistence.*;

import java.util.*;

public class WorkflowRepositoryImpl implements CommonRepository<WorkflowDefinition, Long> {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<WorkflowDefinition> get(Long id) {
        return Optional.ofNullable(entityManager.find(WorkflowDefinition.class, id));
    }

    @Override
    public <S extends WorkflowDefinition> S save(S entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public <S extends WorkflowDefinition> S saveOrUpdate(S entity) {
        if (entity.getHibid() == null || entityManager.find(WorkflowDefinition.class, entity.getHibid()) == null) {
            entityManager.persist(entity);
        } else {
            entity = entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public <S extends WorkflowDefinition> S update(S entity) {
        return entityManager.merge(entity);
    }

    @Override
    public <S extends WorkflowDefinition> void saveCollection(Collection<S> workflows) {
        for (S wf : workflows) {
            entityManager.persist(wf);
        }
    }

    @Override
    public <S extends WorkflowDefinition> void saveOrUpdateCollection(Collection<S> workflows) {
        for (S wf : workflows) {
            saveOrUpdate(wf);
        }
    }

    @Override
    public void delete(Long id) {
        WorkflowDefinition wf = entityManager.find(WorkflowDefinition.class, id);
        if (wf != null) {
            entityManager.remove(wf);
        }
    }

    @Override
    public <S extends WorkflowDefinition> List<S> getAll() {
        TypedQuery<WorkflowDefinition> query = entityManager.createQuery("SELECT w FROM WorkflowDefinition w", WorkflowDefinition.class);
        @SuppressWarnings("unchecked")
        List<S> result = (List<S>) query.getResultList();
        return result;
    }

    @Override
    public <S extends WorkflowDefinition> S getUniqueItem(String uniqueKeyName, String uniqueKeyValue) {
        String ql = String.format("SELECT w FROM WorkflowDefinition w WHERE w.%s = :val", uniqueKeyName);
        TypedQuery<WorkflowDefinition> query = entityManager.createQuery(ql, WorkflowDefinition.class);
        query.setParameter("val", uniqueKeyValue);
        List<WorkflowDefinition> result = query.getResultList();
        return result.isEmpty() ? null : (S) result.get(0);
    }

    @Override
    public <S extends WorkflowDefinition> S getLocked(Long id) {
        return (S) entityManager.find(WorkflowDefinition.class, id, LockModeType.PESSIMISTIC_WRITE);
    }


}
