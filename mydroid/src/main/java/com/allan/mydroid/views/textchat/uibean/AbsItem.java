package com.allan.mydroid.views.textchat.uibean;

import com.au.module_nested.recyclerview.IMultiViewTypeBean;

public abstract class AbsItem implements IMultiViewTypeBean {
    private final int viewType;

    public AbsItem(int viewType) {
        this.viewType = viewType;
    }

    @Override
    public int getViewType() {
        return viewType;
    }

    public static final int VIEW_TYPE_STATUS = 0;
    /**
     * 是不是我的消息
     */
    public static final int VIEW_TYPE_ME = 1;
    /**
     * 是否是别人的消息
     */
    public static final int VIEW_TYPE_OTHER = 2;
}
