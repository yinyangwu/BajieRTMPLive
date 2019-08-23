package com.bajie.uvccamera.rtmplive.util;

import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

/**
 * desc:toast工具类
 * create at: 2016/10/14 16:33
 * create by: yyw
 */
public class ToastUtils {
    private static Toast toast;

    /**
     * 弹出提示消息
     *
     * @param context 上下文对象
     * @param msg     消息内容
     */
    public static void toast(Context context, String msg) {
        int duration = Toast.LENGTH_SHORT;
        if (TextUtils.isEmpty(msg)) {
            return;
        }
        if (msg.length() > 10) {
            duration = Toast.LENGTH_LONG;
        }
        if (toast == null) {
            toast = Toast.makeText(context.getApplicationContext(), msg, duration);
        } else {
            toast.setText(msg);
        }
        toast.show();
    }

    /**
     * 弹出提示消息
     *
     * @param context 上下文对象
     * @param resid   消息内容string id
     */
    public static void toast(Context context, int resid) {
        toast(context, context.getString(resid));
    }

}
