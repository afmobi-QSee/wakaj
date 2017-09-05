package afmobi.wakaj;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Properties;

public class RancherClientBuilder extends Builder implements SimpleBuildStep {
    private final String profile;
    private final String proName;
    private final String accessKey;
    private final String secretKey;
    private final String rancherHost;
    private final String envId;
    private final String serviceId;

    @DataBoundConstructor
    public RancherClientBuilder(String profile, String proName, String accessKey, String secretKey, String rancherHost, String envId, String serviceId) {
        this.profile = profile;
        this.proName = proName;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.rancherHost = rancherHost;
        this.envId = envId;
        this.serviceId = serviceId;
    }

    public String getProfile() {
        return profile;
    }

    public String getProName() {
        return proName;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getRancherHost() {
        return rancherHost;
    }

    public String getEnvId() {
        return envId;
    }

    public String getServiceId() {
        return serviceId;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        RancherModel rancherModel = getRancherModel(workspace);

        this.finishUpgrade(rancherModel, listener);

        JSONObject config = this.getServiceConfig(rancherModel, listener);

        this.updateConfigInfo(rancherModel, config);

        this.putUpgradeConfig(config, rancherModel, listener);
    }

    private RancherModel getRancherModel(FilePath workspace) throws IOException {
        RancherModel rancherModel = new RancherModel();
        rancherModel.setImageName(getImageName(workspace));
        rancherModel.setRancherHost(rancherHost);
        rancherModel.setAccessKey(accessKey);
        rancherModel.setSecretKey(secretKey);
        rancherModel.setEnvId(envId);
        rancherModel.setServiceId(serviceId);
        return rancherModel;
    }

    private String getImageName(FilePath workspace) throws IOException {
        Properties prop = new Properties();

        InputStream in = new BufferedInputStream(new FileInputStream(workspace + "/" + this.profile));

        prop.load(in);

        in.close();

        return prop.get(this.proName) + "";
    }

    private void finishUpgrade(RancherModel rancherModel, TaskListener listener) throws IOException {
        HttpPost post = new HttpPost(rancherModel.getRancherHost() + "/v2-beta/projects/" + rancherModel.getEnvId() + "/services/" + rancherModel.getServiceId() + "/?action=finishupgrade");

        post.setHeader("Authorization", getAuthHeader(rancherModel));

        post.setHeader("Content-Type", "application/json");

        HttpResponse response = doHttpRequest(post);

        response.getStatusLine().getStatusCode();

        String strResult = EntityUtils.toString(response.getEntity());

        listener.getLogger().println(strResult);
    }

    private void putUpgradeConfig(JSONObject config, RancherModel rancherModel, TaskListener listener) throws IOException {
        try {
            this.putUpgradeConfig(config, rancherModel, listener,  0);
        } catch (InterruptedException e) {
            throw new IOException(e.getMessage());
        }
    }

    private void putUpgradeConfig(JSONObject config, RancherModel rancherModel, TaskListener listener, int times) throws IOException, InterruptedException {
        if (times == 3){
            throw new IOException("putUpgradeConfig error with four times trying");
        }
        Thread.sleep(2000);

        HttpPost post = new HttpPost(rancherModel.getRancherHost() + "/v2-beta/projects/" + rancherModel.getEnvId() + "/services/" + rancherModel.getServiceId() + "/?action=upgrade");

        post.setHeader("Authorization", getAuthHeader(rancherModel));

        post.setHeader("Content-Type", "application/json");

        StringEntity s = new StringEntity(config.toString(), "utf-8");

        s.setContentEncoding(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));

        post.setEntity(s);

        HttpResponse response = doHttpRequest(post);

        int code = response.getStatusLine().getStatusCode();

        EntityUtils.toString(response.getEntity());

        if (!isStatusOK(code)){

            listener.getLogger().println("try another action after 2s");

            this.putUpgradeConfig(config, rancherModel, listener, times + 1);

        }

        listener.getLogger().println("putUpgradeConfig success");
    }

    private JSONObject getServiceConfig(RancherModel rancherModel, TaskListener listener) throws IOException {
        HttpGet request = new HttpGet(rancherModel.getRancherHost() + "/v2-beta/projects/" + rancherModel.getEnvId() + "/services/" + rancherModel.getServiceId());

        request.setHeader("Authorization", getAuthHeader(rancherModel));

        HttpResponse response = doHttpRequest(request);

        int code = response.getStatusLine().getStatusCode();

        String strResult = EntityUtils.toString(response.getEntity());

        listener.getLogger().println("getServiceConfig status is " + code);

        if (!isStatusOK(code)){

            listener.getLogger().println(strResult);

            throw new IOException("getUpgradeConfig error");

        }

        return JSONObject.fromObject(strResult);
    }


    private String getAuthHeader(RancherModel rancherModel) throws UnsupportedEncodingException {

        String auth = rancherModel.getAccessKey() + ":" + rancherModel.getSecretKey();

        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("US-ASCII")));

        return "Basic " + new String(encodedAuth, "UTF-8");
    }

    private HttpResponse doHttpRequest(HttpUriRequest request) throws IOException {

        DefaultHttpClient client = new DefaultHttpClient();

        return client.execute(request);
    }

    private boolean isStatusOK(int code){
        switch (code){
            case HttpStatus.SC_OK:
            case HttpStatus.SC_CREATED:
            case HttpStatus.SC_ACCEPTED:
                return true;
            default:
                return false;
        }
    }

    private void updateConfigInfo(RancherModel rancherModel, JSONObject config) {

        config.getJSONObject("launchConfig").put("imageUuid", "docker:" + rancherModel.getImageName());

        JSONObject inServiceStrategy = new JSONObject();

        inServiceStrategy.put("batchSize", 1);

        inServiceStrategy.put("intervalMillis", 2000);

        inServiceStrategy.put("launchConfig", config.getJSONObject("launchConfig"));

        inServiceStrategy.put("secondaryLaunchConfigs", config.getJSONArray("secondaryLaunchConfigs"));

        inServiceStrategy.put("startFirst", false);

        config.put("inServiceStrategy", inServiceStrategy);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        //        private boolean useFrench;
        public DescriptorImpl() {
            load();
        }

        public FormValidation doCheckProfile(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a profile name");
            if (!value.endsWith("properties"))
                return FormValidation.error("Please set a properties file");
            return FormValidation.ok();
        }
        public FormValidation doCheckProName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a Name");
            return FormValidation.ok();
        }
        public FormValidation doCheckAccessKey(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a accessKey");
            return FormValidation.ok();
        }
        public FormValidation doCheckSecretKey(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a secretKey");
            return FormValidation.ok();
        }
        public FormValidation doCheckRancherHost(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a rancherHost");
            return FormValidation.ok();
        }
        public FormValidation doCheckEnvId(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a envId");
            return FormValidation.ok();
        }
        public FormValidation doCheckServiceId(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a serviceId");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "rancher client";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
//            useFrench = formData.getBoolean("useFrench");
            save();
            return super.configure(req, formData);
        }
    }
}

