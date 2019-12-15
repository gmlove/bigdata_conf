package test;

import java.io.File;

public class TestConfig {

    private String testConfigBase;
    private String shdHost;

    public TestConfig() {
        String basePath = new File("").getAbsolutePath();
        testConfigBase = String.join(File.separator, basePath, "src", "test", "resources");
        shdHost = System.getProperty("shdHost", "localhost");
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
        return String.format("jdbc:hive2://%s:10000/default;principal=root/%s@HADOOP.COM", shdHost, shdHost);
    }

    String sparkSqlWarehouseDir() {
        return String.format("hdfs://%s:9000/user/hive/warehouse", shdHost);
    }

    String hiveMetastoreUrl() {
        return String.format("thrift://%s:9083", shdHost);
    }

    String livyUrl() {
        return String.format("http://%s:8998", shdHost);
    }

    private String resourcePath(String fileName) {
        return String.join(File.separator, testConfigBase, fileName);
    }

    public void configKerberos() {
        System.setProperty("java.security.krb5.conf", krb5FilePath());
        System.setProperty("sun.security.krb5.debug", "true");
        System.setProperty("sun.security.spnego.debug", "true");
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

}
