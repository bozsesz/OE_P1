#include "MAX31865.h"
#include "ble_manager.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static const char *TAG = "MAIN";

static max31865_dev_t sensors[PT100_COUNT] = {
    {.cs_pin = GPIO_NUM_7},
    {.cs_pin = GPIO_NUM_8},
    {.cs_pin = GPIO_NUM_9},
    {.cs_pin = GPIO_NUM_10}};

void app_main(void)
{
    // 1. BLE Manager indítása
    esp_err_t ret = ble_manager_init();
    if (ret != ESP_OK)
    {
        ESP_LOGE(TAG, "BLE Manager inditasi hiba: %s", esp_err_to_name(ret));
    }

    // 2. MAX31865 szenzorok indítása
    max31865_init_all(sensors, PT100_COUNT);

    while (1)
    {
        ble_pt100_data_t ble_data;

        for (int i = 0; i < PT100_COUNT; i++)
        {
            max31865_read_sensor(&sensors[i]);

            if (sensors[i].is_faulty)
            {
                ESP_LOGE(TAG, "Szenzor [%d] (GPIO %d) HIBÁS!", i, sensors[i].cs_pin);
                ble_data.temp_celsius[i] = -999.0f; // Hiba esetén kitüntetett érték
            }
            else
            {
                ESP_LOGI(TAG, "Szenzor [%d] Temp: %.2f °C", i, sensors[i].temperature);
                ble_data.temp_celsius[i] = sensors[i].temperature;
            }
            ble_data.is_faulty[i] = sensors[i].is_faulty;
        }

        // 3. Frissített adatok kiküldése BLE-n
        ble_manager_update_data(&ble_data);

        vTaskDelay(pdMS_TO_TICKS(2000));
    }
}