#include "MAX31865.h"
#include "wifi_manager.h"
#include "esp_log.h"
#include "nvs_flash.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdio.h>
#include "esp_http_client.h"

#define BLYNK_TEMPLATE_ID "TMPLxxxxxx"
#define BLYNK_TEMPLATE_NAME "ESP32 Műszerfal"
#define BLYNK_AUTH_TOKEN "az_on_egyedi_tokenje"

static const char *TAG = "MAIN";

static max31865_dev_t sensors[PT100_COUNT] = {
    {.cs_pin = GPIO_NUM_7},
    {.cs_pin = GPIO_NUM_8},
    //{.cs_pin = GPIO_NUM_9},
    {.cs_pin = GPIO_NUM_10}};

void blynk_send_virtual_pin(int pin_number, float value)
{
    char url[128];
    // A Blynk REST API végpontja a pin frissítésére
    snprintf(url, sizeof(url), "https://blynk.cloud/external/api/update?token=%s&V%d=%.2f",
             BLYNK_AUTH_TOKEN, pin_number, value);

    esp_http_client_config_t config = {
        .url = url,
        .method = HTTP_METHOD_GET,
        .timeout_ms = 3000,
    };

    esp_http_client_handle_t client = esp_http_client_init(&config);
    esp_err_t err = esp_http_client_perform(client);

    if (err == ESP_OK) {
        ESP_LOGI(TAG, "Blynk V%d frissítve: %.2f (HTTP status: %d)", 
                 pin_number, value, esp_http_client_get_status_code(client));
    } else {
        ESP_LOGE(TAG, "Blynk HTTP kérés sikertelen: %s", esp_err_to_name(err));
    }

    esp_http_client_cleanup(client);
}

void app_main(void)
{
    vTaskDelay(pdMS_TO_TICKS(2000));
    // 1. NVS flash init
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND)
    {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);
    ESP_LOGI(TAG, "nvs_init done...");

    // 2. Wi-Fi STA + UDP init
    ESP_ERROR_CHECK(wifi_manager_init());
    ESP_LOGI(TAG, "wifi_init done...");

    // 3. Szenzorok init
    max31865_init_all(sensors, PT100_COUNT);
    ESP_LOGI(TAG, "sensor_init done...");

    while (1)
    {
        float temps[PT100_COUNT];
        bool faults[PT100_COUNT];

        for (int i = 0; i < PT100_COUNT; i++)
        {
            max31865_read_sensor(&sensors[i]);

            faults[i] = sensors[i].is_faulty;

            if (sensors[i].is_faulty)
            {
                ESP_LOGE(TAG, "Szenzor [%d] HIBÁS!", i);
                temps[i] = -999.0f;
            }
            else
            {
                ESP_LOGI(TAG, "Szenzor [%d] Temp: %.2f °C", i, sensors[i].temperature);
                temps[i] = sensors[i].temperature;
            }
        }

        ESP_LOGI(TAG, "Lefutott az egesz inti ciklus...");
        wifi_manager_send_json("{\"status\":\"ALIVE\"}");

        // 4. JSON formátumú üzenet összeállítása az UDP küldéshez
        char json_payload[256];
        snprintf(json_payload, sizeof(json_payload),
                 "{"
                 "\"dev\":\"ESP32_C3_01\","
                 "\"temps\":[%.2f,%.2f,%.2f,%.2f],"
                 "\"faults\":[%s,%s,%s,%s]"
                 "}",
                 temps[0], temps[1], temps[2], temps[3],
                 faults[0] ? "true" : "false",
                 faults[1] ? "true" : "false",
                 faults[2] ? "true" : "false",
                 faults[3] ? "true" : "false");

        // 5. Adatküldés UDP Broadcast-al (255.255.255.255:4210)
        wifi_manager_send_json(json_payload);

        // Tegyük fel, hogy kiolvastad a méréseket:
        float temp1 = 24.50; // MAX31865_1
        float temp2 = 80.12; // MAX31865_2
        
        // Adatok küldése a Blynk Dashboard felé (V0, V1, V2, V3 pin-ek)
        blynk_send_virtual_pin(0, temp1);
        blynk_send_virtual_pin(1, temp2);

        vTaskDelay(pdMS_TO_TICKS(2000));
    }
}