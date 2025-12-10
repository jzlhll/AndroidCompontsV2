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
