package ru.localdev.fileservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "files")
public class FileEntity {

    @Id
    @Column(name = "file_id", nullable = false, updatable = false)
    private UUID fileId;

    @Column(name = "name")
    private String name;

    @Column(name = "extension")
    private String extension;

    @Column(name = "size", nullable = false)
    private long size;

    @Column(name = "check_sum")
    private String checkSum;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FileStatus status;

    @Column(name = "bucket", nullable = false)
    private String bucket;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "multipart_upload_id")
    private String multipartUploadId;

    @Column(name = "part_count", nullable = false)
    private int partCount;

    @Column(name = "uploader_user_id")
    private String uploaderUserId;

    @Column(name = "initialized_at")
    private Instant initializedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    public UUID getFileId() {
        return fileId;
    }

    public void setFileId(UUID fileId) {
        this.fileId = fileId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getCheckSum() {
        return checkSum;
    }

    public void setCheckSum(String checkSum) {
        this.checkSum = checkSum;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public FileStatus getStatus() {
        return status;
    }

    public void setStatus(FileStatus status) {
        this.status = status;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public String getMultipartUploadId() {
        return multipartUploadId;
    }

    public void setMultipartUploadId(String multipartUploadId) {
        this.multipartUploadId = multipartUploadId;
    }

    public int getPartCount() {
        return partCount;
    }

    public void setPartCount(int partCount) {
        this.partCount = partCount;
    }

    public String getUploaderUserId() {
        return uploaderUserId;
    }

    public void setUploaderUserId(String uploaderUserId) {
        this.uploaderUserId = uploaderUserId;
    }

    public Instant getInitializedAt() {
        return initializedAt;
    }

    public void setInitializedAt(Instant initializedAt) {
        this.initializedAt = initializedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
