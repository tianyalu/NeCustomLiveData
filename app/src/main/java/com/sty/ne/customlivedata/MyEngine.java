package com.sty.ne.customlivedata;

/**
 * @Author: tian
 * @UpdateDate: 2020/11/18 8:56 PM
 */
public class MyEngine {
    //整个项目都需要用到引擎，所以只用一个实例
    private static volatile MyEngine instance;
    //数据
    //private MutableLiveData<String> data;
    private LiveDataBus<String> data;

    public MyEngine() {
    }

    public static MyEngine getInstance() {
        if(instance == null) {
            synchronized (MyEngine.class) {
                if(instance == null) {
                    instance = new MyEngine();
                }
            }
        }
        return instance;
    }

    //暴露数据
    //public MutableLiveData<String> getData() {
    public LiveDataBus<String> getData() {
        if(null == data) {
            //data = new MutableLiveData<>();
            data = new LiveDataBus<>();
        }
        return data;
    }
}
