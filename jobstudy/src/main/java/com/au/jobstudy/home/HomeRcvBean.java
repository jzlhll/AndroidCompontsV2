package com.au.jobstudy.home;

import com.au.module_nested.recyclerview.IMultiViewTypeBean;

/**
 * @author au
 */
public class HomeRcvBean implements IMultiViewTypeBean {
    @Override
    public int getViewType() {
        return viewType;
    }

    private final int viewType;
    public HomeRcvBean(int viewType) {
        this.viewType = viewType;
    }

    public static final int VIEW_TYPE_MARKUP = 0;
    public static final int VIEW_TYPE_HEAD = 1;
    public static final int VIEW_TYPE_TITLE = 2;
    public static final int VIEW_TYPE_ITEM = 3;

    public static final HomeRcvBean empty = new HomeRcvBean(VIEW_TYPE_MARKUP);
}
