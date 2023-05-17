package top.yocloud.halo.plugin.oss;


import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;

/**
 * 插件入口
 *
 * @author Mr.Bai at 2023/05/16 15:53
 */
@Component
public class GiteeOSSPlugin extends BasePlugin {

    public GiteeOSSPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
