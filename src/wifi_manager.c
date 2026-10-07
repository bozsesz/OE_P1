#include "wifi_manager.h"
#include <string.h>
#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "lwip/sockets.h"
#include "lwip/sys.h"
#include "lwip/inet.h"
#include <lwip/netdb.h>
#include "wifi_credentials.h"

#define UDP_PORT 5005 // Android app port-ja

static const char *TAG = "WIFI_MANAGER";
static int s_udp_socket = -1;
static struct sockaddr_in s_dest_addr;
static bool s_connected = false;

// Event handler a Wi-Fi és IP események kezelésére
static void wifi_event_handler(void *arg, esp_event_base_t event_base,
                               int32_t event_id, void *event_data)
{
    if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_START)
    {
        esp_wifi_connect();
        ESP_LOGI(TAG, "Wi-Fi elindítva, csatlakozás folyamatban...");
    }
    else if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_DISCONNECTED)
    {
        s_connected = false;
        ESP_LOGW(TAG, "Wi-Fi kapcsolat megszakadt, újracsatlakozás...");
        esp_wifi_connect();
    }
    else if (event_base == IP_EVENT && event_id == IP_EVENT_STA_GOT_IP)
    {
        ip_event_got_ip_t *event = (ip_event_got_ip_t *)event_data;
        ESP_LOGI(TAG, "Sikeres csatlakozás! Kiosztott IP cím: " IPSTR, IP2STR(&event->ip_info.ip));
        s_connected = true;
    }
}

// UDP Socket előkészítése unicast küldésre
static void init_udp_socket(void)
{
    s_udp_socket = socket(AF_INET, SOCK_DGRAM, IPPROTO_IP);
    if (s_udp_socket < 0)
    {
        ESP_LOGE(TAG, "Nem sikerült létrehozni az UDP socket-et!");
        return;
    }

    // Célcím beállítása (tablet IP:5005)
    memset(&s_dest_addr, 0, sizeof(s_dest_addr));
    s_dest_addr.sin_family = AF_INET;
    s_dest_addr.sin_port = htons(UDP_PORT);
    s_dest_addr.sin_addr.s_addr = inet_addr(TABLET_IP);
}

esp_err_t wifi_manager_init(void)
{
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_sta();

    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));

    // Eseménykezelők regisztrálása
    ESP_ERROR_CHECK(esp_event_handler_instance_register(WIFI_EVENT,
                                                        ESP_EVENT_ANY_ID,
                                                        &wifi_event_handler,
                                                        NULL,
                                                        NULL));
    ESP_ERROR_CHECK(esp_event_handler_instance_register(IP_EVENT,
                                                        IP_EVENT_STA_GOT_IP,
                                                        &wifi_event_handler,
                                                        NULL,
                                                        NULL));

    wifi_config_t wifi_config = {
        .sta = {
            .ssid = WIFI_SSID,
            .password = WIFI_PASS,
        },
    };

    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &wifi_config));
    ESP_ERROR_CHECK(esp_wifi_start());

    init_udp_socket();

    return ESP_OK;
}

// Tetszőleges szöveges (JSON) adat kiküldése UDP Unicast-on
esp_err_t wifi_manager_send_json(const char *json_string)
{
    if (!s_connected)
    {
        ESP_LOGW(TAG, "Nincs Wi-Fi kapcsolat, UDP csomag nem küldhető.");
        return ESP_FAIL;
    }

    if (s_udp_socket < 0)
    {
        init_udp_socket();
    }

    int err = sendto(s_udp_socket, json_string, strlen(json_string), 0,
                     (struct sockaddr *)&s_dest_addr, sizeof(s_dest_addr));

    if (err < 0)
    {
        ESP_LOGE(TAG, "Hiba az UDP Broadcast küldésekor: errno %d", errno);
        return ESP_FAIL;
    }

    ESP_LOGI(TAG, "UDP csomag elküldve a tabletnek a %s:%d címre", TABLET_IP, UDP_PORT);
    return ESP_OK;
}