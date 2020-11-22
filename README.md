# 自定义LiveData之解决数据倒灌/粘性问题

[TOC]

## 一、问题由来

### 1.1 数据倒灌/粘性问题

传统思想：先监听，后改变，然后可以监听到，反之不行；

`LiveData`：先改变，后监听，仍然能够监听到，类似`EventBus`的粘性事件的表现。

### 1.2 问题分析

在首页`MainActivity`设置`LiveData`的值：

```java
MyEngine.getInstance().getData().setValue("李四");
```

然后跳转到`LoginActivity`时，订阅观察者：

```java
    private void initView() {
        tvText = findViewById(R.id.tv_text);
        //监听数据变化
        MyEngine.getInstance().getData().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                tvText.setText(s);
            }
        });
    }
```

此时订阅流程如下面的时序图所示：

![image](https://github.com/tianyalu/NeCustomLiveData/raw/master/show/livedata_sticky_feature_sequence.png)

当`LifecycleOwner`的状态变化时，会调用`LifecycleRegistry`的`handleLifecycleEvent(...)` --> `moveToState(...)` --> `sync()` --> `forwardPass(...)` -->  `ObserverWithState`的 `dispatchEvent(...)`方法：

```java
void dispatchEvent(LifecycleOwner owner, Event event) {
    State newState = getStateAfter(event);
    mState = min(mState, newState);
    mLifecycleObserver.onStateChanged(owner, event);
    mState = newState;
}
```

然后通过`LifecycleEventObserver`的`onStateChanged`回调到`LiveData`的`onStateChanged`方法：

```java
@Override
public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
    if (mOwner.getLifecycle().getCurrentState() == DESTROYED) {
        removeObserver(mObserver);
        return;
    }
    activeStateChanged(shouldBeActive());
}
```

最终会调用`LiveData.ObserverWrapper`的`activeStateChanged`方法：

```java
void activeStateChanged(boolean newActive) {
    if (newActive == mActive) {
        return;
    }
    // immediately set active state, so we'd never dispatch anything to inactive
    // owner
    mActive = newActive;
    boolean wasInactive = LiveData.this.mActiveCount == 0;
    LiveData.this.mActiveCount += mActive ? 1 : -1;
    if (wasInactive && mActive) {
        onActive();
    }
    if (LiveData.this.mActiveCount == 0 && !mActive) {
        onInactive();
    }
    if (mActive) {
        dispatchingValue(this);
    }
}
```

在`dispatchingValue`方法中又会调用`LiveData`的`considerNotify`方法：

```java
void dispatchingValue(@Nullable ObserverWrapper initiator) {
    if (mDispatchingValue) {
        mDispatchInvalidated = true;
        return;
    }
    mDispatchingValue = true;
    do {
        mDispatchInvalidated = false;
        if (initiator != null) {
            considerNotify(initiator);
            initiator = null;
        } else {
            for (Iterator<Map.Entry<Observer<? super T>, ObserverWrapper>> iterator =
                 mObservers.iteratorWithAdditions(); iterator.hasNext(); ) {
                considerNotify(iterator.next().getValue());
                if (mDispatchInvalidated) {
                    break;
                }
            }
        }
    } while (mDispatchInvalidated);
    mDispatchingValue = false;
}
```

在`considerNotify`方法中最后几行代码最为关键：如果`ObserverWrapper`的`mLastVersion`小于`LiveData`的`mVersion`，就会回调`mObserver`的`onChanged`方法；而每个新的订阅者其`version`都是`-1`，`LiveData`一旦设置过其`version`是大于`-1`的（每次`LiveData`设置值都会使其`version`加1），这样就会导致`LiveData`每注册一个新的订阅者，这个订阅者就会立刻收到一个回调，即使这个设置的动作发生在订阅之前。

```java
private void considerNotify(ObserverWrapper observer) {
    if (!observer.mActive) {
        return;
    }
    // Check latest state b4 dispatch. Maybe it changed state but we didn't get the event yet.
    //
    // we still first check observer.active to keep it as the entrance for events. So even if
    // the observer moved to an active state, if we've not received that event, we better not
    // notify for a more predictable notification order.
    if (!observer.shouldBeActive()) {
        observer.activeStateChanged(false);
        return;
    }
    if (observer.mLastVersion >= mVersion) {
        return;
    }
    observer.mLastVersion = mVersion;
    //noinspection unchecked
    observer.mObserver.onChanged((T) mData);
}
```

**总结如下**：

​	对于`LiveData`，其初始的`version`是-1，当我们调用了其`setValue`或者`postValue`后，其`version`会+1；对于每一个观察者的封装的`ObserverWrapper`，其初始`mLastVersion`也为-1，也就是说对于每一个新注册的观察者，其`mLastVersion`为-1；当`LiveData`设置这个`ObserverWrapper`的时候，如果`LiveData`的`version`大于`ObserverWrapper`的`mLastVersion`，`LiveData`就会强制把当前`value`推送给`Observer`。

参考：[Android消息总线的演进之路：用LiveDataBus替代RxBus、EventBus](https://tech.meituan.com/2018/07/26/android-livedatabus.html)

## 二、问题解决

自定义`LiveDataBus`继承`MutableLiveData`，重写其`observe`方法，然后采用`Hook`技术，强行修改`ObserverWrapper`的`mLastVersion`为`LiveData`的`mVersion`即可解决问题：

```java
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
```