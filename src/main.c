#include "MAX31865.h"
#include "wifi_manager.h"
#include "esp_log.h"
#include "nvs_flash.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdio.h>

static const char *TAG = "MAIN";

static max31865_dev_t sensors[PT100_COUNT] = {
    {.cs_pin = GPIO_NUM_7},
    {.cs_pin = GPIO_NUM_8},
    {.cs_pin = GPIO_NUM_9},
    {.cs_pin = GPIO_NUM_10}};

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

        // 5. Adatküldés UDP Broadcast-al (255.255.255.255:5005)
        wifi_manager_send_json(json_payload);

        vTaskDelay(pdMS_TO_TICKS(2000));
    }
}