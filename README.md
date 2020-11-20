# 自定义LiveData之解决数据倒灌/粘性问题

[TOC]

## 一、问题由来

### 1.1 数据倒灌/粘性问题

传统思想：先监听，后改变，然后可以监听到，反之不行；

`LiveData`：先改变，后监听，仍然能够监听到，类似`EventBus`的粘性事件的表现。

### 1.2 问题分析

![image](https://github.com/tianyalu/NeCustomLiveData/raw/master/show/livedata_sticky_feature_sequence.png)



## 二、问题解决





参考：[Android消息总线的演进之路：用LiveDataBus替代RxBus、EventBus](https://tech.meituan.com/2018/07/26/android-livedatabus.html)