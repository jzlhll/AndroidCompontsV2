# 如果引入了Ucrop库请添加混淆
-keep class com.luck.picture.lib.** { *; }
-keep class com.luck.lib.camerax.** { *; }

-dontwarn com.yalantis.ucrop**
-keep class com.yalantis.ucrop** { *; }
-keep interface com.yalantis.ucrop** { *; }