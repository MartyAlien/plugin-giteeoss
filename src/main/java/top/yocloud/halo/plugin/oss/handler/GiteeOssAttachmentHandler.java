package top.yocloud.halo.plugin.oss.handler;


import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.google.common.io.Files;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Constant;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.infra.utils.JsonUtils;
import top.yocloud.halo.plugin.oss.constants.GiteeOssConstant;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Gitee oss attachment handler.
 * <br/>
 * created by Mr.Bai at 2023/5/23 14:13
 */
@Slf4j
@Component
public class GiteeOssAttachmentHandler implements AttachmentHandler {
    private final WebClient webClient ;
    public GiteeOssAttachmentHandler() {
        webClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create().responseTimeout(Duration.ofMillis(10000))))
            .build();
    }

    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        return Mono.just(uploadContext).filter(context -> this.shouldHandle(context.policy()))
            .flatMap(context -> {
                final var properties = getProperties(context.configMap());
                return upload(context, properties)
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(objectDetail -> this.buildAttachment(properties, objectDetail));
            });
    }

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return Mono.just(deleteContext).filter(context -> this.shouldHandle(context.policy()))
            .publishOn(Schedulers.boundedElastic())
            .doOnNext(context -> {
                var annotations = context.attachment().getMetadata().getAnnotations();
                if (annotations == null || !annotations.containsKey(GiteeOssConstant.METADATA_ATTACHMENT_KEY)) {
                    return;
                }
                var attachmentKey = annotations.get(GiteeOssConstant.METADATA_ATTACHMENT_KEY);
                String fileStoreKey = annotations.getOrDefault(GiteeOssConstant.METADATA_GITEE_STORE_KEY, "{}");
                var properties = getProperties(deleteContext.configMap());
                if (properties.getIfDelSrc()) {
                    log.info("{} is being deleted from Gitee store", properties);
                    delete(new DelAttachmentContext(attachmentKey, fileStoreKey, properties)).flatMap(exit -> {
                        log.info("final delete result：{}", exit);
                        return Mono.just(exit);
                    }).block();
                }
            }).map(DeleteContext::attachment);
    }

    private Mono<Boolean> delete(DelAttachmentContext delAttachmentContext) {
        var filePathName = delAttachmentContext.filePathName();
        var storeJson = delAttachmentContext.storeJson();
        var properties = delAttachmentContext.properties();
        JSONObject storeJsonObj = null;
        if (StringUtils.isBlank(filePathName) || StringUtils.isBlank(storeJson) || !(storeJsonObj = JSONUtil.parseObj(storeJson)).containsKey("sha")) {
            log.warn("The file 【{}】does not have any necessary parameters, and the deletion is skipped", delAttachmentContext.filePathName());
            return Mono.just(Boolean.FALSE);
        }
        JSONObject renderContext = JSONUtil.parseObj(properties)
            .putOpt("filePathName", filePathName)
            .putOpt("message", String.format("halo plguin oss delete file: 【%s】", filePathName));
        renderContext.putAll(storeJsonObj);
        String uri = GiteeOssConstant.renderDelFileApi(renderContext);
        log.info("Prepare to delete file 【{}】", uri);
        return webClient.method(HttpMethod.DELETE)
            .uri(uri)
            .header("Content-Type", "application/json")
            .header("charset", "UTF-8")
            .accept(MediaType.ALL)
            .exchangeToMono(clientResponse -> {
                Mono<String> dataMap = clientResponse.bodyToMono(String.class).map(m -> {
                    log.info("删除文件调用结果:{}", m);
                    return m;
                });
                if (clientResponse.statusCode().is2xxSuccessful()) {
                    return dataMap.flatMap(m->Mono.just(true));
                } else if (clientResponse.statusCode().is4xxClientError()) {
                    return dataMap.flatMap(m->Mono.just(false));
                } else {
                    return dataMap.flatMap(m->Mono.error(new RuntimeException("Failed to delete file")));
                }
            });

    }

    private Mono<ObjectDetail> upload(UploadContext uploadContext, GiteeApiProperties properties) {
        FileRecordInfo fileRecordInfo = FileRecordInfo.initial(uploadContext.file()).generateRemoteAttr(properties);
        log.info("Prepare to upload file 【{}】 to 【{}】 warehouse"
            , String.format("srcName=%s, uploadName=%s",fileRecordInfo.originalFileName(), fileRecordInfo.remoteFileLocation())
            , String.format("Gitee:%s:%s:%s/%s", properties.getOwner(), properties.getRepo(), properties.getBranch(), properties.getPath()));

        String api = GiteeOssConstant.renderGiteeApi(JSONUtil.parseObj(properties).putOpt("filePathName", fileRecordInfo.remoteFileLocation()));
        log.debug("Upload file to Gitee api:{}", api);
        return DataBufferUtils.join(uploadContext.file().content())
            .map(dataBuffer -> {
                byte[] contentBytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(contentBytes);
                DataBufferUtils.release(dataBuffer);
                return contentBytes;
            })
            .map(contentBytes -> Base64.getEncoder().encodeToString(contentBytes))
            .flatMap(content -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return webClient.method(HttpMethod.POST)
                    .uri(api)
                    .header("Content-Type", "application/json")
                    .header("charset", "UTF-8")
                    .accept(MediaType.ALL)
                    .bodyValue(buildUploadBody(content, fileRecordInfo, properties).toString())
                    .exchangeToMono(clientResponse -> {
                        Mono<String> dataMap = clientResponse.bodyToMono(String.class).map(m -> {
                            log.debug("{}({})上传调用结果:{}", fileRecordInfo.remoteFileLocation(), fileRecordInfo.originalFileName(), m);
                            return m;
                        });
                        if (clientResponse.statusCode().is2xxSuccessful()) {
                            return dataMap.map(body -> {
                                JSONObject contentJsonObj = JSONUtil.parseObj(body).getJSONObject("content");
                                return new ObjectDetail(
                                    fileRecordInfo
                                        .fileLink(contentJsonObj.getStr("download_url"))
                                        .size(contentJsonObj.getLong("size"))
                                    , new JSONObject()
                                    .putOpt("originalFileName", fileRecordInfo.originalFileName())
                                    .putOpt("branch", properties.getBranch())
                                    .putOpt("path", contentJsonObj.getStr("path"))
                                    .putOpt("sha", contentJsonObj.getStr("sha")).toString());
                            });
                        } else {
                            return dataMap.flatMap(m->Mono.error(new RuntimeException("Failed to upload file: "+m)));
                        }
                    })
                    .onErrorContinue((e, i) -> {
                        e.printStackTrace();
                        log.info(JSONUtil.toJsonStr(i));
                    });
            }).subscribeOn(Schedulers.boundedElastic());
    }

    private JSONObject buildUploadBody(String content, FileRecordInfo fileRecordInfo, GiteeApiProperties properties) {
        return new JSONObject()
            .putOpt("content", content)
            .putOpt("message", String.format("halo plguin oss upload file: 【originfileName=%s, remoteFileLocation=%s 】", fileRecordInfo.originalFileName(), fileRecordInfo.remoteFileLocation()))
            .putOpt("branch", StringUtils.defaultIfEmpty(properties.getBranch(), "master"))
            .putOpt("access_token", properties.getAccessToken());
    }

    @Data
    @NoArgsConstructor
    @Accessors(chain = true, fluent=true)
    public static class FileRecordInfo{
        /**
         * 原始文件名
         */
        private String originalFileName;
        /**
         * 远程文件名(上传后)
         */
        private String remoteFileName;
        /**
         * 远程文件位置：{prop.path}/{remoteFileName}
         */
        private String remoteFileLocation;
        /**
         * 文件类型
         */
        private String fileType;
        /**
         * 文件大小
         */
        private long size;
        /**
         * 文件链接
         */
        private String fileLink;

        public static FileRecordInfo initial(FilePart filePart){
            Assert.notNull(filePart, "file must not be null");
            return new FileRecordInfo()
                .originalFileName(filePart.filename())
                .fileType(Optional.ofNullable(filePart.headers().getContentType())
                    .map(MediaType::toString)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .size(0L);
        }

        public FileRecordInfo generateRemoteAttr(GiteeApiProperties properties) {
            String suffix = Files.getFileExtension(this.originalFileName());
            String name =
                String.format("%s-%s", DateFormatUtils.format(new Date(), "yyyyMMddHHmmssSSS")
                    , RandomStringUtils.randomAlphabetic(4).toLowerCase());
            return this.remoteFileName(StringUtils.isBlank(suffix) ? name : name + "." + suffix)
                       .remoteFileLocation(properties == null || StringUtils.isBlank(properties.getPath()) ?
                            this.remoteFileName() : properties.getPath() + "/" + this.remoteFileName());
        }

    }

    record ObjectDetail(FileRecordInfo fileRecordInfo, String objectMetadata) {}
    public record DelAttachmentContext(String filePathName, @NonNull String storeJson, @NonNull GiteeApiProperties properties) {}


    @Data
    public static class GiteeApiProperties {
        /**
         * 仓库所属用户名
         */
        private String owner;
        /**
         * 仓库名
         */
        private String repo;
        /**
         * 分支名
         */
        private String branch;
        /**
         * 仓库路径
         */
        private String path;
        /**
         * 令牌
         */
        private String accessToken;
        /**
         * Halo创建者用户名
         */
        private String haloUserName;
        /**
         * 是否删除源文件
         */
        private Boolean ifDelSrc;

        public String generateFilePathName(String fileName) {
            if (StringUtils.isBlank(fileName)) {
                throw new IllegalArgumentException("文件名不能为空");
            }
            if (StringUtils.isNotBlank(getPath())){
                return getPath() + "/" + fileName;
            }
            return fileName;
        }

        public void setPath(String path) {
            this.path = path==null || path.isBlank() ? StringUtils.EMPTY : path.substring(path.startsWith("/")?1:0).trim().substring(0, path.endsWith("/")? path.length()-1 : path.length()).trim();
        }

    }

    private Attachment buildAttachment(GiteeApiProperties properties, ObjectDetail objectDetail) {
        var metadata = new Metadata();
        metadata.setName(UUID.randomUUID().toString());
        metadata.setAnnotations(
            Map.of(GiteeOssConstant.METADATA_ATTACHMENT_KEY, objectDetail.fileRecordInfo().remoteFileLocation()
                , GiteeOssConstant.METADATA_GITEE_STORE_KEY, objectDetail.objectMetadata()
                , Constant.EXTERNAL_LINK_ANNO_KEY, UriUtils.encodePath(objectDetail.fileRecordInfo().fileLink(), StandardCharsets.UTF_8)
            )
        );

        var spec = new Attachment.AttachmentSpec();
        spec.setSize(objectDetail.fileRecordInfo().size());
        spec.setDisplayName(objectDetail.fileRecordInfo().remoteFileName());
        spec.setMediaType(objectDetail.fileRecordInfo().fileType());

        var attachment = new Attachment();
        attachment.setMetadata(metadata);
        attachment.setSpec(spec);
        return attachment;
    }

    private boolean shouldHandle(Policy policy) {
        if (policy == null || policy.getSpec() == null ||
            policy.getSpec().getTemplateName() == null) {
            return false;
        }
        return GiteeOssConstant.METADATA_POLICY_TTEMPLATE_NAME.equals(policy.getSpec().getTemplateName());
    }
    private GiteeApiProperties getProperties(ConfigMap configMap) {
        var settingJson = configMap.getData().getOrDefault("default", "{}");
        return JsonUtils.jsonToObject(settingJson, GiteeApiProperties.class);
    }
}
