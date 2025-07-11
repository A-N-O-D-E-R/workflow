package com.anode.workflow;

import com.anode.tool.StringUtils;
import com.anode.tool.document.Document;
import com.anode.tool.document.JDocument;
import com.anode.tool.service.CommonService;
import com.anode.tool.service.IdFactory;
import com.anode.workflow.service.runtime.RuntimeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileDao implements CommonService {

    private String filePath = null;
    private Map<String, Long> counters = new HashMap<>();

    public FileDao(String filePath) {
        this.filePath = filePath;
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

    public void delete(String key) {
        try {
            Files.deleteIfExists(
                    Paths.get(
                            filePath
                                    + RuntimeService.JOURNEY
                                    + RuntimeService.SEP
                                    + key
                                    + ".json"));
            Files.deleteIfExists(
                    Paths.get(
                            filePath
                                    + RuntimeService.WORKFLOW_INFO
                                    + RuntimeService.SEP
                                    + key
                                    + ".json"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void saveOrUpdate(Serializable id, Object object) {
        if (object instanceof Document d) {
            FileWriter fw = null;
            try {
                fw = new FileWriter(filePath + id + ".json");
                fw.write(d.getPrettyPrintJson());
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            String json;
            try {
                json = new ObjectMapper().writeValueAsString(object);
                this.saveOrUpdate(id, new JDocument(json));
            } catch (JsonProcessingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Override
    public void save(Serializable id, Object object) {
        if (object instanceof Document d) {
            FileWriter fw = null;
            try {
                fw = new FileWriter(filePath + id + ".json");
                fw.write(d.getPrettyPrintJson());
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (object instanceof Document d) {
            String json;
            try {
                json = new ObjectMapper().writeValueAsString(object);
                this.saveOrUpdate(id, new JDocument(json));
            } catch (JsonProcessingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Override
    public void update(Serializable id, Object object) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'update'");
    }

    @Override
    public void saveCollection(Collection objects) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'saveCollection'");
    }

    @Override
    public void saveOrUpdateCollection(Collection objects) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'saveOrUpdateCollection'");
    }

    @Override
    public void delete(Serializable id) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'delete'");
    }

    @Override
    public <T> T get(Class<T> objectClass, Serializable id) {
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(filePath + id + ".json"));
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

        return (T) d;
    }

    @Override
    public List getAll(Class type) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAll'");
    }

    @Override
    public Object getUniqueItem(Class type, String uniqueKeyName, String uniqueKeyValue) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getUniqueItem'");
    }

    @Override
    public Object getLocked(Class objectClass, Serializable id) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getLocked'");
    }

    @Override
    public Map<Serializable, Serializable> makeClone(Object object, IdFactory idFactory) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'makeClone'");
    }

    @Override
    public Serializable getMinimalId(Comparator<Serializable> comparator) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMinimalId'");
    }
}
