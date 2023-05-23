package top.yocloud.halo.plugin.oss.constants;

import top.yocloud.halo.plugin.oss.utils.StringTemplateUtil;

import java.util.Map;

/**
 * Gitee oss constant.
 * <br/>
 * created by Mr.Bai at 2023/5/19 10:56
 */
public class GiteeOssConstant {
    /**
     * 元数据KEY：Gitee OSS
     */
    public static final String METADATA_ATTACHMENT_KEY = "ezgiteeoss.plugin.halo.run/Attachment-key";
    /**
     * 元数据KEY：Gitee store json
     */
    public static final String METADATA_GITEE_STORE_KEY = "ezgiteeoss.plugin.halo.run/GiteeStoreJSON-key";
    /**
     * 元数据KEY：Gitee OSS插件的PolicyTemplate模板名称
     */
    public static final String METADATA_POLICY_TTEMPLATE_NAME = "easy-giteeoss";
    /**
     * Gitee 开放API：文件操作
     */
    public static final String GITEE_API_FILE_OPER = "https://gitee.com/api/v5/repos/${owner}/${repo}/contents/${filePathName}";

    public static String renderGiteeApi(Map<String, Object> context) {
        return StringTemplateUtil.render(GITEE_API_FILE_OPER, context);
    }

    public static String renderFileInfoApi(Map<String, Object> context) {
        return StringTemplateUtil.render(GITEE_API_FILE_OPER.concat("?access_token=${accessToken}&ref=${branch}"), context);
    }

    public static String renderDelFileApi(Map<String, Object> context) {
        return StringTemplateUtil.render(GITEE_API_FILE_OPER.concat("?access_token=${accessToken}&branch=${branch}&sha=${sha}&message=${message}"), context);
    }

}
