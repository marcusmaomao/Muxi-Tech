package cn.muxicloud.mop.basic.storage;

import cn.muxicloud.mop.basic.config.StorageProperties;
import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import java.io.OutputStream;
import java.net.URI;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class ObjectStore {
    private final StorageProperties properties;
    private volatile S3Client client;
    private volatile boolean bucketReady;

    public ObjectStore(StorageProperties properties) {
        this.properties = properties;
    }

    public void put(String key, byte[] body, String contentType) {
        try {
            client().putObject(PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .contentType(contentType)
                    .build(), RequestBody.fromBytes(body));
        } catch (RuntimeException ex) {
            throw new MopException(ErrorCode.INTERNAL, "对象存储写入失败", null);
        }
    }

    public void writeTo(String key, OutputStream out) {
        try (var stream = client().getObject(GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build())) {
            stream.transferTo(out);
        } catch (Exception ex) {
            throw new MopException(ErrorCode.INTERNAL, "对象存储读取失败", null);
        }
    }

    private S3Client client() {
        if (!properties.configured()) {
            throw new MopException(ErrorCode.VALIDATION, "对象存储未配置", null);
        }
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = S3Client.builder()
                            .endpointOverride(URI.create(properties.getEndpoint()))
                            .region(Region.of(properties.getRegion()))
                            .credentialsProvider(StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                            .httpClient(UrlConnectionHttpClient.create())
                            .forcePathStyle(true)
                            .build();
                }
            }
        }
        ensureBucket();
        return client;
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build());
            } catch (NoSuchBucketException ex) {
                client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
            } catch (S3Exception ex) {
                if (ex.statusCode() == 404) {
                    client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
                } else {
                    throw new MopException(ErrorCode.INTERNAL, "对象存储不可用", null);
                }
            }
            bucketReady = true;
        }
    }
}
