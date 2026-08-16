-keep public class com.au.module_nested.layout.* { *; }
-keep public class com.au.module_nested.widget.* { *; }

# NeedUpdateFragmentStateAdapter 反射读取父类 FragmentStateAdapter 的 mFragments
# 做局部更新。查找失败被 ReflectionUtils 吞掉返回 null，不崩但局部更新静默失效。
-keep class androidx.viewpager2.adapter.FragmentStateAdapter {
androidx.collection.LongSparseArray mFragments;
}