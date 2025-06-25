package dev.mlml.systems;

import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IO is an abstract class that provides a framework for saving and loading system data.
 * It uses serialization to store objects in a file and allows for easy retrieval of data types.
 * Subclasses must implement the registerDataTypes method to define their specific data types.
 */
public abstract class IO {
    private static final Logger logger = LoggerFactory.getLogger(IO.class);

    private static final String OBJECT_SEPARATOR = "\u001D";
    private static final String USER_FIELD_SEPARATOR = "\u001E";
    private static final String RECORD_SEPARATOR = "\u001F";

    private static final Map<Class<? extends IO>, IO> systemRegistry = new ConcurrentHashMap<>();

    private final Map<String, DataType<?>> dataTypes = new HashMap<>();
    private final String systemName;
    private final String fileName;

    protected IO(String systemName) {
        this.systemName = systemName;
        this.fileName = systemName.toLowerCase() + ".ilm";
        systemRegistry.put(this.getClass(), this);
        registerDataTypes();
    }

    protected abstract void registerDataTypes();

    protected <T> void registerDataType(String typeName, Class<T> clazz, DataProvider<T> globalProvider, DataProvider<Map<String, T>> collectionProvider) {
        dataTypes.put(typeName.toUpperCase(), new DataType<>(typeName, clazz, globalProvider, collectionProvider));
    }

    protected <T> void registerGlobalDataType(String typeName, Class<T> clazz, DataProvider<T> provider) {
        registerDataType(typeName, clazz, provider, null);
    }

    protected <T> void registerCollectionDataType(String typeName, Class<T> clazz, DataProvider<Map<String, T>> provider) {
        registerDataType(typeName, clazz, null, provider);
    }

    /**
     * Saves all registered systems to their respective files.
     * This method iterates through all systems in the registry and calls their save method.
     */
    public static void saveAll() {
        for (IO system : systemRegistry.values()) {
            system.save();
        }
    }

    /**
     * Loads all registered systems from their respective files.
     * This method iterates through all systems in the registry and calls their load method.
     */
    public static void loadAll() {
        for (IO system : systemRegistry.values()) {
            system.load();
        }
    }

    /**
     * Retrieves a system by its class type.
     * @param systemClass the class of the system to retrieve
     * @return the system instance if found, null otherwise
     */
    public static IO getSystem(Class<? extends IO> systemClass) {
        return systemRegistry.get(systemClass);
    }

    /**
     * Saves the current system data to a file.
     */
    public void save() {
        StringBuilder sb = new StringBuilder();

        for (DataType<?> dataType : dataTypes.values()) {
            sb.append(serializeDataType(dataType));
        }

        try {
            Files.write(new File(fileName).toPath(), sb.toString().getBytes());
            logger.info("Saved {} system data to {}", systemName, fileName);
        } catch (Exception e) {
            logger.error("Failed to save {} system data", systemName, e);
        }
    }

    /**
     * Loads the system data from a file.
     * If the file does not exist, it will log a message and return.
     */
    @SneakyThrows
    public void load() {
        File file = new File(fileName);
        if (!file.exists()) {
            logger.info("No existing data file found for system: {}", systemName);
            return;
        }

        StringBuilder cb = new StringBuilder();
        for (String line : Files.readAllLines(file.toPath())) {
            cb.append(line);
        }

        String content = cb.toString();
        String[] objects = content.split(OBJECT_SEPARATOR);

        for (String object : objects) {
            if (object.isEmpty()) {
                continue;
            }

            deserializeObject(object);
        }

        logger.info("Loaded {} system data from {}", systemName, fileName);
    }

    private <T> String serializeDataType(DataType<T> dataType) {
        StringBuilder sb = new StringBuilder();

        if (dataType.globalProvider != null) {
            T globalData = dataType.globalProvider.get();
            if (globalData != null) {
                sb.append(serializeObject(dataType.typeName.toUpperCase(), "global", globalData, dataType.clazz));
            }
        }

        if (dataType.collectionProvider != null) {
            Map<String, T> collection = dataType.collectionProvider.get();
            if (collection != null) {
                for (Map.Entry<String, T> entry : collection.entrySet()) {
                    sb.append(serializeObject(dataType.typeName.toUpperCase(),
                                              entry.getKey(),
                                              entry.getValue(),
                                              dataType.clazz
                    ));
                }
            }
        }

        return sb.toString();
    }

    private String serializeObject(String type, String id, Object obj, Class<?> clazz) {
        StringBuilder sb = new StringBuilder();

        sb.append(OBJECT_SEPARATOR).append(type).append(RECORD_SEPARATOR).append(id);

        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (!field.isAnnotationPresent(Serialize.class)) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value != null) {
                    sb.append(USER_FIELD_SEPARATOR).append(field.getName()).append(RECORD_SEPARATOR).append(value);
                }
            } catch (IllegalAccessException e) {
                logger.error("Failed to serialize field: {} in class: {}", field.getName(), clazz.getSimpleName());
            }
        }

        return sb.toString();
    }

    private void deserializeObject(String object) {
        String[] parts = object.split(USER_FIELD_SEPARATOR);
        String[] kv = parts[0].split(RECORD_SEPARATOR);

        if (kv.length < 2) {
            logger.warn("Invalid object format: {}", object);
            return;
        }

        String typeName = kv[0];
        String id = kv[1];

        DataType<?> dataType = dataTypes.get(typeName);
        if (dataType == null) {
            logger.warn("Unknown data type: {} in system: {}", typeName, systemName);
            return;
        }

        try {
            Object instance = createInstance(dataType.clazz, id);
            populateFields(instance, parts, dataType.clazz);
            storeInstance(dataType, id, instance);
        } catch (Exception e) {
            logger.error("Failed to deserialize object of type: {} with id: {}", typeName, id, e);
        }
    }

    private Object createInstance(Class<?> clazz, String id) throws Exception {
        try {
            return clazz.getConstructor(String.class).newInstance(id);
        } catch (NoSuchMethodException e) {
            return clazz.getDeclaredConstructor().newInstance();
        }
    }

    private void populateFields(Object instance, String[] parts, Class<?> clazz) {
        for (int i = 1; i < parts.length; i++) {
            String[] kv = parts[i].split(RECORD_SEPARATOR);
            if (kv.length < 2) {
                continue;
            }

            String fieldName = kv[0];
            String value = kv[1];

            try {
                Field field = clazz.getDeclaredField(fieldName);
                setField(instance, field, value);
            } catch (NoSuchFieldException e) {
                logger.error("Failed to find field: {} in class: {}", fieldName, clazz.getSimpleName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void storeInstance(DataType<T> dataType, String id, Object instance) {
        if ("global".equals(id) && dataType.globalProvider != null) {
            // Note: This assumes the provider has a setter method or the provider interface is extended
            logger.debug("Loaded global {} object", dataType.typeName);
        } else if (dataType.collectionProvider != null) {
            Map<String, T> collection = dataType.collectionProvider.get();
            if (collection != null) {
                collection.put(id, (T) instance);
                logger.debug("Loaded {} object with id: {}", dataType.typeName, id);
            }
        }
    }

    @SneakyThrows
    private static void setField(Object obj, Field field, String value) {
        field.setAccessible(true);
        try {
            if (field.getType() == int.class) {
                field.setInt(obj, Integer.parseInt(value));
            } else if (field.getType() == float.class) {
                field.setFloat(obj, Float.parseFloat(value));
            } else if (field.getType() == double.class) {
                field.setDouble(obj, Double.parseDouble(value));
            } else if (field.getType() == long.class) {
                field.setLong(obj, Long.parseLong(value));
            } else if (field.getType() == boolean.class) {
                field.setBoolean(obj, Boolean.parseBoolean(value));
            } else {
                field.set(obj, value);
            }
        } catch (IllegalAccessException | NumberFormatException e) {
            logger.error("Failed to set field: {} with value: {}", field.getName(), value);
        }
    }

    private record DataType<T>(String typeName, Class<T> clazz, DataProvider<T> globalProvider,
                               DataProvider<Map<String, T>> collectionProvider) {
    }

    @FunctionalInterface
    public interface DataProvider<T> {
        T get();
    }
}