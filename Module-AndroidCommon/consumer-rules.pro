#banner
-keep class androidx.viewpager2.widget.ViewPager2{
androidx.viewpager2.widget.PageTransformerAdapter mPageTransformerAdapter;
androidx.viewpager2.widget.ScrollEventAdapter mScrollEventAdapter;
}

-keep class androidx.viewpager2.widget.PageTransformerAdapter{
androidx.recyclerview.widget.LinearLayoutManager mLayoutManager;
}

-keep class androidx.recyclerview.widget.RecyclerView$LayoutManager{
androidx.recyclerview.widget.RecyclerView mRecyclerView;
}

-keep class androidx.viewpager2.widget.ScrollEventAdapter{
androidx.recyclerview.widget.LinearLayoutManager mLayoutManager;
}

#gilde
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}
# for DexGuard only
#-keepresourcexmlelements manifest/application/meta-data@value=GlideModule


# lifecycle
-keep class androidx.lifecycle.** { *;}

-flattenpackagehierarchy
-allowaccessmodification
-keepattributes Exceptions,InnerClasses,Signature,SourceFile,LineNumberTable
-ignorewarnings

#kotlin 相关
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
#-keepclassmembers class kotlin.Metadata {
#    public <methods>;
#}
#-keepclasseswithmembers @kotlin.Metadata class * { *; }
#-keepclassmembers class **.WhenMappings {
#    <fields>;
#}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

-keep class kotlinx.** { *; }
-keep interface kotlinx.** { *; }
-dontwarn kotlinx.**

-keep class org.jetbrains.** { *; }
-keep interface org.jetbrains.** { *; }

-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }

-keep class com.google.android.gms.** { *; }
-keep interface com.google.android.gms.** { *; }

-dontwarn org.jetbrains.**

-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
-keep class **.R$* {*;}
-keepclassmembers enum * { *;}


#Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
# for DexGuard only
#-keepresourcexmlelements manifest/application/meta-data@value=GlideModule
-dontwarn com.bumptech.glide.**

# 或者保留某个包下所有类的字段（常用于模型包）
-keepclassmembers class com.au.module_android.api.** {
    <fields>;
}
