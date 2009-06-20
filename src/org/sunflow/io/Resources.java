package org.sunflow.io;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Resources {
    private static Resources uniqueInstance;

    private Map <Object,Object> availableResources;

    private Resources() {
        availableResources = new ConcurrentHashMap <Object,Object> ();
    }

    public static synchronized Resources getInstance() {
        if (uniqueInstance == null) {
            uniqueInstance = new Resources();
        }
        return uniqueInstance;
    }

    public Object get(Object key) {
        return availableResources.get(key);
    }

    public void store(Object key, Object value) {
        availableResources.put(key,value);
    }

    public boolean contains(Object key) {
        return availableResources.containsKey(key);
    }
}
