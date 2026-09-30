#include "wifi_manager.h"
#include <string.h>
#include "esp_wifi.h"
#include "esp_mac.h"
#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "lwip/sockets.h"
#include "lwip/sys.h"
#include <lwip/netdb.h>

#define EXAMPLE_ESP_WIFI_SSID "One-14BB"  // Írd ide a Wi-Fi / Hotspot nevét
#define UDP_PORT 4210                           // A Node-RED udp in portja

static const char *TAG = "WIFI_MANAGER";
static int s_udp_socket = -1;
static struct sockaddr_in s_dest_addr;
static bool s_connected = false;

static const char *wifi_disconnect_reason(uint8_t reason)
{
    switch (reason)
    {
    case 1:   return "unspecified";
    case 2:   return "authentication expired";
    case 3:   return "authentication left";
    case 4:   return "association expired";
    case 5:   return "AP reached its maximum number of stations";
    case 6:   return "not authenticated";
    case 7:   return "not associated";
    case 8:   return "association left";
    case 9:   return "association not authenticated";
    case 10:  return "unsupported power capability";
    case 11:  return "unsupported channel";
    case 13:  return "invalid information element";
    case 14:  return "MIC/key integrity check failed";
    case 15:  return "4-way handshake timed out (often wrong password/security mismatch)";
    case 16:  return "group key update timed out";
    case 17:  return "security information differs during handshake";
    case 18:  return "invalid group cipher";
    case 19:  return "invalid pairwise cipher";
    case 20:  return "invalid key management protocol";
    case 21:  return "unsupported RSN version";
    case 22:  return "invalid RSN capabilities";
    case 23:  return "802.1X authentication failed";
    case 24:  return "cipher suite rejected";
    case 200: return "beacon lost / AP out of range";
    case 201: return "no matching access point found";
    case 202: return "authentication failed (check password/security settings)";
    case 203: return "association failed";
    case 204: return "handshake timed out (often wrong password/security mismatch)";
    case 205: return "connection failed";
    case 206: return "AP time synchronization reset";
    default:  return "unmapped reason; see numeric reason code";
    }
}

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
        const wifi_event_sta_disconnected_t *event = (const wifi_event_sta_disconnected_t *)event_data;
        s_connected = false;
        ESP_LOGW(TAG,
                 "Wi-Fi disconnect: reason=%u (%s), BSSID=" MACSTR ". Reconnecting...",
                 event->reason, wifi_disconnect_reason(event->reason), MAC2STR(event->bssid));
        esp_wifi_connect();
    }
    else if (event_base == IP_EVENT && event_id == IP_EVENT_STA_GOT_IP)
    {
        ip_event_got_ip_t *event = (ip_event_got_ip_t *)event_data;
        ESP_LOGI(TAG, "Sikeres csatlakozás! Kiosztott IP cím: " IPSTR, IP2STR(&event->ip_info.ip));
        s_connected = true;
    }
}

// UDP Socket előkészítése broadcast küldésre
static void init_udp_socket(void)
{
    s_udp_socket = socket(AF_INET, SOCK_DGRAM, IPPROTO_IP);
    if (s_udp_socket < 0)
    {
        ESP_LOGE(TAG, "Nem sikerült létrehozni az UDP socket-et!");
        return;
    }

    // Broadcast engedélyezése a socketen
    int broadcast_permission = 1;
    setsockopt(s_udp_socket, SOL_SOCKET, SO_BROADCAST, (void *)&broadcast_permission, sizeof(broadcast_permission));

    // Célcím beállítása (255.255.255.255:4210)
    memset(&s_dest_addr, 0, sizeof(s_dest_addr));
    s_dest_addr.sin_family = AF_INET;
    s_dest_addr.sin_port = htons(UDP_PORT);
    s_dest_addr.sin_addr.s_addr = htonl(INADDR_BROADCAST);
}

esp_err_t wifi_manager_init(const char *password)
{
    if (password == NULL)
    {
        return ESP_ERR_INVALID_ARG;
    }

    wifi_config_t wifi_config = {0};
    size_t password_len = strlen(password);
    if (password_len >= sizeof(wifi_config.sta.password))
    {
        ESP_LOGE(TAG, "A Wi-Fi jelszó túl hosszú.");
        return ESP_ERR_INVALID_ARG;
    }
    memcpy(wifi_config.sta.ssid, EXAMPLE_ESP_WIFI_SSID, sizeof(EXAMPLE_ESP_WIFI_SSID));
    memcpy(wifi_config.sta.password, password, password_len + 1);

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

    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &wifi_config));
    ESP_ERROR_CHECK(esp_wifi_start());

    init_udp_socket();

    return ESP_OK;
}

// Tetszőleges szöveges (JSON) adat kiküldése UDP Broadcast-on
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

    ESP_LOGI(TAG, "UDP csomag sikeresen elküldve (255.255.255.255:%d)", UDP_PORT);
    return ESP_OK;
}