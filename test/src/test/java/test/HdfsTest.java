package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class HdfsTest {

    TestConfig testConfig = new TestConfig();

    @Test
    public void should_read_write_files_from_hdfs() throws IOException {
        testConfig.configKerberos();

        Configuration conf = new Configuration();
        conf.addResource(new Path(testConfig.hdfsSiteFilePath()));
        conf.addResource(new Path(testConfig.coreSiteFilePath()));
        UserGroupInformation.setConfiguration(conf);
        UserGroupInformation.loginUserFromKeytab(testConfig.keytabUser(), testConfig.keytabFilePath());

        FileSystem fileSystem = FileSystem.get(conf);
        Path path = new Path("/user/root/input/core-site.xml");
        if (fileSystem.exists(path)) {
            boolean deleteSuccess = fileSystem.delete(path, false);
            assertTrue(deleteSuccess);
        }

        String fileContent = FileUtils.readFileToString(new File(testConfig.coreSiteFilePath()));
        try (FSDataOutputStream fileOut = fileSystem.create(path)) {
            fileOut.write(fileContent.getBytes("utf-8"));
        }

        assertTrue(fileSystem.exists(path));

        System.out.println("start read core-site.xml");
        try (FSDataInputStream in = fileSystem.open(path)) {
            String fileContentRead = IOUtils.toString(in);
            assertEquals(fileContent, fileContentRead);
        }

        fileSystem.close();
    }
}
