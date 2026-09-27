#ifndef WIFI_MANAGER_H
#define WIFI_MANAGER_H

#include <stdint.h>
#include <stdbool.h>
#include "esp_err.h"

#define PT100_COUNT 4

// Az átküldendő adatstruktúra
typedef struct {
    float temperatures[PT100_COUNT];
    bool is_faulty[PT100_COUNT];
} wifi_pt100_data_t;

/**
 * @brief Wi-Fi és ESP-NOW stack inicializálása
 * @return ESP_OK sikeres indítás esetén
 */
esp_err_t wifi_manager_init(void);

/**
 * @brief Adatok kiküldése ESP-NOW-val a megadott MAC címre (vagy Broadcast)
 * @param data A kiküldendő adatszerkezet
 * @param peer_mac A fogadó ESP32 MAC címe (NULL esetén Broadcast)
 * @return ESP_OK ha a csomag átadásra került a TX sornak
 */
esp_err_t wifi_manager_send_data(const wifi_pt100_data_t *data, const uint8_t *peer_mac);

#endif // WIFI_MANAGER_H