package test;

import static org.junit.Assert.assertEquals;

import org.apache.livy.LivyClient;
import org.apache.livy.LivyClientBuilder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

public class LivyTest {
    private static Logger log = LoggerFactory.getLogger(LivyTest.class);

    TestConfig testConfig = new TestConfig();

    @Test
    public void should_submit_and_run_job_through_livy() throws IOException, URISyntaxException, InterruptedException, ExecutionException {
        testConfig.configKerberos();
        LivyClient client = new LivyClientBuilder()
                .setURI(new URI(testConfig.livyUrl()))
                .setConf("livy.client.http.spnego.enable", "true")
                .setConf("livy.client.http.auth.login.config", testConfig.jaasConfPath())
                .setConf("livy.client.http.krb5.conf", testConfig.krb5FilePath())
                .setConf("livy.client.http.krb5.debug", "true")
                .build();

        try {
            String piJar = testConfig.sparkPiJarFilePath();
            log.info("Uploading {} to the Spark context...", piJar);
            client.uploadJar(new File(piJar)).get();

            int samples = 10000;
            log.info("Running PiJob with {} samples...\n", samples);
            double pi = client.submit(new PiJob(samples)).get();

            log.info("Pi is roughly: " + pi);
            assertEquals(3, Double.valueOf(pi).intValue());
        } finally {
            client.stop(true);
        }

    }

}
