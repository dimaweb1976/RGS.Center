package com.rgs.center.service;

import com.rgs.center.objects.DeviceCode;

public interface GetDataAdapter<T> {

    DeviceCode deviceCode();

    T getData();
}
