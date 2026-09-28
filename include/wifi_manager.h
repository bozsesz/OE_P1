#ifndef WIFI_MANAGER_H
#define WIFI_MANAGER_H

#include <stdint.h>
#include <stdbool.h>
#include "esp_err.h"

#define PT100_COUNT 4

typedef enum
{
    MSG_TYPE_PT100_DATA,
    MSG_TYPE_CAN_FORWARD
} wifi_msg_type_t;

// A független vevőnek szánt egységes csomagstruktúra
typedef struct
{
    wifi_msg_type_t msg_type;
    uint8_t sender_id; // ESP32 kártya ID (pl. 1 vagy 2)

    union
    {
        // 1. Típus: PT100 mérések
        struct
        {
            float temperatures[PT100_COUNT];
            bool is_faulty[PT100_COUNT];
        } pt100;

        // 2. Típus: CAN buszról elcsípett üzenet
        struct
        {
            uint32_t can_id;
            uint8_t dlc;
            uint8_t data[8];
        } can_frame;
    } payload;
} wifi_packet_t;

esp_err_t wifi_manager_init(void);
esp_err_t wifi_manager_send_json(const char *json_string);

#endif // WIFI_MANAGER_H