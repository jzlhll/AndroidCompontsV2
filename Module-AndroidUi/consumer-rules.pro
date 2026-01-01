#ViewBinding不混淆
-keep class * implements androidx.viewbinding.ViewBinding {
  ** bind(...);
  ** inflate(...);
}

#与泛型相关的反射不混淆
-keep class * extends android.app.Dialog {
 <init>(...);
}

-keepclassmembers public class * extends android.app.Dialog {
void set*(***);
*** get*();
}

-keep class * implements com.au.module_androidui.widget.* {
 <init>(...);
}
-keep class * implements com.au.module_androidui.dialogs.* {
 <init>(...);
}

-keepclassmembers public class * extends android.view.View {
void set*(***);
*** get*();
}

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}
-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
-keep class **.R$* {*;}
-keepclassmembers enum * { *;}

-keep class android.databinding.** { *; }

