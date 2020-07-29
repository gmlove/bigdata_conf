package test.yarn;

import static java.util.Collections.singletonList;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.util.Shell;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.ApplicationConstants.Environment;
import org.apache.hadoop.yarn.api.protocolrecords.RegisterApplicationMasterResponse;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerExitStatus;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.ContainerState;
import org.apache.hadoop.yarn.api.records.ContainerStatus;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.timeline.TimelineEntity;
import org.apache.hadoop.yarn.api.records.timeline.TimelineEvent;
import org.apache.hadoop.yarn.client.api.AMRMClient.ContainerRequest;
import org.apache.hadoop.yarn.client.api.TimelineClient;
import org.apache.hadoop.yarn.client.api.async.AMRMClientAsync;
import org.apache.hadoop.yarn.client.api.async.NMClientAsync;
import org.apache.hadoop.yarn.client.api.async.impl.NMClientAsyncImpl;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.security.AMRMTokenIdentifier;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.log4j.LogManager;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AppMaster {

    private static final Log LOG = LogFactory.getLog(AppMaster.class);
    private static final String log4jPath = "log4j.properties";
    private static final String shellCommandPath = "shellCommands";
    private static final String shellArgsPath = "shellArgs";
    protected ApplicationAttemptId appAttemptID;
    protected int numTotalContainers = 1;
    protected AtomicInteger numAllocatedContainers = new AtomicInteger();
    protected AtomicInteger numRequestedContainers = new AtomicInteger();
    private Configuration conf;
    private AMRMClientAsync amRMClient;
    private UserGroupInformation appSubmitterUgi;
    private NMClientAsync nmClient;
    private NMCallbackHandler containerListener;
    // Memory to request for the container on which the shell command will run
    private int containerMemory = 10;
    // VirtualCores to request for the container on which the shell command will run
    private int containerVirtualCores = 1;
    // Priority of the request
    private int requestPriority;
    // Counter for completed containers ( complete denotes successful or failed )
    private AtomicInteger numCompletedContainers = new AtomicInteger();
    // Count of failed containers
    private AtomicInteger numFailedContainers = new AtomicInteger();
    private String shellCommand = "";
    private String shellArgs = "";
    private Map<String, String> shellEnv = new HashMap<>();
    private volatile boolean done;

    private ByteBuffer allTokens;

    private List<Thread> launchThreads = new ArrayList<>();

    private TimelineClient timelineClient;

    public AppMaster() {
        conf = new YarnConfiguration();
    }

    public static void main(String[] args) {
        boolean result = false;
        try {
            AppMaster appMaster = new AppMaster();
            LOG.info("Initializing AppMaster");
            boolean doRun = appMaster.init(args);
            if (!doRun) {
                System.exit(0);
            }
            appMaster.run();
            result = appMaster.finish();
        } catch (Throwable t) {
            LOG.fatal("Error running AppMaster", t);
            LogManager.shutdown();
            ExitUtil.terminate(1, t);
        }
        if (result) {
            LOG.info("Application Master completed successfully. exiting");
            System.exit(0);
        } else {
            LOG.info("Application Master failed. exiting");
            System.exit(2);
        }
    }

    private static void publishContainerStartEvent(TimelineClient timelineClient, Container container) throws IOException, YarnException {
        TimelineEntity entity = new TimelineEntity();
        entity.setEntityId(container.getId().toString());
        entity.setEntityType(DSEntity.DS_CONTAINER.toString());
        entity.addPrimaryFilter("user", UserGroupInformation.getCurrentUser().getShortUserName());
        TimelineEvent event = new TimelineEvent();
        event.setTimestamp(System.currentTimeMillis());
        event.setEventType(DSEvent.DS_CONTAINER_START.toString());
        event.addEventInfo("Node", container.getNodeId().toString());
        event.addEventInfo("Resources", container.getResource().toString());
        entity.addEvent(event);

        timelineClient.putEntities(entity);
    }

    private static void publishContainerEndEvent(TimelineClient timelineClient, ContainerStatus container) throws IOException, YarnException {
        TimelineEntity entity = new TimelineEntity();
        entity.setEntityId(container.getContainerId().toString());
        entity.setEntityType(DSEntity.DS_CONTAINER.toString());
        entity.addPrimaryFilter("user", UserGroupInformation.getCurrentUser().getShortUserName());
        TimelineEvent event = new TimelineEvent();
        event.setTimestamp(System.currentTimeMillis());
        event.setEventType(DSEvent.DS_CONTAINER_END.toString());
        event.addEventInfo("State", container.getState().name());
        event.addEventInfo("Exit Status", container.getExitStatus());
        entity.addEvent(event);

        timelineClient.putEntities(entity);
    }

    private static void publishApplicationAttemptEvent(TimelineClient timelineClient, String appAttemptId, DSEvent appEvent) throws IOException, YarnException {
        TimelineEntity entity = new TimelineEntity();
        entity.setEntityId(appAttemptId);
        entity.setEntityType(DSEntity.DS_APP_ATTEMPT.toString());
        entity.addPrimaryFilter("user", UserGroupInformation.getCurrentUser().getShortUserName());
        TimelineEvent event = new TimelineEvent();
        event.setEventType(appEvent.toString());
        event.setTimestamp(System.currentTimeMillis());
        entity.addEvent(event);

        timelineClient.putEntities(entity);
    }

    private void dumpOutDebugInfo() {
        LOG.info("Dump debug output");
        Map<String, String> envs = System.getenv();
        for (Map.Entry<String, String> env : envs.entrySet()) {
            LOG.info("System env: key=" + env.getKey() + ", val=" + env.getValue());
            System.out.println("System env: key=" + env.getKey() + ", val=" + env.getValue());
        }

        BufferedReader buf = null;
        try {
            String lines = Shell.WINDOWS ? Shell.execCommand("cmd", "/c", "dir") : Shell.execCommand("ls", "-al");
            buf = new BufferedReader(new StringReader(lines));
            String line;
            while ((line = buf.readLine()) != null) {
                LOG.info("System CWD content: " + line);
                System.out.println("System CWD content: " + line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.cleanup(LOG, buf);
        }
    }

    public boolean init(String[] args) throws ParseException, IOException {
        Options opts = new Options();
        opts.addOption("app_attempt_id", true,
                "App Attempt ID. Not to be used unless for testing purposes");
        opts.addOption("shell_env", true,
                "Environment for shell script. Specified as env_key=env_val pairs");
        opts.addOption("container_memory", true,
                "Amount of memory in MB to be requested to run the shell command");
        opts.addOption("container_vcores", true,
                "Amount of virtual cores to be requested to run the shell command");
        opts.addOption("num_containers", true,
                "No. of containers on which the shell command needs to be executed");
        opts.addOption("priority", true, "Application Priority. Default 0");
        opts.addOption("debug", false, "Dump out debug information");

        opts.addOption("help", false, "Print usage");
        CommandLine cliParser = new GnuParser().parse(opts, args);

        if (args.length == 0) {
            printUsage(opts);
            throw new IllegalArgumentException("No args specified for application master to initialize");
        }

        if (fileExist(log4jPath)) {
            try {
                Log4jPropertyHelper.updateLog4jConfiguration(AppMaster.class, log4jPath);
            } catch (Exception e) {
                LOG.warn("Can not set up custom log4j properties. " + e);
            }
        }

        if (cliParser.hasOption("help")) {
            printUsage(opts);
            return false;
        }

        if (cliParser.hasOption("debug")) {
            dumpOutDebugInfo();
        }

        Map<String, String> envs = System.getenv();

        if (!envs.containsKey(Environment.CONTAINER_ID.name())) {
            if (cliParser.hasOption("app_attempt_id")) {
                String appIdStr = cliParser.getOptionValue("app_attempt_id", "");
                appAttemptID = ConverterUtils.toApplicationAttemptId(appIdStr);
            } else {
                throw new IllegalArgumentException(
                        "Application Attempt Id not set in the environment");
            }
        } else {
            ContainerId containerId = ConverterUtils.toContainerId(envs.get(Environment.CONTAINER_ID.name()));
            appAttemptID = containerId.getApplicationAttemptId();
        }

        if (!envs.containsKey(ApplicationConstants.APP_SUBMIT_TIME_ENV)) {
            throw new RuntimeException(ApplicationConstants.APP_SUBMIT_TIME_ENV + " not set in the environment");
        }
        if (!envs.containsKey(Environment.NM_HOST.name())) {
            throw new RuntimeException(Environment.NM_HOST.name() + " not set in the environment");
        }
        if (!envs.containsKey(Environment.NM_HTTP_PORT.name())) {
            throw new RuntimeException(Environment.NM_HTTP_PORT + " not set in the environment");
        }
        if (!envs.containsKey(Environment.NM_PORT.name())) {
            throw new RuntimeException(Environment.NM_PORT.name() + " not set in the environment");
        }

        LOG.info("Application master for app, appId=" + appAttemptID.getApplicationId().getId() +
                ", clustertimestamp=" + appAttemptID.getApplicationId().getClusterTimestamp() +
                ", attemptId=" + appAttemptID.getAttemptId());

        if (!fileExist(shellCommandPath)) {
            throw new IllegalArgumentException("No shell command specified to be executed by application master");
        }

        shellCommand = readContent(shellCommandPath);

        if (fileExist(shellArgsPath)) {
            shellArgs = readContent(shellArgsPath);
        }

        if (cliParser.hasOption("shell_env")) {
            String shellEnvs[] = cliParser.getOptionValues("shell_env");
            for (String env : shellEnvs) {
                env = env.trim();
                int index = env.indexOf('=');
                if (index == -1) {
                    shellEnv.put(env, "");
                    continue;
                }
                String key = env.substring(0, index);
                String val = "";
                if (index < (env.length() - 1)) {
                    val = env.substring(index + 1);
                }
                shellEnv.put(key, val);
            }
        }

        containerMemory = Integer.parseInt(cliParser.getOptionValue("container_memory", "10"));
        containerVirtualCores = Integer.parseInt(cliParser.getOptionValue("container_vcores", "1"));
        numTotalContainers = Integer.parseInt(cliParser.getOptionValue("num_containers", "1"));
        if (numTotalContainers == 0) {
            throw new IllegalArgumentException("Cannot run distributed shell with no containers");
        }
        requestPriority = Integer.parseInt(cliParser.getOptionValue("priority", "0"));

        timelineClient = TimelineClient.createTimelineClient();
        timelineClient.init(conf);
        timelineClient.start();

        return true;
    }

    private void printUsage(Options opts) {
        new HelpFormatter().printHelp("AppMaster", opts);
    }

    public void run() throws YarnException, IOException {
        LOG.info("Starting AppMaster");
        publishApplicationAttemptStartEvent();

        appSubmitterUgi = createAppSubmitterUgi();

        amRMClient = initAmRmClient();
        registerApplicationMaster(amRMClient);
        amRMClient.addContainerRequest(setupContainerAskForRM());

        nmClient = initNmClient();

        publishApplicationAttemptEndEvent();
    }

    public boolean finish() {
        while (!done && (numCompletedContainers.get() != numTotalContainers)) {
            sleep(200);
        }
        for (Thread launchThread : launchThreads) {
            joinThread(launchThread);
        }
        LOG.info("Application completed. Stopping running containers");
        nmClient.stop();

        unregisterApplicationMaster();
        amRMClient.stop();

        boolean appSucceeded = numFailedContainers.get() == 0 && numCompletedContainers.get() == numTotalContainers;
        return appSucceeded;
    }

    private void publishApplicationAttemptEndEvent() {
        try {
            publishApplicationAttemptEvent(timelineClient, appAttemptID.toString(), DSEvent.DS_APP_ATTEMPT_END);
        } catch (Exception e) {
            LOG.error("App Attempt start event could not be published for " + appAttemptID.toString(), e);
        }
    }

    private void registerApplicationMaster(AMRMClientAsync amRMClient) throws YarnException, IOException {
        // Setup local RPC Server to accept status requests directly from clients
        // TODO need to setup a protocol for client to be able to communicate to the RPC server
        // TODO use the rpc port info to register with the RM for the client to send requests to this app master

        // Register self with ResourceManager
        // This will start heartbeating to the RM
        // TODO
        // For status update for clients - yet to be implemented
        // Hostname of the container
        String appMasterHostname = NetUtils.getHostname();
        // Port on which the app master listens for status updates from clients
        int appMasterRpcPort = -1;
        // Tracking url to which app master publishes info for clients to monitor
        String appMasterTrackingUrl = "";
        RegisterApplicationMasterResponse response = amRMClient.registerApplicationMaster(appMasterHostname, appMasterRpcPort, appMasterTrackingUrl);
        // Dump out information about cluster capability as seen by the resource manager
        LOG.info("Max mem capability of resources in this cluster " + response.getMaximumResourceCapability().getMemory());

        int maxVCores = response.getMaximumResourceCapability().getVirtualCores();
        LOG.info("Max vcores capability of resources in this cluster " + maxVCores);

        // A resource ask cannot exceed the max.
        if (containerMemory > response.getMaximumResourceCapability().getMemory()) {
            LOG.info("Container memory specified above max threshold of cluster. Using max value." +
                    ", specified=" + containerMemory +
                    ", max=" + response.getMaximumResourceCapability().getMemory());
            containerMemory = response.getMaximumResourceCapability().getMemory();
        }

        if (containerVirtualCores > maxVCores) {
            LOG.info("Container virtual cores specified above max threshold of cluster. Using max value."
                    + "specified=" + containerVirtualCores + ", "
                    + "max=" + maxVCores);
            containerVirtualCores = maxVCores;
        }

        List<Container> previousAMRunningContainers = response.getContainersFromPreviousAttempts();
        LOG.info(appAttemptID + " received " + previousAMRunningContainers.size() + " previous attempts' running containers on AM registration.");
        numAllocatedContainers.addAndGet(previousAMRunningContainers.size());
    }

    private NMClientAsyncImpl initNmClient() {
        containerListener = new NMCallbackHandler(this);
        NMClientAsyncImpl nmClientAsync = new NMClientAsyncImpl(containerListener);
        nmClientAsync.init(conf);
        nmClientAsync.start();
        return nmClientAsync;
    }

    private AMRMClientAsync initAmRmClient() {
        AMRMClientAsync.CallbackHandler allocListener = new RMCallbackHandler();
        AMRMClientAsync amRMClient = AMRMClientAsync.createAMRMClientAsync(1000, allocListener);
        amRMClient.init(conf);
        amRMClient.start();
        return amRMClient;
    }

    private void publishApplicationAttemptStartEvent() {
        try {
            publishApplicationAttemptEvent(timelineClient, appAttemptID.toString(), DSEvent.DS_APP_ATTEMPT_START);
        } catch (Exception e) {
            LOG.error("App Attempt start event could not be published for " + appAttemptID.toString(), e);
        }
    }

    private UserGroupInformation createAppSubmitterUgi() throws IOException {
        Credentials credentials = UserGroupInformation.getCurrentUser().getCredentials();
        DataOutputBuffer dob = new DataOutputBuffer();
        credentials.writeTokenStorageToStream(dob);
        // Now remove the AM->RM token so that containers cannot access it.
        Iterator<Token<?>> iter = credentials.getAllTokens().iterator();
        LOG.info("Executing with tokens:");
        while (iter.hasNext()) {
            Token<?> token = iter.next();
            LOG.info(token);
            if (token.getKind().equals(AMRMTokenIdentifier.KIND_NAME)) {
                iter.remove();
            }
        }
        allTokens = ByteBuffer.wrap(dob.getData(), 0, dob.getLength());

        // Create appSubmitterUgi and add original tokens to it
        String appSubmitterUserName = System.getenv(Environment.USER.name());
        appSubmitterUgi = UserGroupInformation.createRemoteUser(appSubmitterUserName);
        appSubmitterUgi.addCredentials(credentials);

        return appSubmitterUgi;
    }

    private void unregisterApplicationMaster() {
        LOG.info("Application completed. Signalling finish to RM");
        FinalApplicationStatus appStatus;
        String appMessage = null;
        if (numFailedContainers.get() == 0 && numCompletedContainers.get() == numTotalContainers) {
            appStatus = FinalApplicationStatus.SUCCEEDED;
        } else {
            appStatus = FinalApplicationStatus.FAILED;
            appMessage = "Diagnostics." + ", total=" + numTotalContainers
                    + ", completed=" + numCompletedContainers.get() + ", allocated="
                    + numAllocatedContainers.get() + ", failed="
                    + numFailedContainers.get();
        }
        try {
            amRMClient.unregisterApplicationMaster(appStatus, appMessage, null);
        } catch (YarnException ex) {
            LOG.error("Failed to unregister application", ex);
        } catch (IOException e) {
            LOG.error("Failed to unregister application", e);
        }
    }

    private void joinThread(Thread launchThread) {
        try {
            launchThread.join(10000);
        } catch (InterruptedException e) {
            LOG.info("Exception thrown in thread join: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
        }
    }

    private ContainerRequest setupContainerAskForRM() {
        Priority pri = Priority.newInstance(requestPriority);
        Resource capability = Resource.newInstance(containerMemory, containerVirtualCores);
        ContainerRequest request = new ContainerRequest(capability, null, null, pri);
        LOG.info("Requested container ask: " + request.toString());
        return request;
    }

    private boolean fileExist(String filePath) {
        return new File(filePath).exists();
    }

    private String readContent(String filePath) throws IOException {
        DataInputStream ds = null;
        try {
            ds = new DataInputStream(new FileInputStream(filePath));
            return ds.readUTF();
        } finally {
            org.apache.commons.io.IOUtils.closeQuietly(ds);
        }
    }

    public enum DSEvent {
        DS_APP_ATTEMPT_START, DS_APP_ATTEMPT_END, DS_CONTAINER_START, DS_CONTAINER_END
    }

    public enum DSEntity {
        DS_APP_ATTEMPT, DS_CONTAINER
    }

    static class NMCallbackHandler implements NMClientAsync.CallbackHandler {
        private final AppMaster applicationMaster;
        private ConcurrentMap<ContainerId, Container> containers = new ConcurrentHashMap<>();

        public NMCallbackHandler(AppMaster applicationMaster) {
            this.applicationMaster = applicationMaster;
        }

        public void addContainer(ContainerId containerId, Container container) {
            containers.putIfAbsent(containerId, container);
        }

        @Override
        public void onContainerStopped(ContainerId containerId) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Succeeded to stop Container " + containerId);
            }
            containers.remove(containerId);
        }

        @Override
        public void onContainerStatusReceived(ContainerId containerId, ContainerStatus containerStatus) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Container Status: id=" + containerId + ", status=" + containerStatus);
            }
        }

        @Override
        public void onContainerStarted(ContainerId containerId, Map<String, ByteBuffer> allServiceResponse) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Succeeded to start Container " + containerId);
            }
            Container container = containers.get(containerId);
            if (container != null) {
                applicationMaster.nmClient.getContainerStatusAsync(containerId, container.getNodeId());
            }
            try {
                AppMaster.publishContainerStartEvent(applicationMaster.timelineClient, container);
            } catch (Exception e) {
                LOG.error("Container start event coud not be pulished for " + container.getId().toString(), e);
            }
        }

        @Override
        public void onStartContainerError(ContainerId containerId, Throwable t) {
            LOG.error("Failed to start Container " + containerId);
            containers.remove(containerId);
            applicationMaster.numCompletedContainers.incrementAndGet();
            applicationMaster.numFailedContainers.incrementAndGet();
        }

        @Override
        public void onGetContainerStatusError(ContainerId containerId, Throwable t) {
            LOG.error("Failed to query the status of Container " + containerId);
        }

        @Override
        public void onStopContainerError(ContainerId containerId, Throwable t) {
            LOG.error("Failed to stop Container " + containerId);
            containers.remove(containerId);
        }
    }

    private class RMCallbackHandler implements AMRMClientAsync.CallbackHandler {

        @SuppressWarnings("unchecked")
        @Override
        public void onContainersCompleted(List<ContainerStatus> completedContainers) {
            LOG.info("Got response from RM for container ask, completedCnt=" + completedContainers.size());
            for (ContainerStatus containerStatus : completedContainers) {
                LOG.info(appAttemptID + " got container status for containerID=" + containerStatus.getContainerId() +
                        ", state=" + containerStatus.getState() +
                        ", exitStatus=" + containerStatus.getExitStatus() +
                        ", diagnostics=" + containerStatus.getDiagnostics());

                // non complete containers should not be here
                assert (containerStatus.getState() == ContainerState.COMPLETE);

                // increment counters for completed/failed containers
                int exitStatus = containerStatus.getExitStatus();
                if (0 != exitStatus) {
                    // container failed
                    if (ContainerExitStatus.ABORTED != exitStatus) {
                        // shell script failed， counts as completed
                        numCompletedContainers.incrementAndGet();
                        numFailedContainers.incrementAndGet();
                    } else {
                        // container was killed by framework, possibly preempted，we should re-try as the container was lost for some reason
                        numAllocatedContainers.decrementAndGet();
                        numRequestedContainers.decrementAndGet();
                        // we do not need to release the container as it would be done by the RM
                    }
                } else {
                    // nothing to do，container completed successfully
                    numCompletedContainers.incrementAndGet();
                    LOG.info("Container completed successfully." + ", containerId=" + containerStatus.getContainerId());
                }
                try {
                    publishContainerEndEvent(timelineClient, containerStatus);
                } catch (Exception e) {
                    LOG.error("Container start event could not be pulished for " + containerStatus.getContainerId().toString(), e);
                }
            }

            // ask for more containers if any failed
            int askCount = numTotalContainers - numRequestedContainers.get();
            numRequestedContainers.addAndGet(askCount);

            if (askCount > 0) {
                for (int i = 0; i < askCount; ++i) {
                    ContainerRequest containerAsk = setupContainerAskForRM();
                    amRMClient.addContainerRequest(containerAsk);
                }
            }

            if (numCompletedContainers.get() == numTotalContainers) {
                done = true;
            }
        }

        @Override
        public void onContainersAllocated(List<Container> allocatedContainers) {
            LOG.info("Got response from RM for container ask, allocatedCnt=" + allocatedContainers.size());
            numAllocatedContainers.addAndGet(allocatedContainers.size());
            for (Container allocatedContainer : allocatedContainers) {
                LOG.info("Launching shell command on a new container."
                        + ", containerId=" + allocatedContainer.getId()
                        + ", containerNode=" + allocatedContainer.getNodeId().getHost() + ":" + allocatedContainer.getNodeId().getPort()
                        + ", containerNodeURI=" + allocatedContainer.getNodeHttpAddress()
                        + ", containerResourceMemory=" + allocatedContainer.getResource().getMemory()
                        + ", containerResourceVirtualCores=" + allocatedContainer.getResource().getVirtualCores());

                LaunchContainerRunnable runnableLaunchContainer = new LaunchContainerRunnable(allocatedContainer, containerListener);
                Thread launchThread = new Thread(runnableLaunchContainer);

                // launch and start the container on a separate thread to keep the main thread unblocked as all containers may not be allocated at one go.
                launchThreads.add(launchThread);
                launchThread.start();
            }
        }

        @Override
        public void onShutdownRequest() {
            done = true;
        }

        @Override
        public void onNodesUpdated(List<NodeReport> updatedNodes) {}

        @Override
        public float getProgress() {
            return (float) numCompletedContainers.get() / numTotalContainers;
        }

        @Override
        public void onError(Throwable e) {
            done = true;
            amRMClient.stop();
        }
    }

    private class LaunchContainerRunnable implements Runnable {
        Container container;
        NMCallbackHandler containerListener;

        public LaunchContainerRunnable(Container lcontainer, NMCallbackHandler containerListener) {
            this.container = lcontainer;
            this.containerListener = containerListener;
        }

        @Override
        public void run() {
            LOG.info("Setting up container launch container for containerid=" + container.getId());
            Map<String, LocalResource> localResources = new HashMap<>();

            Vector<CharSequence> vargs = new Vector<>(5);
            vargs.add(shellCommand);
            vargs.add(shellArgs);
            vargs.add("1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/stdout");
            vargs.add("2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/stderr");
            final List<String> commands = singletonList(String.join(" ", vargs));

            ContainerLaunchContext ctx = ContainerLaunchContext.newInstance(localResources, shellEnv, commands,null, allTokens.duplicate(), null);
            containerListener.addContainer(container.getId(), container);
            nmClient.startContainerAsync(container, ctx);
        }
    }
}