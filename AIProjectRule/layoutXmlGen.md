编写xml布局，遵守如下规则：

## 控件列表
这里只列出来最常用的一部分，有需要的时候去Module-AndroidCommon/src/main/java/com/au/module_android/widget下寻找其他控件
- com.au.module_android.widget.CustomFontText 替代TextView
- com.au.module_android.widget.CustomButton 替代Button
- com.au.module_android.widget.CustomEditText 替代EditText
- com.au.module_android.widget.BgBuildXXXLayout，是一系列可以添加圆角背景的容器布局
- com.au.module_android.widget.BgBuildView是一个非容器的View布局，用于色块圆角背景
- com.au.module_android.widget.FlowLayout 用于子View自动间隔和自动换行的布局

## 容器布局
使用[控件列表]章节提到的BgBuildXXXLayout或androidx常规布局。
- 如果有圆角就使用他们，否则没有圆角，使用常规布局如androidx.constraintlayout.widget.ConstraintLayout等
- 常用属性有app:backgroundNormal, app:cornerRadius等，参考Module-AndroidCommon/src/main/res/values/attrs.xml中定义的属性
- 如果有阴影，则使用androidx的CardView去包裹
- 如果使用到FlowLayout，主要有2个属性app:flChildSpacing, app:flRowSpacing

## 文本, 输入框和按钮
使用[控件列表]章节提到的CustomFontText/CustomEditText/CustomButton。
- xml中的控件标签需要全称，如<com.au.module_android.widget.XXX>
- 在Module-AndroidColor/src/main/res/values/styles.xml里面查找合适的样式，使用项目的公共样式，StyleI8oXXX
  - 文本，如StyleAuTextNormal, 粗体使用StyleAuBTextHeadline，文字中粗使用StyleAuTextNormalMid等；灰色描述性文字使用StyleAuTextNormalDesc等；
  - 输入框，使用如StyleBlankEditText等；
  - 按钮，使用如常规StyleButtonPrimary, 二级按钮或者取消按钮使用StyleButtonSecondary, 提醒样式StyleButtonWarn等
  - 图片解析得到的文案，使用tools:text来显示，不使用android:text显示

## 边距
- 如果控件是占满屏幕宽度或者靠左，靠右有安全间距，marginStart, marginEnd, marginHorizontal, paddingXXX的数值优先使用@dimen/ui_padding_edge
- 除了靠边边距以外，其他的边距数值大于10dp的，必须保持3的倍数，比如12dp, 15dp, 24dp等

## 命名规则
这里规定xml中控件id的命名规则，使用驼峰命名法，同时遵守：
- 如果是文本，则以xxxTv命名，如titleTv
- 如果是输入框，则以xxxEdit命名
- 如果是按钮，则以xxxBtn命名
- 如果是图片，则以xxxImg命名
- 如果是比较大的容器布局，则以xxxHost命名，如loginHost;如果是比较小的容器布局，内部只有几个控件，则以xxxLayout命名
- 如果是RecyclerView，则命名以xxxRcv命名，如loginRcv

## 联动BindingFragment
理解UI xml + BindingFragment的泛型引入方式，方便从kt+xml的组合开发方式
- 创建BindingFragment的子类，继承BindingFragment<XXXBinding>和泛型填写
- 继承父类的该函数实现控件初始化
    ```kotlin
    override fun onBindingCreated(savedInstanceState: Bundle?)
    ```
- 点击事件使用onClick扩展函数
- 如果有recyclerView，不编写任何adapter, layoutManager等

## RecyclerView开发框架
与标准的RecyclerView有所不同，如下规定了项目中如何使用项目自定义框架(泛型封装)开发模式:
- Adapter需要一个独立于Fragment的类文件，放在Fragment的旁边，命名为XXXAdapter.kt
- Holder不独立创建文件，放在Adapter的类中，命名为XXXHolder.kt
- 创建Adapter子类，继承com.au.module_nested.recyclerview.BindRcvAdapter<DATA:Any, VH: BindViewHolder<DATA, *>>
  - 泛型传入Bean类和xml的ViewHolder
  - 继承函数onCreateViewHolder(parent: ViewGroup, viewType: Int): BindViewHolder<Bean, VH>，根据viewType创建ViewHolder,使用xxxHolder(create(parent))即可创建
  - 提交数据源使用函数submitList(data, false)(注意有第二个参数false)
  - 如果有不同的holder样式，则继承getItemViewType(position: Int): Int {return datas[position].viewType }。其中的viewType是需要自行定义在Bean类中

- Holder使用com.au.module_nested.recyclerview.viewholder.BindViewHolder<Bean, VB>
  - 泛型传入Bean和Xml的ViewBinding
  - 继承函数bindData(bean: Bean)进行数据绑定显示, 注意变量名固定是bean, 必须有super.bindData(bean)
  - 点击事件在init{}中初始化，使用onClick扩展函数
    - 添加Holder的构造函数追加onXXXClickBlock的lambda变量，因此在创建Holder的需要传入xxxHolder(onXxxClickBlock, create(parent))，
    - 同时在Adapter的onCreateViewHolder中传入onXXXClickBlock，adapter中的来源也是构造函数中传入的变量。因此就要求创建Adapter的时候传入点击事件
    - 通过currentData获取数据对象，往外传递，参考currentData?.let { data -> onXxxClickBlock.invoke(data) }
