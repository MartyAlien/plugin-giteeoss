package top.yocloud.halo.plugin.oss.utils;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字符串插值模板工具类
 * <br/>
 * created by Mr.Bai at 2023/05/19 10:27
 */
public class StringTemplateUtil {
    private static final Logger LOGGER = Logger.getLogger(StringTemplateUtil.class.getName());
    /**
     * 匹配${}的占位符
     */
    public static final String PLACEHOLDER_REGEX = "\\$\\{(.+?)\\}";

    /**
     * 渲染文本
     * @param template 带有占位符模板
     * @param data 上下文数据
     * @return 渲染后的文本
     */
    public static String render(String template, Map<String, Object> data) {
        return render(template, data, PLACEHOLDER_REGEX);
    }

    /**
     *
     * 渲染文本
     * @param template 带有占位符模板
     * @param data 上下文数据
     * @param regex 占位符正则
     * @return 渲染后的文本
     */
    public static String render(String template, Map<String, Object> data, String regex) {
        if (template == null || template.trim().isEmpty()) {
            return "";
        }
        if (regex == null || regex.trim().isEmpty()) {
            return template;
        }
        if (data == null || data.size() == 0) {
            return template;
        }
        try {
            StringBuffer appendReplaceSb = new StringBuffer();
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(template);
            while (matcher.find()) {
                String key = matcher.group(1);
                Object value = data.get(key);
                if (value == null) {
                    LOGGER.log(Level.WARNING, "{0} not found in context",  matcher.group(0));
                }
                matcher.appendReplacement(appendReplaceSb, String.valueOf(value));//
            }
            matcher.appendTail(appendReplaceSb);
            return appendReplaceSb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return template;
    }
}
