package com.bajie.uvccamera.rtmplive.callback;

/**
 * desc:基础接口
 * create at: 2016/9/20 14:47
 * create by: yyw
 */
public interface IBase {

	/**
	 * 获取总体布局
	 *
	 * @return layoutID
	 */
	 int getLayout();

	/**
	 * 初始化视图
	 */
	 void initView(Object obj);

	/**
	 * 初始化数据
	 */
	 void initData();

	/**
	 * 设置监听器
	 */
	 void setListener();
}
