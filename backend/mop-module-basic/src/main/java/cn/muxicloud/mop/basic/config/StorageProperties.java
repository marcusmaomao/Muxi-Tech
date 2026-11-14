package cn.muxicloud.mop.basic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mop.storage")
public class StorageProperties {
    private String endpoint = "";
    private String accessKey = "";
    private String secretKey = "";
    private String bucket = "mop";
    private String region = "us-east-1";

    public boolean configured() {
        return endpoint != null && !endpoint.isBlank() && accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank();
    }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
}
