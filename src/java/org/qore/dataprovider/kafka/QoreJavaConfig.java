package org.qore.dataprovider.kafka;

import java.util.Properties;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.qore.jni.Hash;

class QoreJavaConfig {
    /** the required config type
     */
    enum ConfType {
        SHORT,
        INT,
        FLOAT,
    }

    /** @param config the input configuration
        @param prop_types the types of the actual configuration names after the prefix has been stripped

        @return the properties set from config items d
     */
    public static Hash get(Hash config, Map<String, ConfType> prop_types) throws Throwable {
        Hash props = new Hash();

        for (Entry<String, Object> entry : config.entrySet()) {
            Object val = entry.getValue();
            if (val == null) {
                continue;
            }
            String key = entry.getKey();
            // convert value if necessary
            if (prop_types != null && prop_types.containsKey(key)) {
                val = convertValue(config, key, prop_types.get(key));
            }
            props.put(key, val);
            //System.out.printf("setting %s = %s\n", key, val.toString());
        }

        return props;
    }

    /** converts values to the required type
     */
    private static Object convertValue(Hash config, String key, ConfType type) {
        switch (type) {
            case SHORT:
                return config.getAsShort(key);

            case INT:
                return config.getAsInt(key);

            case FLOAT:
                return config.getAsFloat(key);
            }

        return null;
    }
}
