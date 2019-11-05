package test;

public class TestResources {

    private String basePath = "/Users/gmliao/dev/frameworks/bigdata/test";
    private String testResourcesBase = basePath + "/src/test/resources";

    String krb5FilePath() {
        return testResourcesBase + "/krb5.conf";
    }

    String keytabFilePath() {
        return testResourcesBase + "/root.keytab";
    }

    String coreSiteFilePath() {
        return testResourcesBase + "/core-site.xml";
    }

    String hdfsSiteFilePath() {
        return testResourcesBase + "/hdfs-site.xml";
    }

    String hbaseSiteFilePath() {
        return testResourcesBase + "/hbase-site.xml";
    }

    String jaasConfPath() {
        return testResourcesBase + "/jaas.conf";
    }

    String sparkPiJarFilePath() {
        return testResourcesBase + "/spark-pi.jar";
    }

    public void configKerberos() {
        System.setProperty("java.security.krb5.conf", krb5FilePath());
        System.setProperty("sun.security.krb5.debug", "true");
        System.setProperty("sun.security.spnego.debug", "true");
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

}
