package com.sty.ne.customlivedata;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

/**
 * 自定义的LiveData 解决数粘性问题/数据倒灌问题
 * @Author: tian
 * @UpdateDate: 2020/11/18 9:25 PM
 */
public class LiveDataBus<T> extends MutableLiveData<T> {
    private static final String TAG = LiveDataBus.class.getSimpleName();

    @Override
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        super.observe(owner, observer);
        Class<LiveData> liveDataClass = LiveData.class;

        try {
            Field mObservers = liveDataClass.getDeclaredField("mObservers");
            mObservers.setAccessible(true);

            //获取集合 SafeIterableMap
            Object observers = mObservers.get(this);
            Class<?> observersClass = observers.getClass();

            //获取SafeIterableMap的get(Object obj)方法
            Method methodGet = observersClass.getDeclaredMethod("get", Object.class);
            methodGet.setAccessible(true);

            //执行get函数
            Object objectWrapperEntry = methodGet.invoke(observers, observer);

            Object objectWrapper = null;
            if(objectWrapperEntry instanceof Map.Entry) {
                objectWrapper = ((Map.Entry) objectWrapperEntry).getValue();
            }

            if(objectWrapper == null) {
                throw new NullPointerException("ObserverWrapper can not be null");
            }

            //获取ObserverWrapper的Class对象 LifecycleBoundObserver extends ObserverWrapper
            Class<?> wrapperClass = objectWrapper.getClass().getSuperclass();
            //获取ObserverWrapper的field  mLastVersion
            Field mLastVersion = wrapperClass.getDeclaredField("mLastVersion");
            mLastVersion.setAccessible(true);
            //获取liveData的field mVersion
            Field mVersion = liveDataClass.getDeclaredField("mVersion");
            mVersion.setAccessible(true);
            Object mV = mVersion.get(this);
            //吧当前LiveData的mVersion赋值给 ObserverWrapper的field mLastVersion
            mLastVersion.set(objectWrapper, mV);

            mObservers.setAccessible(false);
            methodGet.setAccessible(false);
            mLastVersion.setAccessible(false);
            mVersion.setAccessible(false);

        }catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "observe: HOOK 发生了异常：" + e.getMessage());
        }
    }
}
