package afmobi.wakaj;

/**
 * Created by wen on 17-4-25.
 */
public class RancherModel {
    private String imageName;
    private String rancherHost;
    private String envId;
    private String serviceId;
    private String accessKey ;
    private String secretKey ;

    public String getImageName() {
        return imageName;
    }

    public RancherModel setImageName(String imageName) {
        this.imageName = imageName;
        return this;
    }

    public String getRancherHost() {
        return rancherHost;
    }

    public RancherModel setRancherHost(String rancherHost) {
        this.rancherHost = rancherHost;
        return this;
    }

    public String getEnvId() {
        return envId;
    }

    public RancherModel setEnvId(String envId) {
        this.envId = envId;
        return this;
    }

    public String getServiceId() {
        return serviceId;
    }

    public RancherModel setServiceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public RancherModel setAccessKey(String accessKey) {
        this.accessKey = accessKey;
        return this;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public RancherModel setSecretKey(String secretKey) {
        this.secretKey = secretKey;
        return this;
    }
}
