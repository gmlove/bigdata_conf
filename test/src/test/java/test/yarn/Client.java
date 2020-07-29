package test.yarn;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.ApplicationConstants.Environment;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.ConverterUtils;
import test.TestConfig;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class Client {

    private static final Log LOG = LogFactory.getLog(Client.class);
    private final YarnClient yarnClient;
    private final Configuration conf;

    public Client() throws IOException, YarnException, InterruptedException {
        conf = createConf();
        yarnClient = createYarnClient(conf);
        run("ls /");
    }

    private boolean run(String shellCommand) throws YarnException, IOException, InterruptedException {
        YarnClientApplication app = yarnClient.createApplication();
        ApplicationSubmissionContext appContext = setUpAppContext(
                app.getApplicationSubmissionContext(), shellCommand);
        yarnClient.submitApplication(appContext);
        while (true) {
            Thread.sleep(1000);
            final Boolean appResult = checkAppState(appContext.getApplicationId());
            if (appResult != null) {
                return appResult;
            }
        }
    }

    private ApplicationSubmissionContext setUpAppContext(
            ApplicationSubmissionContext appContext, String shellCommand) throws IOException {
        appContext.setApplicationName("Simple Shell");
        appContext.setResource(Resource.newInstance(128, 1));
        appContext.setAMContainerSpec(setUpAmContainer(conf, appContext, shellCommand));
        appContext.setPriority(Priority.newInstance(1));
        return appContext;
    }

    public static void main(String[] args) throws IOException, YarnException, InterruptedException {
        new Client().run("ls /");
    }

    private Boolean checkAppState(ApplicationId appId) throws YarnException, IOException {
        ApplicationReport report = yarnClient.getApplicationReport(appId);

        LOG.info("Got application report from ASM for"
                + ", appId=" + appId.getId()
                + ", clientToAMToken=" + report.getClientToAMToken()
                + ", appDiagnostics=" + report.getDiagnostics()
                + ", appMasterHost=" + report.getHost()
                + ", appQueue=" + report.getQueue()
                + ", appMasterRpcPort=" + report.getRpcPort()
                + ", appStartTime=" + report.getStartTime()
                + ", yarnAppState=" + report.getYarnApplicationState().toString()
                + ", distributedFinalState=" + report.getFinalApplicationStatus().toString()
                + ", appTrackingUrl=" + report.getTrackingUrl()
                + ", appUser=" + report.getUser());

        YarnApplicationState state = report.getYarnApplicationState();
        FinalApplicationStatus dsStatus = report.getFinalApplicationStatus();
        if (YarnApplicationState.FINISHED == state) {
            if (FinalApplicationStatus.SUCCEEDED == dsStatus) {
                LOG.info("Application has completed successfully. Breaking monitoring loop");
                return true;
            } else {
                LOG.info("Application did finished unsuccessfully."
                        + " YarnState=" + state.toString() + ", DSFinalStatus=" + dsStatus.toString()
                        + ". Breaking monitoring loop");
                return false;
            }
        } else if (YarnApplicationState.KILLED == state || YarnApplicationState.FAILED == state) {
            LOG.info("Application did not finish."
                    + " YarnState=" + state.toString() + ", DSFinalStatus=" + dsStatus.toString()
                    + ". Breaking monitoring loop");
            return false;
        }
        return null;
    }

    private ContainerLaunchContext setUpAmContainer(Configuration conf, ApplicationSubmissionContext appContext, String shellCommand) throws IOException {
        Map<String, LocalResource> localResources = createLocalResources(conf, appContext, shellCommand);
        final String classPath = setUpClassPath(conf);
        final String command = setUpAMCommand();
        final HashMap<String, String> env = new HashMap<String, String>() {{
            put("CLASSPATH", classPath);
        }};
        return ContainerLaunchContext.newInstance(localResources, env, Collections.singletonList(command),null, null, null);
    }

    private String setUpAMCommand() {
        LOG.info("Setting up app master command");
        Vector<CharSequence> vargs = new Vector<>(30);
        vargs.add(Environment.JAVA_HOME.$$() + "/bin/java");
        vargs.add(AppMaster.class.getCanonicalName());
        vargs.add("--container_memory 10");
        vargs.add("--container_vcores 1");
        vargs.add("--num_containers 1");
        vargs.add("--debug");
        vargs.add("1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/AppMaster.stdout");
        vargs.add("2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/AppMaster.stderr");
        final String command = String.join(" ", vargs);
        LOG.info("Completed setting up app master command " + command);
        return command;
    }

    private String setUpClassPath(Configuration conf) {
        // Add AppMaster.jar location to classpath
        // At some point we should not be required to add the hadoop specific classpaths to the env. It should be provided out of the box.
        // For now setting all required classpaths including the classpath to "." for the application jar
        StringBuilder classPathEnv = new StringBuilder(Environment.CLASSPATH.$$())
                .append(ApplicationConstants.CLASS_PATH_SEPARATOR).append("./*");
        for (String c : conf.getStrings(YarnConfiguration.YARN_APPLICATION_CLASSPATH, YarnConfiguration.DEFAULT_YARN_CROSS_PLATFORM_APPLICATION_CLASSPATH)) {
            classPathEnv.append(ApplicationConstants.CLASS_PATH_SEPARATOR);
            classPathEnv.append(c.trim());
        }
        classPathEnv.append(ApplicationConstants.CLASS_PATH_SEPARATOR).append("./log4j.properties");
        return classPathEnv.toString();
    }

    private Map<String, LocalResource> createLocalResources(Configuration conf, ApplicationSubmissionContext appContext, String shellCommand) throws IOException {
        FileSystem fs = FileSystem.get(conf);
        final ShellLocalResources shellLocalResources = new ShellLocalResources(fs, "Simple Shell", appContext.getApplicationId().toString());
        shellLocalResources.addFile("appMaster.jar", "appMaster.jar");
        shellLocalResources.addFileOf("shellCommands", shellCommand);
        return shellLocalResources.getResources();
    }

    private YarnClient createYarnClient(Configuration conf) {
        YarnClient yarnClient = YarnClient.createYarnClient();
        yarnClient.init(conf);
        yarnClient.start();
        return yarnClient;
    }

    private Configuration createConf() {
        final TestConfig testConfig = new TestConfig();
        Configuration conf = new Configuration();
        conf.addResource(new Path(testConfig.hdfsSiteFilePath()));
        conf.addResource(new Path(testConfig.coreSiteFilePath()));
        return conf;
    }

    static class ShellLocalResources {

        private final FileSystem fs;
        private final String appName;
        private final String appId;
        private final HashMap<String, LocalResource> localResources;

        public ShellLocalResources(FileSystem fs, String appName, String appId) {
            this.fs = fs;
            this.appName = appName;
            this.appId = appId;
            localResources = new HashMap<>();
        }

        public void addFile(String dstFileName, String srcFilePath) throws IOException {
            String suffix = appName + "/" + appId + "/" + dstFileName;
            Path dst = new Path(fs.getHomeDirectory(), suffix);
            fs.copyFromLocalFile(new Path(srcFilePath), dst);
            addToLocalResources(dstFileName, dst);
        }

        public void addFileOf(String dstFileName, String content) throws IOException {
            String suffix = "Simple Shell" + "/" + appId + "/" + dstFileName;
            Path dst = new Path(fs.getHomeDirectory(), suffix);
            FSDataOutputStream ostream = null;
            try {
                ostream = FileSystem.create(fs, dst, new FsPermission((short) 0710));
                ostream.writeUTF(content);
            } finally {
                IOUtils.closeQuietly(ostream);
            }
            addToLocalResources(dstFileName, dst);
        }

        private void addToLocalResources(String dstFileName, Path dst) throws IOException {
            FileStatus scFileStatus = fs.getFileStatus(dst);
            LocalResource scRsrc = LocalResource.newInstance(
                    ConverterUtils.getYarnUrlFromURI(dst.toUri()), LocalResourceType.FILE, LocalResourceVisibility.APPLICATION,
                    scFileStatus.getLen(), scFileStatus.getModificationTime());
            localResources.put(dstFileName, scRsrc);
        }

        public Map<String, LocalResource> getResources() {
            return localResources;
        }
    }
}
