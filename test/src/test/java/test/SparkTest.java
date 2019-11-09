package test;

import org.apache.hadoop.security.UserGroupInformation;
import org.apache.spark.sql.SparkSession;
import org.junit.Test;

import java.io.IOException;

public class SparkTest {

    TestResources testResources = new TestResources();

    @Test
    public void should_be_able_to_read_hive_from_spark() throws IOException {
        testResources.configKerberos();
        org.apache.hadoop.conf.Configuration conf = new
                org.apache.hadoop.conf.Configuration();
        conf.set("hadoop.security.authentication", "Kerberos");
        UserGroupInformation.setConfiguration(conf);
        UserGroupInformation.loginUserFromKeytab(testResources.keytabUser(), testResources.keytabFilePath());
        SparkSession spark = SparkSession
                .builder()
                .appName("Simple Spark Example")
                .master("local")
                .enableHiveSupport()
                .config("spark.sql.warehouse.dir", testResources.sparkSqlWarehouseDir())
                .config("hive.metastore.uris", testResources.hiveMetastoreUrl())
                .getOrCreate();

        spark.sql("create database if not exists t");
        spark.sql("drop table if exists t.t");
        spark.sql("create table t.t (a int)");
        spark.sql("insert into table t.t values (1), (2)");
        spark.sql("desc t.t").show();
        spark.sql("select * from t.t").show();

        spark.stop();
        spark.close();
    }


}
