package test;

import static org.junit.Assert.assertEquals;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.Test;

import java.io.IOException;

public class HbaseTest {

    TestResources testResources = new TestResources();

    @Test
    public void should_read_write_hbase() throws IOException {
        testResources.configKerberos();
        Configuration config = HBaseConfiguration.create();
        config.addResource(new Path(testResources.hbaseSiteFilePath()));
        UserGroupInformation.setConfiguration(config);
        UserGroupInformation.loginUserFromKeytab(testResources.keytabUser(), testResources.keytabFilePath());

        TableName tableName = TableName.valueOf("test");

        Connection connection = ConnectionFactory.createConnection(config);
        Admin admin = connection.getAdmin();

        if (admin.tableExists(tableName)) {
            admin.deleteTable(tableName);
        }

        String family1 = "Family1";
        String family2 = "Family2";
        HTableDescriptor tableDescriptor = new HTableDescriptor(tableName);
        tableDescriptor.addFamily(new HColumnDescriptor(family1));
        tableDescriptor.addFamily(new HColumnDescriptor(family2));
        admin.createTable(tableDescriptor);

        Put p = new Put(Bytes.toBytes("row1"));
        String qualifier1 = "Qualifier1";
        p.addColumn(family1.getBytes(), qualifier1.getBytes(), "value1".getBytes());
        p.addColumn(family2.getBytes(), qualifier1.getBytes(), "value2".getBytes());

        Table table = connection.getTable(tableName);
        table.put(p);

        Get g = new Get(Bytes.toBytes("row1"));

        assertEquals("value1", new String(table.get(g).getValue(family1.getBytes(), qualifier1.getBytes())));
        connection.close();
    }

}
