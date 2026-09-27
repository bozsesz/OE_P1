#include "wifi_manager.h"
#include <string.h>
#include "esp_wifi.h"
#include "esp_now.h"
#include "esp_log.h"
#include "esp_netif.h"

static const char *TAG = "WIFI_MANAGER";

// Broadcast MAC cím (FF:FF:FF:FF:FF:FF)
static const uint8_t s_broadcast_mac[ESP_NOW_ETH_ALEN] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

// Frissített ESP-NOW send callback szignatúra (ESP-IDF v5.1+)
static void wifi_manager_send_cb(const esp_now_send_info_t *tx_info, esp_now_send_status_t status)
{
    if (status == ESP_NOW_SEND_SUCCESS) {
        ESP_LOGI(TAG, "ESP-NOW csomag sikeresen elkuldve");
    } else {
        ESP_LOGE(TAG, "ESP-NOW csomag kuldes sikertelen!");
    }
}

// Adatfogadási visszajelzés (Callback)
static void wifi_manager_recv_cb(const esp_now_recv_info_t *recv_info, const uint8_t *data, int len)
{
    if (len == sizeof(wifi_pt100_data_t)) {
        wifi_pt100_data_t received_data;
        memcpy(&received_data, data, sizeof(wifi_pt100_data_t));
        ESP_LOGI(TAG, "Adat erkezett! [0]: %.2f °C", received_data.temperatures[0]);
    }
}

esp_err_t wifi_manager_init(void)
{
    // 1. Wi-Fi Netif es esemenyhurok
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());

    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));
    ESP_ERROR_CHECK(esp_wifi_set_storage(WIFI_STORAGE_RAM));
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_start());

    // 2. ESP-NOW inicializalasa
    esp_err_t ret = esp_now_init();
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "ESP-NOW init hiba: %s", esp_err_to_name(ret));
        return ret;
    }

    // Callback-ek regisztralasa
    ESP_ERROR_CHECK(esp_now_register_send_cb(wifi_manager_send_cb));
    ESP_ERROR_CHECK(esp_now_register_recv_cb(wifi_manager_recv_cb));

    // 3. Broadcast peer hozzaadasa
    esp_now_peer_info_t peer_info = {};
    memcpy(peer_info.peer_addr, s_broadcast_mac, ESP_NOW_ETH_ALEN);
    peer_info.channel = 0;
    peer_info.encrypt = false;

    if (!esp_now_is_peer_exist(s_broadcast_mac)) {
        ret = esp_now_add_peer(&peer_info);
        if (ret != ESP_OK) {
            ESP_LOGE(TAG, "Broadcast peer hozzaadasi hiba: %s", esp_err_to_name(ret));
            return ret;
        }
    }

    ESP_LOGI(TAG, "ESP-NOW sikeresen inicializalva (Broadcast modba allitva).");
    return ESP_OK;
}

esp_err_t wifi_manager_send_data(const wifi_pt100_data_t *data, const uint8_t *peer_mac)
{
    const uint8_t *target_mac = (peer_mac != NULL) ? peer_mac : s_broadcast_mac;
    return esp_now_send(target_mac, (const uint8_t *)data, sizeof(wifi_pt100_data_t));
}