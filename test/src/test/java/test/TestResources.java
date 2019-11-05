package test;

public class TestResources {

    private String basePath = "/Users/gmliao/dev/frameworks/bigdata/test";

    String krb5FilePath() {
        return basePath + "/src/test/resources/krb5.conf";
    }

    String keytabFilePath() {
        return basePath + "/src/test/resources/root.keytab";
    }

    String coreSiteFilePath() {
        return basePath + "/src/test/resources/core-site.xml";
    }

    String hdfsSiteFilePath() {
        return basePath + "/src/test/resources/hdfs-site.xml";
    }

    String hbaseSiteFilePath() {
        return basePath + "/src/test/resources/hbase-site.xml";
    }

    public void configKerberos() {
        System.setProperty("java.security.krb5.conf", krb5FilePath());
        System.setProperty("sun.security.krb5.debug", "true");
        System.setProperty("sun.security.spnego.debug", "true");
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

}
