package com.meshql.repositories.merksql;

public class MerkSqlNative {
    static {
        System.loadLibrary("merksql_jni");
    }

    public static native long create(String dataDir);
    public static native void destroy(long handle);
    public static native String executeSql(long handle, String sql);
    public static native String executePlan(long handle, String planJson);
    public static native void produce(long handle, String topic, String key, String value);
    public static native void registerSource(long handle, String configJson);
    public static native String listQueries(long handle);
    public static native boolean stopQuery(long handle, String queryId);
    public static native void stopAllQueries(long handle);
}
