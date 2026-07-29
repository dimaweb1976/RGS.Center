package com.rgs.center.service;

import com.rgs.center.objects.DeviceCode;

/**
 * Прикладной адаптер получения данных от конкретного устройства.
 *
 * @param <T> тип результата getData
 */
public interface GetDataAdapter<T> {

    /**
     * @return код обслуживаемого устройства
     */
    DeviceCode deviceCode();

    /**
     * Выполняет один законченный цикл получения данных.
     *
     * @return полученные данные
     */
    T getData();
}
