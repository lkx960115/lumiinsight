package com.lumiinsight.infra.minio;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Component
public class ObjectStorage {

    private final MinioClient client;
    private final String bucket;

    public ObjectStorage(
            @Value("${lumiinsight.minio.endpoint}") String endpoint,
            @Value("${lumiinsight.minio.access-key}") String accessKey,
            @Value("${lumiinsight.minio.secret-key}") String secretKey,
            @Value("${lumiinsight.minio.bucket}") String bucket
    ) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
    }

    public void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 不可用: " + e.getMessage(), e);
        }
    }

    public void put(String objectKey, byte[] bytes, String contentType) {
        ensureBucket();
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(in, bytes.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("上传对象失败", e);
        }
    }

    public byte[] get(String objectKey) {
        try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("读取对象失败", e);
        }
    }

    public boolean ping() {
        try {
            ensureBucket();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
