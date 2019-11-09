package test;

import java.io.File;

public class TestResources {

    private String testResourcesBase;

    public TestResources() {
        String basePath = new File("").getAbsolutePath();
        testResourcesBase = String.join(File.separator, basePath, "src", "test", "resources");
    }

    String krb5FilePath() {
        return resourcePath("krb5.conf");
    }

    String keytabUser() {
        return "root@HADOOP.COM";
    }

    String keytabFilePath() {
        return resourcePath("root.keytab");
    }

    String coreSiteFilePath() {
        return resourcePath("core-site.xml");
    }

    String hdfsSiteFilePath() {
        return resourcePath("hdfs-site.xml");
    }

    String hbaseSiteFilePath() {
        return resourcePath("hbase-site.xml");
    }

    String jaasConfPath() {
        return resourcePath("jaas.conf");
    }

    String sparkPiJarFilePath() {
        return resourcePath("spark-pi.jar");
    }

    String hiveUrl() {
        return "jdbc:hive2://localhost:10000/default;principal=root/localhost@HADOOP.COM";
    }

    String sparkSqlWarehouseDir() {
        return "hdfs://localhost:9000/user/hive/warehouse";
    }

    String hiveMetastoreUrl() {
        return "thrift://localhost:9083";
    }

    String livyUrl() {
        return "http://localhost:8998";
    }

    private String resourcePath(String fileName) {
        return String.join(File.separator, testResourcesBase, fileName);
    }

    public void configKerberos() {
        System.setProperty("java.security.krb5.conf", krb5FilePath());
        System.setProperty("sun.security.krb5.debug", "true");
        System.setProperty("sun.security.spnego.debug", "true");
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

}
