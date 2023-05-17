package top.yocloud.halo.plugin.oss;

import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * 配置类
 *
 * @author Mr.Bai at 2023/05/16 15:53
 */
@Data
public class GiteeOssProperties {
    private String owner;
    private String repo;
    private String branch;
    private String path;
    private String token;
    private String creatName;
    private String jsdelivr;
    private Boolean deleteSync;

    public String getObjectName(String filename) {
        var objectName = filename;
        if (StringUtils.hasText(getPath())) {
            objectName = getPath() + "/" + objectName;
        }
        return objectName;
    }
}
