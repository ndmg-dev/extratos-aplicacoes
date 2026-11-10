package com.mendoncagalvao.extratos.storage;

import com.mendoncagalvao.extratos.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;

/**
 * Armazenamento em S3/MinIO (produção). Ativado por {@code storage.type=s3}.
 * Usa path-style access (necessário para o MinIO) e cria o bucket se não existir.
 */
@Component
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "s3")
public class S3Storage implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3Storage.class);

    private final S3Client s3;
    private final String bucket;

    public S3Storage(StorageProperties props) {
        this.bucket = props.bucket();
        var builder = S3Client.builder()
                .region(Region.of(props.region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.pathStyle())
                        .build());
        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder = builder.endpointOverride(URI.create(props.endpoint()));
        }
        if (props.accessKey() != null && !props.accessKey().isBlank()) {
            builder = builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey(), props.secretKey())));
        }
        this.s3 = builder.build();
        garantirBucket();
    }

    private void garantirBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            log.info("Criando bucket de storage: {}", bucket);
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                log.info("Criando bucket de storage: {}", bucket);
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } else {
                throw e;
            }
        }
    }

    @Override
    public String salvar(String storageKey, byte[] conteudo) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(storageKey)
                .contentType("application/pdf").build(), RequestBody.fromBytes(conteudo));
        return storageKey;
    }

    @Override
    public byte[] carregar(String storageKey) {
        ResponseBytes<GetObjectResponse> bytes =
                s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(storageKey).build());
        return bytes.asByteArray();
    }

    @Override
    public boolean existe(String storageKey) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(storageKey).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }
}
