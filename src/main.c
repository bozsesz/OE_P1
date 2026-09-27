#include "MAX31865.h"
#include "wifi_manager.h"
#include "esp_log.h"
#include "nvs_flash.h"
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
    // 1. NVS flash init
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);

    // 2. Wi-Fi ESP-NOW init
    ESP_ERROR_CHECK(wifi_manager_init());

    // 3. Szenzorok init
    max31865_init_all(sensors, PT100_COUNT);

    while (1)
    {
        wifi_packet_t wifi_data;

        for (int i = 0; i < PT100_COUNT; i++)
        {
            max31865_read_sensor(&sensors[i]);

            if (sensors[i].is_faulty)
            {
                ESP_LOGE(TAG, "Szenzor [%d] HIBÁS!", i);
                wifi_data.payload.pt100.temperatures[i] = -999.0f;
            }
            else
            {
                ESP_LOGI(TAG, "Szenzor [%d] Temp: %.2f °C", i, sensors[i].temperature);
                wifi_data.payload.pt100.temperatures[i] = sensors[i].temperature;
            }
            wifi_data.payload.pt100.is_faulty[i] = sensors[i].is_faulty;
        }

        // 4. Adatküldés ESP-NOW-val
        wifi_manager_send_packet(&wifi_data);

        vTaskDelay(pdMS_TO_TICKS(2000));
    }
}