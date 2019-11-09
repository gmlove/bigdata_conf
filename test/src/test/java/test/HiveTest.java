package test;

import static org.junit.Assert.assertEquals;

import org.apache.hadoop.security.UserGroupInformation;
import org.junit.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class HiveTest {

    TestResources testResources = new TestResources();

    @Test
    public void should_connect_to_hive_and_execute_query() throws IOException, SQLException {
        testResources.configKerberos();
        org.apache.hadoop.conf.Configuration conf = new
                org.apache.hadoop.conf.Configuration();
        conf.set("hadoop.security.authentication", "Kerberos");
        UserGroupInformation.setConfiguration(conf);
        UserGroupInformation.loginUserFromKeytab(testResources.keytabUser(), testResources.keytabFilePath());

        String url = testResources.hiveUrl();
        Connection conn = DriverManager.getConnection(url);
        Statement statement = conn.createStatement();

        statement.execute("create database if not exists t");
        statement.execute("drop table if exists t.t");
        statement.execute("create table t.t (a int)");
        statement.execute("insert into table t.t values (1), (2)");

        ResultSet resultSet = statement.executeQuery("desc t.t");
        resultSet.next();
        assertEquals("a", resultSet.getString("col_name"));
        assertEquals("int", resultSet.getString("data_type"));
        assertEquals("", resultSet.getString("comment"));

        resultSet = statement.executeQuery("select * from t.t");
        resultSet.next();
        assertEquals(1, resultSet.getInt("a"));
        resultSet.next();
        assertEquals(2, resultSet.getInt("a"));

        conn.close();
    }

}
