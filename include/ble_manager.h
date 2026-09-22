#ifndef BLE_MANAGER_H
#define BLE_MANAGER_H

#include "esp_err.h"
#include <stdbool.h>

// A PT100 adatokat továbbító telemetria struktúra
typedef struct
{
    float temp_celsius[4];
    bool is_faulty[4];
} ble_pt100_data_t;

// NimBLE stack inicializálása és GATT szerver indítása
esp_err_t ble_manager_init(void);

// Adatok frissítése és küldése Notify segítségével a csatlakozott BLE kliensnek
esp_err_t ble_manager_update_data(const ble_pt100_data_t *data);

#endif // BLE_MANAGER_H