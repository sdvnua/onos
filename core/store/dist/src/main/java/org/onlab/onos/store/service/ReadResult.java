package org.onlab.onos.store.service;


/**
 * Database read result.
 */
public class ReadResult {

    private final String tableName;
    private final String key;
    private final VersionedValue value;

    public ReadResult(String tableName, String key, VersionedValue value) {
        this.tableName = tableName;
        this.key = key;
        this.value = value;
    }

    /**
     * Returns database table name.
     * @return table name.
     */
    public String tableName() {
        return tableName;
    }

    /**
     * Returns database table key.
     * @return key.
     */
    public String key() {
        return key;
    }

    /**
     * Returns value associated with the key.
     * @return non-null value if the table contains one, null otherwise.
     */
    public VersionedValue value() {
        return value;
    }
}