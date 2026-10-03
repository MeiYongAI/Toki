# 保留 XposedModule 入口类及生命周期方法
-keep class io.github.meiyongai.toki.hook.TokiModule { *; }

# Android 组件由 Manifest 自动生成保留规则；libxposed 服务使用依赖自带规则。
# 保留错误定位信息，不保留未使用的图标、工具或方法。
-keepattributes SourceFile,LineNumberTable
