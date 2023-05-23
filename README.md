<H2 align="center"><a href="https://github.com/MartyAlien/plugin-giteeoss">GiteeOSS</a> <a href="https://github.com/halo-dev/halo#">Halo</a>插件</H2>

<p align="center">
<a href="https://github.com/MartyAlien/plugin-giteeoss/releases"><img alt="GitHub release" src="https://img.shields.io/github/release/MartyAlien/plugin-giteeoss.svg?style=flat-square&include_prereleases" /></a>
<a href="https://github.com/MartyAlien/plugin-giteeoss/commits"><img alt="GitHub last commit" src="https://img.shields.io/github/last-commit/MartyAlien/plugin-giteeoss.svg?style=flat-square" /></a>
<br />
<a href="https://github.com/MartyAlien/plugin-giteeoss/issues">Issues</a>
<a href="mailto:libai.ace@gmail.com">邮箱</a>
</p>

------------------------------

## **为 Halo 2.0 提供Gitee OSS 的存储策略**

### 功能特点
1. 支持使用 Gitee 做附件存储
2. 支持删除文件时可以不同步删除仓库

> 感谢<a href="https://github.com/AirboZH/plugin-uposs">GithubOSS</a>插件的作者提供开源代码，本插件基于此插件进行修改
> 修改目的：国内用户可能使用Github不是很方便，故而开发此插件

## 获取插件方式
### 在 Release 下载最新插件包即可（无需开发环境）
### 本地打包（需要有开发环境）
下载源码后，进入项目根目录下并执行以下命令
```
./gradlew build
```
构建完成之后，可以在 build/libs 目录得到插件的 JAR 包，在 Halo 后台的插件管理上传即可。

## 如何使用

1. 准备Gitee仓库以及Access_Token
> 获取方法：不详记。需要开启仓库读写权限https://gitee.com/profile/personal_access_tokens
2. 进入系统后台 - 附件：添加存储策略，按要求填写相关设置保存即可
3. 上传附件：上传附件时选择刚刚添加的存储策略即可