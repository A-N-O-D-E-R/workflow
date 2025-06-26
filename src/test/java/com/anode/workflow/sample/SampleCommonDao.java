package com.anode.workflow.sample;

import com.anode.tool.StringUtils;
import com.anode.tool.document.Document;
import com.anode.tool.document.JDocument;
import com.anode.workflow.CommonDao;
import com.anode.workflow.entities.sla.Milestone;
import com.anode.workflow.entities.workflows.WorkflowDefinition;
import com.anode.workflow.entities.workflows.WorkflowInfo;
import com.anode.workflow.entities.workflows.WorkflowVariables;
import com.anode.workflow.entities.workflows.paths.ExecPath;
import com.anode.workflow.mapper.ExecPathMapper;
import com.anode.workflow.mapper.MilestoneMapper;
import com.anode.workflow.mapper.WorkflowDefinitionMapper;
import com.anode.workflow.mapper.WorkflowInfoMapper;
import com.anode.workflow.mapper.WorkflowVariablesMapper;
import com.anode.workflow.service.runtime.RuntimeService;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SampleCommonDao implements CommonDao {

    private String filePath = null;
    private Map<Serializable, Long> counters = new HashMap<>();

    public SampleCommonDao(String filePath) {
        this.filePath = filePath;
    }

    public void write(Serializable key, Document d) {
        FileWriter fw = null;
        try {
            fw = new FileWriter(filePath + key + ".json");
            fw.write(d.getPrettyPrintJson());
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Document read(Serializable key) {
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(filePath + key + ".json"));
        } catch (FileNotFoundException e) {
        }

        Document d = null;
        if (is != null) {
            String json = StringUtils.getStringFromStream(is);
            d = new JDocument(json);
            try {
                is.close();
            } catch (IOException e) {
            }
        }

        return d;
    }

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

    @Override
    public void saveOrUpdate(Serializable id, Object object) {
        if (object instanceof WorkflowDefinition workflowDefinition) {
            write(id, WorkflowDefinitionMapper.toJDocument(workflowDefinition));
        } else if (object instanceof WorkflowInfo workflowInfo) {
            write(id, WorkflowInfoMapper.toJDocument(workflowInfo));
        } else if (object instanceof List sla && sla.get(0) instanceof Milestone) {
            write(id, MilestoneMapper.toJDocument(sla));
        } else if (object instanceof WorkflowVariables workflowVariables) {
            write(id, WorkflowVariablesMapper.toJDocument(workflowVariables));
        } else if (object instanceof ExecPath execPath) {
            write(id, ExecPathMapper.toJDocument(execPath));
        }
    }

    @Override
    public void save(Serializable id, Object object) {
        if (object instanceof WorkflowDefinition workflowDefinition) {
            write(id, WorkflowDefinitionMapper.toJDocument(workflowDefinition));
        } else if (object instanceof WorkflowInfo workflowInfo) {
            write(id, WorkflowInfoMapper.toJDocument(workflowInfo));
        } else if (object instanceof List sla && sla.get(0) instanceof Milestone) {
            write(id, MilestoneMapper.toJDocument(sla));
        } else if (object instanceof WorkflowVariables workflowVariables) {
            write(id, WorkflowVariablesMapper.toJDocument(workflowVariables));
        } else if (object instanceof ExecPath execPath) {
            write(id, ExecPathMapper.toJDocument(execPath));
        }
    }

    @Override
    public void update(Serializable id, Object object) {
        if (object instanceof WorkflowDefinition workflowDefinition) {
            write(id, WorkflowDefinitionMapper.toJDocument(workflowDefinition));
        } else if (object instanceof WorkflowInfo workflowInfo) {
            write(id, WorkflowInfoMapper.toJDocument(workflowInfo));
        } else if (object instanceof List sla && sla.get(0) instanceof Milestone) {
            write(id, MilestoneMapper.toJDocument(sla));
        } else if (object instanceof WorkflowVariables workflowVariables) {
            write(id, WorkflowVariablesMapper.toJDocument(workflowVariables));
        } else if (object instanceof ExecPath execPath) {
            write(id, ExecPathMapper.toJDocument(execPath));
        }
    }

    @Override
    public void saveCollection(Collection objects) {
        objects.forEach(obj -> save(1, obj));
    }

    @Override
    public void saveOrUpdateCollection(Collection objects) {
        objects.forEach(obj -> saveOrUpdate(1, obj));
    }

    @Override
    public void delete(Serializable id) {
        try {
            Files.deleteIfExists(
                    Paths.get(
                            filePath + RuntimeService.JOURNEY + RuntimeService.SEP + id + ".json"));
            Files.deleteIfExists(
                    Paths.get(
                            filePath
                                    + RuntimeService.WORKFLOW_INFO
                                    + RuntimeService.SEP
                                    + id
                                    + ".json"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public <T> T get(Class<T> objectClass, Serializable id) {
        Document document = read(id);
        if (objectClass == WorkflowDefinition.class) {
            return (T) WorkflowDefinitionMapper.toEntity(document);
        } else if (objectClass == WorkflowInfo.class) {
            return (T) WorkflowInfoMapper.toEntity(document);
        } else if (objectClass == List.class) {
            return (T) MilestoneMapper.toEntities(document);
        } else if (objectClass == List.class) {
            return (T) WorkflowVariablesMapper.toEntities(document);
        } else if (objectClass == ExecPath.class) {
            return (T) ExecPathMapper.toEntity(document);
        }
        return null;
    }

    @Override
    public <T> List<T> getAll(Class<T> type) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAll'");
    }

    @Override
    public <T> T getUniqueItem(Class<T> type, String uniqueKeyName, String uniqueKeyValue) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getUniqueItem'");
    }

    @Override
    public <T> T getLocked(Class<T> objectClass, Serializable id) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getLocked'");
    }
}
