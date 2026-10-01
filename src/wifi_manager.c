#include "wifi_manager.h"
#include <string.h>
#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "lwip/sockets.h"
#include "lwip/sys.h"
#include <lwip/netdb.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

// #define EXAMPLE_ESP_WIFI_SSID "MechatroMotive"  // Írd ide a Wi-Fi / Hotspot nevét
// #define EXAMPLE_ESP_WIFI_PASS "m3chatro_K4kukk" // Írd ide a jelszót
#define UDP_PORT 4210 // A Node-RED udp in portja
#define MAX_CRED_LEN 32

static const char *TAG = "WIFI_MANAGER";
static int s_udp_socket = -1;
static struct sockaddr_in s_dest_addr;
static bool s_connected = false;

// Segédfüggvény egy sor beolvasására a soros portról
void read_line_from_console(char *buffer, size_t max_len)
{
    size_t index = 0;
    while (index < max_len - 1)
    {
        int c = getchar();

        // Ha nem érkezett karakter, várunk egy kicsit (FreeRTOS watchdog miatt)
        if (c == EOF || c == 0xFF)
        {
            vTaskDelay(pdMS_TO_TICKS(50));
            continue;
        }

        // Enter gomb érzékelése (\n vagy \r)
        if (c == '\n' || c == '\r')
        {
            if (index > 0)
            { // Ha már írt be valamit, befejezzük a sort
                break;
            }
            continue; // Üres entereket átlépjük
        }

        // Karakter elmentése és visszajelzése (echo) a konzolra
        buffer[index++] = (char)c;
        putchar(c);
    }
    buffer[index] = '\0'; // Sztring lezárása
    printf("\n");         // Új sor a konzolon
}

// A függvény, ami elkéri a Wi-Fi adatokat
void get_wifi_credentials(char *ssid, char *password)
{
    printf("\n=== WI-FI BEÁLLÍTÁSOK ===\n");

    // SSID bekérése
    printf("Kérlek, add meg a Wi-Fi SSID-t: ");
    fflush(stdout);
    read_line_from_console(ssid, MAX_CRED_LEN);

    // Jelszó bekérése
    printf("Kérlek, add meg a Wi-Fi jelszót: ");
    fflush(stdout);
    read_line_from_console(password, MAX_CRED_LEN);
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

esp_err_t wifi_manager_init(char *wifi_ssid, char *wifi_pass)
{
    ESP_LOGI(TAG, "Wi-Fi Manager inicializálása...");

    // Wi-Fi SSID és jelszó ellenőrzése
    if (strlen(wifi_ssid) == 0 || strlen(wifi_pass) == 0)
    {
        ESP_LOGE(TAG, "Wi-Fi SSID vagy jelszó üres! Kérlek, add meg a helyes adatokat.");
        return ESP_FAIL;
    }
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
                //.ssid = EXAMPLE_ESP_WIFI_SSID,
                //.password = EXAMPLE_ESP_WIFI_PASS,
                .ssid = wifi_ssid,
                .password = wifi_pass},
        };

        ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
        ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &wifi_config));
        ESP_ERROR_CHECK(esp_wifi_start());

        init_udp_socket();

        return ESP_OK;
    }
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
