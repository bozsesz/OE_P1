#include "MAX31865.h"
#include "can_manager.h"
#include "wifi_manager.h"
#include "esp_log.h"
#include "nvs_flash.h"
#include "driver/usb_serial_jtag.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdio.h>
#include <string.h>

static const char *TAG = "MAIN";
#define WIFI_PASSWORD_MAX_LEN 63

static max31865_dev_t sensors[PT100_COUNT] = {
    {.cs_pin = GPIO_NUM_7},
    {.cs_pin = GPIO_NUM_8},
    {.cs_pin = GPIO_NUM_9},
    {.cs_pin = GPIO_NUM_10}};

static esp_err_t read_wifi_password(char password[WIFI_PASSWORD_MAX_LEN + 1])
{
    if (!usb_serial_jtag_is_driver_installed())
    {
        usb_serial_jtag_driver_config_t driver_config = USB_SERIAL_JTAG_DRIVER_CONFIG_DEFAULT();
        esp_err_t err = usb_serial_jtag_driver_install(&driver_config);
        if (err != ESP_OK)
        {
            ESP_LOGE(TAG, "Failed to initialize USB Serial/JTAG input: %s", esp_err_to_name(err));
            return err;
        }
    }

    ESP_LOGI(TAG, "Enter Wi-Fi password for One-14BB, then press Enter:");

    size_t password_len = 0;
    bool password_too_long = false;
    bool skip_lf_after_cr = false;

    while (1)
    {
        uint8_t ch;
        int bytes_read = usb_serial_jtag_read_bytes(&ch, 1, portMAX_DELAY);
        if (bytes_read <= 0)
        {
            continue;
        }

        if (skip_lf_after_cr)
        {
            skip_lf_after_cr = false;
            if (ch == '\n')
            {
                continue;
            }
        }

        if (ch == '\r' || ch == '\n')
        {
            skip_lf_after_cr = ch == '\r';
            putchar('\n');
            fflush(stdout);
            if (password_too_long)
            {
                ESP_LOGW(TAG, "Password is too long; enter at most %d characters.", WIFI_PASSWORD_MAX_LEN);
                ESP_LOGI(TAG, "Enter Wi-Fi password for One-14BB, then press Enter:");
                password_len = 0;
                password_too_long = false;
                continue;
            }

            password[password_len] = '\0';
            return ESP_OK;
        }

        if (ch == '\b' || ch == 0x7f)
        {
            if (!password_too_long && password_len > 0)
            {
                password_len--;
                printf("\b \b");
                fflush(stdout);
            }
            continue;
        }

        if (!password_too_long)
        {
            if (password_len < WIFI_PASSWORD_MAX_LEN)
            {
                password[password_len++] = (char)ch;
                putchar('*');
                fflush(stdout);
            }
            else
            {
                password_too_long = true;
            }
        }
    }
}

void app_main(void)
{
    vTaskDelay(pdMS_TO_TICKS(2000));
    ESP_LOGI(TAG, "app_main started.");
    // 1. NVS flash init
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND)
    {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);

    // 2. Read Wi-Fi password from the USB Serial/JTAG console, then start Wi-Fi.
    char wifi_password[WIFI_PASSWORD_MAX_LEN + 1];
    if (read_wifi_password(wifi_password) != ESP_OK)
    {
        return;
    }
    ESP_ERROR_CHECK(wifi_manager_init(wifi_password));
    memset(wifi_password, 0, sizeof(wifi_password));

    // 3. CAN bus init
    ESP_ERROR_CHECK(can_manager_init());

    // 4. Szenzorok init
    max31865_init_all(sensors, PT100_COUNT);

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

        vTaskDelay(pdMS_TO_TICKS(2000));
    }
}